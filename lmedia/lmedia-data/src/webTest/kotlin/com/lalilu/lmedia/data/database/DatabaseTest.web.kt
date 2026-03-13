package com.lalilu.lmedia.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.lalilu.lmedia.data.createWebWorkerSQLiteDriver
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