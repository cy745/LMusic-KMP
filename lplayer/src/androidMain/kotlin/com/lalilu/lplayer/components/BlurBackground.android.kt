package com.lalilu.lplayer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Bitmap

actual fun Bitmap.toImageBitmap(): ImageBitmap = asImageBitmap()

@Composable
actual fun BlurBackground(
    modifier: Modifier,
    imageData: () -> Any,
    onColorPairFetched: (bgColor: Color, contentColor: Color) -> Unit,
    blurProgress: () -> Float
) {
    DefaultBlurBackground(modifier, imageData, onColorPairFetched, blurProgress)
}