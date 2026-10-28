package com.lalilu.lhome.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.*
import com.lalilu.component.LibraryEmptyState
import com.lalilu.navigation.smartbar.NavigatorHeader
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/** Design-only renderer; does not change production screens. */
@OptIn(ExperimentalTestApi::class)
class LoadingStatesPreviewTest {
    @Test fun renderLoadingComparison() = runDesktopComposeUiTest(width = 1240, height = 820) {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF19120F), surface = Color(0xFF19120F),
                onBackground = Color(0xFFEEDDD5), onSurface = Color(0xFFEEDDD5),
            )) {
                Column(Modifier.fillMaxSize().background(Color(0xFF100D0C)).padding(28.dp)) {
                    Text("曲库 · 加载状态", color = Color(0xFFEEDDD5), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("保留页面结构，只在确认结果后显示空提示", color = Color(0xFF9E8E86), fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Panel("01  快速加载", "不闪空提示，也不闪加载动画", 0, Modifier.weight(1f))
                        Panel("02  等待稍长", "约 300ms 后，出现轻量文字提示", 1, Modifier.weight(1f))
                        Panel("03  确认没有内容", "查询完成后，才显示操作入口", 2, Modifier.weight(1f))
                    }
                }
            }
        }
        waitForIdle()
        val bitmap = captureToImage()
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData()!!.use { File("docs/ui/library-loading/loading-comparison.png").apply { parentFile.mkdirs() }.writeBytes(it.bytes) }
        }
    }
}

@Composable
private fun Panel(label: String, note: String, state: Int, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFFEEDDD5), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(note, color = Color(0xFF9E8E86), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        Column(Modifier.fillMaxWidth().height(590.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("09:41", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("▰  87%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            NavigatorHeader(title = "歌曲", columnExtraContent = {
                // Reserve the same subtitle line without claiming a zero count while loading.
                Box(Modifier.height(18.dp)) {
                    if (state == 2) Text("共 0 首歌曲", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            })
            if (state == 1) Text("正在读取曲库…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                modifier = Modifier.padding(start = 20.dp, top = 56.dp))
            if (state == 2) LibraryEmptyState("还没有歌曲", "添加媒体源后，你的歌曲会出现在这里",
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
                actionLabel = "添加媒体源")
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().background(Color(0xFF302420)).padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("歌曲", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("搜索    排序    ···", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
            }
        }
    }
}
