package com.lalilu.lhome.extensions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.component.LazyGridContent
import com.lalilu.component.rememberGridItemPadding
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.actualScreen
import com.lalilu.remixicon.System


/**
 * 极简主义 + Swiss Style 风格的 EntryPanel
 *
 * 设计原则：
 *  - 无 Surface 边框/阴影，仅在按下时显示极淡背景色
 *  - 图标 + 文字竖排，24dp 图标 + 14sp labelMedium
 *  - 44dp 最小 touch target (满足 mobile 触摸标准)
 *  - press 状态用 alpha 0.6 实现 200ms 过渡
 *  - 4dp 圆角，2 列网格，gapVertical = 16dp（Swiss 风格）
 *  - 文字 onBackground.copy(0.6f) 静态 + onBackground 激活/按下
 *  - 图标颜色与文字一致，按下时 primary 高亮
 */
object EntryPanel : LazyGridContent {

    val screenEntry = mutableStateOf<List<Screen>>(emptyList())

    private data class EntryMeta(val title: String, val icon: ImageVector)

    @Composable
    private fun metaOf(screen: Screen): EntryMeta {
        val actual = screen.actualScreen()
        return if (actual is ScreenInfoFactory) {
            val info = actual.provideScreenInfo()
            EntryMeta(
                title = info.title(),
                icon = info.icon ?: RemixIcon.System.historyLine
            )
        } else {
            val name = actual::class.simpleName?.removeSuffix("Screen") ?: screen.key
            EntryMeta(name, RemixIcon.System.historyLine)
        }
    }

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        // Swiss 风格：8dp gap（而不是 8dp）
        val gridItemPaddings = rememberGridItemPadding(
            count = 2,
            gapVertical = 8.dp,
            gapHorizontal = 8.dp,
            paddingValues = PaddingValues(horizontal = 16.dp)
        )

        LaunchedEffect(Unit) {
            if (screenEntry.value.isEmpty()) {
                screenEntry.value = listOf(
                    "/pages/songs",
                    "/pages/artists",
                    "/pages/albums",
                    "/pages/history",
                    "/media_source",
                    "/log"
                ).mapNotNull { AppRouter.route(it).get() }
            }
        }

        return fun LazyGridScope.() {
            itemsIndexed(
                items = screenEntry.value,
                key = { index, item -> item.key },
                contentType = { index, item -> this@EntryPanel::class.qualifiedName },
                span = { index, item -> GridItemSpan(maxLineSpan / 2) }
            ) { index, item ->
                val meta = metaOf(item)
                EntryItem(
                    modifier = Modifier.padding(gridItemPaddings(index)),
                    title = meta.title,
                    icon = meta.icon,
                    onClick = { AppRouter.intent(NavIntent.Jump(item)) }
                )
            }
        }
    }
}

/**
 * Swiss Style + Visible Shape 的 Entry item：
 *  - 1dp 极淡边框 + 浅色底，显示按钮形状 + 填充空白
 *  - 按下时背景色加深、边框变成 primary
 *  - 8dp 圆角，88dp 高度满足最小 touch target
 *  - 图标 + 标题 左对齐垂直堆叠
 *  - alpha 0.85f 的 border + alpha 0.5f 的 background
 */
@Composable
private fun EntryItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        label = "entry-item-press-alpha"
    )

    val borderColor = if (isPressed)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

    val containerColor = if (isPressed)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    else
        MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 16.dp)
            .alpha(pressedAlpha)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}