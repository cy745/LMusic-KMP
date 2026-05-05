package com.lalilu.lmedia.coil

import coil3.key.Keyer
import coil3.request.Options
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref

class LAlbumKeyer : Keyer<LAlbum> {
    override fun key(data: LAlbum, options: Options): String {
        val firstSong = data.ref<LAudio>().firstOrNull()
        return "${data.idValue()}_${firstSong?.idValue()}_${options.size}"
    }
}