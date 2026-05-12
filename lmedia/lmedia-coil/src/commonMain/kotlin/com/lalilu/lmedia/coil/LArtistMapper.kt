package com.lalilu.lmedia.coil

import coil3.map.Mapper
import coil3.request.Options
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref

class LArtistMapper : Mapper<LArtist, LAudio> {
    override fun map(
        data: LArtist,
        options: Options
    ): LAudio? {
        return data.ref<LAudio>().firstOrNull()
    }
}
