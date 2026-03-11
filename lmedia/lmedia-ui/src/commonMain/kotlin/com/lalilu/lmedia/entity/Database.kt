package com.lalilu.lmedia.entity

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor


@Database(
    version = 1,
    entities = [Song::class],
    exportSchema = true,
)
@ConstructedBy(SimpleDatabaseTempConstructor::class)
abstract class SimpleDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}

expect object SimpleDatabaseTempConstructor : RoomDatabaseConstructor<SimpleDatabase> {
    override fun initialize(): SimpleDatabase
}