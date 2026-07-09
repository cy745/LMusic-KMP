package com.lalilu.lmedia.source

/**
 * SourceItem is a platform-specific sealed interface identifying the origin
 * of a media file. Retained for backward compatibility with coil fetchers.
 * New code should use [MediaData] from the domain layer instead.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect sealed interface SourceItem {
    val key: String
}

object SourceItemDefaults {
    object Empty : SourceItem {
        override val key: String = "Empty"
    }

    object RequestUrl : SourceItem {
        override val key: String = "RequestUrl"
    }
}
