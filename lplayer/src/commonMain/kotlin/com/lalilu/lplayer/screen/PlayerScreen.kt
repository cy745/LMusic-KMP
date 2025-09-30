package com.lalilu.lplayer.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
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
import com.lalilu.navigation.Screen
import com.lalilu.remixicon.Media
import com.lalilu.remixicon.media.pauseLine
import com.lalilu.remixicon.media.playLine
import com.lalilu.remixicon.media.skipBackLine
import com.lalilu.remixicon.media.skipForwardLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Factory

@Destination("/player")
class PlayerScreen : Screen {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val model = koinViewModel<PlayerScreenModel>()
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

            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = currentItem.value,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 500
                        )
                    )
                }
            ) { target ->
                AsyncImage(
                    modifier = Modifier.fillMaxSize()
                        .blur(50.dp, 50.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(color = Color.Black.copy(0.2f))
                        },
                    model = target,
                    contentScale = ContentScale.FillHeight,
                    contentDescription = null
                )
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${currentItem.value?.title}",
                    color = Color.White,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally)
                ) {
                    Button(onClick = { scope.launch { LPlayer.instance.skipToPrevious() } }) {
                        Icon(
                            imageVector = RemixIcon.Media.skipBackLine,
                            contentDescription = "skip back"
                        )
                    }

                    Button(onClick = { scope.launch { LPlayer.instance.togglePlayPause() } }) {
                        Icon(
                            imageVector = if (isPlaying.value) RemixIcon.Media.pauseLine else RemixIcon.Media.playLine,
                            contentDescription = "skip ${if (isPlaying.value) "pause" else "play"}"
                        )
                    }

                    Button(onClick = { scope.launch { LPlayer.instance.skipToNext() } }) {
                        Icon(
                            imageVector = RemixIcon.Media.skipForwardLine,
                            contentDescription = "skip forward"
                        )
                    }
                }
            }
        }
    }
}

@Factory
class PlayerScreenModel : ViewModel() {
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.currentItem

    init {
        LMedia.instance.whenReady {
            viewModelScope.launch {
                val list = LMedia.instance.get<LAudio>()
                LPlayer.instance.updatePlaylist(list)
                Logger.i("[LPlayer] set list: ${list.size}")
            }
        }
    }
}