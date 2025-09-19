package com.lalilu.llyric.parser

import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricParser

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.serialization.XML
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * TTML格式歌词解析器
 * TTML (Timed Text Markup Language) 是一种基于XML的字幕格式
 */
object TtmlParser : LyricParser {
    /**
     * 匹配时间格式的正则表达式，支持 mm:ss.SSS 或 ss.SSS 格式
     */
    private val REGEX_TIME = Regex("(?:(\\d+):)?(\\d+)\\.(\\d{3})")

    /**
     * 匹配span元素间空格的正则表达式
     */
    private val PATTERN_SPACE_IN_LINE = Regex("""</span>(\s+)<span""")

    /**
     * 匹配TTML中单独的'&'符号的正则表达式
     */
    private val PATTERN_AND_IN_TTML = Regex("""&(?!(?:[a-zA-Z]+|#\d+|#[xX][a-fA-F0-9]+);)""")

    /**
     * XML解析器实例，用于解析TTML格式的歌词
     */
    @OptIn(ExperimentalXmlUtilApi::class)
    private val xml = XML(
        serializersModule = SerializersModule {
            polymorphic(Any::class) {
                defaultDeserializer { String.serializer() }
                subclass(String::class)
                subclass(Element::class)
            }
        }
    ) {
        defaultPolicy {
            autoPolymorphic = true
            ignoreUnknownChildren()
            fast_0_90_2 { }
        }
    }

    /**
     * 解析TTML格式的歌词字符串
     * @param lyric TTML格式的歌词字符串
     * @return 解析后的歌词项列表
     */
    @OptIn(ExperimentalTime::class)
    override fun parse(lyric: String): List<LyricItem> {
        if (lyric.isBlank()) return emptyList()
        var actualLyric = lyric

        // 匹配span元素间（词与词之间）的空格
        actualLyric = PATTERN_SPACE_IN_LINE.replace(actualLyric) { result ->
            val group = result.groups[1] ?: return@replace result.value

            result.value.replace(
                oldValue = group.value,
                newValue = """<span begin="00:00.000" end="00:00.000">${group.value}</span>"""
            )
        }

        // 替换所有单独的'&'为"__AND__"
        actualLyric = PATTERN_AND_IN_TTML.replace(actualLyric) { "__AND__" }

        val randomKeyPrefix = Clock.System.now().toEpochMilliseconds()
        val ttml = runCatching { xml.decodeFromString<TTML>(actualLyric) }
            .getOrElse { null }
            ?: return emptyList()

        val divs = ttml.body.div.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        return divs.map { div ->
            val songPart = div.songPart?.takeIf { it.isNotBlank() }?.let {
                LyricItem.FixedTips(
                    content = it.replace("__AND__", "&"),
                    time = parseTime(div.begin),
                    key = "${randomKeyPrefix}_${it}_${div.begin}"
                )
            }

            listOfNotNull<LyricItem>(songPart) + div.p.map { sentence ->
                val sentenceStart = parseTime(sentence.begin)
                val sentenceEnd = parseTime(sentence.end)

                // 解析翻译内容
                val translations = sentence.span.filter { it.isTranslation() }.mapNotNull {
                    val content = it.content()
                    if (content.isNullOrBlank()) return@mapNotNull null

                    LyricItem.WordsLyric.Translation(
                        content = content.replace("__AND__", "&"),
                        lang = it.lang ?: "unknown"
                    )
                }

                // 解析歌词单词
                val words = sentence.span.filter { !it.isTranslation() }.mapNotNull { word ->
                    val content = word.content()
                    if (content.isNullOrEmpty()) return@mapNotNull null

                    LyricItem.WordsLyric.WordWithTiming(
                        startTime = parseTime(word.begin),
                        endTime = parseTime(word.end),
                        content = content.replace("__AND__", "&")
                    )
                }

                // 解析背景歌词
                val xBgWords = sentence.span.filter { it.role == "x-bg" }
                    .mapNotNull { it.children() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, spans ->
                        val words = spans.mapNotNull { word ->
                            val content = word.content()
                            if (content.isNullOrEmpty()) return@mapNotNull null

                            LyricItem.WordsLyric.WordWithTiming(
                                startTime = parseTime(word.begin),
                                endTime = parseTime(word.end),
                                content = content.replace("__AND__", "&")
                            )
                        }
                        val start = words
                            .filter { it.startTime > 0 }
                            .minOf { it.startTime }
                        val end = words.maxOf { it.endTime }

                        LyricItem.WordsLyric(
                            key = "${randomKeyPrefix}_${sentence.key}_xbg_$index",
                            agent = sentence.agent ?: "",
                            startTime = start,
                            endTime = end,
                            translation = emptyList(),
                            words = fixedWordsTime(start, end, words)
                        )
                    }

                listOf(
                    LyricItem.WordsLyric(
                        key = "${randomKeyPrefix}_${sentence.key}",
                        agent = sentence.agent ?: "",
                        startTime = sentenceStart,
                        endTime = sentenceEnd,
                        translation = translations,
                        words = fixedWordsTime(sentenceStart, sentenceEnd, words)
                    )
                ) + xBgWords
            }.flatten()
        }.flatten().sorted()
    }

    /**
     * 修正歌词单词的时间，处理开始时间和结束时间都为0的情况
     * @param sentenceStart 句子开始时间
     * @param sentenceEnd 句子结束时间
     * @param words 歌词单词列表
     * @return 修正时间后的歌词单词列表
     */
    private fun fixedWordsTime(
        sentenceStart: Long,
        sentenceEnd: Long,
        words: List<LyricItem.WordsLyric.WordWithTiming>
    ): List<LyricItem.WordsLyric.WordWithTiming> {
        return words.mapIndexed { index, word ->
            if (word.startTime == word.endTime && word.startTime == 0L) {
                return@mapIndexed word.copy(
                    startTime = words.getOrNull(index - 1)?.endTime ?: sentenceStart,
                    endTime = words.getOrNull(index + 1)?.startTime ?: sentenceEnd
                )
            }
            word
        }
    }

    /**
     * 解析时间字符串为毫秒数
     * @param time 时间字符串，格式为mm:ss.SSS或ss.SSS
     * @return 时间毫秒数
     */
    private fun parseTime(time: String?): Long {
        if (time.isNullOrBlank()) return 0

        val matcher = REGEX_TIME.matchEntire(time)
        if (matcher == null) return 0

        val minute = matcher.groups[1]?.value?.toLongOrNull() ?: 0L
        val second = matcher.groups[2]?.value?.toLongOrNull() ?: 0L
        val milString = matcher.groups[3]?.value ?: "0"
        var mil = milString.toLongOrNull() ?: 0L
        when (milString.length) {
            1 -> mil *= 100
            2 -> mil *= 10
            4 -> mil /= 10
            5 -> mil /= 100
            6 -> mil /= 1000
        }

        return minute * 60 * 1000 + second * 1000 + mil
    }
}