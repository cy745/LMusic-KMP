package com.lalilu.lmedia.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.lmedia.util.IfAddresses
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Composable
fun SandBoxFileSystemSourceContent(
    modifier: Modifier = Modifier,
    onSourceUpdate: () -> Unit = {}
) {
    val address = remember { mutableStateOf<List<IfAddresses>>(emptyList()) }
    val currentIp = remember { mutableStateOf("") }
    val qrCodeData = remember { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()

//    LaunchedEffect(Unit) {
//        delay(2000)
//        if (isActive) {
//            SandBoxFileSystemServer.start(
//                onStart = { urls ->
//                    scope.launch(Dispatchers.io) {
//                        val url = urls.firstOrNull() ?: "hello world"
//                        address.value = IfaddrsInteractor.get(setOf("wlan0", "en0")).toList()
//                        val ip = address.value.firstOrNull { it.afInet?.startsWith("192") == true }
//                            ?.afInet ?: ""
//
//                        val actualUrl = url.replace("0.0.0.0", ip)
//                        currentIp.value = actualUrl
//
//                        val qrCode = QRCode.ofSquares()
//                            .withColor(Colors.BLACK)
//                            .withSize(10)
//                            .build(actualUrl)
//
//                        val bytes = qrCode.renderToBytes()
//
//                        qrCodeData.value = bytes
//                    }
//                },
//                onSourceUpdate = {
//                    onSourceUpdate()
//                }
//            )
//        }
//    }

//    DisposableEffect(Unit) {
//        onDispose { SandBoxFileSystemServer.stop() }
//    }

    Card(modifier = modifier) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Text(text = "SandBoxFileSystemSource", style = MaterialTheme.typography.headlineLarge)
//                Text(text = "Server status: isRunning: ${SandBoxFileSystemServer.server != null}")
                Text(text = "Server url: ${currentIp.value}")

                qrCodeData.value?.let {
                    AsyncImage(
                        modifier = Modifier
                            .height(200.dp)
                            .width(200.dp)
                            .background(color = MaterialTheme.colorScheme.surfaceDim)
                            .padding(16.dp),
                        model = it,
                        contentDescription = "Server Address QRCode"
                    )
                }

                Column {
                    address.value.forEach {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(text = "[${it.ifName}]", style = MaterialTheme.typography.titleMedium)

                            Column {
                                Text(text = "ipv4: ${it.afInet}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "ipv6: ${it.afInet6}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "UP: ${it.isUp}, RUNNING: ${it.isRunning}, LOOPBACK: ${it.isLoopback}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
