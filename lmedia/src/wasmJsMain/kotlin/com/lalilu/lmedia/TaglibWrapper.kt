@file:JsModule("taglib-wasm")

package com.lalilu.lmedia

import kotlin.js.Promise

external class TagLib : JsAny {

    companion object {
        fun initialize(): Promise<TagLib>
    }

    fun version(): JsString
}
