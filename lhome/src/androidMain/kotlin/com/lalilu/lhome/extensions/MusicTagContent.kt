package com.lalilu.lhome.extensions

import android.content.ComponentName
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
import com.lalilu.SlotContent
import com.lalilu.SlotParamContext
import com.lalilu.extensions.LocalToaster
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("music_tags")
@Single
class MusicTagContent : SlotContent {

    @Composable
    override fun SlotParamContext.Content(modifier: Modifier) {
        val uri = param<@Composable () -> String>("uri")?.invoke()
        val context = LocalContext.current
        val toaster = LocalToaster.current

        val intent = remember(uri) {
            Intent().apply {
                component = ComponentName(
                    "com.xjcheng.musictageditor",
                    "com.xjcheng.musictageditor.SongDetailActivity"
                )
                action = "android.intent.action.VIEW"
            }
        }

        TextButton(
            onClick = {
                if (context.checkActivityIsExist(intent)) {
                    context.startActivity(intent)
                } else {
                    toaster?.show(message = "未安装[音乐标签]")
                }
            },
            colors = ButtonDefaults.elevatedButtonColors(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.08f)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(text = "音乐标签编辑")
        }
    }
}

private fun Context.checkActivityIsExist(intent: Intent): Boolean {
    return intent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY) != null
}
