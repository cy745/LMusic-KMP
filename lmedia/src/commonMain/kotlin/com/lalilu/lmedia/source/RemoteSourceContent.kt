package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RemoteSourceContent(
    modifier: Modifier = Modifier,
    title: String,
    url: MutableState<String>,
    password: MutableState<String>,
    enable: MutableState<Boolean>,
    itemsCount: Int = 0,
    enableUpdateConfig: () -> Boolean = { true },
    onUpdateConfig: () -> Unit = {}
) {
    Card {
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(vertical = 12.dp),
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onUpdateConfig,
                enabled = enableUpdateConfig(),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Update Config"
                )
            }

            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总开关",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = enable.value,
                    onCheckedChange = { enable.value = it }
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = url.value,
                onValueChange = { url.value = it },
                label = { Text(text = "URL") }
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text(text = "Password") }
            )

            Column(
                modifier = Modifier.align(Alignment.End)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    modifier = Modifier,
                    text = "扫描到的元素总数:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    modifier = Modifier,
                    text = "$itemsCount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp
                )
            }
        }
    }
}