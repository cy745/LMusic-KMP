package com.lalilu.lplayer.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class PreparationControlTest {
    @Test fun unchangedRequestKeepsItsPlayIntent() {
        val control = PreparationControl()
        assertTrue(control.playIntent(control.ticket(), true)!!)
        assertFalse(control.playIntent(control.ticket(), false)!!)
    }

    @Test fun pauseBeforeNativeApplicationOnlyAllowsSilentPreparation() {
        val control = PreparationControl()
        val ticket = control.ticket()
        control.pause()
        assertFalse(control.playIntent(ticket, true)!!)
        assertTrue(control.playIntent(control.ticket(), true)!!)
    }

    @Test fun stopFollowedByPauseCannotReviveAnOlderRequest() {
        val control = PreparationControl()
        val ticket = control.ticket()
        control.stop()
        control.pause()
        assertNull(control.playIntent(ticket, true))
        assertTrue(control.playIntent(control.ticket(), true)!!)
    }
}
