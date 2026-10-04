package com.lalilu.lplayer.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.LPlayerKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 加载过渡测试面板的动作实现。
 *
 * 与 UI 解耦，让 debug Activity（外部命令驱动）与悬浮面板（用户点击驱动）共用同一套
 * 时序注入逻辑，避免两处各自维护出现行为漂移。注入只改变界面表现所需的时间参数，
 * 不改变真实取源与播放链路。
 */
class PlayerDebugActions(private val scope: CoroutineScope) {

    /** 切换时序 Profile，可选择是否立刻切到下一首（切换本身才产生过渡）。 */
    fun configure(profile: String, advance: Boolean = true) {
        PlayerLoadTestHarness.configure(profile)
        if (advance) next()
    }

    fun next() {
        scope.launch { advanceTrack() }
    }

    /** 连续切歌：用于验证旧请求不会回写新曲。 */
    fun rapid(count: Int = 3, intervalMillis: Long = 300L) {
        scope.launch {
            repeat(count) {
                advanceTrack()
                delay(intervalMillis)
            }
        }
    }

    /** 注入一段再缓冲展示状态，真实的播放位置不受影响。 */
    fun injectBuffering(durationMillis: Long = 3_000L) {
        scope.launch {
            PlayerLoadTestHarness.setBuffering(true)
            delay(durationMillis)
            PlayerLoadTestHarness.setBuffering(false)
        }
    }

    fun disable() = PlayerLoadTestHarness.disable()

    private suspend fun advanceTrack() {
        // 冷启动后队列恢复是异步的：不要静默丢掉这次意图。
        val queue = withTimeoutOrNull(10_000L) {
            LPlayer.instance.queue.expandedItems.first { it.list.isNotEmpty() }
        } ?: return
        // 测试用切换必须能前进，即使用户当前是单曲循环。
        LPlayer.instance.skipTo((queue.index + 1) % queue.list.size, start = true)
    }
}

@Composable
fun rememberPlayerDebugActions(): PlayerDebugActions {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerDebugActions(scope) }
}

/**
 * 加载过渡测试面板。
 *
 * 由「右上角悬浮窗」与「debug 源集的测试 Activity」共用；[onDismiss] 由调用方决定
 * 是关闭悬浮窗还是只隐藏面板。
 */
@Composable
fun PlayerDebugPanel(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "隐藏面板",
    actions: PlayerDebugActions = rememberPlayerDebugActions(),
) {
    val profile by PlayerLoadTestHarness.profile.collectAsState()
    val buffering by PlayerLoadTestHarness.forcedBuffering.collectAsState()
    val queue by LPlayer.instance.queue.expandedItems.collectAsState()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.widthIn(max = 360.dp).padding(horizontal = 8.dp)) {
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "收起加载测试 · ${profile.name}" else "加载测试")
            }
            if (expanded) {
                Text("时序注入 · 仅影响界面表现", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "队列 ${queue.list.size} 首 · ${if (buffering) "模拟再缓冲" else "就绪"}",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (queue.list.isEmpty()) {
                    Text("请先播放任意歌曲建立队列。", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = { actions.configure("local") }) { Text("本地") }
                    TextButton(onClick = { actions.configure("slow") }) { Text("慢加载") }
                    TextButton(onClick = { actions.configure("partial") }) { Text("分批就绪") }
                }
                Row {
                    TextButton(onClick = { actions.configure("unknown") }) { Text("未知时长") }
                    TextButton(onClick = { actions.injectBuffering() }) { Text("再缓冲 3 秒") }
                }
                Row {
                    TextButton(onClick = actions::next) { Text("下一首") }
                    TextButton(onClick = { actions.rapid() }) { Text("连续切 3 首") }
                    TextButton(onClick = actions::disable) { Text("关闭注入") }
                }
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    }
}

/**
 * 悬浮入口：设置页开启「悬浮加载测试面板」后，在任意页面右上角显示测试面板。
 *
 * 默认关闭，且只注入展示层时序，不参与真实取源与播放判定。
 */
@Composable
fun PlayerDebugPanelHost(modifier: Modifier = Modifier) {
    val enabled = LPlayerKV.debugFloatingPanel.value
    if (!enabled) return

    var expanded by remember { mutableStateOf(true) }
    PlayerDebugPanel(
        modifier = modifier,
        expanded = expanded,
        onToggleExpanded = { expanded = !expanded },
        onDismiss = { LPlayerKV.debugFloatingPanel.value = false },
        dismissLabel = "关闭悬浮窗",
    )
}
