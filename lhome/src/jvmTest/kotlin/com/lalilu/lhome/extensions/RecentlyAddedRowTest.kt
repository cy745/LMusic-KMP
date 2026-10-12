package com.lalilu.lhome.extensions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecentlyAddedRowTest {
    @Test
    fun sameRawIdFromDifferentSourcesRendersBothSongsAfterReordering() = runDesktopComposeUiTest(
        width = 700,
        height = 400,
    ) {
        val local = LAudio(id = "audio_shared", title = "Local song", mediaSourceName = "MediaLibrarySource")
        val musicKit = local.copy(title = "Apple Music song", mediaSourceName = "MusicKitSource")
        val songs = mutableStateOf(listOf(local, musicKit))
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    RecentlyAddedRow(songs.value)
                }
            }
        }
        onNodeWithText(local.title).assertIsDisplayed()
        onNodeWithText(musicKit.title).assertIsDisplayed()
        runOnIdle { songs.value = listOf(musicKit, local) }
        onNodeWithText(local.title).assertIsDisplayed()
        onNodeWithText(musicKit.title).assertIsDisplayed()
    }
}
