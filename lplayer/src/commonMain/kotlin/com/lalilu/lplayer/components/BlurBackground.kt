package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.Bitmap
import coil3.compose.AsyncImage
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
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

@Composable
fun DefaultBlurBackground(
    modifier: Modifier = Modifier,
    imageData: () -> Any,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float,
) {
    val blur = rememberUpdatedState(blurProgress())
    val blurRadius = remember { { ((blur.value * 50f)).roundToInt().dp } }
    val scope = rememberCoroutineScope()

    AnimatedContent(
        label = "",
        modifier = modifier
            .clipToBounds()
            .blur(radius = blurRadius(), edgeTreatment = BlurredEdgeTreatment.Unbounded),
        targetState = imageData(),
        transitionSpec = {
            fadeIn(tween(500)) togetherWith fadeOut(tween(300, 500))
        }
    ) { data ->
        AsyncImage(
            modifier = Modifier
                .fillMaxSize(),
            model = data,
            contentScale = ContentScale.Crop,
            contentDescription = "",
            onSuccess = {
                scope.launch(Dispatchers.io) {
                    val image = it.result.image
                    val color = image.toBitmap().toImageBitmap()
                        .themeColorOrNull()
                        ?: Color.DarkGray

                    onColorPairFetched(
                        color,
                        Color.White.compositeOver(color)
                    )
                }
            }
        )
    }
}