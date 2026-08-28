package com.lalilu.lmusic.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerBottomSheetActionTest {
    @Test
    fun `player bottom sheet command accepts canonical states`() {
        assertEquals(PlayerBottomSheetCommand.Expand, PlayerBottomSheetCommand.parse("expanded"))
        assertEquals(PlayerBottomSheetCommand.Collapse, PlayerBottomSheetCommand.parse("collapsed"))
        assertEquals(PlayerBottomSheetCommand.Toggle, PlayerBottomSheetCommand.parse("toggle"))
    }

    @Test
    fun `player bottom sheet command is case insensitive and accepts action aliases`() {
        assertEquals(PlayerBottomSheetCommand.Expand, PlayerBottomSheetCommand.parse("OPEN"))
        assertEquals(PlayerBottomSheetCommand.Collapse, PlayerBottomSheetCommand.parse("hide"))
    }

    @Test
    fun `player bottom sheet command rejects missing and unknown states`() {
        assertNull(PlayerBottomSheetCommand.parse(null))
        assertNull(PlayerBottomSheetCommand.parse(""))
        assertNull(PlayerBottomSheetCommand.parse("unknown"))
    }
}
