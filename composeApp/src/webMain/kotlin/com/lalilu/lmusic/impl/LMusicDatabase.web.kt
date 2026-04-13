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

package com.lalilu.lmusic.impl

import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.lalilu.lmusic.util.createWebWorkerSQLiteDriver
import kotlinx.coroutines.Dispatchers

actual inline fun <reified T : RoomDatabase> requireDatabase(
    name: String,
    forceMemory: Boolean
): T {
    val driver = createWebWorkerSQLiteDriver()

    if (forceMemory) {
        return Room.inMemoryDatabaseBuilder<T>()
            .setQueryCoroutineContext(Dispatchers.Default)
            .setDriver(driver)
            .build()
    }

    return Room.databaseBuilder<T>(name = "db/$name.db")
        .setQueryCoroutineContext(Dispatchers.Default)
        .setDriver(driver)
        .build()
}