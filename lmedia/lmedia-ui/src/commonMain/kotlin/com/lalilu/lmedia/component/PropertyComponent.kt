package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.source.Declaration
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.lmedia.source.range
import com.lalilu.lmedia.source.step

@Suppress("UNCHECKED_CAST")
@Composable
fun MediaSource.PropertyComponent(
    modifier: Modifier = Modifier,
) {
    // 只显示 visibleInUI = true 的属性，隐藏对用户不可见的配置参数
    val properties = remember {
        config.properties
            .filter { it.visibleInUI }
            .sortedByDescending { it.priority }
    }

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
                        enabled = property.mutable,
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
                            enabled = property.mutable,
                            checked = value,
                            onCheckedChange = { value = it; property.set(it) }
                        )
                    }
                }

                Int::class -> {
                    val property = property as Declaration.Property<Int>
                    val range = remember { property.range() }
                    var value by remember {
                        val defaultValue = runCatching { property.get() }.getOrNull()?.toFloat() ?: 0f
                        mutableStateOf(defaultValue)
                    }

                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = property.name,
                                style = MaterialTheme.typography.titleSmall,
                            )

                            Text(
                                modifier = Modifier.alpha(0.6f),
                                text = "[${range.start.toInt()}, ${range.endInclusive.toInt()}]",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                enabled = property.mutable,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp),
                                value = value,
                                onValueChange = { value = it },
                                valueRange = range,
                                steps = property.step(),
                                onValueChangeFinished = { property.set(value.toInt()) }
                            )

                            Text(
                                text = "${value.toInt()}",
                                fontSize = 14.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        if (property.description.isNotBlank()) {
                            Text(
                                modifier = Modifier.alpha(0.6f),
                                text = property.description,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                else -> {
                    Text("Unsupported type for: ${property.name} ${property.type}")
                }
            }
        }
    }
}
