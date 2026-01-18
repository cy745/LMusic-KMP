package com.lalilu.navigation

object Metadata {
    const val KEY_IS_HOME = "key_is_home"
    const val KEY_IS_PLAYER = "key_is_player"

    fun home(): Map<String, Any> = mapOf(KEY_IS_HOME to true)
    fun player(): Map<String, Any> = mapOf(KEY_IS_PLAYER to true)
}