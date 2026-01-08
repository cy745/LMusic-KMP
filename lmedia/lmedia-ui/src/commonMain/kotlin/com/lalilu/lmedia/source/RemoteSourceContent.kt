package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.common.ext.io
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.Dispatchers

@Composable
fun RemoteSource.RemoteSourceContent(modifier: Modifier) {
    val source by remember { source() }.collectAsState(
        initial = Snapshot.Empty,
        context = Dispatchers.io
    )

    SourceCard(
        modifier = modifier,
        state = { source }
    )
}

