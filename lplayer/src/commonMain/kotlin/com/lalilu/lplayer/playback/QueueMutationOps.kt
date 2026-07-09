package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio

/**
 * 播放队列操作定义。
 * 只定义"做什么"（操作签名），不关心"怎么执行"。
 *
 * Callers MUST pre-resolve [LItem] to [List]<[LAudio]> before calling
 * these methods — [QueueMutationOps] no longer performs implicit expansion.
 */
interface QueueMutationOps<out R> {
    fun addToStart(items: List<LAudio>): R
    fun addToEnd(items: List<LAudio>): R
    fun addToNext(items: List<LAudio>): R
    fun switchTo(index: Int): R
    fun replaceAll(items: List<LAudio>, index: Int): R
    fun remove(item: LAudio): R
    fun clear(): R
}
