package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.Options
import com.lalilu.lmedia.entity.SourceItem

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SourceItemFetcherFactory() : Fetcher.Factory<SourceItem> {
    override fun create(data: SourceItem, options: Options, imageLoader: ImageLoader): Fetcher?
}


