package com.cricketcoach.manager.render3d

import com.cricketcoach.manager.game.BallEv
import kotlin.math.*

/** Ground geometry shared by the renderer and the planner (metres; striker stumps z=0, bowler stumps z=20.12, y up). */
object Ground {
    const val CZ = 10.06f; const val BX = 63f; const val BZ = 69f
    const val FAR = 20.12f; const val POP = 1.22f

    /** distance along (dx,dz) from (x0,z0) to the elliptical rope */
    fun boundaryDist(x0: Float, z0: Float, dx: Float, dz: Float): Float {
        val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f); val ux = dx / l; val uz = dz / l
        val a = (ux / BX).pow(2) + (uz / BZ).pow(2); val b = 2f * (x0 * ux / (BX * BX) + (z0 - CZ) * uz / (BZ * BZ))
        val c = (x0 / BX).pow(2) + ((z0 - CZ) / BZ).pow(2) - 1f
        return (-b + sqrt((b * b - 4 * a * c).coerceAtLeast(0f))) / (2 * a)
    }
    /** < 1 inside the rope */
    fun rope(x: Float, z: Float): Float = (x / BX).pow(2) + ((z - CZ) / BZ).pow(2)
    /** height of the lower-tier seating [beyond] metres outside the rope (sixes land in the crowd) */
    fun standHeight(beyond: Float): Float = if (beyond < 8f) 0.04f else 2.4f + (beyond - 8f).coerceAtMost(23f) * (11.1f / 23f)
}

/** What the planner needs to know about the people involved, beyond the BallEv itself. */
class PlanCast(
    val strikerLeft: Boolean, val bowlerLeftArm: Boolean, val spin: Boolean,
    /** who took the catch: fielder slot 0..8, [KEEPER], [BOWLER] or [NONE] (unknown -> nearest fielder) */
    val catcher: Int = NONE,
    /** run-outs: true if the striker was run out (at the bowler's end), false for the non-striker (at the striker's end) */
    val outIsStriker: Boolean = true,
    /** fielder slot that effected the run-out, or [NONE] */
    val runOutFielder: Int = NONE
) { companion object { const val NONE = -99; const val KEEPER = -1; const val BOWLER = -2 } }

/** Resolved state of one person at one instant. x/z include clip root travel; ax/az are the clip-space origin (for hand look-ups). */
class Pose {
    var x = 0f; var z = 0f; var ax = 0f; var az = 0f; var yaw = 0f; var mirror = false
    var clip = "idle"; var loop = true; var clipStart = 0f; var speed = 1f; var fade = 0.2f
    var walkingOff = false
    fun clipTime(t: Float): Float = max(0f, t - clipStart) * speed
}

/**
 * One delivery as a deterministic, stateless timeline: given [ev] it plans the run-up, delivery stride and release (ball in
 * the bowler's hand), a ballistic delivery that bounces and arrives exactly on the bat's sweet spot / pad / stumps / keeper's
 * gloves, the shot, fielders reacting at human speed (interception, pick-up, throw, catch, dive), batters running their real
 * number of runs in their lanes, wicket mechanics, umpire signals and celebrations.  [eval] fills every [Pose] and the ball for
 * any time t, so playback speed, replays and frame drops can never desynchronise it.  Index: 0 striker, 1 non-striker,
 * 2 bowler, 3 keeper, 4 bowler's-end umpire, 5 square-leg umpire, 6..14 fielders.
 */
class DeliveryPlan(val ev: BallEv, val pc: PlanCast) {
    companion object {
        const val N = 15; const val STR = 0; const val NON = 1; const val BWL = 2; const val KPR = 3; const val UMB = 4; const val UML = 5; const val F0 = 6
        const val G = 9.81f; const val V_FIELD = 7.0f; const val REACT = 0.35f; const val ROLL_K = 1.6f; const val SEP = 0.6f
        private val FIELD_PACE = arrayOf(160f to 17f, 150f to 18f, 95f to 26f, 55f to 28f, 18f to 30f, 342f to 30f, 300f to 27f, 255f to 24f, 205f to 58f)
        private val FIELD_SPIN = arrayOf(150f to 7f, 120f to 50f, 95f to 24f, 55f to 26f, 15f to 28f, 345f to 28f, 300f to 25f, 250f to 22f, 210f to 52f)
        val SHOTS = listOf("bat_defend", "bat_cover_drive", "bat_straight_drive", "bat_pull", "bat_hook", "bat_cut", "bat_sweep", "bat_reverse_sweep", "bat_loft")

        fun toWorld(ax: Float, az: Float, yawDeg: Float, mirror: Boolean, l: FloatArray, o: FloatArray) {
            val y = Math.toRadians(yawDeg.toDouble()); val c = cos(y).toFloat(); val s = sin(y).toFloat()
            val mx = if (mirror) -l[0] else l[0]
            o[0] = ax + c * mx + s * l[2]; o[1] = l[1]; o[2] = az - s * mx + c * l[2]
        }
        fun yawTo(dx: Float, dz: Float): Float = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        fun smooth(u: Float): Float { val v = u.coerceIn(0f, 1f); return v * v * (3 - 2 * v) }
        fun lerpAngle(a: Float, b: Float, u: Float): Float { var d = (b - a) % 360f; if (d > 180f) d -= 360f; if (d < -180f) d += 360f; return a + d * u.coerceIn(0f, 1f) }
        /** trapezoidal speed profile: normalised distance after normalised time u */
        fun trap(u: Float, ta: Float = 0.2f, td: Float = 0.18f): Float {
            val x = u.coerceIn(0f, 1f); val vmax = 1f / (1f - ta / 2 - td / 2)
            return when { x < ta -> 0.5f * vmax * x * x / ta; x < 1 - td -> vmax * (ta / 2 + (x - ta)); else -> { val r = 1 - x; 1f - 0.5f * vmax * r * r / td } }
        }
        fun trapInv(f: Float): Float { var lo = 0f; var hi = 1f; repeat(30) { val m = (lo + hi) / 2; if (trap(m) < f) lo = m else hi = m }; return (lo + hi) / 2 }
    }

    // ------------------------------------------------------------------ public timeline
    val pose = Array(N) { Pose() }
    val ball = FloatArray(3)
    var tRel = 0f; private set
    /** bat/pad/stumps contact (or the ball reaching the keeper when nothing was hit) - the moment for the bat/pad sound */
    var tImp = 0f; private set
    /** the moment the result is clear (catch taken, rope crossed, stumps broken, finger up, ball fielded) */
    var tOut = 0f; private set
    var tEnd = 0f; private set
    var hitBall = false; private set
    var shot = "bat_defend"; private set
    var wicket = ""; private set
    var umpSignal = ""; private set
    /** stumps broken at each end: time, or -1 */
    var breakBat = -1f; private set
    var breakBowl = -1f; private set
    var bowledSpeedKph = 0; private set
    /** where the ball meets bat / pad / stumps / the keeper's line at [tImp] (world) */
    val contact = FloatArray(3)
    val effKind: String

    // ------------------------------------------------------------------ randomness (deterministic per delivery)
    private val h = abs((ev.id.toLong() * 2654435761L + ev.inn * 97L + ev.over * 13L).toInt())
    private fun r(k: Int) = ((h shr (k % 20)) and 1023) / 1023f
    private fun rr(k: Int) = ((((h.toLong() * (k * 7919L + 104729L)) ushr 7) and 1023L).toFloat()) / 1023f

    private val offSign = if (pc.strikerLeft) 1f else -1f
    private val legSign = -offSign
    private val armSign = if (pc.bowlerLeftArm) -1f else 1f
    private val spin = pc.spin
    private val bowlX = -0.45f * armSign
    private val bowlSide = sign(bowlX)
    private val sx = legSign * 0.20f; private val sz0 = 1.05f
    private val sYaw = if (pc.strikerLeft) 90f else -90f
    private val nsX = armSign * 0.95f; private val nsZ0 = 21.7f; private val nsZ1 = 20.45f
    private val laneStr = -armSign * 1.75f; private val laneNon = armSign * 1.75f
    private val zKeepEnd = 0.75f; private val zBowlEnd = 19.45f

    // ------------------------------------------------------------------ tracks
    private class Key(val t: Float, val clip: String, val loop: Boolean, val speed: Float, val yaw: Float, val fade: Float)
    private class Path(val t0: Float, val t1: Float, val x0: Float, val z0: Float, val x1: Float, val z1: Float, val prof: Int, val lane: Float) {
        /** the actor stops dead here (run-outs) */
        var cut = Float.MAX_VALUE
        val tEnd get() = min(t1, cut)
    }
    private inner class Track(val x: Float, val z: Float, val yaw0: Float, val mirror: Boolean) {
        val keys = ArrayList<Key>(); val paths = ArrayList<Path>()
        var walkOffFrom = Float.MAX_VALUE
        /** clips this actor plays mirrored (e.g. a dive to his right) */
        val mirrorClips = HashSet<String>()
        fun key(t: Float, clip: String, loop: Boolean = false, yaw: Float = Float.NaN, speed: Float = 1f, fade: Float = 0.2f) {
            val k = Key(t, clip, loop, speed, yaw, fade); var i = keys.size; while (i > 0 && keys[i - 1].t > t) i--; keys.add(i, k)
        }
        private fun at(p: Path, t: Float, o: FloatArray) {
            val te = min(t, p.tEnd)
            val u = ((te - p.t0) / (p.t1 - p.t0)).coerceIn(0f, 1f)
            val f = when (p.prof) { 1 -> trap(u); 2 -> smooth(u); else -> u }
            o[1] = p.z0 + (p.z1 - p.z0) * f
            o[0] = if (p.lane > 0f) p.x0 + (p.x1 - p.x0) * smooth(f / p.lane) else p.x0 + (p.x1 - p.x0) * f
        }
        fun endPos(o: FloatArray) { if (paths.isEmpty()) { o[0] = x; o[1] = z } else at(paths.last(), Float.MAX_VALUE, o) }
        val endT get() = paths.lastOrNull()?.tEnd ?: 0f
        /** stop dead at time t (drop later paths) */
        fun cutAt(t: Float) { paths.removeAll { it.t0 >= t }; paths.lastOrNull()?.let { if (it.tEnd > t) it.cut = t } }
        /** prof 0 linear, 1 trapezoid (sprint), 2 smooth; lane>0: x eases in over the first [lane] of the distance */
        fun moveTo(t0: Float, t1: Float, x1: Float, z1: Float, prof: Int = 0, lane: Float = 0f) {
            val e = FloatArray(2); endPos(e)
            if (t1 <= t0 + 1e-3f || hypot(x1 - e[0], z1 - e[1]) < 1e-3f) { if (hypot(x1 - e[0], z1 - e[1]) >= 1e-3f) paths.add(Path(t0, t0 + 1e-3f, e[0], e[1], x1, z1, 0, 0f)); return }
            paths.add(Path(max(t0, endT), t1, e[0], e[1], x1, z1, prof, lane))
        }
        /** position at t (o[0], o[1]); returns the path being travelled or null */
        fun pos(t: Float, o: FloatArray): Path? {
            o[0] = x; o[1] = z
            for (p in paths) {
                if (t <= p.t0) return null
                at(p, t, o)
                if (t < p.tEnd) return p
            }
            return null
        }
        private fun keyAt(t: Float): Int { var k = -1; for (i in keys.indices) { if (keys[i].t <= t) k = i else break }; return k }
        private val tmp = FloatArray(2)
        /** raw (unsmoothed) yaw target at t */
        fun yawRaw(t: Float): Float {
            val ki = keyAt(t)
            var y = yaw0; var tExp = -1f
            for (i in 0..ki) if (!keys[i].yaw.isNaN()) { y = keys[i].yaw; tExp = keys[i].t }
            if (ki >= 0 && !keys[ki].yaw.isNaN()) return y
            val p = pos(t, tmp)
            if (p != null) { val dx = p.x1 - p.x0; val dz = p.z1 - p.z0; if (hypot(dx, dz) > 0.05f) return yawTo(dx, dz) }
            val last = paths.lastOrNull { it.tEnd <= t && it.t1 - it.t0 > 0.01f && hypot(it.x1 - it.x0, it.z1 - it.z0) > 0.05f }
            if (last != null && last.tEnd > tExp) return yawTo(last.x1 - last.x0, last.z1 - last.z0)
            return y
        }
        fun yawAt(t: Float): Float {
            // smooth turns: blend over 0.25 s after the latest change (key or path start)
            var tc = -1f
            for (k in keys) if (k.t <= t && k.t > tc) tc = k.t
            for (p in paths) { if (p.t1 - p.t0 < 0.06f) continue; if (p.t0 <= t && p.t0 > tc) tc = p.t0; if (p.tEnd <= t && p.tEnd > tc) tc = p.tEnd }
            val target = yawRaw(t)
            if (tc < 0f || t - tc >= 0.25f) return target
            return lerpAngle(yawRaw(tc - 1e-3f), target, smooth((t - tc) / 0.25f))
        }
        private val d2 = FloatArray(2); private val l3 = FloatArray(3); private val w3 = FloatArray(3)
        fun eval(t: Float, p: Pose) {
            pos(t, tmp)
            var ax = tmp[0]; var az = tmp[1]
            val ki = keyAt(t)
            p.walkingOff = t >= walkOffFrom
            if (ki < 0) { p.clip = "idle"; p.loop = true; p.clipStart = 0f; p.speed = 1f; p.fade = 0.2f }
            else {
                val k = keys[ki]; var s = ki
                while (s > 0 && keys[s - 1].clip == k.clip && keys[s - 1].loop == k.loop) s--
                p.clip = k.clip; p.loop = k.loop; p.clipStart = keys[s].t; p.speed = k.speed; p.fade = k.fade
                // root travel of clips played earlier stays with the actor
                for (i in 0 until s) {
                    if (ClipData.DRIFT[keys[i].clip] == null) continue
                    if (i + 1 < keys.size && keys[i + 1].clip == keys[i].clip && keys[i + 1].loop == keys[i].loop) continue
                    var j = i; while (j > 0 && keys[j - 1].clip == keys[i].clip && keys[j - 1].loop == keys[i].loop) j--
                    val dur = (ClipData.DUR[keys[i].clip] ?: 0f); val until = keys[i + 1].t
                    ClipData.drift(keys[i].clip, min(dur, (until - keys[j].t) * keys[i].speed), d2)
                    l3[0] = d2[0]; l3[1] = 0f; l3[2] = d2[1]; toWorld(0f, 0f, yawRaw(keys[j].t), mirror xor (keys[i].clip in mirrorClips), l3, w3); ax += w3[0]; az += w3[2]
                }
            }
            p.ax = ax; p.az = az
            p.mirror = mirror xor (p.clip in mirrorClips)
            p.yaw = yawAt(t)
            val cur = ClipData.DRIFT[p.clip]
            if (cur != null) {
                ClipData.drift(p.clip, min(ClipData.DUR[p.clip] ?: 0f, p.clipTime(t)), d2)
                l3[0] = d2[0]; l3[1] = 0f; l3[2] = d2[1]; toWorld(0f, 0f, p.yaw, p.mirror, l3, w3); p.x = ax + w3[0]; p.z = az + w3[2]
            } else { p.x = ax; p.z = az }
        }
    }

    private val tr = arrayOfNulls<Track>(N)
    private fun T(i: Int) = tr[i]!!
    private val scratch = Pose()
    private val v3 = FloatArray(3)

    /** world position of [who]'s hand at time t (in clip [clip] at clip-time [ct], or whatever it is playing) */
    private fun handWorld(who: Int, t: Float, o: FloatArray, clip: String? = null, ct: Float = -1f) {
        T(who).eval(t, scratch)
        val c = clip ?: scratch.clip
        val time = if (ct >= 0f) ct else scratch.clipTime(t)
        if (!ClipData.hand(c, time, v3, clip == null && scratch.loop)) ClipData.hand("idle", 0f, v3)
        toWorld(scratch.ax, scratch.az, scratch.yaw, scratch.mirror, v3, o)
    }

    // ------------------------------------------------------------------ ball segments
    private abstract inner class Seg(val t0: Float) { abstract fun at(t: Float, o: FloatArray) }
    private inner class Fixed(t0: Float, val p: FloatArray) : Seg(t0) { override fun at(t: Float, o: FloatArray) { p.copyInto(o) } }
    private inner class Ballistic(t0: Float, val p: FloatArray, val v: FloatArray, val g: Float = G) : Seg(t0) {
        override fun at(t: Float, o: FloatArray) { val u = t - t0; o[0] = p[0] + v[0] * u; o[1] = p[1] + v[1] * u - 0.5f * g * u * u; o[2] = p[2] + v[2] * u }
    }
    private inner class Roll(t0: Float, val px: Float, val pz: Float, val dx: Float, val dz: Float, val u0: Float, val k: Float, val sMax: Float, val hop: Float, val y0: Float = 0.036f, val vy0: Float = 0f) : Seg(t0) {
        val tStop = min(u0 / k, if (sMax < u0 * u0 / (2 * k)) (u0 - sqrt((u0 * u0 - 2 * k * sMax).coerceAtLeast(0f))) / k else u0 / k)
        fun s(u: Float): Float { val tt = u.coerceIn(0f, tStop); return min(sMax, u0 * tt - 0.5f * k * tt * tt) }
        fun speedAt(u: Float): Float = if (u >= tStop) 0f else u0 - k * u
        override fun at(t: Float, o: FloatArray) {
            val u = t - t0; val d = s(u)
            o[0] = px + dx * d; o[2] = pz + dz * d
            o[1] = max(y0 + vy0 * u - 0.5f * G * u * u, 0.036f + hop * abs(sin(u * 6.5f)) * exp(-u * 2.4f))
        }
    }
    private inner class Held(t0: Float, val who: Int) : Seg(t0) { override fun at(t: Float, o: FloatArray) { handWorld(who, t, o) } }
    private val segs = ArrayList<Seg>()
    private fun add(s: Seg) { segs.add(s) }
    fun ballAt(t: Float, o: FloatArray) {
        var s: Seg = segs[0]
        for (x in segs) if (x.t0 <= t) s = x else break
        s.at(t, o)
        if (o[1] < 0.036f) o[1] = 0.036f
    }

    /** ballistic from p (time t0) to q (time t1) */
    private fun lob(t0: Float, p: FloatArray, t1: Float, q: FloatArray): Ballistic {
        val d = max(1e-3f, t1 - t0)
        return Ballistic(t0, p.copyOf(), floatArrayOf((q[0] - p[0]) / d, (q[1] - p[1] + 0.5f * G * d * d) / d, (q[2] - p[2]) / d))
    }

    // ------------------------------------------------------------------ field
    private val fpos = Array(9) { FloatArray(2) }      // set positions (after walking in)
    private fun polar(angle: Float, r: Float, out: FloatArray) {
        val ar = Math.toRadians(angle.toDouble()); out[0] = (offSign * sin(ar) * r).toFloat(); out[1] = (cos(ar) * r).toFloat() + 0.6f
    }
    private val standUp: Boolean
    private val kx: Float; private val kz: Float

    // ------------------------------------------------------------------ bowler
    private val vRun = if (spin) 3.9f + 0.4f * r(13) else 6.4f + 0.8f * r(13)
    private val bowlClip = if (spin) "bowl_spin" else "bowl_fast"
    private val tA = if (spin) 1.1f else 1.7f
    private val cyc = if (spin) 0.7f else 1.2f
    private val cRel = ClipData.REL_T[bowlClip]!!
    private val cLand = ClipData.LAND_T[bowlClip]!!
    private val ftDur = if (spin) 1.0f else 2.2f
    private var tStop = 0f
    private var bowlerAfter: (() -> Unit)? = null
    private var resampleBowler: (() -> Unit)? = null
    private fun stopBowler(t: Float) { if (t < tStop) { tStop = t; resampleBowler?.invoke() } }

    private fun sFwd(tt: Float): Float {
        val t = min(tt, tStop)
        val t1 = tA + cyc; val t2 = tA + cRel; val d = t2 - t1
        if (t <= t1) return vRun * t
        val s1 = vRun * t1
        if (t <= t2) { val u = t - t1; return s1 + vRun * u - 0.6f * vRun * u * u / (2 * d) }
        val s2 = s1 + vRun * d - 0.3f * vRun * d
        val w = min(t - t2, ftDur); val vf = 0.4f * vRun
        return s2 + vf * w - vf * w * w / (2 * ftDur)
    }

    init {
        val bat = ev.bat
        effKind = if (ev.kind == "NB") when (bat) { 4 -> "FOUR"; 6 -> "SIX"; 0 -> "DOT"; else -> "RUN" } else ev.kind
        val how = ev.how
        wicket = if (ev.kind == "WICKET") when {
            how.startsWith("lbw") -> "lbw"; how.startsWith("b ") -> "bowled"; how.startsWith("st ") -> "stumped"
            how.contains("run out") -> "runout"; else -> "caught"
        } else ""
        val wide = ev.kind == "WIDE"
        val appeal = ev.kind == "APPEAL"
        standUp = spin || wicket == "stumped"
        kx = offSign * (if (standUp) 0.30f else 0.45f); kz = if (standUp) -0.75f else -11.5f
        val field = if (spin) FIELD_SPIN else FIELD_PACE
        for (i in 0 until 9) polar(field[i].first, field[i].second, fpos[i])

        // ---------------------------------------------------------- base tracks
        tr[STR] = Track(sx, sz0, sYaw, pc.strikerLeft).also { it.key(0f, "bat_stance", true) }
        tr[NON] = Track(nsX, nsZ0, 180f, false).also { it.key(0f, "idle", true) }
        tr[KPR] = Track(kx, kz, 0f, false).also { it.key(0f, "keep_crouch", true) }
        tr[UMB] = Track(armSign * 0.75f, 22.6f, 180f, false).also { it.key(0f, "ump_idle", true) }
        tr[UML] = Track(legSign * 24f, 1.0f, if (legSign > 0) -90f else 90f, false).also { it.key(0f, "ump_idle", true) }
        tRel = tA + cRel
        for (i in 0 until 9) {
            val fx = fpos[i][0]; val fz = fpos[i][1]; val d = hypot(fx, fz - 1f)
            var out = if (d > 10f) 2.4f else 0.6f
            while (out > 0f && Ground.rope(fx + (fx / d) * out, fz + ((fz - 1f) / d) * out) > 0.95f) out -= 0.2f
            out = max(out, 0f)
            val sx0 = fx + (fx / d) * out; val sz00 = fz + ((fz - 1f) / d) * out
            val faceBat = yawTo(-fx, 1f - fz)
            tr[F0 + i] = Track(sx0, sz00, faceBat, false).also {
                it.key(0f, "walk", true, faceBat, 0.6f); it.moveTo(0.2f, tRel - 0.2f, fx, fz, 2); it.key(tRel - 0.2f, "field_ready", true, faceBat)
            }
        }
        // bowler: run-up, delivery stride, follow-through drifting off the pitch (sampled into short path segments)
        tStop = tRel + ftDur
        val footLand = ClipData.FOOT_LAND[bowlClip]!![2]
        val frontFoot = Ground.FAR + Ground.POP + (if (ev.kind == "NB") -0.14f else 0.04f)     // a no-ball oversteps the line
        val aLand = frontFoot + footLand
        val tLand = tA + cLand
        fun bowlerZ(t: Float) = aLand + sFwd(tLand) - sFwd(t)
        fun bowlerX(t: Float) = if (t <= tRel) bowlX else bowlX + bowlSide * (if (spin) 1.4f else 3.0f) * smooth((min(t, tStop) - tRel) / ftDur)
        val bw = Track(bowlX, bowlerZ(0f), 180f, pc.bowlerLeftArm)
        tr[BWL] = bw
        bw.key(0f, "run", true, 180f, if (spin) vRun / 4.6f else vRun / 6.8f)
        bw.key(tA, bowlClip, false, Float.NaN, 1f, 0.12f)
        resampleBowler = { bw.paths.clear(); var t = 0f; while (t < tStop - 1e-4f) { val t1 = min(tStop, t + 0.05f); bw.moveTo(t, t1, bowlerX(t1), bowlerZ(t1)); t = t1 } }
        resampleBowler!!.invoke()
        val clipEnd = tA + (ClipData.DUR[bowlClip] ?: 2.3f)

        // ---------------------------------------------------------- ball in the bowler's hand until release
        add(Held(0f, BWL))
        val rel = FloatArray(3); handWorld(BWL, tRel, rel, bowlClip, cRel)
        val vB = if (spin) 21f + 3f * r(1) else 36f + 5f * r(1)
        bowledSpeedKph = (vB * 3.6f).roundToInt()

        // ---------------------------------------------------------- what the batter does / where the ball is aimed
        var catcherIdx = PlanCast.NONE
        var catchPos: FloatArray? = null
        var dirAngle = ev.angle
        if (wicket == "caught") {
            catcherIdx = when {
                pc.catcher == PlanCast.KEEPER || pc.catcher == PlanCast.BOWLER -> pc.catcher
                pc.catcher in 0..8 -> pc.catcher
                else -> {   // nearest fielder to where the engine sent the ball
                    val a = Math.toRadians(ev.angle.toDouble()); val dd = ev.dist.coerceIn(0.2f, 0.95f) * 60f
                    val tx = (offSign * sin(a) * dd).toFloat(); val tz = (cos(a) * dd).toFloat() + 1f
                    (0 until 9).minBy { hypot(fpos[it][0] - tx, fpos[it][1] - tz) }
                }
            }
            val cp = when (catcherIdx) { PlanCast.KEEPER -> floatArrayOf(kx, kz); PlanCast.BOWLER -> floatArrayOf(bowlerX(tRel + 1.0f), bowlerZ(tRel + 1.0f)); else -> fpos[catcherIdx].copyOf() }
            catchPos = cp
            val ang = Math.toDegrees(atan2((cp[0] / offSign).toDouble(), (cp[1] - 1.2f).toDouble())).toFloat()
            dirAngle = (ang + 360f) % 360f
        }
        val behind = dirAngle in 110f..250f
        shot = when {
            wide -> "bat_leave"
            wicket == "bowled" || wicket == "lbw" || wicket == "stumped" || appeal -> "bat_miss"
            wicket == "caught" && (catcherIdx == PlanCast.KEEPER || behind) -> "bat_defend"   // the edge
            wicket == "caught" -> shotFor(dirAngle, true)
            wicket == "runout" -> shotFor(dirAngle, false)
            effKind == "DOT" -> if (r(7) < 0.3f) "bat_leave" else "bat_defend"
            effKind == "SIX" -> shotFor(dirAngle, true)
            else -> shotFor(dirAngle, false)
        }
        hitBall = shot != "bat_leave" && shot != "bat_miss"
        val impactClip = ClipData.IMPACT[shot] ?: 0.55f
        // a stumping: the batter dances down the track and is beaten
        val charge = wicket == "stumped"
        val chargeZ = 2.35f
        val target = FloatArray(3)
        var through = false        // ball carries on to the keeper
        var padHit = false; var stumpsHit = false
        when {
            hitBall -> { sweet(shot, sz0, target) }
            wicket == "bowled" -> { target[0] = (r(3) - 0.5f) * 0.12f; target[1] = 0.22f + 0.32f * r(4); target[2] = 0.05f; stumpsHit = true }
            wicket == "lbw" || appeal -> { toWorld(sx, sz0, sYaw, pc.strikerLeft, ClipData.PAD, target); padHit = true }
            charge -> { target[0] = legSign * 0.06f; target[1] = 0.42f; target[2] = chargeZ + 0.55f; through = true }
            wide -> { target[0] = offSign * (1.05f + 0.3f * r(5)); target[1] = 0.75f; target[2] = 1.25f; through = true }
            else -> { target[0] = offSign * (0.38f + 0.25f * r(5)); target[1] = 0.8f + 0.25f * r(6); target[2] = 1.25f; through = true }   // leave
        }

        // ---------------------------------------------------------- the delivery itself (ballistic, bounce, seam/spin off the pitch)
        val e = if (spin) 0.46f else 0.53f; val keepH = if (spin) 0.80f else 0.87f
        val dev = if (spin) (if (r(15) > 0.3f) offSign else legSign) * (0.10f + 0.18f * r(16)) else (r(15) - 0.5f) * 0.18f
        val hx = target[0] - rel[0]; val hz = target[2] - rel[2]; val dh = hypot(hx, hz); val ux = hx / dh; val uz = hz / dh
        var bestDb = -1f; var bestErr = 1e9f
        run {
            var db = 0.3f
            while (db < dh - 2.0f) {
                val d1 = dh - db; val t1 = d1 / vB; val vy1 = (0.036f - rel[1] + 0.5f * G * t1 * t1) / t1; val vyb = vy1 - G * t1
                val t2 = db / (vB * keepH); val y = 0.036f - e * vyb * t2 - 0.5f * G * t2 * t2
                val err = abs(y - target[1])
                if (err < bestErr) { bestErr = err; bestDb = db }
                if (err < 0.025f) break
                db += 0.04f
            }
        }
        val tArrive: Float
        val vEnd = FloatArray(3)
        if (bestErr <= 0.08f) {
            val bp = floatArrayOf(rel[0] + ux * (dh - bestDb) + dev, 0.036f, rel[2] + uz * (dh - bestDb))
            val t1 = hypot(bp[0] - rel[0], bp[2] - rel[2]) / vB
            val pre = lob(tRel, rel, tRel + t1, bp); add(pre)
            val vyb = pre.v[1] - G * t1
            val vx2 = vB * keepH
            val t2 = hypot(target[0] - bp[0], target[2] - bp[2]) / vx2
            // restitution fine-tuned (by < 0.08 m worth) so the ball meets the aim point exactly
            val vyPost = (target[1] - 0.036f + 0.5f * G * t2 * t2) / t2
            val post = Ballistic(tRel + t1, bp, floatArrayOf((target[0] - bp[0]) / t2, vyPost, (target[2] - bp[2]) / t2)); add(post)
            tArrive = tRel + t1 + t2
            post.at(tArrive, target)          // exact arrival point (within 2.5 cm of the aim)
            vEnd[0] = post.v[0]; vEnd[1] = post.v[1] - G * t2; vEnd[2] = post.v[2]
        } else {                               // full toss
            val tt = dh / vB
            val ft = lob(tRel, rel, tRel + tt, target); add(ft)
            tArrive = tRel + tt; vEnd[0] = ft.v[0]; vEnd[1] = ft.v[1] - G * tt; vEnd[2] = ft.v[2]
        }
        tImp = tArrive
        target.copyInto(contact)
        val shotStart = tArrive - impactClip - (if (stumpsHit) 0.04f else 0f)
        val st = T(STR)
        if (charge) {
            st.key(tArrive - 1.25f, "walk", true, 0f, 1.4f, 0.15f); st.moveTo(tArrive - 1.25f, tArrive - impactClip - 0.05f, sx, chargeZ, 2)
            st.key(shotStart, shot, false, sYaw, 1f, 0.1f)
        } else st.key(shotStart, shot, false, sYaw, 1f, 0.1f)
        // non-striker backs up as the bowler lets go
        T(NON).key(tRel - 1.0f, "walk", true, 180f, 0.9f); T(NON).moveTo(tRel - 1.0f, tRel + 0.3f, nsX, nsZ1, 2); T(NON).key(tRel + 0.3f, "idle", true, 180f)
        tOut = tImp + 0.8f
        var end = tImp + 2.2f

        // ---------------------------------------------------------- helpers
        val kt = T(KPR)
        fun keeperTake(t0: Float, p: FloatArray, v: FloatArray): Float {
            val hand = FloatArray(3); ClipData.hand("keep_collect", 0.45f, hand)
            val zPlane = kz + hand[2]
            val tk = ((p[2] - zPlane) / max(1f, -v[2])).coerceIn(0.05f, 1.2f)
            val bx = p[0] + v[0] * tk
            val nx = (bx - hand[0]).coerceIn(kx - 1.6f, kx + 1.6f)
            val tArr = t0 + tk
            kt.moveTo(tRel + 0.1f, max(tRel + 0.2f, tArr - 0.25f), nx, kz, 2)
            kt.key(tArr - 0.45f, "keep_collect", false, 0f, 1f, 0.12f)
            val g = FloatArray(3); handWorld(KPR, tArr, g, "keep_collect", 0.45f)
            add(lob(t0, p, tArr, g)); add(Held(tArr, KPR))
            return tArr
        }
        fun celebrate(t: Float, exceptHolder: Int = -1) {
            stopBowler(t + 0.15f); T(BWL).key(t + 0.15f, "celebrate", false, Float.NaN)
            for (i in 0 until 9) if (F0 + i != exceptHolder) T(F0 + i).key(t + 0.3f + 0.06f * i, "celebrate", false)
            if (exceptHolder != KPR) kt.key(t + 0.35f, "celebrate", false)
        }
        fun walkOff(who: Int, t: Float) {
            val tk = T(who); val p = FloatArray(2); tk.endPos(p)
            val tt = max(t, tk.endT)
            tk.key(tt, "walk", true, Float.NaN, 1f, 0.3f); tk.walkOffFrom = tt
            tk.moveTo(tt, tt + 9f, p[0] * 0.6f + legSign * 3f, p[1] - 11f, 0)
        }
        fun umpire(sig: String, t: Float) { umpSignal = sig; T(UMB).key(t, sig, false, 180f, 1f, 0.25f) }

        // ---------------------------------------------------------- outcome
        when {
            stumpsHit -> {            // BOWLED
                breakBat = tImp; tOut = tImp
                add(Ballistic(tImp, target.copyOf(), floatArrayOf(vEnd[0] * 0.15f, 1.6f, vEnd[2] * 0.12f)))
                val tb = tImp + 2f * 1.6f / G; val pb = FloatArray(3); (segs.last() as Ballistic).at(tb, pb)
                add(Roll(tb, pb[0], pb[2], 0f, -1f, 3f, 4f, 3f, 0.03f))
                celebrate(tOut); walkOff(STR, tOut + 1.6f); end = tOut + 3.4f
            }
            padHit -> {               // LBW (out) or a turned-down appeal
                val vx = legSign * (0.6f + 0.6f * r(8)) + vEnd[0] * 0.1f
                val b1 = Ballistic(tImp, target.copyOf(), floatArrayOf(vx, 1.1f, 1.4f + 0.8f * r(9))); add(b1)
                val tg = tImp + (1.1f + sqrt(1.1f * 1.1f + 2 * G * (target[1] - 0.04f))) / G
                val pg = FloatArray(3); b1.at(tg, pg)
                val l = hypot(vx, b1.v[2]); add(Roll(tg, pg[0], pg[2], vx / l, b1.v[2] / l, 2.2f, 3f, 3f, 0.04f))
                val tAp = tImp + 0.12f
                stopBowler(tAp); T(BWL).key(tAp, "appeal", false, 180f, 1f, 0.15f)
                kt.key(tImp + 0.2f, "appeal", false, 0f)
                for (i in 0 until 9) if (hypot(fpos[i][0], fpos[i][1]) < 22f) T(F0 + i).key(tImp + 0.25f + 0.05f * i, "appeal", false)
                if (wicket == "lbw") { umpire("ump_out", tImp + 1.5f); tOut = tImp + 2.0f; celebrate(tOut); walkOff(STR, tOut + 1.2f); end = tOut + 3.0f }
                else { tOut = tImp + 1.5f; end = tOut + 1.3f }
            }
            through -> {              // leave / wide / stumping: the keeper takes it
                val tArr = keeperTake(tImp, target, vEnd)
                if (charge) {
                    st.key(tImp + 0.35f, "walk", true, sYaw, 1.1f, 0.2f); st.moveTo(tImp + 0.35f, tImp + 1.4f, sx, 1.75f, 2)
                    kt.key(tArr + 0.12f, "keep_stump", false, 0f, 1f, 0.1f)
                    breakBat = tArr + 0.42f; tOut = breakBat
                    T(UML).key(tOut + 0.5f, "ump_out", false)
                    celebrate(tOut, KPR); walkOff(STR, tOut + 1.6f); end = tOut + 3.2f
                } else {
                    tOut = tArr
                    if (wide) umpire("ump_wide", tArr + 0.35f)
                    end = tArr + (if (wide) 2.6f else 1.5f)
                }
            }
            wicket == "caught" -> {
                val c = target                      // contact = sweet spot
                val cp = catchPos!!
                val k = FloatArray(3); var tCatch: Float
                when (catcherIdx) {
                    PlanCast.KEEPER -> {
                        val hand = FloatArray(3); ClipData.hand("keep_collect", 0.45f, hand)
                        var nx = (c[0] + (kx - c[0]) * 0.6f - hand[0]).coerceIn(kx - 1.2f, kx + 1.2f)
                        tCatch = tImp + hypot(nx - c[0], kz - c[2]) / (if (spin) 16f else 26f)
                        if (tCatch - 0.2f > tRel + 0.3f) kt.moveTo(tRel + 0.1f, tCatch - 0.2f, nx, kz, 2) else nx = kx
                        kt.key(tCatch - 0.45f, "keep_collect", false, 0f, 1f, 0.1f)
                        handWorld(KPR, tCatch, k, "keep_collect", 0.45f)
                        add(lob(tImp, c, tCatch, k)); add(Held(tCatch, KPR))
                        tOut = tCatch; celebrate(tOut, KPR)
                    }
                    PlanCast.BOWLER -> {
                        tCatch = tImp + 0.75f
                        repeat(3) { val tc = tCatch - 0.4f; val bx = bowlerX(tc); val bz = bowlerZ(tc); tCatch = tImp + max(0.45f, hypot(bx - c[0], bz - c[2]) / 19f) }
                        stopBowler(tCatch - 0.4f)
                        T(BWL).key(tCatch - 0.4f, "field_catch", false, 180f, 1f, 0.12f)
                        handWorld(BWL, tCatch, k, "field_catch", 0.40f)
                        add(lob(tImp, c, tCatch, k)); add(Held(tCatch, BWL))
                        tOut = tCatch
                        for (i in 0 until 9) T(F0 + i).key(tOut + 0.3f + 0.06f * i, "celebrate", false)
                        kt.key(tOut + 0.35f, "celebrate", false); T(BWL).key(tOut + 0.9f, "celebrate", false)
                    }
                    else -> {
                        val ft = T(F0 + catcherIdx)
                        val d = hypot(cp[0] - c[0], cp[1] - c[2])
                        val skied = d > 22f && !behind
                        val hap = if (skied) 13f + 13f * r(10) else max(c[1], 1.6f) + 0.6f + 1.4f * r(10)
                        // a skied catch: the fielder settles under it a few metres from his spot
                        val mv = if (skied) 1.5f + 2.5f * r(11) else 0f
                        val mx = cp[0] + (c[0] - cp[0]) / d * mv * 0.6f + (r(12) - 0.5f) * mv
                        val mz = cp[1] + (c[2] - cp[1]) / d * mv * 0.6f
                        val face = yawTo(c[0] - mx, c[2] - mz)
                        val hand = FloatArray(3); ClipData.hand("field_catch", 0.40f, hand)
                        toWorld(mx, mz, face, false, hand, k)
                        val vy0 = sqrt(2 * G * max(0.2f, hap - c[1])); val tUp = vy0 / G; val tDown = sqrt(2 * max(0.05f, hap - k[1]) / G)
                        val dist = hypot(k[0] - c[0], k[2] - c[2])
                        tCatch = tImp + if (skied) max(tUp + tDown, dist / 32f) else dist / (if (behind) 30f else 24f)
                        if (mv > 0f) { ft.key(tImp + REACT, "run", true, Float.NaN, 0.8f); ft.moveTo(tImp + REACT, tImp + REACT + mv / 4.5f, mx, mz, 2); ft.key(tImp + REACT + mv / 4.5f, "field_ready", true, face) }
                        ft.key(tCatch - 0.40f, "field_catch", false, face, 1f, 0.12f)
                        handWorld(F0 + catcherIdx, tCatch, k, "field_catch", 0.40f)
                        add(lob(tImp, c, tCatch, k)); add(Held(tCatch, F0 + catcherIdx))
                        tOut = tCatch; celebrate(tOut, F0 + catcherIdx); ft.key(tOut + 0.9f, "celebrate", false)
                    }
                }
                umpire("ump_out", tOut + 0.6f)
                walkOff(STR, tOut + 1.5f); end = tOut + 3.2f
            }
            effKind == "SIX" -> {
                val a = Math.toRadians(dirAngle.toDouble()); val dx = (offSign * sin(a)).toFloat(); val dz = cos(a).toFloat()
                val bd = Ground.boundaryDist(target[0], target[2], dx, dz)
                val ld = bd + 9f + 14f * r(10)
                val land = floatArrayOf(target[0] + dx * ld, Ground.standHeight(ld - bd) + 0.4f, target[2] + dz * ld)
                val hap = 19f + 10f * r(11)
                val vy0 = sqrt(2 * G * (hap - target[1])); val tt = vy0 / G + sqrt(2 * max(0.5f, hap - land[1]) / G)
                add(lob(tImp, target, tImp + tt, land)); add(Fixed(tImp + tt, land))
                tOut = tImp + tt * (bd / ld)
                // the nearest boundary rider runs round and watches it sail over
                val ropeX = target[0] + dx * (bd - 1.6f); val ropeZ = target[2] + dz * (bd - 1.6f)
                val wi = (0 until 9).minBy { hypot(fpos[it][0] - ropeX, fpos[it][1] - ropeZ) }
                val dd = hypot(fpos[wi][0] - ropeX, fpos[wi][1] - ropeZ)
                if (dd > 1f && dd < 45f) {
                    val ft = T(F0 + wi); val ta = tImp + REACT + dd / V_FIELD
                    ft.key(tImp + REACT, "run", true, Float.NaN); ft.moveTo(tImp + REACT, ta, ropeX, ropeZ, 1)
                    ft.key(ta, "idle", true, yawTo(-dx, -dz) + 180f)
                }
                umpire("ump_six", tOut + 0.5f)
                if (ev.milestone) st.key(tOut + 0.4f, "bat_celebrate", false, sYaw)
                end = max(tImp + tt + 1.2f, tOut + 2.6f)
            }
            else -> planGround(target, dirAngle, ::celebrate, ::walkOff, ::umpire).let { (o, en) -> tOut = o; end = en }
        }
        if (!hitBall && !through && wicket == "") end = max(end, tOut + 1f)
        // keeper stands up once the ball has been hit somewhere else
        if (hitBall && wicket != "caught" && kt.keys.none { it.t > tImp }) kt.key(tImp + 0.6f, "idle", true, 0f, 1f, 0.3f)
        if (T(BWL).keys.none { it.t > tA + 0.01f && it.clip != bowlClip }) {
            if (tStop > clipEnd) { T(BWL).key(clipEnd, "walk", true, Float.NaN, 0.9f, 0.3f); T(BWL).key(tStop, "idle", true, Float.NaN, 1f, 0.3f) }
            else T(BWL).key(clipEnd, "idle", true, Float.NaN, 1f, 0.3f)
        }
        bowlerAfter?.invoke()
        segs.sortBy { it.t0 }
        tEnd = end.coerceAtMost(tImp + 26f)
    }
    /** bat sweet spot in world space for [shot] with the striker at depth [z] */
    private fun sweet(shot: String, z: Float, o: FloatArray) = toWorld(sx, z, sYaw, pc.strikerLeft, ClipData.SWEET[shot]!!, o)

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

    /** earliest time (after contact) fielder slot [i] can get to the rolling ball, or +inf */
    private fun reach(i: Int, roll: Roll, horizon: Float, o: FloatArray? = null): Float {
        val fx = fpos[i][0]; val fz = fpos[i][1]; val p = FloatArray(3)
        var u = REACT
        while (u < horizon) {
            roll.at(roll.t0 + u, p)
            if (Ground.rope(p[0], p[2]) >= 0.985f) return Float.POSITIVE_INFINITY
            if (hypot(p[0] - fx, p[2] - fz) <= 0.9f + V_FIELD * (u - REACT)) { o?.let { it[0] = p[0]; it[1] = p[2] }; return u }
            u += 0.04f
        }
        return Float.POSITIVE_INFINITY
    }

    /** ground shots: dot pushes, singles to threes, all-run, fours along the turf and run-outs */
    private fun planGround(c: FloatArray, engineAngle: Float, celebrate: (Float, Int) -> Unit, walkOff: (Int, Float) -> Unit, umpire: (String, Float) -> Unit): Pair<Float, Float> {
        val kt = T(KPR)
        fun dir(a: Float): FloatArray { val ar = Math.toRadians(a.toDouble()); return floatArrayOf((offSign * sin(ar)).toFloat(), cos(ar).toFloat()) }
        // dead-bat push
        if (effKind == "DOT" || (effKind != "FOUR" && effKind != "RUN" && wicket != "runout")) {
            val a = (r(9) - 0.5f) * 70f; val d = dir(a); val sMax = 1.5f + 4f * r(10)
            add(Roll(tImp, c[0], c[2], d[0], d[1], sqrt(2 * 3f * sMax), 3f, sMax, 0.05f, c[1], -min(2.5f, c[1] * 2f)))
            return (tImp + 0.8f) to (tImp + 2.4f)
        }
        val runs = if (wicket == "runout") 1 else ev.bat.coerceIn(0, 3)
        if (effKind == "FOUR") {
            // choose the gap nearest the engine's direction that nobody can cut off
            var best = engineAngle; var bestScore = -1e9f
            for (k in 0..14) {
                val dA = if (k == 0) 0f else ((k + 1) / 2) * 5f * (if (k % 2 == 0) 1f else -1f)
                val a = engineAngle + dA; val d = dir(a); val bd = Ground.boundaryDist(c[0], c[2], d[0], d[1])
                val rl = Roll(tImp, c[0], c[2], d[0], d[1], sqrt(2 * ROLL_K * (bd + 12f)), ROLL_K, bd + 3.4f, 0.12f, c[1], -min(2.5f, c[1] * 2f))
                val margin = (0 until 9).minOf { reach(it, rl, 8f).let { u -> if (u.isInfinite()) 9f else u - 9f } }
                val score = (if (margin >= 9f) 100f else margin) - abs(dA) * 0.02f
                if (score > bestScore) { bestScore = score; best = a }
            }
            val d = dir(best); val bd = Ground.boundaryDist(c[0], c[2], d[0], d[1])
            val oneBounce = r(12) > 0.65f
            val roll: Roll
            if (oneBounce) {
                val l1 = bd * (0.55f + 0.2f * r(13)); val hap = 3.5f + 4f * r(14)
                val p1 = floatArrayOf(c[0] + d[0] * l1, 0.04f, c[2] + d[1] * l1)
                val vy0 = sqrt(2 * G * max(0.3f, hap - c[1])); val tt = vy0 / G + sqrt(2 * hap / G)
                add(lob(tImp, c, tImp + tt, p1))
                val rem = bd + 3.4f - l1
                roll = Roll(tImp + tt, p1[0], p1[2], d[0], d[1], sqrt(2 * ROLL_K * (rem + 8f)), ROLL_K, rem, 0.25f)
            } else roll = Roll(tImp, c[0], c[2], d[0], d[1], sqrt(2 * ROLL_K * (bd + 12f)), ROLL_K, bd + 3.4f, 0.12f, c[1], -min(2.5f, c[1] * 2f))
            add(roll)
            // time it crosses the rope
            var uc = 0f; val p = FloatArray(3)
            while (uc < 12f) { roll.at(roll.t0 + uc, p); if (Ground.rope(p[0], p[2]) >= 1f) break; uc += 0.02f }
            val tCross = roll.t0 + uc
            // the nearest boundary fielder chases it to the rope (dives if he gets close but can't stop it)
            val ropeX = c[0] + d[0] * (bd - 1.4f); val ropeZ = c[2] + d[1] * (bd - 1.4f)
            val ci = (0 until 9).minBy { hypot(fpos[it][0] - ropeX, fpos[it][1] - ropeZ) }
            val ft = T(F0 + ci); val dd = hypot(fpos[ci][0] - ropeX, fpos[ci][1] - ropeZ)
            val tGo = tImp + REACT; val tAt = tGo + dd / V_FIELD
            if (dd > 1f) {
                ft.key(tGo, "run", true, Float.NaN)
                if (tAt < tCross + 0.25f) {
                    // he would get there in time: a full-length dive that just fails, ball passing 1.6 m beyond his hands
                    val side = if ((ropeX - fpos[ci][0]) * d[1] - (ropeZ - fpos[ci][1]) * d[0] > 0f) 1f else -1f
                    val px = ropeX - d[1] * side * 1.9f; val pz = ropeZ + d[0] * side * 1.9f
                    val tDive = tCross - 0.45f
                    val tArr = max(tGo + hypot(fpos[ci][0] - px, fpos[ci][1] - pz) / V_FIELD, tGo + 0.2f).coerceAtMost(tDive)
                    ft.moveTo(tGo, tArr, px, pz, 1)
                    val face = yawTo(-d[0], -d[1]) + 180f            // run along the ball's line, diving across it
                    val diveYaw = face; val mirror = side < 0f
                    if (tArr < tDive - 0.1f) ft.key(tArr, "field_ready", true, diveYaw, 1f, 0.15f)
                    ft.key(tDive, "field_dive", false, diveYaw, 1f, 0.1f)
                    if (mirror) ft.mirrorClips.add("field_dive")
                } else { ft.moveTo(tGo, tAt, ropeX, ropeZ, 1); ft.key(tAt, "idle", true, Float.NaN, 1f, 0.3f) }
            }
            umpire("ump_four", tCross + 0.45f)
            if (ev.milestone) T(STR).key(tCross + 0.4f, "bat_celebrate", false, sYaw)
            return tCross to (tCross + 2.4f)
        }

        // -------- singles, twos, threes, run-outs
        val runOut = wicket == "runout"
        var ci = 0; var a = engineAngle
        if (runOut) {
            ci = if (pc.runOutFielder in 0..8) pc.runOutFielder else (0 until 9).filter { hypot(fpos[it][0] - c[0], fpos[it][1] - c[2]) in 9f..32f }.minByOrNull { hypot(fpos[it][0] - c[0], fpos[it][1] - c[2]) } ?: 2
            a = Math.toDegrees(atan2(((fpos[ci][0] - c[0]) / offSign).toDouble(), (fpos[ci][1] - c[2]).toDouble())).toFloat()
        }
        var roll: Roll? = null; var tPick = 0f; val pick = FloatArray(2)
        fun mkRoll(ang: Float, ed: Float): Roll { val d = dir(ang); val bd = Ground.boundaryDist(c[0], c[2], d[0], d[1]); val e = min(ed, bd - 4f); return Roll(tImp, c[0], c[2], d[0], d[1], sqrt(2 * ROLL_K * e), ROLL_K, e, 0.1f, c[1], -min(2.5f, c[1] * 2f)) }
        if (runOut) {
            val dF = hypot(fpos[ci][0] - c[0], fpos[ci][1] - c[2])
            roll = mkRoll(a, dF + 1.5f)
            tPick = reach(ci, roll, 10f, pick).let { if (it.isInfinite()) { roll!!.at(tImp + 10f, v3); pick[0] = v3[0]; pick[1] = v3[2]; roll!!.tStop + 0.5f } else it }
        } else {
            val endFrac = when (runs) { 1 -> 0.30f + 0.25f * r(9); 2 -> 0.62f + 0.18f * r(9); else -> 0.80f + 0.1f * r(9) }
            var bestA = a; var bestT = -1f; var bestCi = 0
            val span = if (runs >= 2) 7 else 0
            for (k in 0..span * 2) {
                val dA = if (k == 0) 0f else ((k + 1) / 2) * 5f * (if (k % 2 == 0) 1f else -1f)
                val aa = a + dA; val d = dir(aa); val bd = Ground.boundaryDist(c[0], c[2], d[0], d[1])
                val rl = mkRoll(aa, endFrac * bd)
                var tMin = Float.POSITIVE_INFINITY; var who = 0
                for (i in 0 until 9) { val u = reach(i, rl, rl.tStop + 6f); if (u < tMin) { tMin = u; who = i } }
                val score = tMin - abs(dA) * 0.01f
                if (score > bestT) { bestT = score; bestA = aa; bestCi = who }
            }
            a = bestA; ci = bestCi
            val d = dir(a); val bd = Ground.boundaryDist(c[0], c[2], d[0], d[1])
            roll = mkRoll(a, endFrac * bd)
            tPick = reach(ci, roll, roll.tStop + 6f, pick)
            if (tPick.isInfinite()) { tPick = roll.tStop + 1f; roll.at(tImp + 30f, v3); pick[0] = v3[0]; pick[1] = v3[2] }
        }
        val rl = roll!!
        add(rl)
        // fielder runs to the ball
        val ft = T(F0 + ci)
        val tGo = tImp + REACT; val tArrF = tImp + tPick
        val dd = hypot(fpos[ci][0] - pick[0], fpos[ci][1] - pick[1])
        // stop just short of the ball, facing it
        val ax = pick[0] + (fpos[ci][0] - pick[0]) / max(dd, 1e-3f) * min(0.55f, dd); val az = pick[1] + (fpos[ci][1] - pick[1]) / max(dd, 1e-3f) * min(0.55f, dd)
        val faceBall = yawTo(pick[0] - fpos[ci][0], pick[1] - fpos[ci][1]).let { if (dd < 0.6f) yawTo(c[0] - ax, c[2] - az) else it }
        if (dd > 0.6f) { ft.key(tGo, "run", true, Float.NaN, (dd / max(0.3f, tArrF - tGo) / V_FIELD).coerceIn(0.5f, 1.1f)); ft.moveTo(tGo, tArrF, ax, az, if (tArrF - tGo > 1.2f) 1 else 0) }
        ft.key(tArrF, "field_ready", true, faceBall, 1f, 0.15f)
        // batters run
        val tRun0Base = tImp + 0.5f
        val runDur = 3.0f + 0.2f * r(14) + (if (runs == 3) 0.2f else 0f)
        val turn = 0.35f
        var tRun0 = tRun0Base; var roDur = runDur
        // throw
        val holdT = tArrF
        var gather = 0.3f
        val receiverIsKeeper: Boolean
        val roEndZ = if (pc.outIsStriker) Ground.FAR else 0f
        if (runOut) receiverIsKeeper = !pc.outIsStriker else receiverIsKeeper = pick[1] < Ground.CZ + 8f
        add(Held(holdT, F0 + ci))
        val tThrow = holdT + gather
        // keeper / bowler get to the stumps for the return
        val kStumpX = offSign * 0.55f; val kStumpZ = -0.45f
        if (!standUp) { kt.key(tImp + 0.4f, "run", true, Float.NaN, 0.85f); kt.moveTo(tImp + 0.4f, tImp + 0.4f + hypot(kStumpX - kx, kStumpZ - kz) / 5.5f, if (runOut && !pc.outIsStriker) offSign * 1.3f else kStumpX, kStumpZ, 1) }
        val kReady = max(tImp + 0.6f, kt.endT)
        val thrower = FloatArray(2).also { it[0] = ax; it[1] = az }
        kt.key(kReady, "field_ready", true, yawTo(thrower[0] - kStumpX, thrower[1] - kStumpZ), 1f, 0.25f)
        val bwT = T(BWL)
        // throw flight
        val relT = tThrow + 0.48f
        val faceStumps = yawTo((0f - ax), ((if (receiverIsKeeper) 0f else Ground.FAR) - az))
        ft.key(tThrow, "field_throw", false, faceStumps, 1f, 0.12f)
        val relP = FloatArray(3); handWorld(F0 + ci, relT, relP, "field_throw", 0.48f)
        var tArr: Float
        val catchP = FloatArray(3)
        if (runOut) {
            val tgt = floatArrayOf(0f, 0.42f, roEndZ + (if (roEndZ > 1f) 0.03f else 0.03f))
            tArr = relT + hypot(tgt[0] - relP[0], tgt[2] - relP[2]) / 22f
            add(lob(relT, relP, tArr, tgt))
            add(Roll(tArr, tgt[0], tgt[2], (tgt[0] - relP[0]) / hypot(tgt[0] - relP[0], tgt[2] - relP[2]), (tgt[2] - relP[2]) / hypot(tgt[0] - relP[0], tgt[2] - relP[2]), 6f, 3f, 6f, 0.2f))
            if (pc.outIsStriker) breakBowl = tArr else breakBat = tArr
            // batters: the out man is 0.9 m short when the stumps go down
            val frac = if (pc.outIsStriker) (Ground.FAR - Ground.POP - 0.9f - sz0) / (zBowlEnd - sz0) else (nsZ1 - (Ground.POP + 0.9f)) / (nsZ1 - zKeepEnd)
            val uStar = trapInv(frac)
            roDur = ((tArr - tRun0) / uStar).coerceIn(2.6f, 4.2f); tRun0 = tArr - uStar * roDur
            if (tRun0 < tImp + 0.3f) { tRun0 = tImp + 0.3f; roDur = ((tArr - tRun0) / uStar).coerceAtLeast(2.0f) }
        } else {
            // the receiver: keeper at the stumps, or the bowler back at his end
            val lastRunEnd = tRun0 + runs * runDur + (runs - 1) * turn
            var tArrW = relT + 0.7f
            if (receiverIsKeeper) {
                val hand = FloatArray(3); ClipData.hand("keep_collect", 0.45f, hand)
                val face = yawTo(relP[0] - kStumpX, relP[2] - kStumpZ)
                toWorld(kStumpX, kStumpZ, face, false, hand, catchP)
            } else {
                val bx = bowlSide * 0.7f; val bz = Ground.FAR + 0.6f
                val pB = FloatArray(2); bwT.endPos(pB)
                val tB0 = max(tStop + 0.3f, holdT - 0.5f); val tB1 = tB0 + hypot(bx - pB[0], bz - pB[1]) / 4.2f
                bowlerAfter = { bwT.key(tB0, "run", true, Float.NaN, 0.7f); bwT.moveTo(tB0, tB1, bx, bz, 2); bwT.key(tB1, "field_ready", true, yawTo(ax - bx, az - bz)) }
                val hand = FloatArray(3); ClipData.hand("field_catch", 0.40f, hand)
                toWorld(bx, bz, yawTo(relP[0] - bx, relP[2] - bz), false, hand, catchP)
                tArrW = max(tArrW, tB1 + 0.5f)
            }
            val dist = hypot(catchP[0] - relP[0], catchP[2] - relP[2])
            var vh = if (dist < 15f) 16f else 24f
            tArr = max(relT + dist / vh, tArrW)
            // don't beat the batters home (that would be a run-out): delay the gather / lob it
            val want = lastRunEnd - 0.4f
            if (tArr < want) {
                val extra = min(1.2f, want - tArr); gather += extra
                val tThrow2 = holdT + gather
                ft.keys.removeAll { it.clip == "field_throw" }; ft.key(tThrow2, "field_throw", false, faceStumps, 1f, 0.12f)
                val relT2 = tThrow2 + 0.48f; handWorld(F0 + ci, relT2, relP, "field_throw", 0.48f)
                vh = max(12f, dist / max(0.3f, want - relT2)).coerceAtMost(24f)
                tArr = max(relT2 + dist / vh, tArrW)
                segs.add(lob(relT2, relP.copyOf(), tArr, catchP))
            } else segs.add(lob(relT, relP.copyOf(), tArr, catchP))
            if (receiverIsKeeper) { kt.key(tArr - 0.45f, "keep_collect", false, yawTo(relP[0] - kStumpX, relP[2] - kStumpZ), 1f, 0.12f); add(Held(tArr, KPR)) }
            else { val prev = bowlerAfter; bowlerAfter = { prev?.invoke(); bwT.key(tArr - 0.40f, "field_catch", false, Float.NaN, 1f, 0.12f) }; add(Held(tArr, BWL)) }
        }
        // running between the wickets
        val sT = T(STR); val nT = T(NON)
        val nRuns = max(1, runs)
        for (k in 0 until nRuns) {
            val t0 = tRun0 + k * (roDur + turn); val t1 = t0 + roDur
            val toBowl = k % 2 == 0
            sT.key(t0, "bat_run", true, Float.NaN, 1f, 0.15f); nT.key(t0, "bat_run", true, Float.NaN, 1f, 0.15f)
            sT.moveTo(t0, t1, laneStr, if (toBowl) zBowlEnd else zKeepEnd, 1, if (k == 0) 0.3f else 0f)
            nT.moveTo(t0, t1, laneNon, if (toBowl) zKeepEnd else zBowlEnd, 1, if (k == 0) 0.3f else 0f)
            if (k < nRuns - 1) { sT.key(t1, "bat_run", true, Float.NaN, 0.25f, 0.1f); nT.key(t1, "bat_run", true, Float.NaN, 0.25f, 0.1f) }
        }
        val runsEnd = tRun0 + nRuns * roDur + (nRuns - 1) * turn
        if (runOut) {
            // freeze both where they are when the stumps are hit; the out batter walks off
            val tb = if (pc.outIsStriker) breakBowl else breakBat
            for (w in listOf(sT, nT)) {
                w.cutAt(tb)
                w.keys.removeAll { it.t > tb }
                w.key(tb + 0.01f, "idle", true, Float.NaN, 1f, 0.25f)
            }
            celebrate(tb, F0 + ci); walkOff(if (pc.outIsStriker) STR else NON, tb + 1.4f)
            return tb to (tb + 3.4f)
        }
        sT.key(runsEnd, "idle", true, Float.NaN, 1f, 0.3f); nT.key(runsEnd, "idle", true, Float.NaN, 1f, 0.3f)
        if (ev.milestone) sT.key(runsEnd + 0.2f, "bat_celebrate", false, Float.NaN)
        val tO = tImp + tPick
        return tO to max(runsEnd + 0.8f, tArr + 1.0f)
    }

    /** Resolve every pose and the ball at time t. */
    fun eval(t: Float) {
        for (i in 0 until N) T(i).eval(t, pose[i])
        separate(t)
        ballAt(t, ball)
        // fielders not involved turn to watch the ball once it's hit
        if (t > tImp + 0.15f && t < tOut + 0.4f) {
            val u = smooth((t - tImp - 0.15f) / 0.5f)
            for (i in F0 until N) {
                val p = pose[i]
                if (p.clip != "field_ready" && p.clip != "walk") continue
                if (T(i).keys.any { it.t > tImp && it.clip != "celebrate" && it.clip != "appeal" }) continue
                p.yaw = lerpAngle(p.yaw, yawTo(ball[0] - p.x, ball[2] - p.z), u)
            }
        }
    }

    /** how much an actor may be nudged aside: umpires and idle men give way, the ball's protagonists never do */
    private fun rank(i: Int, t: Float): Int = when {
        i == UMB || i == UML -> 0
        T(i).keys.none { it.t > tImp - 0.05f && it.t < t + 2f } && T(i).paths.none { it.t1 > tImp && it.t0 < t + 2f } -> 1
        else -> 2
    }
    private val rankCache = IntArray(N)
    /** keep bodies apart (0.6 m): the lower-ranked man steps aside, smoothly, as the other approaches */
    private fun separate(t: Float) {
        if (t < tRel) return
        for (i in 0 until N) rankCache[i] = -1
        for (pass in 0 until 2) for (i in 0 until N) for (j in i + 1 until N) {
            val a = pose[i]; val b = pose[j]
            if (a.walkingOff || b.walkingOff) continue
            var dx = b.x - a.x; var dz = b.z - a.z; val d = hypot(dx, dz)
            if (d >= SEP) continue
            if (rankCache[i] < 0) rankCache[i] = rank(i, t); if (rankCache[j] < 0) rankCache[j] = rank(j, t)
            if (d < 1e-3f) { dx = 1f; dz = 0f } else { dx /= d; dz /= d }
            val push = SEP - d
            val wa = when { rankCache[i] < rankCache[j] -> 1f; rankCache[i] > rankCache[j] -> 0f; else -> 0.5f }
            a.x -= dx * push * wa; a.z -= dz * push * wa; a.ax -= dx * push * wa; a.az -= dz * push * wa
            b.x += dx * push * (1 - wa); b.z += dz * push * (1 - wa); b.ax += dx * push * (1 - wa); b.az += dz * push * (1 - wa)
        }
    }

    /** debug / tests: names of every clip used */
    fun clipsUsed(): Set<String> = tr.flatMap { it!!.keys.map { k -> k.clip } }.toSet()
}
