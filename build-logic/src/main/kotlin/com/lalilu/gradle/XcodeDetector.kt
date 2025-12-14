package com.lalilu.gradle

object XcodeDetector {

    /**
     * 检测是否安装了 Xcode
     */
    fun isXcodeInstalled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("xcodebuild -version")
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