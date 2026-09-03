package com.lalilu.lmedia.source.external

import com.lalilu.lmedia.domain.model.LAudio
import io.github.vinceglb.filekit.PlatformFile

/** A source-owned, exact match for an externally opened file. */
interface ExternalMediaMatcher {
    suspend fun matchExternalMedia(
        file: PlatformFile,
        candidates: List<LAudio>,
    ): ExternalMediaMatch?
}

data class ExternalMediaMatch(
    val audio: LAudio,
    val basis: ExternalMediaMatchBasis,
)

enum class ExternalMediaMatchBasis {
    SourceLocator,
    SandboxPath,
    ContentDigest,
}
