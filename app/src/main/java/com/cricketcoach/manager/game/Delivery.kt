package com.cricketcoach.manager.game

import kotlin.random.Random

/**
 * v2.7 broadcast UI: the physical description of one delivery (line, length, pace, shot, footwork).
 *
 * The ball model in [MatchSim] decides the OUTCOME (dot / runs / boundary / wicket) from the player ratings.  This object
 * is called once, inside the same `nextBall()` step, to resolve HOW that outcome happened - it is written into the
 * [BallEv] and never re-rolled, so the ball tracker, ball history, pitch map and over summary all show the same event.
 * Everything is constrained by the outcome, the bowler type, the dismissal and the batting approach: a bowled wicket is
 * always a ball that hit the stumps, a caught-behind is an edge, a six is never a forward defensive, a left-alone ball is
 * always outside off, and so on.  Nothing here changes runs, wickets or the scorecard.
 */
object Delivery {
    const val LINES = "OUTSIDE OFF|OFF STUMP|MIDDLE|LEG STUMP|DOWN LEG|WIDE OUTSIDE OFF"
    val LENGTHS = listOf("Yorker", "Full", "Good length", "Back of a length", "Short", "Bouncer", "Full toss")
    val DEF_SHOTS = listOf("Forward defensive", "Back-foot defensive", "Straight-bat block", "Left alone", "Beaten outside off", "Defended off the back foot", "Padded away")

    class Detail(
        val line: String, val length: String, val speed: Int, val shot: String, val foot: String, val approach: String,
        /** where the ball pitched: x across the pitch (-1 leg side ... +1 off side, from the striker's view), y along it (0 = batter's stumps, 1 = bowler's crease) */
        val pitchX: Float, val pitchY: Float,
        /** short "why" line used by the ball-history card */
        val note: String
    )

    private fun pick(r: Random, vararg w: Pair<String, Double>): String {
        val tot = w.sumOf { it.second }; var x = r.nextDouble() * tot
        for ((k, v) in w) { x -= v; if (x <= 0) return k }
        return w.last().first
    }

    fun describe(
        r: Random, kind: String, bat: Int, how: String, angle: Float, spin: Boolean, bowlSkill: Int, aggression: Int,
        freeHit: Boolean, leftHand: Boolean
    ): Detail {
        val approach = when (aggression) { 0 -> "Defensive"; 2 -> "Attacking"; else -> "Balanced" }
        // pace
        val base = if (spin) 82 + bowlSkill / 8 else 124 + bowlSkill / 4
        val speed = base + (r.nextDouble() * 10 - 5).toInt()
        // length / line distributions depend on what happened
        var length: String; var line: String; var shot: String; var foot: String; var note: String
        when (kind) {
            "WIDE" -> {
                line = if (r.nextDouble() < 0.78) "WIDE OUTSIDE OFF" else "DOWN LEG"
                length = pick(r, "Full" to 1.0, "Good length" to 1.2, "Short" to 0.6)
                shot = "No shot offered"; foot = "Stood tall"; note = "Called wide by the umpire - out of the batter's reach."
            }
            "NB" -> {
                line = pick(r, "OFF STUMP" to 1.0, "MIDDLE" to 1.0, "OUTSIDE OFF" to 0.8)
                length = pick(r, "Full toss" to 1.4, "Full" to 1.0, "Good length" to 0.8)
                shot = if (bat >= 4) "Lofted drive" else if (bat > 0) "Pushed into the gap" else "Defended"
                foot = if (bat >= 4) "Front foot" else "Front foot"
                note = "Front-foot no ball - the bowler overstepped." + if (bat > 0) " $bat run${if (bat > 1) "s" else ""} off the bat as well." else ""
            }
            "WICKET" -> {
                val h = how.lowercase()
                when {
                    h.startsWith("b ") || h.startsWith("bowled") -> {
                        line = pick(r, "OFF STUMP" to 1.2, "MIDDLE" to 1.0, "LEG STUMP" to 0.5)
                        length = pick(r, "Yorker" to 1.2, "Full" to 1.2, "Good length" to 1.0)
                        shot = pick(r, "Played across the line" to 1.0, "Inside edge onto the stumps" to 0.8, "Beaten through the gate" to 1.0, "Missed a straight one" to 0.8)
                        foot = if (length == "Yorker" || length == "Full") "Front foot" else "Back foot"
                        note = "Through the defence - the stumps are rattled."
                    }
                    h.startsWith("lbw") -> {
                        line = pick(r, "MIDDLE" to 1.2, "LEG STUMP" to 0.9, "OFF STUMP" to 0.7)
                        length = pick(r, "Full" to 1.3, "Good length" to 1.0, "Yorker" to 0.5)
                        shot = pick(r, "Missed a flick" to 1.0, "Played around the front pad" to 1.0, "Trapped on the crease" to 0.8)
                        foot = if (r.nextDouble() < 0.7) "Front foot" else "Stuck on the crease"
                        note = "Struck on the pad in line - given out LBW."
                    }
                    h.startsWith("c & b") || h.startsWith("c and b") -> {
                        line = "OFF STUMP"; length = pick(r, "Good length" to 1.0, "Full" to 1.0)
                        shot = "Checked drive back at the bowler"; foot = "Front foot"; note = "Leading edge - a return catch for the bowler."
                    }
                    h.startsWith("st ") -> {
                        line = pick(r, "OUTSIDE OFF" to 1.0, "WIDE OUTSIDE OFF" to 0.7); length = pick(r, "Full" to 1.0, "Good length" to 0.6)
                        shot = "Charged down the pitch and missed"; foot = "Out of the crease"; note = "Beaten in the flight and stumped by the keeper."
                    }
                    h.startsWith("run out") -> {
                        line = pick(r, "OFF STUMP" to 1.0, "MIDDLE" to 1.0); length = pick(r, "Good length" to 1.0, "Full" to 0.8)
                        shot = pick(r, "Pushed to cover and set off" to 1.0, "Tapped to mid-wicket for a quick single" to 1.0)
                        foot = "Front foot"; note = "Direct hit - short of the crease going for the run."
                    }
                    h.startsWith("c ") && (h.contains("†") || h.contains("wk") || r.nextDouble() < 0.3) -> {
                        line = pick(r, "OUTSIDE OFF" to 1.5, "OFF STUMP" to 0.8); length = pick(r, "Good length" to 1.2, "Back of a length" to 0.8, "Full" to 0.6)
                        shot = pick(r, "Poked at it outside off" to 1.0, "Edged a drive" to 1.0, "Feathered a cut" to 0.5)
                        foot = if (length == "Full") "Front foot" else "Back foot"; note = "Thin edge through to the keeper."
                    }
                    else -> { // caught in the field
                        val sq = angle in 45f..135f || angle in 225f..315f
                        length = pick(r, "Short" to (if (sq) 1.5 else 0.6), "Full" to 1.0, "Good length" to 1.0, "Full toss" to 0.3)
                        line = pick(r, "OFF STUMP" to 1.0, "MIDDLE" to 0.8, "OUTSIDE OFF" to 0.8, "LEG STUMP" to 0.5)
                        shot = when {
                            length == "Short" || length == "Bouncer" -> pick(r, "Top-edged pull" to 1.0, "Mistimed hook" to 0.8, "Uppercut" to 0.4)
                            angle < 40f || angle > 320f -> pick(r, "Lofted straight drive" to 1.0, "Holed out going big" to 1.0)
                            sq && angle < 180f -> pick(r, "Slashed cut" to 1.0, "Lofted cover drive" to 1.0, "Chipped to point" to 0.6)
                            sq -> pick(r, "Slog across the line" to 1.0, "Mistimed sweep" to 0.8, "Flick in the air" to 0.8)
                            else -> pick(r, "Lofted drive" to 1.0, "Leading edge" to 0.6, "Mistimed loft" to 1.0)
                        }
                        foot = if (length == "Short" || length == "Bouncer" || length == "Back of a length") "Back foot" else "Front foot"
                        note = "Didn't get hold of it - caught in the field."
                    }
                }
            }
            "SIX", "FOUR" -> {
                val six = kind == "SIX"
                val sq = angle in 45f..135f || angle in 225f..315f
                val behind = angle in 120f..240f
                val straight = angle < 40f || angle > 320f
                length = when {
                    behind -> pick(r, "Short" to 1.2, "Back of a length" to 1.0, "Bouncer" to 0.6, "Good length" to 0.4)
                    sq && !straight -> pick(r, "Short" to 1.0, "Full" to 1.0, "Good length" to 0.8, "Full toss" to 0.5)
                    else -> pick(r, "Full" to 1.5, "Full toss" to 0.6, "Good length" to 0.8, "Yorker" to 0.2)
                }
                val off = angle in 20f..180f
                line = if (off) pick(r, "OUTSIDE OFF" to 1.3, "OFF STUMP" to 1.0, "MIDDLE" to 0.4) else pick(r, "LEG STUMP" to 1.0, "MIDDLE" to 1.0, "DOWN LEG" to 0.5, "OFF STUMP" to 0.5)
                val short = length == "Short" || length == "Bouncer" || length == "Back of a length"
                shot = when {
                    behind && off -> if (short) "Upper cut over third man" else "Late cut past slip"
                    behind -> if (short) "Hooked fine" else "Glanced fine"
                    straight -> if (six) "Lofted straight down the ground" else "Straight drive"
                    off && sq -> if (short) "Cut shot" else if (six) "Inside-out loft over cover" else "Cover drive"
                    off -> if (six) "Lofted over mid-off" else "Off drive"
                    sq -> if (short) "Pull shot" else if (six) "Slog sweep" else "Flick off the pads"
                    else -> if (six) "Lofted over mid-on" else "On drive"
                }
                if (freeHit && six) shot = "Free-hit slog - $shot"
                foot = if (short) "Back foot" else "Front foot"
                note = if (six) "Cleared the rope - maximum." else "Timed and placed - raced to the boundary."
            }
            "RUN" -> {
                line = pick(r, "OFF STUMP" to 1.0, "MIDDLE" to 1.0, "OUTSIDE OFF" to 0.8, "LEG STUMP" to 0.6, "DOWN LEG" to 0.3)
                length = pick(r, "Good length" to 1.4, "Full" to 1.0, "Back of a length" to 0.8, "Short" to 0.5, "Yorker" to 0.3)
                val off = angle in 20f..180f
                val short = length == "Short" || length == "Back of a length"
                shot = when (bat) {
                    1 -> pick(r, if (off) "Pushed to cover" else "Worked to mid-wicket" to 1.5, "Dabbed to third man" to (if (off) 0.6 else 0.1), "Nudged to square leg" to (if (off) 0.1 else 0.8), if (short) "Dropped and ran" else "Tapped and ran" to 0.8)
                    2 -> pick(r, if (off) "Driven into the covers" else "Clipped through mid-wicket" to 1.5, "Steered behind square" to 0.6)
                    else -> pick(r, if (off) "Driven wide of long-off" else "Whipped wide of deep mid-wicket" to 1.5, "Cut to the sweeper" to 0.5)
                }
                foot = if (short) "Back foot" else "Front foot"
                note = when (bat) { 1 -> "Rotated the strike with a single."; 2 -> "Good running - they come back for two."; else -> "Three run - hard running between the wickets." }
            }
            else -> { // DOT / APPEAL
                val appeal = kind == "APPEAL"
                line = pick(r, "OFF STUMP" to 1.3, "OUTSIDE OFF" to 1.3, "MIDDLE" to 0.9, "LEG STUMP" to 0.4, "WIDE OUTSIDE OFF" to 0.3)
                length = pick(r, "Good length" to 1.8, "Back of a length" to 0.9, "Full" to 0.7, "Short" to 0.4, "Yorker" to 0.35, "Bouncer" to 0.25)
                shot = when {
                    appeal -> pick(r, "Struck on the pad - not out" to 1.0, "Missed the flick - huge LBW shout" to 1.0)
                    line == "WIDE OUTSIDE OFF" || (line == "OUTSIDE OFF" && r.nextDouble() < 0.45) -> if (r.nextDouble() < 0.6) "Left alone" else "Beaten outside off"
                    length == "Bouncer" -> if (r.nextDouble() < 0.6) "Ducked under it" else "Swayed out of the way"
                    length == "Short" || length == "Back of a length" -> if (r.nextDouble() < 0.65) "Back-foot defensive" else "Defended off the back foot"
                    length == "Yorker" -> "Dug out the yorker"
                    length == "Full" -> if (r.nextDouble() < 0.5) "Forward defensive" else "Checked drive straight to the fielder"
                    else -> when {
                        aggression >= 2 && r.nextDouble() < 0.4 -> "Drove straight to the fielder"
                        r.nextDouble() < 0.6 -> "Forward defensive"
                        else -> "Straight-bat block"
                    }
                }
                foot = when {
                    shot.contains("Back-foot") || shot.contains("back foot") || shot.contains("Ducked") || shot.contains("Swayed") -> "Back foot"
                    shot == "Left alone" -> "Shouldered arms"
                    shot.contains("Beaten") -> "Front foot - no contact"
                    else -> "Front foot"
                }
                note = when {
                    appeal -> "Reviewed or turned down - the batter survives."
                    shot == "Left alone" -> "No run: the ball was left outside off stump."
                    shot.contains("Beaten") -> "No run: played and missed outside off."
                    shot.contains("straight to") -> "No run: well struck but straight to the fielder."
                    shot.contains("Ducked") || shot.contains("Swayed") -> "No run: evasive action against the short ball."
                    else -> "No run: solid defence, the ball dies in front of the bat."
                }
            }
        }
        // pitch map coordinates from line / length (striker's view, off side is +x; mirrored for left-handers)
        val lx = when (line) { "WIDE OUTSIDE OFF" -> 0.85f; "OUTSIDE OFF" -> 0.5f; "OFF STUMP" -> 0.18f; "MIDDLE" -> 0f; "LEG STUMP" -> -0.18f; else -> -0.6f }
        val ly = when (length) { "Yorker" -> 0.05f; "Full" -> 0.18f; "Good length" -> 0.34f; "Back of a length" -> 0.46f; "Short" -> 0.58f; "Bouncer" -> 0.7f; else -> -0.05f }
        val px = ((lx + (r.nextFloat() - 0.5f) * 0.14f) * (if (leftHand) -1f else 1f)).coerceIn(-1f, 1f)
        val py = (ly + (r.nextFloat() - 0.5f) * 0.06f).coerceIn(-0.1f, 0.95f)
        return Detail(line, length, speed, shot, foot, approach, px, py, note)
    }

    /** Short chip label for the ball tracker. */
    fun chip(ev: BallEv): String = when (ev.kind) {
        "WICKET" -> "W"; "WIDE" -> if (ev.runs > 1) "${ev.runs}wd" else "wd"; "NB" -> if (ev.bat > 0) "${ev.bat}nb" else "nb"
        "SIX" -> "6"; "FOUR" -> "4"; "RUN" -> "${ev.bat}"; else -> "•"
    }

    /** Human label for the legend / history header. */
    fun label(ev: BallEv): String = when (ev.kind) {
        "WICKET" -> "WICKET"; "WIDE" -> "WIDE"; "NB" -> "NO BALL"; "SIX" -> "SIX"; "FOUR" -> "FOUR"
        "RUN" -> when (ev.bat) { 1 -> "1 RUN"; 2 -> "2 RUNS"; 3 -> "3 RUNS"; else -> "${ev.bat} RUNS" }
        "APPEAL" -> "APPEAL · NOT OUT"; else -> "DOT BALL"
    }
}
