package com.lalilu.lmusic.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkParserTest {
    @Test
    fun `push supports path route and navigation controls`() {
        val command = assertIs<DeepLinkCommand.Navigate>(
            DeepLinkParser.parse(
                "lmusic://push/pages/artists?keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6&single_top=true"
            )
        )

        assertEquals(NavigationOperation.Push, command.operation)
        assertEquals("/pages/artists", command.route)
        assertEquals("周杰伦", command.params["keyword"])
        assertTrue(command.singleTop)
        assertFalse(command.singleInstance)
    }

    @Test
    fun `replace supports query route and list params`() {
        val command = assertIs<DeepLinkCommand.Navigate>(
            DeepLinkParser.parse(
                "lmusic://replace?route=/playlist/add&mediaIds[]=1&mediaIds[]=2&single_instance=true"
            )
        )

        assertEquals(NavigationOperation.Replace, command.operation)
        assertEquals("/playlist/add", command.route)
        assertEquals(listOf("1", "2"), command.params["mediaIds"])
        assertTrue(command.singleInstance)
    }

    @Test
    fun `pop supports one level and target route`() {
        assertEquals(
            DeepLinkCommand.Pop(route = null, params = emptyMap()),
            DeepLinkParser.parse("lmusic://pop")
        )
        assertEquals(
            DeepLinkCommand.Pop(route = "/media_source", params = emptyMap()),
            DeepLinkParser.parse("lmusic://pop/media_source")
        )
    }

    @Test
    fun `action supports path and query key forms`() {
        val pathCommand = assertIs<DeepLinkCommand.InvokeAction>(
            DeepLinkParser.parse("lmusic://action/test?name=helloworld")
        )
        val queryCommand = assertIs<DeepLinkCommand.InvokeAction>(
            DeepLinkParser.parse("lmusic://action?key=test&name=helloworld")
        )

        assertEquals("test", pathCommand.key)
        assertEquals(mapOf("name" to "helloworld"), pathCommand.params)
        assertEquals(pathCommand, queryCommand)
    }

    @Test
    fun `screen action supports path and query key forms`() {
        assertEquals(
            DeepLinkCommand.InvokeScreenAction("search"),
            DeepLinkParser.parse("lmusic://screen_action/search")
        )
        assertEquals(
            DeepLinkCommand.InvokeScreenAction("search"),
            DeepLinkParser.parse("lmusic://screen_action?key=search")
        )
    }

    @Test
    fun `invalid protocol command and control are rejected`() {
        assertNull(DeepLinkParser.parse("https://push/media_source"))
        assertNull(DeepLinkParser.parse("lmusic://unknown/media_source"))
        assertNull(DeepLinkParser.parse("lmusic://push/media_source?single_top=1"))
        assertNull(DeepLinkParser.parse("lmusic://push"))
        assertNull(DeepLinkParser.parse(null))
    }
}
