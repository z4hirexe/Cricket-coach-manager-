package com.cricketcoach.manager

import com.cricketcoach.manager.game.BallEv
import com.cricketcoach.manager.render3d.ClipData
import com.cricketcoach.manager.render3d.DeliveryPlan
import com.cricketcoach.manager.render3d.PlanCast
import org.junit.Test
import java.io.File

/**
 * Dumps real DeliveryPlan frames (every actor's position/yaw/mirror/clip/clip-time and the ball) for a set of key gameplay
 * moments to tools/cache/plan_frames.json, so tools/assets/gameplay_previews.py can render previews of the actual game logic.
 */
class DeliveryPlanDump {
    private fun ev(id: Int, kind: String, runs: Int, bat: Int, how: String, angle: Float, dist: Float) =
        BallEv(id, 0, id / 6, id % 6 + 1, "IND", "Bat", "Bowl", runs, bat, kind, how, angle, dist, "")

    private fun f(v: Float) = "%.4f".format(v)

    private fun frame(p: DeliveryPlan, t: Float): String {
        p.eval(t)
        val actors = p.pose.joinToString(",") { a ->
            val dur = ClipData.DUR[a.clip] ?: 1f
            val ct = a.clipTime(t).let { if (a.loop) it % dur else it.coerceAtMost(dur) }
            """{"x":${f(a.x)},"z":${f(a.z)},"yaw":${f(a.yaw)},"mirror":${a.mirror},"clip":"${a.clip}","ct":${f(ct)}}"""
        }
        val bb = if (p.breakBat >= 0 && t >= p.breakBat) t - p.breakBat else -1f
        val bw = if (p.breakBowl >= 0 && t >= p.breakBowl) t - p.breakBowl else -1f
        return """{"t":${f(t)},"ball":[${f(p.ball[0])},${f(p.ball[1])},${f(p.ball[2])}],"breakBat":${f(bb)},"breakBowl":${f(bw)},"actors":[$actors]}"""
    }

    @Test fun dumpKeyMoments() {
        val out = StringBuilder("{\n")
        fun scene(name: String, e: BallEv, pc: PlanCast, times: (DeliveryPlan) -> List<Pair<String, Float>>) {
            val p = DeliveryPlan(e, pc)
            val fr = times(p).joinToString(",\n") { (lab, t) -> "\"$lab\":${frame(p, t)}" }
            if (out.length > 2) out.append(",\n")
            out.append("\"$name\":{\"shot\":\"${p.shot}\",\"wicket\":\"${p.wicket}\",\"tRel\":${f(p.tRel)},\"tImp\":${f(p.tImp)},\"tOut\":${f(p.tOut)},\"tEnd\":${f(p.tEnd)},\"strikerLeft\":${pc.strikerLeft},\"frames\":{\n$fr}}")
            println("$name: shot=${p.shot} wicket=${p.wicket} rel=${p.tRel} imp=${p.tImp} out=${p.tOut} end=${p.tEnd}")
        }
        val rhbPace = PlanCast(false, false, false)
        scene("delivery", ev(7, "RUN", 1, 1, "", 60f, 0.4f), rhbPace) { p -> listOf("runup" to p.tRel - 0.9f, "release" to p.tRel + 0.02f, "bounce" to p.tRel + 0.33f) }
        scene("contact", ev(11, "FOUR", 4, 4, "", 62f, 1f), rhbPace) { p -> listOf("impact" to p.tImp, "after" to p.tImp + 0.12f) }
        scene("catch", ev(23, "WICKET", 0, 0, "c F b Bowl", 300f, 0.5f), PlanCast(false, false, false, catcher = 6)) { p -> listOf("skied" to p.tImp + (p.tOut - p.tImp) * 0.5f, "taken" to p.tOut + 0.03f, "celebrate" to p.tOut + 1.0f) }
        scene("running", ev(31, "RUN", 2, 2, "", 115f, 0.7f), PlanCast(true, false, false)) { p -> listOf("first" to p.tImp + 1.6f, "turn" to p.tImp + 0.5f + 3.2f + 0.2f, "throw" to p.tImp + 4.5f) }
        scene("runout", ev(43, "WICKET", 0, 0, "run out (F)", 40f, 0.05f), PlanCast(false, false, false, outIsStriker = true, runOutFielder = 4)) { p -> listOf("throw" to p.tImp + 2.2f, "break" to p.tOut + 0.08f, "after" to p.tOut + 0.9f) }
        scene("stumping", ev(53, "WICKET", 0, 0, "st †K b Bowl", 0f, 0.05f), PlanCast(false, false, true)) { p -> listOf("beaten" to p.tImp + 0.05f, "stumped" to p.tOut + 0.08f) }
        scene("four_dive", ev(61, "FOUR", 4, 4, "", 75f, 1f), rhbPace) { p -> listOf("chase" to p.tOut - 1.2f, "dive" to p.tOut - 0.12f, "over" to p.tOut + 0.3f) }
        scene("bowled", ev(71, "WICKET", 0, 0, "b Bowl", 0f, 0.05f), PlanCast(false, true, false)) { p -> listOf("hit" to p.tImp + 0.1f) }
        scene("lbw", ev(83, "WICKET", 0, 0, "lbw b Bowl", 0f, 0.05f), PlanCast(true, false, true)) { p -> listOf("pad" to p.tImp + 0.02f, "finger" to p.tOut - 0.2f) }
        out.append("\n}\n")
        val f = File("../tools/cache/plan_frames.json"); f.parentFile.mkdirs(); f.writeText(out.toString())
        println("wrote ${f.canonicalPath} (${f.length()} B)")
    }
}
