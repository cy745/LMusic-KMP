package com.lalilu.lalbum.component

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.buildSharedMap
import com.lalilu.lmedia.entity.LAlbum

@Composable
fun AlbumCard(
    modifier: Modifier = Modifier,
    album: () -> LAlbum,
    showTitle: () -> Boolean = { true },
    onClick: (SharedMap) -> Unit = {}
) {
    val item = remember { album() }

    AlbumCard(
        modifier = modifier,
        id = item.idValue(),
        imageData = album,
        title = { item.titleValue() },
        showTitle = showTitle,
        onClick = onClick
    )
}

@Composable
fun AlbumCard(
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
        AlbumCoverCard(
            modifier = Modifier.sharedElementV2(key = "COVER"),
            imageData = imageData,
            onClick = { onClick(sharedMap) },
            interactionSource = interactionSource
        )
        AlbumTitleText(
            modifier = Modifier.sharedBoundsV2(key = "TITLE"),
            title = title,
            showTitle = showTitle,
            onClick = { onClick(sharedMap) },
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

/**
 * EntryPanel Swiss Style 图片卡片：
 *  - 无阴影/elevation，用极淡边框暗示形状
 *  - 1dp 边框：闲置态 onBackground @ 0.08，按压态 primary @ 0.40
 *  - 按压时整体 alpha → 0.85，叠一层 primary @ 0.06 的覆层
 *  - 8dp 圆角，200ms 过渡
 */
@Composable
fun AlbumCoverCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    imageData: () -> Any?,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        label = "album-cover-press-alpha"
    )

    val borderColor = if (isPressed)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .alpha(pressedAlpha)
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxWidth(),
            model = imageData(),
            contentScale = ContentScale.Crop,
            contentDescription = "Album Cover"
        )

        // 按压时极淡的 primary 染色覆层
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            )
        }
    }
}