package com.cricketcoach.manager

import com.cricketcoach.manager.game.BallEv
import com.cricketcoach.manager.ui.screens.isBroadcastHighlight
import org.junit.Assert.*
import org.junit.Test

class OverHighlightsTest {
    private fun ev(kind: String, text: String = "") = BallEv(1, 0, 0, 1, "N_IND", "a", "b", 0, 0, kind, "", 0f, 0f, text)
    @Test fun broadcastOnlyImportantDeliveries() {
        assertFalse(isBroadcastHighlight(ev("DOT", "No run")))
        assertFalse(isBroadcastHighlight(ev("RUN", "Single")))
        assertTrue(isBroadcastHighlight(ev("FOUR")))
        assertTrue(isBroadcastHighlight(ev("SIX")))
        assertTrue(isBroadcastHighlight(ev("WICKET")))
        assertTrue(isBroadcastHighlight(ev("APPEAL")))
        assertTrue(isBroadcastHighlight(ev("DOT", "Dropped catch")))
    }
}
