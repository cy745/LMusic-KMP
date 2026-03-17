package com.lalilu.lhome.screen.songs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.AudioItemCard
import com.lalilu.lhome.viewmodel.SongsState
import com.lalilu.lhome.viewmodel.SongsVM
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenActionFactory
import com.lalilu.navigation.ScreenBarFactory
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import org.koin.compose.koinInject

@Destination("/pages/songs")
data class SongsScreen(
    private val title: String? = null,
    private val mediaIds: List<String> = emptyList()
) : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { title ?: "歌曲" }
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        return remember {
            emptyList()
        }
    }

    @Composable
    override fun Content() {
        val viewModel: SongsVM = koinInject()

        SongsScreenContent(
            viewModel = viewModel
        )
    }
}

@Composable
fun SongsScreenContent(
    viewModel: SongsVM
) {
    val songs: List<LAudio> = viewModel.songs.value
    val state: SongsState = viewModel.state.value

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = navigationBar.calculateBottomPadding() + 12.dp
        )
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "歌曲",
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (state.searchKeyWord.isBlank()) {
                        "共 ${songs.size} 首歌曲"
                    } else {
                        "搜索: ${state.searchKeyWord} (${songs.size} 首)"
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                )
            }
        }

        items(
            items = songs,
            key = { it.idValue() }
        ) { audio ->
            AudioItemCard(
                title = audio.titleValue(),
                subtitle = audio.subtitleValue(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
