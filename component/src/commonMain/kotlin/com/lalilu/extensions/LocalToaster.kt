package com.lalilu.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState

val LocalToaster = staticCompositionLocalOf<ToasterState?> { null }

var GlobalToaster: ToasterState? = null
    private set

@Composable
fun ProvideLocalToaster(
    toasterState: ToasterState = rememberToasterState(),
    content: @Composable () -> Unit
) {
    val parentToaster = LocalToaster.current

    // 如果父级没有提供，则设置全局
    if (parentToaster == null) {
        GlobalToaster = toasterState
    }

    CompositionLocalProvider(LocalToaster provides toasterState) {
        content()
    }

    Toaster(
        state = toasterState,
        alignment = Alignment.BottomCenter
    )
}