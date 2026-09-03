package com.lalilu.lmedia.source.sandbox

import com.lalilu.common.ext.md5
import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertEquals

class SandboxIdentityTest {
    @Test
    fun `keeps legacy iOS relative path and audio id`() {
        val root = "/var/mobile/Containers/Data/Application/example/Documents"
        val file = "$root/Imported/song.flac"
        val relative = "/Imported/song.flac"

        assertEquals(
            relative,
            AbstractSandboxMediaSource.sandboxRelativePath(root, file),
        )
        assertEquals(
            "${LAudio.ID_PREFIX}${relative.md5()}",
            AbstractSandboxMediaSource.sandboxAudioId(root, file),
        )
    }

    @Test
    fun `sanitizes external display names without losing extension`() {
        assertEquals(
            "track_name_.mp3",
            AbstractSandboxMediaSource.sanitizeFileName("../folder/track:name?.mp3"),
        )
        assertEquals("audio", AbstractSandboxMediaSource.sanitizeFileName("..."))
    }

    @Test
    fun `rename preserves the original audio extension`() {
        assertEquals(
            "Renamed song.flac",
            AbstractSandboxMediaSource.renamedFileName("original.flac", "Renamed song"),
        )
        assertEquals(
            "Renamed song.flac",
            AbstractSandboxMediaSource.renamedFileName("original.flac", "Renamed song.flac"),
        )
        assertEquals(
            "Renamed song.flac",
            AbstractSandboxMediaSource.renamedFileName("original.flac", "Renamed song.FLAC"),
        )
    }

    @Test
    fun `rename sanitizes path characters`() {
        assertEquals(
            "new_name.flac",
            AbstractSandboxMediaSource.renamedFileName("original.flac", "../new:name"),
        )
    }
}
