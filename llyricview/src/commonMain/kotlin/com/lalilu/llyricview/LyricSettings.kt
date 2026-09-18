@file:UseSerializers(
    TextAlignSerializer::class,
    TextUnitSerializer::class,
    DpSerializer::class,
    PaddingValueSerializer::class
)

package com.lalilu.llyricview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVItem
import com.lalilu.llyricview.serializable.DpSerializer
import com.lalilu.llyricview.serializable.PaddingValueSerializer
import com.lalilu.llyricview.serializable.TextAlignSerializer
import com.lalilu.llyricview.serializable.TextUnitSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers


internal val DEFAULT_TEXT_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.2f),
    offset = Offset(x = 0f, y = 1f),
    blurRadius = 1f
)

@Serializable
@Immutable
data class LyricSettings(
    // 布局样式配置
    val textAlign: TextAlign = TextAlign.Start,
    val containerPadding: PaddingValues = PaddingValues(horizontal = 40.dp, vertical = 15.dp),
    val gapSize: Dp = 8.dp,
    val scaleStart: Float = 0.85f,
    val scaleEnd: Float = 1f,
    val timeOffset: Long = 50L,

    // 字体样式配置
    val mainFontSize: TextUnit = 36.sp,
    val mainLineHeight: TextUnit = 48.sp,
    val mainFontWeight: Int = FontWeight.Black.weight,
    val translationFontSize: TextUnit = 18.sp,
    val translationLineHeight: TextUnit = 32.sp,
    val translationFontWeight: Int = FontWeight.Bold.weight,

    // 特殊效果开关
    val blurEffectEnable: Boolean = true,
    val reducedTransitionEnabled: Boolean = false,
    val translationVisible: Boolean = true,
    val onlyCurrentTranslationVisible: Boolean = false,

    /**
     * 歌词页展开时隐藏其他组件（播放页的 toolbar、进度条与系统状态栏）。
     *
     * 移植自单端 LMusic 的 `SettingsSp.autoHideSeekbar`，默认值与原实现一致。
     */
    val autoHideComponents: Boolean = false,

    // 歌词滚动效果配置
    val scrollSpringStiffness: Float = 100f,
    val scrollSpringDampingRatio: Float = 0.75f,
) {
    @Transient
    val mainTextStyle: TextStyle = TextStyle.Default.copy(
        fontSize = mainFontSize,
        textAlign = textAlign,
        lineHeight = mainLineHeight,
        fontWeight = FontWeight(mainFontWeight),
    )

    @Transient
    val translationTextStyle: TextStyle = TextStyle.Default.copy(
        fontSize = translationFontSize,
        textAlign = textAlign,
        lineHeight = translationLineHeight,
        fontWeight = FontWeight(translationFontWeight),
    )
}

/**
 * 共享的 [LyricSettings] KV 访问器。
 *
 * 与歌词设置页（`provideLyricSettings`）、播放页弹窗（`provideLyricSettingsQuick`）
 * 使用同一份 key，因此播放页可直接读取这里的开关（如 [LyricSettings.autoHideComponents]）。
 */
fun obtainLyricSettings(): KVItem<LyricSettings> =
    KVContext.obtainStatic(key = "LyricSettings", defaultValue = LyricSettings())
