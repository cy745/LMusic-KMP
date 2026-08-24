package com.lalilu.lsearch.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lsearch.lsearch.generated.resources.Res
import com.lalilu.lsearch.lsearch.generated.resources.search_screen_title
import com.lalilu.lsearch.viewmodel.SearchAction
import com.lalilu.lsearch.viewmodel.SearchVM
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Integrated content search screen (Issue #9).
 *
 * Aggregates search results across songs / albums / artists. Layout:
 *  - Content: one MultiLayout with limited previews for all three result types
 *  - More: opens the corresponding full list and forwards the current keyword
 *  - Bottom: persistent [CancellableInputerBarPanel] (acts as the always-visible
 *    search input bar)
 *
 * Registers as a tab screen so the bottom NavigationSmartBar will display
 * this screen as a tab in [com.lalilu.lmusic.App.tabsScreen].
 */
@Destination("/pages/search")
data object SearchScreen :
    Screen,
    ScreenInfoFactory,
    ScreenBarFactory {

    override fun isTabScreen(): Boolean = true

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { stringResource(Res.string.search_screen_title) },
            icon = RemixIcon.System.menuSearchLine
        )
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<SearchVM>()
        val state by vm.state.collectAsState()

        CancellableInputerBarPanel(
            focusOnShow = false,
            isVisible = { true },
            onDismiss = { AppRouter.intent(NavIntent.Pop) },
            keyword = { state.keyword },
            onUpdateKeyword = { vm.intent(SearchAction.UpdateKeyword(it)) }
        )

        SearchScreenContent()
    }
}
