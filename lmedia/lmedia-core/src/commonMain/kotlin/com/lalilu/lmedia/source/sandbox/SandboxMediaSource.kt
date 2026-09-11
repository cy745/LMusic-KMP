package com.lalilu.lmedia.source.sandbox

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.source.external.ExternalMediaMatcher
import io.github.vinceglb.filekit.PlatformFile

interface SandboxMediaSource : MediaSource, ExternalMediaMatcher {
    fun refresh()
    fun cancel()

    suspend fun import(file: PlatformFile, candidates: List<LAudio>): SandboxImportResult

    /** Renames an owned file while keeping the existing audio extension and stable media ID. */
    suspend fun rename(audio: LAudio, newBaseName: String, confirmSnapshot: suspend (Snapshot) -> Unit): LAudio

    /** Confirms downstream state before unlinking; also confirms the restored snapshot on rollback. */
    suspend fun delete(audio: LAudio, confirmSnapshot: suspend (Snapshot) -> Unit)

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_FILE_SIZE = "file_size"
    }
}

/** Application boundary: the UI must not coordinate database and player updates itself. */
interface SandboxFileOperations {
    suspend fun rename(source: SandboxMediaSource, audio: LAudio, newBaseName: String): LAudio
    suspend fun delete(source: SandboxMediaSource, audio: LAudio)
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
