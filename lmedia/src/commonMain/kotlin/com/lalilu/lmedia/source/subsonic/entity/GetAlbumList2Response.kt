package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getAlbumList2接口的响应数据封装
 * 对应API: http://your-server/rest/getAlbumList2 Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getAlbumList2)
 * 用于根据ID3标签组织的媒体库中获取专辑列表（如随机、最新、最高评分等）
 */
@Serializable
data class GetAlbumList2Response(
    @SerialName("albumList2")
    val albumList2: AlbumList2 = AlbumList2()
) : SubsonicResponse() {

    /**
     * 专辑列表容器
     * @property album 专辑列表
     */
    @Serializable
    data class AlbumList2(
        val album: List<Album> = emptyList()
    )

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
     * @property userRating 用户评分（0-5）
     * @property genres 音乐风格列表
     * @property musicBrainzId MusicBrainz标识符
     * @property isCompilation 是否为合辑
     * @property sortName 排序用名称
     * @property discTitles 碟片标题列表
     * @property originalReleaseDate 原始发行日期（空对象表示未设置）
     * @property releaseDate 发行日期（空对象表示未设置）
     * @property releaseTypes 发行类型列表
     * @property recordLabels 唱片公司列表
     * @property moods 情绪标签列表
     * @property artists 艺术家列表（详细信息）
     * @property displayArtist 显示用艺术家名称
     * @property explicitStatus 内容分级状态（如"explicit"表示 explicit content）
     * @property version 版本信息（如特别版、豪华版等）
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
        @SerialName("userRating")
        val userRating: Int = 0,
        @SerialName("genres")
        val genres: List<Genre> = emptyList(),
        @SerialName("musicBrainzId")
        val musicBrainzId: String = "",
        @SerialName("isCompilation")
        val isCompilation: Boolean = false,
        @SerialName("sortName")
        val sortName: String = "",
        @SerialName("discTitles")
        val discTitles: List<DiscTitle> = emptyList(),
        @SerialName("originalReleaseDate")
        val originalReleaseDate: OriginalReleaseDate = OriginalReleaseDate(),
        @SerialName("releaseDate")
        val releaseDate: ReleaseDate = ReleaseDate(),
        @SerialName("releaseTypes")
        val releaseTypes: List<String> = emptyList(),
        @SerialName("recordLabels")
        val recordLabels: List<RecordLabel> = emptyList(),
        val moods: List<String> = emptyList(),
        val artists: List<Artist> = emptyList(),
        @SerialName("displayArtist")
        val displayArtist: String = "",
        @SerialName("explicitStatus")
        val explicitStatus: String = "",
        val version: String = ""
    )

    /**
     * 音乐风格信息模型
     * @property name 风格名称
     */
    @Serializable
    data class Genre(
        val name: String = ""
    )

    /**
     * 碟片标题信息模型
     * @property disc 碟片编号
     * @property title 碟片标题
     */
    @Serializable
    data class DiscTitle(
        val disc: Int = 0,
        val title: String = ""
    )

    /**
     * 唱片公司信息模型
     * @property name 唱片公司名称
     */
    @Serializable
    data class RecordLabel(
        val name: String = ""
    )

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

    /**
     * 原始发行日期（空对象表示未设置具体日期）
     */
    @Serializable
    class OriginalReleaseDate

    /**
     * 发行日期（空对象表示未设置具体日期）
     */
    @Serializable
    class ReleaseDate
}