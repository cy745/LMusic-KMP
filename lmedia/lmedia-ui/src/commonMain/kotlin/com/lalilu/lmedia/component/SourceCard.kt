package com.lalilu.lmedia.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 公共卡片向来源专用 UI 暴露的只读流水线状态。 */
data class SourcePipelineUiState(
    val syncState: SnapshotState,
    val snapshot: Snapshot?,
    val commitState: SnapshotCommitState,
) {
    val isLoading: Boolean
        get() = syncState is SnapshotState.Loading
}

/**
 * 数据源卡片的稳定骨架：只负责来源身份、加载状态、最近结果与数据库写入状态。
 * 目录选择、账号配置等差异化交互由 [content] 自行组织，避免公共层再次演变成配置 DSL。
 */
@Composable
fun MediaSource.SourcePipelineCard(
    modifier: Modifier = Modifier,
    title: String = name,
    description: String = "",
    idleLabel: String = "待同步",
    content: @Composable ColumnScope.(SourcePipelineUiState) -> Unit = {},
) {
    val repository = koinInject<MediaSourceBindingRepository>()
    val scope = rememberCoroutineScope()
    val syncState = state.collectAsStateWithLifecycle()
    val latestSnapshot = snapshot.collectAsStateWithLifecycle()
    val sourceStatus = repository.observeSource(name)
        .collectAsStateWithLifecycle(initialValue = null)
    val currentSnapshot = latestSnapshot.value
    val currentStatus = sourceStatus.value ?: SourceStatus(
        syncState = syncState.value,
        resultRevision = currentSnapshot?.revision,
        songCount = currentSnapshot?.audios?.size ?: 0,
    )
    val uiState = SourcePipelineUiState(
        syncState = currentStatus.syncState,
        snapshot = currentSnapshot,
        commitState = currentStatus.commitState,
    )

    BaseSourceCard(
        modifier = modifier,
        title = title,
        subtitle = description,
        actionContent = {
            SourceStatusBadge(
                syncState = uiState.syncState,
                snapshot = uiState.snapshot,
                commitState = uiState.commitState,
                idleLabel = idleLabel,
            )
        },
    ) {
        AnimatedContent(
            targetState = uiState.syncState,
            contentKey = { it::class },
        ) { state ->
            when (state) {
                SnapshotState.Idle,
                SnapshotState.Success -> Unit

                is SnapshotState.Loading -> LoadingMessage(state)
                is SnapshotState.Error -> ErrorMessage(state.message)
            }
        }

        uiState.snapshot?.let { snapshot ->
            SnapshotPreviewCard(
                modifier = Modifier.padding(top = 12.dp),
                snapshot = snapshot,
            )
        }

        CommitStateMessage(
            modifier = Modifier.padding(top = 8.dp),
            state = uiState.commitState,
            onRetry = { scope.launch { repository.retryCommit(name) } },
        )

        content(uiState)
    }
}

enum class SourceActionStyle {
    Primary,
    Secondary,
    Quiet,
    Danger,
}

@Composable
fun SourceActionButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SourceActionStyle = SourceActionStyle.Secondary,
    onClick: () -> Unit,
) {
    when (style) {
        SourceActionStyle.Primary -> FilledTonalButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            content = { Text(title) },
        )

        SourceActionStyle.Secondary -> OutlinedButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            content = { Text(title) },
        )

        SourceActionStyle.Quiet -> TextButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            content = { Text(title) },
        )

        SourceActionStyle.Danger -> TextButton(
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            content = { Text(title) },
        )
    }
}

/**
 * 数据源配置表单统一使用更柔和的填充和圆角，避免 Material 默认的小圆角、重描边
 * 与外层数据源卡片产生割裂感。
 */
@Composable
fun SourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)

    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    )
}

/** 用于展示路径、账号或权限等当前配置，而不是直接暴露编辑控件。 */
@Composable
fun SourceInfoPanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supportingText?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
fun SourceSectionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToggle) {
            Text(if (expanded) "收起" else "编辑")
        }
    }
}

@Composable
private fun SourceStatusBadge(
    syncState: SnapshotState,
    snapshot: Snapshot?,
    commitState: SnapshotCommitState,
    idleLabel: String,
) {
    val (label, colors) = when {
        syncState is SnapshotState.Loading -> "同步中" to MaterialTheme.colorScheme.tertiaryContainer
        commitState is SnapshotCommitState.Committing -> "写入中" to MaterialTheme.colorScheme.tertiaryContainer
        syncState is SnapshotState.Error -> "同步失败" to MaterialTheme.colorScheme.errorContainer
        commitState is SnapshotCommitState.Failed -> "写入失败" to MaterialTheme.colorScheme.errorContainer
        snapshot != null -> "已就绪" to MaterialTheme.colorScheme.primaryContainer
        else -> idleLabel to MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Surface(
        color = colors,
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingMessage(state: SnapshotState.Loading) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { state.progress },
        )
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp).alpha(0.55f),
            text = state.message,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "同步没有完成",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier.padding(top = 3.dp),
                text = message,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CommitStateMessage(
    modifier: Modifier,
    state: SnapshotCommitState,
    onRetry: () -> Unit,
) {
    val message = when (state) {
        SnapshotCommitState.Idle,
        is SnapshotCommitState.Committed -> null

        is SnapshotCommitState.Committing -> "正在把新结果写入媒体库…"
        is SnapshotCommitState.Failed -> "写入媒体库失败：${state.message}"
    }

    AnimatedVisibility(visible = message != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = message.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = if (state is SnapshotCommitState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (state is SnapshotCommitState.Failed) {
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}
