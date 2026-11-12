package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.RealPlayer
import com.cricketcoach.manager.data.Repo
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Per-career Stumpstagram state (followed players, follower changes earned in the career, post counters). */
class StumpsState(
    var following: MutableList<String> = mutableListOf(),
    /** follower change per player earned during this career (added to the estimate) */
    var fDelta: MutableMap<String, Int> = mutableMapOf(),
    /** posts made in this career per player */
    var posts: MutableMap<String, Int> = mutableMapOf(),
    var seeded: Boolean = false,
)

/** One player's Stumpstagram account. Follower numbers are IN-GAME ESTIMATES, never presented as real data. */
class PlayerAccount(
    val pid: String, val name: String, val username: String, val bio: String,
    val followers: Long, val following: Int, val posts: Int, val verified: Boolean,
    /** always true: no verified, dated follower data is bundled with the game */
    val estimated: Boolean = true, val asOf: String? = null,
    val popularity: Double = 0.0, val real: Boolean = true,
) {
    val handle get() = "@$username"
    /** "12.4M est." style label */
    val followersLabel get() = Stumps.compact(followers) + if (estimated) " est." else ""
}

/**
 * Stumpstagram — the in-game social network (formerly CricGram).
 *  - Every real player in the database has an account (username, avatar = photo/initials, bio, followers, following,
 *    posts, verified badge).  Followers come from a popularity model (international + IPL caps, runs/wickets,
 *    hundreds / five-fors, fan-base size of the country, current IPL contract) and are labelled ESTIMATED.
 *  - Players post automatically after wins, losses, 50s, 100s, five-fors, hat-tricks, auction signings, transfers,
 *    trophies and career milestones; engagement scales with the performance and with the player's popularity.
 *  - Fully offline; the online (Supabase) manager feed is separate and unchanged.
 */
object Stumps {
    const val ESTIMATE_NOTE = "Follower counts are in-game estimates from each player's profile and popularity - not real, verified data."

    private val FANBASE = mapOf(
        "India" to 1.0, "Pakistan" to 0.6, "England" to 0.30, "Australia" to 0.30, "Bangladesh" to 0.24, "Sri Lanka" to 0.17,
        "South Africa" to 0.16, "New Zealand" to 0.13, "West Indies" to 0.16, "Afghanistan" to 0.20, "Zimbabwe" to 0.05, "Ireland" to 0.04,
    )

    fun compact(n: Long): String = when {
        n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
        n >= 10_000_000 -> "${n / 1_000_000}M"
        n >= 1_000_000 -> "%.1fM".format(n / 1e6)
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> "%.1fK".format(n / 1e3)
        else -> n.toString()
    }

    private fun h(s: String): Long {
        var x = 1469598103934665603L; for (c in s) { x = x xor c.code.toLong(); x *= 1099511628211L }
        x = (x xor (x ushr 31)) * -4658895280553007687L; return (x xor (x ushr 29)) and Long.MAX_VALUE
    }

    /* ------------------------------------------------------------------ popularity model */

    /** Relative popularity score (0..~1000) of a real player from the bundled career data. */
    fun popularity(rp: RealPlayer): Double {
        fun m(f: String) = (rp.off?.get(f)?.m ?: rp.stats[f]?.m?.toDouble() ?: 0.0)
        fun runs(f: String) = (rp.off?.get(f)?.runs ?: rp.stats[f]?.runs?.toDouble() ?: 0.0)
        fun wk(f: String) = (rp.off?.get(f)?.wk ?: rp.stats[f]?.wk?.toDouble() ?: 0.0)
        fun h100(f: String) = (rp.off?.get(f)?.h100 ?: rp.stats[f]?.f100?.toDouble() ?: 0.0)
        fun w5(f: String) = (rp.off?.get(f)?.w5 ?: rp.stats[f]?.w5?.toDouble() ?: 0.0)
        val intl = m("TEST") * 1.25 + m("ODI") + m("T20I") * 0.9
        val ipl = (rp.stats["IPL"]?.m ?: 0).toDouble()
        val output = (runs("TEST") + runs("ODI") + runs("T20I")) / 100.0 + (wk("TEST") + wk("ODI") + wk("T20I")) * 0.45 +
            (h100("TEST") + h100("ODI") + h100("T20I")) * 9 + (w5("TEST") + w5("ODI")) * 3 + (rp.stats["IPL"]?.runs ?: 0) / 120.0 + (rp.stats["IPL"]?.wk ?: 0) * 0.35
        val active = rp.iplLast?.take(4)?.toIntOrNull()?.let { if (it >= 2025) 1.15 else 1.0 } ?: 1.0
        return (intl + ipl * 1.2 + output) * active
    }

    /** Estimated follower count for a real player. Monotonic in [popularity]; India / IPL exposure lift it. */
    fun estimateFollowers(rp: RealPlayer): Long {
        val pop = popularity(rp)
        val fan = FANBASE[rp.country] ?: 0.06
        val iplBoost = if (rp.ipl != null) 1.6 else 1.0
        // a broad base (every capped player has a following) plus a steep star term (the superstar long tail)
        val raw = 9000.0 + (pop.pow(3.0) * 0.017 + 300.0 * pop.pow(1.5)) * fan * iplBoost
        // personal jitter (+-12%) so equal profiles don't show identical numbers; rounded like a real counter
        val j = 0.88 + (h(rp.id) % 2400) / 10000.0
        val v = (raw * j).coerceAtMost(3.2e8)
        return roundNice(v)
    }

    private fun roundNice(v: Double): Long = when {
        v >= 1e7 -> (v / 1e5).roundToInt() * 100_000L
        v >= 1e5 -> (v / 1e3).roundToInt() * 1000L
        else -> (v / 10).roundToInt() * 10L
    }

    /* ------------------------------------------------------------------ usernames */

    private val usernames: Map<String, String> by lazy {
        val used = HashSet<String>(); val out = HashMap<String, String>()
        // most popular first, so the big names get the clean handle
        val ordered = Repo.ds.players.sortedByDescending { popularity(it) }
        for (rp in ordered) out[rp.id] = uniqueName(rp.name, rp.id, used)
        out
    }

    private fun slug(name: String): String {
        val parts = name.lowercase().replace(Regex("[^a-z ]"), "").split(' ').filter { it.isNotBlank() }
        return when { parts.isEmpty() -> "player"; parts.size == 1 -> parts[0]; else -> parts.first() + "_" + parts.last() }.take(22)
    }

    private fun uniqueName(name: String, id: String, used: MutableSet<String>): String {
        val base = slug(name)
        val num = (abs(id.hashCode()) % 98) + 1     // same as the shirt number
        for (c in listOf("$base$num", "${base}_$num", "${base}_cricket", "${base}_official", "$base${num}_${h(id) % 900 + 100}")) if (used.add(c)) return c
        var k = 2; while (!used.add("$base$num$k")) k++; return "$base$num$k"
    }

    /* ------------------------------------------------------------------ accounts */

    fun account(save: CareerSave?, pid: String): PlayerAccount? {
        val rp = Repo.byId[pid]
        val gp = save?.players?.get(pid)
        if (rp == null && gp == null) return null
        val name = gp?.name ?: rp!!.name
        val delta = save?.stumps?.fDelta?.get(pid) ?: 0
        val myPosts = save?.stumps?.posts?.get(pid) ?: 0
        val hh = h(pid)
        if (rp != null) {
            val base = estimateFollowers(rp)
            val pop = popularity(rp)
            val intlCaps = listOf("TEST", "ODI", "T20I").sumOf { rp.stats[it]?.m ?: 0 }
            val verified = intlCaps >= 10 || (rp.stats["IPL"]?.m ?: 0) >= 25 || base >= 1_000_000
            val posts = (40 + pop * 0.9 + hh % 120).toInt() + myPosts
            val following = (60 + hh % 640).toInt()
            return PlayerAccount(pid, name, usernames[pid] ?: uniqueName(name, pid, HashSet()), bio(save, rp, gp), max(50L, base + delta), following, posts, verified,
                popularity = pop, real = true)
        }
        // generated / fictional players: small accounts, never verified
        val base = 600L + (hh % 9000) + (gp!!.overall() - 40).coerceAtLeast(0) * 120L
        val user = slug(name) + "_" + (hh % 900 + 100)
        return PlayerAccount(pid, name, user, "🏏 ${Consts.ROLE_NAMES[gp.role] ?: gp.role} · ${gp.country} · fictional player", max(50L, base + delta),
            (40 + hh % 300).toInt(), (6 + hh % 40).toInt() + myPosts, false, popularity = 0.0, real = false)
    }

    private fun bio(save: CareerSave?, rp: RealPlayer, gp: GPlayer?): String {
        val role = Consts.ROLE_NAMES[gp?.role ?: rp.role] ?: rp.role
        val club = gp?.clubId?.let { save?.clubs?.get(it)?.name } ?: rp.ipl
        val h100 = listOf("TEST", "ODI", "T20I").sumOf { (rp.off?.get(it)?.h100 ?: rp.stats[it]?.f100?.toDouble() ?: 0.0).toInt() }
        val wk = listOf("TEST", "ODI", "T20I").sumOf { (rp.off?.get(it)?.wk ?: rp.stats[it]?.wk?.toDouble() ?: 0.0).toInt() }
        val feat = when { h100 >= 5 -> "$h100 international 100s"; wk >= 100 -> "$wk international wickets"; h100 > 0 -> "International centurion"; else -> null }
        return listOfNotNull("🏏 $role", rp.country, club, feat).joinToString(" · ")
    }

    /** All real-player accounts, most-followed first. */
    fun directory(save: CareerSave?): List<PlayerAccount> = Repo.ds.players.mapNotNull { account(save, it.id) }.sortedByDescending { it.followers }

    /* ------------------------------------------------------------------ follow / feeds */

    fun isFollowing(save: CareerSave, pid: String) = pid in save.stumps.following
    fun toggleFollow(save: CareerSave, pid: String): Boolean {
        val st = save.stumps
        return if (st.following.remove(pid)) { bump(save, pid, -1); false } else { st.following.add(pid); bump(save, pid, 1); true }
    }

    /** posts by or about the players the user follows */
    fun followingFeed(save: CareerSave): List<SocialPost> {
        val f = save.stumps.following.toHashSet()
        return save.posts.filter { it.authorPid in f || (it.authorPid.isEmpty() && it.pid in f) }
    }

    /**
     * "For You" ranking (v2.5 Phase G): engagement on a log scale (so a 300M-follower superstar doesn't bury everyone),
     * recency, poster/story bonus, and a diversity penalty so no single account takes over the top of the feed.
     */
    fun forYou(save: CareerSave, limit: Int = 80): List<SocialPost> {
        val pool = save.posts.take(160)
        val uc = save.clubs[save.userClubId]
        fun base(i: Int, p: SocialPost): Double {
            val eng = kotlin.math.log10(10.0 + p.likes + max(p.comments.size, p.commentCount) * 40.0)
            var s = eng * 10 - i * 0.35
            if (p.headline.isNotEmpty()) s += 4
            if (p.mine) s += 6
            if (uc != null && (p.authorPid in uc.squad || p.pid in uc.squad || p.club == uc.id)) s += 5
            if (p.authorPid.isNotEmpty() && p.authorPid in save.stumps.following) s += 4
            return s
        }
        val scored = pool.mapIndexed { i, p -> p to base(i, p) }.toMutableList()
        val seen = HashMap<String, Int>()
        val out = ArrayList<SocialPost>()
        while (scored.isNotEmpty() && out.size < limit) {
            var bi = 0; var bv = Double.NEGATIVE_INFINITY
            for ((k, pr) in scored.withIndex()) {
                val who = pr.first.authorPid.ifEmpty { pr.first.handle }
                val v = pr.second - 9.0 * (seen[who] ?: 0)
                if (v > bv) { bv = v; bi = k }
            }
            val p = scored.removeAt(bi).first
            val who = p.authorPid.ifEmpty { p.handle }
            seen[who] = (seen[who] ?: 0) + 1
            out += p
        }
        return out
    }

    fun postsBy(save: CareerSave, pid: String): List<SocialPost> = save.posts.filter { it.authorPid == pid || (it.authorPid.isEmpty() && it.pid == pid) }

    private fun bump(save: CareerSave, pid: String, d: Int) { save.stumps.fDelta[pid] = (save.stumps.fDelta[pid] ?: 0) + d }

    /* ------------------------------------------------------------------ engagement */

    /** Performance weight of a post kind (scaled by [perf], e.g. runs over 100 or wickets over 5). */
    fun perfFactor(kind: String, perf: Double = 0.0): Double = when (kind) {
        "HATTRICK" -> 2.4; "TROPHY", "TITLE" -> 2.6; "RECORD" -> 2.2
        "CENTURY" -> 1.6 + perf * 0.006; "FIVEFOR" -> 1.5 + perf * 0.2; "FIFTY" -> 0.9 + perf * 0.004
        "MILESTONE" -> 1.4; "AUCTION", "TRANSFER", "SIGNING" -> 1.1 + min(perf, 20.0) * 0.04
        "WIN" -> 1.0; "LOSS" -> 0.45; else -> 0.6
    }

    /** Likes for a post by an account with [followers] (deterministic expectation, jittered by [rnd]). */
    fun likesFor(followers: Long, kind: String, perf: Double, rnd: Random?): Int {
        val rate = 0.018 / (1.0 + ln(1.0 + followers / 2.0e7))        // big accounts get a lower like-rate
        val j = rnd?.let { 0.8 + it.nextDouble() * 0.4 } ?: 1.0
        return (followers * rate * perfFactor(kind, perf) * j).coerceIn(5.0, 2.0e9).toInt()
    }

    fun commentsFor(likes: Int): Int = max(1, (likes * 0.012).toInt())

    /** Followers gained from a post (performance x reach). */
    fun followerGain(followers: Long, kind: String, perf: Double): Int =
        (followers * 0.0015 * perfFactor(kind, perf) + 40 * perfFactor(kind, perf)).toInt().let { if (kind == "LOSS") -it / 3 else it }

    /* ------------------------------------------------------------------ player posts */

    private val OPEN = mapOf(
        "WIN" to listOf("Job done ✅ Thanks for the noise tonight!", "That is how we play our cricket 🔥", "What a team effort today! 💪", "Two points in the bag. Onto the next one 🙌", "Proud of this group. Great win! 🏏", "Big win, bigger smiles 😁"),
        "LOSS" to listOf("Credit to the opposition. We go again.", "Head up, eyes forward. Next game 🏏", "Not our day. We'll come back stronger. 🙏", "Tough one to take. Back to work tomorrow.", "Thank you for the support. We learn and move on.", "Gutted, but this team will bounce back 💪"),
        "FIFTY" to listOf("Building something here 🧱🏏", "Half-century and a lesson or two. On we go!", "Felt good out there today 🏏", "Happy to contribute for the team. 50 and counting!", "Fifty up - wish I'd carried on, but a win's a win.", "Good to spend time in the middle 🙌"),
        "CENTURY" to listOf("Raise the bat moment 🏏🙌 Love you all!", "Patience. Process. Hundred. 💯", "Not out and smiling 😄💯", "Special feeling. Thank you all for the love! 💯", "One of those days when everything clicked 🏏💯", "Dedicating this hundred to the team and the fans ❤️", "Hard work pays off. Hundred! 🙏"),
        "FIVEFOR" to listOf("Kept it simple, hit the deck hard 🎯", "Five in the book! Team effort though 🙌", "Ball came out nicely today 🎯", "Five-for! Grateful for the field-setting and the catches 🙌", "Days like these make all the hard yards worth it 🔥", "Big thanks to my captain and the boys 🙏"),
        "HATTRICK" to listOf("HAT-TRICK!!! Still can't believe it 🤯🎯", "Three in three! What a feeling 🔥🔥🔥", "Dreamt about this as a kid. Hat-trick! 🙏"),
        "AUCTION" to listOf("Excited to join %s! Can't wait to get started 🙌", "New colours, same hunger. Let's go %s! 💪", "Thank you %s for the faith. See you soon, fans! 🏏"),
        "TRANSFER" to listOf("New chapter with %s! Let's do this 💪", "Happy to be part of %s. Can't wait to play in front of you all 🙌"),
        "TROPHY" to listOf("Silverware! 🏆 This one is for the fans", "CHAMPIONS!!! 🏆 What a journey with this team ❤️", "We did it! 🏆 Thank you everyone who believed in us", "Trophy in hand. Memories for life 🏆🙏"),
        "MILESTONE" to listOf("Grateful for every run and every wicket on this journey 🙏", "A milestone I'll never forget - thank you all ❤️", "Numbers don't tell the whole story, but this one's special 🏏"),
    )

    /** Post as player [pid]; likes/comments/follower gain scale with [kind]/[perf] and the player's followers. */
    fun playerPost(save: CareerSave, pid: String, kind: String, perf: Double, text: String, rnd: Random, poster: Social.Poster? = null): SocialPost? {
        val acc = account(save, pid) ?: return null
        val likes = likesFor(acc.followers, kind, perf, rnd)
        val a = Social.Account(acc.name, acc.handle, acc.verified)
        val p = Social.post(save, a, text, kind, likes, rnd, 3, poster)
        p.authorPid = pid
        p.commentCount = max(p.comments.size, commentsFor(likes))
        save.stumps.posts[pid] = (save.stumps.posts[pid] ?: 0) + 1
        bump(save, pid, followerGain(acc.followers, kind, perf))
        return p
    }

    private fun line(kind: String, rnd: Random, club: String = ""): String {
        val l = OPEN[kind]?.random(rnd) ?: "🏏"
        return if (l.contains("%s")) l.format(club) else l
    }

    private fun popular(save: CareerSave, pid: String, min: Long) = (account(save, pid)?.followers ?: 0L) >= min

    /** Hat-tricks in a match: bowler -> innings index, from the recorded deliveries (wides / no-balls don't break it, run-outs don't count). */
    fun hatTricks(events: List<BallEv>): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        val streak = HashMap<String, Int>()
        var curInn = -1
        for (e in events) {
            if (e.inn != curInn) { streak.clear(); curInn = e.inn }
            if (e.kind == "WIDE" || e.kind == "NB") continue
            val bowlerWicket = e.kind == "WICKET" && !e.how.startsWith("run out") && !e.how.contains("retired")
            val n = if (bowlerWicket) (streak[e.bowler] ?: 0) + 1 else 0
            streak[e.bowler] = n
            if (n == 3) out += e.bowler to e.inn
        }
        return out
    }

    /** Career milestone crossed by this match: (before, after) totals -> label or null. */
    fun milestone(beforeRuns: Int, afterRuns: Int, beforeWk: Int, afterWk: Int, beforeM: Int, afterM: Int): String? {
        for (t in listOf(10000, 5000, 3000, 2000, 1000)) if (beforeRuns < t && afterRuns >= t) return "$t career runs"
        for (t in listOf(500, 300, 200, 100, 50)) if (beforeWk < t && afterWk >= t) return "$t career wickets"
        for (t in listOf(300, 200, 100, 50)) if (beforeM < t && afterM >= t) return "$t career matches"
        return null
    }

    /**
     * Player posts for one finished match (called from Social.onMatch after the stats are applied).
     * Volume control: posts come from the user's matches, from user-squad players, and from very popular players.
     */
    fun onMatch(save: CareerSave, comp: Competition, fx: Fixture, sim: MatchSim, rnd: Random) {
        val u = save.userClubId
        val uc = save.clubs[u] ?: return
        val mine = fx.home == u || fx.away == u
        fun relevant(pid: String) = mine || pid in uc.squad || pid in save.stumps.following || popular(save, pid, 5_000_000)
        val posted = HashSet<String>()
        // hundreds / fifties - best innings first so a Test player with two scores posts once, about the bigger one
        val cards = sim.innings.flatMap { inn -> inn.bc.map { inn to it } }.sortedByDescending { it.second.runs }
        for ((inn, c) in cards) {
            if (!c.batted || c.runs < 50 || !relevant(c.pid) || c.pid in posted) continue
            val team = sim.club(inn.batTeam)
            if (c.runs >= 100) {
                val opp = sim.club(inn.bowlTeam)
                val poster = Social.Poster(c.pid, team.id, opp.id, "CENTURY", "${c.runs}${if (!c.out) "*" else ""}",
                    listOf("BALLS|${c.balls}", "4s / 6s|${c.fours} / ${c.sixes}", "STRIKE RATE|${"%.1f".format(if (c.balls > 0) c.runs * 100.0 / c.balls else 0.0)}", "VS|${opp.short}"))
                playerPost(save, c.pid, "CENTURY", (c.runs - 100).toDouble(), "${line("CENTURY", rnd)} ${c.runs}${if (!c.out) "*" else ""} (${c.balls}) v ${opp.short} #${team.short} #Century", rnd, poster)
                posted += c.pid
            } else if (c.runs >= 70 || c.pid in uc.squad || c.pid in save.stumps.following) {
                if (c.pid !in posted && rnd.nextDouble() < 0.7) {
                    playerPost(save, c.pid, "FIFTY", (c.runs - 50).toDouble(), "${line("FIFTY", rnd)} ${c.runs}${if (!c.out) "*" else ""} (${c.balls}) #${team.short}", rnd)
                    posted += c.pid
                }
            }
        }
        // five-fors
        for ((inn, b) in sim.innings.flatMap { inn -> inn.bw.values.map { inn to it } }.sortedByDescending { it.second.wk * 1000 - it.second.runs }) {
            if (b.wk < 5 || !relevant(b.pid) || b.pid in posted) continue
            val team = sim.club(inn.bowlTeam); val opp = sim.club(inn.batTeam)
            val poster = Social.Poster(b.pid, team.id, opp.id, "FIVE-FOR", "${b.wk}/${b.runs}",
                listOf("OVERS|${b.oversText}", "MAIDENS|${b.maidens}", "ECONOMY|${"%.2f".format(b.econ)}", "VS|${opp.short}"))
            playerPost(save, b.pid, "FIVEFOR", (b.wk - 5).toDouble(), "${line("FIVEFOR", rnd)} ${b.wk}/${b.runs} #${team.short} #FiveFor", rnd, poster)
            posted += b.pid
        }
        // hat-tricks (always posted, they're rare)
        for ((bowler, innIdx) in hatTricks(sim.events)) {
            val inn = sim.innings.getOrNull(innIdx) ?: sim.innings.firstOrNull { bowler in it.bw } ?: continue
            val team = sim.club(inn.bowlTeam)
            if (bowler in posted) continue
            val p = playerPost(save, bowler, "HATTRICK", 0.0, "${line("HATTRICK", rnd)} #${team.short} #HatTrick", rnd) ?: continue
            posted += bowler
            save.players[bowler]?.let { Social.post(save, Social.STATS, "HAT-TRICK! ${it.name} takes three in three for ${team.name} (${comp.name}). #HatTrick", "HATTRICK", p.likes / 2 + 500, rnd, 3) }
        }
        // win / loss: the user side's standout performer speaks (if he hasn't already)
        if (mine) {
            val star = Social.topPerformer(sim, u)
            if (star != null && star !in posted) {
                when {
                    sim.winner == u -> { playerPost(save, star, "WIN", 0.0, "${line("WIN", rnd)} #${uc.short}", rnd); posted += star }
                    sim.winner != null && rnd.nextDouble() < 0.6 -> { playerPost(save, star, "LOSS", 0.0, "${line("LOSS", rnd)} #${uc.short}", rnd); posted += star }
                }
            }
        }
        // career milestones (career mode totals) for players who took part
        val inMatch = sim.innings.flatMap { it.bc.filter { b -> b.batted }.map { b -> b.pid } + it.bw.keys }.toSet()
        for (pid in inMatch) {
            if (pid in posted || !relevant(pid)) continue
            val gp = save.players[pid] ?: continue
            val r = sim.innings.flatMap { it.bc }.filter { it.pid == pid }.sumOf { it.runs }
            val w = sim.innings.flatMap { it.bw.values }.filter { it.pid == pid }.sumOf { it.wk }
            val ms = milestone(gp.careerRuns - r, gp.careerRuns, gp.careerWk - w, gp.careerWk, gp.careerM - 1, gp.careerM) ?: continue
            playerPost(save, pid, "MILESTONE", 0.0, "${line("MILESTONE", rnd)} $ms ✅ #Milestone", rnd)
            posted += pid
        }
    }

    fun onSigning(save: CareerSave, p: GPlayer, clubName: String, kind: String, fee: Double, rnd: Random) {
        playerPost(save, p.id, kind, fee / max(0.2, save.economy), "${line(kind, rnd, clubName)} #${kind.lowercase().replaceFirstChar { it.uppercase() }}", rnd)
    }

    fun onTitle(save: CareerSave, comp: Competition, champ: Club, rnd: Random) {
        val who = (listOf(champ.captain) + champ.xi.take(11).sortedByDescending { save.players[it]?.overall() ?: 0 }.take(2)).filter { it.isNotBlank() }.distinct()
        for (pid in who) playerPost(save, pid, "TROPHY", 0.0, "${line("TROPHY", rnd)} #${champ.short} #Champions", rnd)
    }

    /** First open of Stumpstagram in a career: a handful of pre-season posts from popular players, so the feed isn't empty. */
    fun ensureSeeded(save: CareerSave, rnd: Random = Random.Default) {
        if (save.stumps.seeded) return
        save.stumps.seeded = true
        val uc = save.clubs[save.userClubId]
        val mine = uc?.squad.orEmpty().filter { Repo.byId[it] != null }.sortedByDescending { account(save, it)?.followers ?: 0 }.take(3)
        val stars = save.players.values.filter { it.real && !it.retired && Repo.byId[it.id] != null }.sortedByDescending { account(save, it.id)?.followers ?: 0 }.take(5).map { it.id }
        val lines = listOf("Pre-season grind 💪 Who's ready?", "Back in the nets. Feeling good 🏏", "Fitness first. New season, new goals 🎯", "Can't wait to get going this season! 🙌", "Hard work in silence 🤫🏏")
        for ((i, pid) in (mine + stars).distinct().withIndex()) {
            val tag = save.players[pid]?.clubId?.let { save.clubs[it]?.short } ?: "Cricket"
            playerPost(save, pid, "NEWS", 0.0, "${lines[i % lines.size]} #$tag #NewSeason", rnd)
        }
    }
}
