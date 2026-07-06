/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lhistory.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhistory.component.HistoryItemCard
import com.lalilu.lhistory.entity.LHistory
import com.lalilu.lhistory.lhistory.generated.resources.Res
import com.lalilu.lhistory.lhistory.generated.resources.history_screen_title
import com.lalilu.lhistory.viewmodel.HistoryVM
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.remixicon.System
import com.lalilu.remixicon.system.historyLine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Destination(router = ["/pages/history"])
data object HistoryScreen : Screen, ScreenInfoFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo {
        return remember {
            ScreenInfo(
                title = { "历史记录" },
                icon = RemixIcon.System.historyLine
            )
        }
    }

    @Composable
    override fun Content() {
        val viewModel: HistoryVM = koinViewModel()
        val items = viewModel.pager.collectAsLazyPagingItems()

        HistoryScreenContent(
            items = items,
            onGetHistoryList = { callback -> viewModel.getHistoryPlayedIds { callback(it) } }
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun HistoryScreenContent(
    items: LazyPagingItems<LHistory>,
    onGetHistoryList: ((List<String>) -> Unit) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "历史记录") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.history_screen_title),
                subTitle = "播放过的歌曲记录"
            )
        }

        items(
            count = items.itemCount,
            key = items.itemKey { it.id }
        ) { index ->
            val item = items[index]
            val audioRepo = org.koin.compose.koinInject<AudioRepository>()
            val audio = remember(item) { item?.contentId?.let { audioRepo.getAudio(it) } }
                ?.collectAsStateWithLifecycle(null)

            HistoryItemCard(
                modifier = Modifier.animateItem(),
                imageData = { audio?.value },
                title = { item?.contentTitle ?: "" },
                startTime = { item?.startTime ?: Clock.System.now().toEpochMilliseconds() },
                duration = { item?.duration ?: 0 },
                repeatCount = { item?.repeatCount ?: 0 },
                onLongClick = {
                    AppRouter.route("/song/detail")
                        .with("mediaId", item?.contentId)
                        .with("song", audio?.value)
                        .jump()
                },
                onClick = {
                    onGetHistoryList { list ->
                        scope.launch {
                            PlayerAction.UpdateList(
                                ids = list,
                                id = item?.contentId ?: "",
                                start = true
                            ).action()
                        }
                    }
                }
            )
        }

        if (items.loadState.append == LoadState.Loading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}