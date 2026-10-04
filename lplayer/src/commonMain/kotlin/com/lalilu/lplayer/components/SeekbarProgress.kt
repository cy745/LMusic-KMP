package com.lalilu.lplayer.components

/** Unknown duration must never turn absolute playback/buffer time into a percentage. */
internal fun seekbarFraction(value: Float, min: Float, max: Float): Float {
    if (!value.isFinite() || !min.isFinite() || !max.isFinite() || max <= min) return 0f
    return ((value - min) / (max - min)).coerceIn(0f, 1f)
}
