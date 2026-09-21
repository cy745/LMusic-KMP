package com.lalilu.lmusic.repository

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PersistentMediaSourceEnablement
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.data.database.LMediaDao
import com.lalilu.lmedia.data.repository.AudioRepositoryImpl
import com.lalilu.lmedia.data.repository.MediaSourceBindingRepositoryImpl
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaContentState
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourcePatchSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 单曲增量入库的**真实数据库**验收。
 *
 * 分析文档承诺"逐条插入而不重建曲库"，这里用真实 Room 内存库把这件事证出来：补丁只改这一首，
 * 其它歌曲的行与关系表行逐项不变、也不会被标记为不可用。对比组是整源对账（[LMediaDao.insert]），
 * 它的语义恰恰是"本次没出现的歌曲即不可用"。
 */
class WebDavIncrementalCommitTest {

    private val sourceName = "patch-test-source"

    private class PatchableSource : MediaSource, MediaSourcePatchSource {
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
        override val contentState = MutableStateFlow(
            MediaContentState(MediaContentAvailability.Ready)
        )
        private val patches = MutableSharedFlow<LAudio>(extraBufferCapacity = 32)
        override val audioPatches: Flow<LAudio> = patches.asSharedFlow()

        override val name: String = "patch-test-source"
        suspend fun emit(audio: LAudio) = patches.emit(audio)
        override suspend fun deactivate() {}
    }

    private fun song(id: String) = LAudio(
        id = id,
        title = "title-$id",
        mediaSourceName = sourceName,
        extra = mapOf(
            LAudioExtraKeys.ArtistName to "Artist-$id",
            LAudioExtraKeys.AlbumName to "Album-$id",
        ),
    )

    private suspend fun awaitCommitted(
        repository: MediaSourceBindingRepositoryImpl,
        revision: Long,
    ) {
        withTimeout(15_000) {
            repository.observeSource(sourceName).first {
                (it?.commitState as? SnapshotCommitState.Committed)?.revision == revision
            }
        }
    }

    private suspend fun awaitTitle(audios: AudioRepositoryImpl, id: String, title: String) {
        withTimeout(10_000) {
            while (audios.getAudio(id).first()?.title != title) delay(20)
        }
    }

    @Test
    fun patchUpdatesOnlyThatSongAndKeepsTheRestOfTheLibraryUntouched() = runBlocking {
        KVContext.kvMap.clear()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val db = requireDatabase<LMusicDatabase>(forceMemory = true)
        val source = PatchableSource()
        val kv = LMediaKV(InMemoryKVSaver())
        val platform = PlatformMediaSource(listOf(source), PersistentMediaSourceEnablement(kv))
        val repository = MediaSourceBindingRepositoryImpl(platform, db, kv, scope)
        val audios = AudioRepositoryImpl(db, platform)

        repository.startBinding()
        source.snapshot.value = Snapshot(listOf(song("a"), song("b")), revision = 1)
        awaitCommitted(repository, 1)

        val beforeB = db.audioDao().getAudioWithRelations(song("b").id).first()!!

        // 只补全 a：换标题、换歌手与专辑
        source.emit(
            song("a").copy(
                title = "enriched",
                extra = mapOf(
                    LAudioExtraKeys.ArtistName to "NewArtist",
                    LAudioExtraKeys.AlbumName to "NewAlbum",
                ),
            )
        )
        awaitTitle(audios, song("a").id, "enriched")

        val patched = audios.getAudio(song("a").id).first()!!
        assertEquals("NewArtist", patched.artistName)
        assertEquals("NewAlbum", patched.albumName)
        val patchedRelations = db.audioDao().getAudioWithRelations(song("a").id).first()!!
        assertEquals(listOf("NewAlbum"), patchedRelations.albums.map { it.title })

        // 另一首歌：行与关系都不该被动过
        val afterB = db.audioDao().getAudioWithRelations(song("b").id).first()!!
        assertEquals(beforeB.audio.title, afterB.audio.title)
        assertEquals(beforeB.audio.extra, afterB.audio.extra)
        assertEquals(beforeB.artists.map { it.id }, afterB.artists.map { it.id })
        assertEquals(beforeB.albums.map { it.id }, afterB.albums.map { it.id })
        assertTrue(afterB.audio.available, "补丁不得把没有出现在补丁里的歌曲标记为不可用")

        // 补丁可重复执行（幂等）
        source.emit(
            song("a").copy(
                title = "enriched",
                extra = mapOf(
                    LAudioExtraKeys.ArtistName to "NewArtist",
                    LAudioExtraKeys.AlbumName to "NewAlbum",
                ),
            )
        )
        delay(200)
        val afterDuplicate = db.audioDao().getAudioWithRelations(song("a").id).first()!!
        assertEquals(listOf("NewAlbum"), afterDuplicate.albums.map { it.title })
        assertEquals(2, db.mediaDao().getAudioBySource(sourceName).size, "补丁不应新增或删除歌曲行")

        // 对比组：整源对账的语义是"本次没出现的歌曲即不可用"
        db.mediaDao().insert(Snapshot(listOf(song("a")), revision = 2), sourceName)
        assertFalse(
            audios.getAudio(song("b").id).first()!!.available,
            "整源对账会把缺项标记为不可用——这正是补丁通道要避开的代价",
        )
    }

    @Test
    fun patchFailureIsVisibleInSourceStatusWithoutTouchingCommitState() = runBlocking {
        KVContext.kvMap.clear()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val db = requireDatabase<LMusicDatabase>(forceMemory = true)
        val realDao = db.mediaDao()
        val failingDao = object : LMediaDao by realDao {
            override suspend fun upsertAudio(audio: LAudio) {
                error("Injected patch failure")
            }
        }
        val wrapped = object : ILMediaDatabase by db {
            override fun mediaDao(): LMediaDao = failingDao
        }
        val source = PatchableSource()
        val kv = LMediaKV(InMemoryKVSaver())
        val platform = PlatformMediaSource(listOf(source), PersistentMediaSourceEnablement(kv))
        val repository = MediaSourceBindingRepositoryImpl(platform, wrapped, kv, scope)

        repository.startBinding()
        source.snapshot.value = Snapshot(listOf(song("a")), revision = 1)
        awaitCommitted(repository, 1)

        source.emit(song("a").copy(title = "enriched"))

        val status = withTimeout(10_000) {
            repository.observeSource(sourceName).first { (it?.patchFailures ?: 0) > 0 }
        }!!
        assertEquals(1, status.patchFailures)
        assertEquals("Injected patch failure", status.lastPatchError)
        assertEquals(
            SnapshotCommitState.Committed(1),
            status.commitState,
            "补丁失败不能污染完整快照的提交状态",
        )
    }
}
