package com.lalilu.lplayer.menu

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.rococoa.Foundation
import org.rococoa.cocoa.NSApplication
import kotlin.coroutines.CoroutineContext

class MacOSMenu(private val playback: Playback) : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io

    /**
     * MACOS的根menu是不显示的，只是一个隐藏的容器
     */
    private val rootMenu by lazy { NSMenu.alloc().init() }
    private val rootMenuItem by lazy {
        NSMenuItem.alloc().init().also { rootMenu.addItem(it) }
    }

    /**
     * MACOS的第一个菜单的第一个菜单项固定是关于菜单项，只能自定义标题和快捷键
     */
    private val aboutMenuItem by lazy {
        NSMenuItem.alloc().initWithTitle("关于APP", null, "")
    }

    /**
     * 实际显示的第一个菜单
     */
    private val firstMenu by lazy {
        NSMenu.alloc().initWithTitle("App")
            .also { rootMenuItem.setSubmenu(it) }
            .also { it.addItem(aboutMenuItem) }
    }

    init {
        val menuItems = listOf(
            MenuItem.PlayPause,
            MenuItem.Next,
            MenuItem.Previous,
            MenuItem.Like,
            MenuItem.RandomPlay,
        ).map { it.toNSMenuItem(::onClickMenuItem) }

        menuItems.forEach { firstMenu.addItem(it) }

        Foundation.runOnMainThread {
            NSApplication.sharedApplication()
                .setMainMenu(rootMenu)
            Logger.i("菜单初始化完成")
        }
    }

    private fun onClickMenuItem(menuItem: MenuItem) {
        when (menuItem) {
            MenuItem.Like -> {}
            MenuItem.Next -> playback.skipToNext()
            MenuItem.PlayPause -> playback.togglePlayPause()
            MenuItem.Previous -> playback.skipTpPrevious()
            MenuItem.RandomPlay -> {}
        }
    }
}

fun MenuItem.toNSMenuItem(onClick: (MenuItem) -> Unit): NSMenuItem {
    val callback = FoundationCallbackRegistry
        .registerCallback { onClick(this) }

    return NSMenuItem.alloc()
        .initWithTitle(title, callback.selector, keyEquivalent)
        .also { it.setTarget(callback.target.id()) }
}