package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import coil3.Bitmap
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    LaunchedEffect(imageData()) {
        withContext(Dispatchers.io) {
            val request = ImageRequest.Builder(context)
                .data(imageData())
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

            onColorPairFetched(
                color,
                Color.White.compositeOver(color)
            )
        }
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
        targetState = imageData(),
        transitionSpec = {
            fadeIn(tween(500)) togetherWith fadeOut(tween(300, 500))
        }
    ) { data ->
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = data,
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
    }
}