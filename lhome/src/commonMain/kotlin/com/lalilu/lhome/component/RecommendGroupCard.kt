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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.rememberSharedMap
import com.lalilu.lmedia.entity.*
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecommendGroupCard(
    modifier: Modifier = Modifier,
    group: LGroupItem,
    onClick: (LItem, SharedMap) -> Unit = { _, _ -> },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowCount = remember(group) {
        when {
            group.items.size == 1 -> 1
            group.items.size <= 4 -> 2
            group.items.size >= 9 -> 3
            else -> 3
        }
    }
    val title = remember(group) {
        if (group.title.isNotBlank()) return@remember group.title

        when (group) {
            is LAlbum -> "专辑"
            is LArtist -> "歌手"
            is LGenre -> "曲风"
            is LFolder -> "文件夹"
            else -> "元素"
        }
    }
    val subtitle = remember(group) {
        if (group.subtitle.isNotBlank()) return@remember group.subtitle

        when (group) {
            is LAlbum -> "专辑: 共 ${group.itemsCount} 首歌曲"
            is LArtist -> "歌手: 共 ${group.itemsCount} 首歌曲"
            is LGenre -> "曲风: 共 ${group.itemsCount} 首歌曲"
            is LFolder -> "文件夹: 共 ${group.itemsCount} 首歌曲"
            else -> "共 ${group.items.size} 首歌曲"
        }
    }

    SharedContext(
        sharedMap = rememberSharedMap(
            id = group.id,
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
                            val item = group.items.getOrNull(row * rowCount + column)

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
                    .sharedElementV2("TITLE"),
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier.alpha(0.6f)
                    .sharedElementV2("SUBTITLE"),
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
            id = item.id,
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
            model = item,
            contentDescription = item.title
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

        object : LGroupItem {
            override val items = audios.take(4)
            override val id: String = firstItem?.id ?: ""
            override val title: String = firstItem?.title ?: ""
            override val subtitle: String = firstItem?.subtitle ?: ""
            override val extra: Map<String, String> = emptyMap()
        }
    }
    val group2 = remember {
        val firstItem = audios.randomOrNull()

        object : LGroupItem {
            override val items = audios.take(5)
            override val id: String = firstItem?.id ?: ""
            override val title: String = firstItem?.title ?: ""
            override val subtitle: String = firstItem?.subtitle ?: ""
            override val extra: Map<String, String> = emptyMap()
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