package com.lalilu.lmedia.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.component.BaseSourceCard
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourceInfoPanel
import com.lalilu.lmedia.component.SourceTextField
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.compose.koinInject

/**
 * 关闭 KVItem 的自动保存后，config.value 本身就是表单草稿；只有调用 save() 才会发布配置，
 * 因此文本输入和切换媒体源不会反复重启服务。
 */
@Composable
fun RemoteServerPanel(
    modifier: Modifier = Modifier,
) {
    val remoteServer = koinInject<RemoteServer>()
    val sources = koinInject<PlatformMediaSource>()
    val config = remoteServer.config
    val appliedConfig by config.flow().collectAsState(initial = config.value)
    val defaultSourceName = sources.sources.firstOrNull()?.name.orEmpty()
    val draft = config.value
    val hasChanges = draft != appliedConfig

    BaseSourceCard(
        modifier = modifier,
        title = "局域网共享",
        subtitle = "让同一网络中的其他 LMusic 设备访问一个媒体源",
        actionContent = {
            Switch(
                checked = appliedConfig.enable,
                onCheckedChange = { enabled ->
                    config.value = if (enabled) {
                        config.value.copy(
                            enable = true,
                            sourceName = config.value.sourceName.ifBlank { defaultSourceName },
                        )
                    } else {
                        // 关闭服务时丢弃尚未应用的表单修改，只改变已生效配置的开关。
                        appliedConfig.copy(enable = false)
                    }
                    config.save()
                },
            )
        },
    ) {
        if (appliedConfig.enable) {
            SourceInfoPanel(
                modifier = Modifier.padding(top = 14.dp),
                label = "共享服务",
                value = if (remoteServer.running.value) {
                    "正在端口 ${appliedConfig.port} 上运行"
                } else {
                    "正在启动服务…"
                },
                supportingText = appliedConfig.sourceName.takeIf(String::isNotBlank)?.let {
                    val sourceName = sources.sources
                        .firstOrNull { it.name == appliedConfig.sourceName }
                        ?.displayName()
                        ?: appliedConfig.sourceName
                    "当前共享：$sourceName"
                },
                emphasized = remoteServer.running.value,
            )
        }

        AnimatedVisibility(visible = appliedConfig.enable) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "选择要共享的媒体源",
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    sources.sources.forEach { source ->
                        SharedSourceOption(
                            source = source,
                            selected = config.value.sourceName == source.name,
                            onClick = {
                                config.value = config.value.copy(sourceName = source.name)
                            },
                        )
                    }
                }

                SourceTextField(
                    value = config.value.password,
                    onValueChange = {
                        config.value = config.value.copy(password = it)
                    },
                    label = "访问密码（可选）",
                    supportingText = "留空表示同一网络中的设备无需密码即可访问",
                    visualTransformation = PasswordVisualTransformation(),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceActionButton(
                        title = "应用共享设置",
                        style = SourceActionStyle.Primary,
                        enabled = hasChanges && config.value.sourceName.isNotBlank(),
                        onClick = config::save,
                    )
                    if (hasChanges) {
                        SourceActionButton(
                            title = "放弃修改",
                            style = SourceActionStyle.Quiet,
                            onClick = config::update,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSourceOption(
    source: MediaSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val indicatorColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(18.dp)
                .border(1.5.dp, indicatorColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(indicatorColor, CircleShape),
                )
            }
        }
        Text(
            text = source.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) indicatorColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun MediaSource.displayName(): String = when (name) {
    "AndroidFileSystemSource" -> "本地文件夹"
    "MediaStore" -> "系统媒体库"
    "MediaStoreSource" -> "系统媒体库"
    "SubsonicSource" -> "Subsonic / Navidrome"
    "RemoteSource" -> "Remote Server"
    "SandboxFileSystemSource" -> "沙盒文件"
    "MediaLibrarySource" -> "系统媒体库"
    "MusicKitSource" -> "Apple Music"
    "SystemMediaSource" -> "系统媒体库"
    else -> name
}
