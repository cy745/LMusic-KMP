package com.lalilu.lmedia.source.sandbox

import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import org.koin.core.annotation.Single
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** iOS Documents-backed sandbox. Its source name and ID calculation stay legacy-compatible. */
@Single(binds = [MediaSource::class, MediaDataSource::class])
class SandboxFileSystemSource : AbstractSandboxMediaSource(
    rootDirectory = platformDirectory(NSDocumentDirectory),
    workingDirectory = PlatformFile(platformDirectory(NSCachesDirectory), "LMusicExternalImport"),
) {
    override fun buildPlaybackUrl(file: PlatformFile): String =
        file.nsUrl.absoluteString ?: error("Unable to build file URL for ${file.path}")
}

private fun platformDirectory(directory: ULong): PlatformFile {
    val path = NSSearchPathForDirectoriesInDomains(
        directory = directory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: error("Apple sandbox directory is unavailable: $directory")
    return PlatformFile(NSURL.fileURLWithPath(path))
}
