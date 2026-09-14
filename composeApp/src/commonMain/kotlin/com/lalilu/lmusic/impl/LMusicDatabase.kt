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
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.ILPlaylistDatabase
import com.lalilu.lplaylist.repository.LPlaylistDao
import org.koin.core.annotation.Single


@Database(
    version = 5,
    entities = [
        LAudioEntity::class,
        PlaybackFailureEntity::class,
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
    abstract override fun playbackFailureDao(): PlaybackFailureDao
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
        this.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(ILPlaylistDatabase.CALLBACK)
    }
}

/** Upgrade stored references without guessing which source an ambiguous raw ID belonged to. */
internal val MIGRATION_4_5 = Migration(4, 5) { connection ->
    val audioQuery = connection.prepare("SELECT raw_id, song_id FROM l_audio")
    val candidates = try {
        buildList {
            while (audioQuery.step()) add(audioQuery.getText(0) to audioQuery.getText(1))
        }.groupBy({ it.first }, { it.second })
    } finally {
        audioQuery.close()
    }
    fun qualify(rawId: String): String = candidates[rawId]?.singleOrNull() ?: "unresolved:$rawId"

    val historyQuery = connection.prepare("SELECT id, contentId FROM m_history")
    val histories = try {
        buildList { while (historyQuery.step()) add(historyQuery.getLong(0) to historyQuery.getText(1)) }
    } finally {
        historyQuery.close()
    }
    for ((id, rawId) in histories) {
        val update = connection.prepare("UPDATE m_history SET contentId = ? WHERE id = ?")
        try {
            update.bindText(1, qualify(rawId))
            update.bindLong(2, id)
            update.step()
        } finally {
            update.close()
        }
    }
    val playlistQuery = connection.prepare("SELECT id, mediaIds FROM l_playlist")
    val playlists = try {
        buildList { while (playlistQuery.step()) add(playlistQuery.getText(0) to playlistQuery.getText(1)) }
    } finally {
        playlistQuery.close()
    }
    val converter = StringListConverter()
    for ((id, encodedIds) in playlists) {
        val qualified = converter.toStringList(encodedIds).map(::qualify)
        val update = connection.prepare("UPDATE l_playlist SET mediaIds = ? WHERE id = ?")
        try {
            update.bindText(1, converter.fromStringList(qualified))
            update.bindText(2, id)
            update.step()
        } finally {
            update.close()
        }
    }
}

/** Preserve current development installations while moving database keys to source-qualified IDs. */
internal val MIGRATION_3_4 = Migration(3, 4) { connection ->
    connection.executeSQL("CREATE TABLE l_audio_v4 (raw_id TEXT NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL, media_source_name TEXT NOT NULL, extra TEXT, available INTEGER NOT NULL, song_id TEXT NOT NULL PRIMARY KEY)")
    val rows = connection.prepare("SELECT song_id, media_source_name FROM l_audio")
    val identities = try {
        buildList {
            while (rows.step()) add(MediaKey(rows.getText(1), rows.getText(0)))
        }
    } finally {
        rows.close()
    }
    // Calculate the prefix in Kotlin too: SQL length() differs for supplementary Unicode chars.
    for (key in identities) {
        val insert = connection.prepare("INSERT INTO l_audio_v4 SELECT song_id, title, subtitle, media_source_name, extra, available, ? FROM l_audio WHERE song_id = ?")
        try {
            insert.bindText(1, key.stableKey)
            insert.bindText(2, key.id)
            insert.step()
        } finally {
            insert.close()
        }
    }
    for ((table, owner) in listOf(
        "cross_ref_audio_x_artist" to "artist_id",
        "cross_ref_audio_x_album" to "album_id",
        "cross_ref_audio_x_genre" to "genre_id",
    )) {
        // Rebuild mappings in one set, avoiding temporary key collisions during in-place UPDATE.
        connection.executeSQL("CREATE TEMP TABLE audio_relation_migration AS SELECT r.$owner AS owner, a.song_id FROM $table r JOIN l_audio_v4 a ON r.song_id = a.raw_id")
        connection.executeSQL("DELETE FROM $table")
        connection.executeSQL("INSERT INTO $table ($owner, song_id) SELECT owner, song_id FROM audio_relation_migration")
        connection.executeSQL("DROP TABLE audio_relation_migration")
    }
    connection.executeSQL("DROP TABLE l_audio")
    connection.executeSQL("ALTER TABLE l_audio_v4 RENAME TO l_audio")
}

private val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.executeSQL("CREATE TABLE IF NOT EXISTS playback_failure (playbackId TEXT NOT NULL PRIMARY KEY, reason TEXT NOT NULL, occurredAtMillis INTEGER NOT NULL)")
}

/** Remove the metadata column already superseded by extra, retaining media and user data. */
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
