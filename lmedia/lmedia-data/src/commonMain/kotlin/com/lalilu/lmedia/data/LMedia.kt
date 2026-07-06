/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lmedia.data

import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.source.Snapshot as DomainSnapshot
import com.lalilu.lmedia.domain.source.SnapshotState as DomainSnapshotState
import com.lalilu.lmedia.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

private fun convertState(s: com.lalilu.lmedia.entity.SnapshotState): DomainSnapshotState = when (s) {
    com.lalilu.lmedia.entity.SnapshotState.Idle -> DomainSnapshotState.Idle
    com.lalilu.lmedia.entity.SnapshotState.Empty -> DomainSnapshotState.Empty
    com.lalilu.lmedia.entity.SnapshotState.Success -> DomainSnapshotState.Success
    is com.lalilu.lmedia.entity.SnapshotState.Loading -> DomainSnapshotState.Loading(s.message, s.progress)
    is com.lalilu.lmedia.entity.SnapshotState.LoadingDynamic -> DomainSnapshotState.Loading(s.message(), s.progress())
    is com.lalilu.lmedia.entity.SnapshotState.Error -> DomainSnapshotState.Error(s.message)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Single(createdAtStart = true, binds = [Library::class, LMedia::class])
class LMedia(
    private val platformSource: PlatformMediaSource,
    private val database: ILMediaDatabase
) : Library(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()

    init {
        instance = this
        launch { startSourceBinding() }
    }

    companion object {
        lateinit var instance: LMedia
            private set
    }

    suspend fun startSourceBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest { oldSnapshot ->
                // Convert old entity Snapshot to domain Snapshot
                val domainSnapshot = DomainSnapshot(
                    audios = oldSnapshot.audios.map {
                        com.lalilu.lmedia.domain.model.LAudio(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            mediaSourceName = it.mediaSourceName,
                            metadata = with(it.metadata) {
                                com.lalilu.lmedia.domain.model.Metadata(
                                    title = title,
                                    album = album,
                                    artist = artist,
                                    albumArtist = albumArtist,
                                    composer = composer,
                                    lyricist = lyricist,
                                    comment = comment,
                                    genre = genre,
                                    track = track,
                                    disc = disc,
                                    date = date,
                                    duration = duration,
                                    dateAdded = dateAdded,
                                    dateModified = dateModified
                                )
                            },
                            extra = it.extra,
                            available = it.available
                        )
                    },
                    albums = oldSnapshot.albums.map {
                        com.lalilu.lmedia.domain.model.LAlbum(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            extra = it.extra
                        )
                    },
                    artists = oldSnapshot.artists.map {
                        com.lalilu.lmedia.domain.model.LArtist(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            extra = it.extra
                        )
                    },
                    genres = oldSnapshot.genres.map {
                        com.lalilu.lmedia.domain.model.LGenre(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            extra = it.extra
                        )
                    },
                    folders = oldSnapshot.folders.map {
                        com.lalilu.lmedia.domain.model.LFolder(
                            id = it.id,
                            title = it.title,
                            subtitle = it.subtitle,
                            extra = it.extra
                        )
                    },
                    state = convertState(oldSnapshot.state),
                    relations = oldSnapshot.relations,
                    updateTime = oldSnapshot.updateTime
                )
                database.mediaDao()
                    .insert(snapshot = domainSnapshot, sourceName = source.name)
            }.launchIn(this)
        }
    }

    override fun platformMediaSource(): PlatformMediaSource = platformSource

    @Suppress("UNCHECKED_CAST")
    override fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>): Flow<Map<String, T>>? {
        val flow: Any? = when (clazz) {
            LAudio::class -> database.audioDao().getAllAudio()
                .mapLatest { list -> list.associateBy { it.id as String } }

            LArtist::class -> database.artistDao().getAllArtist()
                .mapLatest { list -> list.associateBy { it.id as String } }

            LAlbum::class -> database.albumDao().getAllAlbum()
                .mapLatest { list -> list.associateBy { it.id as String } }

            LGenre::class -> database.genreDao().getAllGenre()
                .mapLatest { list -> list.associateBy { it.id as String } }

            LFolder::class -> database.folderDao().getAllFolder()
                .mapLatest { list -> list.associateBy { it.id as String } }

            else -> null
        }
        return flow as? Flow<Map<String, T>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>, ids: List<String>): Flow<List<T>>? {
        val flow: Any? = when (clazz) {
            LAudio::class -> database.audioDao().getAudios(ids)
            LArtist::class -> database.artistDao().getArtists(ids)
            LAlbum::class -> database.albumDao().getAlbums(ids)
            else -> null
        }
        return flow as? Flow<List<T>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>, id: String): Flow<T?>? {
        val flow: Any? = when (clazz) {
            LAudio::class -> database.audioDao().getAudio(id)
            LArtist::class -> database.artistDao().getArtist(id)
            LAlbum::class -> database.albumDao().getAlbum(id)
            LGenre::class -> database.genreDao().getGenre(id)
            LFolder::class -> database.folderDao().getFolder(id)
            else -> null
        }
        return flow as? Flow<T?>
    }
}
