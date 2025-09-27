package com.lalilu.lmedia.source.sandbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.common.ext.io
import com.lalilu.lmedia.util.IfaddrsInteractor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalForeignApi::class)
@Composable
fun SandBoxFileSystemSourceContent(
    modifier: Modifier = Modifier,
    onSourceUpdate: () -> Unit = {}
) {
    val currentIp = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(2000)
        if (isActive) {
            SandBoxFileSystemServer.start(
                onStart = {
                    scope.launch(Dispatchers.io) {
                        currentIp.value = IfaddrsInteractor.getAll()
                            .joinToString(separator = "\n") { "[${it.ifName}]ipv4: ${it.afInet}, afInet6: ${it.afInet6}" }
                    }
                },
                onSourceUpdate = {
                    onSourceUpdate()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { SandBoxFileSystemServer.stop() }
    }

    Card(modifier = modifier) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Text(text = "SandBoxFileSystemSource", style = MaterialTheme.typography.headlineLarge)
                Text(text = "Server status: isRunning: ${SandBoxFileSystemServer.server != null}")
                Text(text = "Current IP: ${currentIp.value}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
