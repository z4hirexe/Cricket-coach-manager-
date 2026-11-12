package com.cricketcoach.manager.audio

import com.cricketcoach.manager.game.MatchSim
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How the crowd should feel right now (pure logic, unit-tested): [excite] drives the chant layer,
 * [tension] the hushed murmur / heartbeat layer. Both 0..1.
 */
data class Mood(val excite: Float, val tension: Float)

object CrowdMood {
    fun of(m: MatchSim): Mood {
        val inn = m.cur ?: return Mood(0.15f, 0f)
        if (m.finished) return Mood(0.6f, 0f)
        // recent scoring momentum: boundaries in the last few balls lift the crowd
        val recent = m.events.takeLast(12)
        val boundaries = recent.count { it.kind == "FOUR" || it.kind == "SIX" }
        val wickets = recent.count { it.kind == "WICKET" }
        var excite = 0.15f + 0.12f * boundaries + 0.1f * wickets
        var tension = 0f
        val target = inn.target
        if (target != null && !m.isTest && inn.maxBalls > 0) {
            val need = target - inn.runs
            val left = inn.maxBalls - inn.balls
            if (need > 0 && left > 0) {
                val rrr = need * 6f / left
                val wkLeft = inn.maxWk - inn.wk
                // the closer the equation is to "a run a ball-ish" with few balls left, the tighter it gets
                val closeness = 1f - min(1f, abs(rrr - 8f) / 8f)
                val late = 1f - min(1f, left / 36f)
                tension = closeness * (0.35f + 0.65f * late)
                if (wkLeft <= 2) tension = max(tension, 0.5f * late + 0.3f)
                if (left <= 6) tension = max(tension, 0.75f)
                if (need <= 6 && left <= 12) excite += 0.25f
            }
        } else if (!m.isTest && inn.maxBalls > 0 && inn.maxBalls - inn.balls <= 24) {
            excite += 0.15f  // death overs of the first innings
        }
        return Mood(excite.coerceIn(0f, 1f), tension.coerceIn(0f, 1f))
    }
}
