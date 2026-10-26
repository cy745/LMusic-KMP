package com.lalilu.lplayer.service

import com.lalilu.lplayer.core.withQueuePlaybackRequest
import com.lalilu.lplayer.core.START_QUEUE_PLAYBACK
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import com.lalilu.lplayer.playback.PlaybackHistory
import com.lalilu.lplayer.playback.observeHistoryRestoreSettled
import com.lalilu.lplayer.playback.resolveQueue
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lplayer.service.CustomCommand.SeekToNext
import com.lalilu.lplayer.service.CustomCommand.SeekToPrevious
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.guava.await
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext
import com.lalilu.lplayer.core.*
import com.lalilu.playback.core.PlaybackCore
import com.lalilu.playback.core.TrackKey
import com.lalilu.playback.core.ContentResolver
import com.lalilu.lmedia.domain.model.MediaKey

@OptIn(UnstableApi::class)
class MService : MediaLibraryService(), CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.Main.immediate + SupervisorJob()
    private val dataTracker by inject<IPlaybackDataTracker>()
    private val historyAnalyticsListener by lazy { HistoryAnalyticsListener(dataTracker) }

    private var player: Player? = null
    private var exoPlayer: ExoPlayer? = null
    private var outputProbe: com.lalilu.lplayer.core.AndroidOutputProbe? = null
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

        val repository = getKoin().get<AudioRepository>()
        val access = PlaybackContentAccess(this, platformMediaSource, repository, restoreMetadata = { cached ->
            val existing = repository.getAudios().first().map { it.playbackId }.toSet()
            val database = getKoin().get<com.lalilu.lmedia.data.database.ILMediaDatabase>()
            cached.filter { it.playbackId !in existing && platformMediaSource.isEnabled(it.mediaSourceName) }
                .forEach { database.mediaDao().upsertAudio(it) }
        })
        val items = mutableMapOf<TrackKey, MediaItem>()
        val runtime = Media3CoreRuntime(this, access.dataSourceFactory, itemFactory = { ticket, resource ->
            items.getValue(ticket.key).buildUpon().setUri(resource.uri).build()
        })
        exoPlayer = runtime.player.apply {
            setAudioAttributes(defaultAudioAttributes, LPlayerKV.handleAudioFocus.value)
            setHandleAudioBecomingNoisy(LPlayerKV.handleBecomeNoisy.value)
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(id: Int) { eqHelper?.audioSessionId = id }
            })
        }
        outputProbe = com.lalilu.lplayer.core.AndroidOutputProbe(this, runtime.player)
        val core = PlaybackCore(this, ContentResolver { key -> access.resolve(MediaKey(key.source,key.id)) }, runtime)
        player = CoreSessionPlayer(core, runtime, this, items, access::close).apply {
            addListener(MPlayerListener(this))
            addListener(historyAnalyticsListener)
        }

        (player as CoreSessionPlayer).setMode(PlayMode.from(LPlayerKV.playMode.value))

        mediaSession = MediaLibrarySession
            .Builder(this, player!!, MServiceCallback(player!!, this))
            .setSessionActivity(getLauncherPendingIntent())
            .build()

        core.state.map { it.explicitNext to it.pauseAtEnd }.distinctUntilChanged().onEach { (pending, pauseAtEnd) ->
            mediaSession?.setSessionExtras(Bundle().apply {
                pending?.let { putString("pendingNext", MediaKey(it.source,it.id).stableKey) }
                putBoolean("pauseAtEnd",pauseAtEnd)
            })
        }.launchIn(this)
        startListenForValuesUpdate()
    }

    override fun onDestroy() {
        outputProbe?.close()
        outputProbe = null
        // 释放相关实例
        player?.stop()
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        notificationProvider?.cancel()
        cancel()
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

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            LPlayerKV.playMode.value = playModeOf(player.repeatMode,player.shuffleModeEnabled).name
        }
    }

}

@OptIn(UnstableApi::class)
private class MServiceCallback(private val player: Player, private val scope: CoroutineScope) : MediaLibrarySession.Callback, KoinComponent {
    private val playbackHistory by inject<PlaybackHistory>()
    private val audioRepository by inject<AudioRepository>()
    private val sourceBindings by inject<MediaSourceBindingRepository>()
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

        return scope.future(Dispatchers.IO) {
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
                    CustomCommand.PlayNext -> {
                        val item = withContext(Dispatchers.IO) { MMedia.getItem(requireNotNull(args.getString("playbackId"))) }
                            ?: return@withContext
                        (player as CoreSessionPlayer).requestPlayNext(item).await()
                    }
                    CustomCommand.SetMode -> (player as CoreSessionPlayer).setMode(PlayMode.from(requireNotNull(args.getString("mode")))).await()
                    CustomCommand.PauseAtEnd -> (player as CoreSessionPlayer).pauseAtEnd(args.getBoolean("enabled")).await()
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

        return scope.future(Dispatchers.IO) {
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

        return scope.future(Dispatchers.IO) {
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
        return scope.future(Dispatchers.IO) {
            val ids = mediaItems.map { it.mediaId }
            val startPlayback = mediaItems.firstOrNull()?.requestMetadata?.extras?.getBoolean(START_QUEUE_PLAYBACK) == true
            val items = MMedia.getItems(ids).mapIndexed { index, item ->
                if (index == 0) item.withQueuePlaybackRequest(startPlayback) else item
            }

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

        return scope.future(Dispatchers.IO) {
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

        return scope.future(Dispatchers.IO) {
            val snapshot = playbackHistory.restoreFromHistory()
                ?: return@future MediaSession.MediaItemsWithStartPosition(MMedia.getChildren(MMedia.ALL_SONGS), 0, 0L)
            sourceBindings.startBinding()
            val resolution = combine(
                audioRepository.getAudios(snapshot.ids),
                sourceBindings.observeHistoryRestoreSettled(snapshot.sourceNames.getOrNull(snapshot.index)),
            ) { audios, settled -> snapshot.resolveQueue(audios) to settled }
                .first { (resolved, settled) -> resolved.currentIndex >= 0 || settled }.first
            MediaSession.MediaItemsWithStartPosition(
                resolution.items.map { it.toMediaItem() },
                resolution.currentIndex.coerceAtLeast(0),
                if (resolution.currentIndex >= 0) snapshot.position.coerceAtLeast(0L) else 0L,
            )
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
