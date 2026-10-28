package com.lalilu.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import org.jetbrains.compose.resources.vectorResource

/** Quiet, left-aligned empty state shared by library sections and lists. */
@Composable
fun LibraryEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val query = LocalLibraryQuery.current
    if (query?.loading == true) {
        LibraryLoadingState(modifier, query.searching, contentPadding)
        return
    }
    if (query?.failed == true) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLibraryQuery provides null) {
            LibraryEmptyState("暂时无法读取", "请稍后重试", modifier, contentPadding, "重试", query.retry)
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        if (actionLabel != null) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (hovered || pressed || focused) 1f else 0.9f,
            )
            Row(
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onAction,
                ).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = color)
                Icon(vectorResource(RemixIcon.Arrows.arrowRightLine), null,
                    Modifier.size(16.dp), tint = color)
            }
        }
    }
}
