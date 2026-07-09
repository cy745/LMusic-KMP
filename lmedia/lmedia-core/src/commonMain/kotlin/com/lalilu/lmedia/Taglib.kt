package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Metadata

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object Taglib {
    /**
     * 获取Taglib的版本号
     */
    fun version(): String

    /**
     * 读取文件元数据
     *
     * @param fd 文件描述符
     * @return 文件元数据
     */
    suspend fun readMetadata(fd: Int): Metadata?
    suspend fun readMetadata(path: String): Metadata?

    /**
     * 读取文件歌词
     *
     * @param fd 文件描述符
     * @return 歌词文本
     */
    suspend fun getLyric(fd: Int): String?
    suspend fun getLyric(path: String): String?

    /**
     * 读取歌曲文件中的图片
     *
     * @param fd 文件描述符
     * @return 图片数据
     */
    suspend fun getPicture(fd: Int): ByteArray?
    suspend fun getPicture(path: String): ByteArray?
}