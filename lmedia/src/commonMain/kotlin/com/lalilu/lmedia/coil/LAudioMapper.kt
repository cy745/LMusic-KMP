package com.lalilu.lmedia.coil

import coil3.map.Mapper
import coil3.request.Options
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.SourceItem

class LAudioMapper : Mapper<LAudio, SourceItem> {
    override fun map(
        data: LAudio,
        options: Options
    ): SourceItem? {
        return data.sourceItem
    }
}