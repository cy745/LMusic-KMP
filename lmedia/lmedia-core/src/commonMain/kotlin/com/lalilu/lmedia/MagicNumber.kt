package com.lalilu.lmedia

import kotlinx.io.Source
import kotlinx.io.readByteArray

enum class MagicNumber(
    val desc: String,
    val type: String,
    val offset: Int = 0,
    val ext: List<String>,
    val header: ByteArray,
    val trailer: ByteArray = byteArrayOf()
) {
    MP3(
        desc = "MP3 audio file",
        type = "Multimedia",
        ext = listOf("mp3"),
        header = byteArrayOf(0x49, 0x44, 0x33)
    ),
    FLAC(
        desc = "Free Lossless Audio Codec file",
        type = "Multimedia",
        ext = listOf("flac"),
        header = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22)
    ),
    OGG(
        desc = "Ogg Vorbis Codec compressed file",
        type = "Multimedia",
        ext = listOf("oga", "ogg", "ogv", "ogx"),
        header = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x00, 0x02, 0x00, 0x00)
    ),
    M4A(
        desc = "Apple Lossless Audio Codec file",
        type = "Multimedia",
        ext = listOf("m4a"),
        header = byteArrayOf(
            0x66, 0x74, 0x79, 0x70,
            0x4D, 0x34, 0x41, 0x20
        ),
        offset = 4
    ),
    AAC(
        desc = "MPEG-2 AAC audio",
        type = "Audio",
        ext = listOf("aac"),
        header = byteArrayOf(0xFF.toByte(), 0xF9.toByte())
    ),
    DSF(
        desc = "DSD Storage Facility audio file",
        type = "Multimedia",
        ext = listOf("dsf"),
        header = byteArrayOf(0x44, 0x53, 0x44, 0x20)
    ),
    WAV(
        desc = "RIFF Windows Audio",
        type = "Multimedia",
        ext = listOf("wav"),
        header = byteArrayOf(
            0x57, 0x41, 0x56, 0x45,
            0x66, 0x6D, 0x74, 0x20
        ),
        offset = 8
    ),
    ASF(
        desc = "Windows Media Audio-Video File",
        type = "Multimedia",
        ext = listOf("asf", "wma", "wmv"),
        header = byteArrayOf(
            0x30, 0x26, 0xB2.toByte(), 0x75,
            0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11
        )
    );

    companion object {
        private val extMap = mutableMapOf<String, MagicNumber>()
        private var maxHeaderLengthWithOffset = 0

        fun match(ext: String?, source: Source): MagicNumber? {
            if (extMap.isEmpty()) {
                entries.forEach { it.ext.forEach { ext -> extMap[ext.uppercase()] = it } }
                maxHeaderLengthWithOffset = entries.maxOf { it.header.size + it.offset }
            }

            return source.use {
                val readByteArray = source.readByteArray(maxHeaderLengthWithOffset)

                // 尝试匹配扩展名，获取到扩展名以后还要校验其header是否匹配
                val magicNumber = extMap[ext?.uppercase()]
                if (magicNumber != null) {
                    for (i in magicNumber.header.indices) {
                        if (readByteArray.isEmpty() || readByteArray[magicNumber.offset + i] != magicNumber.header[i]) {
                            break
                        }
                        if (i == magicNumber.header.lastIndex) {
                            return magicNumber
                        }
                    }
                }

                // 尝试匹配header
                for (magicNumber in entries) {
                    for (i in magicNumber.header.indices) {
                        if (readByteArray.isEmpty() || readByteArray[magicNumber.offset + i] != magicNumber.header[i]) {
                            break
                        }
                        if (i == magicNumber.header.lastIndex) {
                            return magicNumber
                        }
                    }
                }

                null
            }
        }
    }
}