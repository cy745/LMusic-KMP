package com.lalilu.lhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.lmedia.entity.LAudio

@Composable
fun RecommendCard2(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    item: () -> LAudio,
    onClick: () -> Unit = {}
) {
    val song = remember { item() }

    RecommendCard2(
        modifier = modifier,
        contentModifier = contentModifier,
        imageData = { song },
        title = { song.title },
        subTitle = { song.subtitle },
        onClick = onClick
    )
}

@Composable
fun RecommendCard2(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    imageData: () -> Any?,
    title: () -> String,
    subTitle: () -> String,
    onClick: () -> Unit = {}
) {
    RecommendCardCover(
        modifier = modifier.width(IntrinsicSize.Min),
        contentModifier = contentModifier,
        imageData = imageData,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        0.5f to Color.Transparent,
                        1.0f to Color.Black,
                        start = Offset.Zero,
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
                .fillMaxSize()
                .padding(20.dp)
                .align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(5.dp, alignment = Alignment.Bottom),
        ) {
            ExpendableTextCard(
                title = title,
                subTitle = subTitle,
                titleColor = Color.White,
                subTitleColor = Color.White.copy(0.8f)
            )
        }
    }
}

@Composable
fun RecommendCardCover(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    width: () -> Dp = { 150.dp },
    height: () -> Dp = { 150.dp },
    shape: Shape = RoundedCornerShape(10.dp),
    imageData: () -> Any?,
    onClick: () -> Unit = {},
    extraContent: @Composable BoxScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier,
        tonalElevation = elevation,
        shape = shape
    ) {
        Box(
            modifier = contentModifier
                .width(width())
                .height(height())
                .background(color = MaterialTheme.colorScheme.onBackground.copy(0.15f))
        ) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageData())
//                    .placeholder(R.drawable.ic_music_2_line_100dp)
//                    .error(R.drawable.ic_music_2_line_100dp)
//                    .requirePalette { palette = it }
                    .build(),
                contentScale = ContentScale.Crop,
                contentDescription = "Recommend Card Cover Image"
            )
            extraContent()
        }
    }
}

