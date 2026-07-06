package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio

/**
 * Data source for media content (lyrics, pictures, playback data).
 */
interface MediaDataSource {
    companion object {
        val Empty = object : MediaDataSource {}
    }

    suspend fun getLyric(song: LAudio): String? = null
    suspend fun getPicture(song: LAudio): MediaData? = null
    suspend fun getMedia(song: LAudio): MediaData? = null
}
