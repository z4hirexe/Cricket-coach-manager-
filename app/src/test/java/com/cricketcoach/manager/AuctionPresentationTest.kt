package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.screens.auctionSetLabel
import com.cricketcoach.manager.ui.screens.kitNumber
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Phase B: data shown by the auction broadcast (retained list, sets, kit numbers, history consistency). */
class AuctionPresentationTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun megaAuctionRecordsRetainedSoldAndUnsold() {
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_KKR", 1.0, 1, Random(3))
        s.auctionDoneThisSeason = false
        val a = Auction.start(s, "MEGA"); s.auction = a
        assertEquals("RETENTION", a.phase)
        val u = s.userClub()
        val keep = u.squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() }.take(2).map { it.id }
        Auction.confirmRetention(s, a, keep)
        assertEquals("LIVE", a.phase)
        // the user's 2 + up to 4 for every AI franchise
        assertEquals(keep.toSet(), a.retained.filter { it.club == u.id }.map { it.pid }.toSet())
        assertTrue(a.retained.all { s.players[it.pid]?.clubId == it.club && it.price > 0 })
        assertTrue(a.retained.size in 2..(2 + 9 * 4))
        // first lots are the marquee set
        assertEquals("MARQUEE SET", auctionSetLabel(a, Auction.cur(s, a)))
        Auction.autoComplete(s, a, Random(5))
        assertTrue(a.done)
        // every sale is on the buyer's books and no player is both sold and unsold
        assertTrue(a.sold.all { s.players[it.pid]?.clubId == it.club })
        assertTrue(a.sold.map { it.pid }.intersect(a.unsold.toSet()).isEmpty())
        assertTrue(a.retained.map { it.pid }.intersect(a.sold.map { it.pid }.toSet()).isEmpty())
    }

    @Test fun setLabelsAndKitNumbers() {
        val a = AuctionState(index = 25)
        assertEquals("SET 3 · WICKET-KEEPERS", auctionSetLabel(a, GPlayer(role = "WK")))
        assertEquals("SET 3 · SPIN BOWLERS", auctionSetLabel(a, GPlayer(role = "BOWL", bowlType = "SPIN")))
        assertEquals("ACCELERATED ROUND", auctionSetLabel(a, null))
        for (id in listOf("ba607b88", "462411b3", "x", "")) assertTrue(kitNumber(id) in 1..98)
        assertEquals(kitNumber("ba607b88"), kitNumber("ba607b88"))
    }
}
