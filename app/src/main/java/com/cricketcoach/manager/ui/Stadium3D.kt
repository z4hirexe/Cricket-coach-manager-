package com.cricketcoach.manager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.BallEv
import kotlin.math.*

/* =====================================================================================
 *  A small software 3D engine (perspective camera + near-plane clipping + painter's sort)
 *  rendered with Compose Canvas.  World units are metres:
 *  striker's stumps at z = 0, bowler's stumps at z = 20.12, ground centre (0, 0, 10.06).
 * ===================================================================================== */

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(dot(this))
    fun norm(): V3 { val l = len(); return if (l < 1e-6f) this else this * (1f / l) }
}

fun lerp3(a: V3, b: V3, t: Float) = V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeInOut(t: Float) = t * t * (3f - 2f * t)

class Cam3(val eye: V3, target: V3, fovDeg: Float, val w: Float, val h: Float) {
    private val fwd = (target - eye).norm()
    private val right = fwd.cross(V3(0f, 1f, 0f)).norm()
    private val up = right.cross(fwd)
    val f = h / 2f / tan(Math.toRadians(fovDeg / 2.0).toFloat())
    val near = 0.3f
    fun toCam(p: V3): V3 { val d = p - eye; return V3(d.dot(right), d.dot(up), d.dot(fwd)) }
    fun projC(c: V3) = Offset(w / 2f + f * c.x / c.z, h / 2f - f * c.y / c.z)
    fun project(p: V3): Offset? { val c = toCam(p); return if (c.z < near) null else projC(c) }
    fun depth(p: V3) = toCam(p).z
    fun pxPerM(p: V3): Float { val z = toCam(p).z; return if (z < near) 0f else f / z }

    /** Sutherland–Hodgman clip of a world polygon against the near plane, returned as a screen path. */
    fun polyPath(pts: List<V3>): Path? {
        val cs = pts.map { toCam(it) }
        val out = ArrayList<V3>(cs.size + 4)
        for (i in cs.indices) {
            val a = cs[i]; val b = cs[(i + 1) % cs.size]
            val ain = a.z >= near; val bin = b.z >= near
            if (ain) out.add(a)
            if (ain != bin) { val t = (near - a.z) / (b.z - a.z); out.add(lerp3(a, b, t)) }
        }
        if (out.size < 3) return null
        val path = Path()
        out.forEachIndexed { i, c -> val o = projC(c); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        path.close(); return path
    }
}

private val CENTER = V3(0f, 0f, 10.06f)
private const val BOUNDARY = 65f
private const val STRIKER_Z = 0.9f

/** Distance from the striker to the rope along a ground direction (deg; 0 = straight past the bowler). */
private fun ropeDir(angle: Float): V3 { val a = Math.toRadians(angle.toDouble()); return V3(sin(a).toFloat(), 0f, cos(a).toFloat()) }
private fun ropeDistance(angle: Float): Float {
    val s = V3(0f, 0f, STRIKER_Z) - CENTER; val d = ropeDir(angle)
    val b = 2 * s.dot(d); val c = s.dot(s) - BOUNDARY * BOUNDARY
    return (-b + sqrt(b * b - 4 * c)) / 2f
}

/** Standard field placings (x, z). +x = off side for a right-hander. */
private val FIELD = listOf(
    V3(3.2f, 0f, -4.5f),   // slip
    V3(23f, 0f, 3f),       // point
    V3(24f, 0f, 18f),      // cover
    V3(9f, 0f, 36f),       // mid-off
    V3(-9f, 0f, 36f),      // mid-on
    V3(-25f, 0f, 16f),     // midwicket
    V3(-24f, 0f, -1f),     // square leg
    V3(-26f, 0f, -42f),    // fine leg
    V3(36f, 0f, 58f)       // long-off (deep)
)
private val KEEPER = V3(0f, 0f, -3.0f)

private class Crowd(val pts: List<V3>, val col: List<Int>)
private val CROWD_COLS = listOf(Color(0xFFE53935), Color(0xFFFFEB3B), Color(0xFF42A5F5), Color(0xFFFFFFFF), Color(0xFFFF9800), Color(0xFF66BB6A), Color(0xFFAB47BC))

private fun buildCrowd(): Crowd {
    val r = java.util.Random(11); val pts = ArrayList<V3>(); val cols = ArrayList<Int>()
    for (i in 0 until 1500) {
        val a = r.nextFloat() * 2f * PI.toFloat(); val k = r.nextFloat()
        val rad = 72f + k * 24f; val y = 1.5f + k * 18f
        pts.add(V3(CENTER.x + rad * cos(a), y + 0.4f, CENTER.z + rad * sin(a))); cols.add(r.nextInt(CROWD_COLS.size))
    }
    return Crowd(pts, cols)
}

/** One frame of the scene: everything that moves. */
private class Scene(
    val ball: V3?, val ballShadow: Boolean, val bowler: V3, val bowlerRun: Float, val bowlerArms: Float,
    val striker: V3, val nonStriker: V3, val runPhase: Float, val fielders: List<V3>, val keeper: V3,
    val stumpsFall: Float, val stumpsEndZ: Float, val batSwing: Float
)

/** Builds the scene for event [ev] at progress [p] (0..1). */
private fun sceneFor(ev: BallEv?, p: Float): Scene {
    val bowlerStart = V3(0.7f, 0f, 36f); val crease = V3(0.7f, 0f, 21.0f)
    val fielders = FIELD.toMutableList()
    if (ev == null) return Scene(V3(0.95f, 1.1f, 36f), true, bowlerStart, 0f, 0f, V3(0.3f, 0f, STRIKER_Z), V3(-0.8f, 0f, 19.6f), 0f, fielders, KEEPER, 0f, 0f, 0f)
    val runUp = seg(p, 0f, 0.26f); val deliver = seg(p, 0.26f, 0.40f); val after = seg(p, 0.40f, 1f)
    // bowler
    val bowler = when {
        p < 0.26f -> lerp3(bowlerStart, crease, easeInOut(runUp))
        else -> lerp3(crease, V3(1.6f, 0f, 16.5f), easeOut(seg(p, 0.26f, 0.55f)))
    }
    val appeal = ev.kind == "APPEAL" || (ev.kind == "WICKET" && (ev.how.startsWith("lbw") || ev.how.startsWith("b ") || ev.how.startsWith("c ")))
    val arms = if (p >= 0.45f && appeal) 1f else 0f
    // delivery
    val release = V3(0.8f, 2.25f, 20.3f)
    val wide = ev.kind == "WIDE"
    val bounce = V3(if (wide) 1.4f else 0.15f, 0.03f, 5.5f)
    val atBat = if (wide) V3(1.75f, 0.75f, 0.6f) else V3(0.25f, 0.7f, STRIKER_Z + 0.2f)
    var ball: V3? = when {
        p < 0.26f -> V3(bowler.x + 0.25f, 1.1f + 0.5f * runUp, bowler.z)
        p < 0.40f -> if (deliver < 0.6f) lerp3(release, bounce, deliver / 0.6f) else lerp3(bounce, atBat, (deliver - 0.6f) / 0.4f)
        else -> null
    }
    var striker = V3(0.3f, 0f, STRIKER_Z); var non = V3(-0.8f, 0f, 19.6f)
    var runPhase = 0f; var stumpsFall = 0f; var stumpsEnd = 0f; var keeper = KEEPER
    val swing = if (p in 0.36f..0.5f) seg(p, 0.36f, 0.5f) else 0f
    if (p >= 0.40f) {
        val dir = ropeDir(ev.angle); val rope = ropeDistance(ev.angle)
        val start = atBat
        fun groundTo(dest: V3, reach: Float, hops: Int): V3 {
            val u = easeOut(seg(after, 0f, reach))
            val g = lerp3(start, dest, u)
            val hy = abs(sin(u * PI.toFloat() * hops)) * (1f - u) * 1.6f
            return V3(g.x, max(0.05f, hy + (1 - u) * (start.y - 0.05f) * 0.3f), g.z)
        }
        fun airTo(dest: V3, reach: Float, height: Float): V3 {
            val u = seg(after, 0f, reach)
            val g = lerp3(start, dest, u)
            return V3(g.x, start.y + (dest.y - start.y) * u + 4f * height * u * (1 - u), g.z)
        }
        fun nearestFielder(dest: V3): Int = fielders.indices.minByOrNull { (fielders[it] - dest).len() } ?: 0
        val runs = ev.bat.coerceAtLeast(0)
        when (ev.kind) {
            "SIX" -> {
                val dest = V3(0f, 0f, STRIKER_Z) + dir * (rope * ev.dist) + V3(0f, 3f + (ev.dist - 1f) * 30f, 0f)
                ball = airTo(dest, 0.55f, 24f)
                val fi = nearestFielder(V3(0f, 0f, STRIKER_Z) + dir * rope); fielders[fi] = lerp3(fielders[fi], V3(0f, 0f, STRIKER_Z) + dir * (rope - 2f), easeOut(seg(after, 0f, 0.5f)))
            }
            "FOUR" -> {
                val dest = V3(0f, 0f, STRIKER_Z) + dir * (rope + 1.5f)
                ball = groundTo(dest, 0.5f, 3)
                val fi = nearestFielder(dest); fielders[fi] = lerp3(fielders[fi], lerp3(fielders[fi], dest, 0.8f), easeOut(seg(after, 0f, 0.6f)))
            }
            "RUN", "NB" -> {
                val dest = V3(0f, 0f, STRIKER_Z) + dir * (rope * ev.dist.coerceAtMost(0.97f))
                val fi = nearestFielder(dest)
                val fStart = fielders[fi]
                fielders[fi] = lerp3(fStart, dest, easeOut(seg(after, 0f, 0.45f)))
                ball = if (after < 0.45f) groundTo(dest, 0.45f, 2)
                else { val u = seg(after, 0.5f, 0.75f); val thrown = lerp3(dest + V3(0f, 1f, 0f), V3(0f, 1f, if (runs % 2 == 1) 20.12f else 0f), u); V3(thrown.x, 1f + 6f * u * (1 - u), thrown.z) }
                if (runs > 0) {
                    runPhase = seg(after, 0.05f, 0.95f) * runs
                    val leg = runPhase.toInt().coerceAtMost(runs - 1); val frac = runPhase - leg
                    val toFar = leg % 2 == 0
                    val sz = if (toFar) lerpF(STRIKER_Z, 19.3f, easeInOut(frac)) else lerpF(19.3f, STRIKER_Z, easeInOut(frac))
                    striker = V3(0.5f, 0f, if (runPhase >= runs) (if (runs % 2 == 1) 19.3f else STRIKER_Z) else sz)
                    non = V3(-0.8f, 0f, 20.12f - striker.z)
                }
            }
            "WICKET" -> {
                when {
                    ev.how.startsWith("b ") -> { ball = lerp3(atBat, V3(0f, 0.4f, 0f), seg(after, 0f, 0.08f)); stumpsFall = seg(after, 0.05f, 0.5f); stumpsEnd = 0f }
                    ev.how.startsWith("lbw") -> { ball = lerp3(atBat, V3(0.4f, 0.05f, 2.2f), easeOut(seg(after, 0f, 0.4f))) }
                    ev.how.startsWith("st") -> {
                        ball = lerp3(atBat, keeper + V3(0f, 0.7f, 0.3f), seg(after, 0f, 0.12f))
                        keeper = lerp3(KEEPER, V3(0f, 0f, -0.6f), easeOut(seg(after, 0.1f, 0.3f))); stumpsFall = seg(after, 0.25f, 0.7f); stumpsEnd = 0f
                        striker = lerp3(striker, V3(0.3f, 0f, 2.6f), easeOut(seg(after, 0f, 0.3f)))
                    }
                    ev.how.startsWith("run out") -> {
                        val dest = V3(0f, 0f, STRIKER_Z) + dir * (rope * 0.4f); val fi = nearestFielder(dest)
                        fielders[fi] = lerp3(fielders[fi], dest, easeOut(seg(after, 0f, 0.35f)))
                        ball = if (after < 0.35f) groundTo(dest, 0.35f, 1) else lerp3(dest + V3(0f, 1f, 0f), V3(0f, 0.5f, 20.12f), seg(after, 0.38f, 0.55f))
                        stumpsFall = seg(after, 0.55f, 0.85f); stumpsEnd = 20.12f
                        striker = V3(0.5f, 0f, lerpF(STRIKER_Z, 18f, easeInOut(seg(after, 0.05f, 0.6f))))
                        non = V3(-0.8f, 0f, lerpF(19.6f, 3f, easeInOut(seg(after, 0.05f, 0.6f))))
                    }
                    ev.how.startsWith("c †") -> { ball = lerp3(atBat, keeper + V3(0.3f, 0.9f, 0.3f), seg(after, 0f, 0.12f)) }
                    ev.how.startsWith("c & b") -> { ball = airTo(V3(1.4f, 1.6f, 17f), 0.3f, 3f) }
                    else -> {
                        val dest = V3(0f, 0f, STRIKER_Z) + dir * (rope * ev.dist)
                        val fi = nearestFielder(dest)
                        fielders[fi] = lerp3(fielders[fi], dest, easeOut(seg(after, 0f, 0.5f)))
                        ball = airTo(dest + V3(0f, 1.6f, 0f), 0.5f, 6f + ev.dist * 16f)
                        if (after > 0.5f) ball = fielders[fi] + V3(0f, 1.6f, 0f)
                    }
                }
            }
            "WIDE" -> ball = lerp3(atBat, keeper + V3(1.2f, 0.7f, 0f), seg(after, 0f, 0.15f))
            else -> { // DOT / APPEAL — defended or to the keeper
                ball = if (ev.kind == "APPEAL") lerp3(atBat, V3(0.4f, 0.05f, 2.4f), easeOut(seg(after, 0f, 0.4f)))
                else if (ev.angle < 175f) lerp3(atBat, keeper + V3(0f, 0.7f, 0.4f), seg(after, 0f, 0.15f))
                else groundTo(V3(0f, 0f, STRIKER_Z) + dir * 9f, 0.45f, 1)
            }
        }
    }
    return Scene(ball, true, bowler, if (p < 0.26f) runUp * 14f else 0f, arms, striker, non, runPhase, fielders, keeper, stumpsFall, stumpsEnd, swing)
}

private fun camFor(mode: Int, ev: BallEv?, p: Float, sc: Scene, w: Float, h: Float): Cam3 {
    return when (mode) {
        1 -> Cam3(V3(0f, 95f, 75f), V3(0f, 0f, 14f), 50f, w, h)                      // aerial
        2 -> Cam3(V3(-38f, 7f, 10f), V3(0f, 1.2f, 10f), 42f, w, h)                  // side-on
        3 -> Cam3(V3(1.7f, 2.3f, -7.5f), V3(-0.4f, 1.0f, 22f), 52f, w, h)                  // batter cam
        else -> {                                                                    // broadcast + follow
            val base = Cam3(V3(0f, 7.5f, 49f), V3(0f, 0.6f, 5f), 27f, w, h)
            val b = sc.ball
            val follow = ev != null && p > 0.42f && b != null && (ev.kind == "SIX" || ev.kind == "FOUR" || ev.kind == "RUN" || ev.kind == "NB" ||
                (ev.kind == "WICKET" && (ev.how.startsWith("c ") && !ev.how.startsWith("c †") || ev.how.startsWith("run out"))))
            if (!follow || b == null) base else {
                val k = easeInOut(seg(p, 0.42f, 0.58f))
                val tgt = lerp3(V3(0f, 0.6f, 5f), V3(b.x * 0.85f, b.y * 0.6f, b.z * 0.85f + 2f), k)
                val eye = lerp3(V3(0f, 7.5f, 49f), V3(b.x * 0.2f, 34f, 66f + b.z * 0.1f), k)
                Cam3(eye, tgt, lerpF(27f, 50f, k), w, h)
            }
        }
    }
}

/* ------------------------------- drawing ------------------------------- */
private fun DrawScope.poly(cam: Cam3, pts: List<V3>, color: Color) { cam.polyPath(pts)?.let { drawPath(it, color) } }
private fun DrawScope.line3(cam: Cam3, a: V3, b: V3, color: Color, widthM: Float, minPx: Float = 1f) {
    val ca = cam.toCam(a); val cb = cam.toCam(b)
    if (ca.z < cam.near && cb.z < cam.near) return
    var pa = ca; var pb = cb
    if (pa.z < cam.near) pa = lerp3(pa, pb, (cam.near - pa.z) / (pb.z - pa.z))
    if (pb.z < cam.near) pb = lerp3(pb, pa, (cam.near - pb.z) / (pa.z - pb.z))
    val wpx = max(minPx, widthM * cam.f / ((pa.z + pb.z) / 2f))
    drawLine(color, cam.projC(pa), cam.projC(pb), wpx, StrokeCap.Round)
}
private fun circlePts(c: V3, r: Float, n: Int, y: Float = c.y) = List(n) { i -> val a = 2 * PI * i / n; V3(c.x + r * cos(a).toFloat(), y, c.z + r * sin(a).toFloat()) }

private fun DrawScope.drawPlayer(cam: Cam3, pos: V3, shirt: Color, pants: Color, run: Float, facing: Float, batter: Boolean, arms: Float = 0f, crouch: Boolean = false, swing: Float = 0f) {
    val px = cam.pxPerM(pos); if (px <= 0f) return
    // shadow
    cam.polyPath(circlePts(pos + V3(0.25f, 0.01f, 0.15f), 0.45f, 10, 0.01f))?.let { drawPath(it, Color(0x55000000)) }
    val fx = sin(facing); val fz = cos(facing)
    val side = V3(fz, 0f, -fx)
    val legSwing = if (run > 0f) sin(run * PI.toFloat() * 2f) * 0.35f else 0f
    val hipY = if (crouch) 0.55f else 0.95f
    val hip = pos + V3(0f, hipY, 0f); val shoulder = pos + V3(0f, if (crouch) 1.0f else 1.45f, 0f) + V3(fx, 0f, fz) * (if (crouch) 0.15f else 0f)
    val footL = pos + side * 0.14f + V3(fx, 0f, fz) * legSwing; val footR = pos - side * 0.14f - V3(fx, 0f, fz) * legSwing
    line3(cam, hip + side * 0.1f, footL, pants, 0.16f, 1.2f)
    line3(cam, hip - side * 0.1f, footR, pants, 0.16f, 1.2f)
    line3(cam, hip, shoulder, shirt, 0.36f, 2f)
    val handY = if (arms > 0f) 2.15f else 1.0f
    line3(cam, shoulder + side * 0.18f, pos + side * 0.32f + V3(0f, handY, 0f), shirt, 0.1f, 1f)
    line3(cam, shoulder - side * 0.18f, pos - side * 0.32f + V3(0f, handY, 0f), shirt, 0.1f, 1f)
    val head = shoulder + V3(0f, 0.24f, 0f)
    cam.project(head)?.let { o -> val r = max(1.5f, 0.13f * cam.pxPerM(head)); drawCircle(Color(0xFF8D5524), r, o); if (batter) drawCircle(Color(0xFF0D1B4A), r, o, style = Stroke(r * 0.55f)) }
    if (batter) { // bat
        val a = -0.8f + swing * 2.6f
        val grip = pos + side * 0.25f + V3(0f, 0.95f, 0f)
        val tip = grip + V3(fx * sin(a) * 0.85f, -cos(a) * 0.85f, fz * sin(a) * 0.85f) + side * 0.05f
        line3(cam, grip, tip, Color(0xFFE8D2A0), 0.1f, 1.5f)
    }
}

private fun DrawScope.drawStumps(cam: Cam3, z: Float, fall: Float) {
    for ((i, x) in listOf(-0.11f, 0f, 0.11f).withIndex()) {
        val tilt = fall * (if (i == 0) -0.9f else if (i == 2) 0.9f else 0.4f)
        val back = fall * 0.9f * (if (z < 1f) -1f else 1f)
        val top = V3(x + sin(tilt) * 0.71f, cos(tilt) * cos(back) * 0.71f, z + sin(back) * 0.71f)
        line3(cam, V3(x, 0f, z), top, Color(0xFFF5F0E1), 0.038f, 1.2f)
    }
    if (fall > 0f) { // bails flying
        for (k in 0..1) { val u = fall; cam.project(V3(-0.06f + 0.12f * k + (k - 0.5f) * u * 2.5f, 0.72f + 2.2f * u * (1.4f - u), z + (if (z < 1f) -1f else 1f) * u * 2.4f))?.let { drawCircle(Color(0xFFF5F0E1), 2.5f, it) } }
    } else line3(cam, V3(-0.12f, 0.72f, z), V3(0.12f, 0.72f, z), Color(0xFFF5F0E1), 0.02f, 1f)
}

/**
 * 3D stadium with ball-by-ball animation for [ev].  [onImpact] fires at bat contact, [onDone] when the animation ends.
 */
@Composable
fun Stadium3DView(ev: BallEv?, batColor: Color, bowlColor: Color, camMode: Int, durationMs: Int, replay: Boolean,
                  modifier: Modifier = Modifier, sound: Boolean = true, playKey: Int = 0, onDone: () -> Unit = {}) {
    val crowd = remember { buildCrowd() }
    var p by remember { mutableFloatStateOf(1f) }
    val evKey = ev?.id ?: -1
    LaunchedEffect(evKey, replay, playKey) {
        if (ev == null) { p = 1f; onDone(); return@LaunchedEffect }
        p = 0f
        val dur = durationMs.toFloat() * (when (ev.kind) { "SIX", "WICKET" -> 1.25f; "FOUR" -> 1.1f; "RUN", "NB" -> 1f + 0.12f * ev.bat; else -> 0.85f })
        var impact = false; var outcome = false
        val start = withFrameNanos { it }
        while (p < 1f) {
            val now = withFrameNanos { it }
            p = ((now - start) / 1_000_000f / dur).coerceAtMost(1f)
            if (sound && !impact && p >= 0.39f) { impact = true; GameAudio.onBat(ev.kind) }
            if (sound && !outcome && p >= 0.5f) { outcome = true; GameAudio.onOutcome(ev.kind, ev.how, ev.milestone) }
        }
        onDone()
    }
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val sc = sceneFor(ev, p)
            val cam = camFor(camMode, ev, p, sc, size.width, size.height)
            // sky (floodlit night)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF040B1E), Color(0xFF0E2A4A), Color(0xFF1F4D5C))))
            // ground
            poly(cam, circlePts(CENTER, 72f, 72, 0f), Color(0xFF2E7D32))
            for (k in -6..5) if (k % 2 == 0) {
                val x0 = k * 11f; val x1 = x0 + 11f
                val xs = (0..6).map { x0 + (x1 - x0) * it / 6f }.filter { abs(it) < 71.9f }
                if (xs.size >= 2) {
                    val top = xs.map { V3(it, 0f, CENTER.z + sqrt(72f * 72f - it * it)) }
                    val bot = xs.reversed().map { V3(it, 0f, CENTER.z - sqrt(72f * 72f - it * it)) }
                    poly(cam, top + bot, Color(0xFF388E3C))
                }
            }
            // 30-yard circle & rope
            for (pt in circlePts(CENTER, 27.4f, 60, 0.02f).filterIndexed { i, _ -> i % 2 == 0 }) cam.project(pt)?.let { drawCircle(Color(0xCCFFFFFF), max(1f, 0.25f * cam.pxPerM(pt)), it) }
            val rope = circlePts(CENTER, BOUNDARY, 96, 0.05f)
            for (i in rope.indices) line3(cam, rope[i], rope[(i + 1) % rope.size], Color(0xFFFFFFFF), 0.15f, 1f)
            // advertising boards
            val boards = circlePts(CENTER, 68f, 48, 0f)
            val bcol = listOf(Color(0xFF00C853), Color(0xFFFFC107), Color(0xFF1565C0), Color(0xFFE53935))
            val boardIdx = boards.indices.sortedByDescending { cam.depth(boards[it]) }
            // stands (sorted far to near)
            val n = 40
            val segs = (0 until n).map { i ->
                val a0 = 2 * PI.toFloat() * i / n; val a1 = 2 * PI.toFloat() * (i + 1) / n
                fun pt(r: Float, y: Float, a: Float) = V3(CENTER.x + r * cos(a), y, CENTER.z + r * sin(a))
                listOf(pt(70f, 1.2f, a0), pt(70f, 1.2f, a1), pt(97f, 21f, a1), pt(97f, 21f, a0))
            }.sortedByDescending { q -> cam.depth(V3((q[0].x + q[2].x) / 2, 8f, (q[0].z + q[2].z) / 2)) }
            for (q in segs) {
                poly(cam, q, Color(0xFF1A2430))
                poly(cam, listOf(q[3], q[2], V3(q[2].x * 1.02f, 25f, CENTER.z + (q[2].z - CENTER.z) * 1.02f), V3(q[3].x * 1.02f, 25f, CENTER.z + (q[3].z - CENTER.z) * 1.02f)), Color(0xFF0B1118))
            }
            // crowd
            val buckets = Array(CROWD_COLS.size) { ArrayList<Offset>() }
            crowd.pts.forEachIndexed { i, pt -> cam.project(pt)?.let { o -> if (o.x > -5 && o.x < size.width + 5 && o.y > -5 && o.y < size.height + 5) buckets[crowd.col[i]].add(o) } }
            for (c in buckets.indices) drawPoints(buckets[c], PointMode.Points, CROWD_COLS[c].copy(alpha = 0.85f), strokeWidth = max(2f, size.height / 160f), cap = StrokeCap.Round)
            for (i in boardIdx) { val a = boards[i]; val b = boards[(i + 1) % boards.size]; poly(cam, listOf(a, b, b + V3(0f, 1f, 0f), a + V3(0f, 1f, 0f)), bcol[i % 4]) }
            // floodlights
            for (k in 0 until 4) {
                val a = PI.toFloat() / 4 + k * PI.toFloat() / 2
                val base = V3(CENTER.x + 102f * cos(a), 0f, CENTER.z + 102f * sin(a)); val top = base + V3(0f, 48f, 0f)
                line3(cam, base, top, Color(0xFF4A5560), 1.2f, 1f)
                cam.project(top)?.let { o -> val r = max(4f, 5f * cam.pxPerM(top)); drawCircle(Brush.radialGradient(listOf(Color(0xCCFFFFE0), Color(0x00FFFFE0)), o, r * 3f), r * 3f, o); drawCircle(Color(0xFFFFFDE7), r * 0.8f, o) }
            }
            // pitch + creases
            poly(cam, listOf(V3(-1.52f, 0.01f, -1.6f), V3(1.52f, 0.01f, -1.6f), V3(1.52f, 0.01f, 21.7f), V3(-1.52f, 0.01f, 21.7f)), Color(0xFFCDB582))
            poly(cam, listOf(V3(-1.1f, 0.012f, 3f), V3(1.1f, 0.012f, 3f), V3(1.1f, 0.012f, 17f), V3(-1.1f, 0.012f, 17f)), Color(0xFFD8C392))
            for (z in listOf(1.22f, 18.9f)) line3(cam, V3(-1.5f, 0.02f, z), V3(1.5f, 0.02f, z), Color.White, 0.05f, 1f)
            for (z in listOf(0f, 20.12f)) line3(cam, V3(-1.32f, 0.02f, z), V3(1.32f, 0.02f, z), Color.White, 0.05f, 1f)
            for (x in listOf(-1.32f, 1.32f)) { line3(cam, V3(x, 0.02f, -1.2f), V3(x, 0.02f, 1.22f), Color.White, 0.05f, 1f); line3(cam, V3(x, 0.02f, 18.9f), V3(x, 0.02f, 21.3f), Color.White, 0.05f, 1f) }
            // dynamic objects, painter-sorted
            val items = ArrayList<Pair<Float, () -> Unit>>()
            val fieldShirt = bowlColor; val fieldPants = Color(0xFF263238)
            sc.fielders.forEach { f -> items.add(cam.depth(f) to { drawPlayer(cam, f, fieldShirt, fieldPants, 0f, PI.toFloat(), false) }) }
            items.add(cam.depth(sc.keeper) to { drawPlayer(cam, sc.keeper, fieldShirt, fieldPants, 0f, 0f, false, crouch = true) })
            items.add(cam.depth(sc.bowler) to { drawPlayer(cam, sc.bowler, fieldShirt, fieldPants, sc.bowlerRun, PI.toFloat(), false, sc.bowlerArms) })
            items.add(cam.depth(sc.striker) to { drawPlayer(cam, sc.striker, batColor, Color(0xFFECEFF1), sc.runPhase * 2f, -PI.toFloat() / 2 + 0.3f, true, swing = sc.batSwing) })
            items.add(cam.depth(sc.nonStriker) to { drawPlayer(cam, sc.nonStriker, batColor, Color(0xFFECEFF1), sc.runPhase * 2f, PI.toFloat(), true) })
            val ump = V3(-1.3f, 0f, 22.6f)
            items.add(cam.depth(ump) to { drawPlayer(cam, ump, Color(0xFFFAFAFA), Color(0xFF212121), 0f, PI.toFloat(), false) })
            items.add(cam.depth(V3(0f, 0.3f, 0f)) to { drawStumps(cam, 0f, if (sc.stumpsEndZ < 1f) sc.stumpsFall else 0f) })
            items.add(cam.depth(V3(0f, 0.3f, 20.12f)) to { drawStumps(cam, 20.12f, if (sc.stumpsEndZ > 1f) sc.stumpsFall else 0f) })
            sc.ball?.let { b ->
                items.add(cam.depth(b) - 0.2f to {
                    cam.project(V3(b.x, 0.01f, b.z))?.let { o -> drawCircle(Color(0x66000000), max(1.5f, 0.09f * cam.pxPerM(b)), o) }
                    cam.project(b)?.let { o -> val r = max(2.4f, 0.075f * cam.pxPerM(b)); drawCircle(Color(0x55FFFFFF), r * 1.9f, o); drawCircle(Color(0xFFF8F8F0), r, o) }
                    Unit
                })
            }
            items.sortedByDescending { it.first }.forEach { it.second() }
        }
        // event banner
        if (ev != null && p > 0.5f) {
            val (label, col) = when (ev.kind) {
                "SIX" -> "SIX!" to C.Gold; "FOUR" -> "FOUR!" to C.Green; "WICKET" -> "OUT!" to C.Red
                "WIDE" -> "WIDE" to C.Blue; "NB" -> "NO BALL" to C.Blue; "APPEAL" -> "HOWZAT?" to Color(0xFFFFAB40)
                else -> (if (ev.runs > 0) "${ev.runs} RUN${if (ev.runs > 1) "S" else ""}" else "") to Color.White
            }
            if (label.isNotEmpty()) {
                val s = 0.6f + 0.4f * easeOut(seg(p, 0.5f, 0.62f))
                val big = ev.kind == "SIX" || ev.kind == "FOUR" || ev.kind == "WICKET"
                Text(label, color = col, fontWeight = FontWeight.Black, fontSize = if (big) 40.sp else 20.sp,
                    modifier = Modifier.align(if (big) Alignment.Center else Alignment.TopCenter).padding(top = 6.dp).scale(s)
                        .background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 2.dp))
            }
        }
        if (replay) Text("● REPLAY", color = C.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
