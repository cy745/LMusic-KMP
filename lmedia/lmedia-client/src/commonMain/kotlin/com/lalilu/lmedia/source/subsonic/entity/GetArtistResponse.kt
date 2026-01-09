package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getArtist接口的响应数据封装
 * 对应API: http://your-server/rest/getArtist Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getArtist)
 * 用于根据ID3标签组织的媒体库中获取特定艺术家的详细信息，包括其专辑列表
 *
 * @property artist 艺术家详细信息
 */
@Serializable
data class GetArtistResponse(
    @SerialName("artist")
    val artist: Artist = Artist()
) : SubsonicResponse() {

    /**
     * 艺术家详细信息模型
     * @property id 艺术家唯一标识
     * @property name 艺术家名称
     * @property coverArt 封面艺术标识（用于获取封面图）
     * @property albumCount 专辑数量
     * @property album 专辑列表
     */
    @Serializable
    data class Artist(
        @SerialName("id")
        val id: String = "",
        @SerialName("name")
        val name: String = "",
        @SerialName("coverArt")
        val coverArt: String = "",
        @SerialName("albumCount")
        val albumCount: Int = 0,
        @SerialName("album")
        val album: List<Album> = emptyList()
    )

    /**
     * 专辑信息模型
     * @property id 专辑唯一标识
     * @property name 专辑名称
     * @property coverArt 封面艺术标识（用于获取封面图）
     * @property songCount 歌曲数量
     * @property created 创建时间（ISO格式字符串）
     * @property duration 专辑总时长（秒）
     * @property artist 艺术家名称
     * @property artistId 艺术家唯一标识
     */
    @Serializable
    data class Album(
        @SerialName("id")
        val id: String = "",
        @SerialName("name")
        val name: String = "",
        @SerialName("coverArt")
        val coverArt: String = "",
        @SerialName("songCount")
        val songCount: Int = 0,
        @SerialName("created")
        val created: String = "",
        @SerialName("duration")
        val duration: Int = 0,
        @SerialName("artist")
        val artist: String = "",
        @SerialName("artistId")
        val artistId: String = ""
    )
}