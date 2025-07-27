package com.lalilu.lmusic.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.PlatformMediaSource
import io.github.hristogochev.vortex.model.ScreenModel
import io.github.hristogochev.vortex.model.rememberScreenModel
import io.github.hristogochev.vortex.navigator.LocalNavigator
import io.github.hristogochev.vortex.navigator.Navigator
import io.github.hristogochev.vortex.screen.Screen
import org.koin.compose.koinInject

@Destination("/detail")
data class DetailScreen(
    val item: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current ?: return
        val vm = rememberScreenModel { DetailVM(item, navigator) }
        val sources = koinInject<PlatformMediaSource>()

        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SideEffect {
                println("sources: size ${sources.source.size}")
            }
            sources.source.forEach {
                it.Content(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                )
            }
            Button(onClick = { navigator.pop() }) {
                Text(text = "back")
            }
        }
    }
}

class DetailVM(
    val item: String,
    val navigator: Navigator
) : ScreenModel {

    fun doAction() {
        KRouter.route<Screen>("/extra?item=test")
            ?.let { navigator.push(it) }
        println("[$item]DetailVM: doAction")
    }

    override fun onDispose() {
        println("${this::class.simpleName} is cleared $item")
    }
}