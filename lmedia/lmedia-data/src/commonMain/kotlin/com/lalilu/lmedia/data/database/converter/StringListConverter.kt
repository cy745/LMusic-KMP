package com.lalilu.lmedia.data.database.converter

import androidx.room3.TypeConverter
import kotlinx.serialization.json.Json

class StringListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toStringList(string: String): List<String> {
        return json.decodeFromString(string)
    }
}