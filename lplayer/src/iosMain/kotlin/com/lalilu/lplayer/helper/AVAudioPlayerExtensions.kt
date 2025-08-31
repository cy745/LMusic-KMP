package com.lalilu.lplayer.helper

import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.NSUInteger


object AVAudioPlayerDidPlayToEndHelper {
    private var onFinishPlaying: (player: AVAudioPlayer, successfully: Boolean) -> Unit = { _, _ -> }
    private var onEndInterruptionWithFlags: (player: AVAudioPlayer, withFlags: NSUInteger) -> Unit = { _, _ -> }
    private var onDecodeErrorDidOccur: (player: AVAudioPlayer, error: NSError?) -> Unit = { _, _ -> }
    private var onBeginInterruption: (player: AVAudioPlayer) -> Unit = {}
    private var onEndInterruption: (player: AVAudioPlayer) -> Unit = {}

    private val observer = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerEndInterruption(
            player: AVAudioPlayer,
            withFlags: NSUInteger
        ) {
            onEndInterruptionWithFlags.invoke(player, withFlags)
        }

        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onFinishPlaying.invoke(player, successfully)
        }

        override fun audioPlayerDecodeErrorDidOccur(
            player: AVAudioPlayer,
            error: NSError?
        ) {
            onDecodeErrorDidOccur.invoke(player, error)
        }

        override fun audioPlayerBeginInterruption(player: AVAudioPlayer) {
            onBeginInterruption.invoke(player)
        }

        override fun audioPlayerEndInterruption(player: AVAudioPlayer) {
            onEndInterruption.invoke(player)
        }
    }

    fun observe(
        player: AVAudioPlayer,
        onFinishPlaying: (player: AVAudioPlayer, successfully: Boolean) -> Unit = { _, _ -> },
        onEndInterruptionWithFlags: (player: AVAudioPlayer, withFlags: NSUInteger) -> Unit = { _, _ -> },
        onDecodeErrorDidOccur: (player: AVAudioPlayer, error: NSError?) -> Unit = { _, _ -> },
        onBeginInterruption: (player: AVAudioPlayer) -> Unit = {},
        onEndInterruption: (player: AVAudioPlayer) -> Unit = {}
    ) {
        this.onFinishPlaying = onFinishPlaying
        this.onEndInterruptionWithFlags = onEndInterruptionWithFlags
        this.onDecodeErrorDidOccur = onDecodeErrorDidOccur
        this.onBeginInterruption = onBeginInterruption
        this.onEndInterruption = onEndInterruption
        player.setDelegate(observer)
    }
}