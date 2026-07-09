package com.lalilu.lhome.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.rememberSharedMap
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecommendGroupCard(
    modifier: Modifier = Modifier,
    group: Any,
    onClick: (Any, SharedMap) -> Unit = { _, _ -> },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val title = remember(group) {
        when (group) {
            is LAudio -> group.title.ifBlank { "元素" }
            is LAlbum -> group.title.ifBlank { "元素" }
            is LArtist -> group.title.ifBlank { "元素" }
            else -> "元素"
        }
    }
    val subtitle = remember(group) {
        when (group) {
            is LAudio -> group.subtitle
            is LAlbum -> group.subtitle
            is LArtist -> group.subtitle
            else -> ""
        }
    }

    SharedContext(
        sharedMap = rememberSharedMap(
            id = title,
            keys = listOf("TITLE", "SUBTITLE")
        )
    ) {
        Column(
            modifier = modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onClick(group, sharedMap) }
                )
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (subtitle.isNotBlank()) Modifier.border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                            shape = RoundedCornerShape(4.dp)
                        ) else Modifier
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .background(MaterialTheme.colorScheme.onBackground.copy(0.05f))
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        text = title.firstOrNull()?.toString() ?: title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f),
                    model = group,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp)
                    .alpha(0.8f),
                text = title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )

            if (subtitle.isNotBlank() && subtitle != title) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp)
                        .alpha(0.6f),
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewRecommendGroupCardDefault() = preview {
    RecommendGroupCard(
        modifier = Modifier.width(150.dp),
        group = LAudio(id = "test", title = "Test", subtitle = "Test Artist"),
    )
}
