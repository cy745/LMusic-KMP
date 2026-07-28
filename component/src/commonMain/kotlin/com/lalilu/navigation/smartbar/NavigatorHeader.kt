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