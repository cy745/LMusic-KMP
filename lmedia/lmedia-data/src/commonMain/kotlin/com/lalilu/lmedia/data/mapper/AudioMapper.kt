package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val legacyMetadataJsonParser = Json { ignoreUnknownKeys = true }

fun LAudioEntity.toDomain(): LAudio {
    val mergedExtra = legacyMetadataJson
        .toLegacyAudioExtra()
        .plus(extra.orEmpty().withLegacyAliases())
        .takeIf(Map<String, String>::isNotEmpty)

    return LAudio(
        id = id,
        title = title,
        subtitle = subtitle,
        mediaSourceName = mediaSourceName,
        extra = mergedExtra,
        available = available,
    )
}

/** 保留旧数据源的私有键，同时补齐现行公共键；已经存在的公共键始终优先。 */
private fun Map<String, String>.withLegacyAliases(): Map<String, String> = buildMap {
    putAll(this@withLegacyAliases)
    putAliasIfAbsent(LAudioExtraKeys.DateAdded, "date_added")
    putAliasIfAbsent(LAudioExtraKeys.DateModified, "date_modified")
    putAliasIfAbsent(LAudioExtraKeys.Date, "year")
}

fun LAudio.toEntity(): LAudioEntity = LAudioEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    legacyMetadataJson = "{}",
    extra = extra,
    available = available,
)

/** 将旧 Metadata JSON 稀疏映射到标准 extra；损坏数据按空对象处理，不影响数据库打开。 */
private fun String.toLegacyAudioExtra(): Map<String, String> {
    val jsonObject = runCatching {
        legacyMetadataJsonParser.parseToJsonElement(this) as? JsonObject
    }.getOrNull() ?: return emptyMap()

    return buildMap {
        putIfNotBlank(LAudioExtraKeys.ArtistName, jsonObject.string("artist"))
        putIfNotBlank(LAudioExtraKeys.AlbumName, jsonObject.string("album"))
        putIfNotBlank(LAudioExtraKeys.AlbumArtist, jsonObject.string("albumArtist"))
        putIfNotBlank(LAudioExtraKeys.Genre, jsonObject.string("genre"))
        putIfNotBlank(LAudioExtraKeys.Composer, jsonObject.string("composer"))
        putIfNotBlank(LAudioExtraKeys.Lyricist, jsonObject.string("lyricist"))
        putIfNotBlank(LAudioExtraKeys.Comment, jsonObject.string("comment"))
        putIfNotBlank(LAudioExtraKeys.Track, jsonObject.string("track"))
        putIfNotBlank(LAudioExtraKeys.Disc, jsonObject.string("disc"))
        putIfNotBlank(LAudioExtraKeys.Date, jsonObject.string("date"))
        putIfPositive(LAudioExtraKeys.Duration, jsonObject.long("duration"))
        putIfPositive(LAudioExtraKeys.DateAdded, jsonObject.long("dateAdded"))
        putIfPositive(LAudioExtraKeys.DateModified, jsonObject.long("dateModified"))
    }
}

private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
    value?.takeIf(String::isNotBlank)?.let { put(key, it) }
}

private fun MutableMap<String, String>.putIfPositive(key: String, value: Long?) {
    value?.takeIf { it > 0L }?.let { put(key, it.toString()) }
}

private fun MutableMap<String, String>.putAliasIfAbsent(key: String, legacyKey: String) {
    if (key !in this) this[legacyKey]?.takeIf(String::isNotBlank)?.let { put(key, it) }
}
