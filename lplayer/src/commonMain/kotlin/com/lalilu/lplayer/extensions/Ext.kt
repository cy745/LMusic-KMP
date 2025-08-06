package com.lalilu.lplayer.extensions


fun <T> List<T>.getNextOf(item: T, cycle: Boolean = false): T? {
    val nextIndex = indexOf(item) + 1
    return getOrNull(if (cycle) nextIndex % size else nextIndex)
}

fun <T> List<T>.getPreviousOf(item: T, cycle: Boolean = false): T? {
    var previousIndex = indexOf(item) - 1
    if (previousIndex < 0 && cycle) {
        previousIndex = size - 1
    }
    return getOrNull(previousIndex)
}

fun <T : Any> List<T>.move(from: Int, to: Int): List<T> = toMutableList().apply {
    val targetIndex = if (from < to) to else to + 1
    val temp = removeAt(from)
    add(targetIndex, temp)
}

fun <T : Any> List<T>.add(index: Int = -1, item: T): List<T> = toMutableList().apply {
    if (index == -1) add(item) else add(index, item)
}

fun <T : Any> List<T>.removeAt(index: Int): List<T> = toMutableList().apply {
    removeAt(index)
}