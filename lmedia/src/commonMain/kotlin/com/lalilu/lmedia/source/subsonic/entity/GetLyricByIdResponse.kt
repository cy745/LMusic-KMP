package com.lalilu.lmedia.source.subsonic.entity

import com.lalilu.lmedia.source.subsonic.SubsonicResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * getLyricsById接口的响应数据封装
 * 对应API: http://your-server/rest/getLyricsById (需要确认具体版本)
 * 用于根据ID获取特定歌曲的歌词信息，包括结构化歌词数据
 *
 * @property lyricsList 歌词列表信息
 */
@Serializable
data class GetLyricByIdResponse(
    @SerialName("lyricsList")
    val lyricsList: LyricsList = LyricsList()
) : SubsonicResponse() {

    /**
     * 歌词列表信息模型
     * @property structuredLyrics 结构化歌词列表
     */
    @Serializable
    data class LyricsList(
        @SerialName("structuredLyrics")
        val structuredLyrics: List<StructuredLyrics> = emptyList()
    ) {

        /**
         * 结构化歌词信息模型
         * @property displayArtist 显示用艺术家名称
         * @property displayTitle 显示用标题
         * @property lang 歌词语言
         * @property synced 是否为同步歌词
         * @property line 歌词行列表
         */
        @Serializable
        data class StructuredLyrics(
            val displayArtist: String = "",
            val displayTitle: String = "",
            val lang: String = "",
            val synced: Boolean = false,
            val line: List<StructuredLyricsLine> = emptyList()
        ) {

            /**
             * 结构化歌词行信息模型
             * @property start 歌词行开始时间（毫秒）
             * @property value 歌词行文本内容
             */
            @Serializable
            data class StructuredLyricsLine(
                val start: Long = 0,
                val value: String = ""
            )
        }
    }
}

/**
 * 将结构化歌词转换为LRC格式的歌词内容
 */
fun GetLyricByIdResponse.LyricsList.StructuredLyrics.toLrcContent(): String {
    if (line.isEmpty()) return ""

    return line.sortedBy { it.start }
        .joinToString("\n") { "${milliToLrcTimeTag(it.start)}${it.value}" }
}

/**
 * 将毫秒时间转换为LRC格式的时间标签
 */
private fun milliToLrcTimeTag(milli: Long): String {
    val seconds = milli / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val milli = milli % 1000

    val minutesStr = if (minutes < 10) "0$minutes" else minutes.toString()
    val remainingSecondsStr = if (remainingSeconds < 10) "0$remainingSeconds" else remainingSeconds.toString()
    val milliStr = if (milli < 10) "00$milli" else if (milli < 100) "0$milli" else milli.toString()

    return "[$minutesStr:$remainingSecondsStr.$milliStr]"
}