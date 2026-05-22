package com.lalilu.lplayer.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.lalilu.extensions.Item
import com.lalilu.extensions.diff
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.SongCard
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.LocalModalBottomSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun PlaylistLayout(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    forceRefresh: () -> Boolean = { false },
    items: Flow<List<LAudio>>
) {
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    var actualItems by remember { mutableStateOf(emptyList<Item<LAudio>>()) }
    val isPlaying = LPlayer.instance.isPlaying.collectAsState(false)
    val bottomSheetState = LocalModalBottomSheetState.current

    LaunchedEffect(Unit) {
        items.collect { list ->
            val newList = actualItems.diff(
                items = list,
                getId = { it.idValue() },
                isSameItem = { a, b -> a.idValue() == b.idValue() },
                isSameContent = { a, b ->
                    a.idValue() == b.idValue()
                            && a.title == b.title
                            && a.subtitle == b.subtitle
                            && a.mediaSourceName == b.mediaSourceName
                }
            )
            val newListFirst = newList.firstOrNull()
            val oldListFirst = actualItems.firstOrNull()

            // 若无法获取新列表的首元素，则说明新列表为空，及时返回
            if (newListFirst == null) {
                actualItems = emptyList()
                return@collect
            }

            // 判断新列表的首元素是否处于可视范围内
            val isNewListTopVisible = listState.layoutInfo.visibleItemsInfo
                .any { it.key == newListFirst.key }

            // 判断旧列表的首元素是否处于可视范围内
            val isOldListTopVisible = oldListFirst?.let { item ->
                listState.layoutInfo.visibleItemsInfo
                    .any { it.key == item.key }
            } == true

            actualItems = emptyList()
            withContext(Dispatchers.Main) {
                actualItems = newList
                if (isNewListTopVisible || isOldListTopVisible || forceRefresh()) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
            .clipToBounds(),
        contentPadding = PaddingValues(bottom = 200.dp),
        overscrollEffect = null
    ) {
        itemsIndexed(
            items = actualItems,
            key = { _, item -> item.key },
        ) { index, item ->
            val bgColor = animateColorAsState(
                if (index == 0 && isPlaying.value) MaterialTheme.colorScheme.onBackground.copy(0.01f)
                else Color.Transparent
            )
            val data = item.data

            SongCard(
                modifier = Modifier
                    .animateItem()
                    .drawBehind { drawRect(color = bgColor.value) },
                id = data.idValue(),
                imageData = data,
                title = data.title,
                subtitle = data.subtitle,
                onClick = { PlayerAction.PlayById(data.idValue()).action() },
                onLongClick = { sharedMap ->
                    val coverMemoryKey = context.retrieveCacheKey(item)

                    AppRouter.route("/song/detail")
                        .with("mediaId", data.idValue())
                        .with("song", data)
                        .with("sharedMap", sharedMap)
                        .with("coverCacheKey", coverMemoryKey)
                        .jump()

                    scope.launch { bottomSheetState.show() }
                }
            )
        }
    }
}
