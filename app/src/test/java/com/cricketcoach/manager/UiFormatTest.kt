package com.cricketcoach.manager

import com.cricketcoach.manager.ui.screens.avgPart
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase G: batting averages are not shown on tiny samples (177 runs, 1 dismissal is not "@177.0"). */
class UiFormatTest {
    @Test fun averagesNeedThreeDismissals() {
        assertEquals("", avgPart(0, 0, 0))
        assertEquals(" (2 n.o.)", avgPart(88, 2, 2))
        assertEquals(" · 1 out", avgPart(177, 4, 3))
        assertEquals(" · 2 out", avgPart(120, 3, 1))
        assertEquals(" @38.0", avgPart(114, 4, 1))
    }
}
