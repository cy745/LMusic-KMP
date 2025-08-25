package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.entity.Snapshot
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow

class SubsonicSource : MediaSource {
    override val name: String = "SubsonicSource"
    private val client = HttpClient()

    override fun source(): Flow<Snapshot> {
        return super.source()
    }

    @Composable
    override fun Content(modifier: Modifier) {
        super.Content(modifier)
    }
}