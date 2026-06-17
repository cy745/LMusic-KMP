package com.lalilu.packed

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.lalilu.extensions.SharedContextScope


@Composable
fun SharedContextScope.CoverTitleHeader(
    modifier: Modifier = Modifier,
    coverData: Any? = null,
    title: String = "",
    subtitle: String? = null,
    titleContent: @Composable (Modifier) -> Unit = {
        Text(
            modifier = it,
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
    },
    subtitleContent: @Composable (Modifier) -> Unit = {
        if (subtitle != null) {
            Text(
                modifier = it,
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    },
    extraContent: (@Composable (Modifier) -> Unit)? = null
) {
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
            Column(modifier = modifier) {
                Row {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        titleContent(
                            Modifier.padding(top = 8.dp)
                                .sharedBoundsV2(key = "TITLE")
                        )

                        subtitleContent(
                            Modifier.sharedBoundsV2("SUBTITLE")
                                .alpha(0.6f)
                        )
                    }
                }

                extraContent?.invoke(Modifier)
            }
        }
    }

    val atLeastMedium = windowSizeClass.atLeastMedium()

    Column(modifier = modifier) {
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
                model = coverData,
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

        if (!atLeastMedium) {
            titleContent(
                Modifier.fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
                true
            )
        }
    }
}