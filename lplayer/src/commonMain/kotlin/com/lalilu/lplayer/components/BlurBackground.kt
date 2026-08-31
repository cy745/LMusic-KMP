package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

    suspend fun loadImage(
        context: PlatformContext,
        imageData: Any
    ) = withContext(Dispatchers.Unconfined) {
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
                .size(1200)
                .build()

            ensureActive()

            val imageLoader = SingletonImageLoader.get(context)
            val result = imageLoader.execute(request)

            ensureActive()

            imageState.value = result.image
                ?.toBitmap()
                ?.toImageBitmap()
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

    LaunchedEffect(Unit) {
        vm.bgColorState
            .onEach { onColorPairFetched(it ?: Color.DarkGray, Color.White) }
            .launchIn(this)
    }

    LaunchedEffect(imageData()) {
        // loadImage 是结构化挂起任务：连续切歌时 LaunchedEffect 会取消上一张尚未完成的
        // 解码和首帧准备，不再让过期任务继续争用 CPU，或反过来覆盖最新歌曲。
        vm.loadImage(context, imageData())
    }

    Box(modifier = modifier.clipToBounds()) {
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

            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .scaleBlur(
                        scale = 0.5f,
                        radius = (blur.value * 50f).roundToInt().dp,
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
