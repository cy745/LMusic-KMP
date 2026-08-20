package com.lalilu.lsearch.viewmodel

import com.lalilu.lsearch.lsearch.generated.resources.Res
import com.lalilu.lsearch.lsearch.generated.resources.search_filter_all
import com.lalilu.lsearch.lsearch.generated.resources.search_filter_album
import com.lalilu.lsearch.lsearch.generated.resources.search_filter_artist
import com.lalilu.lsearch.lsearch.generated.resources.search_filter_audio
import org.jetbrains.compose.resources.StringResource

/**
 * The currently-active content-type filter for the integrated search page.
 *
 * [All] aggregates results from songs / albums / artists; the other three
 * values restrict results to a single content type.
 */
enum class SearchTypeFilter(val labelRes: StringResource) {
    All(Res.string.search_filter_all),
    Audio(Res.string.search_filter_audio),
    Album(Res.string.search_filter_album),
    Artist(Res.string.search_filter_artist)
}