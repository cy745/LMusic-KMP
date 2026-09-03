package com.lalilu.lmedia.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.RemixIcon
import com.lalilu.extensions.LocalToaster
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.sandbox.SandboxMediaSource
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

const val SANDBOX_MEDIA_SOURCE_ROUTE = "/media_source/sandbox"

@Destination(SANDBOX_MEDIA_SOURCE_ROUTE)
data object SandboxMediaSourceScreen : Screen, ScreenInfoFactory {
    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "Sandbox 文件" },
            icon = RemixIcon.Media.musicLine,
        )
    }

    @Composable
    override fun Content() {
        val platformSource = koinInject<PlatformMediaSource>()
        val source = remember(platformSource.sources) {
            platformSource.sources.filterIsInstance<SandboxMediaSource>().singleOrNull()
        }

        if (source == null) {
            SandboxMediaSourceUnavailableContent()
            return
        }

        val snapshot by source.snapshot.collectAsStateWithLifecycle()
        val state by source.state.collectAsStateWithLifecycle()
        SandboxMediaSourceContent(
            source = source,
            state = state,
            audios = snapshot?.audios.orEmpty(),
        )
    }
}

@Composable
private fun SandboxMediaSourceContent(
    source: SandboxMediaSource,
    state: SnapshotState,
    audios: List<LAudio>,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() },
    )
    val sortedAudios = remember(audios) {
        audios.sortedBy { it.sandboxFileName().lowercase() }
    }
    val loading = state is SnapshotState.Loading

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp,
        ),
    ) {
        item(key = "sandbox_header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "Sandbox 文件",
                subTitle = "浏览和管理由 LMusic 保存的 ${audios.size} 个音频文件",
                extraContent = {
                    TextButton(
                        enabled = busyId == null,
                        onClick = { if (loading) source.cancel() else source.refresh() },
                    ) {
                        Text(if (loading) "停止扫描" else "重新扫描")
                    }
                },
            )
        }

        if (loading) {
            item(key = "sandbox_loading") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
        }

        if (state is SnapshotState.Error) {
            item(key = "sandbox_error") {
                SandboxMessageCard(
                    title = "扫描失败",
                    message = state.message,
                    isError = true,
                )
            }
        }

        if (sortedAudios.isEmpty() && !loading) {
            item(key = "sandbox_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无导入文件\n从文件管理器使用 LMusic 打开音频后会显示在这里",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            items(
                items = sortedAudios,
                key = { it.id },
            ) { audio ->
                SandboxFileCard(
                    modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                    audio = audio,
                    editing = editingId == audio.id,
                    deleting = deletingId == audio.id,
                    busy = busyId == audio.id,
                    actionsEnabled = busyId == null,
                    onStartRename = {
                        deletingId = null
                        editingId = audio.id
                    },
                    onCancelRename = { editingId = null },
                    onRename = { newName ->
                        busyId = audio.id
                        scope.launch {
                            runCatching { source.rename(audio, newName) }
                                .onSuccess {
                                    editingId = null
                                    toaster?.show("文件已重命名")
                                }
                                .onFailure { toaster?.show(it.message ?: "重命名失败") }
                            busyId = null
                        }
                    },
                    onRequestDelete = {
                        editingId = null
                        deletingId = audio.id
                    },
                    onCancelDelete = { deletingId = null },
                    onDelete = {
                        busyId = audio.id
                        scope.launch {
                            runCatching { source.delete(audio) }
                                .onSuccess {
                                    deletingId = null
                                    toaster?.show("文件已删除")
                                }
                                .onFailure { toaster?.show(it.message ?: "删除失败") }
                            busyId = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SandboxFileCard(
    audio: LAudio,
    editing: Boolean,
    deleting: Boolean,
    busy: Boolean,
    actionsEnabled: Boolean,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onRename: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by remember(audio.id, editing) {
        mutableStateOf(audio.sandboxFileName().substringBeforeLast('.', audio.sandboxFileName()))
    }

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editing) {
                Text(
                    text = "重命名文件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("文件名") },
                    supportingText = { Text("扩展名会保持为 ${audio.sandboxExtensionLabel()}") },
                    enabled = !busy,
                    singleLine = true,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(enabled = !busy, onClick = onCancelRename) { Text("取消") }
                    TextButton(
                        enabled = newName.isNotBlank() && !busy,
                        onClick = { onRename(newName.trim()) },
                    ) { Text("保存") }
                }
                return@Column
            }

            Text(
                text = audio.sandboxFileName(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = audio.sandboxDescription(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (deleting) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "确认永久删除此文件？此操作无法撤销。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(enabled = !busy, onClick = onCancelDelete) { Text("取消") }
                            TextButton(enabled = !busy, onClick = onDelete) {
                                Text("确认删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(enabled = actionsEnabled, onClick = onStartRename) { Text("重命名") }
                    TextButton(enabled = actionsEnabled, onClick = onRequestDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxMediaSourceUnavailableContent() {
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp,
        ),
    ) {
        item {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "Sandbox 文件",
                subTitle = "当前平台未提供 Sandbox 媒体源",
            )
        }
        item {
            SandboxMessageCard(
                title = "无法打开",
                message = "Sandbox 媒体源当前不可用，请返回媒体源页面检查配置。",
                isError = true,
            )
        }
    }
}

@Composable
private fun SandboxMessageCard(
    title: String,
    message: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun LAudio.sandboxFileName(): String = extra
    ?.get(SandboxMediaSource.EXTRA_PATH)
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.takeIf { it.isNotBlank() }
    ?: title

private fun LAudio.sandboxExtensionLabel(): String = sandboxFileName()
    .substringAfterLast('.', "音频原格式")
    .let { if (it == "音频原格式") it else ".$it" }

private fun LAudio.sandboxDescription(): String = buildString {
    append(title)
    subtitle.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    extra?.get(SandboxMediaSource.EXTRA_FILE_SIZE)
        ?.toLongOrNull()
        ?.let { append(" · ").append(formatFileSize(it)) }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L * 1024L)} GB"
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
