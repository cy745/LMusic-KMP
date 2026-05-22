package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem

/**
 * 播放队列操作定义。
 * 只定义"做什么"（操作签名），不关心"怎么执行"。
 * QueueUpdateRequest 基于此接口，新增操作时编译器会强制它同步实现。
 */
interface QueueMutationOps<out R> {
    fun addToStart(item: LItem): R
    fun addToEnd(item: LItem): R
    fun addToNext(item: LItem): R
    fun switchTo(index: Int): R
    fun replaceAll(items: List<LAudio>, index: Int): R
    fun remove(item: LAudio): R
    fun clear(): R
}
