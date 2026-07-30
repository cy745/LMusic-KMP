package com.lalilu.lplaylist.screen


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lplaylist.viewmodel.PlaylistEditAction
import com.lalilu.lplaylist.viewmodel.PlaylistEditVM
import com.lalilu.navigation.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


/**
 * [playlistId]   目标操作歌单的Id
 */
@Destination("/pages/playlist/edit")
data class PlaylistEditScreen(
    private val playlistId: String? = null
) : Screen, ScreenInfoFactory, ScreenActionFactory {
    override val key: String = playlistId.toString()

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "歌单创建编辑页" },
            icon = RemixIcon.Design.editBoxFill
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<PlaylistEditVM>(
            parameters = { parametersOf(playlistId) }
        )

        return remember {
            listOfNotNull(
                if (vm.playlist.value != null) {
                    ScreenAction.Static(
                        title = { "删除歌单" },
                        icon = { RemixIcon.System.deleteBinLine },
                        longClick = { true },
                        color = { Color(0xFFF5381D) },
                        onAction = { vm.intent(PlaylistEditAction.Delete) }
                    )
                } else null,
                ScreenAction.Static(
                    title = { if (vm.playlist.value == null) "创建歌单" else "更新歌单" },
                    icon = { RemixIcon.Design.editBoxFill },
                    longClick = { true },
                    color = { Color(0xFF0074FF) },
                    onAction = { vm.intent(PlaylistEditAction.Confirm) }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<PlaylistEditVM>(
            parameters = { parametersOf(playlistId) }
        )

        PlaylistEditScreenContent(
            isEditing = { vm.playlist.value != null },
            titleHint = { vm.playlist.value?.title ?: "" },
            subTitleHint = { vm.playlist.value?.subTitle ?: "" },
            titleValue = { vm.titleState.value },
            onUpdateTitle = { vm.titleState.value = it },
            subTitleValue = { vm.subTitleState.value },
            onUpdateSubTitle = { vm.subTitleState.value = it }
        )
    }
}