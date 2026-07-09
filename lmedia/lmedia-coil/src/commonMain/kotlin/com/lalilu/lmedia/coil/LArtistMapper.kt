package com.lalilu.lmedia.coil

import coil3.map.Mapper
import coil3.request.Options
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio

/**
 * Maps [LArtist] to its first associated [LAudio] for cover art loading.
 * Since domain models no longer carry Linkable refs, this mapper returns null
 * until a dedicated artist-art fetcher is implemented.
 */
class LArtistMapper : Mapper<LArtist, LAudio> {
    override fun map(
        data: LArtist,
        options: Options
    ): LAudio? {
        return null
    }
}
