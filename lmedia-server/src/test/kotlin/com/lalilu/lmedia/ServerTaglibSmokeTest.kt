package com.lalilu.lmedia

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 锁住「lmedia-server 与 lmedia-core 使用同一份 TagLib」这一约定。
 *
 * lmedia-server 不携带自己的 `natives/`：那份曾是与 core 不一致的陈旧副本（windows_64/tag.dll
 * 还是更新前的旧构建），而 shadowJar 用的是 `DuplicatesStrategy.INCLUDE`，两份同名 dll 都会进包，
 * 运行时谁被加载不确定——一旦加载到旧 dll 就会与 core 的 RegisterNatives 版 JNI 声明不匹配而崩溃。
 *
 * 现在它通过 `implementation(project(":lmedia:lmedia-core"))` 复用同一套原生库与同一个
 * [TaglibWrapper]。若有人再把 `natives/` 复制回本模块，此用例会直接失败（或令 JVM 崩溃）。
 */
class ServerTaglibSmokeTest {

    @Test
    fun serverSharesCoreTaglib() {
        val version = Taglib.version()
        assertTrue(
            version.isNotBlank(),
            "server 应能从 lmedia-core 的 natives 加载 TagLib，实际版本为 '$version'"
        )
    }
}
