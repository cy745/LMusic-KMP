package com.lalilu.lmedia.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

actual inline fun <reified T : RoomDatabase> requireDatabase(
    name: String,
    forceMemory: Boolean
): T {
    if (forceMemory) {
        return Room.inMemoryDatabaseBuilder<T>()
            .setQueryCoroutineContext(Dispatchers.IO)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    return Room.databaseBuilder<T>(name = "db/$name.db")
        .setQueryCoroutineContext(Dispatchers.IO)
        .setDriver(BundledSQLiteDriver())
        .build()
}