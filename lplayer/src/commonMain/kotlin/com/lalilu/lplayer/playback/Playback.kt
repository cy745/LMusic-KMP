package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGroupItem
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Playback {

    /**
     * 获取当前正在播放的播放列表
     */
    fun playlist(): Flow<List<LItem>>

    /**
     * 更新当前的播放列表
     */
    fun updatePlaylist(playlist: List<LItem>)

    /**
     * 获取当前正在播放的播放列表的展开列表
     */
    fun flattenPlaylist(): StateFlow<List<LAudio>>

    /**
     * 获取当前正在播放的元素
     */
    fun currentItem(): Flow<Pair<LGroupItem?, LAudio?>>

    /**
     * 获取当前播放元素的索引
     *
     * @return 父元素索引，子元素索引
     */
    fun currentItemIndex(): Flow<Pair<Int, Int>>
}