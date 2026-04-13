package com.lalilu.lmusic.util

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
fun createWorker() = Worker(js("""new URL("@androidx/sqlite-web-worker/worker.js", import.meta.url)"""))

@OptIn(ExperimentalWasmJsInterop::class)
fun createWebWorkerSQLiteDriver(): WebWorkerSQLiteDriver {
    val worker = createWorker()
    return WebWorkerSQLiteDriver(worker)
}