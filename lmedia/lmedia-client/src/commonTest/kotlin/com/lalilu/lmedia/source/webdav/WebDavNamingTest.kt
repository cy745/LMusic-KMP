package com.lalilu.lmedia.source.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首扫阶段只能从文件名与目录结构派生元数据，命名规范直接决定首批结果质量，
 * 因此这里把规则逐条钉死。
 */
class WebDavNamingTest {

    @Test
    fun `strips track prefix and falls back to the parent directory for artist`() {
        val naming = WebDavNamingRules.derive("/HoneyComeBear/02 Friend.flac")

        assertEquals("Friend", naming.title)
        assertEquals("HoneyComeBear", naming.artist)
        assertEquals("HoneyComeBear", naming.album)
        assertEquals("2", naming.track)
    }

    @Test
    fun `uses grandparent directory as artist for the artist-album-track layout`() {
        val naming = WebDavNamingRules.derive("/HoneyComeBear/Daisy Crown/03 さよならの支度.flac")

        assertEquals("さよならの支度", naming.title)
        assertEquals("HoneyComeBear", naming.artist)
        assertEquals("Daisy Crown", naming.album)
        assertEquals("3", naming.track)
    }

    @Test
    fun `prefers the artist embedded in the file name`() {
        val naming = WebDavNamingRules.derive("/Various/MyGO!!!!! - ノンブレス・オブリージュ (Cover).flac")

        assertEquals("ノンブレス・オブリージュ (Cover)", naming.title)
        assertEquals("MyGO!!!!!", naming.artist)
        assertEquals("Various", naming.album)
        assertNull(naming.track)
    }

    @Test
    fun `keeps title when the file name has no track number or separator`() {
        val naming = WebDavNamingRules.derive("/Music/Sunny Rain (feat. Shion).flac")

        assertEquals("Sunny Rain (feat. Shion)", naming.title)
        assertEquals("Music", naming.artist)
        assertEquals("Music", naming.album)
        assertNull(naming.track)
    }

    @Test
    fun `handles files placed at the library root`() {
        val naming = WebDavNamingRules.derive("/07 ライアーメイデン.flac")

        assertEquals("ライアーメイデン", naming.title)
        assertNull(naming.artist)
        assertNull(naming.album)
        assertEquals("7", naming.track)
    }

    @Test
    fun `treats a separator only file name without artist prefix as a plain title`() {
        val naming = WebDavNamingRules.derive("/Music - Live/01 Intro.flac")

        assertEquals("Intro", naming.title)
        assertEquals("Music - Live", naming.artist)
        assertEquals("Music - Live", naming.album)
    }

    @Test
    fun `derives artist and album relative to the library root`() {
        // 库根必须参与计算：否则 /Music/艺人/曲目.flac 会被当成三级结构，歌手变成 Music
        val singleLevel = WebDavNamingRules.derive(
            path = "/Music/HoneyComeBear/02 Friend.flac",
            rootPath = "/Music/",
        )
        assertEquals("HoneyComeBear", singleLevel.artist)
        assertEquals("HoneyComeBear", singleLevel.album)

        val twoLevels = WebDavNamingRules.derive(
            path = "/Music/HoneyComeBear/Daisy Crown/03 さよならの支度.flac",
            rootPath = "/Music",
        )
        assertEquals("HoneyComeBear", twoLevels.artist)
        assertEquals("Daisy Crown", twoLevels.album)

        val atRoot = WebDavNamingRules.derive(
            path = "/Music/07 ライアーメイデン.flac",
            rootPath = "/Music/",
        )
        assertNull(atRoot.artist)
        assertNull(atRoot.album)
    }

    @Test
    fun `recognises audio extensions case insensitively and rejects others`() {
        assertTrue(WebDavNamingRules.isAudioFile("/a/b.FLAC"))
        assertTrue(WebDavNamingRules.isAudioFile("/a/b.mp3"))
        assertTrue(WebDavNamingRules.isAudioFile("/a/b.m4a"))
        assertTrue(WebDavNamingRules.isAudioFile("/a/b.opus"))

        assertFalse(WebDavNamingRules.isAudioFile("/a/b.txt"))
        assertFalse(WebDavNamingRules.isAudioFile("/a/b.jpg"))
        assertFalse(WebDavNamingRules.isAudioFile("/a/readme"))
        assertFalse(WebDavNamingRules.isAudioFile("/a/"))
    }

    @Test
    fun `normalises server url and directory path`() {
        assertEquals("http://192.168.3.6:8080", normalizeServerUrl("192.168.3.6:8080/"))
        assertEquals("https://dav.example.com", normalizeServerUrl(" https://dav.example.com/ "))
        assertEquals("http://host", normalizeServerUrl("http://host"))

        assertEquals("/music/", normalizeDirectoryPath("music"))
        assertEquals("/music/", normalizeDirectoryPath("/music"))
        assertEquals("/music/", normalizeDirectoryPath("/music/"))
        assertEquals("/", normalizeDirectoryPath(""))
        assertEquals("/", normalizeDirectoryPath("   "))
    }
}
