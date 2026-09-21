package com.lalilu.lmedia.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.component.SourceSectionHeader
import com.lalilu.lmedia.component.SourceTextField
import com.lalilu.lmedia.domain.source.SnapshotState
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
    var password by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
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
