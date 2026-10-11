package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SubsonicResponseWrapperSerializer::class)
data class SubsonicResponseWrapper<T : SubsonicResponse>(
    @SerialName("subsonic-response")
    val response: T
)

// Kotlin 2.4.20 emits invalid Wasm for generated serializers with a class upper bound
// (KT-89275, fixed in 2.4.21). Keep the public bound; generate serialization for an
// unbounded wire surrogate instead. The explicit construction retains the correct cast.
internal class SubsonicResponseWrapperSerializer<T : SubsonicResponse>(serializer: KSerializer<T>) :
    KSerializer<SubsonicResponseWrapper<T>> {
    private val delegate = SubsonicResponseEnvelope.serializer(serializer)
    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: SubsonicResponseWrapper<T>) =
        delegate.serialize(encoder, SubsonicResponseEnvelope(value.response))

    override fun deserialize(decoder: Decoder): SubsonicResponseWrapper<T> =
        SubsonicResponseWrapper(delegate.deserialize(decoder).response)
}

@Serializable
@SerialName("com.lalilu.lmedia.source.subsonic.SubsonicResponseWrapper")
private data class SubsonicResponseEnvelope<T>(
    @SerialName("subsonic-response") val response: T,
)
