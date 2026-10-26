package com.lalilu.lplayer.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.SeekMap
import com.lalilu.lmedia.content.ByteSpan
import java.util.concurrent.ConcurrentHashMap

/** Uses container seek points to describe continuous disk coverage without guessing time from byte ratios. */
class CachedBufferTimeline(private val ranges: (String) -> Pair<Long, List<ByteSpan>>?) {
    private val maps = ConcurrentHashMap<String, SeekMap>()
    val extractors: ExtractorsFactory = object : ExtractorsFactory {
        private val delegate = DefaultExtractorsFactory()
        override fun createExtractors(): Array<Extractor> = delegate.createExtractors()
        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            delegate.createExtractors(uri, responseHeaders).map { extractor ->
                object : Extractor by extractor {
                    override fun init(output: ExtractorOutput) {
                        extractor.init(object : ExtractorOutput by output {
                            override fun seekMap(seekMap: SeekMap) {
                                maps[uri.toString()] = seekMap
                                output.seekMap(seekMap)
                            }
                        })
                    }
                }
            }.toTypedArray()
    }

    fun bufferedPosition(uri: String, positionMs: Long, durationMs: Long, engineMs: Long): Long {
        val (size, spans) = ranges(uri) ?: return engineMs
        if (durationMs <= 0) return engineMs
        if (spans.any { it.start == 0L && it.endExclusive == size }) return durationMs
        val map = maps[uri]?.takeIf { it.isSeekable && it.durationUs != C.TIME_UNSET } ?: return engineMs
        val start = map.getSeekPoints(positionMs * 1000).first.position
        val span = spans.firstOrNull { start >= it.start && start < it.endExclusive } ?: return engineMs
        // Require both bracketing seek points to be cached, conservative for variable-bit-rate formats.
        var low = positionMs
        var high = minOf(durationMs, map.durationUs / 1000)
        while (low < high) {
            val middle = low + (high - low + 1) / 2
            val points = map.getSeekPoints(middle * 1000)
            if (points.first.position >= span.start && points.second.position < span.endExclusive) low = middle
            else high = middle - 1
        }
        return maxOf(engineMs, low).coerceAtMost(durationMs)
    }
}
