package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getArtists接口的响应数据封装
 * 对应API: http://your-server/rest/getArtists Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getArtists)
 * 用于根据ID3标签组织的媒体库中获取艺术家列表
 *
 * @property ignoredArticles 被忽略的文章词（如"The"、"A"等）
 */
@Serializable
data class GetArtistsResponse(
    @SerialName("artists")
    val artists: Artists = Artists()
) : SubsonicResponse() {

    /**
     * 艺术家列表容器
     * @property ignoredArticles 被忽略的文章词（如"The"、"A"等）
     * @property index 艺术家索引列表，按字母顺序分组
     */
    @Serializable
    data class Artists(
        @SerialName("ignoredArticles")
        val ignoredArticles: String = "",
        @SerialName("index")
        val index: List<Index> = emptyList()
    )

    /**
     * 艺术家索引信息模型（按字母分组）
     * @property name 索引名称（字母）
     * @property artist 艺术家列表
     */
    @Serializable
    data class Index(
        @SerialName("name")
        val name: String = "",
        @SerialName("artist")
        val artist: List<Artist> = emptyList()
    )

    /**
     * 艺术家信息模型
     * @property id 艺术家唯一标识
     * @property name 艺术家名称
     * @property coverArt 封面艺术标识（用于获取封面图）
     * @property albumCount 专辑数量
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
        val albumCount: Int = 0
    )
}