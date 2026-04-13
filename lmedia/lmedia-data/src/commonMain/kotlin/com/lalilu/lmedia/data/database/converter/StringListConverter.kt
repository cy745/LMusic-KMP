package com.lalilu.lmedia.data.database.converter

import androidx.room3.TypeConverter

class StringListConverter {
    @TypeConverter
    fun fromStringList(list: MutableList<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toStringList(string: String): MutableList<String> {
        return string.split(",").toMutableList()
    }
}