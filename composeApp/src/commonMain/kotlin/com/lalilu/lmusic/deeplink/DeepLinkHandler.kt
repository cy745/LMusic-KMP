package com.lalilu.lmusic.deeplink

import co.touchlab.kermit.Logger
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenActionRegistry
import com.lalilu.navigation.deeplink.Action
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

/**
 * LMusic 外部命令的统一入口。Android 和 iOS 只负责把系统 URL 转成字符串，协议解析、
 * 导航控制及 Action 分发全部在 commonMain 完成。
 */
object DeepLinkHandler {
    private val logger = Logger.withTag("DeepLinkHandler")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 接收并异步执行 Deep Link。返回值仅表示 URL 已被识别并成功解析，不表示异步业务已完成。
     */
    fun handle(rawUrl: String?): Boolean {
        val command = DeepLinkParser.parse(rawUrl) ?: return false
        scope.launch { execute(command) }
        return true
    }

    private suspend fun execute(command: DeepLinkCommand) {
        when (command) {
            is DeepLinkCommand.Navigate -> command.execute()
            is DeepLinkCommand.Pop -> command.execute()
            is DeepLinkCommand.InvokeAction -> command.execute()
            is DeepLinkCommand.InvokeScreenAction -> command.execute()
        }
    }

    private fun DeepLinkCommand.Navigate.execute() {
        val request = AppRouter.route(route)
            .withParams(params)
            .withSingleTop(singleTop)
            .withSingleInstance(singleInstance)

        when (operation) {
            NavigationOperation.Push -> request.push()
            NavigationOperation.Replace -> request.replace()
        }
    }

    private fun DeepLinkCommand.Pop.execute() {
        if (route == null) {
            AppRouter.intent(NavIntent.Pop)
        } else {
            AppRouter.route(route)
                .withParams(params)
                .popUntil()
        }
    }

    private suspend fun DeepLinkCommand.InvokeAction.execute() {
        val action = runCatching {
            KoinPlatform.getKoin().getOrNull<Action>(qualifier = named(key))
        }.onFailure {
            logger.e(it) { "读取 Deep Link Action 失败：$key" }
        }.getOrNull()

        if (action == null) {
            logger.w { "未找到 Deep Link Action：$key" }
            return
        }

        runCatching { action.action(params) }
            .onFailure { logger.e(it) { "执行 Deep Link Action 失败：$key" } }
    }

    private suspend fun DeepLinkCommand.InvokeScreenAction.execute() {
        AppRouter.awaitBound()
        val screen = AppRouter.currentScreen ?: run {
            logger.w { "导航栈为空，无法执行 ScreenAction：$key" }
            return
        }

        val actions = withTimeoutOrNull(SCREEN_ACTION_WAIT_TIMEOUT_MS) {
            ScreenActionRegistry.await(screen)
        } ?: run {
            logger.w { "等待栈顶页面注册 ScreenAction 超时：${screen.key}" }
            return
        }

        // 页面组合和 Deep Link 异步等待期间可能发生导航，执行前必须再次确认仍为同一个栈顶条目。
        if (AppRouter.currentScreen !== screen) {
            logger.w { "栈顶页面已变化，取消执行 ScreenAction：$key" }
            return
        }

        val matches = actions.filter { it.key == key }
        if (matches.size != 1) {
            logger.w {
                if (matches.isEmpty()) "栈顶页面未注册 ScreenAction：$key"
                else "栈顶页面存在重复的 ScreenAction key：$key"
            }
            return
        }

        runCatching { matches.single().onAction(ActionContext()) }
            .onFailure { logger.e(it) { "执行 ScreenAction 失败：$key" } }
    }

    private const val SCREEN_ACTION_WAIT_TIMEOUT_MS = 3_000L
}

internal enum class NavigationOperation {
    Push,
    Replace,
}

internal sealed interface DeepLinkCommand {
    data class Navigate(
        val operation: NavigationOperation,
        val route: String,
        val params: Map<String, Any?>,
        val singleTop: Boolean,
        val singleInstance: Boolean,
    ) : DeepLinkCommand

    data class Pop(
        val route: String?,
        val params: Map<String, Any?>,
    ) : DeepLinkCommand

    data class InvokeAction(
        val key: String,
        val params: Map<String, String>,
    ) : DeepLinkCommand

    data class InvokeScreenAction(val key: String) : DeepLinkCommand
}

internal object DeepLinkParser {
    private const val SCHEME = "lmusic"
    private const val ROUTE = "route"
    private const val KEY = "key"
    private const val SINGLE_TOP = "single_top"
    private const val SINGLE_INSTANCE = "single_instance"

    fun parse(rawUrl: String?): DeepLinkCommand? {
        val url = rawUrl
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { Url(it) }.getOrNull() }
            ?: return null
        if (url.protocol.name != SCHEME) return null

        return when (url.host.lowercase()) {
            "push" -> parseNavigate(url, NavigationOperation.Push)
            "replace" -> parseNavigate(url, NavigationOperation.Replace)
            "pop" -> parsePop(url)
            "action" -> parseAction(url)
            "screen_action" -> parseScreenAction(url)
            else -> null
        }
    }

    private fun parseNavigate(url: Url, operation: NavigationOperation): DeepLinkCommand.Navigate? {
        val route = url.route() ?: return null
        val singleTop = url.parameters.booleanControl(SINGLE_TOP) ?: return null
        val singleInstance = url.parameters.booleanControl(SINGLE_INSTANCE) ?: return null
        return DeepLinkCommand.Navigate(
            operation = operation,
            route = route,
            params = url.parameters.routeParams(),
            singleTop = singleTop,
            singleInstance = singleInstance,
        )
    }

    private fun parsePop(url: Url): DeepLinkCommand.Pop? {
        val hasRoute = url.parameters[ROUTE]?.isNotBlank() == true || url.segments.isNotEmpty()
        return DeepLinkCommand.Pop(
            route = if (hasRoute) url.route() ?: return null else null,
            params = url.parameters.routeParams(),
        )
    }

    private fun parseAction(url: Url): DeepLinkCommand.InvokeAction? {
        val key = url.parameters[KEY]
            ?.takeIf(String::isNotBlank)
            ?: url.segments.singleOrNull()?.takeIf(String::isNotBlank)
            ?: return null
        return DeepLinkCommand.InvokeAction(key, url.parameters.stringParams(excluded = setOf(KEY)))
    }

    private fun parseScreenAction(url: Url): DeepLinkCommand.InvokeScreenAction? {
        val key = url.parameters[KEY]
            ?.takeIf(String::isNotBlank)
            ?: url.segments.singleOrNull()?.takeIf(String::isNotBlank)
            ?: return null
        return DeepLinkCommand.InvokeScreenAction(key)
    }

    private fun Url.route(): String? {
        val rawRoute = parameters[ROUTE]
            ?.takeIf(String::isNotBlank)
            ?: segments.takeIf(List<String>::isNotEmpty)?.joinToString("/")
            ?: return null
        return if (rawRoute.startsWith('/')) rawRoute else "/$rawRoute"
    }

    /** 缺省为 false；一旦显式传入，就只接受唯一的 true / false。 */
    private fun Parameters.booleanControl(name: String): Boolean? {
        val values = getAll(name) ?: return false
        if (values.size != 1) return null
        return when (values.single().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun Parameters.routeParams(): Map<String, Any?> = buildMap {
        names()
            .filterNot { it in setOf(ROUTE, SINGLE_TOP, SINGLE_INSTANCE) }
            .forEach { rawName ->
                val values = getAll(rawName).orEmpty()
                val name = rawName.removeSuffix("[]")
                if (name.isNotBlank() && values.isNotEmpty()) {
                    put(name, if (rawName.endsWith("[]") || values.size > 1) values else values.single())
                }
            }
    }

    private fun Parameters.stringParams(excluded: Set<String>): Map<String, String> = buildMap {
        names().filterNot(excluded::contains).forEach { name ->
            getAll(name)?.lastOrNull()?.let { put(name, it) }
        }
    }
}
