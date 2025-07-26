package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import platform.MediaPlayer.MPMediaItem
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaQuery

object MediaLibrarySource : MediaSource {
    override val name: String = "MediaLibrarySource"
    private val authorized by lazy {
        MutableStateFlow(MPMediaLibrary.authorizationStatus() == MPMediaLibraryAuthorizationStatusAuthorized)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun source(): Flow<Snapshot> {
        return authorized.mapLatest { result ->
            if (!result) return@mapLatest Snapshot.Empty

            val items = MPMediaQuery.songsQuery().items()
                ?.filterIsInstance<MPMediaItem>()
                ?.filter { it.assetURL != null }
                ?: return@mapLatest Snapshot.Empty

            val songs = items.map {
                LAudio(
                    title = it.title ?: "unknown",
                    subtitle = it.assetURL?.toString() ?: "unknownArtist",
                )
            }

            return@mapLatest Snapshot(audios = songs)
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val authorizedState = authorized.collectAsState()
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

                Text(text = name)

                Button(onClick = {
                    MPMediaLibrary.requestAuthorization { result ->
                        if (result == MPMediaLibraryAuthorizationStatusAuthorized) {
                            authorized.value = true
                            println("MPMediaLibrary authorized")
                        } else {
                            authorized.value = false
                            println("MPMediaLibrary unauthorized")
                        }
                    }
                }) {
                    Text(text = if (authorizedState.value) "Authorized" else "Do authorize")
                }
            }
        }
    }
}