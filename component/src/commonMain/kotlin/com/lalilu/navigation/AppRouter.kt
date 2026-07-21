package com.lalilu.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import co.touchlab.kermit.Logger
import com.lalilu.krouter.KRouter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

typealias NavParams = Map<String, Any?>
typealias MutableNavParams = MutableMap<String, Any?>

sealed class NavIntent(
    open val screen: Screen? = null,
    open val params: NavParams = emptyMap()
) {
    data class Jump(
        override val screen: Screen,
        override val params: NavParams = emptyMap()
    ) : NavIntent(screen, params)

    data class Push(
        override val screen: Screen,
        override val params: NavParams = emptyMap()
    ) : NavIntent(screen, params)

    data class Replace(
        override val screen: Screen,
        override val params: NavParams = emptyMap()
    ) : NavIntent(screen, params)

    data class PopUtil(
        override val screen: Screen,
        override val params: NavParams = emptyMap()
    ) : NavIntent(screen, params)

    data object Pop : NavIntent()
    data object None : NavIntent()

    companion object {
        const val SINGLE_TOP = "singleTop"
        const val SINGLE_INSTANCE = "singleInstance"
    }
}

interface NavInterceptor {
    val name: String
    fun intercept(backStack: NavBackStack<Screen>, intent: NavIntent): NavIntent
}

data class NavInterceptorImpl(
    override val name: String,
    private val doIntercept: (NavBackStack<Screen>, NavIntent) -> NavIntent
) : NavInterceptor {
    override fun intercept(backStack: NavBackStack<Screen>, intent: NavIntent): NavIntent =
        doIntercept(backStack, intent)
}

fun interface NavHandler {
    fun handle(backStack: NavBackStack<Screen>, intent: NavIntent)
}

/**
 * 针对TabScreen的拦截处理逻辑
 */
val DefaultInterceptorForTabScreen = NavInterceptorImpl("TabScreenInterceptor") { backstack, intent ->
    val screen = when (intent) {
        is NavIntent.Jump -> intent.screen
        is NavIntent.Push -> intent.screen
        is NavIntent.Replace -> intent.screen
        else -> return@NavInterceptorImpl intent
    }

    // 若不是TabScreen则返回
    if ((screen.actualScreen() as? ScreenInfoFactory)?.isTabScreen() != true) {
        return@NavInterceptorImpl intent
    }

    val first = backstack.removeAt(0)
    backstack.clear()
    backstack.add(first)

    // 如果栈顶的页面与目标页面不同则替换
    if (backstack.lastOrNull() != screen) {
        NavIntent.Push(screen)
    } else {
        NavIntent.None
    }
}

var lastTime = 0L

/**
 * 避免两个跳转事件过快发生，可能出现用户快速点击同一个按钮或不同的两个按钮的逻辑，一段时间内拦截掉后一个
 */
@OptIn(ExperimentalTime::class)
val DefaultTooFastJumpingInterceptor = NavInterceptorImpl("TooFastJumpingInterceptor") { navigator, intent ->
    val now = Clock.System.now().toEpochMilliseconds()
    if (intent is NavIntent.Jump) {
        if (now - lastTime < 200) {
            return@NavInterceptorImpl NavIntent.None
        }
    }

    lastTime = now
    return@NavInterceptorImpl intent
}

/**
 * 当意图中有 [NavIntent.SINGLE_TOP] 时，当前显示页面如果和目标页面相同则不执行跳转操作
 */
val DefaultSingleTopInterceptor = NavInterceptorImpl("SingleTopInterceptor") { backstack, intent ->
    val screen = intent.screen ?: return@NavInterceptorImpl intent
    val singleTop = intent.params[NavIntent.SINGLE_TOP] as? Boolean ?: return@NavInterceptorImpl intent
    if (!singleTop) return@NavInterceptorImpl intent

    if (backstack.lastOrNull()?.key == screen.key) {
        return@NavInterceptorImpl NavIntent.None
    }

    return@NavInterceptorImpl intent
}

/**
 * 当意图中有 [NavIntent.SINGLE_INSTANCE] 时，当前页面和目标页面属于同类型则替换
 */
val DefaultSingleInstanceInterceptor = NavInterceptorImpl("SingleInstanceInterceptor") { navigator, intent ->
    val lastScreen = navigator.lastOrNull() ?: return@NavInterceptorImpl intent
    val screen = intent.screen ?: return@NavInterceptorImpl intent
    val singleInstance = intent.params[NavIntent.SINGLE_INSTANCE] as? Boolean
        ?: return@NavInterceptorImpl intent
    if (!singleInstance) return@NavInterceptorImpl intent

    if (lastScreen::class == screen::class) {
        return@NavInterceptorImpl NavIntent.Replace(screen)
    }

    return@NavInterceptorImpl intent
}

val DefaultHandler = NavHandler { backstack, intent ->
    when (intent) {
        NavIntent.Pop -> backstack.removeLastOrNull()
        is NavIntent.Push -> backstack.add(intent.screen)
        is NavIntent.Replace -> {
            if (backstack.removeLastOrNull() != null) {
                backstack.add(intent.screen)
            }
        }

        is NavIntent.Jump -> {
            backstack.add(intent.screen)
        }

        is NavIntent.PopUtil -> {
            // TODO screen的比较需要确认是否正确
            while (backstack.lastOrNull() != intent.screen) {
                backstack.removeLastOrNull()
            }
        }

        NavIntent.None -> {}
    }
}

@OptIn(DelicateCoroutinesApi::class)
object AppRouter {
    private val logger = Logger.withTag("AppRouter")
    private val sharedFlow = MutableSharedFlow<NavIntent>()
    private var handler: NavHandler = DefaultHandler
    private val interceptors = mutableListOf<NavInterceptor>(
        DefaultTooFastJumpingInterceptor,
        DefaultSingleTopInterceptor,
        DefaultSingleInstanceInterceptor,
        DefaultInterceptorForTabScreen,
    )

    suspend fun bind(
        backStack: NavBackStack<Screen>,
        onHandler: () -> Unit = {}
    ): Unit = coroutineScope {
        // 监听所有栈变化（包括系统返回键、手势返回等）
        launch {
            snapshotFlow { backStack.toList() }
                .map { it.map { s -> s.key } }
                .distinctUntilChanged()
                .collect { keys ->
                    logger.i { "📚 Stack [${keys.size}] ${keys.joinToString(" → \n")}" }
                }
        }

        // 处理 NavIntent 驱动的跳转
        launch {
            sharedFlow.collect { intent ->
                interceptors
                    .fold(intent) { temp, interceptor ->
                        val result = interceptor.intercept(backStack, temp)
                        if (result != temp) {
                            logger.i { "⛔ Interceptor ${interceptor.name} intercepted: $temp → $result" }
                        }
                        result
                    }
                    .let { handler.handle(backStack, it) }
                onHandler()
            }
        }
    }

    fun intent(intent: NavIntent) = GlobalScope.launch {
        logger.i {
            when (intent) {
                is NavIntent.Push -> "➡️ PUSH  ${intent.screen.key}"
                is NavIntent.Jump -> "🔀 JUMP  ${intent.screen.key}"
                is NavIntent.Replace -> "🔄 REPLACE ${intent.screen.key}"
                is NavIntent.Pop -> "⬅️ POP"
                is NavIntent.PopUtil -> "⬅️ POP_UTIL ${intent.screen.key}"
                is NavIntent.None -> "⏭️ NONE"
            }
        }
        sharedFlow.emit(intent)
    }

    fun intent(block: AppRouter.() -> NavIntent?) = GlobalScope.launch {
        block()?.let { intent(it) }
    }

    fun route(baseUrl: String): Request = Request(baseUrl)

    class Request internal constructor(
        private val baseUrl: String,
        private val params: MutableNavParams = mutableMapOf()
    ) {
        fun <T : Any?> with(key: String, value: T) =
            apply { params[key] = value }

        fun withParams(params: Map<String, Any?>) =
            apply { this.params.putAll(params) }

        fun withSingleInstance(singleInstance: Boolean = true) =
            apply { params[NavIntent.SINGLE_INSTANCE] = singleInstance }

        fun withSingleTop(singleTop: Boolean = true) =
            apply { params[NavIntent.SINGLE_TOP] = singleTop }


        fun jump() = requestResult()?.let { intent(NavIntent.Jump(it, params)) }
        fun push() = requestResult()?.let { intent(NavIntent.Push(it, params)) }
        fun replace() = requestResult()?.let { intent(NavIntent.Replace(it, params)) }
        fun get() = requestResult()


        private fun requestResult(): Screen? {
            val screen = runCatching { KRouter.route<Screen>(baseUrl, params) }
                .onSuccess { logger.i { "✅ $baseUrl → ${it?.key}" } }
                .onFailure { logger.e { "‼️ route request for [$baseUrl] Failed: ${it.message}" } }
                .getOrNull()
            return screen?.wrapWith(params)
        }
    }
}