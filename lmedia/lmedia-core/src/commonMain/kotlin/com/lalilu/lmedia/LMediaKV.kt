package com.lalilu.lmedia

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import org.koin.core.annotation.Single

@Single
class LMediaKV(saver: KVSaver) : KVContext(_prefix = "lmedia", _saver = saver) {
    /** 数据源成功写入后，是否自动删除数据库中已标记为不可用的歌曲。 */
    val clearUnavailableAfterSync = obtain("clearUnavailableAfterSync", false)
}
