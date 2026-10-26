package com.lalilu.lmusic.impl

import java.nio.file.Files
import kotlin.test.*

class DesktopDatabasePathTest {
    @Test fun newDatabaseUsesCreatedApplicationDirectory() {
        val root = Files.createTempDirectory("database-path").toFile()
        try {
            val path = desktopDatabasePath("library", root.resolve("application"), root.resolve("missing-legacy"))
            assertEquals(root.resolve("application/db/library.db").absolutePath, path)
            assertTrue(root.resolve("application/db").isDirectory)
        } finally { root.deleteRecursively() }
    }

    @Test fun existingLegacyDatabaseAndWalRemainUntouched() {
        val root = Files.createTempDirectory("legacy-database-path").toFile()
        try {
            val legacy = root.resolve("legacy").apply { mkdirs() }
            val database = legacy.resolve("library.db").apply { writeText("database") }
            val wal = legacy.resolve("library.db-wal").apply { writeText("wal") }
            assertEquals(database.absolutePath, desktopDatabasePath("library", root.resolve("application"), legacy))
            assertEquals("database", database.readText())
            assertEquals("wal", wal.readText())
            assertFalse(root.resolve("application").exists())
        } finally { root.deleteRecursively() }
    }
}
