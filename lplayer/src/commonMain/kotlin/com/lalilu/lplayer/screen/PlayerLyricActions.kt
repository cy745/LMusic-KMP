package com.lalilu.lplayer.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.kv.KVItem
import com.lalilu.extensions.DialogItem
import com.lalilu.extensions.DialogWrapper
import com.lalilu.llyricview.LyricSettings
import com.lalilu.llyricview.provideLyricSettingsQuick
import com.lalilu.lplayer.components.PlayingToolbar
import com.lalilu.lsettings.SettingsScreenContent
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
internal fun PlayerToolbarContent(
    modifier: Modifier = Modifier,
    title: () -> String,
    subtitle: () -> String,
    contentColor: () -> Color,
    isPlaying: () -> Boolean,
    isUserTouchEnabled: () -> Boolean,
    showExtraActions: () -> Boolean,
    onDoubleClick: () -> Unit = {},
) {
    // 「双击 toolbar」这一手势契约由 toolbar 自己负责，调用方只需传入语义动作。
    // 用 rememberUpdatedState 持有最新回调，避免 pointerInput(Unit) 捕获到旧的 lambda。
    val currentOnDoubleClick = rememberUpdatedState(onDoubleClick)
    PlayingToolbar(
        modifier = modifier.pointerInput(Unit) {
            // 点在按钮上时子节点已消费该按下事件，而 detectTapGestures 默认要求未消费，
            // 因此不会抢走按钮点击；只有点在本区域空白处才会识别为双击。
            detectTapGestures(onDoubleTap = { currentOnDoubleClick.value() })
        },
        title = title,
        subtitle = subtitle,
        contentColor = contentColor,
        isPlaying = isPlaying,
        isUserTouchEnable = isUserTouchEnabled,
        isExtraVisible = showExtraActions,
        extraContent = { PlayerLyricActions(contentColor = contentColor) },
    )
}

/** 播放页共用的歌词显示开关和快捷设置入口。 */
@Composable
internal fun PlayerLyricActions(
    modifier: Modifier = Modifier,
    contentColor: () -> Color,
) {
    val lyricSettings = koinInject<KVItem<LyricSettings>>(named("LyricSettings"))
    val translationAlpha = animateFloatAsState(
        targetValue = if (lyricSettings.value.translationVisible) 1f else 0.5f,
        label = "PlayerTranslationAlpha",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(
            onClick = { showLyricQuickSettings() },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = vectorResource(RemixIcon.Editor.text),
                contentDescription = "歌词样式",
                tint = contentColor(),
            )
        }
        IconButton(
            onClick = {
                lyricSettings.value = lyricSettings.value.copy(
                    translationVisible = !lyricSettings.value.translationVisible,
                )
                lyricSettings.save()
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = vectorResource(RemixIcon.Editor.translate2),
                contentDescription = "翻译",
                modifier = Modifier.graphicsLayer { alpha = translationAlpha.value },
                tint = contentColor(),
            )
        }
    }
}

private fun showLyricQuickSettings() {
    DialogWrapper.push(
        DialogItem.Dynamic(
            backgroundColor = Color.Transparent,
            // 设置项较多，直接完全展开，避免半屏状态下内容被裁剪。
            skipPartiallyExpanded = true,
            content = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreenContent(
                        groups = listOf(
                            provideLyricSettingsQuick(fontManager = koinInject()),
                        ),
                        showNavigatorHeader = false,
                        // 弹层不应用页面级状态栏和 SmartBar inset。
                        contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
                    )
                }
            },
        ),
    )
}
