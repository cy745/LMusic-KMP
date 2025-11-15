package com.lalilu.lhome.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SongInformationCard(
    modifier: Modifier = Modifier,
    extra: Map<String, String>
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            extra.forEach {
                ColumnItem(
                    title = it.key,
                    content = it.value,
                )
            }
        }
    }
}

@Composable
fun ColumnItem(
    modifier: Modifier = Modifier,
    title: String,
    content: String,
    maxLines: Int = 5,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    showBorder: Boolean = true
) {
    val clipboard = LocalClipboard.current
    val contentColor = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (showBorder) {
                    drawLine(
                        color = contentColor.copy(0.15f),
                        start = Offset(16.dp.toPx(), this.size.height),
                        end = Offset(this.size.width - 16.dp.toPx(), this.size.height),
                        cap = StrokeCap.Round
                    )
                }
            }
            .combinedClickable(
                onLongClick = {
//                    clipboard.setClipEntry(ClipEntry(""))
//                    clipboard.setText(buildAnnotatedString { append(content) })
//                    ToastUtils.showShort("复制成功")
                },
                onClick = {
//                    ToastUtils.showShort("长按复制元素内容")
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = MaterialTheme.typography.titleMedium.fontSize
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .alpha(0.9f),
            text = content,
            textAlign = TextAlign.End,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}