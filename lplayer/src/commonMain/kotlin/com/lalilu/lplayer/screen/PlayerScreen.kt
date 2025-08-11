package com.lalilu.lplayer.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.LPlayer
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowLeftLine
import io.github.hristogochev.vortex.model.ScreenModel
import io.github.hristogochev.vortex.model.rememberScreenModel

@Destination("/player")
class PlayerScreen : Screen {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { PlayerScreenModel() }
        val isPlaying = model.isPlaying.collectAsState()
        val currentItem = model.currentItem.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "currentItem: ${currentItem.value?.title}")

            Text(text = "isPlaying: ${isPlaying.value}")

            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { LPlayer.instance.skipTpPrevious() }) {
                    Text(text = "Previous")
                }

                Button(onClick = { LPlayer.instance.play() }) {
                    Text(text = "Play")
                }

                Button(onClick = { LPlayer.instance.pause() }) {
                    Text(text = "Pause")
                }

                Button(onClick = { LPlayer.instance.skipToNext() }) {
                    Text(text = "Next")
                }
            }

            val backStack = LocalBackStack.current
            with(LocalSharedTransitionScope.current) {
                Button(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(48.dp)
                        .sharedElementWithCallerManagedVisibility(
                            sharedContentState = rememberSharedContentState("test"),
                            visible = backStack.last() is PlayerScreen
                        ),
                    onClick = { if (backStack.size >= 2) backStack.removeLastOrNull() }
                ) {
                    Icon(
                        imageVector = RemixIcon.Arrows.arrowLeftLine,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

class PlayerScreenModel : ScreenModel {
    val isPlaying = LPlayer.instance.isPlaying()
    val currentItem = LPlayer.instance.currentItem()

    init {
        LMedia.instance.whenReady {
            val list = LMedia.instance.get<LAudio>()
            LPlayer.instance.updatePlaylist(list)
            Logger.i("[LPlayer] set list: ${list.size}")
        }
    }
}