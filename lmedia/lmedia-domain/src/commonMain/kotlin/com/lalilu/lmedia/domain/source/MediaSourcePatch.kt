package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

/**
 * 数据源在完整快照之外**逐条**发布单曲更新（例如播放过程中顺路补全的元数据）。
 *
 * 完整快照的提交是"整源对账"，代价随库大小增长；单曲更新不能走那条通道，否则每补一首歌都要
 * 重建一次全库关系。实现方通过这条通道让更新立刻可见。
 *
 * 实现方必须自己负责把补丁结果合并回完整快照（见 [MediaSourceStateStore.publishUpdate]），
 * 否则下一次全量提交会用旧数据把它覆盖回去。
 */
interface MediaSourcePatchSource {
    val audioPatches: Flow<LAudio>
}
