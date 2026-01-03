package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.source.MediaSource

@Composable
fun MediaSource.FunctionComponent(modifier: Modifier = Modifier) {
    if (config.functions.isEmpty()) return
    val functions = remember {
        config.functions
            .filter { it.parameters.isEmpty() } // 只处理没有参数的函数，有参数的函数需要自行实现
            .sortedByDescending { it.priority }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        functions.forEach { function ->
            TextButton(
                onClick = { function.call() },
                colors = ButtonDefaults.filledTonalButtonColors(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = function.name,
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (!function.description.isBlank()) {
                        Text(
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .alpha(0.6f),
                            text = function.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}