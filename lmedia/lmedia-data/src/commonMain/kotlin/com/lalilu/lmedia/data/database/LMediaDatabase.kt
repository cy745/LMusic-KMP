package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist


@Database(
    version = 1,
    entities = [LAudio::class, LArtist::class, CrossRefLAudioXLArtist::class],
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
@ConstructedBy(LMediaDatabaseConstructor::class)
abstract class LMediaDatabase : RoomDatabase() {
    abstract fun audioDao(): LAudioDao
    abstract fun artistDao(): LArtistDao
    abstract fun mediaDao(): LMediaDao
}

expect object LMediaDatabaseConstructor : RoomDatabaseConstructor<LMediaDatabase> {
    override fun initialize(): LMediaDatabase
}

expect inline fun <reified T : RoomDatabase> requireDatabase(
    name: String = T::class.qualifiedName!!,
    forceMemory: Boolean = true
): T

class StringListConverter {
    @TypeConverter
    fun fromStringList(list: MutableList<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toStringList(string: String): MutableList<String> {
        return string.split(",").toMutableList()
    }
}