package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** v2.7: no auction in the opening 2026 season (real squads); auction room opens from 2027. */
class AuctionSeasonRuleTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun iplFirstSeasonHasNoAuction() {
        val s = Modes.newCareer(1, "Test", Modes.IPL, "F_KKR", 1.0, 1, Random(7))
        assertEquals(2026, s.year); assertEquals(1, s.season)
        assertTrue(Modes.auctionMode(s))
        assertFalse("season 1 must not offer an auction", Modes.hasAuction(s))
        assertEquals(null, s.auction)
        // simulate roll-over into 2027
        s.season = 2; s.year = 2027
        assertTrue("auction opens from 2027", Modes.hasAuction(s))
    }

    @Test fun internationalNeverHasAuction() {
        val s = Modes.newCareer(1, "Test", Modes.INTL, "N_IND", 1.0, 1, Random(3))
        s.season = 2; s.year = 2027
        assertFalse(Modes.hasAuction(s))
    }
}
