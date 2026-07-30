package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.retrieve
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.extensions.DailyRecommend
import com.lalilu.lhome.extensions.EntryPanel
import com.lalilu.lhome.extensions.LatestPanel
import com.lalilu.lhome.lhome.generated.resources.Res
import com.lalilu.lhome.lhome.generated.resources.home_screen_title
import com.lalilu.navigation.*
import org.koin.core.qualifier.named

@Destination(router = ["/home"])
data object HomeScreen : Screen, ScreenMetadataFactory, ScreenInfoFactory {
    override fun provideMetadata(): Map<String, Any> = Metadata.home()
    override fun isTabScreen(): Boolean = true

    @Composable
    override fun provideScreenInfo(): ScreenInfo {
        return remember {
            ScreenInfo(
                title = { Res.string.home_screen_title.retrieve() },
                icon = RemixIcon.System.loaderLine
            )
        }
    }

    @Composable
    override fun Content() {
        HomeScreenContent()
    }
}


@Composable
fun HomeScreenContent(modifier: Modifier = Modifier) {
    val dailyRecommend = DailyRecommend.register()
    val latestPanel = LatestPanel.register()
    val entryPanel = EntryPanel.register()
    val historyPanel = remember { requestFor<LazyGridContent>(named("history_panel")) }
        ?.register()

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        ),
        content = {
            dailyRecommend.invoke(this)
            latestPanel.invoke(this)
            historyPanel?.invoke(this)
            entryPanel.invoke(this)
        }
    )
}