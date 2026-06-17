package com.lalilu.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

@Composable
fun WrapBox(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {},
    foreground: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box { background() }
            Box { content() }
            Box { foreground() }
        }
    ) { measurables, constraints ->
        val contentPlaceable = measurables[1].measure(constraints)
        val contentConstraint = constraints.copy(
            minWidth = contentPlaceable.width,
            minHeight = contentPlaceable.height,
            maxWidth = contentPlaceable.width,
            maxHeight = contentPlaceable.height
        )

        val backgroundPlaceable = measurables.getOrNull(0)
            ?.measure(contentConstraint)
        val foregroundPlaceable = measurables.getOrNull(2)
            ?.measure(contentConstraint)

        layout(
            width = contentPlaceable.width,
            height = contentPlaceable.height
        ) {
            backgroundPlaceable?.placeRelative(0, 0)
            contentPlaceable.placeRelative(0, 0)
            foregroundPlaceable?.placeRelative(0, 0)
        }
    }
}
