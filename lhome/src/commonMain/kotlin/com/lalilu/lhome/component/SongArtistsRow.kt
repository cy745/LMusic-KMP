package com.lalilu.lhome.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.entity.LArtist

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongArtistsRow(
    modifier: Modifier = Modifier,
    artists: Set<LArtist>
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        artists.forEach {

        }
    }
}