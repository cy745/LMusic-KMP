package com.lalilu.lmedia.coil

import coil3.map.Mapper
import coil3.request.Options
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio

/**
 * Maps [LAlbum] to its first associated [LAudio] for cover art loading.
 * Since domain models no longer carry Linkable refs, this mapper
 * resolves via the first audio found in extra data, or returns null.
 *
 * TODO: In Phase D, replace with direct album-art fetcher.
 */
class LAlbumMapper : Mapper<LAlbum, LAudio> {
    override fun map(
        data: LAlbum,
        options: Options
    ): LAudio? {
        return null
    }
}
