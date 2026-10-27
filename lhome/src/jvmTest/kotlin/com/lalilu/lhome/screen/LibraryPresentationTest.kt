package com.lalilu.lhome.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import com.lalilu.lhome.screen.songs.SongsEmptyState
import com.lalilu.lmedia.domain.model.artistNames
import com.lalilu.lmedia.domain.model.libraryArtistIds
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.preview.preview
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

@OptIn(ExperimentalTestApi::class)
class LibraryPresentationTest {
    @BeforeTest fun setup() { startKoin {} }
    @AfterTest fun teardown() { stopKoin() }
    @Test fun artistLinksAppearWhenMetadataArrivesAfterHeaderComposition() = runDesktopComposeUiTest(width = 1000, height = 700) {
        val artists = mutableStateOf(emptyList<LArtist>())
        setContent {
            preview {
                SongDetailScreenContent(
                    song = LAudio(id = "fixture", title = "合作歌曲", mediaSourceName = "local"),
                    artists = artists.value,
                )
            }
        }
        runOnIdle {
            artists.value = listOf(
                LArtist(id = "artist_one", title = "第一位歌手", subtitle = "", extra = emptyMap()),
                LArtist(id = "artist_two", title = "第二位歌手", subtitle = "", extra = emptyMap()),
            )
        }
        onNodeWithText("第一位歌手").assertIsDisplayed().assertHasClickAction()
        onNodeWithText("第二位歌手").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun emptyStateRetainsReadableHeaderOnPhone() = runDesktopComposeUiTest(width = 390, height = 700) {
        setContent {
            preview {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        NavigatorHeader(title = "歌曲", subTitle = "共 0 首歌曲")
                        SongsEmptyState(searching = false, onClearSearch = {}, onAddSource = {})
                    }
                }
            }
        }
        onNodeWithText("还没有歌曲").assertIsDisplayed()
        onNodeWithText("添加媒体源后，你的歌曲会出现在这里").assertIsDisplayed()
        save("library-empty-phone.png", captureToImage())
    }

    @Test fun emptyLibraryAndSearchOfferDifferentWorkingActions() = runDesktopComposeUiTest(width = 390, height = 700) {
        val searching = mutableStateOf(false)
        var addRequests = 0
        var clearRequests = 0
        setContent {
            preview {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        NavigatorHeader(title = "歌曲", subTitle = if (searching.value) "搜索：未找到 · 0 首" else "共 0 首歌曲")
                        SongsEmptyState(
                            searching = searching.value,
                            onAddSource = { addRequests++ },
                            onClearSearch = { clearRequests++; searching.value = false },
                        )
                    }
                }
            }
        }
        onNodeWithText("添加媒体源").performClick()
        runOnIdle { kotlin.test.assertEquals(1, addRequests); kotlin.test.assertEquals(0, clearRequests); searching.value = true }
        onNodeWithText("没有找到相关歌曲").assertIsDisplayed()
        onNodeWithText("添加媒体源").assertDoesNotExist()
        save("library-search-empty-phone.png", captureToImage())
        onNodeWithText("清除搜索").performClick()
        onNodeWithText("还没有歌曲").assertIsDisplayed()
        runOnIdle { kotlin.test.assertEquals(1, clearRequests); kotlin.test.assertEquals(1, addRequests) }
    }

    @Test fun artistCreditsFollowMetadataOrderInsteadOfRepositoryOrder() {
        val song = LAudio(id = "credits", title = "合作歌曲", mediaSourceName = "local",
            extra = mapOf(com.lalilu.lmedia.domain.model.LAudioExtraKeys.ArtistName to "HoYoFair / Empty old City; HoYoFair"))
        val names = song.artistNames()
        val ids = song.libraryArtistIds()
        val entities = ids.zip(names).map { (id, name) ->
            LArtist(id = id, title = name, subtitle = "", extra = emptyMap())
        }.reversed()
        val ordered = com.lalilu.lhome.viewmodel.orderSongArtists(song, entities)
        kotlin.test.assertEquals(listOf("HoYoFair", "Empty old City"), ordered.map { it.title })
        kotlin.test.assertEquals(ids, ordered.map { it.id })
    }

    private fun save(name: String, bitmap: ImageBitmap) {
        val directory = System.getenv("LMUSIC_UI_SCREENSHOT_DIR") ?: return
        val target = File(directory).apply { mkdirs() }
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData()?.use { File(target, name).writeBytes(it.bytes) }
        }
    }
}
