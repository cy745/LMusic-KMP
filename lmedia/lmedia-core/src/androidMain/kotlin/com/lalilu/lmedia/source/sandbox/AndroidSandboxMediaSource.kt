package com.lalilu.lmedia.source.sandbox

import android.app.Application
import androidx.core.net.toUri
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import org.koin.core.annotation.Single
import java.io.File

@Single(binds = [MediaSource::class, MediaDataSource::class])
class AndroidSandboxMediaSource(
    application: Application,
) : AbstractSandboxMediaSource(
    rootDirectory = PlatformFile(File(application.filesDir, "media-sandbox")),
    workingDirectory = PlatformFile(File(application.cacheDir, "external-audio-import")),
) {
    override fun buildPlaybackUrl(file: PlatformFile): String =
        File(file.path).toUri().toString()
}
