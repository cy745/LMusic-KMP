package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.extensions.DailyRecommend
import com.lalilu.lhome.extensions.EntryPanel
import com.lalilu.lhome.extensions.HistoryPanel
import com.lalilu.lhome.extensions.LatestPanel
import com.lalilu.navigation.Screen

@Destination(router = ["/home"])
class HomeScreen : Screen {

    @Composable
    override fun Content() {
        HomeScreenContent()
    }
}


@Composable
fun HomeScreenContent(modifier: Modifier = Modifier) {
    val dailyRecommend = DailyRecommend.register()
    val latestPanel = LatestPanel.register()
    val historyPanel = HistoryPanel.register()
    val entryPanel = EntryPanel.register()

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(12),
        overscrollEffect = null, // Compose 1.10.0-alpha03 仍存在bug，暂时禁用overscroll，已提交bug [CMP-9153](https://youtrack.jetbrains.com/issue/CMP-9153)
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = navigationBar.calculateBottomPadding() + 12.dp
        ),
        content = {
            dailyRecommend.invoke(this)
            latestPanel.invoke(this)
            historyPanel.invoke(this)
            entryPanel.invoke(this)
        }
    )
}