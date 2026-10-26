package com.lalilu.lplayer.service

import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.lalilu.lplayer.service.CustomCommand.SeekToNext
import com.lalilu.lplayer.service.CustomCommand.SeekToPrevious

internal enum class CustomCommand(val action: String) {
    SeekToNext(action = "com.lalilu.lplayer.service.command.next"),
    SeekToPrevious(action = "com.lalilu.lplayer.service.command.previous"),
    SelectTrack(action = "com.lalilu.lplayer.service.command.select_track"),
    PlayNext(action = "com.lalilu.lplayer.service.command.play_next"),
    SetMode(action = "com.lalilu.lplayer.service.command.set_mode"),
    PauseAtEnd(action = "com.lalilu.lplayer.service.command.pause_at_end");

    fun toSessionCommand(): SessionCommand = SessionCommand(action, Bundle.EMPTY)
}

internal fun SessionCommand.toCustomCommendOrNull(): CustomCommand? {
    return when (customAction) {
        SeekToNext.action -> SeekToNext
        SeekToPrevious.action -> SeekToPrevious
        CustomCommand.SelectTrack.action -> CustomCommand.SelectTrack
        CustomCommand.PlayNext.action -> CustomCommand.PlayNext
        CustomCommand.SetMode.action -> CustomCommand.SetMode
        CustomCommand.PauseAtEnd.action -> CustomCommand.PauseAtEnd
        else -> null
    }
}

internal fun SessionCommands.Builder.registerCustomCommands(): SessionCommands.Builder = apply {
    addSessionCommands(CustomCommand.entries.map { it.toSessionCommand() })
}
