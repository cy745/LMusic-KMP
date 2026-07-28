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

package com.lalilu.component

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

class CupertinoOverscrollEffectFactory(
    private val density: Density,
    private val applyClip: Boolean = false
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect {
        return CupertinoOverscrollEffect(density.density, applyClip)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CupertinoOverscrollEffectFactory

        if (applyClip != other.applyClip) return false
        if (density != other.density) return false

        return true
    }

    override fun hashCode(): Int {
        var result = applyClip.hashCode()
        result = 31 * result + density.hashCode()
        return result
    }
}

@Composable
fun rememberCupertinoOverscrollEffectFactory(
    density: Density = LocalDensity.current,
    applyClip: Boolean = false
): CupertinoOverscrollEffectFactory {
    return remember { CupertinoOverscrollEffectFactory(density, applyClip) }
}