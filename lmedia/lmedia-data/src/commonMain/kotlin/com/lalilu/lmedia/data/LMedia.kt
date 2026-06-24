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
import com.lalilu.lmedia.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

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
        // 把 startSourceBinding 放到 IO 线程上执行：第一次 source.source() 同步 collect
        // 会触发 MediaStore 全量扫描和 SAF 目录树读取，不应阻塞 KoinStartup 主线程。
        launch { startSourceBinding() }
    }

    companion object {
        lateinit var instance: LMedia
            private set
    }

    suspend fun startSourceBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest {
                database.mediaDao()
                    .insert(snapshot = it, sourceName = source.name)
            }.launchIn(this)
        }
    }

    override fun platformMediaSource(): PlatformMediaSource = platformSource

    override fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>): Flow<Map<String, T>>? {
        return when (clazz) {
            LAudio::class -> database.audioDao().getAllAudio()
                .mapLatest { list -> list.associateBy { it.idValue() } }

            LArtist::class -> database.artistDao().getAllArtist()
                .mapLatest { list -> list.associateBy { it.idValue() } }

            LAlbum::class -> database.albumDao().getAllAlbum()
                .mapLatest { list -> list.associateBy { it.idValue() } }

            LGenre::class -> database.genreDao().getAllGenre()
                .mapLatest { list -> list.associateBy { it.idValue() } }

            LFolder::class -> database.folderDao().getAllFolder()
                .mapLatest { list -> list.associateBy { it.idValue() } }

            else -> null
        } as Flow<Map<String, T>>?
    }

    override fun <T : LItem> getSourcesFlowByClass(clazz: KClass<T>, ids: List<String>): Flow<List<T>>? {
        return when (clazz) {
            LAudio::class -> database.audioDao().getAudios(ids)
            LArtist::class -> database.artistDao().getArtists(ids)
            LAlbum::class -> database.albumDao().getAlbums(ids)
            else -> null
        } as Flow<List<T>>?
    }

    override fun <T : LItem> getSourceFlowByClass(clazz: KClass<T>, id: String): Flow<T?>? {
        return when (clazz) {
            LAudio::class -> database.audioDao().getAudio(id)
            LArtist::class -> database.artistDao().getArtist(id)
            LAlbum::class -> database.albumDao().getAlbum(id)
            LGenre::class -> database.genreDao().getGenre(id)
            LFolder::class -> database.folderDao().getFolder(id)
            else -> null
        } as Flow<T?>?
    }
}