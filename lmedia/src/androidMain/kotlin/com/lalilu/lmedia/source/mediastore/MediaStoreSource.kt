package com.lalilu.lmedia.source.mediastore

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.source.MediaSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single
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

            invokeOnClose {
                context.applicationContext.contentResolver
                    .unregisterContentObserver(observer)
            }
        }

        return eventFlow.mapLatest { updateTime ->
            scanner.scan()
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = name)
            }
        }
    }
}
