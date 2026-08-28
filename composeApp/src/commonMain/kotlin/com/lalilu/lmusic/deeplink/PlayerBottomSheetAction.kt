package com.lalilu.lmusic.deeplink

import co.touchlab.kermit.Logger
import com.lalilu.navigation.deeplink.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

const val PLAYER_BOTTOM_SHEET_ACTION_KEY = "player_bottom_sheet"

/**
 * 当前播放器 BottomSheet 的公共控制入口。
 *
 * 手机和平板使用的是两套方向相反的 BottomSheet 状态，因此这里不暴露具体 State，
 * 只保留“播放器是否展开”的统一语义。注册使用独立令牌，避免窗口尺寸切换时旧布局的
 * onDispose 误删新布局刚注册的控制器。
 */
@Single
class PlayerBottomSheetController {
    class Registration internal constructor()

    private data class Binding(
        val registration: Registration,
        val isExpanded: () -> Boolean,
        val expand: () -> Unit,
        val collapse: () -> Unit,
    )

    private val binding = MutableStateFlow<Binding?>(null)

    internal fun register(
        isExpanded: () -> Boolean,
        expand: () -> Unit,
        collapse: () -> Unit,
    ): Registration {
        val registration = Registration()
        binding.value = Binding(
            registration = registration,
            isExpanded = isExpanded,
            expand = expand,
            collapse = collapse,
        )
        return registration
    }

    internal fun unregister(registration: Registration) {
        binding.update { current ->
            current?.takeUnless { it.registration === registration }
        }
    }

    internal suspend fun execute(command: PlayerBottomSheetCommand): Boolean =
        withTimeoutOrNull(CONTROLLER_WAIT_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                // 在主线程读取并立即调用，避免自适应布局切换期间拿到已经注销的旧绑定。
                val active = binding.filterNotNull().first()
                when (command) {
                    PlayerBottomSheetCommand.Expand -> active.expand()
                    PlayerBottomSheetCommand.Collapse -> active.collapse()
                    PlayerBottomSheetCommand.Toggle -> {
                        if (active.isExpanded()) active.collapse() else active.expand()
                    }
                }
            }
            true
        } ?: false

    private companion object {
        const val CONTROLLER_WAIT_TIMEOUT_MS = 3_000L
    }
}

/**
 * 控制当前正在使用的播放器 BottomSheet。
 *
 * 调用方式：
 * - `lmusic://action/player_bottom_sheet?state=expanded`
 * - `lmusic://action/player_bottom_sheet?state=collapsed`
 * - `lmusic://action/player_bottom_sheet?state=toggle`
 */
@Named(PLAYER_BOTTOM_SHEET_ACTION_KEY)
@Single(binds = [Action::class])
class PlayerBottomSheetAction(
    private val controller: PlayerBottomSheetController,
) : Action {
    private val logger = Logger.withTag("PlayerBottomSheetAction")

    override suspend fun action(params: Map<String, String>) {
        val command = PlayerBottomSheetCommand.parse(params[STATE_PARAM]) ?: run {
            logger.w { "无效的播放器底栏状态：${params[STATE_PARAM]}" }
            return
        }

        if (!controller.execute(command)) {
            logger.w { "播放器底栏尚未完成组合，执行命令超时：$command" }
        }
    }

    private companion object {
        const val STATE_PARAM = "state"
    }
}

internal enum class PlayerBottomSheetCommand {
    Expand,
    Collapse,
    Toggle;

    companion object {
        fun parse(value: String?): PlayerBottomSheetCommand? = when (value?.lowercase()) {
            "expand", "expanded", "show", "open" -> Expand
            "collapse", "collapsed", "hide", "close" -> Collapse
            "toggle" -> Toggle
            else -> null
        }
    }
}
