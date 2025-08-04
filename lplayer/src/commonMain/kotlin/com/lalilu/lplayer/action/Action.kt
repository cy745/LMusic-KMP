package com.lalilu.lplayer.action

interface Action {
    /**
     * Action的名称
     */
    fun name(): String = "Not implemented"

    /**
     * Action的描述
     */
    fun desc(): String = "Not implemented"

    /**
     * Action的实现
     */
    fun action()
}