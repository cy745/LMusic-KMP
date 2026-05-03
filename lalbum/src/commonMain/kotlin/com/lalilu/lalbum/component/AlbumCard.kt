package com.lalilu.lalbum.component

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lalilu.animated
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref

@Composable
fun AlbumCard(
    modifier: Modifier = Modifier,
    album: () -> LAlbum,
    showTitle: () -> Boolean = { true },
    onClick: () -> Unit = {}
) {
    val item = remember { album() }
    val firstSong = remember(item) { item.ref<LAudio>().firstOrNull() }

    AlbumCard(
        modifier = modifier,
        imageData = { firstSong },
        title = { item.titleValue() },
        showTitle = showTitle,
        onClick = onClick
    )
}

@Composable
fun AlbumCard(
    modifier: Modifier = Modifier,
    imageData: () -> Any?,
    title: () -> String,
    showTitle: () -> Boolean = { true },
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        AlbumCoverCard(
            imageData = imageData,
            onClick = onClick,
            interactionSource = interactionSource
        )
        AlbumTitleText(
            title = title,
            showTitle = showTitle,
            onClick = onClick,
            interactionSource = interactionSource
        )
    }
}

@Composable
fun AlbumTitleText(
    modifier: Modifier = Modifier,
    title: () -> String,
    showTitle: () -> Boolean = { true },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {}
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = showTitle(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(vertical = 10.dp),
            text = title(),
            color = MaterialTheme.colorScheme.onBackground.copy(0.8f),
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AlbumCoverCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    imageData: () -> Any?,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(5.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Surface(
        shape = shape,
        modifier = modifier,
        shadowElevation = elevation,
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        val imageAspectRatio = remember { mutableStateOf(1f) }
        val imageAspectRatioAnimated = imageAspectRatio.animated()

        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .aspectRatio(imageAspectRatioAnimated.value),
            model = imageData(),
            contentScale = ContentScale.FillWidth,
            contentDescription = "Album Cover",
            onSuccess = { response ->
                imageAspectRatio.value = response.result.image
                    .run { width.toFloat() / height }
                    .takeIf { it > 0 }
                    ?: 1f
            }
        )
    }
}