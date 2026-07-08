package com.lalilu.lmedia.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lalilu.common.ext.io
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.lmedia_ui.generated.resources.Res
import com.lalilu.lmedia.server.SandBoxFileSystemServer
import com.lalilu.lmedia.util.IfAddresses
import com.lalilu.lmedia.util.IfaddrsInteractor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qrcode.QRCode
import qrcode.color.Colors

@OptIn(ExperimentalForeignApi::class)
@Composable
fun MediaSource.SandBoxFileSystemSourceContent(
    modifier: Modifier = Modifier,
) {
    val state = source().collectAsStateWithLifecycle(initialValue = Snapshot.Loading)
    val address = remember { mutableStateOf<List<IfAddresses>>(emptyList()) }
    val currentIp = remember { mutableStateOf("") }
    val qrCodeData = remember { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()

    SourceCard(
        modifier = modifier,
        state = { state.value },
        extraContent = {
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "running on: ${currentIp.value}",
                    style = MaterialTheme.typography.bodySmall,
                )

                qrCodeData.value?.let {
                    AsyncImage(
                        modifier = Modifier
                            .height(200.dp)
                            .width(200.dp)
                            .background(color = MaterialTheme.colorScheme.surfaceDim)
                            .padding(16.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        model = it,
                        contentDescription = "Server Address QRCode"
                    )
                }

                address.value.forEach {
                    Column {
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
    )

    LaunchedEffect(Unit) {
        delay(2000)
        if (isActive) {
            SandBoxFileSystemServer.start(
                indexHtml = { Res.readBytes("files/index.html") },
                onStart = { urls ->
                    scope.launch(Dispatchers.io) {
                        val url = urls.firstOrNull() ?: "hello world"
                        address.value = IfaddrsInteractor.get(setOf("wlan0", "en0")).toList()
                        val ip = address.value.firstOrNull { it.afInet?.startsWith("192") == true }
                            ?.afInet ?: ""

                        val actualUrl = url.replace("0.0.0.0", ip)
                        currentIp.value = actualUrl

                        val qrCode = QRCode.ofSquares()
                            .withColor(Colors.BLACK)
                            .withSize(10)
                            .build(actualUrl)

                        val bytes = qrCode.renderToBytes()

                        qrCodeData.value = bytes
                    }
                },
                onSourceUpdate = {
                    config.call<Unit>("Refresh")
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { SandBoxFileSystemServer.stop() }
    }
}
