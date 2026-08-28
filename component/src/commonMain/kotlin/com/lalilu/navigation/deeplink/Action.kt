package com.lalilu.navigation.deeplink

/**
 * 可通过 `lmusic://action/<key>` 调用的应用级动作。
 *
 * 实现方使用 Koin 的同名 qualifier 注册；Deep Link 中除 `key` 外的查询参数会以
 * String 键值对原样传入。只有显式注册的实现才能被外部调用。
 */
interface Action {
    suspend fun action(params: Map<String, String>)
}
