package com.lalilu.lmedia.source.filesystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.preview.preview
import kotlin.time.ExperimentalTime


@Stable
@Immutable
sealed interface FileSystemScannerCardState {
    object NotSelected : FileSystemScannerCardState
    abstract class Selected(open val path: String) : FileSystemScannerCardState
    abstract class Finished(override val path: String) : Selected(path = path)

    data class Scanning(
        val progress: () -> Float,
        val message: (() -> String)? = null,
        override val path: String
    ) : Selected(path = path)

    data class Success(val result: Snapshot, override val path: String) : Finished(path = path)
    data class Error(val error: Throwable, override val path: String) : Finished(path = path)
}

sealed interface FileSystemScannerCardIntent {
    data object Select : FileSystemScannerCardIntent
    data object Cancel : FileSystemScannerCardIntent
    data object ReScan : FileSystemScannerCardIntent
}

@OptIn(ExperimentalTime::class)
@Composable
fun FileSystemScannerCard(
    modifier: Modifier = Modifier,
    state: FileSystemScannerCardState = FileSystemScannerCardState.NotSelected,
    onIntent: (FileSystemScannerCardIntent) -> Unit = {}
) {
    Card(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "FileSystemScannerCard",
                style = MaterialTheme.typography.titleMedium
            )

            AnimatedVisibility(visible = state is FileSystemScannerCardState.Selected) {
                Text(
                    modifier = Modifier
                        .alpha(0.6f)
                        .padding(top = 8.dp),
                    text = "Selected Path: ${(state as? FileSystemScannerCardState.Selected)?.path}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            AnimatedContent(targetState = state) { stateValue ->
                when (stateValue) {
                    is FileSystemScannerCardState.Scanning -> Column {
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

                    is FileSystemScannerCardState.Success -> SnapshotPreviewCard(
                        modifier = Modifier.padding(top = 12.dp),
                        snapshot = { stateValue.result }
                    )

                    is FileSystemScannerCardState.Error -> {
                        Text(
                            modifier = Modifier.padding(top = 12.dp),
                            text = "${stateValue.error.message}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {}
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedContent(targetState = state is FileSystemScannerCardState.Scanning) { isScanning ->
                    TextButton(
                        onClick = {
                            onIntent(if (isScanning) FileSystemScannerCardIntent.Cancel else FileSystemScannerCardIntent.Select)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            text = if (isScanning) "Cancel" else "Select"
                        )
                    }
                }

                AnimatedVisibility(visible = state is FileSystemScannerCardState.Finished) {
                    TextButton(
                        onClick = { onIntent(FileSystemScannerCardIntent.ReScan) },
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            text = "ReScan"
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun FileSystemScannerCardPreviewDefault() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FileSystemScannerCard(
            modifier = Modifier,
            state = FileSystemScannerCardState.NotSelected
        )
    }
}

@Preview
@Composable
private fun FileSystemScannerCardPreviewScanning() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FileSystemScannerCard(
            modifier = Modifier,
            state = FileSystemScannerCardState.Scanning(
                progress = { 0.5f },
                message = { "Scanning..." },
                path = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
        )
    }
}

@Preview
@Composable
private fun FileSystemScannerCardPreviewSuccess() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FileSystemScannerCard(
            modifier = Modifier,
            state = FileSystemScannerCardState.Success(
                result = Snapshot.Empty.copy(
                    updateTime = 0L
                ),
                path = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
        )
    }
}

@Preview
@Composable
private fun FileSystemScannerCardPreviewError() = preview(isDarkMode = true) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FileSystemScannerCard(
            modifier = Modifier,
            state = FileSystemScannerCardState.Error(
                error = IllegalArgumentException("Invalid path"),
                path = "/Users/miku/Documents/IdeaProjects/LMusic-KMP/lmedia/src/androidMain/kotlin/com/lalilu/lmedia/source/filesystem"
            )
        )
    }
}