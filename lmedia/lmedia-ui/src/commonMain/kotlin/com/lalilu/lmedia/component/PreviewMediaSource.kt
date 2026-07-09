package com.lalilu.lmedia.component

import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource
import com.lalilu.lmedia.source.Configurable
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.buildConfig

internal object PreviewMediaSource : DomainMediaSource, Configurable {
    override val name: String = "Preview Source"
    override val config: MediaSourceConfig = buildConfig(
        key = name,
        description = "Preview Source only work in preview"
    ) {
        property<String>("url").provide("http://localhost:9999")
        property<String>("username").provide("admin")
        property<String>("password").provide("admin")
        property<Int>(key = "Min duration", description = "Set the duration min threshold").provide(0)
        property<Boolean>(key = "Enable", description = "Enable Preview Source", priority = 10).provide(false)

        function<Unit>(key = "Reset", description = "Reset Preview Source").onCall {}
        function<Unit>(key = "Cancel", description = "Cancel Preview Source").onCall {}
        function<Unit>(key = "Reload", description = "Reload Preview Source").onCall {}
    }
}
