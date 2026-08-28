package com.lalilu.lmusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    private val externalNavigationRequest = mutableStateOf<ExternalNavigationRequest?>(null)
    private var externalNavigationRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (CrashReportStore.showPendingReport(this)) {
            finish()
            return
        }

        FileKit.init(this)
        handleDeepLink(intent)

        setContent {
            App(externalNavigationRequest = externalNavigationRequest.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * 支持两种等价格式：
     * - lmusic://navigate/media_source
     * - lmusic://navigate?route=/pages/artists&keyword=周杰伦
     *
     * 普通查询参数按 String 传给 KRouter；同名参数或以 [] 结尾的参数按 List<String> 传入。
     */
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != DEEP_LINK_SCHEME || uri.host != DEEP_LINK_HOST) return

        val route = uri.getQueryParameter(ROUTE_PARAM)
            ?.takeIf(String::isNotBlank)
            ?: uri.path?.takeIf { it.isNotBlank() && it != "/" }
            ?: return
        val normalizedRoute = if (route.startsWith('/')) route else "/$route"

        externalNavigationRequest.value = ExternalNavigationRequest(
            id = ++externalNavigationRequestId,
            route = normalizedRoute,
            params = uri.toRouteParams(),
        )
    }

    private fun Uri.toRouteParams(): Map<String, Any?> = buildMap {
        queryParameterNames
            .filterNot { it == ROUTE_PARAM }
            .forEach { rawName ->
                val isList = rawName.endsWith("[]")
                val name = rawName.removeSuffix("[]")
                val values = getQueryParameters(rawName)
                if (name.isNotBlank() && values.isNotEmpty()) {
                    put(name, if (isList || values.size > 1) values else values.first())
                }
            }
    }

    private companion object {
        const val DEEP_LINK_SCHEME = "lmusic"
        const val DEEP_LINK_HOST = "navigate"
        const val ROUTE_PARAM = "route"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
