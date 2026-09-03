package com.lalilu.lmedia.source.mediastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaStoreFallbackMatcherTest {
    @Test
    fun matchesUniqueProviderFileByMetadataAndIdentity() {
        val incoming = descriptor(identity = identity(7))
        val candidates = listOf(
            candidate("target", identity = identity(7)),
            candidate("other", displayName = "other.flac", identity = identity(8)),
        )

        assertEquals("target", matchMediaStoreFallback(incoming, candidates))
    }

    @Test
    fun fileIdentityDisambiguatesOtherwiseIdenticalRows() {
        val incoming = descriptor(identity = identity(22))
        val candidates = listOf(
            candidate("first", identity = identity(21)),
            candidate("second", identity = identity(22)),
        )

        assertEquals("second", matchMediaStoreFallback(incoming, candidates))
    }

    @Test
    fun rejectsMetadataMatchWhenFileIdentityDiffers() {
        val incoming = descriptor(identity = identity(31))

        assertNull(
            matchMediaStoreFallback(
                incoming,
                listOf(candidate("different-file", identity = identity(32))),
            )
        )
    }

    @Test
    fun rejectsAmbiguousMetadataWhenIdentityIsUnavailable() {
        val incoming = descriptor(identity = null)
        val candidates = listOf(
            candidate("first", identity = null),
            candidate("second", identity = null),
        )

        assertNull(matchMediaStoreFallback(incoming, candidates))
    }

    @Test
    fun sizeAndRelativePathAreRequiredWhenProvided() {
        val incoming = descriptor(identity = null)
        val candidates = listOf(
            candidate("wrong-size", size = 2048, identity = null),
            candidate("wrong-path", relativePath = "Download/", identity = null),
            candidate("target", identity = null),
        )

        assertEquals("target", matchMediaStoreFallback(incoming, candidates))
    }

    @Test
    fun normalizesRelativePathSlashes() {
        val incoming = descriptor(relativePath = "/Music", identity = null)
        val candidate = candidate("target", relativePath = "Music/", identity = null)

        assertEquals("target", matchMediaStoreFallback(incoming, listOf(candidate)))
    }

    @Test
    fun rejectsDescriptorWithoutDisplayName() {
        assertNull(
            matchMediaStoreFallback(
                descriptor(displayName = null),
                listOf(candidate("target")),
            )
        )
    }

    @Test
    fun extractsRelativePathFromExternalFileProviderUri() {
        assertEquals(
            "Music/Albums/",
            inferExternalRelativePath(
                listOf("external_files", "Music", "Albums", "song.flac")
            )
        )
    }

    @Test
    fun doesNotInferPathFromUnknownProviderRoot() {
        assertNull(
            inferExternalRelativePath(
                listOf("shared_files", "Music", "song.flac")
            )
        )
    }

    private fun descriptor(
        displayName: String? = "song.flac",
        size: Long? = 1024,
        relativePath: String? = "Music/",
        identity: LocalFileIdentity? = identity(1),
    ) = ExternalMediaDescriptor(displayName, size, relativePath, identity)

    private fun candidate(
        value: String,
        displayName: String? = "song.flac",
        size: Long? = 1024,
        relativePath: String? = "Music/",
        identity: LocalFileIdentity? = identity(1),
    ) = MediaStoreFallbackCandidate(value, displayName, size, relativePath, identity)

    private fun identity(inode: Long) = LocalFileIdentity(device = 5, inode = inode)
}
