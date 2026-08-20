package com.lalilu.lsearch.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lsearch.viewmodel.SearchTypeFilter
import org.jetbrains.compose.resources.stringResource

/**
 * Floating type-filter TabRow for [com.lalilu.lsearch.screen.SearchScreen].
 *
 * Visual contract:
 *  - Always anchored at the bottom of its parent Box.
 *  - Background is a custom [Brush.verticalGradient] (transparent → background
 *    color from top to bottom), so content scrolling behind the tab bar
 *    remains partially visible.
 *  - The pill chips sit at the bottom of the gradient box.
 *
 * @param selected supplier of the currently selected filter (lambda avoids
 *                 unnecessary recomposition when state changes are
 *                 unrelated to selection)
 * @param onSelect callback when the user taps a chip
 * @param modifier parent modifier (typically [Modifier.align(Alignment.BottomCenter)])
 * @param height total height of the tab bar; gradient is rendered across this height
 */
@Composable
fun SearchTypeTabBar(
    selected: () -> SearchTypeFilter,
    onSelect: (SearchTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    val bgColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current

    val gradient = remember(bgColor, height, density) {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, bgColor),
            startY = 0f,
            endY = with(density) { height.toPx() }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(gradient)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchTypeFilter.entries.forEach { type ->
                SearchTypeChip(
                    selected = { selected() == type },
                    onClick = { onSelect(type) },
                    label = stringResource(type.labelRes)
                )
            }
        }
    }
}

/**
 * Pill-shaped filter chip. Filled when selected, outlined when not.
 */
@Composable
private fun SearchTypeChip(
    selected: () -> Boolean,
    onClick: () -> Unit,
    label: String
) {
    val bg = if (selected()) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    }
    val fg = if (selected()) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected()) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}