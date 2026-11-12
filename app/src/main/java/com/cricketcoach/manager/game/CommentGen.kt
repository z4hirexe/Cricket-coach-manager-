package com.cricketcoach.manager.game

import kotlin.math.abs

/**
 * Lazy, deterministic fan comments for Stumpstagram / Yorker posts (v2.6).
 *
 * A post can show a realistic total (e.g. "20.4K comments") without storing thousands of strings: comment #i of a post
 * is generated on demand from (post seed, i). Each comment is a unique (opener, body, tail) combination picked through a
 * full-period permutation, so no comment text ever repeats inside one post (up to [capacity] comments; the UI stops
 * paging there). Bodies are written per context (century, five-for, hat-trick, win, loss, trophy, signing...) and use
 * the player / team / opponent / stat of the post.
 */
object CommentGen {
    class Ctx(val kind: String, val name: String = "", val team: String = "", val opp: String = "", val stat: String = "")

    private val FIRST = listOf("aarav", "riya", "kabir", "meera", "rohan", "sana", "dev", "ishita", "arjun", "zoya", "vikram", "anaya", "karan", "tara",
        "nikhil", "pooja", "farhan", "neha", "yash", "diya", "imran", "sneha", "rahul", "aisha", "manav", "kavya", "omar", "priya", "sid", "laila",
        "jack", "emma", "liam", "chloe", "ali", "fatima", "ravi", "lucy", "sam", "noah", "harsh", "tanvi", "aditya", "naina", "jay", "ruhi")
    private val LAST = listOf("cricket", "sixes", "fan", "army", "coverdrive", "yorkers", "stands", "speaks", "daily", "zone", "ki_awaaz", "nation",
        "hq", "11", "07", "99", "18", "45", "talks", "updates", "gully", "pitchside", "straightdrive", "dhoom", "online", "views")

    private fun openers(kind: String) = when (kind) {
        "LOSS" -> listOf("", "Honestly,", "Yaar,", "Not gonna lie,", "Sigh.", "Painful.", "Hmm.", "Real talk:", "Look,", "Again?!", "Bhai,", "Ouch.")
        else -> listOf("", "Honestly,", "Bro,", "Yaar,", "No words.", "Calling it now:", "Unreal.", "Not gonna lie,", "Big moment.", "Arre wah!",
            "Say what you want,", "Goosebumps.", "Okay but", "Stop it.", "Real talk:")
    }

    private fun bodies(c: Ctx): List<String> {
        val n = c.name.ifBlank { "this guy" }; val f = c.name.substringBefore(' ').ifBlank { n }
        val t = c.team.ifBlank { "the team" }; val o = c.opp.ifBlank { "them" }; val st = c.stat
        return when (c.kind) {
            "CENTURY" -> listOf("$f makes batting look easy", "${st.ifBlank { "a hundred" }} against $o is ridiculous", "pure class from $n", "that cover drive deserves its own post",
                "$f should open in every format", "frame this innings", "the patience before the big shots was the real story", "hundred and still hungry",
                "the way $f paced it was a masterclass", "$t's best batter right now, no debate", "every ball out of the middle", "this is why we watch cricket",
                "$f bhai ne aaj dhuaan nikaal diya", "that celebration had me tearing up", "selectors, are you watching $f?", "$o had no plan for $f")
            "FIFTY" -> listOf("solid knock from $f", "${st.ifBlank { "a fifty" }} at the right time", "$f is getting into rhythm", "wish $f had converted that",
                "good to see $f back among the runs", "anchor role done perfectly", "the strike rotation was top class", "$f's footwork looked sharp today",
                "next one is a hundred, mark my words", "quality fifty under pressure", "$t needed exactly that from $f", "that pull shot though")
            "FIVEFOR" -> listOf("$f was unplayable", "${st.ifBlank { "five wickets" }} - give $f the new ball always", "$o batters had no answer", "that spell changed the game",
                "seam position was perfect all day", "$f hit the deck like a machine", "best spell I've seen this season", "the setup for the third wicket was genius",
                "$f deserves the player of the match", "fast, accurate, relentless", "$t's bowling attack is scary with $f", "the slower ball was filthy")
            "HATTRICK" -> listOf("three in three from $f", "a HAT-TRICK, I'm screaming", "$f just wrote his name in history", "rewatched it ten times already",
                "that third ball was the best of the lot", "$o will have nightmares about $f", "we witnessed something special", "$f is on another planet",
                "the stadium went silent then exploded", "save this post forever")
            "WIN" -> listOf("what a win for $t", "team effort, everyone contributed", "$t look like champions", "the coach got the tactics spot on",
                "bowling at the death won it", "$f was the difference today", "two points and a statement", "this squad has belief now",
                "$o were outplayed in every department", "fielding was top notch", "keep this XI, don't change a thing", "kya jeet thi yaar")
            "LOSS" -> listOf("need changes in the XI", "death bowling cost us again", "credit to $o, they were better", "middle order has to step up",
                "we bounce back next game", "too many dot balls in the powerplay", "$t can't keep losing tight games", "still backing this squad",
                "the toss decision was wrong", "fielding let us down today", "drop the out-of-form guys please", "we lost it in the last five overs")
            "TROPHY", "TITLE" -> listOf("CHAMPIONS! $t deserved it", "silverware for $t at last", "this squad will be remembered", "what a journey this season",
                "the final was a classic", "$f lifting that trophy is the image of the year", "dynasty loading", "every fan deserved this night",
                "the coach built something special", "tears of joy over here", "$o fought hard but $t were better", "parade when?")
            "AUCTION", "TRANSFER", "SIGNING" -> listOf("welcome $f to $t", "smart signing by $t", "${st.ifBlank { "that price" }} for $f is a steal", "paisa vasool if $f stays fit",
                "$f fills exactly the gap we had", "big statement from the management", "not convinced yet, prove me wrong $f", "$f in these colours looks right",
                "this changes our whole batting order", "the auction table won this one", "can't wait to see $f debut", "other teams will regret letting $f go")
            "MILESTONE" -> listOf("legend status for $f", "longevity like this is rare", "$f has given everything to the game", "numbers that tell a story",
                "respect, $f", "a milestone earned the hard way", "remember when $f was a youngster?", "many more to come for $f")
            "RESULT" -> listOf("what a game between $t and $o", "the scorecard doesn't show how tense that was", "points table just got interesting",
                "$t needed that", "$o will be disappointed", "net run rate matters now", "the chase was paced well", "huge result for the standings",
                "anyone else nervous for the next fixture?", "that last over though")
            else -> listOf("let's go $t", "can't wait for the next match", "full support from the stands", "this season feels different", "$f looking sharp in training",
                "interesting move", "the vibes are good", "who else is ready?", "hope the pitch helps the spinners", "need more of this content", "big season ahead for $t")
        }
    }

    private fun tails(kind: String, team: String) = when (kind) {
        "LOSS" -> listOf("", "😤", "🙏", "💔", "...", "😔", "smh")
        else -> listOf("", "🔥", "👏👏", "🙌", "💯", "🏏", "❤️", "!!", "😍", if (team.isNotBlank()) "#${team.filter { it.isLetterOrDigit() }}" else "✨")
    }

    /** Number of distinct comments this context can produce for one post. */
    fun capacity(c: Ctx): Int = openers(c.kind).size * bodies(c).size * tails(c.kind, c.team).size

    private fun mix(z0: Long): Long { var z = z0 + -0x61c8864680b583ebL; z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L; z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L; return z xor (z ushr 31) }
    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    /** Comment #[i] (0-based) of the post with [seed]. Distinct texts for every i < [capacity]. */
    fun at(seed: Long, c: Ctx, i: Int): SocialComment {
        val ops = openers(c.kind); val bs = bodies(c); val ts = tails(c.kind, c.team)
        val n = ops.size.toLong() * bs.size * ts.size
        var a = (abs(mix(seed)) % n).coerceAtLeast(1)
        while (gcd(a, n) != 1L) a = a % n + 1
        val b = abs(mix(seed xor 0x5bd1e995L)) % n
        val idx = ((a * (i.toLong() % n)) + b) % n
        val o = ops[(idx % ops.size).toInt()]
        val body = bs[((idx / ops.size) % bs.size).toInt()]
        val tl = ts[(idx / (ops.size.toLong() * bs.size)).toInt()]
        val bodyCased = if (o.isEmpty() || o.endsWith(".") || o.endsWith("!") || o.endsWith(":") || o.endsWith("?")) body.replaceFirstChar { it.uppercase() } else body
        val text = listOf(o, bodyCased, tl).filter { it.isNotEmpty() }.joinToString(" ")
        val h = mix(seed * 31 + i)
        val first = FIRST[(abs(h) % FIRST.size).toInt()]; val last = LAST[(abs(h shr 17) % LAST.size).toInt()]
        val handle = "${first}_$last"
        // top comments carry most of the likes (power-law by position)
        val likes = ((abs(h shr 7) % 900 + 40) / (1.0 + i * 0.35)).toInt()
        return SocialComment(first.replaceFirstChar { it.uppercase() } + " " + last.replace('_', ' ').replaceFirstChar { it.uppercase() }, "@$handle", text, likes, id = -(i + 1))
    }

    /** A page of generated comments [from, from+n) (clipped to capacity). */
    fun page(seed: Long, c: Ctx, from: Int, n: Int): List<SocialComment> {
        val cap = capacity(c)
        return (from until minOf(from + n, cap)).map { at(seed, c, it) }
    }

    private val REPLIES = listOf("Thank you so much! 🙏", "Means a lot, %s ❤️", "Appreciate the support 🙌", "More to come 💪", "Haha, working on it! 😄",
        "Thanks for always backing us 🏏", "Couldn't do it without you all 🙏", "Noted, coach! 😉", "Big thanks %s!", "Love this, thank you 💙",
        "Back to work tomorrow, but this felt good 😄", "The support keeps us going 🙌")

    /** A player's reply to the user's comment (deterministic for the comment). */
    fun playerReply(seed: Long, userName: String): String {
        val r = REPLIES[(abs(mix(seed)) % REPLIES.size).toInt()]
        return if (r.contains("%s")) r.format(userName.substringBefore(' ').ifBlank { "coach" }) else r
    }
}
