package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


class LMediaLAudioDaoTest {
    val dao = requireDatabase<LMediaDatabase>()
        .audioDao()

    @Test
    fun `test insert and retrieve audio jvm`() = runTest {
        val testAudio = LAudio(
            id = "1L",
            title = "Test Song",
            mediaSourceName = ""
        )

        // 测试插入
        dao.insert(testAudio)

        // 测试查询所有
        val allAudios = dao.getAll()
        require(allAudios.isNotEmpty()) { "音频列表不应为空" }
        require(allAudios.any { it.id == testAudio.id }) { "应包含刚插入的音频" }

        // 测试更新
        val updatedAudio = testAudio.copy(title = "Updated Song")
        dao.update(updatedAudio)
        val retrievedAudio = dao.getAll().firstOrNull { it.id == testAudio.id }
        require(retrievedAudio?.title == "Updated Song") { "更新后的标题应匹配" }

        // 测试删除
        dao.delete(testAudio)
        val afterDeleteAudios = dao.getAll()
        require(afterDeleteAudios.none { it.id == testAudio.id }) { "删除后不应再包含该音频" }
    }
}