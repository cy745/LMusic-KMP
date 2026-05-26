package com.lalilu.navigation

import androidx.navigation3.runtime.NavBackStack
import co.touchlab.kermit.Logger
import com.lalilu.krouter.KRouter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

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

fun interface NavInterceptor {
    fun intercept(backStack: NavBackStack<Screen>, intent: NavIntent): NavIntent
}

fun interface NavHandler {
    fun handle(backStack: NavBackStack<Screen>, intent: NavIntent)
}

/**
 * 针对TabScreen的拦截处理逻辑
 */
val DefaultInterceptorForTabScreen = NavInterceptor { backstack, intent ->
    val screen = when (intent) {
        is NavIntent.Jump -> intent.screen
        is NavIntent.Push -> intent.screen
        is NavIntent.Replace -> intent.screen
        else -> return@NavInterceptor intent
    }

    // 若不是TabScreen则返回
    if ((screen.actualScreen() as? ScreenInfoFactory)?.isTabScreen() != true) {
        return@NavInterceptor intent
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

/**
 * 当意图中有 [NavIntent.SINGLE_TOP] 时，当前显示页面如果和目标页面相同则不执行跳转操作
 */
val DefaultSingleTopInterceptor = NavInterceptor { backstack, intent ->
    val screen = intent.screen ?: return@NavInterceptor intent
    val singleTop = intent.params[NavIntent.SINGLE_TOP] as? Boolean ?: return@NavInterceptor intent
    if (!singleTop) return@NavInterceptor intent

    if (backstack.lastOrNull()?.key == screen.key) {
        return@NavInterceptor NavIntent.None
    }

    return@NavInterceptor intent
}

/**
 * 当意图中有 [NavIntent.SINGLE_INSTANCE] 时，当前页面和目标页面属于同类型则替换
 */
val DefaultSingleInstanceInterceptor = NavInterceptor { navigator, intent ->
    val lastScreen = navigator.lastOrNull() ?: return@NavInterceptor intent
    val screen = intent.screen ?: return@NavInterceptor intent
    val singleInstance = intent.params[NavIntent.SINGLE_INSTANCE] as? Boolean
        ?: return@NavInterceptor intent
    if (!singleInstance) return@NavInterceptor intent

    if (lastScreen::class == screen::class) {
        return@NavInterceptor NavIntent.Replace(screen)
    }

    return@NavInterceptor intent
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
    private val sharedFlow = MutableSharedFlow<NavIntent>()
    private var handler: NavHandler = DefaultHandler
    private val interceptors = mutableListOf(
        DefaultSingleTopInterceptor,
        DefaultSingleInstanceInterceptor,
        DefaultInterceptorForTabScreen,
    )

    suspend fun bind(
        backStack: NavBackStack<Screen>,
        onHandler: () -> Unit = {}
    ): Unit = sharedFlow.collect { intent ->
        interceptors
            .fold(intent) { temp, interceptor -> interceptor.intercept(backStack, temp) }
            .let { handler.handle(backStack, it) }
        onHandler()
    }

    fun intent(intent: NavIntent) = GlobalScope.launch {
        sharedFlow.emit(intent)
    }

    fun intent(block: AppRouter.() -> NavIntent?) = GlobalScope.launch {
        block()?.let { sharedFlow.emit(it) }
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
                .getOrElse {
                    Logger.e(
                        tag = "AppRouter",
                        messageString = "route request for [$baseUrl] Failed",
                    )
                    null
                }
            return screen?.wrapWith(params)
        }
    }
}