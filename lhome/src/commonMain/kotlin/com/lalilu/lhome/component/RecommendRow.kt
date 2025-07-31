package com.lalilu.lhome.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <I> RecommendRow(
    modifier: Modifier = Modifier,
    items: () -> List<I>,
    getId: (I) -> Any,
    itemContent: @Composable LazyItemScope.(item: I) -> Unit,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = SpringSpec(stiffness = Spring.StiffnessLow)),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        items(
            items = items(),
            key = getId,
            itemContent = itemContent
        )
    }
}
