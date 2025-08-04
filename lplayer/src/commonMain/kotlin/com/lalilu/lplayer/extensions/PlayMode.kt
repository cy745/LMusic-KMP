package com.lalilu.lplayer.extensions;

enum class PlayMode(val index: Int) {
    ListRecycle(0),
    RepeatOne(1),
    Shuffle(2);

    companion object {
        fun indexOf(index: Int): PlayMode {
            return entries.firstOrNull { it.index == index } ?: ListRecycle
        }

        fun from(string: String?): PlayMode {
            return string?.runCatching { valueOf(this) }
                ?.getOrNull()
                ?: ListRecycle
        }
    }
}