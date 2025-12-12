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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.source.JvmFileSystemSource.Companion.KEY_PATH
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun JvmFileSystemSource.JvmFileSystemSourceContent(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val path = fileFlow.collectAsState(null)
    val source by remember { source() }.collectAsState(
        initial = Snapshot.Empty,
        context = Dispatchers.IO
    )

    val launcher = rememberDirectoryPickerLauncher {
        scope.launch(Dispatchers.IO) {
            settings.putString(KEY_PATH, it?.absolutePath() ?: "")
        }
    }

    JvmFileSystemSourceContent(
        modifier = modifier,
        title = name,
        path = path.value?.name ?: "",
        itemsCount = source.audios.size,
        onSelectDirectory = { launcher.launch() }
    )
}

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
                modifier = Modifier.padding(vertical = 12.dp),
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