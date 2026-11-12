package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay

/*
 * Phase 6 - Auction presentation: an auction-hall stage with spotlights, an auctioneer at the lectern whose gavel strikes on
 * "going once / twice / sold", the lot card, an animated bid ticker, a wall of team paddles (the leading paddle is raised)
 * and a SOLD / UNSOLD stamp. All artwork is drawn in code (generic auctioneer silhouette, original team badges).
 */

private val StageTop = Color(0xFF0B1230)
private val StageBottom = Color(0xFF02040C)

/** Live bidding stage. [preview] 0 = normal, 1 = clock stopped (screenshots), 2 = clock stopped + SOLD stamp shown. */
@Composable
internal fun AuctionStage(vm: GameVM, s: CareerSave, a: AuctionState, preview: Int = 0) {
    val p = Auction.cur(s, a)
    val u = s.userClub()
    var running by remember { mutableStateOf(preview == 0) }
    // SOLD / UNSOLD stamp: shown when a new hammer result arrives (not for the one that was there when the screen opened)
    var seenSeq by remember { mutableIntStateOf(if (preview == 2) a.lastSeq - 1 else a.lastSeq) }
    var stampSeq by remember { mutableIntStateOf(-1) }
    LaunchedEffect(a.lastSeq) {
        if (a.lastSeq > seenSeq && a.lastPid.isNotEmpty()) {
            seenSeq = a.lastSeq; stampSeq = a.lastSeq
            if (preview != 2) { delay(2300); if (stampSeq == a.lastSeq) stampSeq = -1 }
        }
    }
    val stampOn = stampSeq >= 0
    LaunchedEffect(a.index, running, vm.tick, stampOn) {
        if (!running || stampOn) return@LaunchedEffect
        delay(1150)
        if (s.auction?.phase == "LIVE") vm.auctionTick()
    }
    var podium3d by remember { mutableStateOf(false) }
    val leader = s.clubs[a.curBidder]
    val tint by animateColorAsState(leader?.let { Color(it.primary) } ?: Color(0xFF2D6BFF), tween(600), label = "tint")

    Box(Modifier.fillMaxSize()) {
        StageBackdrop(tint)
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            // ---- auctioneer + lot strip
            Column(Modifier.width(132.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                LedChip("${if (a.type == "MEGA") "MEGA" else "MINI"} AUCTION", C.Gold)
                Spacer(Modifier.height(4.dp))
                Text("LOT ${(a.index + 1).coerceAtMost(a.lots.size)}", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp)
                Text("of ${a.lots.size}", color = C.Sub, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                StageBtn(if (podium3d) "3D PODIUM: ON" else "3D PODIUM", Modifier.fillMaxWidth()) {
                    if (vm.settings.gfx == 0 && !podium3d) vm.msg("Set Graphics to Medium or High in Settings for the 3D podium") else podium3d = !podium3d
                }
                Spacer(Modifier.weight(1f))
                Auctioneer(strikeKey = a.countdown + a.lastSeq * 10, countdown = a.countdown, modifier = Modifier.fillMaxWidth().height(150.dp))
                GoingPips(a.countdown)
            }
            Spacer(Modifier.width(6.dp))
            // ---- lot card + bid ticker
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (p != null) LotCard(vm, s, p, u, Modifier.weight(1f), if (podium3d) vm.settings.gfx else -1)
                Spacer(Modifier.height(6.dp))
                BidTicker(s, a, leader, u)
            }
            Spacer(Modifier.width(8.dp))
            // ---- paddles + controls
            Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                PurseStrip(s, u)
                Spacer(Modifier.height(6.dp))
                Text("BIDDING ROOM", color = C.Sub, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                PaddleWall(s, a)
                Spacer(Modifier.height(6.dp))
                val leading = a.curBidder == u.id
                BidPaddleButton(Money.fmt(Auction.nextAmount(a)), u, enabled = p != null && !leading && !stampOn, leading = leading) { vm.auctionBid() }
                Spacer(Modifier.height(6.dp))
                Row {
                    StageBtn("PASS", Modifier.weight(1f)) { vm.auctionPass() }
                    Spacer(Modifier.width(5.dp))
                    StageBtn(if (running) "PAUSE" else "RESUME", Modifier.weight(1f)) { running = !running }
                    Spacer(Modifier.width(5.dp))
                    StageBtn("AUTO", Modifier.weight(1f)) { vm.auctionAuto() }
                }
                Spacer(Modifier.height(6.dp))
                a.log.takeLast(4).reversed().forEachIndexed { i, line ->
                    Text(line, color = logColor(line).copy(alpha = 1f - i * 0.2f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(stampOn, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
            SoldStamp(s, a.lastPid, s.clubs[a.lastClub], a.lastPrice, a.lastClub == u.id)
        }
    }
}

private fun logColor(line: String) = when {
    line.startsWith("🔨") -> C.Gold; line.startsWith("YOU") -> C.Positive; line.startsWith("UNSOLD") -> C.Red; line.startsWith("LOT") -> C.Blue; else -> C.Sub
}

/* ----------------------------------------------------------------- backdrop */

@Composable
private fun StageBackdrop(tint: Color) {
    val inf = rememberInfiniteTransition(label = "stage")
    val sweep by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(StageTop, StageBottom)))
        // LED wall glow tinted by the leading team
        drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent), 0f, h * 0.55f))
        // dotted LED matrix
        val step = 14f
        var y = 6f
        while (y < h * 0.42f) { var x = 6f; while (x < w) { drawCircle(Color.White.copy(alpha = 0.035f), 1.4f, Offset(x, y)); x += step }; y += step }
        // spotlight cones
        fun cone(cx: Float, spread: Float, a: Float) {
            val path = Path().apply { moveTo(cx - 10f, 0f); lineTo(cx + 10f, 0f); lineTo(cx + spread, h); lineTo(cx - spread, h); close() }
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), 0f, h * 0.95f))
        }
        cone(w * 0.36f + sweep * w * 0.04f, w * 0.16f, 0.10f)
        cone(w * 0.55f - sweep * w * 0.05f, w * 0.18f, 0.08f)
        cone(w * 0.10f, w * 0.10f, 0.07f)
        // stage floor with a gold rim
        drawOval(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.42f, h * 1.02f), w * 0.4f), Offset(w * 0.02f, h * 0.84f), Size(w * 0.8f, h * 0.36f))
        drawOval(C.Gold.copy(alpha = 0.25f), Offset(w * 0.04f, h * 0.88f), Size(w * 0.76f, h * 0.3f), style = Stroke(2f))
    }
}

@Composable
private fun LedChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCC050A18)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, maxLines = 1)
    }
}

/* ----------------------------------------------------------------- auctioneer */

/** Generic auctioneer silhouette at a lectern; the gavel strikes whenever [strikeKey] changes. */
@Composable
private fun Auctioneer(strikeKey: Int, countdown: Int, modifier: Modifier = Modifier) {
    val swing = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(strikeKey) {
        if (first) { first = false; return@LaunchedEffect }
        swing.animateTo(1f, tween(140, easing = FastOutLinearInEasing))
        swing.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val inf = rememberInfiniteTransition(label = "breath")
    val breath by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w * 0.45f
        val bob = breath * 2f
        // body: suit + shirt + tie
        val torso = Path().apply {
            moveTo(cx - w * 0.30f, h * 0.70f); quadraticBezierTo(cx - w * 0.30f, h * 0.36f + bob, cx - w * 0.10f, h * 0.33f + bob)
            lineTo(cx + w * 0.10f, h * 0.33f + bob); quadraticBezierTo(cx + w * 0.30f, h * 0.36f + bob, cx + w * 0.30f, h * 0.70f); close()
        }
        drawPath(torso, Brush.verticalGradient(listOf(Color(0xFF1E2A4A), Color(0xFF0C1226))))
        val shirt = Path().apply { moveTo(cx - w * 0.07f, h * 0.335f + bob); lineTo(cx + w * 0.07f, h * 0.335f + bob); lineTo(cx, h * 0.52f); close() }
        drawPath(shirt, Color(0xFFE8ECF5))
        val tie = Path().apply { moveTo(cx - w * 0.02f, h * 0.35f + bob); lineTo(cx + w * 0.02f, h * 0.35f + bob); lineTo(cx + w * 0.03f, h * 0.50f); lineTo(cx, h * 0.53f); lineTo(cx - w * 0.03f, h * 0.50f); close() }
        drawPath(tie, C.Gold)
        // head (featureless, softly lit)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFB98A68), Color(0xFF6E4B35)), Offset(cx - w * 0.03f, h * 0.18f + bob), w * 0.14f), w * 0.11f, Offset(cx, h * 0.21f + bob))
        drawArc(Color(0xFF1B1410), 180f, 180f, true, Offset(cx - w * 0.115f, h * 0.085f + bob), Size(w * 0.23f, h * 0.15f))
        // gavel arm + gavel (pivot at the right hand)
        val pivot = Offset(cx + w * 0.30f, h * 0.55f)
        val angle = -55f + 55f * swing.value
        drawLine(Color(0xFF1E2A4A), Offset(cx + w * 0.22f, h * 0.42f + bob), pivot, w * 0.07f)
        rotate(angle, pivot) {
            drawLine(Color(0xFF8D5A2B), pivot, Offset(pivot.x + w * 0.20f, pivot.y), w * 0.025f)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA86B36), Color(0xFF5E3615))), Offset(pivot.x + w * 0.17f, pivot.y - h * 0.06f), Size(w * 0.09f, h * 0.12f), CornerRadius(4f))
            drawRect(C.Gold, Offset(pivot.x + w * 0.17f, pivot.y - h * 0.012f), Size(w * 0.09f, h * 0.024f))
        }
        // strike flash on the sound block
        val block = Offset(pivot.x + w * 0.21f, h * 0.66f)
        if (swing.value > 0.7f) drawCircle(C.GoldLight.copy(alpha = (swing.value - 0.7f) * 2.5f), w * 0.12f, block)
        drawRoundRect(Color(0xFF4A2A10), Offset(block.x - w * 0.08f, block.y - h * 0.015f), Size(w * 0.16f, h * 0.035f), CornerRadius(3f))
        // lectern
        val lec = Path().apply { moveTo(w * 0.02f, h * 0.68f); lineTo(w * 0.98f, h * 0.68f); lineTo(w * 0.88f, h); lineTo(w * 0.12f, h); close() }
        drawPath(lec, Brush.verticalGradient(listOf(Color(0xFF2A1A0C), Color(0xFF120A04)), h * 0.68f, h))
        drawRect(C.Gold, Offset(w * 0.02f, h * 0.675f), Size(w * 0.96f, h * 0.02f))
        drawCircle(C.GoldBrush, w * 0.09f, Offset(w * 0.5f, h * 0.84f))
        drawCircle(Color(0xFF120A04), w * 0.065f, Offset(w * 0.5f, h * 0.84f))
        drawCircle(C.Gold, w * 0.03f, Offset(w * 0.5f, h * 0.84f))
        if (countdown > 0) drawCircle(C.Red.copy(alpha = 0.25f + 0.15f * breath), w * 0.02f, Offset(w * 0.9f, h * 0.73f))
    }
}

@Composable
private fun GoingPips(countdown: Int) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("ONCE", "TWICE", "SOLD").forEachIndexed { i, l ->
            val on = countdown > i
            val col by animateColorAsState(if (on) (if (i == 2) C.Red else C.Gold) else Color(0x33FFFFFF), label = "pip")
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = if (on) 1f else 0.25f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text(l, color = if (on) Color.Black else C.Sub, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/* ----------------------------------------------------------------- lot card */

@Composable
private fun LotCard(vm: GameVM, s: CareerSave, p: GPlayer, u: Club, modifier: Modifier, quality3d: Int = -1) {
    val nat = Consts.nationByCountry(p.country)
    val glow = nat?.let { Color(it.primary) } ?: C.Blue
    val overseas = TeamSelector.isOverseas(p, u)
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(glow.copy(alpha = 0.35f), Color(0xE60A1530), Color(0xF2050A18))))
        .border(1.dp, Brush.linearGradient(listOf(C.GoldLight, C.GoldDeep.copy(alpha = 0.3f))), RoundedCornerShape(16.dp))
        .clickable { vm.nav(Screen.Profile(p.id)) }
        .padding(10.dp)) {
        // giant faded rating in the background
        Text("${p.overall()}", color = Color.White.copy(alpha = 0.05f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 130.sp,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp), lineHeight = 130.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (quality3d >= 0) {
                // the lot "on the podium": 3D close-up in his national colours
                Box(Modifier.width(150.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
                    com.cricketcoach.manager.render3d.Player3DShowcase(p.id, p.country, nat?.primary ?: 0xFF1565C0, nat?.secondary ?: 0xFFFFC107, quality3d, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PlayerFace(p.id, p.name, 96.dp) }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clip(RoundedCornerShape(6.dp)).background(C.GoldBrush).padding(horizontal = 8.dp, vertical = 1.dp)) {
                        Text("${p.overall()}", color = Color(0xFF1A1200), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else Box(contentAlignment = Alignment.BottomCenter) {
                PlayerFace(p.id, p.name, 96.dp)
                Box(Modifier.offset(y = 8.dp).clip(RoundedCornerShape(6.dp)).background(C.GoldBrush).padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text("${p.overall()}", color = Color(0xFF1A1200), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name.uppercase(), color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    nat?.let { if (it.short in Flags.CODES) { NationFlag(it.short, 22.dp); Spacer(Modifier.width(6.dp)) } }
                    TagChip(Consts.ROLE_NAMES[p.role] ?: p.role, C.Blue)
                    Spacer(Modifier.width(4.dp))
                    TagChip("${p.age(s.year)}Y", C.Sub)
                    if (overseas) { Spacer(Modifier.width(4.dp)); TagChip("OVERSEAS", UI.Amber) }
                    if (p.fictional) { Spacer(Modifier.width(4.dp)); TagChip("FICTIONAL", UI.Violet) }
                }
                Spacer(Modifier.height(6.dp))
                MiniBar("BAT", p.batSkill(), C.Gold)
                MiniBar("BOWL", p.bowl, UI.Cyan)
                MiniBar("FIELD", p.field, C.Positive)
                MiniBar("FORM", p.form, UI.Pink, 100)
                Text("Base ${Money.fmt(Money.basePrice(p.overall(), s.economy))} • tap for full profile", color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun TagChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

@Composable
private fun MiniBar(label: String, v: Int, col: Color, max: Int = 99) {
    val f by animateFloatAsState((v / max.toFloat()).coerceIn(0f, 1f), tween(700), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.6f), col))))
        }
        Text("$v", color = C.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(26.dp))
    }
}

/* ----------------------------------------------------------------- bid ticker */

@Composable
private fun BidTicker(s: CareerSave, a: AuctionState, leader: Club?, u: Club) {
    val amount by animateFloatAsState(a.curBid.toFloat(), tween(450, easing = FastOutSlowInEasing), label = "amt")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(a.bidSeq) { pop.snapTo(1.12f); pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
    val mine = leader?.id == u.id
    val edge = when { leader == null -> Color(0x553D5A99); mine -> C.Positive; else -> Color(leader.primary) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xE6050A18)).border(2.dp, edge, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (a.curBidder.isEmpty()) "OPENING BID" else "CURRENT BID", color = C.Sub, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(Money.fmt(amount.toDouble()), color = C.Gold, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 38.sp, modifier = Modifier.scale(pop.value))
        }
        Column(horizontalAlignment = Alignment.End) {
            if (leader != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (mine) "YOU LEAD" else "LEADING", color = if (mine) C.Positive else C.Sub, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text(leader.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    ClubBadge(leader, 38.dp)
                }
            } else Text("No paddles yet", color = C.Sub, fontSize = 12.sp)
            if (a.countdown > 0) Text(if (a.countdown == 1) "GOING ONCE…" else "GOING TWICE…", color = C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

/* ----------------------------------------------------------------- purse + paddles + buttons */

@Composable
private fun PurseStrip(s: CareerSave, u: Club) {
    val ps = u.squad.mapNotNull { s.players[it] }
    val wk = ps.count { it.role == "WK" }; val bowl = ps.count { it.role == "BOWL" || it.role == "AR" }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xCC0A1530)).border(1.dp, Color(0x333D5A99), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ClubBadge(u, 26.dp)
        Spacer(Modifier.width(6.dp))
        PurseCell("PURSE", Money.fmt(Career.purse(s, u)), C.Gold, Modifier.weight(1.3f))
        PurseCell("SQUAD", "${u.squad.size}/25", if (u.squad.size < 15) C.Red else C.Text, Modifier.weight(1f))
        PurseCell("O/S", "${Career.overseasCount(s, u)}/8", C.Text, Modifier.weight(0.8f))
        PurseCell("WK", "$wk", if (wk < 2) UI.Amber else C.Text, Modifier.weight(0.6f))
        PurseCell("BWL", "$bowl", if (bowl < 7) UI.Amber else C.Text, Modifier.weight(0.6f))
    }
}

@Composable
private fun PurseCell(label: String, value: String, col: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(value, color = col, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Every franchise in the room holds a paddle: the leader's is raised and glowing, clubs that bid on this lot are lit. */
@Composable
private fun PaddleWall(s: CareerSave, a: AuctionState) {
    val kind = Modes.marketKind(s)
    val all = s.clubs.values.filter { it.kind == kind }
    val ordered = (listOfNotNull(s.clubs[s.userClubId]) + a.lotBidders.reversed().mapNotNull { s.clubs[it] } + all.sortedBy { it.short })
        .distinctBy { it.id }.take(10)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ordered.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { c ->
                    val state = when { c.id == a.curBidder -> 2; c.id in a.lotBidders -> 1; else -> 0 }
                    Paddle(c, state, c.id == s.userClubId, Modifier.weight(1f))
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Paddle(c: Club, state: Int, mine: Boolean, modifier: Modifier) {
    val lift by animateDpAsState(if (state == 2) (-10).dp else 0.dp, spring(dampingRatio = 0.45f, stiffness = 300f), label = "lift")
    val alpha by animateFloatAsState(when (state) { 2 -> 1f; 1 -> 0.85f; else -> 0.45f }, label = "a")
    val prim = Color(c.primary); val sec = Color(c.secondary)
    Column(modifier.height(62.dp).alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.offset(y = lift), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(width = 44.dp, height = 54.dp)) {
                val w = size.width; val h = size.height; val r = w * 0.40f; val ctr = Offset(w / 2, r + 2f)
                if (state == 2) drawCircle(Brush.radialGradient(listOf(C.GoldLight.copy(alpha = 0.7f), Color.Transparent), ctr, r * 1.6f), r * 1.6f, ctr)
                drawRoundRect(Color(0xFF6D4C2F), Offset(w / 2 - w * 0.05f, ctr.y + r * 0.6f), Size(w * 0.1f, h - ctr.y - r * 0.6f), CornerRadius(3f))
                drawCircle(prim, r, ctr)
                drawCircle(sec, r, ctr, style = Stroke(r * 0.16f))
                if (mine) drawCircle(C.Gold, r + 3f, ctr, style = Stroke(2.5f))
            }
            Text(c.short.take(4), color = if (prim.luminanceApprox() > 0.6f) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun BidPaddleButton(amount: String, u: Club, enabled: Boolean, leading: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "bidpulse")
    val pulse by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(48.dp).clip(shape)
        .background(if (enabled) C.GoldBrush else Brush.verticalGradient(listOf(Color(0x332D6BFF), Color(0x222D6BFF))))
        .border(2.dp, if (enabled) C.GoldLight.copy(alpha = pulse) else if (leading) C.Positive else Color(0x333D5A99), shape)
        .clickable(enabled = enabled) { onClick() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(28.dp)) {
            val r = size.width * 0.36f; val ctr = Offset(size.width / 2, r + 1f)
            drawRoundRect(Color(0xFF6D4C2F), Offset(size.width / 2 - 2.5f, ctr.y + r * 0.6f), Size(5f, size.height - ctr.y - r * 0.6f), CornerRadius(2f))
            drawCircle(Color(u.primary), r, ctr); drawCircle(Color(u.secondary), r, ctr, style = Stroke(3f))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (leading) "YOU ARE THE HIGHEST BIDDER" else "RAISE PADDLE", color = if (enabled) Color(0xFF1A1200) else if (leading) C.Positive else C.Sub,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1)
            Text(if (leading) "Hold your nerve…" else "BID $amount", color = if (enabled) Color(0xFF1A1200) else C.Sub, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp, maxLines = 1)
        }
    }
}

@Composable
private fun StageBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xCC0A1530)).border(1.dp, C.Gold.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
        .clickable { com.cricketcoach.manager.audio.GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Text(text, color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}

/* ----------------------------------------------------------------- SOLD / UNSOLD stamp */

@Composable
private fun SoldStamp(s: CareerSave, pid: String, club: Club?, price: Double, mine: Boolean) {
    val p = s.players[pid]
    val sold = club != null
    val scale = remember { Animatable(2.4f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(pid, price) {
        scale.snapTo(2.4f); ring.snapTo(0f)
        scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }
    LaunchedEffect(pid, price) { delay(120); ring.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
    val col = when { !sold -> Color(0xFF9AA4B8); mine -> C.Positive; else -> C.Gold }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        // team-colour burst behind the stamp
        Canvas(Modifier.fillMaxSize()) {
            val ctr = Offset(size.width / 2, size.height / 2)
            val base = club?.let { Color(it.primary) } ?: Color(0xFF39404F)
            drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.55f), Color.Transparent), ctr, size.minDimension * 0.7f), size.minDimension * 0.7f, ctr)
            drawCircle(col.copy(alpha = (1f - ring.value) * 0.8f), size.minDimension * (0.2f + 0.5f * ring.value), ctr, style = Stroke(6f * (1f - ring.value) + 1f))
            if (sold) for (i in 0 until 16) {
                val ang = Math.toRadians(i * 22.5).toFloat()
                val r0 = size.minDimension * (0.22f + 0.25f * ring.value); val r1 = r0 + 26f
                drawLine(col.copy(alpha = (1f - ring.value) * 0.9f), Offset(ctr.x + r0 * kotlin.math.cos(ang), ctr.y + r0 * kotlin.math.sin(ang)),
                    Offset(ctr.x + r1 * kotlin.math.cos(ang), ctr.y + r1 * kotlin.math.sin(ang)), 4f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(scale.value).rotate(-8f).clip(RoundedCornerShape(10.dp)).border(5.dp, col, RoundedCornerShape(10.dp)).background(Color(0x66000000))
                .padding(horizontal = 26.dp, vertical = 2.dp)) {
                Text(if (sold) "SOLD!" else "UNSOLD", color = col, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 72.sp, letterSpacing = 4.sp, lineHeight = 76.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p != null) PlayerFace(p.id, p.name, 54.dp, col)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p?.name?.uppercase() ?: "", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp)
                    if (sold) Text("to ${club!!.name} for ${Money.fmt(price)}", color = C.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    else Text("No bids — goes to the free-agent pool", color = C.Sub, fontSize = 13.sp)
                    if (mine) Text("WELCOME TO THE SQUAD!", color = C.Positive, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }
                if (club != null) { Spacer(Modifier.width(12.dp)); ClubBadge(club, 54.dp) }
            }
        }
    }
}
