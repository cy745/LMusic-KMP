package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Metadata
import com.lalilu.lmedia.entity.buildSnapshot
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


class LMediaLMediaDaoTest {
    val db = requireDatabase<LMediaDatabase>(forceMemory = false)

    @Test
    fun testInsertAndRetrieveArtistWithAudios() = runTest {
        val snapshot = listOf(
            LAudio(
                id = "1",
                title = "Audio 1",
                subtitle = "Subtitle 1",
                metadata = Metadata(artist = "周杰伦/夜的第七章"),
                extra = mapOf("key" to "value")
            ),
            LAudio(
                id = "2",
                title = "Audio 2",
                subtitle = "Subtitle 2",
                metadata = Metadata(artist = "张杰/周杰伦/告白气球"),
                extra = mapOf("key" to "value")
            )
        ).buildSnapshot()

        db.mediaDao().insert(snapshot)

        db.artistDao().getAllArtist().firstOrNull()
            ?.forEach { linkable ->
                println(linkable)
                linkable.ref<LAudio>().forEach {
                    println(it)
                }
            }
    }
}