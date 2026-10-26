package com.lalilu.playback.core

/** Evidence is deliberately separate from a user's preference and advertised device profiles. */
enum class Support { Supported, Unsupported, Unknown }
enum class OutputMode { Experience, Original }
data class OutputFormat(val sampleRate: Int, val channels: Int? = null, val encoding: String? = null)
data class OutputCapabilities(
    val backend: String = "尚未连接播放器",
    val original: Support = Support.Unknown,
    val originalReason: String = "尚未取得当前播放链路的验证结果",
    val gapless: Support = Support.Unknown,
    val route: String? = null,
    /** Format delivered to the platform output API; not proof of the physical DAC format. */
    val apiFormat: OutputFormat? = null,
    val apiFormatEvidence: String? = null,
    val availableDevices: List<String> = emptyList(),
    val advertisedSampleRates: List<Int> = emptyList(),
    val spatial: Support = Support.Unknown,
    val dolby: Support = Support.Unknown,
    /** Must be set by a runtime only after an original-output path has actually been activated. */
    val originalActive: Boolean = false,
) {
    fun actualMode(requested: OutputMode): OutputMode =
        if (requested == OutputMode.Original && original == Support.Supported && originalActive)
            OutputMode.Original else OutputMode.Experience

    fun fallbackReason(requested: OutputMode): String? =
        if (requested == OutputMode.Original && actualMode(requested) != OutputMode.Original)
            originalReason else null
}
