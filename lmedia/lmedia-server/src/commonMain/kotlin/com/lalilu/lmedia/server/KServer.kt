package com.lalilu.lmedia.server

interface KServer {
    suspend fun startSync() {}
    suspend fun startAsync() {}
    suspend fun stopAndRelease() {}
}