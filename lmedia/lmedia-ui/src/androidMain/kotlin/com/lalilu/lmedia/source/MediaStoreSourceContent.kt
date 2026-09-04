package com.lalilu.lmedia.source

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.*
import com.lalilu.lmedia.source.mediastore.MediaStoreSource

fun MediaStoreSource.mediaStoreSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val context = LocalContext.current
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val granted = remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { success ->
            granted.value = success
            if (success) refresh()
        },
    )

    return@LazyStaggeredGridContent {
        item(key = this@mediaStoreSourceContent.name) {
            var minDuration by remember(config.value.minDurationSeconds) {
                mutableFloatStateOf(config.value.minDurationSeconds.toFloat())
            }
            SourcePipelineCard(
                modifier = modifier,
                title = "Android 媒体库",
                description = "读取系统已经索引的音频，扫描速度更快",
                idleLabel = if (granted.value) "待扫描" else "待授权",
            ) { uiState ->
                if (!granted.value) {
                    SourceInfoPanel(
                        modifier = Modifier.padding(top = 14.dp),
                        label = "需要媒体访问权限",
                        value = "允许 LMusic 查看设备中的音频文件",
                        supportingText = "权限只用于读取歌曲、封面和内嵌歌词",
                        emphasized = true,
                    )
                    SourceActionButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        title = "允许访问本机音乐",
                        style = SourceActionStyle.Primary,
                        enabled = !uiState.isLoading,
                        onClick = { launcher.launch(permission) },
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("忽略过短音频", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "过滤铃声、提示音等短文件",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                                )
                            }
                            Text(
                                "${minDuration.toInt()} 秒",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Slider(
                            value = minDuration,
                            onValueChange = { minDuration = it },
                            onValueChangeFinished = { updateMinDuration(minDuration.toInt()) },
                            valueRange = 0f..60f,
                            steps = 59,
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourceActionButton(
                            title = if (uiState.isLoading) "停止扫描" else "扫描系统媒体库",
                            style = SourceActionStyle.Primary,
                            onClick = { if (uiState.isLoading) cancel() else refresh() },
                        )
                    }
                }
            }
        }
    }
}
