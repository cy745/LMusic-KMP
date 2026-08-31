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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    PlayingToolbar(
        modifier = modifier,
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
