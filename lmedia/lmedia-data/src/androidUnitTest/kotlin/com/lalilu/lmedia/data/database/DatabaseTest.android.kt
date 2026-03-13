package com.lalilu.lmedia.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

actual inline fun <reified T : RoomDatabase> requireDatabase(
    name: String,
    forceMemory: Boolean
): T {
    val context = ApplicationProvider.getApplicationContext<Context>()

    if (forceMemory) {
        return Room.inMemoryDatabaseBuilder(
            context = context,
            klass = T::class.java
        ).setQueryCoroutineContext(Dispatchers.IO)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    return Room.databaseBuilder<T>(
        context = context,
        name = "db/$name.db"
    ).setQueryCoroutineContext(Dispatchers.IO)
        .setDriver(BundledSQLiteDriver())
        .build()
}