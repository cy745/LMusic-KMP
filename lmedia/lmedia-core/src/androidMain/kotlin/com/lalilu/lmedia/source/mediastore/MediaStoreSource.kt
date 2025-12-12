package com.lalilu.lmedia.source.mediastore

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.source.MediaSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreSource(
    private val context: Application
) : MediaSource {
    override val name: String = "MediaStore"
    private val scanner = when {
        Build.VERSION.SDK_INT >= 30 -> Api30MediaStoreScanner(context)
        Build.VERSION.SDK_INT >= 29 -> Api29MediaStoreScanner(context)
        Build.VERSION.SDK_INT >= 21 -> Api21MediaStoreScanner(context)
        else -> MediaStoreScanner(context)
    }

    override fun source(): Flow<Snapshot> {
        val eventFlow = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    launch { send(System.currentTimeMillis()) }
                }
            }

            context.applicationContext.contentResolver
                .registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)

            awaitClose {
                context.applicationContext.contentResolver
                    .unregisterContentObserver(observer)
            }
        }

        return callbackFlow {
            // 先返回Snapshot.Loading，避免阻塞下游的combine
            send(Snapshot.Loading)
            eventFlow.collectLatest {
                send(scanner.scan())
            }
            awaitClose {
            }
        }
    }
}
