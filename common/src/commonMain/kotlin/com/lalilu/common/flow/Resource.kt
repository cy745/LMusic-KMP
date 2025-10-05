package com.lalilu.common.flow


/**
 * 资源状态密封接口，用于表示数据加载的各种状态
 * @param T 泛型参数，表示资源的数据类型
 */
sealed interface Resource<T> {
    /**
     * 空闲状态 - 表示尚未开始加载数据
     * 一般用于需要用户手动触发价值数据的场景
     */
    data object Idle : Resource<Nothing>

    /**
     * 加载中状态 - 表示正在加载数据
     * 一般直接用于自动开始加载的情况
     */
    data object Loading : Resource<Nothing>

    /**
     * 成功状态 - 表示数据加载成功
     * @param result 加载成功的数据结果
     */
    data class Success<T>(val result: T) : Resource<T>

    /**
     * 错误状态 - 表示数据加载失败
     * @param throwable 异常信息
     * @param isRetryable 是否可以重试，默认为false
     */
    data class Error(
        val throwable: Throwable,
        val isRetryable: Boolean = false
    ) : Resource<Nothing>

    /**
     * 空状态 - 表示数据为空（与空闲状态不同，这是明确的空数据结果）
     */
    data object Empty : Resource<Nothing>
}