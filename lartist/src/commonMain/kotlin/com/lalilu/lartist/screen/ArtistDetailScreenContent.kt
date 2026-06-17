package com.lalilu.lartist.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.extensions.*
import com.lalilu.lartist.component.ArtistCard
import com.lalilu.lartist.viewmodel.ArtistDetailEvent
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.packed.CoverTitleHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
internal fun ArtistDetailScreenContent(
    artist: LArtist? = null,
    songs: SortResult<LAudio> = SortResult.empty(),
    sharedMap: SharedMap = emptyMap(),
    coverCacheKey: String? = null,
    eventFlow: Flow<ArtistDetailEvent> = emptyFlow(),
    keys: () -> Collection<Any> = { emptyList() },
    recorder: () -> ItemRecorder,
    selector: () -> ItemSelector<LAudio>,
    onClickGroup: (GroupId) -> Unit = {},
    onClickAddToPlaylist: () -> Unit = {},
    onClickPlayAll: () -> Unit = {},
) = SharedContext(sharedMap) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val stickyHeaderContentType = remember { "group" }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keys = keys
    )

    val statusBar = WindowInsets.statusBars
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )


    val relateArtist = remember { mutableStateListOf<LArtist>() }
    LaunchedEffect(artist) {
        val songs = artist?.ref<LAudio>() ?: emptyList()
        val actualSongs = LMedia.instance.mapBy<LAudio>(songs.map { it.idValue() })
        val artists = actualSongs.flatMap { it.ref<LArtist>() }
            .distinctBy { it.idValue() }
            .filter { it.idValue() != artist?.idValue() }

        relateArtist.clear()
        relateArtist.addAll(artists)
    }

    LaunchedEffect(Unit) {
        eventFlow.collectLatest { event ->
            when (event) {
                is ArtistDetailEvent.ScrollToItem -> {
                    scroller.animateTo(
                        key = event.key,
                        isStickyHeader = { it.contentType == stickyHeaderContentType },
                        offset = { item ->
                            if (item.contentType == stickyHeaderContentType) {
                                return@animateTo -statusBar.getTop(density)
                            }

                            val closestStickyHeaderSize = listState.layoutInfo.visibleItemsInfo
                                .lastOrNull {
                                    it.index < item.index && it.contentType == stickyHeaderContentType
                                }
                                ?.size ?: 0

                            -(statusBar.getTop(density) + closestStickyHeaderSize)
                        }
                    )
                }

                else -> {}
            }
        }
    }

    val coverData = remember(artist) {
        ImageRequest.Builder(context)
            .placeholderMemoryCacheKey(coverCacheKey)
            .data(artist)
            .build()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
        contentPadding = PaddingValues(bottom = smartBarHeight() + 16.dp),
    ) {
        startRecord(recorder()) {
            item {
                val primaryColor = MaterialTheme.colorScheme.primary
                val annotatedSubtitle = remember {
                    val title = artist?.titleValue() ?: "Unknown"
                    val subtitle = artist?.subtitleValue()?.takeIf { it.isNotBlank() }
                    buildAnnotatedString {
                        if (subtitle == null){
                            append("${songs.itemList.size} songs")
                            return@buildAnnotatedString
                        }

                        append(subtitle.substringBefore(title))
                        withStyle(SpanStyle(color = primaryColor)) { append(title) }
                        append(subtitle.substringAfter(title))
                    }
                }

                CoverTitleHeader(
                    coverData = coverData,
                    title = artist?.titleValue() ?: "Unknown Artist",
                    subtitle = "",
                    subtitleContent = {
                        Text(
                            modifier = it,
                            text = annotatedSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    extraContent = {
                        Row(modifier = it) {
                            TextButton(onClick = onClickAddToPlaylist) {
                                Text(text = "添加歌手歌曲到播放列表")
                            }
                            TextButton(onClick = onClickPlayAll) {
                                Text(text = "播放全部")
                            }
                        }
                    }
                )
            }

            songs.draw {
                groupId?.let { groupId ->
                    stickyHeader(
                        key = groupId,
                        contentType = stickyHeaderContentType
                    ) {
                        Text(
                            modifier = Modifier.animateItem(),
                            text = groupId.text
                        )
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { index, item -> item.idValue() },
                    contentType = { index, item -> item::class }
                ) { index, item ->
                    AudioItemCard(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        title = item.titleValue(),
                        subtitle = item.subtitleValue(),
                        imageData = item,
                        isSelecting = { selector().isSelecting.value },
                        isSelected = { selector().isSelected(item) },
                        onEnterSelect = { selector().onSelect(item) },
                        onSelect = { selector().onSelect(item) },
                        onPlay = {
                            scope.launch {
                                PlayerAction.UpdateList(
                                    ids = songs.itemList.map { it.idValue() },
                                    id = item.idValue(),
                                    start = true
                                ).action()
                            }
                        },
                        onNavigateToDetail = {
                            val coverMemoryKey = context.retrieveCacheKey(item)

                            AppRouter.route("/song/detail")
                                .with("mediaId", item.idValue())
                                .with("song", item)
                                .with("coverCacheKey", coverMemoryKey)
                                .jump()
                        }
                    )
                }
            }

            if (relateArtist.isNotEmpty()) {
                item(key = "EXTRA_HEADER") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "相关艺术家",
                            fontSize = 20.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                itemsIndexed(
                    items = relateArtist,
                    key = { _, item -> item.idValue() },
                    contentType = { _, _ -> LArtist::class }
                ) { _, item ->
                    ArtistCard(
                        modifier = Modifier.animateItem(),
                        artist = item,
                        sharedMapPrefix = "detail",
                        onClick = { sharedMap ->
                            val coverCacheKey = context.retrieveCacheKey(item)

                            AppRouter.route("/pages/artists/detail")
                                .with("artistId", item.idValue())
                                .with("artist", item)
                                .with("sharedMap", sharedMap)
                                .with("coverCacheKey", coverCacheKey)
                                .push()
                        }
                    )
                }
            }
        }
    }
}
