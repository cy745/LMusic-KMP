package com.lalilu.lmedia

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import org.koin.core.annotation.Single

@Single
class LMediaKV(saver: KVSaver) : KVContext(_prefix = "lmedia", _saver = saver) {

}