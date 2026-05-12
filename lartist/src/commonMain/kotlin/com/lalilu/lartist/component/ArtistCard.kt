package com.lalilu.lartist.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.buildSharedMap
import com.lalilu.lmedia.entity.LArtist

@Composable
fun ArtistCard(
    modifier: Modifier = Modifier,
    artist: () -> LArtist,
    showTitle: () -> Boolean = { true },
    onClick: (SharedMap) -> Unit = {}
) {
    val item = remember { artist() }

    ArtistCard(
        modifier = modifier,
        id = item.idValue(),
        imageData = artist,
        title = { item.titleValue() },
        showTitle = showTitle,
        onClick = onClick
    )
}

@Composable
fun ArtistCard(
    modifier: Modifier = Modifier,
    id: String = "",
    imageData: () -> Any?,
    title: () -> String,
    showTitle: () -> Boolean = { true },
    onClick: (SharedMap) -> Unit = {}
) = SharedContext(
    sharedMap = buildSharedMap(
        id = id,
        keys = listOf(
            "BOUND",
            "COVER",
            "TITLE",
            "SUBTITLE"
        )
    )
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        ArtistCoverCard(
            modifier = Modifier.sharedElementV2(key = "COVER"),
            imageData = imageData,
            onClick = { onClick(sharedMap) },
            interactionSource = interactionSource
        )
        ArtistTitleText(
            modifier = Modifier.sharedBoundsV2(key = "TITLE"),
            title = title,
            showTitle = showTitle,
            onClick = { onClick(sharedMap) },
            interactionSource = interactionSource
        )
    }
}

@Composable
fun ArtistTitleText(
    modifier: Modifier = Modifier,
    title: () -> String,
    showTitle: () -> Boolean = { true },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {}
) {
    androidx.compose.animation.AnimatedVisibility(
        modifier = modifier,
        visible = showTitle(),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
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

@Composable
fun ArtistCoverCard(
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
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            model = imageData(),
            contentScale = ContentScale.Crop,
            contentDescription = "Artist Cover"
        )
    }
}
