package com.lalilu.lplayer.extensions

import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.component.KoinComponent
import kotlin.coroutines.CoroutineContext

const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
const val FLAG_ONLY_UPDATE_TICKER = 0x2000000

@OptIn(UnstableApi::class)
class FlymeStatusLyricHelper : CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private var loadLyricJob: Job? = null
//    private var lyrics: Pair<String, List<LyricItem>>? = null
    private val enableState = MutableStateFlow(false)

    suspend fun updateEnable(enable: Boolean) {
        enableState.emit(enable)
    }

    fun loadLyricIntoNotification(
        mediaSession: MediaSession,
        mediaItem: MediaItem?,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ) {
        loadLyricJob?.cancel()
        if (mediaItem == null) return

        loadLyricJob = launch {
            // 加载歌词
//            if (lyrics?.first != mediaItem.mediaId) {
//                lyrics = "mediaItem.mediaId to (lyricSource.loadLyric(mediaItem)
//                    ?.let { LyricUtils.parseLrc(it.first, it.second) }
//                    ?: emptyList())"
//            }

            var lastIndex = -1
            enableState.collectLatest { enable ->
                while (isActive && enable) {
//                    val list = lyrics?.second ?: break
//                    val time = withContext(Dispatchers.Main) { mediaSession.player.currentPosition }
//
//                    val index = list.findPlayingIndex(time)
//                    if (lastIndex == index) {
//                        delay(50)
//                        continue
//                    }
//
//                    lastIndex = index
//                    val current = list.getOrNull(index)
//
//                    if (current != null) {
//                        val text = when (current) {
//                            is LyricItem.NormalLyric -> current.content
//                            is LyricItem.WordsLyric -> current.getSentenceContent()
//                            else -> ""
//                        }
//
//                        builder.setTicker(text)
//                        val notification = MediaNotification(notificationId, builder.build().apply {
//                            flags = flags or FLAG_ALWAYS_SHOW_TICKER or FLAG_ONLY_UPDATE_TICKER
//                        })
//
//                        onNotificationChangedCallback.onNotificationChanged(notification)
//                    }
//                    delay(50)
                }

                if (!enable) {
                    builder.setTicker(null)
                    onNotificationChangedCallback.onNotificationChanged(
                        MediaNotification(
                            notificationId,
                            builder.build()
                        )
                    )
                }
            }
        }
    }
}