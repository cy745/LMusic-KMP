package com.lalilu.lmedia

import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LFolder
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SnapshotState
import com.lalilu.lmedia.entity.combineToOne
import com.lalilu.lmedia.entity.priority
import com.lalilu.lmedia.source.Library
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Single(binds = [Library::class], createdAtStart = true)
class LMedia(platformSource: PlatformMediaSource) : Library() {

    override val snapshotStateFlow: StateFlow<Snapshot> =
        combine(
            flows = platformSource.sources.map { it.source() },
            transform = { it.combineToOne() }
        ).distinctUntilChangedBy { it.updateTime }
            .onEach { if (it.state.priority() > SnapshotState.Loading::class.priority()) onReady() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, Snapshot.Loading)

    private val _songsFlow = singleStateFlow(Snapshot::audios)
    private val _albumsFlow = singleStateFlow(Snapshot::albums)
    private val _artistsFlow = singleStateFlow(Snapshot::artists)
    private val _genresFlow = singleStateFlow(Snapshot::genres)
    private val _foldersFlow = singleStateFlow(Snapshot::folders)

    override fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>): StateFlow<Map<String, T>>? {
        return when (clazz) {
            LAudio::class -> _songsFlow
            LArtist::class -> _artistsFlow
            LAlbum::class -> _albumsFlow
            LGenre::class -> _genresFlow
            LFolder::class -> _foldersFlow
            else -> null
        } as StateFlow<Map<String, T>>?
    }

    init {
        instance = this
    }

    companion object {
        lateinit var instance: LMedia
            private set
    }
}