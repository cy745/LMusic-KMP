package com.lalilu.lmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lplayer.debug.PlayerDebugActions
import com.lalilu.lplayer.debug.PlayerDebugPanel
import com.lalilu.lplayer.debug.PlayerLoadTestHarness
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Debug source set only: real application, real local playback, controlled presentation delays.
 *
 * 面板本身与「设置页 → 调试 → 悬浮加载测试面板」共用 [PlayerDebugPanel]；本 Activity 只负责
 * 把 adb 传入的场景参数翻译成同一套 [PlayerDebugActions]，供命令行驱动。
 */
class PlayerTransitionTestActivity : ComponentActivity() {
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actions by lazy { PlayerDebugActions(testScope) }
    private var showPanel by mutableStateOf(true)
    private var showOverlay by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        applyIntent(intent)
        setContent {
            Box(Modifier.fillMaxSize()) {
                App()
                if (showOverlay) MaterialTheme {
                    PlayerDebugPanel(
                        expanded = showPanel,
                        onToggleExpanded = { showPanel = !showPanel },
                        onDismiss = { showOverlay = false },
                        dismissLabel = "隐藏测试面板",
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent) {
        showOverlay = !intent.getBooleanExtra("hidePanel", false)
        showPanel = true
        actions.configure(
            profile = intent.getStringExtra("scenario") ?: "slow",
            advance = intent.getBooleanExtra("next", true),
        )
        if (intent.getBooleanExtra("rapid", false)) actions.rapid()
        if (intent.getBooleanExtra("buffer", false)) actions.injectBuffering()
    }

    override fun onDestroy() {
        testScope.cancel()
        if (!isChangingConfigurations) PlayerLoadTestHarness.disable()
        super.onDestroy()
    }
}
