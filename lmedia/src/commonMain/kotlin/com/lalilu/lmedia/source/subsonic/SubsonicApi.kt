package com.lalilu.lmedia.source.subsonic

import com.lalilu.lmedia.source.subsonic.entity.GetAlbumInfo2Response
import com.lalilu.lmedia.source.subsonic.entity.GetAlbumList2Response
import com.lalilu.lmedia.source.subsonic.entity.GetAlbumResponse
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/**
 * Subsonic API接口
 */
interface SubsonicApi {
    @GET("ping")
    suspend fun ping(): SubsonicResponseWrapper<SubsonicResponse>

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