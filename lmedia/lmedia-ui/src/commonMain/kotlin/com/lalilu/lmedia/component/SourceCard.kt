package com.lalilu.lmedia.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.preview.preview
import kotlin.time.ExperimentalTime


@Stable
@Immutable
sealed interface SourceState {
    abstract class Selected(open val state: String) : SourceState
    abstract class Finished(override val state: String) : Selected(state = state)

    object Idle : SourceState

    data class Loading(
        val progress: () -> Float,
        val message: (() -> String)? = null,
        override val state: String
    ) : Selected(state = state)

    data class Success(val result: Snapshot, override val state: String) : Finished(state = state)
    data class Error(val error: Throwable, override val state: String) : Finished(state = state)
}

@OptIn(ExperimentalTime::class)
@Composable
fun MediaSource.SourceCard(
    modifier: Modifier = Modifier,
    state: SourceState = SourceState.Idle,
    configForm: @Composable MediaSource.() -> Unit = { PropertyComponent() },
    configActions: @Composable MediaSource.(Modifier) -> Unit = { FunctionComponent(it) },
    sourceActions: @Composable MediaSource.(SourceState) -> Unit = {}
) {
    val title = remember { config.name }
    val subtitle = remember { config.description }

    BaseSourceCard(
        modifier = modifier,
        title = title,
        subtitle = "Selected Path: ${(state as? SourceState.Selected)?.state}",
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

                val msg = (state as? SourceState.Selected)?.state
                AnimatedVisibility(visible = !msg.isNullOrBlank()) {
                    Text(
                        modifier = Modifier
                            .alpha(0.6f),
                        text = "$msg",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    ) {
        AnimatedContent(targetState = state) { stateValue ->
            when (stateValue) {
                is SourceState.Idle -> Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    configForm()
                }

                is SourceState.Loading -> Column {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        progress = stateValue.progress
                    )

                    stateValue.message?.let {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .alpha(0.3f),
                            text = it.invoke(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                is SourceState.Success -> SnapshotPreviewCard(
                    modifier = Modifier.padding(top = 4.dp),
                    snapshot = { stateValue.result }
                )

                is SourceState.Error -> {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "${stateValue.error.message}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {}
            }
        }

        Column(
            Modifier.padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            configActions(Modifier)
            sourceActions(state)
        }
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
            state = SourceState.Idle,
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
            state = SourceState.Loading(
                progress = { 0.5f },
                message = { "Scanning..." },
                state = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
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
            state = SourceState.Success(
                result = Snapshot.Empty.copy(
                    updateTime = 0L
                ),
                state = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
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
            state = SourceState.Error(
                error = IllegalArgumentException("Invalid path"),
                state = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
        )
    }
}