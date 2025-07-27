package com.lalilu.lhome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.extensions.DailyRecommend
import io.github.hristogochev.vortex.screen.Screen

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
    val statusBar = WindowInsets.statusBars.asPaddingValues()

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding(),
        ),
        content = {
            dailyRecommend.invoke(this)
        }
    )
}