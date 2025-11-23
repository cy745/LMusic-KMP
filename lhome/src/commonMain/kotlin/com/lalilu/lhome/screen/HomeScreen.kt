package com.lalilu.lhome.screen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.OverrideNavDisplay
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.extensions.DailyRecommend
import com.lalilu.lhome.extensions.EntryPanel
import com.lalilu.lhome.extensions.HistoryPanel
import com.lalilu.lhome.extensions.LatestPanel
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenTransitionFactory

@Destination(router = ["/home"])
class HomeScreen : Screen, ScreenTransitionFactory {

    @Composable
    override fun Content() {
        HomeScreenContent()
    }

    override fun provideTransitionMetadata(): Map<String, Any> = OverrideNavDisplay.transitionSpec {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Up,
            animationSpec = tween(300)
        ) togetherWith ExitTransition.None
    } + OverrideNavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Down,
            animationSpec = tween(300)
        )
    } + OverrideNavDisplay.predictivePopTransitionSpec {
        EnterTransition.None togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Down,
            animationSpec = tween(300)
        )
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