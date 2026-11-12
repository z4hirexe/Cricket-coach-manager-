package com.cricketcoach.manager

import com.cricketcoach.manager.game.BallEv
import com.cricketcoach.manager.render3d.ClipData
import com.cricketcoach.manager.render3d.DeliveryPlan
import com.cricketcoach.manager.render3d.Ground
import com.cricketcoach.manager.render3d.PlanCast
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Plays every kind of delivery through the 3D planner and checks the physical sanity of the whole timeline. */
class DeliveryPlanTest {
    private data class Case(val ev: BallEv, val pc: PlanCast)

    private fun cases(): List<Case> {
        val out = ArrayList<Case>(); var id = 1
        fun ev(kind: String, runs: Int, bat: Int, how: String, angle: Float, dist: Float) =
            BallEv(id++, 1, id / 6, id % 6, "IND", "Bat", "Bowl", runs, bat, kind, how, angle, dist, "", id % 17 == 0)
        val kinds = listOf(
            Triple("DOT", 0, ""), Triple("RUN", 1, ""), Triple("RUN", 2, ""), Triple("RUN", 3, ""), Triple("FOUR", 4, ""), Triple("SIX", 6, ""),
            Triple("WIDE", 1, ""), Triple("NB", 1, ""), Triple("NB", 5, ""), Triple("APPEAL", 0, ""),
            Triple("WICKET", 0, "b Bowl"), Triple("WICKET", 0, "lbw b Bowl"), Triple("WICKET", 0, "st †K b Bowl"),
            Triple("WICKET", 0, "run out (F)"), Triple("WICKET", 0, "c F b Bowl"), Triple("WICKET", 0, "c & b Bowl"), Triple("WICKET", 0, "c †K b Bowl"))
        for (left in listOf(false, true)) for (lArm in listOf(false, true)) for (spin in listOf(false, true)) for ((kind, runs, how) in kinds) for (k in 0 until 8) {
            val angle = (k * 47f + 11f) % 360f
            val dist = when (kind) { "SIX" -> 1.15f; "FOUR" -> 1f; "RUN" -> 0.3f + 0.2f * runs; "DOT", "WIDE", "APPEAL" -> 0.1f; else -> 0.6f }
            val bat = when (kind) { "NB" -> runs - 1; "WIDE" -> 0; else -> runs }
            val catcher = when { how.startsWith("c & b") -> PlanCast.BOWLER; how.startsWith("c †") -> PlanCast.KEEPER; how.startsWith("c ") -> if (k % 2 == 0) k else PlanCast.NONE; else -> PlanCast.NONE }
            out += Case(ev(kind, runs, bat, how, angle, dist), PlanCast(left, lArm, spin, catcher, outIsStriker = k % 2 == 0, runOutFielder = if (k % 3 == 0) k else PlanCast.NONE))
        }
        return out
    }

    private fun fin(v: Float) = !v.isNaN() && !v.isInfinite()

    @Test fun everyDeliveryIsPhysicallySane() {
        val all = cases(); val fails = ArrayList<String>()
        val clips = ClipData.DUR.keys + setOf("ump_idle", "ump_out", "ump_four", "ump_six", "ump_wide", "keep_crouch", "keep_collect", "keep_stump")
        var maxOverlap = 0
        for ((ev, pc) in all) {
            val tag = "${ev.kind} '${ev.how}' bat=${ev.bat} a=${ev.angle} L=${pc.strikerLeft} LA=${pc.bowlerLeftArm} spin=${pc.spin} c=${pc.catcher} ois=${pc.outIsStriker}"
            val p = try { DeliveryPlan(ev, pc) } catch (e: Throwable) { fails += "$tag: ${e.javaClass.simpleName} ${e.message} @ ${e.stackTrace.take(3).joinToString()}"; continue }
            if (!(p.tRel > 1f && p.tImp > p.tRel && p.tOut >= p.tImp - 1e-3f && p.tEnd > p.tOut && p.tEnd <= p.tImp + 26f + 1e-3f)) fails += "$tag: times rel=${p.tRel} imp=${p.tImp} out=${p.tOut} end=${p.tEnd}"
            val unknown = p.clipsUsed() - clips
            if (unknown.isNotEmpty()) fails += "$tag: unknown clips $unknown"
            // ball meets its target exactly at tImp
            p.eval(p.tImp)
            val err = hypot(hypot(p.ball[0] - p.contact[0], p.ball[1] - p.contact[1]), p.ball[2] - p.contact[2])
            if (err > 0.03f) fails += "$tag: ball ${err}m from contact at tImp"
            // contact is on the bat in the striker's actual pose (independent check of the frame transform)
            if (p.hitBall) {
                val s = p.pose[DeliveryPlan.STR]; val sw = FloatArray(3)
                DeliveryPlan.toWorld(s.ax, s.az, s.yaw, s.mirror, ClipData.SWEET[p.shot]!!, sw)
                val e2 = hypot(hypot(sw[0] - p.contact[0], sw[1] - p.contact[1]), sw[2] - p.contact[2])
                if (e2 > 0.03f) fails += "$tag: sweet spot off by $e2 (${p.shot}, clip ${s.clip})"
                if (s.clip != p.shot) fails += "$tag: striker playing ${s.clip} at impact, expected ${p.shot}"
            }
            // ball leaves the bowler's hand at release, not before
            p.eval(p.tRel - 0.3f); val bwl = p.pose[DeliveryPlan.BWL]
            if (hypot(p.ball[0] - bwl.x, p.ball[2] - bwl.z) > 1.3f) fails += "$tag: ball not with bowler before release"
            // run-out: victim short of the crease at the break
            if (p.wicket == "runout") {
                val tb = if (pc.outIsStriker) p.breakBowl else p.breakBat
                if (tb < 0f) fails += "$tag: run-out without broken stumps"
                else {
                    p.eval(tb)
                    val who = p.pose.take(2).maxBy { if (pc.outIsStriker) it.z else -it.z }   // the one heading for that end
                    val short = if (pc.outIsStriker) (Ground.FAR - Ground.POP) - who.z else who.z - Ground.POP
                    if (short !in 0.3f..2.0f) fails += "$tag: run-out victim short by $short"
                    p.eval(tb + 0.02f); if (abs(p.ball[2] - (if (pc.outIsStriker) Ground.FAR else 0f)) > 0.6f) fails += "$tag: ball ${p.ball[2]} not at the stumps on break"
                }
            }
            if (p.wicket == "bowled" && p.breakBat < 0f) fails += "$tag: bowled without broken stumps"
            if (p.wicket == "stumped") {
                p.eval(p.breakBat); val s = p.pose[DeliveryPlan.STR]
                if (s.z < Ground.POP + 0.2f) fails += "$tag: stumped batter inside crease (z=${s.z})"
            }
            // sweep the whole timeline
            var t = 0f; var overlapReported = false
            while (t <= p.tEnd) {
                p.eval(t)
                if (!(fin(p.ball[0]) && fin(p.ball[1]) && fin(p.ball[2]))) { fails += "$tag: non-finite ball at $t"; break }
                if (p.ball[1] < 0.03f || p.ball[1] > 60f || abs(p.ball[0]) > 110f || abs(p.ball[2] - Ground.CZ) > 115f) { fails += "$tag: ball out of world ${p.ball.toList()} at $t"; break }
                for (i in 0 until DeliveryPlan.N) {
                    val a = p.pose[i]
                    if (!(fin(a.x) && fin(a.z) && fin(a.yaw))) { fails += "$tag: actor $i non-finite at $t"; break }
                    if (!a.walkingOff && Ground.rope(a.x, a.z) > 1.02f) { fails += "$tag: actor $i outside the rope (${a.x},${a.z}) at $t"; break }
                }
                if (!overlapReported && t > 0.3f && t < p.tOut) {
                    loop@ for (i in 0 until DeliveryPlan.N) for (j in i + 1 until DeliveryPlan.N) {
                        val a = p.pose[i]; val b = p.pose[j]
                        if (a.walkingOff || b.walkingOff) continue
                        val d = hypot(a.x - b.x, a.z - b.z)
                        if (d < 0.5f) { fails += "$tag: actors $i,$j overlap ${"%.2f".format(d)}m at ${"%.2f".format(t)} (${a.clip}/${b.clip})"; overlapReported = true; maxOverlap++; break@loop }
                    }
                }
                t += 1f / 30f
            }
        }
        println("DeliveryPlanTest: ${all.size} deliveries, ${fails.size} problems")
        fails.groupBy { it.substringAfter(": ").replace(Regex("[-0-9.,]+"), "#").take(34) }.forEach { (k, v) -> println("  [${v.size}] $k ... e.g. ${v.first()}") }
        assertTrue("${fails.size} problems, first: ${fails.take(12).joinToString("\n")}", fails.isEmpty())
    }
}
