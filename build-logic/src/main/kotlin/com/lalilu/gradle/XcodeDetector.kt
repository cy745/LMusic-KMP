package com.lalilu.gradle

@Deprecated("待移除")
object XcodeDetector {

    /**
     * 检测是否安装了 Xcode
     */
    fun isXcodeInstalled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("xcodebuild", "-version"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun whenXcodeInstalled(block: () -> Unit) {
        if (isXcodeInstalled()) {
            block()
        }
    }
}