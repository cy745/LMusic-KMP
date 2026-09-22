package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
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
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

expect fun Bitmap.toImageBitmap(): ImageBitmap

/** 模糊背景的解码尺寸上限（px）：它最终会被整体模糊，超过这个尺寸肉眼和性能都不划算。 */
private const val BLUR_BACKGROUND_MAX_DECODE_PX = 720

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

    suspend fun loadImage(
        context: PlatformContext,
        imageData: Any,
        size: Int = 1200
    ) = withContext(Dispatchers.Unconfined) {
        val paletteFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(400)
                .build()

            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

            ensureActive()

            // 取不到图时**不要**退回中性色：那会让底色当着用户的面跳一下。
            // 拿不到就保持上一次的配色，等真的解析出来再换。
            val color = result.image?.toBitmap()?.toImageBitmap()
                ?.themeColorOrNull(maxColors = 8)

            ensureActive()

            if (color != null) {
                bgColorState.value = color
                contentColorState.value = Color.White.compositeOver(color)
            }
        }

        val coverFetch = async(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData)
                .size(size)
                .build()

            ensureActive()

            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

            ensureActive()

            // ★ 只在**成功**时写入。
            // 之前这里是 `imageState.value = result.image?...`：失败会把上一张抹成 null，
            // 于是 AnimatedContent 只能画占位/空 → 用户看到的"先闪一下底色再切到新封面"。
            // 下面 AnimatedContent 的 transitionSpec 本来就是为"旧封面保留、新封面淡入"写的，
            // 把这一行改成只在成功时写入，那个淡入才真正生效。
            val bitmap = result.image
                ?.toBitmap()
                ?.toImageBitmap()

            if (bitmap != null) {
                imageState.value = bitmap
            }
        }

        paletteFetch.await()
        coverFetch.await()
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
            // 只在真的解析出配色时才回调：`it ?: Color.DarkGray` 会让底色在失败时跳成中性灰
            vm.bgColorState
                .filterNotNull()
                .onEach { onColorPairFetched(it, Color.White) }
                .launchIn(this)
        }

        LaunchedEffect(imageData()) {
            // loadImage 是结构化挂起任务：连续切歌时 LaunchedEffect 会取消上一张尚未完成的
            // 解码和首帧准备，不再让过期任务继续争用 CPU，或反过来覆盖最新歌曲。
            //
            // 解码尺寸必须封顶：这是一张会被整体模糊铺满窗口的背景，按窗口宽度取意味着全屏下
            // 动辄两三千像素的大图，而模糊/合成是逐帧做的，代价随之翻几倍。
            val size = minOf(
                constraints.maxWidth,
                windowInfo.containerSize.width,
                BLUR_BACKGROUND_MAX_DECODE_PX,
            )
            vm.loadImage(context = context, imageData = imageData(), size = size)
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
                // 首次还没有任何封面（不是切歌）：给一个明确的底色占位，而不是一片空。
                // 用主题色做一层渐变，读起来是"背景正在准备"，而不是"这里坏了"。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surface,
                                )
                            )
                        )
                )
                return@AnimatedContent
            }

            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBlur(
                        scale = 0.4f,
                        // 半径按 1/8 量化：模糊是逐帧的重活，半径连续变化等于每帧都重算一遍。
                        // 8 档视觉上看不出跳变，却把重算次数压到零头——面板拖拽时尤其明显。
                        radius = ((blur.value * 8f).roundToInt() / 8f * 50f).roundToInt().dp,
                    )
                    .drawWithContent {
                        drawContent()
                        drawRect(color = Color.Black.copy(alpha = blur.value * (100f / 255f)))
                    },
                bitmap = cover,
                contentScale = ContentScale.Crop,
                contentDescription = ""
            )
        }
    }
}
