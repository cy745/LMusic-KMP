package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun JvmFileSystemSourceContent(
    modifier: Modifier = Modifier,
    title: String,
    path: String = "",
    itemsCount: Int = 0,
    onSelectDirectory: () -> Unit = {}
) {
    Card {
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onSelectDirectory,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Select Directory"
                )
            }

            if (path.isNotBlank()) {
                Text(
                    modifier = Modifier.alpha(0.8f),
                    text = path,
                    style = MaterialTheme.typography.bodySmall
                )
            }

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

@Preview
@Composable
private fun JvmFileSystemSourceContentPreview() {
    JvmFileSystemSourceContent(
        title = "JvmFileSystemSource",
        itemsCount = 11,
        path = "/user/qiu/music"
    )
}