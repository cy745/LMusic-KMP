/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.llyricview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.kv.KVContext
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.llyricview.calibration.LyricCalibrationAudioConfig
import com.lalilu.llyricview.calibration.LyricCalibrationAudioController
import com.lalilu.llyricview.calibration.LyricCalibrationAudioState
import com.lalilu.llyricview.calibration.LyricCalibrationTap
import com.lalilu.llyricview.calibration.LyricOffsetEstimate
import com.lalilu.llyricview.calibration.estimateLyricTimeOffset
import com.lalilu.llyricview.calibration.matchCalibrationTap
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

private const val CalibrationCompletionFeedbackMs = 750L

private enum class CalibrationSessionMode {
    Calibration,
    Preview,
}

@Destination("/settings/lyric/offset-calibration")
data object LyricOffsetCalibrationScreen : Screen, ScreenInfoFactory {
    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "歌词偏移校准" },
            icon = RemixIcon.Media.music2Line,
        )
    }

    @Composable
    override fun Content() = LyricOffsetCalibrationContent()
}

/**
 * 音游式歌词偏移校准页。
 *
 * 收缩圆环只负责表现理论节拍时间线。采样始终以 Android AudioTrack 的硬件时间戳为准，
 * 时间戳不可用时才回退到播放头，避免用 UI 帧时间或协程 delay 推算音频位置造成额外
 * 误差。
 */
@Composable
private fun LyricOffsetCalibrationContent() {
    val controller = remember {
        KoinPlatform.getKoin().getOrNull<LyricCalibrationAudioController>()
    }
    val unavailableState = remember {
        MutableStateFlow<LyricCalibrationAudioState>(LyricCalibrationAudioState.Idle)
    }
    val audioState by (controller?.state ?: unavailableState).collectAsState()
    val scope = rememberCoroutineScope()
    val config = remember { LyricCalibrationAudioConfig() }
    val previewConfig = remember {
        LyricCalibrationAudioConfig(
            leadInMs = 500L,
            beatCount = 16,
            sampleStartBeat = 3,
            requiredTapCount = 2,
        )
    }
    val settings = remember {
        KVContext.obtainStatic<LyricSettings>(
            key = "LyricSettings",
            defaultValue = LyricSettings(),
        ).apply { disableAutoSave() }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var lastTapAttemptMs by remember { mutableStateOf<Long?>(null) }
    var calibrationFinished by remember { mutableStateOf(false) }
    var appliedResult by remember { mutableStateOf<Long?>(null) }
    var sessionMode by remember { mutableStateOf(CalibrationSessionMode.Calibration) }
    var fineTuneOffsetMs by remember { mutableLongStateOf(settings.value.timeOffset) }
    val taps = remember { mutableStateListOf<LyricCalibrationTap>() }
    val estimate by remember {
        derivedStateOf {
            if (taps.size >= config.requiredTapCount) estimateLyricTimeOffset(taps) else null
        }
    }
    val isPlaying = audioState is LyricCalibrationAudioState.Playing
    val isCalibrating = isPlaying && sessionMode == CalibrationSessionMode.Calibration
    val isPreviewing = isPlaying && sessionMode == CalibrationSessionMode.Preview

    LaunchedEffect(estimate?.timeOffsetMs) {
        estimate?.let { result ->
            fineTuneOffsetMs = result.timeOffsetMs
            appliedResult = null
        }
    }

    LaunchedEffect(isPlaying, controller) {
        if (!isPlaying || controller == null) return@LaunchedEffect
        while (isActive && controller.state.value is LyricCalibrationAudioState.Playing) {
            withFrameNanos { positionMs = controller.currentPositionMs() }
        }
        positionMs = controller.currentPositionMs()
    }

    LaunchedEffect(calibrationFinished, sessionMode, controller) {
        if (!calibrationFinished ||
            sessionMode != CalibrationSessionMode.Calibration ||
            controller == null
        ) {
            return@LaunchedEffect
        }

        // 最后一次有效点击已经写入 taps。短暂停留用于呈现明确的成功反馈，避免立即停止
        // 音轨让用户误以为最后一下没有被识别。
        delay(CalibrationCompletionFeedbackMs)
        if (controller.state.value is LyricCalibrationAudioState.Playing) controller.stop()
    }

    DisposableEffect(controller) {
        onDispose { controller?.stop() }
    }

    fun startCalibration() {
        val audioController = controller ?: return
        taps.clear()
        positionMs = 0L
        lastTapAttemptMs = null
        calibrationFinished = false
        appliedResult = null
        sessionMode = CalibrationSessionMode.Calibration
        scope.launch { audioController.start(config) }
    }

    fun startPreview() {
        val audioController = controller ?: return
        positionMs = 0L
        lastTapAttemptMs = null
        sessionMode = CalibrationSessionMode.Preview
        scope.launch { audioController.start(previewConfig) }
    }

    fun recordTap() {
        val audioController = controller ?: return
        if (audioController.state.value !is LyricCalibrationAudioState.Playing ||
            sessionMode != CalibrationSessionMode.Calibration ||
            calibrationFinished
        ) {
            return
        }
        val sampledPositionMs = audioController.currentPositionMs()
        lastTapAttemptMs = sampledPositionMs
        val tap = matchCalibrationTap(
            positionMs = sampledPositionMs,
            config = config,
            sampledBeatIndices = taps.mapTo(mutableSetOf()) { it.beatIndex },
        ) ?: return
        taps += tap
        if (taps.size >= config.requiredTapCount) {
            calibrationFinished = true
        }
    }

    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding(),
            bottom = smartBarHeight() + 20.dp,
        ),
    ) {
        item(key = "header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "歌词偏移校准",
                subTitle = "跟随节拍点击，测量当前音频设备的实际延迟",
            )
        }

        item(key = "calibration_ring") {
            val previewingResult = sessionMode == CalibrationSessionMode.Preview && estimate != null
            CalibrationRingCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(310.dp),
                config = if (previewingResult) previewConfig else config,
                // 正式歌词使用「播放位置 + timeOffset」。试听时同样只移动视觉时间线，
                // 节拍音保持原始位置，二者重合时即表示候选偏移符合当前输出设备。
                positionMs = positionMs + if (previewingResult) fineTuneOffsetMs else 0L,
                isPlaying = isPlaying,
                isPreviewing = isPreviewing,
                showCalibrationComplete = calibrationFinished &&
                        sessionMode == CalibrationSessionMode.Calibration,
                taps = taps,
                lastTapAttemptMs = lastTapAttemptMs,
                onTap = ::recordTap,
            )
        }

        item(key = "status") {
            CalibrationStatus(
                modifier = Modifier.padding(horizontal = 20.dp),
                controllerAvailable = controller != null,
                audioState = audioState,
                taps = taps,
                requiredTapCount = config.requiredTapCount,
                estimate = estimate,
                currentOffsetMs = settings.value.timeOffset,
                appliedResult = appliedResult,
            )
        }

        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = controller != null,
                    onClick = {
                        if (isCalibrating) controller?.stop() else startCalibration()
                    },
                ) {
                    Text(if (isCalibrating) "停止" else if (taps.isEmpty()) "开始校准" else "重新校准")
                }
            }
        }

        estimate?.let {
            item(key = "fine_tune") {
                FineTunePanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    offsetMs = fineTuneOffsetMs,
                    isPreviewing = isPreviewing,
                    controllerAvailable = controller != null,
                    applied = appliedResult == fineTuneOffsetMs,
                    onOffsetChange = { value ->
                        fineTuneOffsetMs = value
                        appliedResult = null
                    },
                    onPreview = {
                        if (isPreviewing) controller?.stop() else startPreview()
                    },
                    onApply = {
                        settings.value = settings.value.copy(timeOffset = fineTuneOffsetMs)
                        settings.save()
                        appliedResult = fineTuneOffsetMs
                    },
                )
            }
        }

        item(key = "explanation") {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                text = "校准期间会暂停当前歌曲，节拍音不会进入播放队列；结束后会按原状态恢复。" +
                        "建议在实际听歌所用的扬声器或耳机连接状态下完成校准。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationRingCard(
    modifier: Modifier,
    config: LyricCalibrationAudioConfig,
    positionMs: Long,
    isPlaying: Boolean,
    isPreviewing: Boolean,
    showCalibrationComplete: Boolean,
    taps: List<LyricCalibrationTap>,
    lastTapAttemptMs: Long?,
    onTap: () -> Unit,
) {
    val latestTap = taps.lastOrNull()
    val latestOnTap by rememberUpdatedState(onTap)
    val feedbackColor = when {
        latestTap == null -> MaterialTheme.colorScheme.primary
        abs(latestTap.errorMs) <= 40L -> MaterialTheme.colorScheme.tertiary
        abs(latestTap.errorMs) <= 100L -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        // onPress 在第一帧按下时执行；onTap 再作为手势完成后的兜底。同一重音
                        // 在匹配层只会接受一次，因此正常情况下不会产生重复样本。
                        onPress = {
                            latestOnTap()
                            tryAwaitRelease()
                        },
                        onTap = { latestOnTap() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ApproachRingPlayfield(
                config = config,
                positionMs = positionMs,
                active = isPlaying,
                color = feedbackColor,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = when {
                        isPreviewing -> "观察重音是否同步"
                        showCalibrationComplete -> "采样完成"
                        !isPlaying -> "准备好后开始"
                        positionMs < config.beatTimeMs(config.sampleStartBeat) -> "先听一小节"
                        else -> "只在重音点击"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when {
                        isPreviewing -> "轻 · 轻 · 轻 · 重"
                        showCalibrationComplete ->
                            "最后一次点击已记录 · ${taps.size} / ${config.requiredTapCount}"
                        else -> latestTap?.judgementText()
                            ?: lastTapAttemptMs?.let { "已检测点击 · ${it} ms" }
                            ?: "轻 · 轻 · 轻 · 重"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = feedbackColor,
                )
            }

            HitErrorStrip(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .height(38.dp),
                taps = taps,
                color = feedbackColor,
            )
        }
    }
}

@Composable
private fun FineTunePanel(
    modifier: Modifier,
    offsetMs: Long,
    isPreviewing: Boolean,
    controllerAvailable: Boolean,
    applied: Boolean,
    onOffsetChange: (Long) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "试听微调  ${formatOffset(offsetMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "试听时让画面与每小节的重音重合；画面偏早就减小数值，偏晚就增大数值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(-25L, -10L, 10L, 25L).forEach { delta ->
                    FilterChip(
                        selected = false,
                        onClick = { onOffsetChange(offsetMs + delta) },
                        label = { Text(formatSigned(delta)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = controllerAvailable,
                    onClick = onPreview,
                ) {
                    Text(if (isPreviewing) "停止试听" else "试听对齐")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !applied,
                    onClick = onApply,
                ) {
                    Text(if (applied) "已应用" else "应用偏移")
                }
            }
        }
    }
}

@Composable
private fun ApproachRingPlayfield(
    config: LyricCalibrationAudioConfig,
    positionMs: Long,
    active: Boolean,
    color: Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.45f)
        val nextAccentBeat = nextAccentBeat(config, positionMs)
        val timeUntilAccent = config.beatTimeMs(nextAccentBeat) - positionMs
        val measureDuration = config.beatIntervalMs * config.beatsPerMeasure
        val approachProgress = if (active) {
            1f - (timeUntilAccent.toFloat() / measureDuration).coerceIn(0f, 1f)
        } else 0f
        val targetRadius = min(size.width, size.height) * 0.15f
        val approachRadius = targetRadius + (1f - approachProgress) * targetRadius * 1.4f

        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = targetRadius,
            center = center,
        )
        drawCircle(
            color = color.copy(alpha = if (active) 0.9f else 0.35f),
            radius = targetRadius,
            center = center,
            style = Stroke(width = 5.dp.toPx()),
        )
        drawCircle(
            color = color.copy(alpha = if (active) 0.75f else 0.2f),
            radius = approachRadius,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun HitErrorStrip(
    modifier: Modifier,
    taps: List<LyricCalibrationTap>,
    color: Color,
) {
    Canvas(modifier) {
        val centerX = size.width / 2f
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = color,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 2.dp.toPx(),
        )
        taps.takeLast(12).forEachIndexed { index, tap ->
            val x = centerX + (tap.errorMs / 250f).coerceIn(-1f, 1f) * centerX
            drawLine(
                color = color.copy(alpha = 0.25f + 0.75f * (index + 1) / min(taps.size, 12).toFloat()),
                start = Offset(x, size.height * 0.2f),
                end = Offset(x, size.height * 0.8f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CalibrationStatus(
    modifier: Modifier,
    controllerAvailable: Boolean,
    audioState: LyricCalibrationAudioState,
    taps: List<LyricCalibrationTap>,
    requiredTapCount: Int,
    estimate: LyricOffsetEstimate?,
    currentOffsetMs: Long,
    appliedResult: Long?,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when {
                    !controllerAvailable -> "当前平台暂不支持校准音频"
                    audioState is LyricCalibrationAudioState.Failed -> audioState.message
                    estimate != null -> "建议偏移 ${formatOffset(estimate.timeOffsetMs)}"
                    taps.size >= requiredTapCount -> "采样波动过大"
                    audioState is LyricCalibrationAudioState.Playing ->
                        "采样 ${taps.size} / $requiredTapCount"
                    taps.isNotEmpty() -> "采样未完成 ${taps.size} / $requiredTapCount"
                    else -> "当前偏移 ${formatOffset(currentOffsetMs)}"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    appliedResult != null -> "已保存到歌词 timeOffset 字段"
                    estimate != null -> {
                        val direction = if (estimate.medianTapErrorMs >= 0L) "晚" else "早"
                        "你通常点击得${direction} ${abs(estimate.medianTapErrorMs)} ms；" +
                                "已采用 ${estimate.acceptedSampleCount} 次有效采样，" +
                                "波动约 ±${estimate.spreadMs} ms" +
                                if (estimate.rejectedSampleCount > 0) {
                                    "，排除 ${estimate.rejectedSampleCount} 次误触"
                                } else ""
                    }
                    !controllerAvailable -> "旁路播放器目前只实现了 Android 端"
                    taps.size >= requiredTapCount -> "点击分布不够稳定，请重新校准"
                    taps.isNotEmpty() -> "还需要 ${requiredTapCount - taps.size} 次有效点击"
                    else -> "先听一小节，之后只在每小节的重音点击，共采集 8 次"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LyricCalibrationTap.judgementText(): String = when {
    abs(errorMs) <= 40L -> "准确  ${formatSigned(errorMs)}"
    errorMs < 0L -> "偏早  ${formatSigned(errorMs)}"
    else -> "偏晚  ${formatSigned(errorMs)}"
}

private fun nextAccentBeat(
    config: LyricCalibrationAudioConfig,
    positionMs: Long,
): Int {
    val relative = positionMs - config.leadInMs
    val currentBeat = maxOf(0, floor(relative.toDouble() / config.beatIntervalMs).toInt())
    var nextAccent = config.nextAccentBeatAtOrAfter(currentBeat)
    if (config.beatTimeMs(nextAccent) < positionMs) {
        nextAccent = config.nextAccentBeatAtOrAfter(nextAccent + 1)
    }
    return nextAccent
}

private fun formatOffset(value: Long): String = "${if (value > 0) "+" else ""}$value ms"
private fun formatSigned(value: Long): String = "${if (value >= 0) "+" else ""}$value ms"
