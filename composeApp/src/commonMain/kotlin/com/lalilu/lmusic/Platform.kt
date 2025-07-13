package com.lalilu.lmusic

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform