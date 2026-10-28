package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import com.lalilu.component.LibraryEmptyState
import com.lalilu.component.LibraryQueryContent
import com.lalilu.extensions.queryState
import com.lalilu.extensions.toQueryState
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class LibraryQueryStateTest {
    @Test fun firstQueryDoesNotRenderEmptyOrZeroCount() = runDesktopComposeUiTest(width = 390, height = 700) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val responses = Channel<List<String>>(Channel.UNLIMITED)
        val state = responses.receiveAsFlow().toQueryState(emptyList(), scope)
        try {
            setContent {
                MaterialTheme {
                    LibraryQueryContent(state) {
                        val items by state
                        Column(Modifier.fillMaxSize()) {
                            NavigatorHeader(title = "歌曲", subTitle = "共 ${items.size} 首歌曲")
                            if (items.isEmpty()) LibraryEmptyState("还没有歌曲", "添加后显示", actionLabel = "添加媒体源")
                            else androidx.compose.material3.Text(items.first())
                        }
                    }
                }
            }
            onNodeWithText("还没有歌曲").assertDoesNotExist()
            onNodeWithText("共 0 首歌曲").assertDoesNotExist()
            onNodeWithText("添加媒体源").assertDoesNotExist()
            runOnIdle { responses.trySend(listOf("已加载歌曲")) }
            onNodeWithText("已加载歌曲").assertExists()
            onNodeWithText("共 1 首歌曲").assertExists()
            runOnIdle { responses.trySend(emptyList()) }
            onNodeWithText("还没有歌曲").assertExists()
            onNodeWithText("添加媒体源").assertExists()
        } finally { scope.cancel() }
    }

    @Test fun waitingLabelIsDelayedAndDisappearsWhenQueryCompletes() = runDesktopComposeUiTest(width = 390, height = 700) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val responses = Channel<List<String>>(Channel.UNLIMITED)
        val state = responses.receiveAsFlow().toQueryState(emptyList(), scope)
        mainClock.autoAdvance = false
        try {
            setContent {
                MaterialTheme {
                    LibraryQueryContent(state, searching = true) {
                        if (state.value.isEmpty()) LibraryEmptyState("没有找到相关歌曲", "换个关键词")
                        else androidx.compose.material3.Text(state.value.first())
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            onNodeWithText("正在搜索…").assertDoesNotExist()
            onNodeWithText("没有找到相关歌曲").assertDoesNotExist()
            mainClock.advanceTimeBy(400)
            onNodeWithText("正在搜索…").assertExists()
            runOnIdle { responses.trySend(listOf("结果")) }
            mainClock.advanceTimeByFrame()
            onNodeWithText("正在搜索…").assertDoesNotExist()
            onNodeWithText("结果").assertExists()
        } finally { scope.cancel() }
    }

    @Test fun querySwitchKeepsContentAndCancelsPreviousRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val key = MutableStateFlow("a")
        val channels = mapOf("a" to Channel<List<String>>(Channel.UNLIMITED), "b" to Channel(Channel.UNLIMITED))
        val cancelled = mutableListOf<String>()
        val state = key.queryState(emptyList<String>(), scope) { selected ->
            flow { try { emitAll(channels.getValue(selected).receiveAsFlow()) } finally { cancelled += selected } }
        }
        try {
            assertTrue(state.loading)
            assertFalse(state.hasResult)
            channels.getValue("a").send(listOf("A"))
            assertEquals(listOf("A"), state.value)
            key.value = "b"
            assertTrue(state.loading)
            assertEquals(listOf("A"), state.value)
            assertTrue("a" in cancelled)
            channels.getValue("a").send(listOf("过期结果"))
            channels.getValue("b").send(emptyList())
            assertFalse(state.loading)
            assertTrue(state.hasResult)
            assertEquals(emptyList(), state.value)
        } finally { scope.cancel() }
    }

    @Test fun upstreamAndQueryConstructionFailuresCanBothRetry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var upstreamAttempts = 0
        var queryAttempts = 0
        val keys = flow {
            if (++upstreamAttempts == 1) error("upstream unavailable")
            emit("key")
        }
        val state = keys.queryState(emptyList<String>(), scope) {
            if (++queryAttempts == 1) error("cannot construct query")
            flowOf(listOf("recovered"))
        }
        try {
            assertNotNull(state.error)
            assertFalse(state.loading)
            state.retry()
            assertNotNull(state.error)
            assertFalse(state.loading)
            state.retry()
            assertEquals(listOf("recovered"), state.value)
            assertNull(state.error)
        } finally { scope.cancel() }
    }

    @Test fun failureIsNotAnEmptyResultAndRetryCanRecover() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempt = 0
        val state = flowOf(Unit).queryState(emptyList<String>(), scope) {
            flow { if (++attempt == 1) error("fixture") else emit(listOf("重试成功")) }
        }
        try {
            assertFalse(state.hasResult)
            assertNotNull(state.error)
            assertFalse(state.loading)
            state.retry()
            assertEquals(listOf("重试成功"), state.value)
            assertNull(state.error)
        } finally { scope.cancel() }
    }
}
