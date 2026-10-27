package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.playback.PlayableQueue
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.PlaybackMode
import com.lalilu.lplayer.playback.QueueState
import com.lalilu.navigation.smartbar.ActionItemContent
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class SongDetailActionsTest {
    @Test fun transportUpdatesInsideStableActionsAndExpandedMenuShowsLabels() = runDesktopComposeUiTest {
        val song = LAudio(id = "song", title = "Song", mediaSourceName = "local")
        val selection = MutableStateFlow(QueueState(listOf(song)))
        val intent = MutableStateFlow(false)
        val mode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
        val queue = proxy<PlayableQueue> { name ->
            when (name) { "getExpandedItems" -> selection; else -> error(name) }
        }
        val playback = proxy<Playback> { name ->
            when (name) { "getQueue" -> queue; "getWantsToPlay" -> intent; "getPlaybackMode" -> mode; else -> error(name) }
        }
        val expanded = mutableStateOf(false)
        lateinit var actions: List<ScreenAction>
        setContent {
            MaterialTheme {
                val current = rememberSongDetailActions(song.playbackId, playback, null, null)
                SideEffect { actions = current }
                Row {
                    current.forEach {
                        when (it) {
                            is ScreenAction.Dynamic -> it.content(ActionContext(isFullyExpanded = expanded.value))
                            is ScreenAction.Static -> ActionItemContent(color = it.color(), title = it.title())
                            is ScreenAction.DeepLink -> Unit
                        }
                    }
                }
            }
        }
        onNodeWithContentDescription("播放").assertIsDisplayed()
        onNodeWithContentDescription("下一首播放").assertIsDisplayed()
        onNodeWithText("播放").assertDoesNotExist()
        onNodeWithText("下一首播放").assertDoesNotExist()
        val original = actions
        runOnIdle { mode.value = PlaybackMode.SHUFFLE }
        onNodeWithContentDescription("下一首播放").assertIsDisplayed()
        runOnIdle { assertSame(original, actions) }
        runOnIdle { intent.value = true }
        onNodeWithContentDescription("暂停").assertIsDisplayed()
        runOnIdle { assertSame(original, actions); expanded.value = true }
        onNodeWithText("暂停").assertIsDisplayed()
        onNodeWithText("下一首播放").assertIsDisplayed()
        runOnIdle { intent.value = false }
        onNodeWithText("播放").assertIsDisplayed()
        runOnIdle { assertSame(original, actions); selection.value = QueueState() }
        onNodeWithText("播放").assertIsDisplayed()
    }

    private inline fun <reified T> proxy(crossinline getter: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> T::class.java.simpleName
                else -> getter(method.name)
            }
        } as T
}
