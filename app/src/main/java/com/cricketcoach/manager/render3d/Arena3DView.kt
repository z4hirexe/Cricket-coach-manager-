package com.cricketcoach.manager.render3d

import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cricketcoach.manager.game.BallEv

/** Hosts a Filament [Arena3D] in a SurfaceView driven by the Choreographer, with safe teardown. */
private class Host(val arena: Arena3D, val director: MatchDirector) : Choreographer.FrameCallback {
    private var last = 0L; var running = false
    var failed by mutableStateOf(false)
    fun start() { if (!running) { running = true; last = 0L; Choreographer.getInstance().postFrameCallback(this) } }
    fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }
    override fun doFrame(now: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f); last = now
        try { director.update(dt); arena.render(now) }
        catch (t: Throwable) { android.util.Log.e("Arena3D", "frame failed - falling back to 2.5D", t); stop(); failed = true }
    }
}

@Composable
private fun rememberHost(quality: Int): Host? {
    val ctx = LocalContext.current
    val host = remember {
        try { Arena3D.initLibs(); val a = Arena3D(ctx.applicationContext, quality); Host(a, MatchDirector(a)) } catch (t: Throwable) { android.util.Log.e("Arena3D", "init failed", t); null }
    }
    DisposableEffect(host) { onDispose { host?.stop(); host?.arena?.destroy() } }
    LaunchedEffect(quality) { host?.arena?.let { if (it.quality != quality) it.applyQuality(quality) } }
    return host
}

/** Realistic 3D match view (Filament). Falls back to [fallback] if the GPU/driver can't create the engine. */
@Composable
fun Match3DView(
    ev: BallEv?, cast: Cast?, camMode: Int, durationMs: Int, replay: Boolean, quality: Int, lighting: Int, redBall: Boolean,
    modifier: Modifier = Modifier, playKey: Int = 0, pitchWear: Float = 0.1f, rate: Float = 1f, onImpact: (BallEv) -> Unit = {}, onOutcome: (BallEv) -> Unit = {}, onDone: () -> Unit = {},
    fallback: @Composable () -> Unit
) {
    val host = rememberHost(quality)
    if (host == null || host.failed) { fallback(); return }
    var loading by remember { mutableStateOf(true) }
    val d = host.director
    d.cam = camMode
    d.rate = rate
    val curImpact by rememberUpdatedState(onImpact); val curOutcome by rememberUpdatedState(onOutcome); val curDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        d.onImpact = { curImpact(it) }; d.onOutcome = { curOutcome(it) }; d.onDone = { curDone() }
        host.arena.onFirstFrame = { loading = false }
    }
    LaunchedEffect(lighting) { host.arena.setLighting(lighting) }
    LaunchedEffect(redBall) { host.arena.setBallColor(redBall) }
    LaunchedEffect((pitchWear * 50).toInt()) { host.arena.setPitchWear(pitchWear) }
    val castKey = cast?.let { "${it.strikerId}|${it.nonStrikerId}|${it.bowlerId}|${it.keeperId}|${it.batKit.primary}|${it.bowlKit.primary}" }
    LaunchedEffect(castKey) { cast?.let { d.setup(it) } }
    LaunchedEffect(ev?.id, replay, playKey, castKey) {
        if (ev == null) { onDone(); return@LaunchedEffect }
        cast?.let { d.replay = replay; d.start(ev, durationMs, it) } ?: onDone()
    }
    Box(modifier) {
        AndroidView(factory = { c -> SurfaceView(c).also { host.arena.attach(it); host.start() } }, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(loading, exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0xFF050B09)), contentAlignment = Alignment.Center) { Text("Loading stadium…", color = Color(0xFFE8C766), fontSize = 13.sp) }
        }
    }
}

/** Full-screen cinematic stadium for the title screen with a featured star player. */
@Composable
fun Title3DView(starId: String, country: String, kit: Kit, quality: Int, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val host = rememberHost(quality)
    if (host == null || host.failed) { fallback(); return }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(starId, kit.primary) { host.arena.setLighting(Lighting.NIGHT); host.director.setupTitle(starId, country, kit); host.arena.onFirstFrame = { loading = false } }
    Box(modifier) {
        AndroidView(factory = { c -> SurfaceView(c).also { host.arena.attach(it); host.start() } }, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(loading, exit = fadeOut()) { Box(Modifier.fillMaxSize().background(Color(0xFF050B09))) }
    }
}
