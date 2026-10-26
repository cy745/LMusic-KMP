package com.lalilu.lplayer

import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

@Factory
@Named("settings_content_cache")
fun provideContentCacheSettings(): SettingsGroup = settingsGroup(key="content_cache",order=11,title={ "音频缓存" }) {
    switch(kv=LPlayerKV.contentCacheEnabled,title={ "使用音频缓存" },summary={ "支持的数据源按需缓存，完整缓存可离线播放；切换后从下次选曲生效" })
    dropdown(kv=LPlayerKV.contentCacheQuotaMiB,title={ "音频缓存容量" },options=listOf(256,512,1024,2048),
        optionLabel={ "$it MiB" },serialize={ it.toString() },deserialize={ it.toIntOrNull() ?: 512 },fallback=512)
}
