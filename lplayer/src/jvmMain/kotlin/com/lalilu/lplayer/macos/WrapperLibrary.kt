package com.lalilu.lplayer.macos

import com.lalilu.wrapper.MediaPlayerLibrary
import com.sun.jna.Library
import com.sun.jna.Native
import org.rococoa.ID

interface WrapperLibrary : Library {
    companion object Companion {
        val instance: WrapperLibrary by lazy {
            MediaPlayerLibrary.Companion.load()
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
        bitmapData: ID,
        bitmapWidth: Int,
        bitmapHeight: Int,
        bitsPerPixel: Int,
        bitsPerComponent: Int,
        bytesPerRow: Int
    ): ID
}