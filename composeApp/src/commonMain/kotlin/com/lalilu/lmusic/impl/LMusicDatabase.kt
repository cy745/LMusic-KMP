package com.lalilu.lmusic.impl

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.executeSQL
import androidx.sqlite.prepare
import androidx.sqlite.step
import com.lalilu.lhistory.entity.LHistory
import com.lalilu.lhistory.repository.ILHistoryDatabase
import com.lalilu.lhistory.repository.LHistoryDao
import com.lalilu.lmedia.data.database.*
import com.lalilu.lmedia.data.database.converter.StringListConverter
import com.lalilu.lmedia.data.database.converter.StringMapConverter
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.data.entity.*
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.ILPlaylistDatabase
import com.lalilu.lplaylist.repository.LPlaylistDao
import org.koin.core.annotation.Single


@Database(
    version = 2,
    entities = [
        LAudioEntity::class,
        LArtistEntity::class,
        LAlbumEntity::class,
        LGenreEntity::class,
        LFolderEntity::class,
        LHistory::class,
        LPlaylist::class,
        CrossRefLAudioXLArtist::class,
        CrossRefLAudioXAlbum::class,
        CrossRefLAudioXGenre::class
    ],
    exportSchema = true,
)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@TypeConverters(
    StringListConverter::class,
    StringMapConverter::class
)
@ConstructedBy(LMusicDatabaseConstructor::class)
abstract class LMusicDatabase : RoomDatabase(),
    ILMediaDatabase,
    ILHistoryDatabase,
    ILPlaylistDatabase {
    abstract override fun audioDao(): LAudioDao
    abstract override fun artistDao(): LArtistDao
    abstract override fun albumDao(): LAlbumDao
    abstract override fun genreDao(): LGenreDao
    abstract override fun folderDao(): LFolderDao
    abstract override fun mediaDao(): LMediaDao
    abstract override fun historyDao(): LHistoryDao
    abstract override fun playlistDao(): LPlaylistDao
}

@Single(
    binds = [
        LMusicDatabase::class,
        ILMediaDatabase::class,
        ILHistoryDatabase::class,
        ILPlaylistDatabase::class
    ]
)
fun provideDatabase(): LMusicDatabase {
    return requireDatabase<LMusicDatabase>(forceMemory = false) {
        this.addMigrations(MIGRATION_1_2)
            .addCallback(ILPlaylistDatabase.CALLBACK)
    }
}

/** 删除已经由 [LAudioEntity.extra] 完全替代的旧 metadata 列，保留其他媒体及用户数据。 */
private val MIGRATION_1_2 = Migration(1, 2) { connection ->
    if (connection.hasColumn(table = "l_audio", column = "metadata")) {
        connection.executeSQL("ALTER TABLE l_audio DROP COLUMN metadata")
    }
}

private suspend fun SQLiteConnection.hasColumn(table: String, column: String): Boolean {
    val statement = prepare("PRAGMA table_info($table)")
    return try {
        var found = false
        while (!found && statement.step()) {
            found = statement.getText(1) == column
        }
        found
    } finally {
        statement.close()
    }
}

expect object LMusicDatabaseConstructor : RoomDatabaseConstructor<LMusicDatabase> {
    override fun initialize(): LMusicDatabase
}

expect inline fun <reified T : RoomDatabase> requireDatabase(
    name: String = T::class.qualifiedName!!,
    forceMemory: Boolean = true,
    builder: RoomDatabase.Builder<T>.() -> RoomDatabase.Builder<T> = { this }
): T
