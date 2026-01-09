package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getArtistInfo2接口的响应数据封装
 * 对应API: http://your-server/rest/getArtistInfo2 Since [1.11.0](https://www.subsonic.org/pages/api.jsp#getArtistInfo2)
 * 用于获取特定艺术家的详细信息，如传记、图片、相似艺术家等，数据来源于last.fm
 *
 * @property artistInfo2 艺术家详细信息
 */
@Serializable
data class GetArtistInfo2Response(
    @SerialName("artistInfo2")
    val artistInfo2: ArtistInfo2 = ArtistInfo2()
) : SubsonicResponse() {

    /**
     * 艺术家详细信息模型
     * @property biography 艺术家传记
     * @property musicBrainzId MusicBrainz标识符
     * @property lastFmUrl Last.fm链接
     * @property smallImageUrl 小尺寸图片URL
     * @property mediumImageUrl 中等尺寸图片URL
     * @property largeImageUrl 大尺寸图片URL
     * @property similarArtist 相似艺术家列表
     */
    @Serializable
    data class ArtistInfo2(
        @SerialName("biography")
        val biography: String = "",
        @SerialName("musicBrainzId")
        val musicBrainzId: String = "",
        @SerialName("lastFmUrl")
        val lastFmUrl: String = "",
        @SerialName("smallImageUrl")
        val smallImageUrl: String = "",
        @SerialName("mediumImageUrl")
        val mediumImageUrl: String = "",
        @SerialName("largeImageUrl")
        val largeImageUrl: String = "",
        @SerialName("similarArtist")
        val similarArtist: List<SimilarArtist> = emptyList()
    )

    /**
     * 相似艺术家信息模型
     * @property id 艺术家唯一标识
     * @property name 艺术家名称
     * @property coverArt 封面艺术标识（用于获取封面图，可选）
     * @property albumCount 专辑数量（可选）
     */
    @Serializable
    data class SimilarArtist(
        @SerialName("id")
        val id: String = "",
        @SerialName("name")
        val name: String = "",
        @SerialName("coverArt")
        val coverArt: String? = null,
        @SerialName("albumCount")
        val albumCount: Int? = null
    )
}