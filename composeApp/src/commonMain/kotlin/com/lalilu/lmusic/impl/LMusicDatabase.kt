package com.lalilu.lmusic.impl

import androidx.room3.*
import com.lalilu.lmedia.data.database.*
import com.lalilu.lmedia.data.database.converter.MetadataConverter
import com.lalilu.lmedia.data.database.converter.StringListConverter
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
@ConstructedBy(LMusicDatabaseConstructor::class)
abstract class LMusicDatabase : RoomDatabase(), ILMediaDatabase {
    abstract override fun audioDao(): LAudioDao
    abstract override fun artistDao(): LArtistDao
    abstract override fun albumDao(): LAlbumDao
    abstract override fun genreDao(): LGenreDao
    abstract override fun folderDao(): LFolderDao
    abstract override fun mediaDao(): LMediaDao
    abstract override fun historyDao(): LHistoryDao
}

@Single(binds = [LMusicDatabase::class, ILMediaDatabase::class])
fun provideDatabase(): LMusicDatabase {
    return requireDatabase<LMusicDatabase>(forceMemory = false)
}

expect object LMusicDatabaseConstructor : RoomDatabaseConstructor<LMusicDatabase> {
    override fun initialize(): LMusicDatabase
}

expect inline fun <reified T : RoomDatabase> requireDatabase(
    name: String = T::class.qualifiedName!!,
    forceMemory: Boolean = true
): T