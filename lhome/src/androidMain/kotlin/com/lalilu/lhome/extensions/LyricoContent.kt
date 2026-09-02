package com.lalilu.lhome.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.lalilu.SlotContent
import com.lalilu.SlotParamContext
import com.lalilu.extensions.LocalToaster
import com.lalilu.lmedia.domain.model.LAudio
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

private const val LYRICO_PACKAGE_NAME = "com.lonx.lyrico"
private const val LYRICO_EDIT_TAG_ACTION = "com.lonx.lyrico.action.EDIT_TAG"

@Named("lyrico_tags")
@Single
class LyricoContent : SlotContent {

    @Composable
    override fun SlotParamContext.Content(modifier: Modifier) {
        val song = param<@Composable () -> LAudio?>("song")?.invoke()
        val context = LocalContext.current
        val toaster = LocalToaster.current
        val intent = remember(song) { song?.buildLyricoEditIntent() }

        TextButton(
            modifier = modifier,
            onClick = {
                Logger.i(tag = "LyricoContent", messageString = "启动 Lyrico 编辑: $song")

                when {
                    !context.checkPackageIsInstalled(LYRICO_PACKAGE_NAME) -> {
                        toaster?.show(message = "未安装[Lyrico]")
                    }

                    intent == null -> {
                        toaster?.show(message = "无法打开此歌曲")
                    }

                    context.checkActivityIsExist(intent) -> {
                        context.startActivity(intent)
                    }

                    else -> {
                        toaster?.show(message = "无法启动 Lyrico")
                    }
                }
            },
            colors = ButtonDefaults.elevatedButtonColors(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.08f)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(text = "Lyrico 编辑")
        }
    }
}

private fun LAudio.buildLyricoEditIntent(): Intent? {
    val uri = extra?.get("uri")?.toUri()?.takeIf { it.scheme == "content" } ?: return null
    return Intent(LYRICO_EDIT_TAG_ACTION).apply {
        setPackage(LYRICO_PACKAGE_NAME)
        setDataAndType(uri, "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Context.checkActivityIsExist(intent: Intent): Boolean {
    return intent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY) != null
}

@Suppress("DEPRECATION")
private fun Context.checkPackageIsInstalled(packageName: String): Boolean {
    return runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
}
