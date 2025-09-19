package com.lalilu.llyricview.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricContext
import com.lalilu.llyricview.LyricItemLayout
import com.lalilu.llyricview.LyricSettings
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Named("LyricFixedTipsContent")
@Single(createdAtStart = true)
class LyricFixedTipsContent : LyricItemLayout<LyricItem.FixedTips> {

    init {
        LyricItemLayout.set(LyricItem.FixedTips::class, this)
    }

    @Composable
    override fun content(
        index: Int,
        item: LyricItem.FixedTips,
        modifier: Modifier,
        settings: LyricSettings,
        context: LyricContext,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?
    ) {
        LyricFixedTips(
            index = index,
            item = item,
            modifier = modifier,
            settings = settings,
            context = context,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}


@Composable
fun LyricFixedTips(
    index: Int,
    item: LyricItem.FixedTips,
    modifier: Modifier = Modifier,
    settings: LyricSettings,
    context: LyricContext,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
            .padding(settings.containerPadding),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.content,
            style = settings.translationTextStyle,
            color = Color(0x80FFFFFF)
        )
    }
}