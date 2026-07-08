package com.lalilu.lplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import co.touchlab.kermit.Logger
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lalilu.common.kv.KVContext
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.*
import com.lalilu.lplayer.playback.IPlaybackDataTracker
import com.lalilu.lplayer.service.CustomCommand.SeekToNext
import com.lalilu.lplayer.service.CustomCommand.SeekToPrevious
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.guava.future
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext

@OptIn(UnstableApi::class)
class MService : MediaLibraryService(), CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
    private val dataTracker by inject<IPlaybackDataTracker>()
    private val historyAnalyticsListener by lazy { HistoryAnalyticsListener(dataTracker) }

    private var player: Player? = null
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var eqHelper: EQHelper? = null
    private var notificationProvider: MNotificationProvider? = null
    private val platformMediaSource by inject<PlatformMediaSource>()
    private val defaultAudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        eqHelper = KoinPlatform.getKoin().getOrNull<EQHelper>()
        notificationProvider = MNotificationProvider(this)
            .also { setMediaNotificationProvider(it) }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(FadeTransitionRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(LMediaDataSource.Factory(this, platformMediaSource)))
            .setHandleAudioBecomingNoisy(LPlayerKV.handleBecomeNoisy.value)
            .setAudioAttributes(defaultAudioAttributes, LPlayerKV.handleAudioFocus.value)
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE) // 避免播放上一首需要点两次
            .build()
            .apply {
                exoPlayer = this
                addAnalyticsListener(historyAnalyticsListener)
                addListener(
                    MPlayerListener(
                        player = this,
                        onSessionIdChange = { eqHelper?.audioSessionId = it }
                    )
                )
            }
            .setUpQueueControl()

        mediaSession = MediaLibrarySession
            .Builder(this, player!!, MServiceCallback(player!!))
            .setSessionActivity(getLauncherPendingIntent())
            .build()

        startListenForValuesUpdate()
    }

    override fun onDestroy() {
        // 释放相关实例
        player?.stop()
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = mediaSession

    private fun startListenForValuesUpdate() = launch {
        LPlayerKV.handleAudioFocus.flow().onEach {
            withContext(Dispatchers.Main) {
                player?.setAudioAttributes(defaultAudioAttributes, it)
            }
        }.launchIn(this)

        LPlayerKV.handleBecomeNoisy.flow().onEach {
            withContext(Dispatchers.Main) {
                exoPlayer?.setHandleAudioBecomingNoisy(it)
            }
        }.launchIn(this)

        LPlayerKV.playMode.flow().onEach {
            withContext(Dispatchers.Main) {
                player?.playMode = PlayMode.from(it)
            }
        }.launchIn(this)

        KVContext.obtainStatic<Boolean>("enable_system_eq", false, "settings").flow().onEach {
            eqHelper?.setSystemEqEnable(it)
        }.launchIn(this)

        KVContext.obtainStatic<Boolean>("enable_status_lyric", false, "settings").flow().onEach {
            notificationProvider?.flymeStatusLyricHelper?.updateEnable(it)
        }.launchIn(this)
    }
}

private class MPlayerListener(
    val player: Player,
    val onSessionIdChange: (Int) -> Unit = {}
) : Player.Listener {
    @UnstableApi
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        super.onAudioSessionIdChanged(audioSessionId)
        onSessionIdChange(audioSessionId)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        val playMode = playModeOf(
            repeatMode = player.repeatMode,
            shuffleModeEnabled = shuffleModeEnabled
        )
        LPlayerKV.playMode.value = playMode.name
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        val playMode = playModeOf(
            repeatMode = repeatMode,
            shuffleModeEnabled = player.shuffleModeEnabled
        )
        LPlayerKV.playMode.value = playMode.name
    }
}

@OptIn(UnstableApi::class)
private class MServiceCallback(private val player: Player) : MediaLibrarySession.Callback {
    private val logger = Logger.withTag("MServiceCallback")

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        logger.i {
            "onConnect: packageName=${controller.packageName}, uid=${controller.uid}, " +
                    "isTrusted=${controller.isTrusted}, controllerVersion=${controller.controllerVersion}"
        }

        val sessionCommands = MediaSession.ConnectionResult
            .DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .registerCustomCommands()
            .build()

        return AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .build().also {
                logger.i { "onConnect: accepted with ${it.availableSessionCommands.commands.size} sessionCommands and ${it.availablePlayerCommands.size()} playerCommands" }
            }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        logger.i {
            "onCustomCommand: customAction=${customCommand.customAction}, " +
                    "args=$args, packageName=${controller.packageName}"
        }

        return CoroutineScope(Dispatchers.IO).future {
            val action = customCommand.toCustomCommendOrNull()

            if (action == null) {
                logger.i { "onCustomCommand: unsupported customAction=${customCommand.customAction}" }
                return@future SessionResult(SessionError.ERROR_NOT_SUPPORTED)
            }

            logger.i { "onCustomCommand: executing action=$action" }
            withContext(Dispatchers.Main) {
                when (action) {
                    SeekToNext -> player.seekToNext()
                    SeekToPrevious -> player.seekToPrevious()
                }
            }

            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    private fun buildBrowsableItem(id: String, title: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        logger.i {
            "onGetLibraryRoot: packageName=${browser.packageName}, " +
                    "isTrusted=${browser.isTrusted}, params=$params"
        }
        return Futures.immediateFuture(
            LibraryResult.ofItem(buildBrowsableItem(MMedia.ROOT, "LMedia Library"), params)
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        logger.i {
            "onGetChildren: parentId=$parentId, page=$page, pageSize=$pageSize, " +
                    "packageName=${browser.packageName}"
        }

        return CoroutineScope(Dispatchers.IO).future {
            val result = if (parentId == MMedia.ROOT) {
                LibraryResult.ofItemList(
                    listOf(
                        buildBrowsableItem(MMedia.ALL_SONGS, "All Songs"),
                        buildBrowsableItem(MMedia.ALL_ARTISTS, "All Artists"),
                        buildBrowsableItem(MMedia.ALL_ALBUMS, "All Albums")
                    ),
                    params
                )
            } else {
                LibraryResult.ofItemList(MMedia.getChildren(parentId), params)
            }

            logger.i { "onGetChildren: parentId=$parentId, resultItemCount=${result.value?.size}" }
            result
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        logger.i {
            "onGetItem: mediaId=$mediaId, packageName=${browser.packageName}"
        }

        return CoroutineScope(Dispatchers.IO).future {
            if (mediaId == MMedia.ROOT) {
                return@future LibraryResult.ofItem(buildBrowsableItem(MMedia.ROOT, "LMedia Library"), null)
            }

            val item = MMedia.getItem(mediaId)

            if (item != null) {
                logger.i { "onGetItem: mediaId=$mediaId found, title=${item.mediaMetadata.title}" }
                LibraryResult.ofItem(item, null)
            } else {
                logger.i { "onGetItem: mediaId=$mediaId not found" }
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return CoroutineScope(Dispatchers.IO).future {
            val ids = mediaItems.map { it.mediaId }
            val items = MMedia.getItems(ids)

            MediaSession.MediaItemsWithStartPosition(
                items,
                startIndex,
                startPositionMs
            )
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        logger.i {
            "onAddMediaItems: ids=${mediaItems.map { it.mediaId }}, " +
                    "packageName=${controller.packageName}"
        }

        return CoroutineScope(Dispatchers.IO).future {
            val ids = mediaItems.map { it.mediaId }
            val items = MMedia.getItems(ids)

            logger.i {
                "onAddMediaItems: requested=${ids.size} ids, resolved=${items.size} items"
            }

            items.toMutableList()
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        logger.i {
            "onPlaybackResumption: packageName=${controller.packageName}"
        }

        return CoroutineScope(Dispatchers.IO).future {
            val history = LPlayerKV.historyPlaylistIds.getData()

            logger.i { "onPlaybackResumption: historySize=${history.size}, history=$history" }

            val items = if (history.isNotEmpty()) history.mapNotNull { mediaId -> MMedia.getItem(mediaId) }
            else MMedia.getChildren(MMedia.ALL_SONGS)

            logger.i { "onPlaybackResumption: resolvedItems=${items.size}, source=${if (history.isNotEmpty()) "history" else "allSongs"}" }

            MediaSession.MediaItemsWithStartPosition(items, 0, 0L)
        }
    }
}

private fun Context.getLauncherPendingIntent(): PendingIntent {
    return PendingIntent.getActivity(
        this, 0, Intent().apply {
            setClassName(
                AppUtils.getAppPackageName(),
                ActivityUtils.getLauncherActivity()
            )
        }, PendingIntent.FLAG_IMMUTABLE
    )
}