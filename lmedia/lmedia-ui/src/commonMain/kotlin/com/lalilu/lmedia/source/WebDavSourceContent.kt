package com.lalilu.lmedia.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourceInfoPanel
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.component.SourceSectionHeader
import com.lalilu.lmedia.component.SourceTextField
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.webdav.WebDavExtractionState
import com.lalilu.lmedia.source.webdav.WebDavMetadataSyncState
import com.lalilu.lmedia.source.webdav.WebDavSource
import androidx.compose.runtime.collectAsState

/**
 * WebDAV 数据源的配置卡片。
 *
 * 密码使用明文保存（Basic 认证需要原始密码），因此文案明确引导用户使用服务端签发的
 * 「应用专用密码」，把泄露影响限制在单个可撤销的凭据上。
 */
fun WebDavSource.webDavSourceContent(
    modifier: Modifier = Modifier,
) = LazyStaggeredGridContent {
    val appliedConfig by config.flow().collectAsState(initial = config.value)
    val extraction by extractionProgress.collectAsState()
    val syncState by metadataSyncState.collectAsState()
    var password by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var optionError by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }

    return@LazyStaggeredGridContent {
        item(key = this@webDavSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "WebDAV",
                description = "直接播放自建 WebDAV 上的音乐（NAS / Nextcloud / 坚果云）",
                idleLabel = if (appliedConfig.isConfigured) "待连接" else "未配置",
            ) { uiState ->
                val configured = appliedConfig.isConfigured
                val showForm = editing || !configured

                LaunchedEffect(uiState.syncState) {
                    if (uiState.syncState is SnapshotState.Error) editing = true
                }

                if (configured && !showForm) {
                    SourceSectionHeader(
                        title = "服务器",
                        summary = buildString {
                            if (appliedConfig.username.isNotBlank()) {
                                append(appliedConfig.username)
                                append(" · ")
                            }
                            append(appliedConfig.url)
                            append(appliedConfig.rootPath)
                        },
                        expanded = editing,
                        onToggle = { editing = !editing },
                    )
                }

                AnimatedVisibility(visible = showForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = if (configured && !showForm) 4.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SourceTextField(
                            value = config.value.url,
                            onValueChange = {
                                config.value = config.value.copy(url = it)
                                formError = null
                            },
                            label = "服务器地址",
                            placeholder = "https://dav.example.com",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = config.value.rootPath,
                            onValueChange = {
                                config.value = config.value.copy(rootPath = it)
                                formError = null
                            },
                            label = "媒体库根目录",
                            placeholder = "/",
                            supportingText = "只扫描该目录及子目录",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = config.value.username,
                            onValueChange = {
                                config.value = config.value.copy(username = it)
                                formError = null
                            },
                            label = "用户名",
                            supportingText = "留空表示匿名访问",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                formError = null
                            },
                            label = if (configured) "密码（留空复用已保存的密码）" else "密码",
                            supportingText = formError
                                ?: "建议使用应用专用密码；密码以明文保存在本机",
                            isError = formError != null,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourceActionButton(
                                title = if (uiState.isLoading) "取消连接" else "保存并连接",
                                style = SourceActionStyle.Primary,
                                onClick = {
                                    if (uiState.isLoading) {
                                        cancel()
                                    } else {
                                        connect(
                                            url = config.value.url,
                                            username = config.value.username,
                                            password = password,
                                            // 规范化由 WebDavSource.connect 负责，UI 只透传用户输入
                                            rootPath = config.value.rootPath,
                                        ).onSuccess {
                                            password = ""
                                            formError = null
                                            editing = false
                                        }.onFailure { formError = it.message ?: "连接参数错误" }
                                    }
                                },
                            )
                            SourceActionButton(
                                title = if (configured) "放弃修改" else "取消",
                                style = SourceActionStyle.Quiet,
                                enabled = !uiState.isLoading,
                                onClick = {
                                    config.update()
                                    password = ""
                                    formError = null
                                    editing = false
                                },
                            )
                        }
                    }
                }

                if (!showForm) {
                    ExtractionProgressPanel(
                        state = extraction,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    OptionSwitchRow(
                        title = "后台主动下载",
                        description = "自动把未提取元数据的歌曲下载到本地缓存并补全标签与封面，" +
                            "顺序执行，可能占用较多流量；关闭后只在播放时补全。",
                        checked = appliedConfig.backgroundFetchEnabled,
                        onCheckedChange = { enabled ->
                            updateOptions(backgroundFetchEnabled = enabled)
                                .onFailure { optionError = it.message ?: "保存失败" }
                                .onSuccess { optionError = null }
                        },
                    )
                    OptionSwitchRow(
                        title = "同步元数据到 WebDAV",
                        description = "把提取出的标签与封面同步到服务器目录（默认 /.lmusic/）。" +
                            "本地读取优先，本地缺失时从服务器取回并写回本地；" +
                            "目录只读时会给出提示，不影响播放与提取。",
                        checked = appliedConfig.metaSyncEnabled,
                        onCheckedChange = { enabled ->
                            updateOptions(metaSyncEnabled = enabled)
                                .onFailure { optionError = it.message ?: "保存失败" }
                                .onSuccess { optionError = null }
                        },
                    )
                    MetadataSyncStatusLine(
                        state = syncState,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    optionError?.let { message ->
                        Text(
                            modifier = Modifier.padding(top = 6.dp),
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourceActionButton(
                            title = if (uiState.isLoading) "取消" else "刷新媒体库",
                            style = SourceActionStyle.Primary,
                            onClick = {
                                if (uiState.isLoading) cancel()
                                else refresh().onFailure { formError = it.message ?: "刷新失败" }
                            },
                        )
                        SourceActionButton(
                            title = "修改配置",
                            enabled = !uiState.isLoading,
                            onClick = {
                                config.update()
                                editing = true
                            },
                        )
                        SourceActionButton(
                            title = "清除认证",
                            enabled = !uiState.isLoading,
                            style = SourceActionStyle.Quiet,
                            onClick = {
                                reset()
                                password = ""
                                formError = null
                                editing = true
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 提取进度：库总量来自快照，已提取数与排队/进行中/失败数来自提取器。
 *
 * 只在有进展可看时展示，避免未配置或刚清空缓存时出现一行"0/0"的噪音。
 */
@Composable
private fun ExtractionProgressPanel(
    state: WebDavExtractionState,
    modifier: Modifier = Modifier,
) {
    val hasWork = !state.isIdle || state.failed > 0
    if (state.total <= 0 && !hasWork) return

    SourceInfoPanel(
        modifier = modifier,
        label = "元数据提取",
        value = "${state.extracted} / ${state.total}",
        supportingText = buildString {
            if (state.running > 0) append("进行中 ${state.running}")
            if (state.pending > 0) {
                if (isNotEmpty()) append(" · ")
                append("排队 ${state.pending}")
            }
            if (state.failed > 0) {
                if (isNotEmpty()) append(" · ")
                append("失败 ${state.failed}")
            }
            if (isEmpty()) append("播放过的歌曲会在这里补齐标签与封面")
        },
        emphasized = hasWork,
    )
}

/** 同步状态的单行提示：只在同步中或失败时出现。 */
@Composable
private fun MetadataSyncStatusLine(
    state: WebDavMetadataSyncState,
    modifier: Modifier = Modifier,
) {
    val text = when (state) {
        WebDavMetadataSyncState.Idle -> return
        WebDavMetadataSyncState.Syncing -> "正在同步元数据…"
        is WebDavMetadataSyncState.Synced -> {
            if (state.uploaded == 0 && state.pulled == 0) {
                "元数据已与服务器一致"
            } else {
                "元数据同步完成：上传 ${state.uploaded}，取回 ${state.pulled}"
            }
        }

        is WebDavMetadataSyncState.Failed -> if (state.readOnly) {
            "元数据目录不可写：${state.message}。请换一个有写权限的目录，或关闭该开关。"
        } else {
            "元数据同步失败：${state.message}"
        }
    }

    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (state is WebDavMetadataSyncState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
    )
}

/** 带说明文字的开关行；用于不影响连接参数的即时生效选项。 */
@Composable
private fun OptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
