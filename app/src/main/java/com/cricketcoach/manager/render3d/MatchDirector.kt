package com.cricketcoach.manager.render3d

import com.cricketcoach.manager.game.BallEv
import kotlin.math.*

/** Who is on the field for the current delivery (resolved from the real squad data). */
class Cast(
    val strikerId: String, val strikerLeft: Boolean, val nonStrikerId: String,
    val bowlerId: String, val bowlerLeftArm: Boolean, val bowlerSpin: Boolean,
    val keeperId: String, val fielderIds: List<String>, val countryOf: (String) -> String,
    val batKit: Kit, val bowlKit: Kit,
    /** this delivery's dismissal details, mapped onto the actors: catcher slot (0..8 = fielderIds index, or PlanCast.KEEPER/BOWLER/NONE) */
    val catcher: Int = PlanCast.NONE, val outIsStriker: Boolean = true, val runOutFielder: Int = PlanCast.NONE
) { fun planCast() = PlanCast(strikerLeft, bowlerLeftArm, bowlerSpin, catcher, outIsStriker, runOutFielder) }

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

    private var offSign = -1f
    private var spin = false
    private val fpos = Array(9) { FloatArray(2) }
    private var plan: DeliveryPlan? = null
    /** playback speed (1x..8x, free); replays always run at slow motion */
    var rate = 1f
    private val FIELD_PACE = arrayOf(160f to 17f, 150f to 18f, 95f to 26f, 55f to 28f, 18f to 30f, 342f to 30f, 300f to 27f, 255f to 24f, 205f to 58f)
    private val FIELD_SPIN = arrayOf(150f to 7f, 120f to 50f, 95f to 24f, 55f to 26f, 15f to 28f, 345f to 28f, 300f to 25f, 250f to 22f, 210f to 52f)

    private fun polar(angle: Float, r: Float, out: FloatArray, oz: Float = 0f) {
        val ar = Math.toRadians(angle.toDouble())
        out[0] = (offSign * sin(ar) * r).toFloat(); out[1] = (cos(ar) * r).toFloat() + oz
    }

    fun setup(c: Cast) {
        cast = c
        offSign = if (c.strikerLeft) 1f else -1f
        spin = c.bowlerSpin
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

    /** Plan & start the sequence for [e] (deterministic timeline: see [DeliveryPlan]). */
    fun start(e: BallEv, @Suppress("UNUSED_PARAMETER") durationMs: Int, c: Cast? = null) {
        val cc = c ?: cast ?: return
        ev = e; t = 0f; done = false; firedImpact = false; firedOutcome = false
        plan = try { DeliveryPlan(e, cc.planCast()) } catch (x: Throwable) { android.util.Log.e("MatchDirector", "plan failed", x); null }
        if (plan == null) { done = true; onDone?.invoke(); return }
        resetStumps()
        for (act in a.actors) act.resetDrive()
        apply(0f, 0f)
    }

    private fun place(act: Actor, x: Float, y: Float, z: Float, yaw: Float, mirror: Boolean) { act.x = x; act.y = y; act.z = z; act.yaw = yaw; act.mirror = mirror }

    private fun resetStumps() {
        for (end in listOf("bat" to 0f, "bowl" to 20.12f)) {
            for (i in 0..2) a.stumps["stump_${end.first}_$i"]?.let { a.setTransform(it, (i - 1) * 0.0953f, 0f, end.second) }
            for (i in 0..1) a.stumps["bail_${end.first}_$i"]?.let { a.setTransform(it, (i * 2 - 1) * 0.054f, 0.718f, end.second) }
        }
    }
    /** stumps knocked back and bails flying, [k] seconds after the break, pushed in direction [dir] (+1 towards the far end) */
    private fun breakStumps(end: String, z: Float, k: Float, dir: Float) {
        val e = (k * 3f).coerceIn(0f, 1f)
        for (i in 0..2) a.stumps["stump_${end}_$i"]?.let { a.setTransform(it, (i - 1) * 0.0953f, 0f, z + dir * 0.05f * e * (i + 1), 0f, 1f, dir * 55f * e * (0.6f + 0.3f * i), (i - 1) * 25f * e) }
        for (i in 0..1) a.stumps["bail_${end}_$i"]?.let {
            val tt = (k * 2.7f).coerceIn(0f, 0.95f)
            val y = (0.718f + 1.6f * tt - 3.5f * tt * tt).coerceAtLeast(0.02f)
            a.setTransform(it, (i * 2 - 1) * (0.054f + 1.2f * tt), y, z + dir * 1.8f * tt, 0f, 1f, 720f * tt, 360f * tt * (i * 2 - 1))
        }
    }

    private val bp = FloatArray(3)

    /** pose every actor, the ball and the stumps at timeline time [tt] */
    private fun apply(tt: Float, dt: Float) {
        val p = plan ?: return
        p.eval(tt)
        for (i in 0 until DeliveryPlan.N) {
            val act = a.actors[i]; val ps = p.pose[i]
            act.x = ps.x; act.y = 0f; act.z = ps.z; act.yaw = ps.yaw; act.mirror = ps.mirror
            act.drive(ps.clip, ps.loop, ps.clipStart, ps.clipTime(tt), ps.fade, dt)
        }
        p.ball.copyInto(bp)
        a.setTransform(a.ball, bp[0], bp[1], bp[2])
        if (p.breakBat >= 0f && tt >= p.breakBat) breakStumps("bat", 0f, tt - p.breakBat, -1f)
        if (p.breakBowl >= 0f && tt >= p.breakBowl) breakStumps("bowl", 20.12f, tt - p.breakBowl, 1f)
    }

    /** Advance by dt seconds of real time and apply transforms/animations/camera. */
    fun update(dtReal: Float) {
        if (showroom) { updateShowroom(dtReal); return }
        if (title) { updateTitle(dtReal); return }
        val e = ev; val p = plan
        if (e == null || p == null || done) { idle(dtReal); return }
        val dt = dtReal * (if (replay) 0.55f else rate.coerceIn(0.25f, 8f))
        t = min(t + dt, p.tEnd)
        apply(t, dt)
        if (!firedImpact && t >= p.tImp) { firedImpact = true; onImpact?.invoke(e) }
        if (!firedOutcome && t >= p.tOut) { firedOutcome = true; onOutcome?.invoke(e) }
        camera(dt, e)
        if (t >= p.tEnd) { done = true; if (!firedImpact) onImpact?.invoke(e); if (!firedOutcome) onOutcome?.invoke(e); onDone?.invoke() }
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
        val p = plan
        val follow = p != null && p.hitBall && t > p.tImp + 0.35f
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
        title = true; showroom = false
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

    /* ------------------------------------------------------------ Phase C: player showroom (profile / auction 3D viewer) */
    var showroom = false; private set
    /** camera orbit around the player, degrees (dragged by the user); auto-spins slowly while [autoSpin] */
    var orbitYaw = 205f
    var orbitDist = 3.6f
    var autoSpin = true
    private var showSeq: List<String> = listOf("idle")
    private var showIdx = 0
    private var showClipT = 0f
    private var showLook: Look? = null
    private val SX = 6f; private val SZ = 9f          // on the square, well clear of both sets of stumps
    private val drift = FloatArray(2)

    /** Put [pid] alone on the square in his kit, playing the clips of [sc] (with the matching gear). */
    fun setupShowroom(pid: String, country: String, kit: Kit, sc: Showcase) {
        showroom = true; title = false
        for (x in a.actors) x.active = false
        val look = Look.of(pid, country); showLook = look
        val star = a.actors[0]; star.active = true
        star.dress(look, kit, if (sc.gear == Gear.KEEPING) Role.KEEPER else Role.STAR, 0, sc.gear, inMatch = false)
        showSeq = sc.clips; showIdx = 0; showClipT = 0f
        place(star, SX, 0f, SZ, 0f, false); star.resetDrive()
        a.setTransform(a.ball, 0f, -5f, 0f)
    }

    private fun updateShowroom(dt: Float) {
        val star = a.actors[0]
        val clip = showSeq[showIdx % showSeq.size]
        val single = showSeq.size == 1
        val dur = ClipData.DUR[clip] ?: 1f
        showClipT += dt
        // hold the last frame of one-shot clips briefly, then move on through the sequence
        val hold = if (clip.startsWith("bat_stance") || clip == "field_ready" || clip == "keep_crouch") 0.6f else 0.45f
        if (!single && showClipT > dur + hold) { showIdx++; showClipT = 0f }
        val c2 = showSeq[showIdx % showSeq.size]
        val loop = single || c2 == "idle" || c2 == "run" || c2 == "bat_stance" || c2 == "field_ready" || c2 == "keep_crouch"
        val cycle = max(dur + 0.6f, 0.1f)
        val tt = if (single && !loop) showClipT % cycle else showClipT
        val seg = when { single && loop -> 0f; single -> (showClipT / cycle).toInt() * 100f; else -> showIdx * 1000f }
        val time = if (loop) tt else min(tt, dur)
        // the run-up travels (its root drift is re-applied so the feet don't skate); each delivery restarts from the mark
        if (c2.startsWith("bowl_")) ClipData.drift(c2, min(tt, dur), drift) else { drift[0] = 0f; drift[1] = 0f }
        star.x = SX + drift[0] * star.scale; star.z = SZ + drift[1] * star.scale; star.yaw = 0f
        star.drive(c2, loop, seg, time, 0.25f, dt)
        if (autoSpin) orbitYaw += dt * 9f
        val yr = Math.toRadians(orbitYaw.toDouble())
        val s = showLook?.scale ?: 1f
        val cx = star.x; val cz = star.z
        a.lookAt((cx + orbitDist * sin(yr)).toFloat(), 1.15f * s + 0.2f, (cz + orbitDist * cos(yr)).toFloat(), cx, 0.95f * s, cz, 34f)
    }
}
