package com.lalilu.lmedia.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
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
            .alpha(0.6f)
            .padding(top = 8.dp),
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
    )
}