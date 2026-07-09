package com.lalilu.lmedia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lalilu.lmedia.entity.Metadata
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

    actual fun version(): String = instance?.version()?.toString() ?: ""
    actual suspend fun readMetadata(fd: Int): Metadata? = null
    actual suspend fun readMetadata(path: String): Metadata? = null
    actual suspend fun getLyric(fd: Int): String? = null
    actual suspend fun getLyric(path: String): String? = null
    actual suspend fun getPicture(fd: Int): ByteArray? = null
    actual suspend fun getPicture(path: String): ByteArray? = null
}

