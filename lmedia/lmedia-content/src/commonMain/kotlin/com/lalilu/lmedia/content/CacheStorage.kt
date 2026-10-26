package com.lalilu.lmedia.content

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.kotlincrypto.hash.sha2.SHA256

internal fun digest(bytes: ByteArray): String = SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
internal fun digest(text: String) = digest(text.encodeToByteArray())

/** One owner per storage root. Operations run on the cache's IO dispatcher, under its lock. */
interface CacheStorage {
    fun read(name: String): ByteArray?
    fun writeAtomic(name: String, bytes: ByteArray)
    fun delete(name: String)
    fun names(): List<String>
}

/** Atomic same-directory replacement: incomplete writes never become visible cache entries. */
class FileCacheStorage(root: String) : CacheStorage {
    private val root = Path(root)
    init { SystemFileSystem.createDirectories(this.root) }
    private fun path(name: String): Path {
        require(name.matches(Regex("[a-zA-Z0-9_.-]+")) && !name.startsWith('.'))
        return Path(root, name)
    }
    override fun read(name: String): ByteArray? {
        val file = path(name)
        if (!SystemFileSystem.exists(file)) return null
        return SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    }
    override fun writeAtomic(name: String, bytes: ByteArray) {
        val temporary = path("$name.tmp")
        try {
            SystemFileSystem.sink(temporary).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(temporary, path(name))
        } finally {
            if (SystemFileSystem.exists(temporary)) SystemFileSystem.delete(temporary)
        }
    }
    override fun delete(name: String) { SystemFileSystem.delete(path(name), mustExist = false) }
    override fun names(): List<String> = SystemFileSystem.list(root).map { it.name }
}
