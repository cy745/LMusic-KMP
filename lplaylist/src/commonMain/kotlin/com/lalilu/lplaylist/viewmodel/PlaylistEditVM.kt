package com.lalilu.lplaylist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.extensions.GlobalToaster
import com.lalilu.extensions.toMutableState
import com.lalilu.extensions.toState
import com.lalilu.krouter.KRouter
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.PlaylistRepository
import com.lalilu.mviImplWithIntent
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent
import com.lalilu.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
data class PlaylistEditState(
    val playlistId: String,
) {
    fun getPlaylistFlow(playlistRepo: PlaylistRepository): Flow<LPlaylist?> {
        return playlistRepo.getPlaylistsFlow().mapLatest { list ->
            list.firstOrNull { it.id == playlistId }
        }
    }
}

sealed interface PlaylistEditAction {
    data object Confirm : PlaylistEditAction
    data object Delete : PlaylistEditAction
}

sealed interface PlaylistEditEvent {

}

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
@KoinViewModel
data class PlaylistEditVM(
    val playlistId: String?,
    private val actualId: String = playlistId ?: Uuid.random().toHexString(),
    private val playlistRepo: PlaylistRepository
) : ViewModel(),
    MviWithIntent<PlaylistEditState, PlaylistEditEvent, PlaylistEditAction>
    by mviImplWithIntent(PlaylistEditState(actualId)) {

    val state = stateFlow()
        .toState(PlaylistEditState(actualId), viewModelScope)

    private val playlistFlow = stateFlow()
        .distinctUntilChangedBy { it.playlistId }
        .flatMapLatest { it.getPlaylistFlow(playlistRepo) }

    val titleState = playlistFlow
        .mapLatest { it?.title ?: "" }
        .toMutableState("", viewModelScope)

    val subTitleState = playlistFlow
        .mapLatest { it?.subTitle ?: "" }
        .toMutableState("", viewModelScope)

    val playlist = playlistFlow
        .toState(viewModelScope)

    override fun intent(intent: PlaylistEditAction) = viewModelScope.launch {
        when (intent) {
            is PlaylistEditAction.Confirm -> {
                if (titleState.value.isBlank()) {
                    GlobalToaster?.show("歌单标题不能为空")
                    return@launch
                }

                playlistRepo.save(
                    LPlaylist(
                        id = state.value.playlistId,
                        title = titleState.value,
                        subTitle = subTitleState.value,
                        mediaIds = playlist.value?.mediaIds ?: emptyList(),
                        coverUri = playlist.value?.coverUri ?: "",
                    )
                )

                AppRouter.intent(NavIntent.Pop)
            }

            is PlaylistEditAction.Delete -> {
                playlistRepo.removeById(actualId)

                KRouter.route<Screen>("/pages/playlist")
                    ?.let { AppRouter.intent(NavIntent.PopUntil(it)) }
            }

            else -> {}
        }
    }
}
