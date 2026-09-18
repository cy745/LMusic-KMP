package com.lalilu.test

import org.junit.Assume.assumeTrue
import java.util.Locale

/**
 * JVM 桌面端(desktop)支持的操作系统。
 *
 * 桌面端是唯一需要按宿主系统区分用例的目标：同一份 jvmTest 会在 Windows / macOS / Linux 上执行，
 * 而 Rococoa（Darwin 桥接）、MacOsVlcDiscoverer 这类被测代码只在某一个系统上才有意义。
 */
enum class DesktopOs {
    WINDOWS,
    MACOS,
    LINUX;

    companion object {
        /** 当前宿主系统；无法识别时返回 null。 */
        val current: DesktopOs? by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
            when {
                name.contains("win") -> WINDOWS
                name.contains("mac") || name.contains("darwin") -> MACOS
                name.contains("nux") || name.contains("nix") -> LINUX
                else -> null
            }
        }
    }
}

/**
 * 平台专属用例的守卫：当前宿主不在 [supported] 中时**跳过**该用例（而不是失败）。
 *
 * 桌面端测试会在多个系统上跑，而有些被测代码本身就是平台专属的，例如：
 * - [RococoaTest]：依赖只在 macOS 上存在的 `libwrapper.dylib` 与 Darwin 运行时；
 * - `MacOsVlcDiscovererTest`：被测的 `MacOsVlcDiscoverer` 只在 macOS 上使用，且拼接的是 `/` 分隔路径。
 *
 * 用法（放在用例体第一行，需在触碰任何平台专属资源之前调用）：
 * ```kotlin
 * @Test
 * fun onlyMeaningfulOnMac() {
 *     assumeDesktopOs(DesktopOs.MACOS)
 *     ...
 * }
 * ```
 * 反向同理：只在 Windows 上成立的用例写 `assumeDesktopOs(DesktopOs.WINDOWS)`。
 */
fun assumeDesktopOs(vararg supported: DesktopOs) {
    val current = DesktopOs.current
    assumeTrue(
        "当前宿主为 ${current ?: System.getProperty("os.name")}，本用例只支持 ${supported.joinToString(" / ")}",
        current != null && supported.contains(current)
    )
}
