package com.lalilu.lmedia.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.content
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.remote.RemoteServerPanel
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.remixicon.Document
import com.lalilu.remixicon.document.folderOpenLine
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
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val column = when {
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 3
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 2
            else -> 1
        }
        val statusBar = WindowInsets.statusBars.asPaddingValues()
        val navigationBar = WindowInsets.navigationBars.asPaddingValues()
        val smartBarHeight = PassThroughHelper.getValue(
            key = "SmartBarHeight",
            default = { navigationBar.calculateBottomPadding() }
        )

        val sourcesContent = remember(platformSource.sources) {
            platformSource.sources.mapNotNull { it.content(modifier = Modifier.fillMaxWidth()) }
        }.map { it.register() }

        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBar.calculateTopPadding() + 16.dp,
                bottom = smartBarHeight() + 16.dp
            ),
            columns = StaggeredGridCells.Fixed(column),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp
        ) {
            item {
                NavigatorHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "媒体数据源",
                    subTitle = "自由添加媒体数据源",
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