package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow

class WebDavSource : MediaSource {
    override val name: String = "WebDavSource"

    override fun source(): Flow<Snapshot> {
        return super.source()
    }

    @Composable
    override fun Content(modifier: Modifier) {

    }
}