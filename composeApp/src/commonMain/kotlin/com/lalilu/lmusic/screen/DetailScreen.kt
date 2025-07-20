package com.lalilu.lmusic.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.annotation.Destination
import io.github.hristogochev.vortex.model.ScreenModel
import io.github.hristogochev.vortex.model.rememberScreenModel
import io.github.hristogochev.vortex.navigator.LocalNavigator
import io.github.hristogochev.vortex.navigator.Navigator
import io.github.hristogochev.vortex.screen.Screen

@Destination("/detail")
data class DetailScreen(
    val item: String
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current ?: return
        val vm = rememberScreenModel { DetailVM(item, navigator) }

        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Detail Screen: $item")
            Button(onClick = { vm.doAction() }) {
                Text(text = " to Extra Screen")
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