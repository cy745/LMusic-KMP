package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSInteger

sealed class MPRemoteCommandHandlerStatus(
    val value: Long = 0
) : NSInteger(value) {
    override fun toByte(): Byte = value.toByte()
    override fun toShort(): Short = value.toShort()


    data object Success : MPRemoteCommandHandlerStatus(0) {
        private fun readResolve(): Any = Success
    }

    data object NoSuchContent : MPRemoteCommandHandlerStatus(1) {
        private fun readResolve(): Any = NoSuchContent
    }

    data object NoActionableNowPlayingItem : MPRemoteCommandHandlerStatus(2) {
        private fun readResolve(): Any = NoActionableNowPlayingItem
    }

    data object DeviceNotFound : MPRemoteCommandHandlerStatus(3) {
        private fun readResolve(): Any = DeviceNotFound
    }

    data object CommandFailed : MPRemoteCommandHandlerStatus(4) {
        private fun readResolve(): Any = CommandFailed
    }
}