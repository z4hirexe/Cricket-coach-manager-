package com.cricketcoach.manager.render3d

import com.cricketcoach.manager.game.BallEv
import kotlin.math.*

/** Who is on the field for the current delivery (resolved from the real squad data). */
class Cast(
    val strikerId: String, val strikerLeft: Boolean, val nonStrikerId: String,
    val bowlerId: String, val bowlerLeftArm: Boolean, val bowlerSpin: Boolean,
    val keeperId: String, val fielderIds: List<String>, val countryOf: (String) -> String,
    val batKit: Kit, val bowlKit: Kit
)

/**
 * Turns one simulated delivery (BallEv) into a timed 3D sequence: run-up, delivery stride and release, ball flight with
 * bounce, a shot chosen from the real direction/outcome, fielding, wicket mechanics, running between the wickets,
 * umpire signals and broadcast cameras.  World: metres, striker stumps z=0, bowler stumps z=20.12, y up.
 * For a right-hander the off side is -x (screen-left from the bowler's-end camera, as on TV).
 */
class MatchDirector(private val a: Arena3D) {
    private val striker get() = a.actors[0]
    private val nonStriker get() = a.actors[1]
    private val bowler get() = a.actors[2]
    private val keeper get() = a.actors[3]
    private val umpBowl get() = a.actors[4]
    private val umpLeg get() = a.actors[5]
    private val fielders get() = a.actors.subList(6, 15)

    var cam = 0
    var replay = false
    var title = false
    private var ev: BallEv? = null
    private var cast: Cast? = null
    private var t = 0f
    var done = true; private set
    var onImpact: ((BallEv) -> Unit)? = null
    var onOutcome: ((BallEv) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    private var firedImpact = false; private var firedOutcome = false

    // plan
    private var offSign = -1f
    private var spin = false
    private var armSign = 1f
    private var bowlX = -0.45f
    private var runStartZ = 34f; private var creaseZ = 21.4f; private var runSpeed = 7f
    private var tRunEnd = 0f; private var tRel = 0f; private var tBounce = 0f; private var tImp = 0f; private var tEnd = 6f
    private val rel = FloatArray(3); private val bounce = FloatArray(3); private val contact = FloatArray(3)
    private val land = FloatArray(3); private var tLand = 0f; private var aerial = false; private var hitBall = true
    private var shot = "bat_defend"
    private var chaser = -1; private var chaseFrom = FloatArray(2); private var throwT = 0f
    private var runs = 0; private var wicket = ""; private var umpSignal = ""
    private val fpos = Array(9) { FloatArray(2) }
    private var timeScale = 1f
    private var keeperZ = -8f

    private val FIELD_PACE = arrayOf(160f to 17f, 150f to 18f, 95f to 26f, 55f to 28f, 18f to 30f, 342f to 30f, 300f to 27f, 255f to 24f, 205f to 58f)
    private val FIELD_SPIN = arrayOf(150f to 7f, 120f to 50f, 95f to 24f, 55f to 26f, 15f to 28f, 345f to 28f, 300f to 25f, 250f to 22f, 210f to 52f)

    private fun polar(angle: Float, r: Float, out: FloatArray, oz: Float = 0f) {
        val ar = Math.toRadians(angle.toDouble())
        out[0] = (offSign * sin(ar) * r).toFloat(); out[1] = (cos(ar) * r).toFloat() + oz
    }

    fun setup(c: Cast) {
        cast = c
        offSign = if (c.strikerLeft) 1f else -1f
        spin = c.bowlerSpin; armSign = if (c.bowlerLeftArm) -1f else 1f
        val field = if (spin) FIELD_SPIN else FIELD_PACE
        for (i in 0 until 9) polar(field[i].first, field[i].second, fpos[i], 0.6f)
        val lod = { d: Float -> if (a.quality >= Gfx.HIGH || d < 30f) 0 else 1 }
        val all = a.actors
        for (x in all) x.active = false
        fun use(act: Actor, id: String, kit: Kit?, role: Role, d: Float) { act.active = true; act.dress(Look.of(id, c.countryOf(id)), kit, role, lod(d)) }
        use(striker, c.strikerId, c.batKit, Role.STRIKER, 0f)
        use(nonStriker, c.nonStrikerId, c.batKit, Role.NONSTRIKER, 20f)
        use(bowler, c.bowlerId, c.bowlKit, Role.BOWLER, 20f)
        use(keeper, c.keeperId, c.bowlKit, Role.KEEPER, 3f)
        use(umpBowl, "umpire-a", null, Role.UMPIRE, 20f)
        use(umpLeg, "umpire-b", null, Role.UMPIRE, 25f)
        for (i in 0 until 9) use(fielders[i], c.fielderIds.getOrElse(i) { "f$i" }, c.bowlKit, Role.FIELDER, hypot(fpos[i][0], fpos[i][1]))
        for (x in all.subList(15, all.size)) x.dress(Look.of("x"), null, Role.FIELDER, 1)
        resetStumps()
    }

    /** Plan & start the sequence for [e]. */
    fun start(e: BallEv, durationMs: Int) {
        val c = cast ?: return
        ev = e; t = 0f; done = false; firedImpact = false; firedOutcome = false
        val h = (e.id * 2654435761L).toInt().let { abs(it) }
        fun r(k: Int) = ((h shr k) and 1023) / 1023f
        // bowler
        bowlX = -0.45f * armSign
        keeperZ = if (spin) -0.9f else -8f
        runSpeed = if (spin) 3.6f else 7.2f
        creaseZ = 20.12f + if (spin) 1.25f else 1.55f
        runStartZ = creaseZ + if (spin) 4.5f else 11f
        tRunEnd = (runStartZ - creaseZ) / runSpeed
        val relClip = if (spin) 1.30f else 1.68f
        tRel = tRunEnd + relClip
        rel[0] = bowlX + armSign * 0.28f; rel[1] = if (spin) 2.05f else 2.25f; rel[2] = creaseZ - 1.15f
        val speed = if (spin) 22f + 3f * r(1) else 36f + 6f * r(1)
        val wide = e.kind == "WIDE"
        contact[0] = offSign * (if (wide) 1.15f else 0.18f + 0.15f * r(3)); contact[1] = 0.62f; contact[2] = if (e.kind == "WICKET" && e.how.startsWith("b ")) 0.1f else 1.35f
        bounce[2] = if (spin) 3.2f + 1.6f * r(5) else 4.5f + 3.5f * r(5)
        bounce[0] = rel[0] + (contact[0] - rel[0]) * ((rel[2] - bounce[2]) / (rel[2] - contact[2])); bounce[1] = 0.036f
        val flight = (rel[2] - contact[2]) / speed
        tBounce = tRel + flight * ((rel[2] - bounce[2]) / (rel[2] - contact[2])); tImp = tRel + flight
        // outcome
        runs = e.bat; wicket = ""; umpSignal = ""; chaser = -1; aerial = false; hitBall = true
        val ang = e.angle
        val how = e.how
        when (e.kind) {
            "WICKET" -> wicket = when {
                how.startsWith("lbw") -> "lbw"; how.startsWith("b ") -> "bowled"; how.startsWith("st ") -> "stumped"
                how.startsWith("run out") || how.contains("run out") -> "runout"; else -> "caught"
            }
        }
        shot = when {
            wide -> "bat_leave"
            wicket == "bowled" || wicket == "lbw" || wicket == "stumped" -> "bat_miss"
            e.kind == "DOT" -> if (r(7) < 0.3f) "bat_leave" else "bat_defend"
            else -> shotFor(ang, e.kind == "SIX" || wicket == "caught" && e.dist > 0.6f)
        }
        hitBall = shot != "bat_leave" && shot != "bat_miss"
        val dirA = Math.toRadians(ang.toDouble())
        val dx = (offSign * sin(dirA)).toFloat(); val dz = cos(dirA).toFloat()
        // real oval: distance to the rope along this shot's direction
        val bd = Arena3D.boundaryDist(contact[0], contact[2], dx, dz)
        var dist = when (e.kind) { "FOUR" -> bd + 2.2f; "SIX" -> bd + 6f + 12f * r(9); "DOT" -> 2f + 4f * r(9); else -> min(e.dist.coerceIn(0.05f, 1.2f) * 62f, bd - 3f) }
        if (wicket == "caught") {
            // ball goes to the nearest fielder (or keeper/slip when edged)
            var best = 0; var bd = 1e9f
            val tx = contact[0] + dx * e.dist * 62f; val tz = contact[2] + dz * e.dist * 62f
            for (i in 0 until 9) { val d = hypot(fpos[i][0] - tx, fpos[i][1] - tz); if (d < bd) { bd = d; best = i } }
            chaser = best; land[0] = fpos[best][0]; land[2] = fpos[best][1]; land[1] = 1.4f; aerial = true
            tLand = tImp + max(0.6f, hypot(land[0] - contact[0], land[2] - contact[2]) / 24f)
        } else if (hitBall) {
            land[0] = contact[0] + dx * dist; land[2] = contact[2] + dz * dist; land[1] = 0.036f
            aerial = e.kind == "SIX"
            tLand = tImp + if (aerial) 2.6f + 0.6f * r(11) else dist / (if (e.kind == "FOUR") 26f else 18f)
            if (e.kind != "SIX" && e.kind != "DOT") {
                var best = 0; var bd = 1e9f
                for (i in 0 until 9) { val d = hypot(fpos[i][0] - land[0], fpos[i][1] - land[2]); if (d < bd) { bd = d; best = i } }
                chaser = best
            }
        } else {
            land[0] = offSign * 0.3f; land[2] = keeperZ + 0.5f; land[1] = 0.75f; tLand = tImp + (contact[2] - land[2]) / (speed * 0.8f)
        }
        chaseFrom = floatArrayOf(chaser.takeIf { it >= 0 }?.let { fpos[it][0] } ?: 0f, chaser.takeIf { it >= 0 }?.let { fpos[it][1] } ?: 0f)
        throwT = if (chaser >= 0 && wicket != "caught") tLand + 0.4f else -1f
        umpSignal = when {
            wicket != "" -> if (wicket == "runout" || wicket == "stumped") "" else "ump_out"
            e.kind == "FOUR" -> "ump_four"; e.kind == "SIX" -> "ump_six"; wide -> "ump_wide"; else -> ""
        }
        val runTime = if (runs in 1..3 && e.kind != "FOUR" && e.kind != "SIX") runs * 2.9f else 0f
        tEnd = maxOf(tLand + 1.2f, tImp + 2.0f + runTime, if (umpSignal.isNotEmpty()) tImp + 3.4f else 0f)
        // fit long sequences into the user's sim speed, without looking sped-up on normal settings
        timeScale = (tEnd / (durationMs / 1000f * 1.9f)).coerceIn(1f, 1.9f)
        if (replay) timeScale = 0.55f
        // initial poses
        resetStumps()
        place(striker, 0.0f + offSign * 0.25f, 0f, 1.05f, if (offSign < 0) -90f else 90f, offSign > 0); striker.play("bat_stance", true, 0.01f)
        place(nonStriker, -bowlX * 2.0f, 0f, 20.12f + 1.4f, 180f, false); nonStriker.play("idle", true, 0.01f)
        place(bowler, bowlX, 0f, runStartZ, 180f, armSign < 0); bowler.play(if (spin) "walk" else "run", true, 0.01f)
        place(keeper, offSign * 0.25f, 0f, keeperZ, 0f, false); keeper.play("keep_crouch", true, 0.01f)
        place(umpBowl, -bowlX * 1.4f, 0f, 20.12f + 2.3f, 180f, false); umpBowl.play("ump_idle", true, 0.01f)
        place(umpLeg, -offSign * 24f, 0f, 1.2f, if (offSign < 0) 90f else -90f, false); umpLeg.play("ump_idle", true, 0.01f)
        for (i in 0 until 9) {
            val f = fielders[i]; val yaw = Math.toDegrees(atan2(-fpos[i][0].toDouble(), (1.0 - fpos[i][1]))).toFloat()
            place(f, fpos[i][0], 0f, fpos[i][1], yaw, false); f.play("field_ready", true, 0.01f)
        }
        a.setTransform(a.ball, rel[0], rel[1], rel[2])
    }

    private fun shotFor(angle: Float, air: Boolean): String {
        val g = ((angle % 360f) + 360f) % 360f
        return when {
            g < 25f || g > 335f -> if (air) "bat_loft" else "bat_straight_drive"
            g < 80f -> if (air) "bat_loft" else "bat_cover_drive"
            g < 150f -> "bat_cut"
            g < 215f -> if (spin) "bat_reverse_sweep" else "bat_cut"
            g < 260f -> if (spin) "bat_sweep" else "bat_hook"
            else -> if (air && !spin) "bat_hook" else "bat_pull"
        }
    }

    private fun place(act: Actor, x: Float, y: Float, z: Float, yaw: Float, mirror: Boolean) { act.x = x; act.y = y; act.z = z; act.yaw = yaw; act.mirror = mirror }

    private fun resetStumps() {
        for (end in listOf("bat" to 0f, "bowl" to 20.12f)) {
            for (i in 0..2) a.stumps["stump_${end.first}_$i"]?.let { a.setTransform(it, (i - 1) * 0.0953f, 0f, end.second) }
            for (i in 0..1) a.stumps["bail_${end.first}_$i"]?.let { a.setTransform(it, (i * 2 - 1) * 0.054f, 0.718f, end.second) }
        }
    }
    private fun breakStumps(end: String, z: Float, k: Float) {
        val e = k.coerceIn(0f, 1f)
        for (i in 0..2) a.stumps["stump_${end}_$i"]?.let { a.setTransform(it, (i - 1) * 0.0953f, 0f, z - 0.05f * e * (i + 1), 0f, 1f, -55f * e * (0.6f + 0.3f * i), (i - 1) * 25f * e) }
        for (i in 0..1) a.stumps["bail_${end}_$i"]?.let {
            val tt = e * 0.9f; a.setTransform(it, (i * 2 - 1) * (0.054f + 1.2f * tt), 0.718f + 1.6f * tt - 3.5f * tt * tt, z - 1.8f * tt, 0f, 1f, 720f * tt, 360f * tt * (i * 2 - 1))
        }
    }

    private fun ballAt(tt: Float, out: FloatArray) {
        when {
            tt < tRel -> { out[0] = rel[0]; out[1] = rel[1]; out[2] = rel[2]; }
            tt < tBounce -> { val u = (tt - tRel) / (tBounce - tRel); lerp3(rel, bounce, u, out); out[1] = rel[1] + (bounce[1] - rel[1]) * u * u.coerceAtLeast(0.6f) }
            tt < tImp -> { val u = (tt - tBounce) / (tImp - tBounce); lerp3(bounce, contact, u, out); out[1] = bounce[1] + (contact[1] - bounce[1]) * sqrt(u) }
            tt < tLand -> {
                val u = (tt - tImp) / (tLand - tImp)
                lerp3(contact, land, if (aerial || !hitBall) u else 1f - (1f - u) * (1f - u), out)
                out[1] = if (aerial) contact[1] + (land[1] - contact[1]) * u + (if (ev?.kind == "SIX") 26f else 9f) * 4f * u * (1f - u)
                         else if (!hitBall) contact[1] + (land[1] - contact[1]) * u else 0.036f + 0.5f * abs(sin(u * 9f)) * (1f - u) * 0.6f
            }
            else -> {
                out[0] = land[0]; out[1] = land[1]; out[2] = land[2]
                if (throwT > 0 && tt > throwT + 0.35f) { val u = ((tt - throwT - 0.35f) / 0.9f).coerceIn(0f, 1f); out[0] = land[0] + (offSign * 0.3f - land[0]) * u; out[2] = land[2] + ((if (wicket == "runout") 20.12f else -0.6f) - land[2]) * u; out[1] = 0.6f + 6f * u * (1 - u) }
            }
        }
    }
    private fun lerp3(p: FloatArray, q: FloatArray, u: Float, o: FloatArray) { for (i in 0..2) o[i] = p[i] + (q[i] - p[i]) * u }
    private val bp = FloatArray(3)

    /** Advance by dt seconds of real time and apply transforms/animations/camera. */
    fun update(dtReal: Float) {
        if (title) { updateTitle(dtReal); return }
        val e = ev
        if (e == null || done) { idle(dtReal); return }
        val dt = dtReal / timeScale * (if (replay) 1f else 1f)
        t += dt
        // bowler
        if (t < tRunEnd) { bowler.z = runStartZ - runSpeed * t }
        else { if (bowler.z != creaseZ) { bowler.z = creaseZ; bowler.play(if (spin) "bowl_spin" else "bowl_fast", false, 0.12f) } }
        // batter: start shot so the clip's impact frame (0.55 s) meets the ball
        if (t >= tImp - 0.55f && striker.clipTime >= 0f && t < tImp + 0.1f) { striker.play(shot, false, 0.1f) }
        val runTime = if (runs in 1..3 && e.kind != "FOUR" && e.kind != "SIX" && wicket != "caught") runs * 2.9f else 0f
        if (runTime > 0 || wicket == "runout") {
            val start = tImp + 0.9f
            if (t > start) {
                val leg = ((t - start) / 2.9f); val k = floor(leg).toInt(); val u = (leg - k).coerceIn(0f, 1f)
                val nRuns = if (wicket == "runout") 1 else runs
                if (k < nRuns) {
                    val from = if (k % 2 == 0) 1.05f else 19.07f; val to = if (k % 2 == 0) 19.07f else 1.05f
                    val s = u * u * (3 - 2 * u)
                    striker.z = from + (to - from) * s; nonStriker.z = to + (from - to) * s
                    striker.yaw = if (to > from) 0f else 180f; nonStriker.yaw = if (to > from) 180f else 0f
                    striker.play("bat_run", true, 0.15f); nonStriker.play("bat_run", true, 0.15f)
                    if (wicket == "runout" && u > 0.85f) breakStumps("bowl", 20.12f, (u - 0.85f) * 6f)
                } else { striker.play("bat_stance", true, 0.3f); nonStriker.play("idle", true, 0.3f) }
            }
        }
        // ball
        ballAt(t, bp); a.setTransform(a.ball, bp[0], bp[1], bp[2])
        if (!firedImpact && t >= tImp) { firedImpact = true; onImpact?.invoke(e) }
        // wickets
        if (wicket == "bowled" && t > tImp) breakStumps("bat", 0f, (t - tImp) * 3f)
        if (wicket == "stumped" && t > tImp + 0.35f) { keeper.play("keep_stump", false, 0.08f); if (t > tImp + 0.8f) breakStumps("bat", 0f, (t - tImp - 0.8f) * 3f) }
        if (wicket == "lbw" && t > tImp + 0.3f) { bowler.play("appeal", false, 0.15f) }
        // keeper
        if (!hitBall && t > tImp - 0.2f && wicket != "stumped") keeper.play("keep_collect", false, 0.1f)
        // fielding
        if (chaser >= 0 && t > tImp + 0.15f) {
            val f = fielders[chaser]
            if (wicket == "caught") {
                if (t > tLand - 0.55f) f.play("field_catch", false, 0.1f)
            } else if (t < tLand + 0.4f) {
                val u = ((t - tImp - 0.15f) / (tLand - tImp + 0.25f)).coerceIn(0f, 1f)
                val tx = land[0]; val tz = land[2]
                f.x = chaseFrom[0] + (tx - chaseFrom[0]) * u; f.z = chaseFrom[1] + (tz - chaseFrom[1]) * u
                f.yaw = Math.toDegrees(atan2((tx - chaseFrom[0]).toDouble(), (tz - chaseFrom[1]).toDouble())).toFloat()
                if (hypot(tx - f.x, tz - f.z) > 0.8f) f.play("run", true, 0.15f)
                else if (e.kind == "FOUR" && u > 0.9f) f.play("field_dive", false, 0.1f)
            } else if (throwT > 0 && t > throwT) {
                f.yaw = Math.toDegrees(atan2((0f - f.x).toDouble(), ((if (wicket == "runout") 20.12f else 0f) - f.z).toDouble())).toFloat(); f.play("field_throw", false, 0.1f)
            }
        }
        // celebrations & umpire
        if (!firedOutcome && t >= min(tLand, tImp + 1.0f)) { firedOutcome = true; onOutcome?.invoke(e) }
        if (umpSignal.isNotEmpty() && t > tImp + 1.3f) umpBowl.play(umpSignal, false, 0.2f)
        if (wicket.isNotEmpty() && t > tLand + 0.3f) {
            bowler.play("celebrate", false, 0.2f); fielders.forEachIndexed { i, f -> if (i != chaser || wicket == "caught") if (t > tLand + 0.6f) f.play("celebrate", false, 0.3f) }
        }
        if ((e.kind == "FOUR" || e.kind == "SIX" || e.milestone) && t > tLand + 0.2f) striker.play("bat_celebrate", false, 0.3f)
        for (act in a.actors) act.update(dt)
        camera(dt, e)
        if (t >= tEnd) { done = true; onDone?.invoke() }
    }

    private var idleT = 0f
    private fun idle(dt: Float) {
        idleT += dt
        for (act in a.actors) act.update(dt)
        val ang = idleT * 0.05f
        if (cast == null) a.lookAt(60f * sin(ang), 24f, 10.06f + 60f * cos(ang), 0f, 0f, 10.06f, 40f)
        else when (cam) { 3 -> a.lookAt(0f, 1.8f, -7f, 0f, 1.2f, 20f, 30f); else -> a.lookAt(0f, 11f, 92f, 0f, 1.0f, 2.5f, 11f) }
    }

    // smoothed follow camera
    private val ce = floatArrayOf(0f, 11f, 92f); private val ct = floatArrayOf(0f, 1f, 2f); private var cfov = 15f
    private fun camera(dt: Float, e: BallEv) {
        val k = 1f - exp(-dt * 4f)
        val follow = t > tImp + 0.35f && hitBall
        var ex: Float; var ey: Float; var ez: Float; var tx: Float; var ty: Float; var tz: Float; var fov: Float
        val mode = if (replay) 2 else cam
        when {
            mode == 3 && !follow -> { ex = 0f; ey = 1.8f; ez = -7f; tx = 0f; ty = 1.3f; tz = 18f; fov = 30f }
            mode == 1 -> { ex = bp[0] * 0.5f; ey = 22f + (if (follow) 10f else 0f); ez = (bp[2] - 14f).coerceIn(-30f, 30f); tx = bp[0]; ty = 0f; tz = bp[2]; fov = 50f }
            mode == 2 -> { ex = -offSign * 34f; ey = 3.2f; ez = 10f; tx = bp[0] * 0.6f; ty = 1f; tz = if (follow) bp[2] else 6f; fov = if (follow) 34f else 24f }
            !follow -> { ex = 0f; ey = 11f; ez = 92f; tx = 0f; ty = 1.0f; tz = 2.5f; fov = 10.5f }   // TV gantry above the far sight screen
            else -> { ex = 0f; ey = 30f; ez = 20.12f + 48f; tx = bp[0]; ty = 0f; tz = bp[2]; fov = 38f }
        }
        val snap = (t < 0.05f)
        val kk = if (snap) 1f else k
        ce[0] += (ex - ce[0]) * kk; ce[1] += (ey - ce[1]) * kk; ce[2] += (ez - ce[2]) * kk
        ct[0] += (tx - ct[0]) * kk * 1.6f.coerceAtMost(1f / max(kk, 1e-3f)); ct[1] += (ty - ct[1]) * kk; ct[2] += (tz - ct[2]) * kk
        cfov += (fov - cfov) * kk
        a.lookAt(ce[0], ce[1], ce[2], ct[0], ct[1], ct[2], cfov)
    }

    /** Title screen: slow cinematic orbit with a featured star in the foreground. */
    private var tt0 = 0f
    fun setupTitle(starId: String, country: String, kit: Kit) {
        title = true
        for (x in a.actors) x.active = false
        val star = a.actors[0]; star.active = true; star.dress(Look.of(starId, country), kit, Role.STRIKER, 0)
        place(star, 0f, 0f, 1.05f, -90f, false); star.play("bat_stance", true, 0.01f)
        val names = listOf("field_ready", "idle", "keep_crouch", "field_ready", "field_ready", "idle")
        for (i in 1..6) { val act = a.actors[i]; act.active = true; act.dress(Look.of("t$i"), kit, if (i == 3) Role.KEEPER else Role.FIELDER, 1)
            val ang = i * 1.05f; place(act, 24f * sin(ang), 0f, 10f + 24f * cos(ang), 180f + i * 40f, false); act.play(names[i - 1], true) }
        a.setTransform(a.ball, 0f, -5f, 0f)
    }
    private var shotT = 0f
    private fun updateTitle(dt: Float) {
        tt0 += dt; shotT += dt
        val star = a.actors[0]
        val cyc = listOf("bat_cover_drive", "bat_pull", "bat_straight_drive", "bat_loft")
        if (shotT > 5.5f) { shotT = 0f; star.play(cyc[((tt0 / 5.5f).toInt()) % cyc.size], false, 0.15f) }
        else if (shotT > 2.2f) star.play("bat_stance", true, 0.4f)
        for (act in a.actors) act.update(dt)
        val ang = tt0 * 0.06f
        val r = 4.2f + 1.2f * sin(tt0 * 0.1f)
        a.lookAt(-r * cos(ang) - 0.3f, 1.25f + 0.25f * sin(tt0 * 0.13f), 1.05f + r * sin(ang) + 1.4f, 0f, 1.05f, 1.6f + 6f * (0.5f + 0.5f * sin(tt0 * 0.05f)), 42f)
    }
}
