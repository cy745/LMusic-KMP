package com.lalilu.lsearch.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * UI state for [com.lalilu.lsearch.screen.SearchScreen].
 *
 * @param keyword current keyword in the bottom input bar; blank means "show all"
 * @param typeFilter active content-type filter; [SearchTypeFilter.All] previews
 *                   songs/albums/artists, the other values show the full list
 *                   for one content type
 */
@Stable
@Immutable
data class SearchState(
    val keyword: String = "",
    val typeFilter: SearchTypeFilter = SearchTypeFilter.All
) {
    val distinctKey: Int = keyword.hashCode() * 31 + typeFilter.ordinal
}

/**
 * Sealed hierarchy of side-events emitted by [com.lalilu.lsearch.viewmodel.SearchVM].
 *
 * Currently unused — kept for parity with [com.lalilu.MviWithIntent] so that
 * future events (e.g. "scroll to top on keyword clear") can be introduced
 * without breaking the public contract.
 */
sealed interface SearchEvent

/**
 * Sealed hierarchy of user-initiated actions for [com.lalilu.lsearch.viewmodel.SearchVM].
 */
sealed interface SearchAction {
    /** Update the search keyword; triggers re-query across all three UseCases. */
    data class UpdateKeyword(val keyword: String) : SearchAction

    /** Switch the active content-type filter (All / Songs / Albums / Artists). */
    data class SelectType(val type: SearchTypeFilter) : SearchAction

    /** Clear the keyword (called by the input bar's clear button). */
    data object ClearKeyword : SearchAction
}
