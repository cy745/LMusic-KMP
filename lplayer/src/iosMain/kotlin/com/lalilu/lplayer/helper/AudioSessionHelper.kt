package com.lalilu.lplayer.helper

import co.touchlab.kermit.Logger
import com.lalilu.lplayer.playback.Playback
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object AudioSessionHelper {
    private val audioSession by lazy { AVAudioSession.sharedInstance() }
    private val logger = Logger.withTag("AudioSessionInterruptionHelper")
    private val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    fun debugLog(message: String) = logger.i(messageString = message)

    fun setUpAudioSession(): Boolean {
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

    fun ensureAudioSessionActive() {
        audioSession.setActive(
            active = true,
            withOptions = 0u,
            error = errorPtr.ptr
        )
    }

    fun bindPlayback(playback: Playback) {
        NSNotificationCenter.defaultCenter().addObserverForName(
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
                        AVAudioSessionInterruptionTypeBegan -> playback.pause()
                        AVAudioSessionInterruptionTypeEnded -> {
                            val options = userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber
                            if (options?.unsignedLongValue == AVAudioSessionInterruptionOptionShouldResume) {
                                playback.play()
                            }
                        }
                    }
                }
            }
        )
    }
}