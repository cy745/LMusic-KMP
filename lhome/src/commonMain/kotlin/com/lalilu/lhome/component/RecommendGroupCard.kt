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
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.Linkable
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.ref
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecommendGroupCard(
    modifier: Modifier = Modifier,
    group: LItem,
    onClick: (LItem, SharedMap) -> Unit = { _, _ -> },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val linkable = group as? Linkable
    val items = linkable?.ref<LAudio>() ?: emptyList()
    val rowCount = remember(group) {
        when {
            items.size == 1 -> 1
            items.size <= 4 -> 2
            items.size >= 9 -> 3
            else -> 3
        }
    }
    val title = remember(group) {
        group.titleValue().ifBlank { "元素" }
    }
    val subtitle = remember(group) {
        group.subtitleValue().ifBlank { "共 ${items.size} 首歌曲" }
    }

    SharedContext(
        sharedMap = rememberSharedMap(
            id = group.idValue(),
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.05f))
                    .clickable(
                        interactionSource = interactionSource,
                        onClick = { onClick(group, sharedMap) }
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(rowCount) { row ->
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(rowCount) { column ->
                            val item = items.getOrNull(row * rowCount + column)

                            if (item != null) {
                                RecommendGroupItemCard(
                                    modifier = modifier.weight(1f),
                                    item = item,
                                    onClick = onClick
                                )
                            } else {
                                Spacer(
                                    modifier = Modifier.weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }

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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecommendGroupItemCard(
    modifier: Modifier = Modifier,
    item: LItem,
    onClick: (LItem, SharedMap) -> Unit = { _, _ -> }
) {
    SharedContext(
        sharedMap = rememberSharedMap(
            id = item.idValue(),
            keys = listOf("COVER")
        )
    ) {
        AsyncImage(
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1f.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = { onClick(item, sharedMap) })
                .background(MaterialTheme.colorScheme.onBackground.copy(0.15f))
                .sharedElementV2("COVER"),
            contentScale = ContentScale.Crop,
            model = item,
            contentDescription = item.titleValue()
        )
    }
}

@Preview
@Composable
private fun RecommendGroupCardPreview() = preview {
    val audios = remember {
        dataContext.filterIsInstance<PreviewPresets>()
            .mapIndexed { index, preset ->
                LAudio(
                    id = index.toString(),
                    title = preset.stringValue("title"),
                    subtitle = preset.stringValue("subtitle"),
                    mediaSourceName = ""
                )
            }
    }
    val group1 = remember {
        val firstItem = audios.randomOrNull()
        object : LItem, Linkable {
            override val refs: MutableMap<kotlin.reflect.KClass<*>, MutableSet<Linkable>> = mutableMapOf()
            override fun idValue(): String = firstItem?.idValue() ?: ""
            override fun titleValue(): String = firstItem?.titleValue() ?: ""
            override fun subtitleValue(): String = firstItem?.subtitleValue() ?: ""
            override fun extraValue(): Map<String, String> = emptyMap()
        }.also { linkable ->
            audios.take(4).forEach { linkable.link(it) }
        }
    }
    val group2 = remember {
        val firstItem = audios.randomOrNull()
        object : LItem, Linkable {
            override val refs: MutableMap<kotlin.reflect.KClass<*>, MutableSet<Linkable>> = mutableMapOf()
            override fun idValue(): String = firstItem?.idValue() ?: ""
            override fun titleValue(): String = firstItem?.titleValue() ?: ""
            override fun subtitleValue(): String = firstItem?.subtitleValue() ?: ""
            override fun extraValue(): Map<String, String> = emptyMap()
        }.also { linkable ->
            audios.take(5).forEach { linkable.link(it) }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RecommendGroupCard(
                modifier = Modifier.width(250.dp),
                group = group1
            )
        }

        item {
            RecommendGroupCard(
                modifier = Modifier.width(250.dp),
                group = group2
            )
        }
    }
}
