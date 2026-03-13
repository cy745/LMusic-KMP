package com.lalilu.lmedia.data.database

import androidx.room3.RoomDatabase

expect inline fun <reified T : RoomDatabase> requireDatabase(
    name: String = T::class.qualifiedName!!,
    forceMemory: Boolean = true
): T