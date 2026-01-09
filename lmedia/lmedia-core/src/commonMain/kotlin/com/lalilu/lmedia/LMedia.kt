package com.lalilu.lmedia

import com.lalilu.lmedia.entity.*
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
        ).onEach { if (it.state.priority() < SnapshotState.Loading::class.priority()) onReady() }
            .distinctUntilChangedBy { it.updateTime }
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