package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.Bitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.lalilu.lplayer.debug.PlayerLoadTestHarness
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 等待门槛：超过它仍未解码，才把旧封面当作离场残影。
 *
 * 等待态的表达方式（2026-10-04 评审确认）：
 * - 旧封面只做**一次** 180ms 静态失焦，之后 blur 数值不再变化。原先让 blur 在 16↔22 之间
 *   呼吸，半径一变，裁剪边上的透出量跟着变，观感就是封面边缘在闪。
 * - 等待感改由一层白色遮罩的透明度呼吸承担（[CoverMaskMinAlpha] ↔ [CoverMaskMaxAlpha]）。
 * - 失焦层按 [CoverWaitingScale] 放大，让模糊的透明溢出落在容器之外；
 *   解析（新图由模糊到清晰）时缩放与解糊同步收缩，保证每帧留白都大于等于当前 blur 半径，
 *   否则解析头几帧的边缘会透出下面的旧图。
 */
private const val CoverWaitingGateMillis = 150L
private const val CoverDefocusRadius = 16f

/** 旧封面进入等待失焦的时长；放大与失焦共用同一条曲线。 */
private const val CoverDefocusMillis = 180
private const val CoverWaitingScale = 1.09f
private const val CoverArrivalRadius = 18f
private const val CoverArrivalScale = 1.10f
private const val CoverResolveMillis = 500
private const val CoverMaskMinAlpha = 0.30f
private const val CoverMaskMaxAlpha = 0.45f

/** 单程时长；配合 [RepeatMode.Reverse] 构成 2.4s 一个来回的呼吸。 */
private const val CoverMaskBreatheMillis = 1200
private const val CoverFailedRadius = 16f

expect fun Bitmap.toImageBitmap(): ImageBitmap

@Composable
expect fun BlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
)

class BlurBackgroundViewModel : ViewModel() {
    val imageState = mutableStateOf<ImageBitmap?>(null)
    val bgColorState = MutableStateFlow<Color?>(null)
    val contentColorState = MutableStateFlow<Color?>(null)

    /** 150ms 仍未解码：旧封面进入静态失焦，等待感交给白色遮罩呼吸。 */
    val waiting = mutableStateOf(false)

    /** 解码失败：保持中性占位，主题沿用最后一次稳定色。 */
    val failed = mutableStateOf(false)

    /** 资源确实迟到过（不是缓存快路径）：到达后需要由模糊到清晰。 */
    val slowArrival = mutableStateOf(false)

    suspend fun loadImage(
        context: PlatformContext,
        imageData: Any,
        size: Int = 1200
    ) = coroutineScope {
        failed.value = false
        waiting.value = false
        slowArrival.value = false
        val test = PlayerLoadTestHarness.profile.value
        val gate = launch {
            delay(CoverWaitingGateMillis)
            waiting.value = true
            slowArrival.value = true
        }

        val paletteFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(400)
                .build()

            if (test.enabled) delay(test.paletteDelayMs)
            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

            ensureActive()

            val image = result.image
            image?.toBitmap()?.toImageBitmap()?.themeColorOrNull(maxColors = 8)
        }

        val coverFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(size)
                .build()

            ensureActive()

            if (test.enabled) delay(test.coverDelayMs)
            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

            ensureActive()

            val decoded = result.image?.toBitmap()?.toImageBitmap()
            ensureActive()

            withContext(Dispatchers.Main.immediate) {
                ensureActive()
                gate.cancel()
                failed.value = decoded == null
                if (decoded == null) slowArrival.value = true
                waiting.value = false
                if (decoded != null) imageState.value = decoded
            }
            decoded
        }

        val image = coverFetch.await()
        val color = paletteFetch.await()
        // 取色只在当前封面成功后提交：旧曲主题不得回写；取色失败保留最后稳定主题。
        if (image != null && color != null) {
            bgColorState.value = color
            contentColorState.value = Color.White.compositeOver(color)
        }
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
    val testProfile by PlayerLoadTestHarness.profile.collectAsState()
    val latestImageData by rememberUpdatedState(imageData())

    // 白色遮罩的呼吸：只动 alpha，不牵动任何模糊半径。
    // 旧封面的失焦由每张图自己的 defocus 动画负责（见下方内容 lambda），这里不再集中驱动，
    // 否则失焦会在 waiting 翻转的瞬间跳变，且离场旧图无法保持住已失焦的状态。
    val maskAlpha = remember { Animatable(0f) }
    LaunchedEffect(vm.waiting.value) {
        if (vm.waiting.value) {
            maskAlpha.animateTo(CoverMaskMinAlpha, tween(CoverDefocusMillis))
            maskAlpha.animateTo(
                targetValue = CoverMaskMaxAlpha,
                animationSpec = infiniteRepeatable(
                    tween(CoverMaskBreatheMillis, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
            )
        } else {
            maskAlpha.snapTo(0f)
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        LaunchedEffect(Unit) {
            vm.bgColorState
                .filterNotNull()
                .onEach { onColorPairFetched(it, Color.White) }
                .launchIn(this)
        }

        LaunchedEffect(latestImageData, testProfile) {
            // loadImage 是结构化挂起任务：连续切歌时 LaunchedEffect 会取消上一张尚未完成的
            // 解码和首帧准备，不再让过期任务继续争用 CPU，或反过来覆盖最新歌曲。
            val size = constraints.maxWidth.coerceAtMost(windowInfo.containerSize.width)
            vm.loadImage(context = context, imageData = latestImageData, size = size)
        }

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

            val isOutgoing = cover !== vm.imageState.value
            // 新图一律由模糊到清晰（快慢路径一致）：blur 18dp→0 与缩放 1.10→1 同步收缩。
            val entryBlur = remember(cover) { Animatable(CoverArrivalRadius) }
            val entryScale = remember(cover) { Animatable(CoverArrivalScale) }
            LaunchedEffect(cover) {
                coroutineScope {
                    launch { entryBlur.animateTo(0f, tween(CoverResolveMillis)) }
                    launch { entryScale.animateTo(1f, tween(CoverResolveMillis)) }
                }
            }

            // 等待失焦：每张图各自持有，用 180ms 渐变而不是跳变。
            // 覆盖「正在等待的这一张」与「等待过、正在离场的旧图」：后者保持住已失焦的值，
            // 直到新图清晰后退出，避免交接瞬间突然变清晰或突然缩回。
            val shouldDefocus = vm.waiting.value || (isOutgoing && vm.slowArrival.value)
            val defocus = remember(cover) { Animatable(0f) }
            LaunchedEffect(cover, shouldDefocus) {
                defocus.animateTo(
                    targetValue = if (shouldDefocus) CoverDefocusRadius else 0f,
                    animationSpec = tween(CoverDefocusMillis),
                )
            }

            // 过渡期间的额外模糊（等待失焦 / 解析解糊）。失败占位单独给值。
            val resourceBlur = when {
                vm.failed.value -> CoverFailedRadius
                else -> maxOf(entryBlur.value, defocus.value)
            }
            // 放大与失焦同步推进：既把模糊的溢出推出容器，也避免「先模糊再放大」的边界抖动。
            val waitingScale = 1f + (CoverWaitingScale - 1f) * (defocus.value / CoverDefocusRadius)
            val resourceScale = maxOf(entryScale.value, waitingScale)
            // 两级模糊合并进同一次 scaleBlur，而不是再叠一层裸 Modifier.blur。
            // scaleBlur 内部是「3σ 外扩 + TileMode.Clamp」：内容按外扩尺寸铺满后，
            // 边界外采样命中的是自身像素而不是透明区，所以不会出现边缘发白/发灰。
            // 裸 Modifier.blur 没有这层保护，边界必然透出底色——这正是之前边缘变透明的原因。
            // 平方和合并等价于「先模糊 a 再模糊 b」的等效半径；播放器自身的模糊占主导，
            // 因此在歌词页那种 50dp 场景下，过渡模糊只会带来物理上合理的轻微增强。
            val playerBlur = blur.value * 50f
            val totalBlur = sqrt(playerBlur * playerBlur + resourceBlur * resourceBlur)

            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBlur(
                        scale = 0.4f,
                        radius = totalBlur.roundToInt().dp,
                    )
                    .drawWithContent {
                        // 缩放必须放在绘制阶段，不能额外套一层 graphicsLayer：
                        // 在 scaleBlur 外面再加 graphicsLayer 会让它的模糊失效
                        // （实测：切歌后歌词页背景从 50dp 模糊变成清晰）。
                        // 绘制期变换不产生图层，scaleBlur 记录的仍是缩放后的内容。
                        if (resourceScale != 1f) {
                            scale(scaleX = resourceScale, scaleY = resourceScale) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                        drawRect(color = Color.Black.copy(alpha = blur.value * (100f / 255f)))
                    },
                bitmap = cover,
                contentScale = ContentScale.Crop,
                contentDescription = ""
            )
        }

        // 等待态的表达：一层白色遮罩呼吸。放在最上层，不参与任何模糊计算。
        if (maskAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = maskAlpha.value))
            )
        }
    }
}
