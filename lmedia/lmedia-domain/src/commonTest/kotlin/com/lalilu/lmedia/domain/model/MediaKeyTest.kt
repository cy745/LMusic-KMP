package com.lalilu.lmedia.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MediaKeyTest {
    @Test fun identityRoundTripsWithoutEscapingOrSeparatorAmbiguity() {
        val parts = listOf("", "local", "沙盒🎵", "a:b/c?x=1&y=%20", "0:123", "\n")
        for (source in parts) for (id in parts) {
            val key = MediaKey(source, id)
            assertEquals(key, MediaKey.parse(key.stableKey))
        }
    }

    @Test fun sameRawIdInDifferentSourcesHasDifferentPlaybackIdentity() {
        val first = LAudio(id = "42", mediaSourceName = "local")
        val second = first.copy(mediaSourceName = "sandbox")
        assertNotEquals(first.playbackId, second.playbackId)
        assertEquals(first.mediaKey, MediaKey.parse(first.playbackId))
        assertEquals("42", MediaKey.parse(second.playbackId)?.id)
    }

    @Test fun malformedAndNonCanonicalIdentitiesAreRejected() {
        listOf("", "audio_42", ":abc", "-1:abc", "+1:abc", "01:abc", "4:abc",
            "2147483647:abc", "999999999999:abc", "x:abc").forEach {
            assertNull(MediaKey.parse(it), it)
        }
    }

    @Test fun sourceLengthPreventsConcatenationCollisions() {
        assertNotEquals(MediaKey("ab", "c").stableKey, MediaKey("a", "bc").stableKey)
    }
}
