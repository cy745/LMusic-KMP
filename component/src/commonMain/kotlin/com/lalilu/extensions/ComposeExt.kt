package com.lalilu.extensions

import androidx.compose.animation.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isUnspecified
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 经典返回处理器，处理系统的返回事件（如返回键或手势）
 *
 * @param enabled 是否启用返回处理，默认为true
 * @param onBack 当触发返回事件时执行的回调函数，默认为空实现
 */
@Composable
fun ClassicBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit = {}
) {
    // 如果处于检查模式，则不处理返回事件
    if (LocalInspectionMode.current) {
        return
    }

    // 记住导航事件状态，初始化为无导航信息
    val navEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    // 处理导航事件，监听返回手势或按键
    NavigationEventHandler(
        state = navEventState,
        isBackEnabled = enabled,        // 是否启用返回功能
        onBackCancelled = {},           // 返回被取消时的回调（空实现）
        onBackCompleted = onBack        // 返回完成时执行的回调
    )
}


/**
 * 扩展函数，用于在 Compose 环境中同步获取 StringResource 的字符串值
 *
 * @return StringResource 对应的本地化字符串
 */
@Composable
fun StringResource.retrieve(): String = stringResource(this)

/**
 * 扩展函数，用于在协程环境中异步获取 StringResource 的字符串值
 *
 * @return StringResource 对应的本地化字符串
 */
suspend fun StringResource.get(): String = getString(this)


/**
 * 一个仅执行一次进入动画的可见性组件
 *
 * 该组件在首次组合时处于隐藏状态，并在指定的延迟后变为可见，从而触发 [enter] 动画。
 * 一旦变为可见，它将保持可见状态，除非父级重组导致其被移除。退出动画 [exit] 仅在组件从组合中移除时触发。
 *
 * @param modifier 应用于 [AnimatedVisibility] 的修饰符
 * @param delay 在显示内容之前等待的持续时间，默认为 0 毫秒
 * @param enter 当组件变为可见时应用的进入过渡动画，默认为淡入效果
 * @param exit 当组件变为不可见时应用的退出过渡动画，默认为淡出效果
 * @param label 用于调试和性能分析的标签
 * @param content 当组件可见时显示的内容
 */
@Composable
fun AnimateVisibleForOnce(
    modifier: Modifier = Modifier,
    delay: Duration = 0.milliseconds,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    label: String = "AnimateVisibleForOnce",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    var visible by retain { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        if (isActive) visible = true
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = enter,
        exit = exit,
        label = label,
        content = content
    )
}

@Stable
fun PaddingValues.copy(
    top: Dp = Dp.Unspecified,
    start: Dp = Dp.Unspecified,
    end: Dp = Dp.Unspecified,
    bottom: Dp = Dp.Unspecified
): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding(): Dp = top.takeIf { !it.isUnspecified }
        ?: this@copy.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = bottom.takeIf { !it.isUnspecified }
        ?: this@copy.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = start.takeIf { !it.isUnspecified }
        ?: this@copy.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = end.takeIf { !it.isUnspecified }
        ?: this@copy.calculateRightPadding(layoutDirection)
}
