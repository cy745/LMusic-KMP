package com.lalilu.lmedia.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.*
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.subsonic.SubsonicSource

fun SubsonicSource.subsonicSourceContent(
    modifier: Modifier = Modifier,
) = LazyStaggeredGridContent {
    val appliedConfig by config.flow().collectAsState(initial = config.value)
    var password by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(!appliedConfig.isConfigured) }

    return@LazyStaggeredGridContent {
        item(key = this@subsonicSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "Subsonic / Navidrome",
                description = "使用 Subsonic API 读取自建音乐服务器",
                idleLabel = if (appliedConfig.isConfigured) "待连接" else "未配置",
            ) { uiState ->
                val configured = appliedConfig.isConfigured
                val showForm = !configured || editing

                LaunchedEffect(uiState.syncState) {
                    if (uiState.syncState is SnapshotState.Error) editing = true
                }

                if (configured) {
                    SourceSectionHeader(
                        title = "服务器账号",
                        summary = "${appliedConfig.username} · ${appliedConfig.url}",
                        expanded = editing,
                        onToggle = { editing = !editing },
                    )
                }

                AnimatedVisibility(visible = showForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = if (configured) 4.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SourceTextField(
                            value = config.value.url,
                            onValueChange = {
                                config.value = updateDraftAuthentication(
                                    appliedConfig = appliedConfig,
                                    url = it,
                                    username = config.value.username,
                                )
                                formError = null
                            },
                            label = "Subsonic API 地址",
                            placeholder = "https://music.example.com/rest/",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = config.value.username,
                            onValueChange = {
                                config.value = updateDraftAuthentication(
                                    appliedConfig = appliedConfig,
                                    url = config.value.url,
                                    username = it,
                                )
                                formError = null
                            },
                            label = "用户名",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                formError = null
                            },
                            label = if (configured) "密码（留空复用现有认证）" else "密码",
                            supportingText = formError ?: "原始密码不会保存在设备中",
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
                                        connect(config.value.url, config.value.username, password)
                                            .onSuccess {
                                                password = ""
                                                formError = null
                                                editing = false
                                            }
                                            .onFailure { formError = it.message ?: "连接参数错误" }
                                    }
                                },
                            )
                            if (configured) {
                                SourceActionButton(
                                    title = "放弃修改",
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
                }

                if (!showForm) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                            title = "修改账号",
                            enabled = !uiState.isLoading,
                            onClick = {
                                config.update()
                                editing = true
                            },
                        )
                        SourceActionButton(
                            title = "重置认证",
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

private fun updateDraftAuthentication(
    appliedConfig: com.lalilu.lmedia.source.subsonic.SubsonicConfig,
    url: String,
    username: String,
) = if (url == appliedConfig.url && username == appliedConfig.username) {
    appliedConfig
} else {
    appliedConfig.copy(
        url = url,
        username = username,
        salt = "",
        token = "",
    )
}
