package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import uk.co.caprica.vlcj.binding.internal.libvlc_media_close_cb
import uk.co.caprica.vlcj.binding.internal.libvlc_media_open_cb
import uk.co.caprica.vlcj.binding.internal.libvlc_media_read_cb
import uk.co.caprica.vlcj.binding.internal.libvlc_media_seek_cb
import uk.co.caprica.vlcj.binding.support.types.size_t
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 实现CallbackMedia接口，用于播放内存中的ByteArray音频数据
 */
class ByteArrayCallbackMedia private constructor(
    private var data: ByteArray
) : CallbackMedia {
    companion object {
        private const val TAG = "ByteArrayCallbackMedia"
        private val cache = mutableSetOf<ByteArrayCallbackMedia>()

        /**
         * 创建或重用ByteArrayCallbackMedia实例
         */
        fun obtain(data: ByteArray): ByteArrayCallbackMedia {
            // 获取缓存中的已关闭的ByteArrayCallbackMedia实例
            val media = cache.firstOrNull { it.closed.get() }

            // 若存在，则重用
            if (media != null) {
                Logger.i(tag = TAG, messageString = "Reuse: currentSize: ${cache.size}")
                return media.reset(data)
            }

            // 创建新的ByteArrayCallbackMedia实例
            return ByteArrayCallbackMedia(data)
                .also { cache.add(it) }
                .also { Logger.i(tag = TAG, messageString = "New: currentSize: ${cache.size}") }
        }
    }

    private fun reset(data: ByteArray): ByteArrayCallbackMedia = apply {
        this.data = data
        position.set(0)
        closed.set(false)
    }

    private val position = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val length get() = data.size.toLong()

    // Open回调函数 - 初始化媒体数据
    private val openCallback = libvlc_media_open_cb { opaque, _, sizePointer ->
        lock.withLock {
            if (closed.get()) return@libvlc_media_open_cb -1

            // 设置媒体总长度
            (sizePointer as LongByReference).value = length
            position.set(0)
            0 // 成功
        }
    }

    // Read回调函数 - 读取媒体数据
    private val readCallback = libvlc_media_read_cb { opaque, buffer, bufferSize ->
        lock.withLock {
            if (closed.get()) return@libvlc_media_read_cb size_t(-1L)

            val currentPosition = position.get()
            if (currentPosition >= length) {
                return@libvlc_media_read_cb size_t(0L) // 已经读取完所有数据
            }

            // 计算可以读取的字节数
            val bytesToRead = bufferSize.toLong()
                .coerceAtMost(length - currentPosition)
                .toInt()

            // 将数据从ByteArray复制到VLC的缓冲区
            buffer?.write(0, data, currentPosition.toInt(), bytesToRead)

            // 更新位置
            position.addAndGet(bytesToRead.toLong())

            size_t(bytesToRead.toLong()) // 返回实际读取的字节数
        }
    }

    // Seek回调函数 - 跳转到指定位置
    private val seekCallback = libvlc_media_seek_cb { opaque, offset ->
        lock.withLock {
            if (closed.get()) return@libvlc_media_seek_cb -1

            // 设置新的位置
            position.set(offset)
            0 // 成功
        }
    }

    // Close回调函数 - 关闭媒体
    private val closeCallback = libvlc_media_close_cb { opaque ->
        lock.withLock {
            closed.set(true)
        }
    }

    override fun getOpen(): libvlc_media_open_cb = openCallback

    override fun getRead(): libvlc_media_read_cb = readCallback

    override fun getSeek(): libvlc_media_seek_cb = seekCallback

    override fun getClose(): libvlc_media_close_cb = closeCallback

    override fun getOpaque(): Pointer? = null
}