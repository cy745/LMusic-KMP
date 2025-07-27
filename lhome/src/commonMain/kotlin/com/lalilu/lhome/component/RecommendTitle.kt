package com.lalilu.lhome.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowRightSLine
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RecommendTitle(
    modifier: Modifier = Modifier,
    title: String,
    paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp),
    onClick: () -> Unit = {},
    extraContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(paddingValues),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        extraContent()
    }
}

@Preview
@Composable
private fun RecommendTitle() {
    RecommendTitle(
        title = "Recommend",
    ) {
        Icon(
            imageVector = RemixIcon.Arrows.arrowRightSLine,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}