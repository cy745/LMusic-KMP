package com.lalilu.lmedia.coil

import coil3.key.Keyer
import coil3.request.Options
import com.lalilu.lmedia.entity.SourceItem

class SourceItemKeyer : Keyer<SourceItem> {
    override fun key(data: SourceItem, options: Options): String? {
        return data.key
    }
}