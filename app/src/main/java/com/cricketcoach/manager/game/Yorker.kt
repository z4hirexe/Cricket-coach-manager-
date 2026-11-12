package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.Repo
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/** One Yorker post. [author] is an account key: "p:<pid>" player, "t:<clubId>" team, "m:<id>" media desk, "me" the coach. */
class YorkerPost(
    var id: Int = 0, var author: String = "", var text: String = "", var kind: String = "NEWS",
    /** photo moment key (same artwork set as Stumpstagram, kit tinted to [club]) */
    var photo: String = "", var pid: String = "", var club: String = "", var opp: String = "", var stat: String = "",
    var likes: Int = 0, var reposts: Int = 0, var replies: Int = 0, var liked: Boolean = false, var reposted: Boolean = false,
    var season: Int = 1, var day: Int = 0, var date: String = "", var tags: MutableList<String> = mutableListOf(),
    /** stored replies: the coach's replies and the answers they received */
    var thread: MutableList<SocialComment> = mutableListOf(),
    var matchKey: String = "",
)

/** Per-career Yorker state. */
class YorkerState(
    var posts: MutableList<YorkerPost> = mutableListOf(),
    var following: MutableList<String> = mutableListOf(),
    var nextId: Int = 1, var seenId: Int = 0, var seeded: Boolean = false,
    var myFollowers: Int = 0, var myFollowing: Int = 0,
)

/**
 * Yorker — the in-game text + photo social network (v2.6). An ORIGINAL network: its own name, mark (three stumps and a
 * ball at the base) and colours; no third-party branding. Fully offline and generated from real career events:
 * results of every match (including other teams), standout performances, auction sales, transfers and titles.
 * Follower numbers are in-game estimates (labelled "est."); fictional players are never verified.
 */
object Yorker {
    const val NAME = "Yorker"
    const val TAGLINE = "Cricket, as it happens."
    const val ESTIMATE_NOTE = "Follower counts on Yorker are in-game estimates, not real data."
    private const val MAX_POSTS = 320

    class Acct(
        val key: String, val name: String, val handle: String, val followers: Long, val following: Int, val verified: Boolean,
        val bio: String, val color: Long, val pid: String = "", val clubId: String = "", val estimated: Boolean = true,
    ) { val followersLabel get() = Stumps.compact(followers) + if (estimated) " est." else "" }

    private val MEDIA = mapOf(
        "desk" to Triple("Yorker Cricket Desk", "@YorkerDesk", "Match updates, breaking news and results from every competition."),
        "stats" to Triple("Numbers Game", "@NumbersGame", "Records, milestones and the stats behind the headlines."),
        "auction" to Triple("Auction Room Live", "@AuctionRoomLive", "Every bid, every sale, every surprise."),
        "fans" to Triple("Stands Se Live", "@StandsSeLive", "The fan view from the stands. Opinions included."),
    )

    private fun mix(z0: Long): Long { var z = z0 + -0x61c8864680b583ebL; z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L; z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L; return z xor (z ushr 31) }

    fun keyOfPlayer(pid: String) = "p:$pid"
    fun keyOfTeam(id: String) = "t:$id"

    /** Account for a key (null when the player / team does not exist). */
    fun account(save: CareerSave, key: String): Acct? = when {
        key == "me" -> Acct("me", "${save.manager} (Coach)", meHandle(save), (save.yorker.myFollowers + save.followers / 5).toLong(), save.yorker.following.size,
            false, "Head coach of ${save.clubs[save.userClubId]?.name ?: "-"}.", save.clubs[save.userClubId]?.primary ?: 0xFFE9B949, clubId = save.userClubId, estimated = false)
        key.startsWith("p:") -> {
            val pid = key.substring(2)
            Stumps.account(save, pid)?.let { a ->
                val gp = save.players[pid]
                // Yorker audiences are smaller than photo-app audiences: ~30% of the Stumpstagram estimate
                Acct(key, a.name, "@" + a.username.replace('.', '_'), max(40L, (a.followers * 0.30).toLong()), (a.following * 0.6).toInt() + 20,
                    a.verified && a.real, a.bio, gp?.clubId?.let { save.clubs[it]?.primary } ?: 0xFF4F8EF7, pid = pid, clubId = gp?.clubId ?: "")
            }
        }
        key.startsWith("t:") -> save.clubs[key.substring(2)]?.let { c ->
            val base = when (c.kind) { "FRANCHISE" -> 2_500_000L; "NATION" -> 1_800_000L; "DOMESTIC" -> 45_000L; else -> 12_000L }
            val fanbase = when (c.country) { "India" -> 3.0; "Pakistan" -> 1.6; "England", "Australia" -> 1.2; else -> 0.7 }
            val f = (base * (0.4 + c.reputation / 80.0) * (if (c.kind == "NATION") fanbase else 1.0)).toLong()
            Acct(key, c.name, "@" + c.short.filter { it.isLetterOrDigit() } + if (c.kind == "DOMESTIC") "Cricket" else "", f, 120 + (abs(c.id.hashCode()) % 300),
                false, "Official-style fan account of ${c.name} in this career (fictional account).", c.primary, clubId = c.id)
        }
        key.startsWith("m:") -> MEDIA[key.substring(2)]?.let { (n, h, b) ->
            val f = when (key) { "m:desk" -> 4_200_000L; "m:stats" -> 860_000L; "m:auction" -> 1_100_000L; else -> 640_000L }
            Acct(key, n, h, f, 300, false, "$b Fictional account.", 0xFFE9B949)
        }
        else -> null
    }

    fun meHandle(save: CareerSave) = "@coach_${save.manager.filter { it.isLetterOrDigit() }.lowercase().take(12).ifBlank { "cmp" }}"

    private fun engagement(followers: Long, kind: String, rnd: Random): Int {
        val rate = 0.012 / (1.0 + ln(1.0 + followers / 1.5e7))
        val k = when (kind) { "CENTURY" -> 1.7; "FIVEFOR" -> 1.6; "HATTRICK" -> 2.4; "TROPHY", "TITLE" -> 2.6; "AUCTION", "TRANSFER" -> 1.3; "RESULT" -> 0.9; "LOSS" -> 0.5; else -> 0.8 }
        return (followers * rate * k * (0.8 + rnd.nextDouble() * 0.4)).toInt().coerceAtLeast(3)
    }

    /** Create a post as [author]; engagement scales with the author's (estimated) audience. */
    fun post(save: CareerSave, author: String, text: String, kind: String, rnd: Random, photo: String = "", pid: String = "", club: String = "",
             opp: String = "", stat: String = "", matchKey: String = ""): YorkerPost? {
        val acc = account(save, author) ?: return null
        val st = save.yorker
        if (matchKey.isNotEmpty() && st.posts.any { it.matchKey == matchKey && it.author == author }) return null   // one post per author per match
        val likes = engagement(acc.followers, kind, rnd)
        val p = YorkerPost(st.nextId++, author, text.take(280), kind, photo, pid, club, opp, stat, likes, likes / (6 + rnd.nextInt(6)), max(1, likes / (25 + rnd.nextInt(20))),
            season = save.season, day = save.day, date = runCatching { Schedule.today(save).toString() }.getOrDefault(""),
            tags = Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toMutableList(), matchKey = matchKey)
        st.posts.add(0, p)
        while (st.posts.size > MAX_POSTS) st.posts.removeAt(st.posts.size - 1)
        return p
    }

    private fun tag(comp: Competition) = when (comp.key) {
        "RANJI" -> "RanjiTrophy"; "SMAT" -> "SMAT"; "VHT" -> "VijayHazare"; "DULEEP" -> "DuleepTrophy"; "IRANI" -> "IraniCup"
        "IPL" -> "IPL${comp.name.takeLast(4).filter { it.isDigit() }}"; "ODI_WC" -> "CWC"; "T20_WC" -> "T20WorldCup"; "CT" -> "ChampionsTrophy"
        else -> comp.name.filter { it.isLetterOrDigit() }.take(16)
    }

    /** Match updates: every result is posted by the winning team (big competitions) and the desk covers the user's games. */
    fun onMatch(save: CareerSave, comp: Competition, fx: Fixture, sim: MatchSim, rnd: Random = Random.Default) {
        val r = fx.res ?: return
        val h = save.clubs[fx.home] ?: return; val a = save.clubs[fx.away] ?: return
        val key = "${comp.id}:${fx.id}"
        val mine = fx.home == save.userClubId || fx.away == save.userClubId
        val big = comp.kind == "FRANCHISE" || comp.kind == "NATION"
        val ht = "#${h.short.filter { it.isLetterOrDigit() }}v${a.short.filter { it.isLetterOrDigit() }}"
        val score = "${h.short} ${r.hScore} · ${a.short} ${r.aScore}"
        if (mine || big || (abs(mix(fx.id.hashCode().toLong())) % 4 == 0L)) {
            val w = r.winner?.let { save.clubs[it] }
            if (w != null) post(save, keyOfTeam(w.id), "FULL TIME | ${r.text}. $score $ht #${tag(comp)}", "RESULT", rnd, club = w.id, opp = if (w.id == h.id) a.id else h.id, matchKey = key)
            else post(save, "m:desk", "${comp.name}: ${r.text}. $score $ht", "RESULT", rnd, matchKey = key)
        }
        if (mine) {
            val potm = r.potm.takeIf { it.isNotEmpty() }?.let { save.players[it]?.name }
            post(save, "m:desk", "${fx.stage} | ${h.name} v ${a.name}: ${r.text}.${if (potm != null) " Player of the match: $potm." else ""} $ht", "RESULT", rnd, matchKey = key)
            val u = save.userClub()
            if (r.winner == u.id) post(save, "m:fans", listOf("What a night for ${u.short} fans! 🔥", "${u.short} ka jalwa! Another win in the bag.", "Coach ${save.manager} getting the tactics right 👏").random(rnd) + " #${u.short}", "WIN", rnd, club = u.id, matchKey = key)
            else if (r.winner != null) post(save, "m:fans", listOf("Tough watch for ${u.short} fans today. Changes needed?", "${u.short} need answers after that one.", "Back to the drawing board for ${u.short}.").random(rnd) + " #${u.short}", "LOSS", rnd, club = u.id, matchKey = key)
        }
        // standout performances (any match): players post them themselves with a photo, the stats account adds context
        val relevantPid = { pid: String -> mine || pid in save.userClub().squad || keyOfPlayer(pid) in save.yorker.following || (Stumps.account(save, pid)?.followers ?: 0) >= 8_000_000 }
        for (inn in sim.innings) for (c in inn.bc) {
            if (!c.batted || c.runs < 100 || !relevantPid(c.pid)) continue
            val team = sim.club(inn.batTeam); val opp = sim.club(inn.bowlTeam)
            val st = "${c.runs}${if (!c.out) "*" else ""} (${c.balls})"
            post(save, keyOfPlayer(c.pid), listOf("Grateful for days like this 🙏💯", "Hundred! Love you all ❤️", "One of those days 🏏💯").random(rnd) + " $st v ${opp.short} #${team.short.filter { it.isLetterOrDigit() }}",
                "CENTURY", rnd, photo = "century", pid = c.pid, club = team.id, opp = opp.id, stat = st, matchKey = key)
            post(save, "m:stats", "${save.players[c.pid]?.name} ${st} for ${team.name} against ${opp.name} - ${c.fours} fours, ${c.sixes} sixes. #${tag(comp)}", "CENTURY", rnd, pid = c.pid, matchKey = key + ":" + c.pid)
        }
        for (inn in sim.innings) for (b in inn.bw.values) {
            if (b.wk < 5 || !relevantPid(b.pid)) continue
            val team = sim.club(inn.bowlTeam); val opp = sim.club(inn.batTeam)
            post(save, keyOfPlayer(b.pid), "Five! 🎯 ${b.wk}/${b.runs} v ${opp.short}. Team effort, great catches 🙌 #${team.short.filter { it.isLetterOrDigit() }}", "FIVEFOR", rnd,
                photo = "fivefor", pid = b.pid, club = team.id, opp = opp.id, stat = "${b.wk}/${b.runs}", matchKey = key)
        }
        for ((bowler, innIdx) in Stumps.hatTricks(sim.events)) {
            val inn = sim.innings.getOrNull(innIdx) ?: continue
            val team = sim.club(inn.bowlTeam)
            post(save, "m:stats", "HAT-TRICK! ${save.players[bowler]?.name} takes three in three for ${team.name}. #HatTrick #${tag(comp)}", "HATTRICK", rnd, photo = "hattrick", pid = bowler, club = team.id, matchKey = "$key:ht:$bowler")
        }
    }

    fun onSigning(save: CareerSave, p: GPlayer, club: Club, kind: String, fee: Double, rnd: Random = Random.Default) {
        val price = if (fee > 0) Money.fmt(fee) else "a free transfer"
        if (kind == "AUCTION") post(save, "m:auction", "SOLD! ${p.name} to ${club.name} for $price. #Auction #${club.short}", "AUCTION", rnd, photo = "signing", pid = p.id, club = club.id, stat = price)
        else post(save, "m:desk", "DONE DEAL: ${p.name} joins ${club.name} ($price). #Transfer #${club.short}", "TRANSFER", rnd, photo = "signing", pid = p.id, club = club.id, stat = price)
        post(save, keyOfTeam(club.id), "Welcome to ${club.name}, ${p.name}! 💙 #${club.short}", kind, rnd, pid = p.id, club = club.id)
        post(save, keyOfPlayer(p.id), "New colours, same hunger. Let's go ${club.short}! 🏏", kind, rnd, photo = "signing", pid = p.id, club = club.id)
    }

    /** Big auction sales to other clubs (news only). */
    fun auctionSale(save: CareerSave, p: GPlayer, club: Club, price: Double, rnd: Random = Random.Default) {
        post(save, "m:auction", "SOLD! ${p.name} goes to ${club.name} for ${Money.fmt(price)}. #Auction #${club.short}", "AUCTION", rnd, pid = p.id, club = club.id, stat = Money.fmt(price))
    }

    fun onTitle(save: CareerSave, comp: Competition, champ: Club, rnd: Random = Random.Default) {
        post(save, keyOfTeam(champ.id), "🏆 CHAMPIONS! ${champ.name} win the ${comp.name}! #${champ.short} #${tag(comp)}", "TROPHY", rnd, photo = "trophy", club = champ.id)
        post(save, "m:desk", "${champ.name} are the ${comp.name} champions. #${tag(comp)}", "TROPHY", rnd, club = champ.id)
    }

    /** First open in a career: a few posts so the feed is not empty, and follow the user's team + stars. */
    fun ensureSeeded(save: CareerSave, rnd: Random = Random.Default) {
        val st = save.yorker
        if (st.seeded) return
        st.seeded = true
        val u = save.clubs[save.userClubId] ?: return
        if (st.following.isEmpty()) {
            st.following.add(keyOfTeam(u.id)); st.following.add("m:desk")
            u.squad.filter { Repo.byId[it] != null }.sortedByDescending { Stumps.account(save, it)?.followers ?: 0 }.take(3).forEach { st.following.add(keyOfPlayer(it)) }
        }
        post(save, "m:desk", "Welcome to the ${save.year} season! Fixtures, results and breaking news - all here. #Season${save.year}", "NEWS", rnd)
        post(save, keyOfTeam(u.id), "${u.name} announce ${save.manager} as head coach. A new era begins! #${u.short}", "NEWS", rnd, club = u.id)
        save.league.firstOrNull { it.source.isNotEmpty() }?.let { c -> post(save, "m:desk", "The ${c.name} fixture list is out. ${c.fixtures.count { !CompEngine.isKO(it) }} league matches. #${tag(c)}", "NEWS", rnd) }
        for (pid in u.squad.filter { Repo.byId[it] != null }.sortedByDescending { Stumps.account(save, it)?.followers ?: 0 }.take(2))
            post(save, keyOfPlayer(pid), listOf("Pre-season work done. Ready 💪", "Nets, gym, repeat. Season loading 🏏", "Can't wait to play in front of you all! 🙌").random(rnd) + " #${u.short}", "NEWS", rnd, pid = pid, club = u.id)
    }

    /* ---------------------------------------------------------------- user actions */

    fun userPost(save: CareerSave, text: String, photo: String = ""): YorkerPost? {
        val t = text.trim(); if (t.isEmpty()) return null
        val p = post(save, "me", t, "POST", Random.Default, photo = photo, club = save.userClubId) ?: return null
        save.yorker.myFollowers += 3
        return p
    }

    fun toggleLike(p: YorkerPost) { p.liked = !p.liked; p.likes += if (p.liked) 1 else -1 }
    fun toggleRepost(save: CareerSave, p: YorkerPost) { p.reposted = !p.reposted; p.reposts += if (p.reposted) 1 else -1; if (p.reposted) save.yorker.myFollowers += 1 }

    /** The coach replies; players and teams answer the coach (deterministic ~65%, always the first time on a post). */
    fun reply(save: CareerSave, p: YorkerPost, text: String, replyTo: Int = 0): SocialComment? {
        val t = text.trim(); if (t.isEmpty()) return null
        val me = account(save, "me")!!
        val c = SocialComment(me.name, me.handle, t.take(280), 0, id = save.nextId++, replyTo = replyTo, mine = true, day = save.day)
        p.thread.add(c); p.replies++
        val author = account(save, p.author)
        if (author != null && p.author != "me" && !p.author.startsWith("m:")) {
            val first = p.thread.count { it.mine } == 1
            if (first || abs(mix(c.id.toLong())) % 100 < 65) {
                p.thread.add(SocialComment(author.name, author.handle, CommentGen.playerReply(c.id.toLong() * 31 + p.id, save.manager), 2 + (abs(mix(p.id.toLong())) % 40).toInt(),
                    id = save.nextId++, replyTo = c.id, pid = author.pid, day = save.day))
                p.replies++
            }
        }
        return c
    }

    fun isFollowing(save: CareerSave, key: String) = key in save.yorker.following
    fun toggleFollow(save: CareerSave, key: String): Boolean {
        val l = save.yorker.following
        return if (key in l) { l.remove(key); false } else { l.add(key); true }
    }

    /* ---------------------------------------------------------------- feeds */

    /** Home ("For you"): newest first, boosted by engagement, follows, the user's team and photos. */
    fun home(save: CareerSave, limit: Int = 120): List<YorkerPost> {
        val st = save.yorker
        val newest = st.posts.maxOfOrNull { it.id } ?: 0
        val u = save.userClubId
        return st.posts.sortedByDescending { p ->
            ln(1.0 + p.likes) * 0.55 - (newest - p.id) * 0.22 + (if (p.author in st.following) 2.2 else 0.0) + (if (p.club == u) 1.4 else 0.0) +
                (if (p.photo.isNotEmpty()) 0.6 else 0.0) + (if (p.author == "me") 3.0 else 0.0)
        }.take(limit)
    }

    fun followingFeed(save: CareerSave): List<YorkerPost> = save.yorker.posts.filter { it.author in save.yorker.following || it.author == "me" }
    fun postsBy(save: CareerSave, key: String): List<YorkerPost> = save.yorker.posts.filter { it.author == key || (key == "me" && it.reposted) }

    /** Trending hashtags: recent posts weigh more, engagement adds a little. */
    fun trending(save: CareerSave, n: Int = 10): List<Pair<String, Int>> {
        val recent = save.yorker.posts.take(160)
        val score = HashMap<String, Double>(); val count = HashMap<String, Int>()
        recent.forEachIndexed { i, p -> for (t in p.tags.distinct()) { score[t] = (score[t] ?: 0.0) + (1.0 - i / 200.0) + ln(1.0 + p.likes) * 0.05; count[t] = (count[t] ?: 0) + 1 } }
        return score.entries.sortedByDescending { it.value }.take(n).map { it.key to (count[it.key] ?: 0) }
    }

    /** Search accounts (players / teams / media) and posts. */
    fun search(save: CareerSave, q: String): Pair<List<Acct>, List<YorkerPost>> {
        val s = q.trim().removePrefix("#").removePrefix("@")
        if (s.length < 2) return emptyList<Acct>() to emptyList()
        val accts = ArrayList<Acct>()
        for (c in save.clubs.values) if (c.name.contains(s, true) || c.short.equals(s, true)) account(save, keyOfTeam(c.id))?.let { accts.add(it) }
        for (k in MEDIA.keys) account(save, "m:$k")?.takeIf { it.name.contains(s, true) || it.handle.contains(s, true) }?.let { accts.add(it) }
        save.players.values.asSequence().filter { !it.retired && it.name.contains(s, true) }.take(25).forEach { p -> account(save, keyOfPlayer(p.id))?.let { accts.add(it) } }
        val posts = save.yorker.posts.filter { p -> p.text.contains(s, true) || p.tags.any { it.equals(s, true) } }.take(60)
        return accts.sortedByDescending { it.followers }.take(30) to posts
    }

    /** Accounts to follow: user's squad, rivals, popular players not yet followed. */
    fun suggestions(save: CareerSave, n: Int = 6): List<Acct> {
        val f = save.yorker.following.toSet()
        val u = save.clubs[save.userClubId]
        val keys = (u?.squad.orEmpty().map { keyOfPlayer(it) } + save.league.flatMap { it.teams }.distinct().map { keyOfTeam(it) } + MEDIA.keys.map { "m:$it" }).filter { it !in f && it != keyOfTeam(save.userClubId) }
        return keys.mapNotNull { account(save, it) }.sortedByDescending { it.followers }.take(n)
    }

    /** Stored + generated reply count and generated replies (lazy pages, same unique generator as Stumpstagram). */
    fun replyCtx(save: CareerSave, p: YorkerPost) = CommentGen.Ctx(when (p.kind) { "TITLE" -> "TROPHY"; "POST" -> "NEWS"; else -> p.kind },
        save.players[p.pid]?.name ?: "", save.clubs[p.club]?.short ?: "", save.clubs[p.opp]?.short ?: "", p.stat)
    fun generatedReplies(save: CareerSave, p: YorkerPost) = minOf(max(0, p.replies - p.thread.size), CommentGen.capacity(replyCtx(save, p)))
    fun replyPage(save: CareerSave, p: YorkerPost, from: Int, n: Int): List<SocialComment> {
        val total = generatedReplies(save, p); if (from >= total) return emptyList()
        return CommentGen.page(p.id.toLong() * 7_368_787L + p.author.hashCode(), replyCtx(save, p), from, minOf(n, total - from))
    }
}
