package com.lalilu.lmedia.source.mediastore

/** Stable local-file identity obtained from an opened file descriptor. */
internal data class LocalFileIdentity(
    val device: Long,
    val inode: Long,
)

internal data class ExternalMediaDescriptor(
    val displayName: String?,
    val size: Long?,
    val relativePath: String?,
    val identity: LocalFileIdentity?,
)

internal data class MediaStoreFallbackCandidate<T>(
    val value: T,
    val displayName: String?,
    val size: Long?,
    val relativePath: String?,
    val identity: LocalFileIdentity?,
)

/**
 * Selects a MediaStore item for a provider URI that Android cannot convert with
 * `MediaStore.getMediaUri()`.
 *
 * Every piece of metadata supplied by the provider is treated as a constraint. When a stable
 * file-descriptor identity is available it must match; otherwise metadata is accepted only when
 * it identifies exactly one MediaStore row.
 */
internal fun <T> matchMediaStoreFallback(
    incoming: ExternalMediaDescriptor,
    candidates: List<MediaStoreFallbackCandidate<T>>,
): T? {
    val displayName = incoming.displayName?.takeIf { it.isNotBlank() } ?: return null
    var matches = candidates.filter { it.displayName == displayName }

    incoming.size?.takeIf { it >= 0L }?.let { size ->
        matches = matches.filter { it.size == size }
    }
    incoming.relativePath.normalizedRelativePath()?.let { relativePath ->
        matches = matches.filter {
            it.relativePath.normalizedRelativePath() == relativePath
        }
    }
    incoming.identity?.let { identity ->
        matches = matches.filter { it.identity == identity }
    }

    return matches.singleOrNull()?.value
}

/** Extracts the relative directory exposed by FileProvider roots such as `external_files`. */
internal fun inferExternalRelativePath(pathSegments: List<String>): String? {
    val externalRoot = pathSegments.indexOf("external_files")
    if (externalRoot < 0 || externalRoot >= pathSegments.lastIndex) return null
    return pathSegments.subList(externalRoot + 1, pathSegments.lastIndex)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "/", postfix = "/")
}

private fun String?.normalizedRelativePath(): String? = this
    ?.trim()
    ?.trim('/')
    ?.takeIf { it.isNotEmpty() }
    ?.plus('/')
