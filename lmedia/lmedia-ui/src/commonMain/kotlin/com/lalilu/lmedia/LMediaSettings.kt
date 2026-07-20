package com.lalilu.lmedia

import com.lalilu.common.settings.NoOpToaster
import com.lalilu.common.settings.settingsGroup
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named


@Factory
@Named("settings_lmedia_data")
fun provideLMediaDataSettings(
    audioRepository: AudioRepository
) = settingsGroup(
    key = "lmedia_data",
    order = 11,
    title = { "媒体数据库" },
) {
    click(
        key = "lmedia_data.clear_unavailable_items",
        title = { "清除不可播放元素" },
        summary = { "从数据库中删除不可播放的元素" },
        onClick = { ctx ->
            GlobalScope.launch {
                audioRepository.clearUnavailableAudio()

                // 仅在业务侧注入了真实 Toaster 时才反馈（默认 NoOpToaster）
                (ctx.toaster.takeIf { it !== NoOpToaster })?.info("已清除")
            }
        }
    )
}
