/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.navigation.smartbar

import androidx.compose.animation.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowLeftSLine


/**
 * 通用导航栏，显示返回按钮和屏幕操作
 */
@Composable
fun NavigateCommonBar(
    modifier: Modifier = Modifier,
    previousScreenTitle: String?,
    screenActions: () -> List<ScreenAction>? = { emptyList() },
    onBackPress: (() -> Unit)? = null
) {
    val screenActions = screenActions()
    val actionContext = remember { ActionContext(isFullyExpanded = false) }
    var isDialogVisible by remember { mutableStateOf(false) }

    NavigateCommonBarContent(
        modifier = modifier,
        backActionBtn = remember(previousScreenTitle) {
            previousScreenTitle?.let {
                BackActionBtn(
                    text = previousScreenTitle,
                    icon = RemixIcon.Arrows.arrowLeftSLine
                )
            }
        },
        dialogVisible = isDialogVisible,
        onDialogVisibilityChange = { isDialogVisible = it },
        screenActions = screenActions,
        actionContext = actionContext,
        onBackPress = onBackPress
    )
}

data class BackActionBtn(
    val text: String,
    val icon: ImageVector = RemixIcon.Arrows.arrowLeftSLine
)

/**
 * 通用导航栏内容
 */
@Composable
fun NavigateCommonBarContent(
    modifier: Modifier = Modifier,
    backActionBtn: BackActionBtn? = null,
    dialogVisible: Boolean,
    onDialogVisibilityChange: (Boolean) -> Unit,
    screenActions: List<ScreenAction>?,
    actionContext: ActionContext,
    onBackPress: (() -> Unit)? = null
) {
    MoreActionPanelDialog(
        isVisible = dialogVisible,
        onDismiss = { onDialogVisibilityChange(false) },
        actions = screenActions ?: emptyList()
    )

    AnimatedContent(
        modifier = modifier.fillMaxHeight(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        targetState = backActionBtn to screenActions,
        label = "ExtraActions"
    ) { (backAction, actions) ->
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = backAction != null) {
                TextButton(
                    modifier = Modifier.fillMaxHeight(),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(start = 8.dp, end = 16.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    onClick = {
                        onBackPress?.invoke()
                    }
                ) {
                    Icon(
                        imageVector = backAction?.icon ?: RemixIcon.Arrows.arrowLeftSLine,
                        tint = MaterialTheme.colorScheme.onBackground,
                        contentDescription = null
                    )
                    Text(
                        text = backAction?.text ?: "返回",
                        fontSize = 14.sp
                    )
                }
            }


            SubcomposeLayout(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) { constraints ->
                // 若actions为空，则不显示
                if (actions.isNullOrEmpty()) {
                    return@SubcomposeLayout layout(0, 0) {}
                }

                val moreBtnMeasurable = subcompose("moreBtn") {
                    val colors = screenActions?.filterIsInstance<ScreenAction.Static>()
                        ?.mapNotNull { it.dotColor() }
                        ?: emptyList()

                    MoreActionBtn(
                        dotColors = colors,
                        onClick = { onDialogVisibilityChange(true) },
                    )
                }[0]
                val moreBtnPlaceable = moreBtnMeasurable.measure(
                    constraints.copy(
                        maxWidth = moreBtnMeasurable.maxIntrinsicWidth(constraints.maxWidth),
                        minWidth = 0
                    )
                )

                var widthSum = 0f
                val targets = mutableListOf<Placeable>()
                for (action in actions) {
                    val measurable = subcompose(action) {
                        ActionItem(
                            action = action,
                            actionContext = actionContext
                        )
                    }[0]
                    val placeable = measurable.measure(
                        constraints.copy(
                            maxWidth = measurable.maxIntrinsicWidth(constraints.maxWidth),
                            minWidth = 0
                        )
                    )

                    // 若宽度超出，则显示下拉菜单按钮
                    if (placeable.width + moreBtnPlaceable.width + widthSum > constraints.maxWidth) {
                        targets.add(moreBtnPlaceable)
                        break
                    }

                    targets.add(placeable)
                    widthSum += placeable.width
                }

                layout(width = constraints.maxWidth, height = constraints.maxHeight) {
                    var startX = constraints.maxWidth

                    targets.reversed().forEach {
                        it.place(x = startX - it.width, y = 0)
                        startX -= it.width
                    }
                }
            }
        }
    }
}
