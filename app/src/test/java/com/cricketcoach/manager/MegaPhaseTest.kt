package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.Brand
import com.cricketcoach.manager.ui.PhotoArt
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Mega phase H (v2.6, Cricket Manager Pro 2027): branding, dates, comments, photo posts, Yorker, save migration, report. */
class MegaPhaseTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    private fun career(mode: String = Modes.IPL, team: String = "F_MI", days: Int = 0, seed: Int = 3): CareerSave {
        val rnd = Random(seed)
        val s = Modes.newCareer(1, "Test Coach", mode, team, 1.0, 1, rnd)
        s.auction = null
        assertNull(Career.startSeason(s))
        repeat(days) {
            val d = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: return@repeat
            s.day = d
            for ((c, f) in Career.fixturesOnDay(s, d)) Career.quickSim(s, c, f, rnd)
            Career.weeklyUpdate(s, emptySet(), rnd)
        }
        return s
    }

    /* ---------------------------------------------------------------- branding */

    @Test fun brandingIs2027WithTrademark() {
        assertEquals("Cricket Manager Pro 2027\u2122", Brand.FULL)
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("Cricket Manager Pro 2027\u2122"))
        // no old / temporary names left in the shipped sources
        val src = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        assertFalse(src.contains("CricGram"))
        assertFalse(src.contains("Cricket Coach Manager"))
        val gradle = File("build.gradle.kts").readText()
        assertTrue("versionName must match Brand.VERSION", gradle.contains("\"${Brand.VERSION}\""))
    }

    /* ---------------------------------------------------------------- dates */

    @Test fun newCareersStartIn2026AndOneDateFormat() {
        for ((m, t) in listOf(Modes.IPL to "F_KKR", Modes.DOMESTIC to "S_BEN", Modes.INTL to "N_IND")) assertEquals(2026, career(m, t).year)
        val d = LocalDate.of(2026, 3, 28)
        assertEquals("28 Mar 2026", Schedule.fmt(d, "IN"))
        assertEquals("Mar 28, 2026", Schedule.fmt(d, "US"))
        assertEquals("2026-03-28", Schedule.fmt(d, "ISO"))
        assertEquals("Sat 28 Mar 2026", Schedule.fmt(d, "IN", dow = true))
    }

    @Test fun generatedCompetitionsAreLabelledAndOfficialOnlyIpl2026() {
        val s = career(Modes.DOMESTIC, "S_BEN")
        assertTrue(s.league.all { it.source.isEmpty() })
        assertEquals(OfficialSchedule.GENERATED, OfficialSchedule.label(s.league.first()))
        // a non-2026 IPL season stays generated
        val ipl = career(Modes.IPL, "F_CSK").league.first { it.key == "IPL" }
        val copy = Competition(id = "X", name = "IPL 2027", key = "IPL", format = "T20", kind = "FRANCHISE").apply { teams.addAll(ipl.teams) }
        assertFalse(OfficialSchedule.applyIpl(copy, 2027))
        assertTrue(copy.source.isEmpty())
    }

    /* ---------------------------------------------------------------- comments */

    @Test fun generatedCommentsAreUniquePerPostAndContextual() {
        val ctx = CommentGen.Ctx("CENTURY", "Virat Kohli", "RCB", "CSK", "122* (98)")
        val cap = CommentGen.capacity(ctx)
        assertTrue("capacity $cap", cap >= 1000)
        val texts = CommentGen.page(42L, ctx, 0, 1000).map { it.text }
        assertEquals("no repeated comment in the first 1000", texts.size, texts.toSet().size)
        // deterministic and pageable
        assertEquals(CommentGen.page(42L, ctx, 20, 20).map { it.text }, texts.subList(20, 40))
        // different posts read differently
        val other = CommentGen.page(43L, ctx, 0, 20).map { it.text }
        assertTrue(other.zip(texts.take(20)).count { it.first == it.second } < 5)
        // a loss post never gets century-style praise
        val loss = CommentGen.page(7L, CommentGen.Ctx("LOSS", "", "MI", "CSK"), 0, 200).map { it.text.lowercase() }
        assertTrue(loss.none { it.contains("hundred") || it.contains("century") })
    }

    @Test fun stumpsCommentsPaginateAndUserCommentGetsAPlayerReply() {
        val s = career(Modes.INTL, "N_IND", 2)
        Stumps.ensureSeeded(s)
        val p = Stumps.playerPost(s, "ba607b88", "CENTURY", 22.0, "Hundred! 💯 #IND", Random(2))!!
        assertEquals("century", p.photo)
        assertTrue("big account gets a big comment count: ${p.commentCount}", p.commentCount > 1000)
        val total = Stumps.generatedTotal(s, p)
        val first = Stumps.commentPage(s, p, 0, 20)
        assertEquals(20, first.size)
        val second = Stumps.commentPage(s, p, 20, 20)
        assertTrue(first.map { it.text }.intersect(second.map { it.text }.toSet()).isEmpty())
        assertTrue(Stumps.commentPage(s, p, total, 20).isEmpty())
        val before = p.commentCount
        val c = Stumps.userComment(s, p, "Take a bow! 👏")
        assertTrue(c.mine && c.id > 0)
        assertTrue("player answers the first comment", p.comments.any { it.replyTo == c.id && it.pid == "ba607b88" })
        assertEquals(before + 2, p.commentCount)
        // reply to a generated comment
        val g = first[3]
        val r = Stumps.userComment(s, p, "Agreed!", g.id)
        assertEquals(g.id, r.replyTo)
        assertTrue(g.id < 0)
    }

    /* ---------------------------------------------------------------- photo posts */

    @Test fun photoKeysAndKitTint() {
        for (k in listOf("CENTURY", "FIFTY", "HATTRICK", "FIVEFOR", "WIN", "TROPHY", "AUCTION")) assertTrue(Stumps.photoFor(k) in PhotoArt.KEYS)
        assertEquals("", Stumps.photoFor("NEWS"))
        for (k in PhotoArt.KEYS) {
            assertTrue(File("src/main/res/drawable-nodpi/ph_$k.jpg").exists())
            assertTrue(File("src/main/res/drawable-nodpi/ph_${k}_kit.webp").exists())
        }
        // grey kit pixel inside the mask takes the team colour; pixels outside the mask are untouched
        val grey = 0xFFC8C8C8.toInt(); val skin = 0xFF9A6B4F.toInt()
        val out = PhotoArt.tint(intArrayOf(grey, skin), intArrayOf(0xFF000000.toInt(), 0x00000000), 0xFF004BA0.toInt())
        assertEquals(skin, out[1])
        val b = out[0] and 0xFF; val r = (out[0] shr 16) and 0xFF
        assertTrue("tinted blue: r=$r b=$b", b > r + 60)
        // a very dark kit colour is lifted so folds stay visible
        val dark = PhotoArt.tint(intArrayOf(grey), intArrayOf(0xFF000000.toInt()), 0xFF050505.toInt())[0]
        assertTrue(((dark shr 8) and 0xFF) > 20)
    }

    /* ---------------------------------------------------------------- Yorker */

    @Test fun yorkerFeedFollowRepostReplyAndAutoPosts() {
        val s = career(Modes.IPL, "F_CSK", 6)
        Yorker.ensureSeeded(s, Random(1))
        assertTrue(s.yorker.posts.isNotEmpty())
        assertTrue("user team followed", Yorker.isFollowing(s, Yorker.keyOfTeam("F_CSK")))
        // automatic match updates exist after simulated days
        assertTrue(s.yorker.posts.any { it.kind == "RESULT" })
        // no verified badge for fictional / team / media accounts; estimates labelled
        val team = Yorker.account(s, Yorker.keyOfTeam("F_MI"))!!
        assertFalse(team.verified); assertTrue(team.followersLabel.endsWith("est."))
        assertFalse(Yorker.account(s, "m:desk")!!.verified)
        s.players.values.filter { Repo.byId[it.id] == null }.take(20).forEach { gp -> Yorker.account(s, Yorker.keyOfPlayer(gp.id))?.let { assertFalse("${gp.name} is fictional", it.verified) } }
        // user post, like, repost, follow
        val mine = Yorker.userPost(s, "Big game tomorrow. #CSK")!!
        assertEquals("me", mine.author); assertEquals(listOf("CSK"), mine.tags)
        assertTrue(Yorker.home(s).contains(mine))
        val p = s.yorker.posts.first { it.author != "me" }
        val l = p.likes; Yorker.toggleLike(p); assertEquals(l + 1, p.likes); Yorker.toggleLike(p); assertEquals(l, p.likes)
        Yorker.toggleRepost(s, p); assertTrue(Yorker.postsBy(s, "me").contains(p))
        val k = Yorker.keyOfTeam("F_MI")
        assertTrue(Yorker.toggleFollow(s, k)); assertTrue(Yorker.isFollowing(s, k)); assertFalse(Yorker.toggleFollow(s, k))
        // replies: the author answers the coach
        val tp = s.yorker.posts.first { it.author.startsWith("t:") || it.author.startsWith("p:") }
        val c = Yorker.reply(s, tp, "Well played!")!!
        assertTrue(tp.thread.any { it.replyTo == c.id && !it.mine })
        val page = Yorker.replyPage(s, tp, 0, 20)
        assertEquals(page.size, page.map { it.text }.toSet().size)
        // search and trending
        assertTrue(Yorker.search(s, "chennai").first.any { it.key == Yorker.keyOfTeam("F_CSK") })
        assertTrue(Yorker.trending(s).isNotEmpty())
    }

    /* ---------------------------------------------------------------- saves */

    @Test fun oldSaveMigratesWithoutLosingProgress() {
        val s = career(Modes.IPL, "F_MI", 3)
        val json = Gson().toJson(s)
        // simulate a v2.5 save: no Yorker state, no dataVersion, no comment ids, an older career year
        val old = com.google.gson.JsonParser.parseString(json).asJsonObject
        old.remove("yorker"); old.remove("dataVersion"); old.addProperty("year", 2027)
        old.getAsJsonArray("posts")?.forEach { p -> p.asJsonObject.remove("photo"); p.asJsonObject.getAsJsonArray("comments")?.forEach { c -> c.asJsonObject.remove("id") } }
        val loaded = SaveMigration.migrate(Gson().fromJson(old.toString(), CareerSave::class.java))
        assertEquals(SaveMigration.CURRENT, loaded.dataVersion)
        assertEquals("old career keeps its year", 2027, loaded.year)
        assertNotNull(loaded.yorker)
        assertEquals(s.day, loaded.day); assertEquals(s.season, loaded.season); assertEquals(s.userClubId, loaded.userClubId)
        assertEquals(s.league.sumOf { c -> c.fixtures.count { it.played } }, loaded.league.sumOf { c -> c.fixtures.count { it.played } })
        assertEquals(s.players.size, loaded.players.size)
        for (p in loaded.posts) {
            assertNotNull(p.photo)
            assertEquals(p.comments.size, p.comments.map { it.id }.toSet().size)
            assertTrue(p.comments.all { it.id != 0 })
        }
        // migration is idempotent
        val again = SaveMigration.migrate(loaded)
        assertEquals(loaded.posts.flatMap { p -> p.comments.map { it.id } }, again.posts.flatMap { p -> p.comments.map { it.id } })
    }

    /* ---------------------------------------------------------------- match report */

    @Test fun scorecardParsesStoredLinesForTheResultCardAndReport() {
        val s = career(Modes.INTL, "N_IND")
        val comp = Competition(id = "PV", name = "Border Cup", key = "PV", format = "T20", kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("pv", "N_IND", "N_AUS", "Match", 0, 1), Random(9))
        m.autoAll = true; m.simToEnd()
        val inns = Scorecard.parse(m.scorecardLines())
        assertEquals(2, inns.size)
        for (i in inns) {
            assertTrue(i.bat.size >= 2); assertTrue(i.bowl.isNotEmpty())
            assertTrue(i.overs.isNotBlank())
            // batting runs + extras = innings total
            val total = i.score.substringBefore('/').trim().toIntOrNull()
            if (total != null) assertEquals(total, i.bat.sumOf { it.runs } + i.extras)
        }
        val top = Scorecard.topBat(inns, 2)
        assertEquals(2, top.size)
        assertTrue(top[0].second.runs >= top[1].second.runs)
        assertTrue(Scorecard.topBowl(inns, 2).first().second.wk >= Scorecard.topBowl(inns, 2).last().second.wk)
        // a bowler is credited to the fielding side, i.e. he bowled in the OTHER team's innings
        for ((team, b) in Scorecard.topBowl(inns, 3)) assertTrue(inns.first { it.team != team }.bowl.contains(b))
    }

    @Test fun playedFixturesHaveCompactResultData() {
        val s = career(Modes.IPL, "F_RCB", 4)
        val played = s.league.flatMap { c -> c.fixtures.filter { it.played }.map { c to it } }
        assertTrue(played.isNotEmpty())
        for ((c, f) in played) {
            assertNotNull(f.res)
            assertTrue(Scorecard.parse(f.res!!.card).size >= 1)
            assertEquals(c, com.cricketcoach.manager.ui.screens.compOf(s, f))
            assertNotNull(Schedule.fixtureDate(s, c, f))
            assertTrue(f.venue.isNotBlank())    // official IPL 2026 venues
        }
    }
}
