package com.lalilu.navigation.smartbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.navigation.ActionContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/** Dynamic action content: icon in the bar, icon and label in the expanded panel. */
@Composable
fun ActionIconButton(
    context: ActionContext,
    title: String,
    icon: DrawableResource,
    color: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = color.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(vectorResource(icon), contentDescription = title,
                modifier = Modifier.size(24.dp), tint = color)
            if (context.isFullyExpanded) {
                Text(title, color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
