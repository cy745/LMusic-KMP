package com.lalilu.lmedia.source.mediastore

import com.lalilu.lmedia.domain.model.LAudio

interface Scanner {
    /** 返回一次完整扫描得到的歌曲；运行状态由 MediaSource 维护。 */
    suspend fun scan(): List<LAudio>
}
