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

package com.lalilu.navigation.smartbar


import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigatorHeader(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String,
    paddingValues: PaddingValues = PaddingValues(
        top = 16.dp,
        bottom = 16.dp,
        start = 20.dp,
        end = 20.dp
    ),
    columnExtraSpace: Dp = 4.dp,
    rowExtraSpace: Dp = 12.dp,
    extraContent: @Composable RowScope.() -> Unit = {}
) = NavigatorHeader(
    modifier = modifier,
    title = title,
    columnExtraSpace = columnExtraSpace,
    rowExtraSpace = rowExtraSpace,
    rowExtraContent = extraContent,
    paddingValues = paddingValues,
    columnExtraContent = {
        if (subTitle.isNotBlank()) {
            Text(
                text = subTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = 0.5f)
            )
        }
    }
)

@Composable
fun NavigatorHeader(
    modifier: Modifier = Modifier,
    title: String,
    paddingValues: PaddingValues = PaddingValues(
        top = 26.dp,
        bottom = 20.dp,
        start = 20.dp,
        end = 20.dp
    ),
    columnExtraSpace: Dp = 4.dp,
    rowExtraSpace: Dp = 12.dp,
    columnExtraContent: @Composable ColumnScope.() -> Unit = {},
    rowExtraContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.padding(paddingValues),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(rowExtraSpace),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(columnExtraSpace)
        ) {
            Text(
                modifier = Modifier,
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            columnExtraContent()
        }
        rowExtraContent()
    }
}