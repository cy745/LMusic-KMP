package com.lalilu.lmusic.repository

import com.lalilu.lhistory.LHistoryModule
import com.lalilu.lhistory.viewmodel.HistoryVM
import org.koin.core.definition.Kind
import org.koin.ksp.generated.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(org.koin.core.annotation.KoinInternalApi::class)
class HistoryViewModelLifetimeTest {
    @Test fun historyViewModelIsCreatedPerOwnerRatherThanReusingAClearedSingleton() {
        // Exercise the generated registration used by all three consumers: home,
        // history content, and SmartBar. A singleton can be cleared by any owner.
        val definitions = LHistoryModule.module.mappings.values
            .map { it.beanDefinition }.filter { it.primaryType == HistoryVM::class }
        assertTrue(definitions.isNotEmpty())
        definitions.forEach { assertEquals(Kind.Factory, it.kind) }
    }
}
