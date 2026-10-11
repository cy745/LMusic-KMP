package com.lalilu.lmusic.util

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
private fun sqliteWorkerUrl(): String = js("""new URL("@androidx/sqlite-web-worker/worker.js", import.meta.url).href""")

fun createWorker() = Worker(sqliteWorkerUrl())

@OptIn(ExperimentalWasmJsInterop::class)
fun createWebWorkerSQLiteDriver(): WebWorkerSQLiteDriver {
    val worker = createWorker()
    return WebWorkerSQLiteDriver(worker)
}
