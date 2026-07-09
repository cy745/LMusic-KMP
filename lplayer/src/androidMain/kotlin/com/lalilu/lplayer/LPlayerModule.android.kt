package com.lalilu.lplayer

import android.content.Context
import androidx.lifecycle.LifecycleEventObserver
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.playback.HistoryStorage
import com.lalilu.lplayer.playback.HistoryStorageImpl
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.PlaybackHistory
import com.lalilu.lplayer.playback.PlaybackHistoryImpl
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Workaround: koin-ksp compiler (cy745 fork 2.3.1) doesn't generate
 * module definitions for Android target in this module.
 * This class provides the definitions that KSP should have generated.
 *
 * See: LPlayerModule (commonMain) for the @ComponentScan source.
 */
@Module
@ComponentScan("com.lalilu.lplayer")
abstract class LPlayerModuleAndroid
