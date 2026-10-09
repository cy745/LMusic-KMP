package com.lalilu.lplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.Bitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.lalilu.RemixIcon
import com.lalilu.common.ext.io
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

expect fun Bitmap.toImageBitmap(): ImageBitmap

@Composable
expect fun BlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
)

/** 快路径阈值：150ms 内加载完成就只是一次普通的淡入 + 模糊切换，不出现任何加载中提示。 */
private val FastPathThreshold = 150.milliseconds

/** 加载超时：超过它仍未拿到图片就取消本次请求并进入失败态。 */
private val LoadTimeout = 10_000.milliseconds

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

/** 加载超时的记录对象；只用于 [BlurBackgroundViewModel.errorState] 的可观测性，不会被抛出。 */
private class CoverLoadTimeoutException : Exception("cover load timed out")

/**
 * 封面加载相位。
 *
 * 它与 [BlurBackgroundViewModel.imageState]（"当前该显示哪张图"）是正交的两件事：
 * 相位只驱动加载中/失败的附加视觉，不参与 [AnimatedContent] 的转场身份，所以相位的来回变化
 * 不会产生任何多余的淡入淡出——转场始终只由"位图换了没"触发。
 *
 * @param source 进入加载时仍在显示的那张旧封面，用来判定"遮罩归属哪张图"。
 *               冷启动时没有旧封面，这里为 null，也就没有可归属的遮罩。
 */
data class LoadingPhase(val source: ImageBitmap?)

class BlurBackgroundViewModel : ViewModel() {
    /** 已成功加载、当前该显示的封面；null 表示还没有任何可显示的封面。 */
    val imageState = mutableStateOf<ImageBitmap?>(null)

    /** 非空表示正在加载中（[LoadingPhase.source] 是此刻仍在显示的旧封面）。 */
    val loadingState = mutableStateOf<LoadingPhase?>(null)

    /** 非空表示本次加载失败，保持到下一次 [loadImage] 开始。 */
    val errorState = mutableStateOf<Throwable?>(null)

    val bgColorState = MutableStateFlow<Color?>(null)
    val contentColorState = MutableStateFlow<Color?>(null)

    suspend fun loadImage(
        context: PlatformContext,
        imageData: Any,
        size: Int = 1200
    ) = withContext(Dispatchers.Unconfined) {
        errorState.value = null

        // 本次加载开始前先记下"此刻仍在显示的那张图"：加载期间继续显示它，加载失败时也由它兜底，
        // 不会像以前那样在失败时把它清成 null，导致背景闪空。
        val lastDisplayed = imageState.value

        val paletteFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(400)
                .build()

            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

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

            // 外部歌曲发生变化后，理论上会有两个路径：
            // 1. 快路径，本地加载图片或者之前加载的图片已经缓存，通常能在150ms内完成加载
            // 2. 慢路径，需要网络加载的图片（我们这里不考虑它的加载实现），只考虑它的耗时通常会超过150ms
            //
            // 针对两个路径，快路径直接就fadeIn驱动blur快速切换即可；
            // 主要问题在于慢路径，我希望要能以三个时间节点来驱动动效来及时传递表达加载中状态的信息：
            // 大致的思路是150ms时如果还未加载成功则让旧封面变模糊并渐变出现一个白色透明的遮罩，
            // 设置10s的超时时间，如果10s内加载成功则继续blur状态的新图片的渐入且逐渐变清晰的动画，
            // 如果超出10s仍未加载成功则显示异常提示背景
            delay(1000.milliseconds)
            imageLoader.execute(request)
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
        val result = fastPath ?: slowPath
        ?: if (coverFetch.isCompleted) coverFetch.await() else null
        if (result == null) coverFetch.cancel()

        val bitmap = (result as? SuccessResult)
            ?.image
            ?.toBitmap()
            ?.toImageBitmap()

        loadingState.value = null
        if (bitmap != null) {
            imageState.value = bitmap
        } else {
            errorState.value = (result as? ErrorResult)?.throwable ?: CoverLoadTimeoutException()
        }

        paletteFetch.await()
    }
}

@Composable
fun DefaultBlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
) {
    val context = LocalPlatformContext.current
    val blur = rememberUpdatedState(blurProgress())
    val vm = viewModel { BlurBackgroundViewModel() }
    val windowInfo = LocalWindowInfo.current

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        LaunchedEffect(Unit) {
            vm.bgColorState
                .onEach { onColorPairFetched(it ?: Color.DarkGray, Color.White) }
                .launchIn(this)
        }

        LaunchedEffect(imageData()) {
            // loadImage 是结构化挂起任务：连续切歌时 LaunchedEffect 会取消上一张尚未完成的
            // 解码和首帧准备，不再让过期任务继续争用 CPU，或反过来覆盖最新歌曲。
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
        val loadingBlur by animateDpAsState(
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
            if (cover == null) {
                Spacer(modifier = Modifier.fillMaxSize())
                return@AnimatedContent
            }

            val blurTransition by transition.animateDp(
                transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
                label = "CoverBlur",
            ) { state ->
                when (state) {
                    EnterExitState.Visible -> 0.dp
                    EnterExitState.PreEnter -> 10.dp
                    EnterExitState.PostExit -> 10.dp
                }
            }

            val targetBlur = maxOf(
                (blur.value * 50f).roundToInt().dp,
                blurTransition,
                loadingBlur,
            )

            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBlur(
                        scale = 0.4f,
                        radius = targetBlur,
                    )
                    .drawWithContent {
                        drawContent()
                        drawRect(color = Color.Black.copy(alpha = blur.value * (100f / 255f)))
                        // 白色遮罩只归属"正在被替换的那张旧封面"。新封面拿不到归属，
                        // 因此不会出现新旧两份遮罩叠加导致的中段变亮。
                        // 这里在绘制期读取状态，遮罩淡入淡出只触发重绘，不会逐帧重组内容。
                        val owner = veilOwner
                        if (owner != null && cover === owner) {
                            drawRect(
                                color = Color.White.copy(
                                    alpha = LoadingVeilAlpha * veilAlpha.value,
                                )
                            )
                        }
                    },
                bitmap = cover,
                contentScale = ContentScale.Crop,
                contentDescription = ""
            )
        }

        // 加载失败：旧封面保持不动，在其上方浮出主题色背景与居中的音符提示。
        val error = vm.errorState.value
        val errorAlpha by animateFloatAsState(
            targetValue = if (error != null) 1f else 0f,
            animationSpec = tween(LoadingVeilFadeOutMillis),
            label = "ErrorAlpha",
        )
        if (errorAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = errorAlpha }
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vectorResource(RemixIcon.Media.music2Line),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}
