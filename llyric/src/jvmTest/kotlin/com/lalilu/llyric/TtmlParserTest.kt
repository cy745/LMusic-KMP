package com.lalilu.llyric

import com.lalilu.llyric.parser.TtmlParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtmlParserTest {
    private fun fixture(): String = checkNotNull(
        javaClass.getResource("/lyrics/missing-container-times.ttml")
    ).readText()

    @Test
    fun `embedded lyrics retain sentences words translations and agents`() {
        val items = TtmlParser.parse(fixture()).filterIsInstance<LyricItem.WordsLyric>()
        assertEquals(97, items.size)
        assertEquals(971, items.sumOf { it.words.size })
        assertEquals(5, items.sumOf { it.translation.size })
        assertTrue(items.all { it.words.isNotEmpty() && it.startTime < it.endTime })
        val words = items.flatMap { it.words }
        assertTrue(words.all { it.startTime <= it.endTime })
        // 原始标签中包含 15 个零时长字词（主要是右括号），应保留原始时间。
        assertEquals(15, words.count { it.startTime == it.endTime })
        assertEquals(items.map { it.startTime }.sorted(), items.map { it.startTime })
        assertEquals(items.size, items.map { it.key }.toSet().size)

        val sentence = items.single { it.startTime == 24_329L }
        assertEquals(26_393L, sentence.endTime)
        assertEquals("但慢慢都会be ok", sentence.getSentenceContent())
        assertEquals("v1", sentence.agent)
        assertEquals(listOf(LyricItem.WordsLyric.Translation("但慢慢都会变好", "zh-CN")), sentence.translation)
        assertEquals(24_329L, sentence.words.first().startTime)
        assertEquals(26_393L, sentence.words.last().endTime)
        assertEquals("v2", items.single { it.startTime == 32_567L }.agent)
    }

    @Test
    fun `public lyric entry point preserves all TTML sentences`() {
        val items = LyricUtils.parseLrc(fixture()).orEmpty()
        assertEquals(97, items.filterIsInstance<LyricItem.WordsLyric>().size)
    }

    @Test
    fun `container timing remains compatible when provided`() {
        val original = TtmlParser.parse(fixture()).filterIsInstance<LyricItem.WordsLyric>()
        val withTimes = fixture().replace("<body>", "<body dur=\"05:00.000\">")
            .replace("<div>", "<div begin=\"00:00.000\" end=\"05:00.000\">")
        val parsed = TtmlParser.parse(withTimes).filterIsInstance<LyricItem.WordsLyric>()
        assertEquals(97, parsed.size)
        assertEquals(original.map { it.copy(key = "") }, parsed.map { it.copy(key = "") })
    }

    @Test
    fun `each container timing attribute can be omitted independently`() {
        for ((body, div) in listOf(
            "" to "begin=\"00:00.000\" end=\"00:02.000\"",
            "dur=\"00:02.000\"" to "end=\"00:02.000\"",
            "dur=\"00:02.000\"" to "begin=\"00:00.000\""
        )) {
            val items = TtmlParser.parse(document(body, div))
            assertEquals(1, items.size, "body=$body, div=$div")
            val line = items.single() as LyricItem.WordsLyric
            assertEquals(1_000L, line.startTime)
            assertEquals(2_000L, line.endTime)
            assertEquals("test", line.getSentenceContent())
        }
    }

    @Test
    fun `section without begin uses earliest sentence time`() {
        val items = TtmlParser.parse(document("", "itunes:songPart=\"Verse\""))
        val section = items.filterIsInstance<LyricItem.FixedTips>().single()
        assertEquals("Verse", section.content)
        assertEquals(1_000L, section.time)
    }

    @Test
    fun `explicit section start is preserved`() {
        val items = TtmlParser.parse(document("", "itunes:songPart=\"Verse\" begin=\"00:00.500\""))
        assertEquals(500L, items.filterIsInstance<LyricItem.FixedTips>().single().time)
    }

    @Test
    fun `malformed XML and empty containers return no lyrics`() {
        assertTrue(TtmlParser.parse("<tt>").isEmpty())
        assertTrue(TtmlParser.parse(document("", "").replace(
            "<p begin=\"00:01.000\" end=\"00:02.000\"><span begin=\"00:01.000\" end=\"00:02.000\">test</span></p>", ""
        )).isEmpty())
    }

    private fun document(bodyAttributes: String, divAttributes: String) = """
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
          <head/>
          <body $bodyAttributes><div $divAttributes>
            <p begin="00:01.000" end="00:02.000"><span begin="00:01.000" end="00:02.000">test</span></p>
          </div></body>
        </tt>
    """.trimIndent()
}
