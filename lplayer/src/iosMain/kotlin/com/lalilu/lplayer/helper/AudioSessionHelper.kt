package com.lalilu.lplayer.helper

import co.touchlab.kermit.Logger
import com.lalilu.lplayer.playback.Playback
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import com.lalilu.lplayer.action.launchPlayerAction
import platform.AVFAudio.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object AudioSessionHelper {
    private var bindingScope: CoroutineScope? = null
    private val audioSession by lazy { AVAudioSession.sharedInstance() }
    private val logger = Logger.withTag("AudioSessionInterruptionHelper")
    private var bindingGeneration = 0L
    private val observers = mutableListOf<Any>()
    fun debugLog(message: String) = logger.i(messageString = message)

    fun setUpAudioSession(): Boolean = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        if (!audioSession.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = 0u,
                error = errorPtr.ptr
            )
        ) {
            errorPtr.value?.let { error ->
                debugLog("Error setting audio session category: ${error.localizedDescription}")
            }
            return false
        }

        if (!audioSession.setActive(active = true, withOptions = 0u, error = errorPtr.ptr)) {
            errorPtr.value?.let { error ->
                debugLog("Error activating audio session: ${error.localizedDescription}")
            }
            return false
        }

        return true
    }

    fun ensureAudioSessionActive() = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        if (!audioSession.setActive(active = true, withOptions = 0u, error = errorPtr.ptr)) {
            errorPtr.value?.let { error ->
                debugLog("Error ensuring audio session active: ${error.localizedDescription}")
            }
        }
    }

    /**
     * 绑定 AudioSession 中断监听。
     *
     * @param playback Playback 实例
     * @param shouldHandleInterruption 可选的中断过滤（默认处理）。
     *                            当 MusicKitEngine 活跃时传入可跳过误中断。
     */
    fun unbindPlayback() {
        bindingScope?.cancel(); bindingScope = null
        bindingGeneration++
        observers.forEach { NSNotificationCenter.defaultCenter().removeObserver(it) }
        observers.clear()
    }

    fun bindPlayback(
        playback: Playback,
        shouldHandleInterruption: (() -> Boolean)? = null
    ) {
        unbindPlayback()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { bindingScope = it }
        val generation = bindingGeneration
        var resumeToken: com.lalilu.lplayer.core.CorePlayback.InterruptionToken? = null
        observers += NSNotificationCenter.defaultCenter().addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = audioSession,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = { notification ->
                val reason = notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber
                if (reason?.unsignedLongValue == AVAudioSessionRouteChangeReasonOldDeviceUnavailable &&
                    com.lalilu.lplayer.LPlayerKV.handleBecomeNoisy.value) {
                    scope.launchPlayerAction {
                        if (generation != bindingGeneration) return@launchPlayerAction
                        resumeToken = null
                        if (playback is com.lalilu.lplayer.core.CorePlayback) playback.pauseForInterruption()
                        else playback.pause()
                    }
                }
            }
        )
        observers += NSNotificationCenter.defaultCenter().addObserverForName(
            name = "AVAudioSessionInterruptionNotification",
            `object` = audioSession,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = { notification: NSNotification? ->
                debugLog("AVAudioSessionInterruptionNotification")

                notification?.userInfo?.let { userInfo ->
                    val interruptionType = userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber
                    val typeValue = interruptionType?.unsignedLongValue
                    debugLog("interruptionType: $typeValue")

                    when (typeValue) {
                        AVAudioSessionInterruptionTypeBegan -> {
                            scope.launchPlayerAction {
                                if (generation != bindingGeneration) return@launchPlayerAction
                                resumeToken = null
                                if (com.lalilu.lplayer.LPlayerKV.handleAudioFocus.value) {
                                    // MusicKit owns its own session interruption; the host callback may elect to ignore it.
                                    if (shouldHandleInterruption?.invoke() == false) return@launchPlayerAction
                                    if (playback is com.lalilu.lplayer.core.CorePlayback)
                                        resumeToken = playback.pauseForInterruption()
                                    else playback.pause()
                                }
                            }
                        }
                        AVAudioSessionInterruptionTypeEnded -> {
                            val options = userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber
                            scope.launchPlayerAction {
                                if (generation != bindingGeneration) return@launchPlayerAction
                                val token = resumeToken
                                resumeToken = null
                                if ((options?.unsignedLongValue ?: 0uL) and AVAudioSessionInterruptionOptionShouldResume != 0uL && token != null &&
                                    playback is com.lalilu.lplayer.core.CorePlayback) playback.resumeAfterInterruption(token)
                            }
                        }
                    }
                }
            }
        )
    }
}
