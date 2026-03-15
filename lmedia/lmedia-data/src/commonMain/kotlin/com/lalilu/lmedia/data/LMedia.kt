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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@Single(createdAtStart = true)
class LMedia(
    private val platformSource: PlatformMediaSource
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    private val db by lazy { requireDatabase<LMediaDatabase>(forceMemory = false) }

    init {
        startSourceBinding()
    }

    fun startSourceBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest { db.mediaDao().insert(it) }
                .launchIn(this)
        }
    }
}