package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
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

    fun loadImage(context: PlatformContext, imageData: Any) = viewModelScope.launch {
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
    val blurRadius = remember { { ((blur.value * 50f)).roundToInt().dp } }
    val vm = viewModel<BlurBackgroundViewModel>()

    LaunchedEffect(Unit) {
        vm.bgColorState
            .onEach { onColorPairFetched(it ?: Color.DarkGray, Color.White) }
            .launchIn(this)
    }

    LaunchedEffect(imageData()) {
        vm.loadImage(context, imageData())
    }

    AnimatedContent(
        label = "",
        modifier = modifier
            .clipToBounds()
            .blur(radius = blurRadius(), edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .drawWithContent {
                drawContent()
                drawRect(color = Color.Black.copy(alpha = blurProgress() * (100f / 255f)))
            },
        targetState = vm.imageState.value,
        transitionSpec = {
            fadeIn(tween(500)) togetherWith fadeOut(tween(300, 500))
        }
    ) { data ->
        if (data == null) {
            Spacer(modifier = Modifier.fillMaxSize())
            return@AnimatedContent
        }

        Image(
            modifier = Modifier.fillMaxSize(),
            bitmap = data,
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
    }
}