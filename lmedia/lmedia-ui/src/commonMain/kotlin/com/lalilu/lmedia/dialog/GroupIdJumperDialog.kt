package com.lalilu.lmedia.dialog


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.extensions.DialogItem
import com.lalilu.extensions.DialogWrapper
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lmedia.sortable.Sortable

@Composable
fun <T : Sortable> GroupIdJumperDialog(
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    sortResult: SortResult<T>,
    onSelectItem: (item: GroupId) -> Unit = {}
) {
    val items = rememberUpdatedState(sortResult)

    val dialog = remember {
        DialogItem.Dynamic(backgroundColor = Color.Transparent) {
            GroupIdJumperDialogContent(
                items = { items.value.groups.mapNotNull { it.groupId } },
                onDismiss = ::dismiss,
                onSelectItem = {
                    onSelectItem(it)
                    dismiss()
                }
            )
        }
    }

    DialogWrapper.register(
        isVisible = isVisible,
        onDismiss = onDismiss,
        dialogItem = dialog
    )
}

@Composable
private fun GroupIdJumperDialogContent(
    modifier: Modifier = Modifier,
    items: () -> Collection<GroupId>,
    onSelectItem: (item: GroupId) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val charMapping = remember(items()) {
        items().filter { it.text.isNotBlank() }
            .groupBy { it.text[0].category }
    }


    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .statusBarsPadding()
//                .verticalFadingEdges(
//                    gravity = FadingEdgesGravity.Start,
//                    length = 100.dp,
//                    fillType = FadingEdgesFillType.FadeClip()
//                )
//                .verticalFadingEdges(
//                    gravity = FadingEdgesGravity.End,
//                    length = 24.dp,
//                    fillType = FadingEdgesFillType.FadeClip()
//                )
        ,
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = navigationBarsPadding.calculateBottomPadding() + 32.dp + 64.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        charMapping.forEach { (key, value) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    color = Color.White,
                    text = i18nForCharCategory(key)
                )
            }

            items(
                items = value,
                key = { it },
                span = { GridItemSpan(maxLineSpan / 6) }
            ) {
                FilterChip(
                    modifier = Modifier.aspectRatio(1f),
                    shape = RoundedCornerShape(4.dp),
                    selected = it == GroupId.None,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
                    onClick = { onSelectItem(it) },
                    label = {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            text = it.text
                        )
                    }
                )
            }
        }
    }
}

@Stable
@Composable
private fun i18nForCharCategory(category: CharCategory): String {
    // TODO 待完善多语言
    return when (category) {
        CharCategory.MATH_SYMBOL -> "数学符号"
        CharCategory.CURRENCY_SYMBOL -> "货币符号"
        CharCategory.DECIMAL_DIGIT_NUMBER -> "数字"
        CharCategory.LOWERCASE_LETTER -> "小写字母"
        CharCategory.UPPERCASE_LETTER -> "大写字母"
        CharCategory.TITLECASE_LETTER -> "标题字母"
        CharCategory.MODIFIER_LETTER -> "修饰字母"
        else -> "其他符号"
    }
}