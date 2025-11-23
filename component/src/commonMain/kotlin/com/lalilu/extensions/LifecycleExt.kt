package com.lalilu.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun OnAppBackground(callback: () -> Unit = {}) {
    // 创建一个LifecycleEventObserver监听应用进入后台事件
    val observer = remember {
        LifecycleEventObserver { source, event ->
            // 当生命周期事件为ON_PAUSE时，表示应用进入后台
            if (event == Lifecycle.Event.ON_PAUSE) {
                callback()
            }
        }
    }
    // 将观察者绑定到当前组件的生命周期
    observer.bindToLifecycle()
}

@Composable
fun OnAppForeground(callback: () -> Unit = {}) {
    // 创建一个LifecycleEventObserver监听应用进入前台事件
    val observer = remember {
        LifecycleEventObserver { source, event ->
            // 当生命周期事件为ON_START时，表示应用进入前台
            if (event == Lifecycle.Event.ON_START) {
                callback()
            }
        }
    }
    // 将观察者绑定到当前组件的生命周期
    observer.bindToLifecycle()
}

@Composable
fun LifecycleObserver.bindToLifecycle() {
    // 获取当前组件的LifecycleOwner
    val lifecycleOwner = LocalLifecycleOwner.current

    // 使用DisposableEffect确保在组件销毁时正确移除观察者
    DisposableEffect(Unit) {
        // 添加观察者到Lifecycle
        lifecycleOwner.lifecycle.addObserver(this@bindToLifecycle)
        onDispose {
            // 在组件销毁时移除观察者
            lifecycleOwner.lifecycle.removeObserver(this@bindToLifecycle)
        }
    }
}