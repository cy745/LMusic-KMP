package com.lalilu.lmedia.coil

import coil3.map.Mapper
import coil3.request.Options
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref

class LAlbumMapper : Mapper<LAlbum, LAudio> {
    override fun map(
        data: LAlbum,
        options: Options
    ): LAudio? {
        return data.ref<LAudio>().firstOrNull()
    }
}