package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Phase D: Stumpstagram player accounts, estimated followers, auto posts, engagement, follow / like. */
class StumpsTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun everyRealPlayerHasAUniqueAccount() {
        val accs = Repo.ds.players.map { Stumps.account(null, it.id) }
        assertTrue(accs.all { it != null })
        val a = accs.filterNotNull()
        assertEquals(Repo.ds.players.size, a.map { it.username }.toSet().size)
        val re = Regex("^[a-z][a-z0-9_]{2,40}$")
        a.forEach {
            assertTrue("bad username ${it.username}", re.matches(it.username))
            assertTrue(it.estimated)                       // no verified/dated follower data is bundled
            assertTrue(it.followersLabel.endsWith("est."))
            assertTrue(it.bio.isNotBlank() && it.followers > 0 && it.following > 0 && it.posts > 0)
        }
        println("Top accounts: " + Stumps.directory(null).take(12).joinToString { "${it.name} ${it.handle} ${it.followersLabel}" })
    }

    @Test fun followersReflectRelativePopularity() {
        fun f(id: String) = Stumps.account(null, id)!!.followers
        val kohli = f("ba607b88"); val bumrah = f("462411b3"); val dhoni = f("4a8a2e3b")
        val all = Repo.ds.players.map { f(it.id) }.sorted()
        val median = all[all.size / 2]
        assertTrue("Kohli $kohli should be top-5", all.takeLast(5).contains(kohli))
        assertTrue(kohli > bumrah && kohli > median * 50)
        assertTrue(dhoni > median * 10 && bumrah > median * 5)
        // a 2-cap associate-level player is far below the stars
        val minor = Repo.ds.players.filter { p -> listOf("TEST", "ODI", "T20I").sumOf { p.stats[it]?.m ?: 0 } in 1..3 && p.ipl == null }.map { f(it.id) }
        assertTrue(minor.isNotEmpty() && minor.max() < bumrah)
        // big names are verified, unknowns mostly aren't
        assertTrue(Stumps.account(null, "ba607b88")!!.verified && Stumps.account(null, "462411b3")!!.verified)
    }

    @Test fun engagementScalesWithPerformanceAndPopularity() {
        assertTrue(Stumps.likesFor(50_000_000, "CENTURY", 0.0, null) > Stumps.likesFor(500_000, "CENTURY", 0.0, null))
        assertTrue(Stumps.likesFor(1_000_000, "HATTRICK", 0.0, null) > Stumps.likesFor(1_000_000, "FIFTY", 0.0, null))
        assertTrue(Stumps.likesFor(1_000_000, "CENTURY", 80.0, null) > Stumps.likesFor(1_000_000, "CENTURY", 0.0, null))
        assertTrue(Stumps.likesFor(1_000_000, "WIN", 0.0, null) > Stumps.likesFor(1_000_000, "LOSS", 0.0, null))
        assertTrue(Stumps.followerGain(1_000_000, "TROPHY", 0.0) > Stumps.followerGain(1_000_000, "WIN", 0.0))
        assertTrue(Stumps.followerGain(1_000_000, "LOSS", 0.0) < 0)
    }

    @Test fun hatTricksAndMilestones() {
        fun ev(i: Int, inn: Int, bowler: String, kind: String, how: String = "") = BallEv(i, inn, i / 6, i % 6, "A", "x$i", bowler, 0, 0, kind, how, 0f, 0f, "")
        val evs = listOf(ev(0, 0, "B1", "WICKET", "b B1"), ev(1, 0, "B1", "WIDE"), ev(2, 0, "B1", "WICKET", "c X b B1"), ev(3, 0, "B2", "DOT"),
            ev(4, 0, "B1", "WICKET", "lbw b B1"), ev(5, 0, "B2", "WICKET", "b B2"), ev(6, 0, "B2", "WICKET", "run out (X)"), ev(7, 0, "B2", "WICKET", "b B2"))
        assertEquals(listOf("B1" to 0), Stumps.hatTricks(evs))
        assertEquals(emptyList<Pair<String, Int>>(), Stumps.hatTricks(listOf(ev(0, 0, "B", "WICKET", "b B"), ev(1, 1, "B", "WICKET", "b B"), ev(2, 1, "B", "WICKET", "b B"))))
        assertEquals("1000 career runs", Stumps.milestone(960, 1012, 0, 0, 10, 11))
        assertEquals("100 career wickets", Stumps.milestone(0, 10, 98, 101, 10, 11))
        assertEquals("50 career matches", Stumps.milestone(0, 10, 0, 0, 49, 50))
        assertNull(Stumps.milestone(100, 140, 3, 4, 10, 11))
    }

    @Test fun careerProducesPlayerPostsAndFollowWorks() {
        val rnd = Random(11)
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_MI", 1.0, 1, rnd)
        s.auction = null
        Career.startSeason(s)
        Stumps.ensureSeeded(s, rnd)
        assertTrue(s.posts.count { it.authorPid.isNotEmpty() } >= 3)
        repeat(12) {
            val d = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: return@repeat
            s.day = d
            for ((c, f) in Career.fixturesOnDay(s, d)) Career.quickSim(s, c, f, rnd)
        }
        val byPlayers = s.posts.filter { it.authorPid.isNotEmpty() }
        val kinds = byPlayers.map { it.kind }.toSet()
        println("player posts ${byPlayers.size}, kinds $kinds, e.g. " + byPlayers.take(4).joinToString(" | ") { "${it.handle}: ${it.text} (${it.likes} likes)" })
        assertTrue("player posts after 12 match days: ${byPlayers.size}", byPlayers.size >= 8)
        assertTrue(kinds.intersect(setOf("WIN", "LOSS", "FIFTY", "CENTURY", "FIVEFOR")).size >= 2)
        byPlayers.forEach { assertTrue(it.likes > 0 && it.commentCount >= it.comments.size && Stumps.account(s, it.authorPid) != null) }
        // follow / unfollow + following feed + follower delta
        val pid = byPlayers.first().authorPid
        val before = Stumps.account(s, pid)!!.followers
        assertTrue(Stumps.toggleFollow(s, pid))
        assertEquals(before + 1, Stumps.account(s, pid)!!.followers)
        assertTrue(Stumps.followingFeed(s).isNotEmpty() && Stumps.followingFeed(s).all { it.authorPid == pid || it.pid == pid })
        assertFalse(Stumps.toggleFollow(s, pid))
        assertTrue(Stumps.followingFeed(s).isEmpty())
        // like toggles and comments persist on the post
        val p = byPlayers.first(); val l = p.likes
        Social.toggleLike(p); assertEquals(l + 1, p.likes); Social.toggleLike(p); assertEquals(l, p.likes)
        // the save round-trips through Gson with the new fields
        val g = com.google.gson.Gson()
        val back = g.fromJson(g.toJson(s), CareerSave::class.java)
        assertEquals(s.posts.count { it.authorPid.isNotEmpty() }, back.posts.count { it.authorPid.isNotEmpty() })
        assertTrue(back.stumps.seeded)
        // an old save without the Stumpstagram fields still loads with defaults
        val old = g.fromJson("{\"manager\":\"X\",\"posts\":[{\"id\":1,\"text\":\"hi\"}]}", CareerSave::class.java)
        assertNotNull(old.stumps); assertFalse(old.stumps.seeded); assertEquals("", old.posts[0].authorPid)
    }
}
