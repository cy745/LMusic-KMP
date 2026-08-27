package com.lalilu.lmedia.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.Configurable
import com.lalilu.lmedia.source.Declaration
import com.lalilu.lmedia.source.configOrNullCompat


/**
 * 独立展示数据源的执行状态、最近一次成功结果与数据库提交状态。
 * Loading 或 Error 不会遮蔽上一次成功结果，便于区分“正在刷新”与“当前无数据”。
 */
@Composable
fun MediaSource.SourcePipelineCard(
    modifier: Modifier = Modifier,
    status: () -> SourceStatus = { SourceStatus(syncState = state.value) },
    snapshot: () -> Snapshot? = { this.snapshot.value },
    configForm: @Composable Configurable.() -> Unit = { PropertyComponent() },
    configActions: @Composable Configurable.(Modifier, () -> List<Declaration.Function<*>>) -> Unit = { actionModifier, functions ->
        FunctionComponent(actionModifier, functions)
    },
    extraMessage: () -> String? = { null },
    extraFunctions: () -> List<Declaration.Function<*>> = { EMPTY_LIST },
    extraContent: (@Composable () -> Unit)? = null,
) {
    val cfg = configOrNullCompat
    val title = remember { cfg?.name ?: name }
    val subtitle = remember { cfg?.description ?: "" }
    val currentStatus = status()
    val currentSnapshot = snapshot()

    BaseSourceCard(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        subtitleContent = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        modifier = Modifier.alpha(0.6f),
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                AnimatedVisibility(visible = !extraMessage().isNullOrBlank()) {
                    Text(
                        modifier = Modifier.alpha(0.6f),
                        text = "${extraMessage()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = currentStatus.syncState,
            contentKey = { it::class },
        ) { syncState ->
            when (syncState) {
                SnapshotState.Idle -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    (this as? Configurable)?.configForm()
                }

                is SnapshotState.Loading -> Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        progress = { syncState.progress },
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).alpha(0.3f),
                        text = syncState.message,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                is SnapshotState.Error -> ErrorMessage(syncState.message)
                SnapshotState.Success -> Unit
            }
        }

        currentSnapshot?.let {
            SnapshotPreviewCard(
                modifier = Modifier.padding(top = 4.dp),
                snapshot = { it },
            )
        }

        CommitStateMessage(
            modifier = Modifier.padding(top = 6.dp),
            state = currentStatus.commitState,
        )

        Column(Modifier.padding(top = 8.dp).fillMaxWidth()) {
            (this@SourcePipelineCard as? Configurable)
                ?.configActions(Modifier, extraFunctions)
        }

        extraContent?.invoke()
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            text = message,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CommitStateMessage(
    modifier: Modifier,
    state: SnapshotCommitState,
) {
    val message = when (state) {
        SnapshotCommitState.Idle -> null
        is SnapshotCommitState.Committing -> "正在写入媒体库…"
        is SnapshotCommitState.Committed -> "已写入媒体库"
        is SnapshotCommitState.Failed -> "写入媒体库失败：${state.message}"
    }

    AnimatedVisibility(visible = message != null) {
        Text(
            modifier = modifier.fillMaxWidth().alpha(0.4f),
            text = message.orEmpty(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = if (state is SnapshotCommitState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
