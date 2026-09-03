package com.lalilu.lmedia.source

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.common.ext.io
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourceInfoPanel
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.lmedia_ui.generated.resources.Res
import com.lalilu.lmedia.screen.SANDBOX_MEDIA_SOURCE_ROUTE
import com.lalilu.lmedia.server.SandBoxFileSystemServer
import com.lalilu.lmedia.source.sandbox.SandboxFileSystemSource
import com.lalilu.lmedia.util.IfAddresses
import com.lalilu.lmedia.util.IfaddrsInteractor
import com.lalilu.navigation.AppRouter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qrcode.QRCode
import qrcode.color.Colors

@OptIn(ExperimentalForeignApi::class)
fun SandboxFileSystemSource.sandBoxFileSystemSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val address = remember { mutableStateOf<List<IfAddresses>>(emptyList()) }
    val currentIp = remember { mutableStateOf("") }
    val qrCodeData = remember { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()
    var networkDetailsExpanded by rememberSaveable { mutableStateOf(false) }

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
                    refresh()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { SandBoxFileSystemServer.stop() }
    }

    return@LazyStaggeredGridContent {
        item(key = this@sandBoxFileSystemSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "Sandbox 文件系统",
                description = "扫描应用 Documents 目录，也可以从局域网向这里上传音乐",
                idleLabel = "待扫描",
            ) { uiState ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceActionButton(
                        title = if (uiState.isLoading) "停止扫描" else "重新扫描 Documents",
                        style = SourceActionStyle.Primary,
                        onClick = { if (uiState.isLoading) cancel() else refresh() },
                    )
                    SourceActionButton(
                        title = "编辑文件",
                        style = SourceActionStyle.Secondary,
                        enabled = !uiState.isLoading,
                        onClick = { AppRouter.route(SANDBOX_MEDIA_SOURCE_ROUTE).push() },
                    )
                }

                SourceInfoPanel(
                    modifier = Modifier.padding(top = 12.dp),
                    label = "局域网上传服务",
                    value = currentIp.value.ifBlank { "正在启动…" },
                    supportingText = "在同一网络的设备上打开该地址，或扫描二维码上传文件",
                    emphasized = currentIp.value.isNotBlank(),
                )

                qrCodeData.value?.let {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            modifier = Modifier
                                .size(184.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceDim,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .padding(14.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            model = it,
                            contentDescription = "局域网上传地址二维码",
                        )
                    }
                }

                if (address.value.isNotEmpty()) {
                    com.lalilu.lmedia.component.SourceSectionHeader(
                        title = "网络详情",
                        summary = "${address.value.size} 个网络接口",
                        expanded = networkDetailsExpanded,
                        onToggle = { networkDetailsExpanded = !networkDetailsExpanded },
                    )
                    AnimatedVisibility(visible = networkDetailsExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            address.value.forEach { item ->
                                SourceInfoPanel(
                                    label = item.ifName,
                                    value = item.afInet ?: item.afInet6 ?: "无可用地址",
                                    supportingText = "UP ${item.isUp} · RUNNING ${item.isRunning}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
