package com.lalilu.lplayer.service

import android.annotation.SuppressLint
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext


@SuppressLint("UnsafeOptInUsageError")
class MService : MediaLibraryService(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

    private var player: Player? = null
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var notificationProvider: MediaNotification.Provider? = null
    private val defaultAudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    override fun onGetSession(p0: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onCreate() {
        super.onCreate()

        notificationProvider = MNotificationProvider(this)
            .also { setMediaNotificationProvider(it) }
    }
}