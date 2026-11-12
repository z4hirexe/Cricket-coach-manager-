package com.cricketcoach.manager

import com.cricketcoach.manager.audio.CrowdMood
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Phase 6 (auction presentation state) and Phase 8 (dynamic crowd mood) logic. */
class PresentationTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun auctionPresentationState() {
        val rnd = Random(9)
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_KKR", 1.0, 1, rnd)
        if (s.auction == null) s.auction = Auction.start(s, "MINI")
        val a = s.auction!!
        if (a.phase == "RETENTION") Auction.confirmRetention(s, a, s.userClub().squad.take(2))
        assertEquals("LIVE", a.phase)
        assertTrue("fresh lot has no paddles", a.lotBidders.isEmpty())
        // user raises a paddle
        val before = a.bidSeq
        val err = Auction.userBid(s, a)
        if (err == null) {
            assertEquals(before + 1, a.bidSeq)
            assertEquals(s.userClubId, a.lotBidders.last())
            assertEquals(s.userClubId, a.curBidder)
        }
        // run the clock until the lot is hammered
        val seq = a.lastSeq
        var g = 0
        while (a.lastSeq == seq && g++ < 400) Auction.tick(s, a, rnd)
        assertEquals(seq + 1, a.lastSeq)
        assertTrue(a.lastPid.isNotEmpty())
        if (a.lastClub.isNotEmpty()) {
            assertEquals(a.lastClub, s.players[a.lastPid]!!.clubId)
            assertTrue(a.lastPrice > 0)
        }
        assertTrue("paddles reset for the next lot", a.lotBidders.isEmpty() || a.lotBidders.all { it in s.clubs })
        // every bidder recorded on a lot is a real club, the leader is always the last paddle raised
        repeat(30) {
            Auction.tick(s, a, rnd)
            if (a.curBidder.isNotEmpty()) assertEquals(a.curBidder, a.lotBidders.last())
            assertTrue(a.lotBidders.size <= 12)
        }
        Auction.autoComplete(s, a, rnd)
        assertTrue(a.done)
        // old saves (no presentation fields) still load with safe defaults
        val old = Gson().fromJson("{\"type\":\"MINI\",\"phase\":\"LIVE\",\"index\":3}", AuctionState::class.java)
        assertNotNull(old.lotBidders); assertEquals(0, old.bidSeq); assertEquals("", old.lastPid)
        // and the new fields survive a save round-trip
        val back = Gson().fromJson(Gson().toJson(a), AuctionState::class.java)
        assertEquals(a.lastSeq, back.lastSeq); assertEquals(a.lastPid, back.lastPid)
    }

    @Test fun crowdMoodFollowsTheChase() {
        val s = Career.newCareer(1, "Test", "Siliguri Strikers", "SIL", "India", 0xFF00C853, 0xFFFFC107, 0, 0, "SQUAD", 1.0, 1)
        Tournaments.ensureNations(s)
        val comp = Competition(id = "M", name = "Mood Cup", key = "M", format = "T20", kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("m1", "N_IND", "N_AUS", "Match", 0, 1), Random(3))
        m.autoToss()
        val early = CrowdMood.of(m)
        assertTrue(early.excite in 0f..1f && early.tension == 0f)
        var g = 0
        while (m.innings.size < 2 && !m.finished && g++ < 1000) m.nextBall()
        if (m.finished) return
        val inn = m.cur!!
        // tight finish: 10 needed off the last over
        inn.target = 150; inn.runs = 140; inn.balls = inn.maxBalls - 6
        val tight = CrowdMood.of(m)
        assertTrue("tension in a tight finish ${tight.tension}", tight.tension >= 0.75f)
        // stroll: 10 needed off 10 overs
        inn.runs = 140; inn.balls = inn.maxBalls - 60
        val easy = CrowdMood.of(m)
        assertTrue("no tension when cruising ${easy.tension}", easy.tension < 0.3f)
        assertTrue(easy.tension < tight.tension)
        m.simToEnd()
        assertEquals(0f, CrowdMood.of(m).tension)
    }

    @Test fun soundAssetsExist() {
        val raw = File("src/main/res/raw")
        val need = listOf("sfx_bat", "sfx_bat_big", "sfx_edge", "sfx_pad", "sfx_glove", "sfx_stumps", "sfx_cheer", "sfx_cheer2", "sfx_roar", "sfx_roar2",
            "sfx_wicket", "sfx_applause", "sfx_groan", "sfx_ooh", "sfx_appeal", "sfx_catch", "sfx_dhol", "sfx_bugle", "sfx_click", "sfx_hammer",
            "sfx_gavel_tap", "sfx_sold", "sfx_unsold", "sfx_paddle", "sfx_bid_rival", "sfx_coin", "sfx_horn", "sfx_whoosh", "sfx_success",
            "sfx_notify", "sfx_error", "amb_crowd", "amb_tension", "amb_chant", "amb_auction", "music_theme", "music_auction")
        for (n in need) assertTrue("missing $n", File(raw, "$n.wav").exists() || File(raw, "$n.ogg").exists())
        // resource names must be unique regardless of extension
        val names = raw.listFiles()!!.map { it.nameWithoutExtension }
        assertEquals(names.size, names.toSet().size)
    }
}
