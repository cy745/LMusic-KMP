package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.source.Declaration
import com.lalilu.lmedia.source.MediaSource

@Suppress("UNCHECKED_CAST")
@Composable
fun MediaSource.PropertyComponent(
    modifier: Modifier = Modifier,
) {
    val properties = remember { config.properties.sortedByDescending { it.priority } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        properties.forEach { property ->
            when (property.type) {
                String::class -> {
                    val property = property as Declaration.Property<String>
                    var value by remember { mutableStateOf(runCatching { property.get() }.getOrNull() ?: "") }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = value,
                        onValueChange = { value = it; property.set(it) },
                        label = { Text(text = property.name) },
                        placeholder = { Text(text = property.description) },
                    )
                }

                Boolean::class -> {
                    val property = property as Declaration.Property<Boolean>
                    var value by remember { mutableStateOf(runCatching { property.get() }.getOrNull() ?: false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = property.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                modifier = Modifier.alpha(0.6f),
                                text = property.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Switch(
                            checked = value,
                            onCheckedChange = { value = it; property.set(it) }
                        )
                    }
                }

                else -> {
                    Text("Unsupported type for: ${property.name} ${property.type}")
                }
            }
        }
    }
}
