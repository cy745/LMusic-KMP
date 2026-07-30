package com.lalilu.lplaylist.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.lplaylist.component.PlaylistCard
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.lplaylist.generated.resources.Res
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_screen_title
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.smartbar.NavigatorHeader
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun PlaylistScreenContent(
    modifier: Modifier = Modifier,
    isSearching: () -> Boolean = { true },
    onStartSearch: () -> Unit = {},
    isSelecting: () -> Boolean = { true },
    isSelected: (LPlaylist) -> Boolean = { false },
    playlists: () -> List<LPlaylist> = { emptyList() },
    onUpdatePlaylist: (List<LPlaylist>) -> Unit = {},
    onClickPlaylist: (LPlaylist) -> Unit = {},
    onLongClickPlaylist: (LPlaylist) -> Unit = {}
) {
    val listState: LazyListState = rememberLazyListState()
    val playlistState = remember(playlists()) {
        playlists().toMutableStateList()
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState
    ) { from, to ->
        playlistState.toMutableList().apply {
            val toIndex = indexOfFirst { it.id == to.key }
            val fromIndex = indexOfFirst { it.id == from.key }
            if (toIndex < 0 || fromIndex < 0) return@rememberReorderableLazyListState

            add(toIndex, removeAt(fromIndex))
            playlistState.clear()
            playlistState.addAll(this)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "HEADER") {
            NavigatorHeader(
                modifier = Modifier.statusBarsPadding(),
                title = stringResource(Res.string.playlist_screen_title),
                subTitle = "长按后拖拽调整歌单显示顺序",
                rowExtraSpace = 8.dp,
                extraContent = rowExtra@{
                    TextButton(
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f),
                        ),
                        onClick = {
                            AppRouter.route("/pages/playlist/edit")
                                .push()
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = vectorResource(RemixIcon.System.addLargeFill),
                            contentDescription = "新建歌单",
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "新建歌单")
                    }

                    Box {
                        IconButton(onClick = onStartSearch) {
                            Icon(
                                imageVector = vectorResource(RemixIcon.System.search2Line),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        this@rowExtra.AnimatedVisibility(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(8.dp, 8.dp),
                            enter = fadeIn(),
                            exit = fadeOut(),
                            visible = isSearching()
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(color = Color.Red)
                                    .size(8.dp)
                            )
                        }
                    }
                }
            )
        }

        items(
            items = playlistState,
            key = { it.id },
            contentType = { LPlaylist::class }
        ) { playlist ->
            ReorderableItem(
                state = reorderableState,
                key = playlist.id
            ) { isDragging ->
                PlaylistCard(
                    playlist = playlist,
                    draggingModifier = Modifier.draggableHandle(
                        onDragStopped = { onUpdatePlaylist(playlistState) }
                    ),
                    isDragging = { isDragging },
                    isSelected = { isSelected(playlist) },
                    isSelecting = isSelecting,
                    onClick = onClickPlaylist,
                    onLongClick = onLongClickPlaylist
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistScreenContentPreview() {
    MaterialTheme {
        PlaylistScreenContent(
            playlists = {
                buildList<LPlaylist> {
                    repeat(10) {
                        add(
                            LPlaylist(
                                id = "$it",
                                title = "Playlist $it",
                                subTitle = "Subtitle $it",
                                coverUri = "",
                                mediaIds = listOf("", "")
                            )
                        )
                    }
                }
            }
        )
    }
}