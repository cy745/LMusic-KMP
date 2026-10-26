package com.lalilu.lplayer.core

import com.lalilu.playback.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local observation only. It never issues playback commands or changes the active sink. */
object PlaybackOutput {
    private val mutable = MutableStateFlow(OutputCapabilities())
    val capabilities = mutable.asStateFlow()
    fun report(value: OutputCapabilities) { mutable.value = value }
}

fun OutputCapabilities.describe(requestOriginal: Boolean): String {
    val request = if (requestOriginal) OutputMode.Original else OutputMode.Experience
    val actual = actualMode(request)
    return buildString {
        append(if (actual == OutputMode.Original) "原样输出已生效" else "当前使用体验输出")
        fallbackReason(request)?.let { append("；原样输出未生效：").append(it) }
        append("\n播放后端：").append(backend)
        route?.let { append("\n系统路由：").append(it) }
        apiFormat?.let {
            append("\n平台接口格式：").append(it.sampleRate).append(" Hz")
            it.channels?.let { channels -> append(" / ").append(channels).append(" 声道") }
            it.encoding?.let { encoding -> append(" / ").append(encoding) }
            append("（").append(apiFormatEvidence ?: "平台报告").append("；不代表 DAC 实测）")
        }
        if (apiFormat == null) append("\n当前输出格式：尚无法确认")
        append("\n连续无缝输出：").append(when (gapless) {
            Support.Supported -> "已接入（受内容、缓冲和路由条件限制）"
            Support.Unsupported -> "当前后端不支持连续输出"
            Support.Unknown -> "尚未验证"
        })
        if (availableDevices.isNotEmpty()) append("\n可用输出设备：").append(availableDevices.joinToString())
        if (advertisedSampleRates.isNotEmpty()) append("\n设备声明采样率：").append(advertisedSampleRates.joinToString()).append(" Hz（不是当前输出）")
        append("\n杜比 / 空间音频：尚未验证实际生效")
    }
}
