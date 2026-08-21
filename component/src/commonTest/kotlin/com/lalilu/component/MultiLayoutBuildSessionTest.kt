package com.lalilu.component

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiLayoutBuildSessionTest {

    @Test
    fun uniformFullLineMatchesRememberGridItemPaddingDistribution() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val scope = session.createGapScope(
            parentId = ROOT_GAP_SCOPE_ID,
            horizontalGap = 8.dp,
            verticalGap = 0.dp
        )
        val items = session.addItems(
            count = 3,
            span = 4,
            contentPadding = PaddingValues(horizontal = 16.dp),
            gapScopeId = scope
        )

        session.finish()

        assertHorizontalPadding(items[0], start = 16.dp, end = 0.dp)
        assertHorizontalPadding(items[1], start = 8.dp, end = 8.dp)
        assertHorizontalPadding(items[2], start = 0.dp, end = 16.dp)
        assertEquals(8.dp, end(items[0]) + start(items[1]))
        assertEquals(8.dp, end(items[1]) + start(items[2]))
    }

    @Test
    fun siblingGapScopesDoNotLeakAcrossTheirBoundary() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val gap8 = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 0.dp)
        val gap16 = session.createGapScope(ROOT_GAP_SCOPE_ID, 16.dp, 0.dp)
        val firstGroup = session.addItems(2, 3, PaddingValues(), gap8)
        val secondGroup = session.addItems(2, 3, PaddingValues(), gap16)
        val items = firstGroup + secondGroup

        session.finish()

        assertEquals(8.dp, end(items[0]) + start(items[1]))
        assertEquals(0.dp, end(items[1]) + start(items[2]))
        assertEquals(16.dp, end(items[2]) + start(items[3]))
    }

    @Test
    fun nestedScopeUsesNearestCommonGapScope() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val outer = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 0.dp)
        val inner = session.createGapScope(outer, 16.dp, 0.dp)
        val first = session.addItems(1, 3, PaddingValues(), outer).single()
        val middle = session.addItems(2, 3, PaddingValues(), inner)
        val last = session.addItems(1, 3, PaddingValues(), outer).single()
        val items = listOf(first) + middle + last

        session.finish()

        assertEquals(8.dp, end(items[0]) + start(items[1]))
        assertEquals(16.dp, end(items[1]) + start(items[2]))
        assertEquals(8.dp, end(items[2]) + start(items[3]))
    }

    @Test
    fun differentSpansReceiveProportionalPaddingWhenFeasible() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val scope = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 0.dp)
        val first = session.addItems(1, 3, PaddingValues(horizontal = 8.dp), scope).single()
        val middle = session.addItems(1, 6, PaddingValues(horizontal = 8.dp), scope).single()
        val last = session.addItems(1, 3, PaddingValues(horizontal = 8.dp), scope).single()

        session.finish()

        assertEquals(8.dp, start(first) + end(first))
        assertEquals(16.dp, start(middle) + end(middle))
        assertEquals(8.dp, start(last) + end(last))
        assertEquals(8.dp, end(first) + start(middle))
        assertEquals(8.dp, end(middle) + start(last))
    }

    @Test
    fun verticalGapUsesLargestScopeCrossingThePhysicalLineBoundary() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val outer = session.createGapScope(ROOT_GAP_SCOPE_ID, 0.dp, 8.dp)
        val inner = session.createGapScope(outer, 0.dp, 16.dp)

        session.addItems(1, 4, PaddingValues(), outer)
        val innerItems = session.addItems(3, 4, PaddingValues(), inner)
        val secondLineOuterItems = session.addItems(2, 4, PaddingValues(), outer)

        session.finish()

        val secondLineItems = listOf(innerItems.last()) + secondLineOuterItems
        secondLineItems.forEach { item ->
            assertEquals(16.dp, item.resolvedPadding.calculateTopPadding())
        }
    }

    @Test
    fun wrappingInsideOneScopeAppliesVerticalGapToTheWholeNextLine() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val scope = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 12.dp)
        val items = session.addItems(
            count = 3,
            span = 6,
            contentPadding = PaddingValues(horizontal = 16.dp),
            gapScopeId = scope
        )

        session.finish()

        assertEquals(0.dp, items[0].resolvedPadding.calculateTopPadding())
        assertEquals(0.dp, items[1].resolvedPadding.calculateTopPadding())
        assertEquals(12.dp, items[2].resolvedPadding.calculateTopPadding())
    }

    @Test
    fun uniformIncompleteLineUsesVirtualTracks() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val scope = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 0.dp)
        val items = session.addItems(
            count = 2,
            span = 4,
            contentPadding = PaddingValues(horizontal = 16.dp),
            gapScopeId = scope
        )

        session.finish()

        assertEquals(16.dp, start(items.first()))
        assertEquals(16.dp, start(items.last()) + end(items.last()))
        assertEquals(8.dp, end(items.first()) + start(items.last()))
    }

    @Test
    fun firstTrackKeepsTheSameWidthOnAUniformTrailingLine() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val scope = session.createGapScope(ROOT_GAP_SCOPE_ID, 16.dp, 0.dp)
        val items = session.addItems(
            count = 4,
            span = 4,
            contentPadding = PaddingValues(horizontal = 16.dp),
            gapScopeId = scope
        )

        session.finish()

        assertEquals(start(items[0]), start(items[3]))
        assertEquals(end(items[0]), end(items[3]))
        assertEquals(
            start(items[0]) + end(items[0]),
            start(items[3]) + end(items[3])
        )
    }

    @Test
    fun mixedGapScopesKeepUsingActualItemBoundariesOnAnIncompleteLine() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)
        val gap8 = session.createGapScope(ROOT_GAP_SCOPE_ID, 8.dp, 0.dp)
        val gap16 = session.createGapScope(ROOT_GAP_SCOPE_ID, 16.dp, 0.dp)
        val first = session.addItems(1, 4, PaddingValues(horizontal = 16.dp), gap8).single()
        val second = session.addItems(1, 4, PaddingValues(horizontal = 16.dp), gap16).single()

        session.finish()

        assertEquals(0.dp, end(first) + start(second))
        assertEquals(0.dp, end(second))
    }

    @Test
    fun asymmetricContentPaddingKeepsLogicalStartAndEndInRtl() {
        val session = MultiLayoutBuildSession(LayoutDirection.Rtl)
        val scope = session.createGapScope(ROOT_GAP_SCOPE_ID, 10.dp, 0.dp)
        val items = session.addItems(
            count = 2,
            span = 6,
            contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
            gapScopeId = scope
        )

        session.finish()

        assertEquals(
            12.dp,
            items.first().resolvedPadding.calculateStartPadding(LayoutDirection.Rtl)
        )
        assertEquals(
            20.dp,
            items.last().resolvedPadding.calculateEndPadding(LayoutDirection.Rtl)
        )
        assertEquals(
            10.dp,
            items.first().resolvedPadding.calculateEndPadding(LayoutDirection.Rtl) +
                items.last().resolvedPadding.calculateStartPadding(LayoutDirection.Rtl)
        )
    }

    @Test
    fun negativeGapIsRejectedBeforeRegisteringItems() {
        val session = MultiLayoutBuildSession(LayoutDirection.Ltr)

        assertFailsWith<IllegalArgumentException> {
            session.createGapScope(ROOT_GAP_SCOPE_ID, (-1).dp, 0.dp)
        }
    }

    @Test
    fun globalScopeStackRestoresTheParentAfterNestedGap() {
        val global = MultiLayoutGlobalData()
        val session = global.beginBuild(LayoutDirection.Ltr)
        val items = mutableListOf<MultiLayoutItemPlan>()
        val outer = global.createGapScope(horizontalGap = 8.dp, verticalGap = 0.dp)

        global.withGapScope(outer) {
            items += global.addItems(1, 3, PaddingValues())
            val inner = global.createGapScope(horizontalGap = 16.dp, verticalGap = 0.dp)
            global.withGapScope(inner) {
                items += global.addItems(2, 3, PaddingValues())
            }
            items += global.addItems(1, 3, PaddingValues())
        }
        global.finishBuild(session)

        assertEquals(8.dp, end(items[0]) + start(items[1]))
        assertEquals(16.dp, end(items[1]) + start(items[2]))
        assertEquals(8.dp, end(items[2]) + start(items[3]))
    }

    private fun assertHorizontalPadding(
        item: MultiLayoutItemPlan,
        start: Dp,
        end: Dp
    ) {
        assertEquals(start, start(item))
        assertEquals(end, end(item))
    }

    private fun start(item: MultiLayoutItemPlan) =
        item.resolvedPadding.calculateStartPadding(LayoutDirection.Ltr)

    private fun end(item: MultiLayoutItemPlan) =
        item.resolvedPadding.calculateEndPadding(LayoutDirection.Ltr)
}
