/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lmedia.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import com.lalilu.extensions.DialogItem
import com.lalilu.extensions.DialogWrapper
import com.lalilu.lmedia.sortable.SortAction
import com.lalilu.lmedia.sortable.SortConfig
import com.lalilu.lmedia.sortable.SortRuleNormal


@Composable
fun SortPanelDialog(
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    supportSortActions: Collection<SortAction>,
    sortConfig: () -> SortConfig,
    onUpdateSortConfig: (SortConfig) -> Unit,
    selectedSortAction: () -> SortAction?,
    onSelectSortAction: (SortAction) -> Unit
) {
    val dialog = remember {
        DialogItem.Dynamic(backgroundColor = Color.Transparent) {
            SortPanelDialogContent(
                supportSortActions = supportSortActions,
                selectedSortAction = selectedSortAction,
                onSelectSortAction = onSelectSortAction,
                sortConfig = sortConfig,
                onUpdateSortConfig = onUpdateSortConfig,
                onDismiss = { dismiss() }
            )
        }
    }

    DialogWrapper.register(
        isVisible = isVisible,
        onDismiss = onDismiss,
        dialogItem = dialog
    )
}

@OptIn(ExperimentalFlexBoxApi::class)
@Composable
private fun SortPanelDialogContent(
    modifier: Modifier = Modifier,
    supportSortActions: Collection<SortAction>,
    selectedSortAction: () -> SortAction? = { null },
    onSelectSortAction: (SortAction) -> Unit = {},
    sortConfig: () -> SortConfig = { SortConfig() },
    onUpdateSortConfig: (SortConfig) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color(0xFF029DF3),
        selectedLabelColor = Color.Black,
        labelColor = MaterialTheme.colorScheme.onBackground,
        containerColor = MaterialTheme.colorScheme.onSurface
            .compositeOver(MaterialTheme.colorScheme.surface)
            .copy(alpha = 0.05f)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .navigationBarsPadding(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        VerticalGrid(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            columns = SimpleGridCells.Fixed(6)
        ) {
            val needSpanIndex = remember(supportSortActions) {
                if (supportSortActions.size % 2 == 1) supportSortActions.indices.last else -1
            }

            supportSortActions.forEachIndexed { index, sortAction ->
                val info = sortAction.getActionInfo()

                SortItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .span { if (needSpanIndex == index) 6 else 3 },
                    title = info.title,
                    subTitle = info.subTitle ?: "",
                    colors = colors,
                    selected = { selectedSortAction() == sortAction },
                    onClick = { onSelectSortAction(sortAction) }
                )
            }

            Spacer(
                modifier = Modifier
                    .span { 6 }
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.05f))
            )

            SortItem(
                modifier = Modifier.span { 2 },
                title = "取消",
                center = true,
                selected = { true },
                onClick = { onDismiss() },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x52EF0606),
                    selectedLabelColor = Color(0xFFEF0606),
                ),
            )
            SortItem(
                modifier = Modifier.span { 2 },
                title = "隐藏分组",
                center = true,
                selected = { sortConfig().hideGroup },
                onClick = { onUpdateSortConfig(sortConfig().let { it.copy(hideGroup = !it.hideGroup) }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x523F51B5),
                    selectedLabelColor = Color(0xFF3F51B5),
                    labelColor = MaterialTheme.colorScheme.onBackground,
                    containerColor = MaterialTheme.colorScheme.onSurface
                        .compositeOver(MaterialTheme.colorScheme.surface)
                        .copy(alpha = 0.05f)
                ),
            )
            SortItem(
                modifier = Modifier.span { 2 },
                title = "顺序倒转",
                center = true,
                selected = { sortConfig().reverse },
                onClick = { onUpdateSortConfig(sortConfig().let { it.copy(reverse = !it.reverse) }) },
                colors = FilterChipDefaults.filterChipColors(
                    disabledContainerColor = Color.LightGray,
                    selectedContainerColor = Color(0x526A10F5),
                    selectedLabelColor = Color(0xFF6A10F5),
                    labelColor = MaterialTheme.colorScheme.onBackground,
                    containerColor = MaterialTheme.colorScheme.onSurface
                        .compositeOver(MaterialTheme.colorScheme.surface)
                        .copy(alpha = 0.05f)
                ),
            )
        }
    }
}

@Composable
private fun SortItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    subTitle: String? = "",
    center: Boolean = false,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    selected: () -> Boolean,
    onClick: () -> Unit = {}
) {
    FilterChip(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = colors,
        enabled = enabled,
        shape = RoundedCornerShape(5.dp),
        selected = selected(),
        border = null,
        onClick = onClick,
        label = {
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    text = title,
                    textAlign = if (center) TextAlign.Center else TextAlign.Start,
                    color = LocalContentColor.current
                )

                subTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = it,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = LocalContentColor.current.copy(0.5f)
                    )
                }
            }
        }
    )
}

@Preview(
    showSystemUi = false,
    showBackground = true,
)
@Composable
private fun SongsSortPanelDialogPVDay() {
    SortPanelDialogContent(
        supportSortActions = setOf(SortRuleNormal())
    )
}

@Preview(
    showSystemUi = false,
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
@Composable
private fun SongsSortPanelDialogPV() {
    SortPanelDialogContent(
        supportSortActions = setOf(SortRuleNormal())
    )
}