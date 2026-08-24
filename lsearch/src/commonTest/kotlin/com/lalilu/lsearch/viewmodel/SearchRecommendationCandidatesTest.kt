package com.lalilu.lsearch.viewmodel

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SearchRecommendationCandidatesTest {

    @Test
    fun initialRecommendations_followExpectedRatioAndAvoidDuplicates() {
        val candidates = SearchRecommendationCandidates.create(
            artistNames = (1..6).map { "Artist $it" },
            albumNames = (1..6).map { "Album $it" },
            audioNames = (1..6).map { "Audio $it" },
        )

        val recommendations = candidates.initialRecommendations(Random(0))

        assertEquals(8, recommendations.size)
        assertEquals(3, recommendations.count { it.type == SearchRecommendationType.Artist })
        assertEquals(3, recommendations.count { it.type == SearchRecommendationType.Album })
        assertEquals(2, recommendations.count { it.type == SearchRecommendationType.Audio })
        assertEquals(
            recommendations.size,
            recommendations.distinctBy { it.keyword.lowercase() }.size,
        )
    }

    @Test
    fun replacement_preservesTypeAndPrefersHiddenKeyword() {
        val candidates = SearchRecommendationCandidates.create(
            artistNames = listOf("Artist 1", "Artist 2", "Artist 3", "Artist 4"),
            albumNames = emptyList(),
            audioNames = emptyList(),
        )
        val displayed = listOf(
            SearchRecommendation(SearchRecommendationType.Artist, "Artist 1"),
            SearchRecommendation(SearchRecommendationType.Artist, "Artist 2"),
            SearchRecommendation(SearchRecommendationType.Artist, "Artist 3"),
        )

        val replacement = candidates.replacementFor(
            current = displayed.first(),
            displayed = displayed,
            random = Random(0),
        )

        assertEquals(SearchRecommendationType.Artist, replacement?.type)
        assertEquals("Artist 4", replacement?.keyword)
        assertNotEquals(displayed.first().keyword, replacement?.keyword)
    }

    @Test
    fun create_filtersBlankNamesAndDeduplicatesIgnoringCase() {
        val candidates = SearchRecommendationCandidates.create(
            artistNames = listOf(" Artist ", "artist", "", "   "),
            albumNames = listOf("Album"),
            audioNames = listOf("Audio"),
        )

        assertEquals(listOf("Artist"), candidates.artistNames)
        assertTrue(candidates.initialRecommendations(Random(0)).all { it.keyword.isNotBlank() })
    }
}
