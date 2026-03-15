package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


class LMediaLArtistDaoTest {
    val dao = requireDatabase<LMediaDatabase>(forceMemory = false)
        .artistDao()

    @Test
    fun testInsertAndRetrieveArtistWithAudios() = runTest {
        val artist = LArtist(
            id = "1",
            title = "Artist 1",
            subtitle = "Subtitle 1",
        ).apply {
            link(
                LAudio(
                    id = "1",
                    title = "Audio 1",
                    subtitle = "Subtitle 1",
                    extra = mapOf("key" to "value")
                ).also { it.link(this) }
            )
            link(
                LAudio(
                    id = "2",
                    title = "Audio 2",
                    subtitle = "Subtitle 2",
                    extra = mapOf("key" to "value")
                ).also { it.link(this) }
            )
        }

        dao.insertAll(artist)
        dao.getAllArtist()
            .firstOrNull()
            ?.firstOrNull()
            ?.let { linkable ->
                println(linkable)
                linkable.ref<LAudio>().forEach {
                    println(it)
                }
            }
    }
}