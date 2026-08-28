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

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayCornerSettingsTest {
    @Test
    fun radiusIsClampedAndSnappedToHalfDp() {
        assertEquals(0f, snapDisplayCornerRadius(-1f))
        assertEquals(31f, snapDisplayCornerRadius(31.24f))
        assertEquals(31.5f, snapDisplayCornerRadius(31.26f))
        assertEquals(100f, snapDisplayCornerRadius(101f))
    }

    @Test
    fun manualRadiusOverridesBothSystemTopCorners() {
        val resolved = resolveDisplayCornerRadii(
            settings = DisplayCornerSettings(manualRadiusDp = 45.26f),
            systemRadii = SystemDisplayCornerRadii(
                topLeftDp = 12f,
                topRightDp = 14f,
            ),
        )

        assertEquals(SystemDisplayCornerRadii.uniform(45.5f), resolved)
    }

    @Test
    fun systemCornersAreUsedAndBoundedWithoutManualOverride() {
        val resolved = resolveDisplayCornerRadii(
            settings = DisplayCornerSettings(),
            systemRadii = SystemDisplayCornerRadii(
                topLeftDp = -2f,
                topRightDp = 42.25f,
            ),
        )

        assertEquals(
            SystemDisplayCornerRadii(
                topLeftDp = 0f,
                topRightDp = 42.25f,
            ),
            resolved,
        )
    }

    @Test
    fun defaultRadiusIsUsedWhenDetectionIsUnavailable() {
        val resolved = resolveDisplayCornerRadii(
            settings = DisplayCornerSettings(),
            systemRadii = null,
        )

        assertEquals(
            SystemDisplayCornerRadii.uniform(DEFAULT_DISPLAY_CORNER_RADIUS_DP),
            resolved,
        )
    }
}
