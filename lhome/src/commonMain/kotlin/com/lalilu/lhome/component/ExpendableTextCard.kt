package com.lalilu.lhome.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp


/**
 * 此组件的效果时在一定的宽度内保证文字单行显示，若无法在单行内显示完全则会省略显示对应溢出的部分
 *
 * 最好使用Compose的 [IntrinsicSize] 来使父级设定按最小宽度布局，以此为本组件满足 “在一定的宽度内” 的条件前提
 *
 * @param title             主文字
 * @param subTitle          次要文字
 * @param defaultState      初始化时是否展开文字
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ExpendableTextCard(
    modifier: Modifier = Modifier,
    maxWidth: () -> Dp = { Dp.Infinity },
    title: () -> String,
    subTitle: () -> String?,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subTitleColor: Color = titleColor.copy(alpha = 0.5f),
    defaultState: Boolean = false
) {
    var expendedState by remember { mutableStateOf(defaultState) }

    AnimatedContent(
        modifier = modifier,
        targetState = expendedState
    ) { expended ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth())
                .clickable(
                    interactionSource = null,
                    indication = null
                ) {
                    expendedState = !expended
                }
        ) {
            Text(
                text = title(),
                softWrap = true,
                maxLines = if (expended) Int.MAX_VALUE else 1,
                overflow = if (expended) TextOverflow.Visible else TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = titleColor
            )
            subTitle()?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    softWrap = true,
                    maxLines = if (expended) Int.MAX_VALUE else 1,
                    overflow = if (expended) TextOverflow.Visible else TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = subTitleColor
                )
            }
        }
    }
}