package com.lalilu.lplayer.core

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/** AVFoundation's asynchronous byte bridge; cache, credentials and fill policy stay shared. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosCachedAsset(private val access: NativeContentAccess, private val uri: String) :
    NSObject(), AVAssetResourceLoaderDelegateProtocol {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = mutableMapOf<AVAssetResourceLoadingRequest, Job>()
    val asset = AVURLAsset(NSURL.URLWithString(uri)!!, null).also {
        it.resourceLoader.setDelegate(this, dispatch_get_main_queue())
    }

    @ObjCSignatureOverride
    override fun resourceLoader(resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest): Boolean {
        val request = shouldWaitForLoadingOfRequestedResource
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var handle: com.lalilu.lmedia.content.ContentHandle? = null
            try {
                handle = access.open(uri)
                val size = handle.descriptor.size
                request.contentInformationRequest?.let { info ->
                    val header = handle.read(0, minOf(16L, size).toInt())
                    info.contentLength = size
                    info.byteRangeAccessSupported = true
                    info.contentType = when {
                        header.decodeToString().startsWith("RIFF") -> "com.microsoft.waveform-audio"
                        header.decodeToString().startsWith("fLaC") -> "org.xiph.flac"
                        header.decodeToString().startsWith("FORM") -> "public.aiff-audio"
                        header.decodeToString().startsWith("OggS") -> "org.xiph.ogg"
                        header.size >= 8 && header.copyOfRange(4, 8).decodeToString() == "ftyp" -> "public.mpeg-4-audio"
                        header.size >= 2 && header[0].toInt() and 255 == 255 && header[1].toInt() and 246 == 240 -> "public.aac-audio"
                        else -> "public.mp3"
                    }
                }
                request.dataRequest?.let { data ->
                    var offset = maxOf(data.requestedOffset, data.currentOffset)
                    val end = if (data.requestsAllDataToEndOfResource) size else minOf(size, data.requestedOffset + data.requestedLength)
                    while (offset < end) {
                        ensureActive()
                        val bytes = handle.read(offset, minOf(64 * 1024L, end - offset).toInt())
                        check(bytes.isNotEmpty()) { "Unexpected content end" }
                        bytes.usePinned { data.respondWithData(NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong())) }
                        offset += bytes.size
                    }
                }
                if (!request.cancelled) request.finishLoading()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!request.cancelled) request.finishLoadingWithError(NSError.errorWithDomain("LMusicContent", 1, null))
            } finally {
                withContext(NonCancellable) { handle?.close() }
                requests.remove(request)
            }
        }
        requests[request] = job
        job.start()
        return true
    }

    @ObjCSignatureOverride
    override fun resourceLoader(resourceLoader: AVAssetResourceLoader, didCancelLoadingRequest: AVAssetResourceLoadingRequest) {
        requests.remove(didCancelLoadingRequest)?.cancel()
    }

    fun close() { asset.resourceLoader.setDelegate(null, null); scope.cancel(); requests.clear() }
}
