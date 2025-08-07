package com.lalilu.lplayer.menu

/**
 * @param title 菜单项标题
 * @param keyEquivalent 菜单项快捷键
 */
sealed class MenuItem(
    val title: String,
    val keyEquivalent: String = ""
) {
    data object RandomPlay : MenuItem(
        title = "随机播放",
        keyEquivalent = "r"
    )

    data object PlayPause : MenuItem(
        title = "播放/暂停",
        keyEquivalent = "p"
    )

    data object Like : MenuItem(
        title = "喜欢",
        keyEquivalent = "l"
    )

    data object Next : MenuItem(
        title = "下一首",
        keyEquivalent = "n"
    )

    data object Previous : MenuItem(
        title = "上一首",
        keyEquivalent = "b"
    )
}