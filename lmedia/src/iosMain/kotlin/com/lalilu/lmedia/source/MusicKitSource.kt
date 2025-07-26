package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.MusicKitWrapper
import com.lalilu.lmedia.SongInfo
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalForeignApi::class)
object MusicKitSource : MediaSource {
    override val name: String = "MusicKitSource"

    override fun source(): Flow<Snapshot> {
        val songsFlow = callbackFlow {
            MusicKitWrapper.fetchUserLibrarySongsWithCompletionHandler { result ->
                launch {
                    send(result?.filterIsInstance<SongInfo>() ?: emptyList())
                }
            }
            awaitClose {}
        }

        return songsFlow.map { songs ->
            Snapshot(
                audios = songs.map {
                    LAudio(
                        title = it.title(),
                        subtitle = it.artist()
                    )
                }
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val source by remember { source() }
            .collectAsState(Snapshot.Empty)

        Card(modifier = modifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    source.audios.forEach {
                        Text(text = "${it.title} - ${it.subtitle}")
                    }
                }

                Text(text = "$name ${MusicKitWrapper.helloWorld()}")
            }
        }
    }
}