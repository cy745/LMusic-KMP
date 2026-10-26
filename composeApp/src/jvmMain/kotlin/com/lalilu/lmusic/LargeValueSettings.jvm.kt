package com.lalilu.lmusic

import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.prefs.Preferences

internal actual fun persistentAppSettings(): Settings = LargeValueSettings(
    Settings(), Path.of(FileKit.filesDir.path, "large-settings"))

/** Preferences remains readable; oversized strings use atomically replaced files. */
internal class LargeValueSettings(private val delegate: Settings, private val directory: Path) : Settings by delegate {
    private fun file(key: String): Path = directory.resolve(
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".value")

    @Synchronized override fun getStringOrNull(key: String): String? {
        val path = file(key)
        if (!Files.exists(path)) return delegate.getStringOrNull(key)
        return DataInputStream(Files.newInputStream(path)).use {
            check(it.readUTF() == key) { "Invalid settings record" }
            it.readBytes().toString(Charsets.UTF_8)
        }
    }
    override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue

    @Synchronized override fun putString(key: String, value: String) {
        val target = file(key)
        if (value.length <= Preferences.MAX_VALUE_LENGTH && !Files.exists(target)) {
            delegate.putString(key, value)
            return
        }
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "pending-", ".tmp")
        try {
            DataOutputStream(Files.newOutputStream(temporary)).use {
                it.writeUTF(key)
                it.write(value.toByteArray(Charsets.UTF_8))
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    @Synchronized override fun putInt(key: String, value: Int) { delegate.putInt(key, value); Files.deleteIfExists(file(key)) }
    @Synchronized override fun putLong(key: String, value: Long) { delegate.putLong(key, value); Files.deleteIfExists(file(key)) }
    @Synchronized override fun putFloat(key: String, value: Float) { delegate.putFloat(key, value); Files.deleteIfExists(file(key)) }
    @Synchronized override fun putDouble(key: String, value: Double) { delegate.putDouble(key, value); Files.deleteIfExists(file(key)) }
    @Synchronized override fun putBoolean(key: String, value: Boolean) { delegate.putBoolean(key, value); Files.deleteIfExists(file(key)) }
    override val keys: Set<String> get() = synchronized(this) {
        delegate.keys + if (!Files.isDirectory(directory)) emptySet() else Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".value") }.map { path ->
                DataInputStream(Files.newInputStream(path)).use { it.readUTF() }
            }.toList().toSet()
        }
    }
    override val size: Int get() = keys.size
    override fun hasKey(key: String): Boolean = Files.exists(file(key)) || delegate.hasKey(key)
    @Synchronized override fun remove(key: String) {
        delegate.remove(key)
        Files.deleteIfExists(file(key))
    }
    @Synchronized override fun clear() { keys.forEach(::remove) }
}
