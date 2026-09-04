package com.lalilu.lmedia.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.content
import com.lalilu.lmedia.component.LocalSourceTextFieldRelocator
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.remote.RemoteServerPanel
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.navigation.smartbar.SmartBarContentHeight
import org.koin.compose.koinInject


@Destination("/media_source")
object MediaSourceScreen : Screen, ScreenInfoFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "媒体源" },
            icon = RemixIcon.Document.folderOpenLine
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val platformSource = koinInject<PlatformMediaSource>()
        val bindingRepository = koinInject<MediaSourceBindingRepository>()
        val librarySummary by bindingRepository.summary.collectAsStateWithLifecycle()
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val column = when {
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 3
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 2
            else -> 1
        }
        val horizontalPadding = when (column) {
            1 -> 16.dp
            2 -> 28.dp
            else -> 40.dp
        }
        val columnSpacing = if (column == 1) 0.dp else 40.dp
        val itemSpacing = if (column == 1) 28.dp else 36.dp
        val statusBar = WindowInsets.statusBars.asPaddingValues()
        val navigationBar = WindowInsets.navigationBars.asPaddingValues()
        val smartBarHeight = PassThroughHelper.getValue(
            key = "SmartBarHeight",
            default = { navigationBar.calculateBottomPadding() }
        )
        val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val density = LocalDensity.current
        val gridState = rememberLazyStaggeredGridState()
        val imeBottomPaddingPx = with(density) { imeBottomPadding.toPx() }
        val smartBarContentHeightPx = with(density) { SmartBarContentHeight.toPx() }
        val relocateFocusedField: suspend (Rect, Float) -> Unit = remember(
            gridState,
            imeBottomPaddingPx,
            smartBarContentHeightPx,
        ) {
            val relocator: suspend (Rect, Float) -> Unit = { fieldBounds, rootHeight ->
                val visibleBottom = rootHeight - imeBottomPaddingPx - smartBarContentHeightPx
                val coveredHeight = (fieldBounds.bottom - visibleBottom).coerceAtLeast(0f)
                if (coveredHeight > 0f) gridState.scrollBy(coveredHeight)
            }
            relocator
        }

        val sourcesContent = remember(platformSource.sources) {
            platformSource.sources.mapNotNull { it.content(modifier = Modifier.fillMaxWidth()) }
        }.map { it.register() }

        CompositionLocalProvider(LocalSourceTextFieldRelocator provides relocateFocusedField) {
            LazyVerticalStaggeredGrid(
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = statusBar.calculateTopPadding() + 16.dp,
                    bottom = smartBarHeight() + 16.dp
                ),
                columns = StaggeredGridCells.Fixed(column),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing),
                verticalItemSpacing = itemSpacing
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    NavigatorHeader(
                        modifier = Modifier.fillMaxWidth(),
                        title = "媒体数据源",
                        subTitle = remember(librarySummary) {
                            "媒体库 ${librarySummary.databaseSongCount} 首 · " +
                                "可用 ${librarySummary.availableSongCount} · " +
                                "不可用 ${librarySummary.unavailableSongCount}"
                        },
                        paddingValues = PaddingValues(top = 16.dp, bottom = 16.dp, start = 4.dp, end = 4.dp)
                    )
                }
                item {
                    RemoteServerPanel(modifier = Modifier.fillMaxWidth())
                }
                sourcesContent.forEach { it.invoke(this) }
            }
        }
    }
}
