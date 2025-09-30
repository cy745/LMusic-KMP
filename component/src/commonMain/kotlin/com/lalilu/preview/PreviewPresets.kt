package com.lalilu.preview

class PreviewPresets(
    val key: String,
    private val presets: Map<String, Any>
) {
    companion object {
        val EMPTY = PreviewPresets("EMPTY", emptyMap())

        fun build(
            key: String,
            list: List<Map<String, Any>>
        ): List<PreviewPresets> {
            return list.map {
                PreviewPresets(key, it)
            }
        }
    }

    fun intValue(key: String, elseValue: Int = 0): Int {
        return presets[key] as? Int ?: elseValue
    }

    fun stringValue(key: String, elseValue: String = ""): String {
        return presets[key] as? String ?: elseValue
    }

    fun floatValue(key: String, elseValue: Float = 0f): Float {
        return presets[key] as? Float ?: elseValue
    }

    fun booleanValue(key: String, elseValue: Boolean = false): Boolean {
        return presets[key] as? Boolean ?: elseValue
    }
}

/**
 * 歌曲预览数据
 *
 * 歌曲相关参数：
 * id           歌曲ID
 * title        歌曲标题
 * subtitle     歌曲副标题
 * duration     歌曲时长
 */
val SongsPreviewData = PreviewPresets.build(
    key = "SONGS",
    list = listOf(
        mapOf(
            "id" to 1,
            "title" to "Blinding Lights",
            "subtitle" to "The Weeknd",
            "duration" to 2000
        ),
        mapOf(
            "id" to 2,
            "title" to "夜曲",
            "subtitle" to "周杰伦",
            "duration" to 2100
        ),
        mapOf(
            "id" to 3,
            "title" to "Lemon",
            "subtitle" to "米津玄師",
            "duration" to 2500
        ),
        mapOf(
            "id" to 4,
            "title" to "Shape of You",
            "subtitle" to "Ed Sheeran",
            "duration" to 2300
        ),
        mapOf(
            "id" to 5,
            "title" to "稻香",
            "subtitle" to "周杰伦",
            "duration" to 1800
        ),
        mapOf(
            "id" to 6,
            "title" to "Dynamite",
            "subtitle" to "BTS",
            "duration" to 1900
        ),
        mapOf(
            "id" to 7,
            "title" to "告白气球",
            "subtitle" to "周杰伦",
            "duration" to 2200
        ),
        mapOf(
            "id" to 8,
            "title" to "Uptown Funk",
            "subtitle" to "Mark Ronson ft. Bruno Mars",
            "duration" to 2700
        ),
        mapOf(
            "id" to 9,
            "title" to "演员",
            "subtitle" to "薛之谦",
            "duration" to 2400
        ),
        mapOf(
            "id" to 10,
            "title" to "Senorita",
            "subtitle" to "Shawn Mendes & Camila Cabello",
            "duration" to 2300
        ),
        mapOf(
            "id" to 11,
            "title" to "光年之外",
            "subtitle" to "邓紫棋",
            "duration" to 2900
        ),
        mapOf(
            "id" to 12,
            "title" to "Despacito",
            "subtitle" to "Luis Fonsi ft. Daddy Yankee",
            "duration" to 2800
        ),
        mapOf(
            "id" to 13,
            "title" to "紅蓮華",
            "subtitle" to "LiSA",
            "duration" to 2600
        ),
        mapOf(
            "id" to 14,
            "title" to "Perfect",
            "subtitle" to "Ed Sheeran",
            "duration" to 2500
        ),
        mapOf(
            "id" to 15,
            "title" to "匆匆那年",
            "subtitle" to "王菲",
            "duration" to 2700
        ),
        mapOf(
            "id" to 16,
            "title" to "Gangnam Style",
            "subtitle" to "PSY",
            "duration" to 2900
        ),
        mapOf(
            "id" to 17,
            "title" to "泡沫",
            "subtitle" to "邓紫棋",
            "duration" to 2500
        ),
        mapOf(
            "id" to 18,
            "title" to "Counting Stars",
            "subtitle" to "OneRepublic",
            "duration" to 2400
        ),
        mapOf(
            "id" to 19,
            "title" to "Flower",
            "subtitle" to "尹美莱",
            "duration" to 2300
        ),
        mapOf(
            "id" to 20,
            "title" to "青花瓷",
            "subtitle" to "周杰伦",
            "duration" to 2100
        )
    )
)