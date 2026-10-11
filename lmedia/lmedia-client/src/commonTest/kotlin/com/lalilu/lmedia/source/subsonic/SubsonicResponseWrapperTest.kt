package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubsonicResponseWrapperTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun preservesTheEnvelopeAndConcreteResponseSerializer() {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = SubsonicResponseWrapper.serializer(TestResponse.serializer())
        val input = """{"subsonic-response":{"status":"ok","version":"1.16.1","payload":"song","extra":1}}"""
        val value = json.decodeFromString(serializer, input)
        assertTrue(value.response.isSuccess)
        assertEquals("1.16.1", value.response.version)
        assertEquals("song", value.response.payload)
        val encoded = json.encodeToString(serializer, value)
        assertEquals(setOf("subsonic-response"), json.parseToJsonElement(encoded).jsonObject.keys)
        assertEquals("song", json.decodeFromString(serializer, encoded).response.payload)
        assertEquals("com.lalilu.lmedia.source.subsonic.SubsonicResponseWrapper", serializer.descriptor.serialName)
    }

    @Test
    fun rejectsAMissingOrNullResponse() {
        val serializer = SubsonicResponseWrapper.serializer(SubsonicResponse.serializer())
        assertFailsWith<SerializationException> { Json.decodeFromString(serializer, "{}") }
        assertFailsWith<SerializationException> { Json.decodeFromString(serializer, """{"subsonic-response":null}""") }
    }
}

@Serializable
private class TestResponse(val payload: String) : SubsonicResponse()
