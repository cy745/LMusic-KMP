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
import com.lalilu.lmedia.data.database.LMediaDatabase
import com.lalilu.lmedia.data.database.requireDatabase
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LFolder
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Single(createdAtStart = true, binds = [Library::class, LMedia::class])
class LMedia(
    private val platformSource: PlatformMediaSource
) : Library(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    private val db by lazy { requireDatabase<LMediaDatabase>(forceMemory = false) }

    init {
        instance = this
        startSourceBinding()
    }

    companion object {
        lateinit var instance: LMedia
            private set
    }

    fun startSourceBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest { db.mediaDao().insert(it) }
                .launchIn(this)
        }
    }

    override fun platformMediaSource(): PlatformMediaSource = platformSource

    override fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>): Flow<Map<String, T>>? {
        return when (clazz) {
            LAudio::class -> db.audioDao().getAllAudio().mapLatest { list -> list.associateBy { it.id } }
            LArtist::class -> db.artistDao().getAllArtist().mapLatest { list -> list.associateBy { it.id } }
            LAlbum::class -> db.albumDao().getAllAlbum().mapLatest { list -> list.associateBy { it.id } }
            LGenre::class -> db.genreDao().getAllGenre().mapLatest { list -> list.associateBy { it.id } }
            LFolder::class -> db.folderDao().getAllFolder().mapLatest { list -> list.associateBy { it.id } }
            else -> null
        } as Flow<Map<String, T>>?
    }

    override fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>, id: String): Flow<T?>? {
        return when (clazz) {
            LAudio::class -> db.audioDao().getAudio(id)
            LArtist::class -> db.artistDao().getArtist(id)
            LAlbum::class -> db.albumDao().getAlbum(id)
            LGenre::class -> db.genreDao().getGenre(id)
            LFolder::class -> db.folderDao().getFolder(id)
            else -> null
        } as Flow<T?>?
    }
}