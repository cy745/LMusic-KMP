package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getAlbumInfo2接口的响应数据封装
 * 对应API: http://your-server/rest/getAlbumInfo2 Since [1.14.0](https://www.subsonic.org/pages/api.jsp#getAlbumInfo2)
 * 用于根据ID3标签组织的媒体库中获取专辑详细信息（如描述、封面图片等）
 *
 * 该接口返回的数据来源于last.fm，包含专辑的详细信息
 */
@Serializable
data class GetAlbumInfo2Response(
    /** 专辑详细信息 */
    @SerialName("albumInfo")
    val albumInfo: AlbumInfo = AlbumInfo()
) : SubsonicResponse() {

    /**
     * 专辑信息详情
     * 包含不同尺寸的专辑封面图片URL和专辑描述信息
     */
    @Serializable
    data class AlbumInfo(
        /** 小尺寸专辑封面图片URL */
        @SerialName("smallImageUrl")
        val smallImageUrl: String = "",

        /** 中尺寸专辑封面图片URL */
        @SerialName("mediumImageUrl")
        val mediumImageUrl: String = "",

        /** 大尺寸专辑封面图片URL */
        @SerialName("largeImageUrl")
        val largeImageUrl: String = "",
        
        /** 超大尺寸专辑封面图片URL */
        @SerialName("xLargeImageUrl")
        val xLargeImageUrl: String = "",

        /** 专辑描述文本 */
        @SerialName("notes")
        val notes: String = "",
        
        /** MusicBrainz标识符 */
        @SerialName("musicBrainzId")
        val musicBrainzId: String = "",
        
        /** Last.fm链接 */
        @SerialName("lastFmUrl")
        val lastFmUrl: String = "",

        /** 音乐类型/风格 */
        @SerialName("genres")
        val genres: List<String> = emptyList(),

        /** 艺术家信息 */
        @SerialName("artists")
        val artists: List<Artist> = emptyList(),

        /** 发行年份 */
        @SerialName("year")
        val year: Int = 0
    ) {
        /**
         * 艺术家信息模型
         * @property id 艺术家唯一标识
         * @property name 艺术家名称
         */
        @Serializable
        data class Artist(
            val id: String = "",
            val name: String = ""
        )
    }
}