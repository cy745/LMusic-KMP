package com.lalilu.lmedia.source.sandbox

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.external.ExternalMediaMatcher
import io.github.vinceglb.filekit.PlatformFile

interface SandboxMediaSource : MediaSource, ExternalMediaMatcher {
    fun refresh()
    fun cancel()

    suspend fun import(file: PlatformFile, candidates: List<LAudio>): SandboxImportResult

    /** Renames an owned file while keeping the existing audio extension and stable media ID. */
    suspend fun rename(audio: LAudio, newBaseName: String): LAudio

    /** Permanently removes an owned file from the application sandbox. */
    suspend fun delete(audio: LAudio)

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_FILE_SIZE = "file_size"
    }
}

sealed interface SandboxImportResult {
    val audio: LAudio

    data class Existing(
        override val audio: LAudio,
    ) : SandboxImportResult

    data class Imported(
        override val audio: LAudio,
        val snapshotRevision: Long,
    ) : SandboxImportResult
}

class UnsupportedExternalAudioException(message: String) : IllegalArgumentException(message)
