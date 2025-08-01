package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.extensions.DailyRecommend
import io.github.hristogochev.vortex.navigator.LocalNavigator
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
    val navigator = LocalNavigator.current

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(top = statusBar.calculateTopPadding()),
        content = {
            dailyRecommend.invoke(this)
            item(span = { GridItemSpan(12) }) {
                Button(onClick = {
                    KRouter.route<Screen>("/media_source")
                        ?.let { navigator?.push(it) }
                }) {
                    Text("Go to MediaSourceScreen")
                }
            }
        }
    )
}