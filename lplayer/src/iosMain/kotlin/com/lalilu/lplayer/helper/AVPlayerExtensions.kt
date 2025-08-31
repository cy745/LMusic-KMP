package com.lalilu.lplayer.helper

import com.lalilu.common.ext.io
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.removeTimeObserver
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObjectProtocol
import kotlin.coroutines.CoroutineContext


object AVPlayerItemEventObserver : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val notificationCenter by lazy { NSNotificationCenter.defaultCenter() }
    private val mainQueue by lazy { NSOperationQueue.mainQueue() }
    private val observerMap = mutableMapOf<String, NSObjectProtocol>()

    fun observe(
        key: String?,
        target: AVPlayerItem,
        callback: suspend () -> Unit
    ) {
        key ?: return
        removeObserver(key)

        val observer = notificationCenter.addObserverForName(
            name = key,
            `object` = target,
            queue = mainQueue,
            usingBlock = { notification -> launch { callback() } }
        )
        observerMap.put(key, observer)
    }

    fun removeObserver(key: String) {
        val observer = observerMap[key] ?: return
        notificationCenter.removeObserver(observer)
        observerMap.remove(key)
    }

    fun removeAllObserver() {
        observerMap.values.forEach { notificationCenter.removeObserver(it) }
        observerMap.clear()
    }
}

@OptIn(ExperimentalForeignApi::class)
object AVPlayerPositionObserver : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private var timeObserver: Any? = null

    fun observe(
        player: AVPlayer,
        callback: suspend (position: Double) -> Unit
    ) {
        timeObserver?.let { player.removeTimeObserver(it) }
        timeObserver = null

        val observer: (CValue<CMTime>) -> Unit = { time ->
            val seconds = CMTimeGetSeconds(time)
            launch { callback(seconds) }
        }

        val interval = CMTimeMakeWithSeconds(1.0, NSEC_PER_SEC.toInt())
        timeObserver = player.addPeriodicTimeObserverForInterval(interval, queue = null, usingBlock = observer)
    }

    fun removeObserver(player: AVPlayer) {
        timeObserver?.let { player.removeTimeObserver(it) }
        timeObserver = null
    }
}