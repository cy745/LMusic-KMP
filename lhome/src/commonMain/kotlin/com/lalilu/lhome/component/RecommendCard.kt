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
import com.lalilu.lmedia.rememberMediaCoverRequest
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecommendCard(
    modifier: Modifier = Modifier,
    id: String = "",
    title: String,
    subTitle: String,
    imageData: Any = Unit,
    onClick: (SharedMap) -> Unit = {}
) {
    SharedContext(
        sharedMap = rememberSharedMap(
            id = id,
            keys = listOf(
                "COVER",
                "TITLE",
                "SUBTITLE"
            )
        )
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val coverData = rememberMediaCoverRequest(imageData)

        Column(
            modifier = modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onClick(sharedMap) }
                )
        ) {
            AsyncImage(
                modifier = Modifier
                    .sharedElementV2("COVER")
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1f.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        onClick = { onClick(sharedMap) }
                    )
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.15f)),
                contentScale = ContentScale.Crop,
                model = coverData,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(top = 8.dp)
                    .sharedBoundsV2("TITLE"),
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier.sharedBoundsV2("SUBTITLE")
                    .alpha(0.6f),
                text = subTitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun RecommendCardPreview() = preview {
    FlowRow(
        modifier = Modifier.fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat<PreviewPresets>(
            count = 9,
            key = "SONGS",
            shuffle = true
        ) {
            RecommendCard(
                modifier = Modifier.width(120.dp),
                title = stringValue("title"),
                subTitle = stringValue("subtitle"),
            )
        }
    }
}

@Preview
@Composable
private fun RecommendCardPreviewDark() = preview(isDarkMode = true) {
    FlowRow(
        modifier = Modifier.fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat<PreviewPresets>(
            count = 9,
            key = "SONGS",
            shuffle = true
        ) {
            RecommendCard(
                modifier = Modifier.width(120.dp),
                title = stringValue("title"),
                subTitle = stringValue("subtitle"),
            )
        }
    }
}

@Preview
@Composable
private fun RecommendCardPreviewLarge() = preview {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat<PreviewPresets>(
            count = 2,
            key = "SONGS",
            shuffle = true
        ) {
            item {
                RecommendCard(
                    modifier = Modifier.width(250.dp),
                    title = stringValue("title"),
                    subTitle = stringValue("subtitle"),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RecommendCardPreviewLargeDark() = preview(isDarkMode = true) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat<PreviewPresets>(
            count = 2,
            key = "SONGS",
            shuffle = true
        ) {
            item {
                RecommendCard(
                    modifier = Modifier.width(250.dp),
                    title = stringValue("title"),
                    subTitle = stringValue("subtitle"),
                )
            }
        }
    }
}
