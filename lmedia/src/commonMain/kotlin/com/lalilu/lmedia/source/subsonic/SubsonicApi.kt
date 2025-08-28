package com.lalilu.lmedia.source.subsonic

import com.lalilu.lmedia.source.subsonic.entity.*
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/**
 * Subsonic API接口
 */
interface SubsonicApi {
    @GET("ping")
    suspend fun ping(): SubsonicResponseWrapper<SubsonicResponse>

    /**
     * http://your-server/rest/getArtists Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getArtists)
     *
     * Similar to getIndexes, but organizes music according to ID3 tags.
     * 与getIndexes类似，但按ID3标签组织音乐
     *
     * @param musicFolderId If specified, only return artists in the music folder with the given ID.
     *                     如果指定，则仅返回指定音乐文件夹中的艺术家
     * @return 包含按字母索引分组的艺术家列表的响应包装类
     */
    @GET("getArtists")
    suspend fun getArtists(
        @Query("musicFolderId") musicFolderId: String? = null
    ): SubsonicResponseWrapper<GetArtistsResponse>

    /**
     * http://your-server/rest/getArtist Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getArtist)
     *
     * Returns details for an artist, including a list of albums. This method organizes music according to ID3 tags.
     * 返回艺术家的详细信息，包括专辑列表。此方法按ID3标签组织音乐
     *
     * @param id The artist ID. 艺术家ID（必填）
     * @return 包含艺术家详细信息和专辑列表的响应包装类
     */
    @GET("getArtist")
    suspend fun getArtist(
        @Query("id") id: String
    ): SubsonicResponseWrapper<GetArtistResponse>

    /**
     * http://your-server/rest/getArtistInfo2 Since [1.11.0](https://www.subsonic.org/pages/api.jsp#getArtistInfo2)
     *
     * Returns artist info with biography, image URLs and similar artists, using data from last.fm.
     * 返回艺术家信息，包括传记、图片URL和相似艺术家，数据来源于last.fm
     *
     * @param id The artist ID. 艺术家ID（必填）
     * @return 包含艺术家详细信息的响应包装类，成功时返回艺术家的传记、图片URL、相似艺术家等信息
     */
    @GET("getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String
    ): SubsonicResponseWrapper<GetArtistInfo2Response>

    /**
     * http://your-server/rest/getAlbumList2 Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getAlbumList2)
     *
     * 获取基于ID3标签组织的专辑列表（如随机、最新、最高评分等）
     * 与getAlbumList类似，但基于ID3标签而非文件结构
     *
     * @param type 列表类型（必填），必须为以下值之一：
     *             - random: 随机专辑
     *             - newest: 最新添加
     *             - frequent: 常听
     *             - recent: 最近播放
     *             - starred: 已收藏
     *             - alphabeticalByName: 按名称字母序
     *             - alphabeticalByArtist: 按艺术家字母序
     *             - byYear: 按年份范围（需配合fromYear和toYear）
     *             - byGenre: 按风格（需配合genre参数）
     * @param size 返回专辑数量，默认10，最大500
     * @param offset 列表偏移量，用于分页，默认0
     * @param fromYear 年份范围起始（仅type=byYear时必填），若大于toYear则按倒序返回
     * @param toYear 年份范围结束（仅type=byYear时必填）
     * @param genre 风格名称（仅type=byGenre时必填），如"Rock"
     * @param musicFolderId 音乐文件夹ID（可选），仅返回该文件夹下的专辑
     * @return 包含专辑列表的响应包装类
     */
    @GET("getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): SubsonicResponseWrapper<GetAlbumList2Response>

    /**
     * http://your-server/rest/getAlbumInfo2 Since [1.14.0](https://www.subsonic.org/pages/api.jsp#getAlbumInfo2)
     *
     * 获取专辑详情信息（基于ID3标签组织音乐），返回专辑的描述、图片URL等信息
     * 数据来源于last.fm
     *
     * @param id 专辑ID（必填），用于指定要获取信息的专辑
     * @return 包含专辑信息的响应包装类，成功时返回专辑的不同尺寸封面图片URL、描述等信息
     */
    @GET("getAlbumInfo2")
    suspend fun getAlbumInfo2(
        @Query("id") id: String
    ): SubsonicResponseWrapper<GetAlbumInfo2Response>

    /**
     * http://your-server/rest/getAlbum Since [1.8.0](https://www.subsonic.org/pages/api.jsp#getAlbum)
     *
     * 返回专辑的详细信息，包括专辑中的歌曲列表
     * 此方法根据ID3标签组织音乐
     *
     * @param id 专辑ID（必填），用于指定要获取详细信息的专辑
     * @return 包含专辑详细信息和歌曲列表的响应包装类
     */
    @GET("getAlbum")
    suspend fun getAlbum(
        @Query("id") id: String
    ): SubsonicResponseWrapper<GetAlbumResponse>
}