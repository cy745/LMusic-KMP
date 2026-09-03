package com.lalilu.lmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lalilu.lmusic.deeplink.DeepLinkHandler
import com.lalilu.lmusic.external.ExternalAudioOpenCoordinator
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.init
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (CrashReportStore.showPendingReport(this)) {
            finish()
            return
        }

        FileKit.init(this)

        // 配置变化会携带原 Intent 重建 Activity；仅在首次创建时消费，避免重复执行外部命令。
        if (savedInstanceState == null) handleViewIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme.equals("lmusic", ignoreCase = true)) {
            DeepLinkHandler.handle(intent.dataString)
            return
        }
        KoinPlatform.getKoin()
            .get<ExternalAudioOpenCoordinator>()
            .submit(PlatformFile(uri))
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
