package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio

/**
 * 可选能力：把「这首歌的内容还没准备好」和「这首歌就是没有这个内容」分开。
 *
 * 为什么需要它：`getPicture` 返回 null 时上层分不清这两种情况，只能都当成"没有"——
 * 于是网络歌曲（封面要等提取/下载，实测 1~2 秒）在封面到达之前就已经按失败渲染了，
 * 用户看到的就是"先闪一下底色/占位，再跳成封面"。本地来源没有这个问题：它返回 null
 * 就是真的没有，立刻走占位才是对的，所以只有"内容要等它到了才有"的来源才实现这个接口。
 *
 * 不实现 = 立即有结论，上层不会等待。
 */
interface MediaItemContentPending {

    /**
     * 这首歌的内容是否**还在路上**：`true` 表示"现在没有，但可能会到"，上层应当在预算内
     * 继续等；`false` 表示"就是没有"，上层应当立刻走占位。
     *
     * 必须是**廉价且无副作用**的判断（解析器会在等待期间反复调用它），不要把网络请求或
     * 大文件读取放进来。
     */
    fun isItemContentPending(audio: LAudio): Boolean
}
