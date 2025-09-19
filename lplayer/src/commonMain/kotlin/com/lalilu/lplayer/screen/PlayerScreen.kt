package com.lalilu.lplayer.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.lalilu.RemixIcon
import com.lalilu.common.ext.io
import com.lalilu.krouter.annotation.Destination
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.LPlayer
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowLeftLine
import io.github.hristogochev.vortex.model.ScreenModel
import io.github.hristogochev.vortex.model.rememberScreenModel
import kotlinx.coroutines.*
import org.koin.compose.koinInject

@Destination("/player")
class PlayerScreen : Screen {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { PlayerScreenModel() }
        val isPlaying = model.isPlaying.collectAsState()
        val currentItem = model.currentItem.collectAsState()
        val scope = rememberCoroutineScope()
        val currentTime = remember { mutableStateOf(0L) }
        val platformSource = koinInject<PlatformMediaSource>()

        LaunchedEffect(isPlaying.value) {
            while (isActive && isPlaying.value) {
                withFrameMillis { currentTime.value = LPlayer.instance.currentPosition() }
            }
        }


        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .background(color = Color.Gray)
        ) {
            val lyricEntries = remember {
                mutableStateOf<List<LyricItem>>(emptyList())
            }

            LaunchedEffect(currentItem.value) {
                withContext(Dispatchers.io) {
                    val song = currentItem.value ?: return@withContext
                    val lyric = platformSource.sources.firstOrNull { it.name == song.mediaSourceName }
                        ?.dataSource
                        ?.runCatching { getLyric(song) }
                        ?.getOrNull()

                    lyricEntries.value = LyricUtils.parseLrc(lyric)
                        ?: emptyList()
                }
            }

            LyricLayout(
                modifier = Modifier.fillMaxSize(),
                currentTime = { currentTime.value },
                screenConstraints = constraints,
                lyricEntry = lyricEntries,
                isUserClickEnable = { false },
                isUserScrollEnable = { false }
            )

            Column(
                modifier = Modifier.fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(
                        text = "${currentItem.value?.title}",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }

                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { scope.launch { LPlayer.instance.skipToPrevious() } }) {
                        Text(text = "<")
                    }

                    Button(onClick = { scope.launch { LPlayer.instance.togglePlayPause() } }) {
                        Text(text = if (isPlaying.value) "Pause" else "Play")
                    }

                    Button(onClick = { scope.launch { LPlayer.instance.skipToNext() } }) {
                        Text(text = ">")
                    }
                }
            }
        }
    }
}

class PlayerScreenModel : ScreenModel, CoroutineScope by CoroutineScope(Dispatchers.Default) {
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.currentItem

    init {
        LMedia.instance.whenReady {
            launch {
                val list = LMedia.instance.get<LAudio>()
                LPlayer.instance.updatePlaylist(list)
                Logger.i("[LPlayer] set list: ${list.size}")
            }
        }
    }
}