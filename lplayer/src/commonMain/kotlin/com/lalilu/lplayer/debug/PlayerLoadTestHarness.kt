package com.lalilu.lplayer.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation-only fault injection. Disabled unless the Android debug activity opens it. */
object PlayerLoadTestHarness {
    data class Profile(
        val name: String = "disabled",
        val enabled: Boolean = false,
        val coverDelayMs: Long = 0,
        val paletteDelayMs: Long = 0,
        val lyricsDelayMs: Long = 0,
        val durationDelayMs: Long = 0,
        val audioDelayMs: Long = 0,
        val unknownDuration: Boolean = false,
        val demoLyrics: Boolean = false,
    )

    private val mutableProfile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = mutableProfile.asStateFlow()
    private val mutableBuffering = MutableStateFlow(false)
    val forcedBuffering: StateFlow<Boolean> = mutableBuffering.asStateFlow()

    fun configure(name: String) {
        mutableBuffering.value = false
        mutableProfile.value = when (name) {
            "local" -> Profile(name = name, enabled = true, demoLyrics = true)
            "partial" -> Profile(name, true, 1800, 2300, 12000, 16000, 2000, demoLyrics = true)
            "unknown" -> Profile(name, true, 2200, 2700, 3500, 0, 1800, true, true)
            "slow" -> Profile(name, true, 3500, 4000, 4500, 5000, 3000, demoLyrics = true)
            else -> Profile()
        }
    }

    fun setBuffering(buffering: Boolean) { mutableBuffering.value = buffering }
    fun disable() { mutableProfile.value = Profile(); mutableBuffering.value = false }

    /** Original text, deliberately different between tracks; no dependency on external lyrics. */
    fun demoLyricsFor(identity: String): String {
        val lines = if (identity.hashCode() and 1 == 0) listOf(
            "晚风从窗边轻轻经过", "带走白天没说完的话", "街灯在远处慢慢亮起",
            "你的脚步停在月光下", "我们把时间折成纸船", "顺着河流漂向下一站",
            "等一阵雨落进旧故事", "等一道光照见新答案", "此刻的歌还在心里回响",
        ) else listOf(
            "清晨把云推向远方", "山间有一条安静的路", "我听见树叶回应风声",
            "也听见你轻轻的脚步", "沿着光走过每个转弯", "把昨天留在身后的雾", 
            "下一站有未见过的海", "下一句有未说完的祝福", "让这首歌陪我们出发",
        )
        return lines.mapIndexed { index, line ->
            val seconds = index * 8
            "[${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}.00]$line"
        }.joinToString("\n")
    }
}
