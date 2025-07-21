package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class WebDavSource : MediaSource {
    override val name: String = "WebDavSource"

    override fun source(): Flow<Snapshot> {
        return emptyFlow()
    }

    @Composable
    override fun Content(modifier: Modifier) {

    }
}