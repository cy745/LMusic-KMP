package com.lalilu.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import co.touchlab.kermit.Logger
import com.lalilu.krouter.KRouter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    data class PopUntil(
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

        is NavIntent.PopUntil -> {
            val targetIndex = backstack.indexOfLast { it.key == intent.screen.key }
            if (targetIndex >= 0) {
                repeat(backstack.lastIndex - targetIndex) {
                    backstack.removeLastOrNull()
                }
            }
        }

        NavIntent.None -> {}
    }
}

/**
 * 路由跳转后展开播放页底栏（ModalBottomSheetLayout）的拦截器。
 *
 * ## 背景
 *
 * 播放页（PlayerScreen）由 `ScaleBottomSheetLayout` 作为底层 content 常驻组合，
 * 主路由页面渲染在其 sheetContent（上层）。当播放页处于折叠（Hidden）状态时，
 * 任意跳转到主路由页面的操作（如歌词设置子页、歌曲详情）都会让新页面被
 * 折叠的播放页挡住——历史上需要在跳转处手动 `bottomSheetState.show()`。
 *
 * 本拦截器放在拦截器链**最后**：只要意图没有被前面的拦截器消化为
 * [NavIntent.None]（说明发生了实际跳转），就通过 [SheetExpandInterceptor.expandModalSheet]
 * 请求展开底栏（若未注册则为 no-op，如平板布局 / 播放页未组合时）。
 *
 * ## 为什么放在最后
 *
 * 前面的拦截器（TooFast / SingleTop / SingleInstance / TabScreen）会把"不应
 * 跳转"的意图折叠为 [NavIntent.None]；只有真正要跳转的意图才会走到这里。
 * [com.lalilu.component.ModalBottomSheetState.show] 幂等——已展开时无副作用。
 */
object SheetExpandInterceptor : NavInterceptor {
    override val name: String = "SheetExpandInterceptor"

    /**
     * 由底栏容器（如 `ScaleBottomSheetLayout`）组合时注册的"展开回调"。
     *
     * 拦截器不是 @Composable 上下文，无法直接读 `LocalModalBottomSheetState`，
     * 因此通过此注册表间接触发展开。注册方负责用自身协程作用域包装
     * `bottomSheetState.show()`，并在组合销毁时置回 `null`。
     *
     * 独立于 [AppRouter] 挂载：展开逻辑是播放页底栏对导航行为的响应，
     * 不属于路由核心，避免 AppRouter 持有 UI 回调。
     */
    var expandModalSheet: (() -> Unit)? = null

    override fun intercept(backStack: NavBackStack<Screen>, intent: NavIntent): NavIntent {
        if (intent is NavIntent.None) return intent
        expandModalSheet?.invoke()
        return intent
    }
}

@OptIn(DelicateCoroutinesApi::class)
object AppRouter {
    private val logger = Logger.withTag("AppRouter")
    private val sharedFlow = MutableSharedFlow<NavIntent>()
    private val isBound = MutableStateFlow(false)
    private val _backStack = MutableStateFlow<List<Screen>>(emptyList())
    private var handler: NavHandler = DefaultHandler
    private val interceptors = mutableListOf<NavInterceptor>(
        DefaultTooFastJumpingInterceptor,
        DefaultSingleTopInterceptor,
        DefaultSingleInstanceInterceptor,
        DefaultInterceptorForTabScreen,
        SheetExpandInterceptor,
    )

    /** 路由意图热流：供其他组件监听实际发生的跳转（如弹窗自动关闭）。 */
    val intents: SharedFlow<NavIntent> get() = sharedFlow

    /**
     * 当前导航栈的只读快照。外部只能观察，所有修改仍需通过 [intent] 或 [route] 完成。
     */
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    /** 当前栈顶页面；返回导航栈中的原始条目，供组合期注册信息按实例匹配。 */
    val currentScreen: Screen? get() = _backStack.value.lastOrNull()

    /** 等待导航栈完成绑定，适用于冷启动阶段收到的外部命令。 */
    suspend fun awaitBound() = isBound.first { it }

    suspend fun bind(
        backStack: NavBackStack<Screen>,
        onHandler: () -> Unit = {}
    ): Unit = coroutineScope {
        // 在开放外部路由消费前同步发布初始栈，避免冷启动 Deep Link 已等到绑定完成，
        // 却因为 snapshotFlow 的首轮收集尚未调度而短暂读到空栈。
        _backStack.value = backStack.toList()

        // 监听所有栈变化（包括系统返回键、手势返回等）
        launch {
            snapshotFlow { backStack.toList() }
                .distinctUntilChanged()
                .collect { screens ->
                    _backStack.value = screens
                    val keys = screens.map { it.key }
                    logger.i { "📚 Stack [${keys.size}] ${keys.joinToString(" → \n")}" }
                }
        }

        // 处理 NavIntent 驱动的跳转
        launch {
            isBound.value = true
            try {
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
            } finally {
                isBound.value = false
                _backStack.value = emptyList()
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
                is NavIntent.PopUntil -> "⬅️ POP_UNTIL ${intent.screen.key}"
                is NavIntent.None -> "⏭️ NONE"
            }
        }
        // 调用方可能早于首帧导航栈绑定；等待绑定后再发送，避免 SharedFlow 丢事件。
        awaitBound()
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
        fun popUntil() = requestResult()?.let { intent(NavIntent.PopUntil(it, params)) }
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
