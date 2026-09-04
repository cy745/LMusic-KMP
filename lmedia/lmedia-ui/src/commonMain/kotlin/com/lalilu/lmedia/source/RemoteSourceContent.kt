package com.lalilu.lmedia.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.*
import com.lalilu.lmedia.domain.source.SnapshotState

fun RemoteSource.remoteSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val appliedConfig by config.flow().collectAsState(initial = config.value)
    var password by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }

    return@LazyStaggeredGridContent {
        item(key = this@remoteSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "Remote Server",
                description = "连接另一台运行 LMusic Remote Server 的设备",
                idleLabel = if (appliedConfig.isConfigured) "待连接" else "未配置",
            ) { uiState ->
                val configured = appliedConfig.isConfigured
                val showForm = editing

                LaunchedEffect(uiState.syncState) {
                    if (uiState.syncState is SnapshotState.Error) editing = true
                }

                if (configured) {
                    SourceSectionHeader(
                        title = "连接设置",
                        summary = appliedConfig.url,
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
                                config.value = if (it == appliedConfig.url) {
                                    appliedConfig
                                } else {
                                    config.value.copy(url = it, salt = "", token = "")
                                }
                                formError = null
                            },
                            label = "服务器地址",
                            placeholder = "192.168.1.2:7779",
                            isError = formError != null,
                        )
                        SourceTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                formError = null
                            },
                            label = "访问密码（可选）",
                            supportingText = formError,
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
                                        connect(config.value.url, password)
                                            .onSuccess {
                                                password = ""
                                                formError = null
                                                editing = false
                                            }
                                            .onFailure { formError = it.message ?: "连接参数错误" }
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (configured) {
                            SourceActionButton(
                                title = if (uiState.isLoading) "取消" else "刷新远程媒体库",
                                style = SourceActionStyle.Primary,
                                onClick = {
                                    if (uiState.isLoading) cancel()
                                    else refresh().onFailure { formError = it.message ?: "刷新失败" }
                                },
                            )
                            SourceActionButton(
                                title = "修改连接",
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
                        } else {
                            SourceActionButton(
                                title = "配置连接",
                                style = SourceActionStyle.Primary,
                                onClick = {
                                    config.update()
                                    editing = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
