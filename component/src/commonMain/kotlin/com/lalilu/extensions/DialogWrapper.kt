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

package com.lalilu.extensions

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class DialogItem {
    data class Static(
        val title: String,
        val message: String,
        val backgroundColor: Color? = null,
        val onConfirm: () -> Unit = {},
        val onCancel: () -> Unit = {},
        val onDismiss: () -> Unit = {},
    ) : DialogItem()

    data class Dynamic(
        val backgroundColor: Color? = null,
        val onDismiss: () -> Unit = {},
        val content: @Composable DialogContext.() -> Unit,
    ) : DialogItem()
}

interface DialogHost {
    @Composable
    fun Content()
    fun push(dialogItem: DialogItem)

    @Composable
    fun register(
        isVisible: () -> Boolean,
        onDismiss: () -> Unit,
        dialogItem: DialogItem
    )
}

interface DialogContext {
    fun dismiss()
    fun isVisible(): Boolean
}

object DialogWrapper : DialogHost, DialogContext {
    private var dialogItem by mutableStateOf<DialogItem?>(null)
    private var dismissFunc by mutableStateOf<(() -> Unit)?>(null)

    override fun isVisible(): Boolean = dialogItem != null
    override fun dismiss(): Unit = run { dismissFunc?.invoke() }

    override fun push(dialogItem: DialogItem) {
        this.dialogItem = dialogItem
    }

    @Composable
    override fun register(
        isVisible: () -> Boolean,
        onDismiss: () -> Unit,
        dialogItem: DialogItem
    ) {
        LaunchedEffect(Unit) {
            snapshotFlow { this@DialogWrapper.dialogItem }
                .collectLatest {
                    if (it != null || !isVisible()) return@collectLatest
                    onDismiss()
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { isVisible() }
                .collectLatest { visible ->
                    if (visible) {
                        this@DialogWrapper.dialogItem = dialogItem
                        return@collectLatest
                    }

                    this@DialogWrapper.dialogItem ?: return@collectLatest
                    dismiss()
                }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        if (dialogItem == null) return

        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState().also { state ->
            dismissFunc = {
                scope.launch {
                    state.hide()
                    dialogItem?.let { item ->
                        when (item) {
                            is DialogItem.Dynamic -> item.onDismiss.invoke()
                            is DialogItem.Static -> item.onDismiss.invoke()
                        }
                    }
                    dialogItem = null
                }
            }
        }

        val backgroundColor = remember(dialogItem) {
            dialogItem?.let {
                when (it) {
                    is DialogItem.Dynamic -> it.backgroundColor
                    is DialogItem.Static -> it.backgroundColor
                }
            }
        }

        ModalBottomSheet(
            modifier = Modifier,
            onDismissRequest = {
                dialogItem?.let {
                    when (it) {
                        is DialogItem.Dynamic -> it.onDismiss.invoke()
                        is DialogItem.Static -> it.onDismiss.invoke()
                    }
                }
                dialogItem = null
            },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            sheetMaxWidth = 540.dp,
            content = {
                AnimatedContent(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .background(color = backgroundColor ?: MaterialTheme.colorScheme.background),
                    targetState = dialogItem,
                    label = "",
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            initialOffsetY = { (it * 1.2f).roundToInt() }
                        ) togetherWith slideOutVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
                            targetOffsetY = { (it * 1.2f).roundToInt() }
                        ) + scaleOut(
                            animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
                            targetScale = 0.6f
                        ) + fadeOut()
                    }
                ) { dialog ->
                    dialog?.apply {
                        when (this) {
                            is DialogItem.Static -> {
                                StaticDialogCard(
                                    title = title,
                                    message = message,
                                    onConfirm = { dismiss(); onConfirm() },
                                    onCancel = { dismiss(); onCancel() }
                                )
                            }

                            is DialogItem.Dynamic -> {
                                content.invoke(this@DialogWrapper)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun StaticDialogCard(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    cancelText: String = "取消",
    confirmText: String = "确认",
    onCancel: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.3f)
            )
        }

        extraContent()

        if (onCancel != null || onConfirm != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val cancelColor = remember { Color(0xFFFF7575) }
                val confirmColor = remember { Color(0xFF258302) }

                onCancel?.let {
                    TextButton(
                        shape = RectangleShape,
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .widthIn(min = 84.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = cancelColor.copy(alpha = 0.15f),
                            contentColor = cancelColor
                        ),
                        onClick = onCancel
                    ) {
                        Text(text = cancelText)
                    }
                }

                onConfirm?.let {
                    TextButton(
                        shape = RectangleShape,
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .widthIn(min = 84.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = confirmColor.copy(alpha = 0.15f),
                            contentColor = confirmColor
                        ),
                        onClick = onConfirm
                    ) {
                        Text(text = confirmText)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StaticCardDialogPreview() {
    StaticDialogCard(
        title = "是否需要删除文件{}",
        message = "确认删除吗？",
        onCancel = {},
        onConfirm = {}
    )
}