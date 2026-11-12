package com.cricketcoach.manager.game

/**
 * Save-format migrations (v2.6). Old careers are never rewritten destructively:
 *  - their own season year is kept (a 2027 career stays 2027; only NEW careers start in 2026),
 *  - generated fixture lists keep their game-day dates (no official dates are injected into old seasons),
 *  - fields added in v2.6 get safe defaults (Yorker state, comment ids for threaded replies, photo keys).
 */
object SaveMigration {
    const val CURRENT = 2

    /** Id for the i-th pre-generated fan comment stored on post [postId]; far above save.nextId (user comments) so threads never collide. */
    fun storedCommentId(postId: Int, i: Int): Int = 600_000_000 + (postId % 50_000_000) * 24 + i.coerceAtMost(23)

    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
    fun migrate(s: CareerSave): CareerSave {
        // Gson can leave Kotlin non-null fields null when a JSON key is explicitly null - repair those first
        if ((s.yorker as YorkerState?) == null) s.yorker = YorkerState()
        if ((s.stumps as StumpsState?) == null) s.stumps = StumpsState()
        if ((s.posts as MutableList<SocialPost>?) == null) s.posts = mutableListOf()
        for (c in s.league) { if ((c.source as String?) == null) c.source = ""; for (f in c.fixtures) { if ((f.date as String?) == null) f.date = ""; if ((f.venue as String?) == null) f.venue = ""; if ((f.time as String?) == null) f.time = "" } }
        if (s.dataVersion >= CURRENT) return s
        // v0/1 -> v2: give stored comments stable ids (threads / replies) and keep the total comment count consistent
        for (p in s.posts) {
            if ((p.photo as String?) == null) p.photo = ""
            p.comments.forEachIndexed { i, c -> if (c.id == 0) c.id = storedCommentId(p.id, i) }
            if (p.commentCount < p.comments.size) p.commentCount = p.comments.size
        }
        s.dataVersion = CURRENT
        return s
    }
}
