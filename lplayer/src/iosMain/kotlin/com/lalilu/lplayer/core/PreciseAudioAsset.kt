@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.lalilu.lplayer.core

import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.Foundation.*

// Keep the native NSDictionary intact. A Kotlin Map boxes even NSNumber(true)
// as KotlinBoolean; AVFoundation then silently ignores this precision option.
// Foundation's JSON parser creates a native CFBoolean and NSDictionaryAsKMap
// preserves that dictionary on the return trip. Do not copy it with toMap().
private val preciseTimingOptions: Map<Any?, *> by lazy {
    val json = NSString.create(string = """{"$AVURLAssetPreferPreciseDurationAndTimingKey":true}""")
        .dataUsingEncoding(NSUTF8StringEncoding)!!
    @Suppress("UNCHECKED_CAST")
    (NSJSONSerialization.JSONObjectWithData(json, 0u, null) as Map<Any?, *>)
}

/** Request a real media time index, including FLAC/MP3 through custom resource loaders. */
internal fun preciseAudioAsset(url: NSURL): AVURLAsset = AVURLAsset(url, preciseTimingOptions)
