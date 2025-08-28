package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getAlbum接口的响应数据封装
 * 对应API: http://your-server/rest/getAlbum Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getAlbum)
 * 用于根据ID3标签组织的媒体库中获取专辑详细信息，包括专辑中的歌曲列表
 */
@Serializable
data class GetAlbumResponse(
    /** 专辑详细信息 */
    @SerialName("album")
    val album: Album = Album()
) : SubsonicResponse() {

    /**
     * 专辑信息模型
     * @property id 专辑唯一标识
     * @property name 专辑名称
     * @property artist 专辑艺术家名称
     * @property artistId 艺术家唯一标识
     * @property coverArt 封面艺术标识（用于获取封面图）
     * @property songCount 专辑包含的歌曲数量
     * @property duration 专辑总时长（秒）
     * @property created 专辑创建时间（ISO格式字符串）
     * @property year 发行年份
     * @property genre 音乐风格
     * @property song 歌曲列表
     */
    @Serializable
    data class Album(
        val id: String = "",
        val name: String = "",
        val artist: String = "",
        @SerialName("artistId")
        val artistId: String = "",
        @SerialName("coverArt")
        val coverArt: String = "",
        @SerialName("songCount")
        val songCount: Int = 0,
        val duration: Int = 0,
        val created: String = "",
        val year: Int = 0,
        val genre: String = "",
        @SerialName("song")
        val song: List<Song> = emptyList()
    )

    /**
     * 歌曲信息模型
     * @property id 歌曲唯一标识
     * @property parent 父级ID（通常是专辑ID）
     * @property title 歌曲标题
     * @property album 专辑名称
     * @property artist 艺术家名称
     * @property track 歌曲在专辑中的序号
     * @property year 发行年份
     * @property genre 音乐风格
     * @property coverArt 封面艺术标识
     * @property size 文件大小（字节）
     * @property contentType 内容类型（如audio/mpeg）
     * @property suffix 文件后缀（如mp3）
     * @property duration 歌曲时长（秒）
     * @property bitRate 比特率（kbps）
     * @property path 文件路径
     */
    @Serializable
    data class Song(
        val id: String = "",
        val parent: String = "",
        val title: String = "",
        val album: String = "",
        val artist: String = "",
        val track: Int = 0,
        val year: Int = 0,
        val genre: String = "",
        @SerialName("coverArt")
        val coverArt: String = "",
        val size: Long = 0L,
        @SerialName("contentType")
        val contentType: String = "",
        val suffix: String = "",
        val duration: Int = 0,
        @SerialName("bitRate")
        val bitRate: Int = 0,
        val path: String = ""
    )
}