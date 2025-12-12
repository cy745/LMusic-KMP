package com.lalilu.lmedia

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.Metadata
import com.lalilu.taglib.*
import io.ktor.http.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {
    actual fun version(): String {
        return taglib_runtime_version()?.toKString()
            ?: "Unknown"
    }

    actual suspend fun readMetadata(fd: Int): Metadata? {
        return null
    }

    actual suspend fun getLyric(fd: Int): String? {
        return null
    }

    actual suspend fun getPicture(fd: Int): ByteArray? {
        return null
    }

    @Throws(Exception::class)
    actual suspend fun readMetadata(path: String): Metadata? = withContext(Dispatchers.io) {
        var result: Metadata? = null

        readTag(path) { file, tag, audioProperties ->
            val title = taglib_tag_title(tag)?.toKString() ?: "Unknown"
            val artist = taglib_tag_artist(tag)?.toKString() ?: "Unknown"
            val album = taglib_tag_album(tag)?.toKString() ?: "Unknown"
            val comment = taglib_tag_comment(tag)?.toKString() ?: ""
            val genre = taglib_tag_genre(tag)?.toKString() ?: ""
            val year = taglib_tag_year(tag)
            val track = taglib_tag_track(tag)
            val disc = taglib_property_get(file, "DISCNUMBER")
                ?.get(0)
                ?.toKString()
                ?: ""

            val duration = taglib_audioproperties_length(audioProperties)
                .toLong() * 1000 // seconds to milliseconds

            result = Metadata(
                title = title,
                album = album,
                artist = artist,
                albumArtist = artist,
                composer = "",
                lyricist = "",
                comment = comment,
                genre = genre,
                track = "$track",
                disc = disc,
                date = "$year",
                duration = duration,
                dateAdded = 0,
                dateModified = 0
            )
        }

        result
    }

    actual suspend fun getLyric(path: String): String? {
        var result: String? = null

        readFile(path) { file ->
            val prop = taglib_property_get(file, "LYRICS")

            // 获取歌词列表
            val lyrics = prop?.toList()
                ?.map { it.toKString() }

            // 获取第一个非空的歌词
            result = lyrics
                ?.firstOrNull { it.isNotBlank() }
        }

        return result
    }

    actual suspend fun getPicture(path: String): ByteArray? {
        var result: ByteArray? = null

        readFile(path) { file ->
            val pictureData = taglib_complex_property_get(file, "PICTURE")

            if (pictureData != null) {
                val pictureRef = alloc<TagLib_Complex_Property_Picture_Data>()
                taglib_picture_from_complex_property(pictureData, pictureRef.ptr)

                val size = pictureRef.size.toInt()
                val pictureType = pictureRef.pictureType?.toKString()
                val mimeType = pictureRef.mimeType?.toKString()
                val description = pictureRef.description?.toKString()

                result = pictureRef.data?.toByteArray(size)
            }

            // 回收内存资源
            taglib_complex_property_free(pictureData)
        }

        return result
    }


}

/**
 * 将指针指向的数组转换为列表
 */
@OptIn(ExperimentalForeignApi::class)
private fun <T : CPointer<*>> CPointer<CPointerVarOf<T>>.toList(): List<T> {
    return buildList {
        var index = 0
        while (true) {
            this@toList[index++]
                ?.also { add(it) }
                ?: break
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readTag(
    path: String,
    block: MemScope.(
        CPointer<TagLib_File>,
        CPointer<TagLib_Tag>,
        CPointer<TagLib_AudioProperties>?
    ) -> Unit
) = memScoped {
    val actualPath = path.substringAfter("file://")
        .decodeURLPart()

    var error: Throwable? = null
    var tag: CPointer<TagLib_Tag>? = null
    var audioProperties: CPointer<TagLib_AudioProperties>? = null

    readFile(path) { file ->
        tag = taglib_file_tag(file)
        audioProperties = taglib_file_audioproperties(file)


        if (tag == null) {
            error = IllegalArgumentException("Tag is null: $actualPath")
        }

        tag?.let { block(file, it, audioProperties) }
    }

    // 释放字符串资源
    taglib_tag_free_strings()

    if (error != null) {
        throw error
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(
    path: String,
    block: MemScope.(CPointer<TagLib_File>) -> Unit
) = memScoped {
    val actualPath = path.substringAfter("file://")
        .decodeURLPart()

    var error: Throwable? = null
    var file: CPointer<TagLib_File>? = null

    while (true) {
        file = taglib_file_new(actualPath)
        if (file == null) {
            error = IllegalArgumentException("File is null: $actualPath")
            break
        }

        val isValid = taglib_file_is_valid(file) != 0
        if (!isValid) {
            error = IllegalArgumentException("File is not valid: $actualPath")
            break
        }

        break
    }

    if (file != null) {
        block(file)
        taglib_file_free(file)
    }

    if (error != null) {
        throw error
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<ByteVarOf<Byte>>?.toByteArray(size: Int): ByteArray? {
    val bytes = this ?: return null
    return ByteArray(size) { bytes[it] }
}