package com.lalilu.lmedia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch


@OptIn(DelicateCoroutinesApi::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {
    private var instance by mutableStateOf<TagLib?>(null)

    init {
        GlobalScope.launch {
            instance = TagLib.initialize().await<TagLib>()
        }
    }

    actual fun test(): String {
        return "WASM, version: ${instance?.version()}"
    }
}

