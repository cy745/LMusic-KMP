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

package com.lalilu.lmusic.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.lalilu.common.kv.KVContext
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

internal const val DEFAULT_DISPLAY_CORNER_RADIUS_DP = 32f
internal const val MAX_DISPLAY_CORNER_RADIUS_DP = 100f

/**
 * [manualRadiusDp] 为 null 时优先采用平台检测值，平台不支持检测时回退到 32dp。
 * 手动值使用一个半径覆盖左上角和右上角，便于用户通过单个 Slider 完成视觉校准。
 */
@Serializable
@Immutable
internal data class DisplayCornerSettings(
    val manualRadiusDp: Float? = null,
)

/** 平台返回的窗口顶部两个物理圆角半径，单位统一转换成 dp。 */
@Immutable
internal data class SystemDisplayCornerRadii(
    val topLeftDp: Float,
    val topRightDp: Float,
) {
    companion object {
        fun uniform(radiusDp: Float) = SystemDisplayCornerRadii(
            topLeftDp = radiusDp,
            topRightDp = radiusDp,
        )
    }
}

/**
 * Android 12+ 读取 WindowInsets 的 RoundedCorner；其他平台返回 null。
 * 返回值只描述物理窗口，不承担设置持久化。
 */
@Composable
internal expect fun rememberSystemDisplayCornerRadii(): SystemDisplayCornerRadii?

internal object DisplayCornerSettingsStore {
    val settings by lazy {
        KVContext.obtainStatic(
            key = "DisplayCornerSettings",
            defaultValue = DisplayCornerSettings(),
        ).apply { disableAutoSave() }
    }

    fun updateManualRadius(radiusDp: Float) {
        settings.value = settings.value.copy(
            manualRadiusDp = snapDisplayCornerRadius(radiusDp),
        )
    }

    fun useSystemRadius() {
        settings.value = settings.value.copy(manualRadiusDp = null)
        settings.save()
    }

    fun persist() = settings.save()
}

internal fun resolveDisplayCornerRadii(
    settings: DisplayCornerSettings,
    systemRadii: SystemDisplayCornerRadii?,
): SystemDisplayCornerRadii {
    val manualRadius = settings.manualRadiusDp
    if (manualRadius != null) {
        return SystemDisplayCornerRadii.uniform(snapDisplayCornerRadius(manualRadius))
    }

    return systemRadii?.bounded()
        ?: SystemDisplayCornerRadii.uniform(DEFAULT_DISPLAY_CORNER_RADIUS_DP)
}

/** Slider 连续拖动，但对外状态始终以 0.5dp 为一个刻度。 */
internal fun snapDisplayCornerRadius(radiusDp: Float): Float =
    ((radiusDp.coerceIn(0f, MAX_DISPLAY_CORNER_RADIUS_DP) * 2f).roundToInt() / 2f)

internal fun SystemDisplayCornerRadii.representativeTopRadius(): Float =
    maxOf(topLeftDp, topRightDp)

private fun SystemDisplayCornerRadii.bounded() = SystemDisplayCornerRadii(
    topLeftDp = topLeftDp.coerceIn(0f, MAX_DISPLAY_CORNER_RADIUS_DP),
    topRightDp = topRightDp.coerceIn(0f, MAX_DISPLAY_CORNER_RADIUS_DP),
)
