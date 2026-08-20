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
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenBarComponent
import com.lalilu.navigation.ScreenBarFactory
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Integrated content search screen (Issue #9).
 *
 * Aggregates search results across songs / albums / artists. Layout:
 *  - Top: LazyColumn showing the current type-filter's results
 *  - Mid-bottom: floating [com.lalilu.lsearch.component.SearchTypeTabBar]
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
        SearchScreenContent()
    }

    /**
     * Returns the always-visible search input bar as the bottom NormalBar.
     *
     * [CancellableInputerBarPanel] internally calls [ScreenBarFactory.RegisterContent],
     * pushing a [ScreenBarComponent] onto the [com.lalilu.navigation.smartbar.ComponentStack].
     * Since `isVisible = { true }`, the component is registered once and stays
     * on the stack as long as this screen is composed.
     *
     * On `onDismiss` (the "关闭" / back-arrow button), we issue a [NavIntent.Pop]
     * which mirrors the system back-press, popping the current tab back to home.
     */
    @Composable
    override fun content(): ScreenBarComponent? {
        val vm = koinViewModel<SearchVM>()
        val state by vm.state.collectAsState()

        CancellableInputerBarPanel(
            isVisible = { true },
            onDismiss = { AppRouter.intent(NavIntent.Pop) },
            keyword = { state.keyword },
            onUpdateKeyword = { vm.intent(SearchAction.UpdateKeyword(it)) }
        )

        // CancellableInputerBarPanel has already registered itself via RegisterContent;
        // we don't need to return a separate component here.
        return null
    }
}