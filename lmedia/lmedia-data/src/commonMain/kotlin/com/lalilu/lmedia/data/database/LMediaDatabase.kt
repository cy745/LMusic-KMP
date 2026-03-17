package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.data.database.converter.MetadataConverter
import com.lalilu.lmedia.data.database.converter.StringMapConverter
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.*
import org.koin.core.annotation.Single


@Database(
    version = 1,
    entities = [
        LAudio::class,
        LArtist::class,
        LAlbum::class,
        LGenre::class,
        LFolder::class,
        LHistory::class,
        CrossRefLAudioXLArtist::class,
        CrossRefLAudioXAlbum::class,
        CrossRefLAudioXGenre::class
    ],
    exportSchema = true,
)
@TypeConverters(
    StringListConverter::class,
    MetadataConverter::class,
    StringMapConverter::class
)
@ConstructedBy(LMediaDatabaseConstructor::class)
abstract class LMediaDatabase : RoomDatabase() {
    abstract fun audioDao(): LAudioDao
    abstract fun artistDao(): LArtistDao
    abstract fun albumDao(): LAlbumDao
    abstract fun genreDao(): LGenreDao
    abstract fun folderDao(): LFolderDao
    abstract fun mediaDao(): LMediaDao
    abstract fun historyDao(): LHistoryDao
}

@Single
fun provideDatabase(): LMediaDatabase {
    return requireDatabase<LMediaDatabase>(forceMemory = false)
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