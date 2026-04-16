package com.lalilu.lplayer.notification


external interface MediaSession : JsAny {
    fun setActionHandler(action: String, handler: () -> Unit)
}

fun mediaSessionOrNull(): MediaSession? = js("navigator.mediaSession")

fun createMetadata(
    title: String, artist: String, album: String,
): JsAny? = js(
    "new MediaMetadata({title: title, artist: artist, album: album, artwork: []})"
)

fun createMetadataWithArtwork(
    title: String,
    artist: String,
    album: String,
    url: String,
    size: String,
    type: String
): JsAny? = js(
    "new MediaMetadata({title: title, artist: artist, album: album, artwork: [{ src: url, sizes: size, type: type}]})"
)

fun setMetadata(metadata: JsAny): Unit = js("navigator.mediaSession.metadata = metadata")