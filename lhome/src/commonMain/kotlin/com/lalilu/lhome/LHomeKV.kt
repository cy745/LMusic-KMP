package com.lalilu.lhome

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import org.koin.core.annotation.Single

@Single
class LHomeKV(saver: KVSaver) : KVContext(_prefix = "lhome", _saver = saver) {
    val dailyRecommends = obtainList<String>("DAILY_RECOMMENDS")
}