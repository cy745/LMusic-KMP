package com.lalilu.lplayer.player

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsVlcDiscovererTest {
    @Test fun pluginCallbackReceivesAlreadyResolvedDirectory() {
        val root = Files.createTempDirectory("lmusic-vlc-discovery-").toFile()
        try {
            val plugins = root.resolve("plugins").apply { mkdirs() }
            val values = mutableListOf<String>()
            val strategy = MacOsVlcDiscoverer { values += it; true }
            assertTrue(strategy.onSetPluginPath(root.path))
            assertEquals(listOf(plugins.path), values)
        } finally { root.deleteRecursively() }
    }

    @Test fun missingPluginDirectoryDoesNotConfigureAnInventedPath() {
        val root = Files.createTempDirectory("lmusic-vlc-discovery-").toFile()
        try {
            val values = mutableListOf<String>()
            val strategy = MacOsVlcDiscoverer { values += it; true }
            assertFalse(strategy.onSetPluginPath(root.path))
            assertTrue(values.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun environmentSetterFailureIsNotReportedAsSuccess() {
        val root = Files.createTempDirectory("lmusic-vlc-discovery-").toFile()
        try {
            root.resolve("plugins").mkdirs()
            assertFalse(MacOsVlcDiscoverer { false }.onSetPluginPath(root.path))
        } finally { root.deleteRecursively() }
    }
}
