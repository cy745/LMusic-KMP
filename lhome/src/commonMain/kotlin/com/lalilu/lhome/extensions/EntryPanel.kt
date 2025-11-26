package com.lalilu.lhome.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyGridContent
import com.lalilu.component.rememberGridItemPadding
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent


object EntryPanel : LazyGridContent {
    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val screenEntry = remember {
            val targetScreen = listOf(
                "/pages/songs",
                "/pages/artists",
                "/pages/albums",
                "/pages/history",
                "/media_source",
                "/log"
            )
            targetScreen.mapNotNull { AppRouter.route(it).get() }
        }
        val gridItemPaddings = rememberGridItemPadding(
            count = 2,
            gapVertical = 8.dp,
            gapHorizontal = 8.dp,
            paddingValues = PaddingValues(horizontal = 16.dp)
        )

        return fun LazyGridScope.() {
            itemsIndexed(
                items = screenEntry,
                key = { index, item -> item.key },
                contentType = { index, item -> this@EntryPanel::class.qualifiedName },
                span = { index, item -> GridItemSpan(maxLineSpan / 2) }
            ) { index, item ->
//                val title = infoFactory?.title?.invoke() ?: defaultString
//                val icon = infoFactory?.icon

                Surface(
                    modifier = Modifier.padding(gridItemPaddings(index)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AppRouter.intent(NavIntent.Jump(item)) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
//                        icon?.let { icon ->
//                            Icon(
//                                imageVector = icon,
//                                contentDescription = title,
//                                tint = MaterialTheme.colorScheme.onBackground.copy(0.7f)
//                            )
//                        }

                        Text(
                            text = item.key,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}