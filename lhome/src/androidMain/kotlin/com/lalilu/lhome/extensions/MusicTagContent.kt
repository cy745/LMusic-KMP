package com.lalilu.lhome.extensions

import android.app.SearchManager
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
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.lalilu.SlotContent
import com.lalilu.SlotParamContext
import com.lalilu.extensions.LocalToaster
import com.lalilu.lmedia.entity.LAudio
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("music_tags")
@Single
class MusicTagContent : SlotContent {

    @Composable
    override fun SlotParamContext.Content(modifier: Modifier) {
        val song = param<@Composable () -> LAudio?>("song")?.invoke()
        val context = LocalContext.current
        val toaster = LocalToaster.current

        val intent = remember(song) {
            val uri = song?.extra?.get("uri")?.toUri()
            when {
                // 如果是MediaStore获取到的，则直接跳转详情页
                uri?.scheme == "content" && uri.authority == "media" -> Intent().apply {
                    component = ComponentName(
                        "com.xjcheng.musictageditor",
                        "com.xjcheng.musictageditor.SongDetailActivity"
                    )
                    action = Intent.ACTION_SEARCH
                    data = uri
                }

                // 降级搜索逻辑，让用户搜索后点击对应的歌曲
                else -> Intent().apply {
                    component = ComponentName(
                        "com.xjcheng.musictageditor",
                        "com.xjcheng.musictageditor.activity.SearchActivity"
                    )
                    putExtra(SearchManager.QUERY, song?.title)
                    action = Intent.ACTION_SEARCH
                }
            }
        }

        TextButton(
            onClick = {
                Logger.i(tag = "MusicTagContent", messageString = "启动音乐标签编辑: $song")

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
