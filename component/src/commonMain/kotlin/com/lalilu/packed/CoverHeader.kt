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

package com.lalilu.packed

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.lalilu.adaptive
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.atLeastMedium
import com.lalilu.component.*
import com.lalilu.extensions.SharedContextScope

@Suppress("UNCHECKED_CAST")
object CoverHeader : LazyColumnContent<CoverHeader.Param> {

    enum class Param {
        SHARED_CONTEXT_SCOPE,
        COVER,
        TITLE,
        SUBTITLE,
        EXTRA_CONTENT
    }

    @Composable
    override fun register(
        mapper: ContentMapper<Param>
    ): LazyListScope.() -> Unit = mapper.cast<Param, SharedContextScope>(Param.SHARED_CONTEXT_SCOPE).run {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val statusBar = WindowInsets.statusBars.asPaddingValues()
        val paddingTop = adaptiveValue(
            compact = { 0.dp },
            medium = { statusBar.calculateTopPadding() + 16.dp },
        ).animated()

        val paddingHorizontal = adaptiveValue(
            compact = { 0.dp },
            medium = { 40.dp }
        ).animated()

        val adaptiveWidth = adaptiveValue(
            compact = { WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp },
            medium = { 250.dp }
        ).animated()

        val clipRadius = adaptiveValue(
            compact = { 0.dp },
            medium = { 12.dp }
        ).animated()

        val titleContent = remember {
            movableContentOf { modifier: Modifier, atColumn: Boolean ->
                val extraContent = remember {
                    mapper.get(Param.EXTRA_CONTENT)
                        ?.let { it as? ComposableContentWithModifier }
                        ?.let { movableContentOf(it.block) }
                }

                Column(modifier = modifier) {
                    Row {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp)
                                    .sharedBoundsV2(key = "TITLE"),
                                text = mapper.get(Param.TITLE) as? String ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                modifier = Modifier.sharedBoundsV2("SUBTITLE")
                                    .alpha(0.6f),
                                text = mapper.get(Param.SUBTITLE) as? String ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    extraContent?.invoke(Modifier)
                }
            }
        }

        return fun LazyListScope.() {
            val atLeastMedium = windowSizeClass.atLeastMedium()

            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = paddingTop.value)
                        .padding(horizontal = paddingHorizontal.value),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .width(width = adaptiveWidth.value)
                            .adaptive(
                                compact = { fillMaxWidth() },
                                medium = { this }
                            )
                            .aspectRatio(1f)
                            .sharedElementV2("COVER")
                            .clip(RoundedCornerShape(clipRadius.value))
                            .border(
                                width = 1f.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                                shape = RoundedCornerShape(clipRadius.value)
                            ),
                        model = mapper.get(Param.COVER),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                    if (atLeastMedium) {
                        titleContent(
                            Modifier.weight(1f)
                                .padding(start = 32.dp),
                            false
                        )
                    }
                }
            }

            if (!atLeastMedium) {
                item {
                    titleContent(
                        Modifier.fillMaxWidth()
                            .padding(top = 16.dp)
                            .padding(horizontal = 16.dp),
                        true
                    )
                }
            }
        }
    }
}