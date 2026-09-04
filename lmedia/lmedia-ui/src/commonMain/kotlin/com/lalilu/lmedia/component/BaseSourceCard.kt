package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BaseSourceCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    subtitleContent: @Composable ColumnScope.(String) -> Unit = { DefaultSubtitleContent(subtitle = it) },
    actionContent: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = Size(width = 2.dp.toPx(), height = size.height),
                    )
                }
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    subtitleContent(subtitle)
                }

                actionContent()
            }

            content()
        }
    }
}

@Composable
fun DefaultSubtitleContent(
    modifier: Modifier = Modifier,
    subtitle: String
) {
    Text(
        modifier = modifier
            .alpha(0.56f)
            .padding(top = 5.dp),
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
    )
}
