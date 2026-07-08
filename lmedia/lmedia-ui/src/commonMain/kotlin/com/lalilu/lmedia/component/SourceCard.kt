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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.Declaration
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.preview.preview
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@Composable
fun MediaSource.SourceCard(
    modifier: Modifier = Modifier,
    state: () -> Snapshot = { Snapshot.Idle },
    configForm: @Composable MediaSource.() -> Unit = { PropertyComponent() },
    configActions: @Composable MediaSource.(Modifier, () -> List<Declaration.Function<*>>) -> Unit = { modifier, extraFunctions ->
        FunctionComponent(
            modifier,
            extraFunctions
        )
    },
    extraMessage: () -> String? = { null },
    extraFunctions: () -> List<Declaration.Function<*>> = { EMPTY_LIST },
    extraContent: (@Composable () -> Unit)? = null
) {
    val title = remember { config.name }
    val subtitle = remember { config.description }

    BaseSourceCard(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        subtitleContent = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        modifier = Modifier
                            .alpha(0.6f),
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                AnimatedVisibility(visible = !extraMessage().isNullOrBlank()) {
                    Text(
                        modifier = Modifier
                            .alpha(0.6f),
                        text = "${extraMessage()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    ) {
        AnimatedContent(targetState = state()) { stateValue ->
            when (val snapshotState = stateValue.state) {
                is SnapshotState.Idle -> Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    configForm()
                }

                is SnapshotState.Loading -> Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        progress = { snapshotState.progress }
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .alpha(0.3f),
                        text = snapshotState.message,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                is SnapshotState.Loading -> {
                    // LoadingDynamic merged into Loading in domain model
                }

                is SnapshotState.Empty,
                is SnapshotState.Success -> SnapshotPreviewCard(
                    modifier = Modifier.padding(top = 4.dp),
                    snapshot = { stateValue }
                )

                is SnapshotState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                modifier = Modifier,
                                text = snapshotState.message,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                else -> {}
            }
        }

        Column(
            Modifier.padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            configActions(Modifier, extraFunctions)
        }

        extraContent?.invoke()
    }
}

@Preview
@Composable
private fun SourceCardPreviewDefault() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewMediaSource.SourceCard(
            modifier = Modifier,
            state = { Snapshot.Idle },
        )
    }
}

@Preview
@Composable
private fun SourceCardPreviewScanning() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewMediaSource.SourceCard(
            modifier = Modifier,
            state = { Snapshot.Loading }
        )
    }
}

@Preview
@Composable
private fun SourceCardPreviewSuccess() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewMediaSource.SourceCard(
            modifier = Modifier,
            state = { Snapshot(state = SnapshotState.Success) }
        )
    }
}

@Preview
@Composable
private fun SourceCardPreviewError() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PreviewMediaSource.SourceCard(
            modifier = Modifier,
            state = { Snapshot(state = SnapshotState.Error("Test Error")) }
        )
    }
}