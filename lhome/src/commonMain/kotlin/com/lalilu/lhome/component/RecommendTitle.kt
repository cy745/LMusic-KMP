package com.lalilu.lhome.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.preview.preview
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowRightSLine
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RecommendTitle(
    modifier: Modifier = Modifier,
    title: String,
    paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp),
    extraContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(paddingValues),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        extraContent()
    }
}

@Preview
@Composable
private fun RecommendTitlePreview() = preview {
    Column {
        RecommendTitle(
            modifier = Modifier.fillMaxWidth(),
            title = "Recommend",
        ) {
            Icon(
                imageVector = RemixIcon.Arrows.arrowRightSLine,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}