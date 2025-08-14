package com.lalilu.wrapper

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface WrapperLibrary : Library {
    companion object Companion {
        val instance: WrapperLibrary by lazy {
            MediaPlayerLibrary.load()
            Native.load("wrapper", WrapperLibrary::class.java)
        }
    }

    /**
     * 创建媒体项的封面
     *
     * @param bitmapData       图片数据
     * @param bitmapWidth      图片宽度
     * @param bitmapHeight     图片高度
     * @param bitsPerPixel     图片每像素位数
     * @param bitsPerComponent 图片颜色分量位数
     * @param bytesPerRow      图片每行字节数
     * @return 媒体
     */
    fun createMediaItemArtwork(
        bitmapData: Pointer,
        bitmapWidth: Int,
        bitmapHeight: Int,
        bitsPerPixel: Int,
        bitsPerComponent: Int,
        bytesPerRow: Int
    ): Pointer
}