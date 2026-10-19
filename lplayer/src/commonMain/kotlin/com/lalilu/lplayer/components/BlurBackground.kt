package com.lalilu.lplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.Bitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
import com.lalilu.RemixIcon
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** 组合层（非 ViewModel 内）的诊断日志。 */
private val compositionLogger = Logger.withTag("BlurBackground")

/**
 * 把解码结果转成 Compose 的 [ImageBitmap]。
 *
 * 各平台实现（Android 的 `asImageBitmap`、其余的 `asComposeImageBitmap`）都是重新包一层，
 * [DefaultBlurBackground] 用 `cover === veilOwner` 判断"遮罩归属哪张图"，依赖的正是
 * "不同次加载得到不同实例"这一点——同一张图重复加载也不会误领旧封面的遮罩。
 */
expect fun Bitmap.toImageBitmap(): ImageBitmap

/**
 * 平台相关的封面背景入口。
 *
 * @param imageData 当前歌曲对应的封面数据（Coil 的 data），变化时会触发一次新的加载
 * @param onColorPairFetched 从封面取到的主色回调（背景色，前景色）
 * @param blurProgress 外部驱动的基础模糊进度 `0f..1f`，会映射成最多 50dp 的模糊与压黑
 */
@Composable
expect fun BlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any?,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
)

/** 快路径阈值：150ms 内加载完成就只是一次普通的淡入 + 模糊切换，不出现任何加载中提示。 */
private val FastPathThreshold = 150.milliseconds

/** 加载超时：超过它仍未拿到图片就取消本次请求并进入失败态。 */
private val LoadTimeout = 10_000.milliseconds

/**
 * 失败重试的退避区间。
 *
 * 封面"立刻失败"通常不是真的没有封面：远端源的封面要等元数据提取完成、边车图片要等下载、
 * 数据源可能刚好在重连。第一次请求就打上失败标记会让旧封面在切歌瞬间被占位背景顶掉。
 */
private val RetryInitialDelay = 250.milliseconds
private val RetryMaxDelay = 1_000.milliseconds

/**
 * 加载中在旧封面上额外叠加的模糊半径。
 *
 * 刻意与封面进入时的起始模糊（[EnterExitState.PreEnter] 对应的 10dp）取同一个值：
 * 旧封面从清晰变糊、新封面从糊变清晰，两端用同一个值衔接，切换瞬间不会出现模糊量跳变。
 */
private val LoadingBlur = 10.dp

/** 加载中白色遮罩的最大不透明度。 */
private const val LoadingVeilAlpha = 0.35f

private const val LoadingVeilFadeInMillis = 300
private const val LoadingVeilFadeOutMillis = 400

/**
 * 加载失败的记录对象；只写进 [BlurBackgroundViewModel.errorState]，不会被抛出。
 *
 * 两种失败共用它：请求本身失败或超时（拿不到结果），以及拿到了结果却取不到位图。
 */
private class CoverLoadTimeoutException : Exception("cover load timed out")

/**
 * 封面加载相位。
 *
 * 它与 [BlurBackgroundViewModel.imageState]（"当前该显示哪张图"）是正交的两件事：
 * 相位只驱动"加载中"的附加视觉（旧封面加糊 + 白色遮罩），不参与 [AnimatedContent] 的转场身份，
 * 所以相位的来回变化不会产生任何多余的淡入淡出——转场始终只由"位图换了没"触发。
 * 失败态不在相位里：它由 [BlurBackgroundViewModel.imageState] 置 null 切到占位背景表达。
 *
 * @param source 进入加载时仍在显示的那张旧封面，用来判定"遮罩归属哪张图"。
 *               冷启动时没有旧封面，这里为 null，也就没有可归属的遮罩。
 */
data class LoadingPhase(val source: ImageBitmap?)

class BlurBackgroundViewModel : ViewModel() {
    companion object {
        /** 仅用于排查的单调编号：一眼看出是不是又新建了一个实例。 */
        private var nextInstanceId = 1
    }

    private val logger = Logger.withTag("BlurBackground")

    /** 诊断用：这个实例是第几个创建的。 */
    internal val instanceId: Int = nextInstanceId++

    init {
        logger.i { "封面 VM 创建 #$instanceId" }
    }

    override fun onCleared() {
        logger.i { "封面 VM 销毁 #$instanceId" }
    }
    /**
     * 当前该显示的封面。
     *
     * null 有两种来源：冷启动还没有任何加载结果，以及本次加载失败/超时。
     * 两种情况都会让 [DefaultBlurBackground] 显示占位背景。
     */
    val imageState = mutableStateOf<ImageBitmap?>(null)

    /**
     * 非空表示正在加载中（[LoadingPhase.source] 是此刻仍在显示的旧封面）。
     *
     * 连续切歌时，上一次加载可能在下一次 [loadImage] 出结果之前就被取消，
     * 相位会短暂停留在上一次的值；此时 owner 仍是当前展示的那张图，遮罩归属依然成立。
     */
    val loadingState = mutableStateOf<LoadingPhase?>(null)

    /**
     * 非空表示本次加载失败，保持到下一次 [loadImage] 开始。
     *
     * 目前只作可观测性记录：UI 的失败态由 [imageState] 置 null 驱动，没有消费方。
     */
    val errorState = mutableStateOf<Throwable?>(null)

    val bgColorState = MutableStateFlow<Color?>(null)
    val contentColorState = MutableStateFlow<Color?>(null)

    suspend fun loadImage(
        context: PlatformContext,
        imageData: Any?,
        size: Int = 1200
    ) = withContext(Dispatchers.Unconfined) {
        errorState.value = null
        if (imageData == null) {
            loadingState.value = null
            // 切歌瞬间曲目可能短暂为空（队列的一个中间快照），此时**不能**把当前封面清掉：
            // 清掉就等于旧图直接消失，等新图到达再"啪"地出现，过渡动画自然也没了。
            // 真的没有封面（冷启动 / 加载失败）由后面的失败路径负责切到占位背景。
            if (imageState.value != null) {
                logger.i { "封面数据暂时为空，保留当前封面等下一次数据" }
            }
            return@withContext
        }

        // 本次加载开始前先记下"此刻仍在显示的那张图"：加载期间 imageState 不动，旧封面继续显示，
        // 它同时也是加载遮罩（LoadingPhase.source）的归属对象；
        // 只有真的拿到新图才会替换它，失败/超时则走 imageState = null 的占位背景。
        val lastDisplayed = imageState.value
        logger.i {
            "开始加载封面 data=${imageData::class.simpleName} 当前显示=${lastDisplayed != null}"
        }

        val paletteFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(400)
                .build()

            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.fetchWithRetry(request)

            ensureActive()

            val image = result.image
            val color = image?.toBitmap()?.toImageBitmap()
                ?.themeColorOrNull(maxColors = 8)
                ?: Color.DarkGray

            ensureActive()

            bgColorState.value = color
            contentColorState.value = Color.White.compositeOver(color)
        }

        val coverFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(size)
                .build()

            ensureActive()

            val imageLoader = SingletonImageLoader.get(context)

            // 歌曲切换后的加载分两条路径，动效按三个时间节点表达加载状态：
            // 1. 快路径：本地图片或已缓存，通常 150ms 内完成 —— 只做淡入 + 模糊切换，不出现加载提示；
            // 2. 慢路径：需要网络加载（这里不关心它的实现），通常超过 150ms；
            //    - 150ms 仍未完成：旧封面变糊并淡入一层白色遮罩（LoadingBlur / veilAlpha）；
            //    - 10s 内完成：新图带着模糊淡入并逐渐清晰，旧封面与遮罩随后淡出；
            //    - 超过 10s：放弃本次加载，切到占位背景。
            // 请求只在这里发起，计时与相位判断全部交给下面的 fastPath / slowPath。
            // 窗口内的失败会重试：真正决定"没有封面"的是加载超时，不是第一次请求的结果。
            imageLoader.fetchWithRetry(request)
        }

        // 150ms 的观察窗口只负责把视觉切到"加载中"：它既不取消、也不打断图片请求，
        // 请求本身继续在 coverFetch 里跑。这里刻意不使用独立的计时协程，
        // 否则计时器与下面的超时会同时到期，两个协程谁后写 loadingState 是不确定的。
        val fastPath = withTimeoutOrNull(FastPathThreshold) { coverFetch.await() }
        if (fastPath == null) {
            loadingState.value = LoadingPhase(lastDisplayed)
        }

        // withTimeoutOrNull 只会取消"等待者"，不会取消 coverFetch 本身，所以这次超时判断不会
        // 掐掉已经在进行的网络请求，慢路径依然有机会在 10s 内返回。
        val slowPath = fastPath
            ?: withTimeoutOrNull(LoadTimeout - FastPathThreshold) { coverFetch.await() }

        // 超时即放弃本次加载（按约定失败态保持到下次切歌）。
        // 若慢路径恰好与超时同一刻完成，coverFetch 已经完成，这里补一次等待即可立即拿到结果。
        val result =
            fastPath ?: slowPath ?: if (coverFetch.isCompleted) coverFetch.await() else null
        if (result == null) coverFetch.cancel()

        val bitmap = (result as? SuccessResult)
            ?.image
            ?.toBitmap()
            ?.toImageBitmap()

        loadingState.value = null
        if (bitmap != null) {
            logger.i { "封面就绪，替换当前封面（VM=#$instanceId）" }
            imageState.value = bitmap
        } else {
            // 重试到超时仍拿不到：记下失败，但**不清空已经显示的封面**。
            // 清空会让旧封面直接消失（换成占位背景），等下一张图到达再淡入——用户看到的就是
            // "旧封面没了，然后新封面突然出现"，过渡动画等于没有。占位背景只在本来就没图
            // （冷启动）时才有意义，那本来就是 null，不用我们再清一次。
            val failure = (result as? ErrorResult)?.throwable ?: CoverLoadTimeoutException()
            logger.w(
                messageString = "封面加载失败（保留当前封面）：${imageData::class.simpleName}，原因：$failure",
                throwable = failure,
            )
            errorState.value = failure
        }

        paletteFetch.await()
    }
}

/**
 * 在加载超时窗口内反复重试，直到拿到图片或窗口结束。
 *
 * 只对"明确失败/没有图"的结果重试；成功或者已经超时就返回，由调用方决定展示哪张图。
 */
private suspend fun ImageLoader.fetchWithRetry(request: ImageRequest): ImageResult {
    val startedAt = TimeSource.Monotonic.markNow()
    return retryUntilLoaded(
        deadlineMillis = LoadTimeout.inWholeMilliseconds,
        initialDelayMillis = RetryInitialDelay.inWholeMilliseconds,
        maxDelayMillis = RetryMaxDelay.inWholeMilliseconds,
        isLoaded = ImageResult::hasImage,
        nowMillis = { startedAt.elapsedNow().inWholeMilliseconds },
        attempt = { execute(request) },
    )
}

/**
 * 通用的"窗口内重试"循环：拿到 [isLoaded] 认可的结果、或窗口结束就返回最后一次结果。
 *
 * 抽出来是为了能脱离 Coil 验证重试节奏——真正容易写错的是"下一次重试会不会越过窗口"，
 * 而不是怎么发请求。时间来源由调用方注入，测试里可以直接用虚拟时钟。
 */
internal suspend fun <T> retryUntilLoaded(
    deadlineMillis: Long,
    initialDelayMillis: Long,
    maxDelayMillis: Long,
    nowMillis: () -> Long,
    isLoaded: (T) -> Boolean,
    attempt: suspend () -> T,
): T {
    val startedAt = nowMillis()
    var delayMillis = initialDelayMillis
    while (true) {
        // 取消由 attempt/delay 的挂起点自己传播，这里不必再查一次
        val result = attempt()
        if (isLoaded(result)) return result

        val elapsed = nowMillis() - startedAt
        // 下一次重试会越过窗口时就不必再等了，直接把结果交给上层
        if (elapsed >= deadlineMillis || elapsed + delayMillis >= deadlineMillis) return result

        delay(delayMillis)
        delayMillis = (delayMillis * 2).coerceAtMost(maxDelayMillis)
    }
}

private fun ImageResult?.hasImage(): Boolean = this is SuccessResult && image != null

/**
 * 全屏模糊封面背景。
 *
 * 转场身份只由 [BlurBackgroundViewModel.imageState]（当前该显示哪张图）决定：
 * 新封面淡入绘制在上层，旧封面保留到转场结束，因此加载相位的变化不会额外产生一次淡入淡出。
 * 模糊半径统一取 `max(外部 blurProgress、槽位进出模糊、加载相位模糊)`，不相加。
 *
 * 半径、压黑、遮罩这些"每帧都在变"的输入全部在**绘制期/图层期**读取
 * （`rememberUpdatedState(blurProgress)` 存的是 lambda，`animateDp`/`animateDpAsState` 存的是 State），
 * 所以拖动展开、转场、遮罩淡入淡出都只触发重绘，不会让本组件逐帧重组。
 * 可以配合 `recomposeHighlighter` 验证：这些动画期间计数不应增长，
 * 只有换图、加载相位真正变化时才会 +1。
 */
@Composable
fun DefaultBlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any?,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val context = LocalPlatformContext.current
        // 存的是 lambda 而不是它的值：真正的读取发生在绘制期，拖动展开时只重绘、不重组
        val currentBlurProgress = rememberUpdatedState(blurProgress)
        val vm = viewModel { BlurBackgroundViewModel() }
        LaunchedEffect(vm) { compositionLogger.i { "封面 VM 绑定到组合：#${vm.instanceId}" } }
        val windowInfo = LocalWindowInfo.current

        LaunchedEffect(Unit) {
            vm.bgColorState
                .onEach { onColorPairFetched(it ?: Color.DarkGray, Color.White) }
                .launchIn(this)
        }

        LaunchedEffect(imageData()) {
            // loadImage 是结构化挂起任务：连续切歌时 LaunchedEffect 会取消上一张尚未完成的解码，
            // 过期任务不会再继续争用 CPU，也不会反过来覆盖最新歌曲
            // （唯一残留的是上一次的加载相位，见 BlurBackgroundViewModel.loadingState）。
            val size = constraints.maxWidth.coerceAtMost(windowInfo.containerSize.width)
            vm.loadImage(context = context, imageData = imageData(), size = size)
        }

        // 加载中遮罩的归属引用。它必须活到淡出动画结束：如果直接读 loadingState 判断归属，
        // 加载成功的那一帧归属条件就会失效，遮罩会在仍然接近满不透明时整片消失，形成一次闪跳。
        var veilOwner by remember { mutableStateOf<ImageBitmap?>(null) }
        val veilAlpha = remember { Animatable(0f) }
        val isLoading = vm.loadingState.value != null

        LaunchedEffect(isLoading) {
            if (isLoading) {
                veilOwner = vm.loadingState.value?.source
                veilAlpha.animateTo(1f, tween(LoadingVeilFadeInMillis))
            } else {
                veilAlpha.animateTo(0f, tween(LoadingVeilFadeOutMillis))
                veilOwner = null
            }
        }

        // 加载相位带来的额外模糊。它与槽位的进出模糊取 max 而不是相加：两者语义相同（都是
        // "这张图该有多糊"），取 max 天然幂等，新封面进入时不会被叠加成双重模糊。
        // 占位背景同样吃这三个值，所以冷启动加载中时占位图标也会一起变糊。
        val loadingBlur = animateDpAsState(
            targetValue = if (isLoading) LoadingBlur else 0.dp,
            animationSpec = tween(LoadingVeilFadeOutMillis),
            label = "LoadingBlur",
        )

        AnimatedContent(
            label = "",
            modifier = Modifier.fillMaxSize(),
            targetState = vm.imageState.value,
            transitionSpec = {
                // 旧封面不淡出，并保留到新封面的淡入动画结束；新封面始终绘制在上层。
                (fadeIn(tween(500)) togetherWith ExitTransition.KeepUntilTransitionsFinished)
                    .apply { targetContentZIndex = 1f }
            }
        ) { cover ->
            // 槽位进出的模糊：新封面从 10dp 逐渐清晰，旧封面在退出阶段停在同一档 10dp
            // （与 LoadingBlur 同值，所以"加载中变糊"到"新封面进场"不会出现模糊量跳变）。
            val blurTransition = transition.animateDp(
                transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
                label = "CoverBlur",
            ) { state ->
                when (state) {
                    EnterExitState.Visible -> 0.dp
                    EnterExitState.PreEnter -> 10.dp
                    EnterExitState.PostExit -> 10.dp
                }
            }

            // 模糊半径是**绘制期**输入：三个来源（外部进度、槽位进出、加载相位）都在绘制期读取，
            // 只有真正改变转场/加载相位的重组才会走到这里，动画本身只触发重绘。
            val targetBlur: () -> Dp = remember(currentBlurProgress, blurTransition, loadingBlur) {
                {
                    maxOf(
                        (currentBlurProgress.value() * 50f).roundToInt().dp,
                        blurTransition.value,
                        loadingBlur.value,
                    )
                }
            }

            // 占位背景：冷启动还没有任何图，或本次加载失败/超时（imageState 被置 null）。
            // 与图片分支共用同一套模糊与压黑，切换时亮度不会跳；图标随 blurProgress 一起淡出。
            if (cover == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.primaryContainer)
                        // 与 Modifier.blur(Unbounded) 等价，但半径在图层阶段求值（只重绘不重组）
                        .graphicsLayer {
                            val radiusPx = targetBlur().toPx().coerceAtLeast(0f)
                            renderEffect = if (radiusPx > 0f) {
                                BlurEffect(
                                    radiusX = radiusPx,
                                    radiusY = radiusPx,
                                    edgeTreatment = TileMode.Decal,
                                )
                            } else {
                                null
                            }
                        }
                        .drawBehind {
                            drawRect(
                                color = Color.Black.copy(alpha = currentBlurProgress.value() * (100f / 255f)),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = vectorResource(RemixIcon.Media.music2Line),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                alpha = 1f - currentBlurProgress.value(); clip = false
                            },
                    )
                }
                return@AnimatedContent
            }

            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBlur(
                        scale = 0.4f,
                        radius = targetBlur,
                    )
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            color = Color.Black.copy(alpha = currentBlurProgress.value() * (100f / 255f)),
                        )
                        // 白色遮罩只归属"正在被替换的那张旧封面"。新封面拿不到归属，
                        // 因此不会出现新旧两份遮罩叠加导致的中段变亮。
                        // 这里在绘制期读取状态，遮罩淡入淡出只触发重绘，不会逐帧重组内容。
                        val owner = veilOwner
                        if (owner != null && cover === owner) {
                            drawRect(color = Color.White.copy(alpha = LoadingVeilAlpha * veilAlpha.value))
                        }
                    },
                bitmap = cover,
                contentScale = ContentScale.Crop,
                contentDescription = ""
            )
        }
    }
}
