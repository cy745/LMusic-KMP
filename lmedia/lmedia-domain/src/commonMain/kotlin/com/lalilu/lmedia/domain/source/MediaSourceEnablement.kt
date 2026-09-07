package com.lalilu.lmedia.domain.source

/**
 * 数据源启用状态的持久化边界。
 *
 * 实现必须以 [MediaSource.name] 为稳定键；未保存过的来源默认启用，以保持升级前的行为。
 */
interface MediaSourceEnablement {
    fun isEnabled(sourceName: String): Boolean
    fun setEnabled(sourceName: String, enabled: Boolean)

    data object AlwaysEnabled : MediaSourceEnablement {
        override fun isEnabled(sourceName: String): Boolean = true
        override fun setEnabled(sourceName: String, enabled: Boolean) = Unit
    }
}
