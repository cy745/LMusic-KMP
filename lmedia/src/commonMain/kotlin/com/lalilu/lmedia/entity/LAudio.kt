package com.lalilu.lmedia.entity

class LAudio(
    override val id: String = "",
    override val title: String = "",
    override val subtitle: String = "",
    override val extra: Map<String, String> = emptyMap(),
    val sourceItem: SourceItem
) : LItem

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect sealed interface SourceItem