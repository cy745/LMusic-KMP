package com.lalilu.lplayer.player

import java.nio.file.Files
import kotlin.test.*

class PackagedVlcDiscoveryTest {
    @Test fun missingLibrariesAreNotReportedAsFound() {
        val root = Files.createTempDirectory("vlc-missing").toFile()
        try {
            val strategy = DefaultVlcDiscoverer(false, { root.path }, { true })
            assertNull(strategy.discover())
            root.resolve("vlc").mkdirs()
            root.resolve("vlc/libvlc.so").writeText("")
            assertNull(strategy.discover())
        } finally { root.deleteRecursively() }
    }
    @Test fun packagedWindowsAndLinuxLibrariesResolveTheirRealPlugins() {
        for (windows in listOf(true, false)) {
            val root = Files.createTempDirectory("vlc-layout").toFile()
            try {
                val lib = root.resolve("vlc").apply { mkdirs() }
                val extension = if (windows) ".dll" else ".so.5.12.0"
                lib.resolve("libvlc$extension").writeText("")
                lib.resolve("libvlccore$extension").writeText("")
                val plugins = lib.resolve("plugins").apply { mkdirs() }
                val paths = mutableListOf<String>()
                val strategy = DefaultVlcDiscoverer(windows, { root.path }, { paths += it; true })
                assertEquals(lib.absolutePath, strategy.discover())
                assertTrue(strategy.onSetPluginPath(lib.path))
                assertEquals(listOf(plugins.path), paths)
            } finally { root.deleteRecursively() }
        }
    }
}
