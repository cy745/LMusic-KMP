package com.lalilu.lmusic

import com.russhwolf.settings.PreferencesSettings
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

class LargeValueSettingsTest {
    @Test fun largeQueueSurvivesReopenAndSmallerRewrite() {
        val node = Preferences.userRoot().node("lmusic-tests/" + UUID.randomUUID())
        val directory = Files.createTempDirectory("large-settings")
        try {
            val native = PreferencesSettings(node)
            val queue = "{\"playbackIds\":[" + (0 until 10000).joinToString(",") { "\"source:track-$it\"" } + "],\"index\":123}"
            assertFailsWith<IllegalArgumentException> { native.putString("queue", queue) }
            native.putString("queue", "legacy")
            var settings = LargeValueSettings(native, directory)
            assertEquals("legacy", settings.getStringOrNull("queue"))
            settings.putString("queue", queue)
            settings = LargeValueSettings(PreferencesSettings(node), directory)
            assertEquals(queue, settings.getStringOrNull("queue"))
            assertTrue(settings.hasKey("queue"))
            assertEquals(setOf("queue"), settings.keys)
            settings.putString("queue", "short")
            assertEquals("short", LargeValueSettings(native, directory).getStringOrNull("queue"))
            settings.remove("queue")
            assertNull(settings.getStringOrNull("queue"))
            settings.putString("unicode", "歌曲🎶".repeat(4000))
            assertEquals("歌曲🎶".repeat(4000), LargeValueSettings(native, directory).getStringOrNull("unicode"))
            settings.putInt("unicode", 12)
            assertEquals(12, settings.getInt("unicode", 0))
            assertEquals("12", settings.getStringOrNull("unicode"))
            settings.clear()
            assertEquals(0, settings.size)
            com.lalilu.lmusic.impl.KvSettingsSaver(settings)
            val history = com.lalilu.lplayer.playback.HistoryStorageImpl()
            val identity = com.lalilu.lplayer.playback.HistoryQueueIdentity(
                List(10000) { "track-$it" }, List(10000) { "local" }, 123)
            history.saveSnapshot(identity, 4567L)
            val snapshot = history.readSnapshot()!!
            assertEquals(identity.ids, snapshot.ids)
            assertEquals(123, snapshot.index)
            assertEquals(4567L, snapshot.position)

        } finally { node.removeNode(); directory.toFile().deleteRecursively() }
    }

    @Test fun failedFileWritePreservesLegacyValue() {
        val node = Preferences.userRoot().node("lmusic-tests/" + UUID.randomUUID())
        val root = Files.createTempDirectory("settings-failure")
        try {
            val native = PreferencesSettings(node)
            native.putString("queue", "previous")
            val unavailable = root.resolve("not-directory"); Files.writeString(unavailable, "occupied")
            assertFails { LargeValueSettings(native, unavailable).putString("queue", "x".repeat(10000)) }
            assertEquals("previous", native.getStringOrNull("queue"))
        } finally { node.removeNode(); root.toFile().deleteRecursively() }
    }
}
