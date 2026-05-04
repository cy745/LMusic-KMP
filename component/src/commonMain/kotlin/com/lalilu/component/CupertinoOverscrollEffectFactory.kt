/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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