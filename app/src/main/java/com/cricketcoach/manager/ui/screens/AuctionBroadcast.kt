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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.data.RealPlayer
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Phase B — premium auction broadcast.
 * Navy / metallic-gold mega-auction look: compact broadcast header, a spotlight stage with the lot's body (or the 3D
 * player model), a full player dossier (nationality, age, role, batting / bowling style, previous IPL team, real career
 * stats, recent form), the price rail (base / current bid / highest bidder), team bidding panels (purse, slots,
 * overseas, live paddle state), a recent-purchases ticker and a full auction history (sold / unsold / retained).
 * All artwork is drawn in code; no official logos or likenesses.
 */

private val Navy0 = Color(0xFF040A1C)
private val Navy1 = Color(0xFF0A1636)
private val Navy2 = Color(0xFF12224D)
private val GoldEdge = Brush.linearGradient(listOf(Color(0xFFFFE7A6), Color(0xFFB8862B), Color(0x66B8862B)))

/** Live bidding stage. [preview] 0 = normal, 1 = clock stopped (screenshots), 2 = clock stopped + SOLD stamp shown. */
@Composable
internal fun AuctionStage(vm: GameVM, s: CareerSave, a: AuctionState, preview: Int = 0) {
    val p = Auction.cur(s, a)
    val u = s.userClub()
    var running by remember { mutableStateOf(preview == 0) }
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
    var show3d by remember { mutableStateOf(preview == 0 && vm.settings.gfx > 0) }
    var history by remember { mutableStateOf(false) }
    var simAsk by remember { mutableStateOf(false) }
    val leader = s.clubs[a.curBidder]
    val tint by animateColorAsState(leader?.let { Color(it.primary) } ?: Color(0xFF1D3A8A), tween(600), label = "tint")

    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = 1.18.em)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Navy0).windowInsetsPadding(WindowInsets.displayCutout)) {
        val narrow = maxWidth < 800.dp
        val spotW: Dp = if (narrow) 150.dp else 196.dp
        val teamsW: Dp = if (narrow) 222.dp else 252.dp
        StageBackdrop(tint)
        Column(Modifier.fillMaxSize()) {
            BroadcastHeader(vm, s, a, p, running, show3d, narrow,
                onPause = { running = !running }, onAuto = { simAsk = true }, on3d = {
                    if (vm.settings.gfx == 0 && !show3d) vm.msg("Set Graphics to Medium or High in Settings for the 3D player view") else show3d = !show3d
                }, onHistory = { history = true })
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                // ---- spotlight
                if (p != null) Spotlight(s, a, p, u, show3d, vm.settings.gfx, Modifier.width(spotW).fillMaxHeight())
                Spacer(Modifier.width(6.dp))
                // ---- dossier + price rail + recent ticker
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (p != null) Dossier(vm, s, p, u, Modifier.weight(1f).fillMaxWidth())
                    else Box(Modifier.weight(1f))
                    Spacer(Modifier.height(5.dp))
                    PriceRail(s, a, p, leader, u, narrow)
                    Spacer(Modifier.height(5.dp))
                    RecentTicker(s, a) { history = true }
                }
                Spacer(Modifier.width(6.dp))
                // ---- team panels + controls
                Column(Modifier.width(teamsW).fillMaxHeight()) {
                    TeamPanels(s, a, Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(5.dp))
                    val leading = a.curBidder == u.id
                    BidButton(Money.fmt(Auction.nextAmount(a)), u, enabled = p != null && !leading && !stampOn, leading = leading) { vm.auctionBid() }
                    Spacer(Modifier.height(5.dp))
                    Row {
                        RailBtn("PASS", Modifier.weight(1f)) { vm.auctionPass() }
                        Spacer(Modifier.width(5.dp))
                        RailBtn("PROFILE", Modifier.weight(1f)) { p?.let { vm.nav(Screen.Profile(it.id)) } }
                    }
                }
            }
        }
        if (simAsk) SimAllDialog(u.squad.size, onDismiss = { simAsk = false }) { forMe -> simAsk = false; vm.auctionAuto(forMe) }
        AnimatedVisibility(stampOn, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
            SoldStamp(s, a.lastPid, s.clubs[a.lastClub], a.lastPrice, a.lastClub == u.id)
        }
    }
    }
    if (history) AuctionHistoryDialog(vm, s, a) { history = false }
}

/* ----------------------------------------------------------------- header */

/** Which "set" a lot belongs to (marquee first, then by role), like a broadcast lower-third. */
fun auctionSetLabel(a: AuctionState, p: GPlayer?): String {
    if (p == null) return "ACCELERATED ROUND"
    if (a.index < 10) return "MARQUEE SET"
    val role = when (p.role) { "WK" -> "WICKET-KEEPERS"; "BOWL" -> if (p.bowlType == "SPIN") "SPIN BOWLERS" else "FAST BOWLERS"; "AR" -> "ALL-ROUNDERS"; else -> "BATTERS" }
    return "SET ${a.index / 10 + 1} · $role"
}

@Composable
private fun BroadcastHeader(vm: GameVM, s: CareerSave, a: AuctionState, p: GPlayer?, running: Boolean, show3d: Boolean, narrow: Boolean,
                            onPause: () -> Unit, onAuto: () -> Unit, on3d: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(38.dp).background(Brush.horizontalGradient(listOf(Color(0xF2050B20), Color(0xE60B1838), Color(0xF2050B20))))
        .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { GameAudio.click(); vm.back() }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.GoldLight, modifier = Modifier.size(20.dp))
        }
        Glyph("gavel", C.Gold, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(if (narrow) "${if (a.type == "MEGA") "MEGA" else "MINI"} AUCTION" else "${if (a.type == "MEGA") "MEGA" else "MINI"} AUCTION ${s.year}", fontFamily = Display, color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = if (narrow) 17.sp else 19.sp, letterSpacing = 1.2.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        LiveDot(running)
        Spacer(Modifier.width(8.dp))
        HeaderTag("LOT ${(a.index + 1).coerceAtMost(a.lots.size)}/${a.lots.size}", C.Text)
        Spacer(Modifier.width(4.dp))
        HeaderTag(if (narrow) auctionSetLabel(a, p).substringAfter(" · ") else auctionSetLabel(a, p), C.Gold)
        Spacer(Modifier.weight(1f))
        if (!narrow) Text("SOLD ${a.sold.size} · UNSOLD ${a.unsold.size}" + (if (a.retained.isNotEmpty()) " · RETAINED ${a.retained.size}" else "") + " · ${Money.fmt(a.sold.sumOf { it.price })}",
            color = C.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
        Spacer(Modifier.width(6.dp))
        HeaderBtn(if (show3d) "3D ON" else "3D", show3d, on3d)
        Spacer(Modifier.width(4.dp))
        HeaderBtn("HISTORY", false, onHistory)
        Spacer(Modifier.width(4.dp))
        HeaderBtn(if (running) "PAUSE" else "RESUME", !running, onPause)
        Spacer(Modifier.width(4.dp))
        HeaderBtn("SIM ALL", false, onAuto)
    }
    Box(Modifier.fillMaxWidth().height(1.5.dp).background(GoldEdge))
}

@Composable
private fun LiveDot(running: Boolean) {
    val inf = rememberInfiniteTransition(label = "live")
    val a by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "la")
    Row(Modifier.clip(RoundedCornerShape(4.dp)).background(if (running) C.Red else Color(0xFF475569)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).alpha(if (running) a else 1f).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text(if (running) "LIVE" else "PAUSED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun HeaderTag(text: String, col: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = 0.12f)).border(0.5.dp, col.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = col, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun HeaderBtn(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Box(Modifier.height(30.dp).clip(shape).background(if (on) C.Gold.copy(alpha = 0.9f) else Color(0x22E9B949)).border(1.dp, C.Gold.copy(alpha = 0.6f), shape)
        .clickable { GameAudio.click(); onClick() }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Color(0xFF1A1200) else C.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

/* ----------------------------------------------------------------- spotlight */

@Composable
private fun Spotlight(s: CareerSave, a: AuctionState, p: GPlayer, u: Club, show3d: Boolean, gfx: Int, modifier: Modifier) {
    val nat = Consts.nationByCountry(p.country)
    val prim = nat?.primary ?: 0xFF1565C0
    val sec = nat?.secondary ?: 0xFFFFC107
    val inf = rememberInfiniteTransition(label = "spot")
    val sway by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sw")
    // entrance: every new lot walks into the light
    val enter = remember(p.id) { Animatable(0f) }
    LaunchedEffect(p.id) { enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF07112B), Color(0xFF02050F)))).border(1.dp, GoldEdge, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2 + sway * w * 0.05f
            val cone = Path().apply { moveTo(cx - w * 0.06f, 0f); lineTo(cx + w * 0.06f, 0f); lineTo(w * 0.95f, h * 0.9f); lineTo(w * 0.05f, h * 0.9f); close() }
            drawPath(cone, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color(prim).copy(alpha = 0.10f), Color.Transparent), 0f, h * 0.92f))
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color(prim).copy(alpha = 0.12f), Color.Transparent), Offset(w / 2, h * 0.88f), w * 0.5f),
                Offset(w * 0.06f, h * 0.82f), Size(w * 0.88f, h * 0.12f))
            drawOval(C.Gold.copy(alpha = 0.45f), Offset(w * 0.1f, h * 0.84f), Size(w * 0.8f, h * 0.08f), style = Stroke(1.5f))
        }
        if (show3d && gfx > 0) {
            com.cricketcoach.manager.render3d.Player3DShowcase(p.id, p.country, prim, sec, gfx, Modifier.fillMaxSize().padding(bottom = 26.dp)) {
                BodyFigure(p, prim, sec, Modifier.fillMaxSize())
            }
        } else {
            BodyFigure(p, prim, sec, Modifier.fillMaxSize().padding(top = 18.dp, bottom = 26.dp).alpha(enter.value).scale(0.92f + 0.08f * enter.value))
        }
        // status + OVR
        Row(Modifier.align(Alignment.TopStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(C.Gold).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text("ON THE BLOCK", color = Color(0xFF1A1200), fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(C.GoldBrush), contentAlignment = Alignment.Center) {
                Text("${p.overall()}", color = Color(0xFF1A1200), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Text("OVR", color = C.GoldLight, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Text(if (show3d && gfx > 0) "Generic 3D model in national kit" else "Kit preview · not a real likeness", color = Color.White.copy(alpha = 0.45f), fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), maxLines = 1)
    }
}

/** Shirt number used on kits (stable per player). */
fun kitNumber(pid: String): Int = com.cricketcoach.manager.render3d.Look.number(pid)   // same number as the 3D model's shirt

/** Stylised full-body cricketer in his kit with the real photo / initials medallion as the head (no likeness modelling). */
@Composable
private fun BodyFigure(p: GPlayer, prim: Long, sec: Long, modifier: Modifier) {
    val primary = Color(prim); val secondary = Color(sec)
    val wk = p.role == "WK"; val bowler = p.role == "BOWL"
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val h = maxHeight
        val headSize = (h * 0.21f).coerceAtMost(64.dp)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val hh = size.height
            val cx = w / 2
            val top = headSize.toPx() * 0.92f
            val shoulder = w * 0.20f
            // legs (whites) + pads
            val legTop = hh * 0.56f
            for (side in listOf(-1f, 1f)) {
                val lx = cx + side * w * 0.07f
                drawRoundRect(Color(0xFFE9EDF5), Offset(lx - w * 0.055f, legTop), Size(w * 0.11f, hh * 0.36f), CornerRadius(10f))
                if (!bowler) drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFCDD5E4))), Offset(lx - w * 0.065f, legTop + hh * 0.14f), Size(w * 0.13f, hh * 0.2f), CornerRadius(8f))
                if (!bowler) for (k in 0..2) drawLine(Color(0xFFB7C1D6), Offset(lx - w * 0.02f + k * w * 0.02f, legTop + hh * 0.15f), Offset(lx - w * 0.02f + k * w * 0.02f, legTop + hh * 0.33f), 2f)
                drawRoundRect(Color(0xFF1B1F2A), Offset(lx - w * 0.07f, hh * 0.915f), Size(w * 0.13f, hh * 0.04f), CornerRadius(6f))
                drawRect(secondary, Offset(lx - w * 0.07f, hh * 0.915f), Size(w * 0.13f, hh * 0.008f))
            }
            // torso (jersey)
            val torso = Path().apply {
                moveTo(cx - shoulder, top + hh * 0.04f); quadraticBezierTo(cx, top - hh * 0.01f, cx + shoulder, top + hh * 0.04f)
                lineTo(cx + w * 0.15f, legTop + hh * 0.02f); lineTo(cx - w * 0.15f, legTop + hh * 0.02f); close()
            }
            drawPath(torso, Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.85f), Color(0xFF0A1020)), top, legTop + hh * 0.1f))
            // chest band + collar in the secondary colour
            drawRect(secondary.copy(alpha = 0.9f), Offset(cx - w * 0.17f, top + hh * 0.10f), Size(w * 0.34f, hh * 0.018f))
            drawArc(secondary, 0f, 180f, false, Offset(cx - w * 0.05f, top - hh * 0.005f), Size(w * 0.1f, hh * 0.04f), style = Stroke(4f))
            // arms + gloves
            for (side in listOf(-1f, 1f)) {
                val sx = cx + side * shoulder
                drawLine(primary, Offset(sx, top + hh * 0.05f), Offset(sx + side * w * 0.07f, top + hh * 0.2f), w * 0.075f)
                drawLine(Color(0xFFB98A68), Offset(sx + side * w * 0.07f, top + hh * 0.2f), Offset(sx + side * w * 0.06f, top + hh * 0.31f), w * 0.055f)
                val gloveCol = if (wk) Color(0xFFF2F4F8) else if (!bowler) Color(0xFFFFFFFF) else Color(0xFFB98A68)
                drawCircle(gloveCol, w * (if (wk) 0.05f else 0.035f), Offset(sx + side * w * 0.06f, top + hh * 0.33f))
            }
            // bat at the right hand (batters, keepers, all-rounders) or a ball for bowlers
            if (!bowler) {
                val hx = cx + shoulder + w * 0.06f; val hy = top + hh * 0.33f
                drawLine(Color(0xFF2B2B2B), Offset(hx, hy - hh * 0.02f), Offset(hx + w * 0.02f, hy + hh * 0.08f), w * 0.022f)
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF1D9A8), Color(0xFFD6B27A))), Offset(hx - w * 0.015f, hy + hh * 0.08f), Size(w * 0.07f, hh * 0.26f), CornerRadius(6f))
            } else drawCircle(Color(0xFFC62828), w * 0.03f, Offset(cx + shoulder + w * 0.06f, top + hh * 0.31f))
        }
        // number on the chest
        Text("${kitNumber(p.id)}", color = secondary, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (h.value * 0.075f).coerceIn(12f, 26f).sp,
            modifier = Modifier.padding(top = headSize * 0.92f + h * 0.15f))
        Box(Modifier.padding(top = 0.dp)) { PlayerFace(p.id, p.name, headSize, secondary) }
    }
}

/* ----------------------------------------------------------------- dossier */

@Composable
private fun Dossier(vm: GameVM, s: CareerSave, p: GPlayer, u: Club, modifier: Modifier) {
    val rp: RealPlayer? = Repo.byId[p.id]
    val nat = Consts.nationByCountry(p.country)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Brush.linearGradient(listOf(Navy2.copy(alpha = 0.95f), Navy1.copy(alpha = 0.95f), Navy0.copy(alpha = 0.95f))))
        .border(1.dp, GoldEdge, shape).clickable { vm.nav(Screen.Profile(p.id)) }.padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name.uppercase(), color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                rp?.full?.takeIf { it.isNotBlank() && it != p.name }?.let { Text(it, color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (nat != null && nat.short in Flags.CODES) NationFlag(nat.short, 30.dp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TagChip(p.country.ifBlank { "—" }, C.Text); Spacer(Modifier.width(4.dp))
            TagChip("${p.age(s.year)} YRS", C.Sub); Spacer(Modifier.width(4.dp))
            TagChip(Consts.ROLE_NAMES[p.role] ?: p.role, C.Blue); Spacer(Modifier.width(4.dp))
            if (TeamSelector.isOverseas(p, u)) { TagChip("OVERSEAS", UI.Amber); Spacer(Modifier.width(4.dp)) }
            val capped = (rp?.stats?.let { (it["TEST"]?.m ?: 0) + (it["ODI"]?.m ?: 0) + (it["T20I"]?.m ?: 0) } ?: 0) > 0
            TagChip(if (capped) "CAPPED" else "UNCAPPED", if (capped) C.Positive else C.Sub); Spacer(Modifier.width(4.dp))
            if (p.fictional) TagChip("FICTIONAL", UI.Violet)
        }
        Spacer(Modifier.height(5.dp))
        // styles + previous team
        Row(Modifier.fillMaxWidth()) {
            InfoCell("BATTING", rp?.bat?.ifBlank { null } ?: "Right-hand bat", Modifier.weight(1f))
            InfoCell("BOWLING", rp?.bowl?.ifBlank { null } ?: when (p.bowlType) { "SPIN" -> "Spin"; else -> if (p.role == "BAT" || p.role == "WK") "—" else "Pace" }, Modifier.weight(1f))
            InfoCell("PREVIOUS TEAM", rp?.ipl?.ifBlank { null } ?: s.clubs[p.history.lastOrNull { it.contains("Bought by") }?.substringAfter("Bought by ")?.substringBefore(" at ") ?: ""]?.name ?: "—", Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(5.dp))
        CareerStatsTable(p, rp, Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        RecentForm(p, rp)
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 6.dp)) {
        Text(label, color = C.Gold.copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CareerStatsTable(p: GPlayer, rp: RealPlayer?, modifier: Modifier) {
    val rows = ArrayList<List<String>>()
    if (rp != null) for (f in listOf("IPL", "T20I", "ODI", "TEST")) {
        val st = rp.stats[f] ?: continue
        if (st.m == 0) continue
        rows.add(listOf(if (f == "TEST") "Test" else f, "${st.m}", "${st.runs}", st.avg?.let { "%.1f".format(it) } ?: "-", st.sr?.let { "%.0f".format(it) } ?: "-",
            "${st.wk}", st.econ?.let { "%.2f".format(it) } ?: "-"))
    }
    if (p.careerM > 0) rows.add(listOf("Career", "${p.careerM}", "${p.careerRuns}", "-", "-", "${p.careerWk}", "-"))
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x33000000))) {
        val head = listOf("FORMAT", "M", "RUNS", "AVG", "SR", "WKTS", "ECON")
        val weights = listOf(1.3f, 0.8f, 1f, 0.9f, 0.8f, 0.9f, 0.9f)
        Row(Modifier.fillMaxWidth().background(Color(0x22E9B949)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            head.forEachIndexed { i, h -> Text(h, color = C.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, modifier = Modifier.weight(weights[i]), textAlign = if (i == 0) TextAlign.Start else TextAlign.End) }
        }
        if (rows.isEmpty()) Text("No senior career record — scouted on skills", color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
        rows.take(4).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.5.dp)) {
                r.forEachIndexed { i, v -> Text(v, color = if (i == 0) C.Text else C.Text.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(weights[i]), textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun RecentForm(p: GPlayer, rp: RealPlayer?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("RECENT", color = C.Gold.copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.width(44.dp))
        val bat = rp?.form.orEmpty().takeLast(5)
        val bowl = rp?.bform.orEmpty().takeLast(5)
        val useBowl = (p.role == "BOWL") && bowl.isNotEmpty()
        if (!useBowl && bat.isNotEmpty()) for (f in bat.reversed()) FormChip("${f.r}${if (f.no) "*" else ""}", if (f.r >= 50) C.Gold else if (f.r >= 25) C.Positive else C.Sub, f.o)
        else if (useBowl) for (f in bowl.reversed()) FormChip("${f.w}/${f.r}", if (f.w >= 3) C.Gold else if (f.w >= 1) C.Positive else C.Sub, f.o)
        else MiniBarInline(p)
    }
}

@Composable
private fun FormChip(v: String, col: Color, opp: String) {
    Column(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(5.dp)).background(col.copy(alpha = 0.12f)).border(0.5.dp, col.copy(alpha = 0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = col, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(opp.take(10).uppercase(), color = C.Sub, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.MiniBarInline(p: GPlayer) {
    Column(Modifier.weight(1f)) { MiniBar("BAT", p.batSkill(), C.Gold); MiniBar("BOWL", p.bowl, UI.Cyan) }
}

/* ----------------------------------------------------------------- price rail */

@Composable
private fun PriceRail(s: CareerSave, a: AuctionState, p: GPlayer?, leader: Club?, u: Club, narrow: Boolean = false) {
    val amount by animateFloatAsState(a.curBid.toFloat(), tween(450, easing = FastOutSlowInEasing), label = "amt")
    val pop = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    var lastInc by remember { mutableStateOf("") }
    var prevBid by remember { mutableStateOf(a.curBid) }
    LaunchedEffect(a.bidSeq) {
        val inc = a.curBid - prevBid; prevBid = a.curBid
        if (inc > 0.001) { lastInc = "+" + Money.fmt(inc); rise.snapTo(0f); launch { rise.animateTo(1f, tween(900)) } }
        pop.snapTo(1.12f); pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
    }
    val mine = leader?.id == u.id
    val edge = when { leader == null -> C.Gold.copy(alpha = 0.5f); mine -> C.Positive; else -> Color(leader.primary) }
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(70.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xF0050A18), Color(0xF00E1B3D), Color(0xF0050A18))))
        .border(1.5.dp, edge, shape), verticalAlignment = Alignment.CenterVertically) {
        // base
        Column(Modifier.width(if (narrow) 76.dp else 92.dp).padding(start = 10.dp)) {
            Text("BASE PRICE", color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(p?.let { Money.fmt(Money.basePrice(it.overall(), s.economy)) } ?: "—", color = C.Text, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Color(0x33E9B949)))
        // current bid
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Column {
                Text(if (a.curBidder.isEmpty()) "OPENING BID" else "CURRENT BID", color = C.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(Money.fmt(amount.toDouble()), color = C.GoldLight, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (narrow) 28.sp else 34.sp, lineHeight = 34.sp, maxLines = 1, softWrap = false, modifier = Modifier.scale(pop.value))
            }
            if (rise.value < 1f && lastInc.isNotEmpty())
                Text(lastInc, color = C.Positive, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).offset(y = (8 - 18 * rise.value).dp).alpha(1f - rise.value))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Color(0x33E9B949)))
        // highest bidder
        Row(Modifier.width(if (narrow) 128.dp else 170.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leader != null) ClubBadge(leader, if (narrow) 28.dp else 36.dp) else Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, Color(0x55FFFFFF), CircleShape))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(if (mine) "YOU LEAD" else "HIGHEST BIDDER", color = if (mine) C.Positive else C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(leader?.name ?: "Awaiting paddles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                GoingPips(a.countdown, compact = narrow)
            }
        }
    }
}

/* ----------------------------------------------------------------- recent purchases ticker */

@Composable
private fun RecentTicker(s: CareerSave, a: AuctionState, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(Modifier.fillMaxWidth().height(32.dp).clip(shape).background(Color(0xE6050A18)).border(1.dp, Color(0x33E9B949), shape).clickable { GameAudio.click(); onOpen() }
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("RECENT", color = C.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.width(8.dp))
        val recent = a.sold.takeLast(6).reversed()
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (recent.isEmpty()) Text("No players sold yet", color = C.Sub, fontSize = 10.sp)
            recent.forEach { sale ->
                val c = s.clubs[sale.club]
                ClubBadge(c, 18.dp); Spacer(Modifier.width(4.dp))
                Text(s.players[sale.pid]?.name ?: "", color = C.Text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(3.dp))
                Text(Money.fmt(sale.price), color = C.GoldLight, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                Text("   •   ", color = Color(0x55E9B949), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("HISTORY ›", color = C.GoldLight, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
    }
}

/* ----------------------------------------------------------------- team bidding panels */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TeamPanels(s: CareerSave, a: AuctionState, modifier: Modifier) {
    val kind = Modes.marketKind(s)
    val all = s.clubs.values.filter { it.kind == kind }
    val ordered = (listOfNotNull(s.clubs[s.userClubId]) + listOfNotNull(s.clubs[a.curBidder]) + a.lotBidders.reversed().mapNotNull { s.clubs[it] } +
        all.sortedByDescending { Career.purse(s, it) }).distinctBy { it.id }.take(10)
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(Color(0xE6060D24)).border(1.dp, GoldEdge, shape).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp)) {
            Text("TEAMS", color = C.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("PURSE", color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(62.dp), textAlign = TextAlign.End)
            Text("SLOTS", color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
            Text("O/S", color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(ordered, key = { it.id }) { c ->
                val state = when { c.id == a.curBidder -> 2; c.id in a.lotBidders -> 1; else -> 0 }
                TeamRow(s, c, state, c.id == s.userClubId, a.bidSeq, Modifier.animateItemPlacement())
            }
        }
    }
}

@Composable
private fun TeamRow(s: CareerSave, c: Club, state: Int, mine: Boolean, bidSeq: Int, modifier: Modifier) {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(bidSeq, state) { if (state == 2) { flash.snapTo(1f); flash.animateTo(0f, tween(900)) } }
    val bg by animateColorAsState(when (state) { 2 -> Color(c.primary).copy(alpha = 0.45f); 1 -> Color(0x22FFFFFF); else -> Color.Transparent }, tween(350), label = "tb")
    val shape = RoundedCornerShape(7.dp)
    Row(modifier.fillMaxWidth().padding(vertical = 1.dp).height(26.dp).clip(shape).background(bg)
        .border(if (state == 2 || mine) 1.dp else 0.dp, if (state == 2) C.GoldLight.copy(alpha = 0.6f + 0.4f * flash.value) else if (mine) C.Gold.copy(alpha = 0.5f) else Color.Transparent, shape)
        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // paddle state indicator
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(when (state) { 2 -> C.Gold; 1 -> C.Sub; else -> Color(0x22FFFFFF) }))
        Spacer(Modifier.width(4.dp))
        ClubBadge(c, 18.dp)
        Spacer(Modifier.width(5.dp))
        Text(c.short.take(5), color = if (state == 2) Color.White else if (state == 1) C.Text else C.Text.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        if (mine) { Spacer(Modifier.width(3.dp)); Text("YOU", color = C.Gold, fontSize = 7.5.sp, fontWeight = FontWeight.Black) }
        if (state == 2) { Spacer(Modifier.width(3.dp)); Text("▲ BID", color = C.GoldLight, fontSize = 7.5.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.weight(1f))
        Text(Money.fmt(Career.purse(s, c)), color = C.GoldLight, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(62.dp), textAlign = TextAlign.End, maxLines = 1)
        val slots = (25 - c.squad.size).coerceAtLeast(0)
        Text("$slots", color = if (slots == 0) C.Red else C.Text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
        Text("${(8 - Career.overseasCount(s, c)).coerceAtLeast(0)}", color = C.Sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun BidButton(amount: String, u: Club, enabled: Boolean, leading: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "bidpulse")
    val pulse by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "p")
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().height(44.dp).clip(shape)
        .background(if (enabled) C.GoldBrush else Brush.verticalGradient(listOf(Color(0x332D6BFF), Color(0x222D6BFF))))
        .border(1.5.dp, if (enabled) C.GoldLight.copy(alpha = pulse) else if (leading) C.Positive else Color(0x333D5A99), shape)
        .clickable(enabled = enabled) { onClick() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ClubBadge(u, 26.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (leading) "YOU ARE THE HIGHEST BIDDER" else "RAISE PADDLE", color = if (enabled) Color(0xFF1A1200) else if (leading) C.Positive else C.Sub,
                fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
            Text(if (leading) "Hold your nerve…" else "BID $amount", color = if (enabled) Color(0xFF1A1200) else C.Sub, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 19.sp, maxLines = 1)
        }
    }
}

@Composable
private fun RailBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC0A1530)).border(1.dp, C.Gold.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
        .clickable { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Text(text, color = C.GoldLight, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

/* ----------------------------------------------------------------- history */

@Composable
internal fun AuctionHistoryDialog(vm: GameVM, s: CareerSave, a: AuctionState, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(16.dp)
        Column(Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.9f).clip(shape).background(Brush.verticalGradient(listOf(Navy2, Navy0))).border(1.dp, GoldEdge, shape).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUCTION HISTORY", fontFamily = Display, color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Close", tint = C.Sub) }
            }
            val mine = a.sold.filter { it.club == s.userClubId }
            val tabs = listOf("SOLD ${a.sold.size}", "UNSOLD ${a.unsold.size}", "RETAINED ${a.retained.size}", "MY BUYS ${mine.size}")
            Row(Modifier.padding(vertical = 4.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Box(Modifier.padding(end = 5.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (on) C.Gold else Color(0x14FFFFFF)).border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { GameAudio.click(); tab = i }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(t, color = if (on) Color(0xFF1A1200) else C.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            val list: List<Triple<String, String?, Double?>> = when (tab) {
                0 -> a.sold.reversed().map { Triple(it.pid, it.club, it.price) }
                1 -> a.unsold.reversed().map { Triple(it, null, null) }
                2 -> a.retained.map { Triple(it.pid, it.club, it.price) }
                else -> mine.reversed().map { Triple(it.pid, it.club, it.price) }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (list.isEmpty()) item { Text("Nothing here yet.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                items(list.size) { i ->
                    val (pid, club, price) = list[i]
                    val p = s.players[pid]
                    val c = club?.let { s.clubs[it] }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x10FFFFFF)).clickable { onClose(); vm.nav(Screen.Profile(pid)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p != null) PlayerFace(p.id, p.name, 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p?.name ?: pid, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${Consts.ROLE_NAMES[p?.role] ?: ""} · ${p?.country ?: ""} · OVR ${p?.overall() ?: "-"}", color = C.Sub, fontSize = 10.sp, maxLines = 1)
                        }
                        val status = when (tab) { 1 -> "UNSOLD"; 2 -> "RETAINED"; else -> "SOLD" }
                        val col = when (status) { "UNSOLD" -> C.Red; "RETAINED" -> UI.Cyan; else -> C.Positive }
                        TagChip(status, col)
                        if (c != null) { Spacer(Modifier.width(6.dp)); ClubBadge(c, 22.dp); Spacer(Modifier.width(4.dp)); Text(c.short, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp)) }
                        Text(price?.let { Money.fmt(it) } ?: "—", color = C.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------- retention + report boards */

/** Non-live auction phases (retention, final report) in the same broadcast frame. */
@Composable
internal fun AuctionBoard(vm: GameVM, s: CareerSave, a: AuctionState) {
    var history by remember { mutableStateOf(false) }
    val u = s.userClub()
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = 1.18.em)) {
    Box(Modifier.fillMaxSize().background(Navy0).windowInsetsPadding(WindowInsets.displayCutout)) {
        StageBackdrop(Color(u.primary))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(38.dp).background(Brush.horizontalGradient(listOf(Color(0xF2050B20), Color(0xE60B1838), Color(0xF2050B20))))
                .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { GameAudio.click(); vm.back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.GoldLight, modifier = Modifier.size(20.dp))
                }
                Glyph("gavel", C.Gold, 18.dp); Spacer(Modifier.width(6.dp))
                Text("${if (a.type == "MEGA") "MEGA" else "MINI"} AUCTION ${s.year}", fontFamily = Display, color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(8.dp))
                HeaderTag(if (a.phase == "RETENTION") "RETENTION WINDOW" else "AUCTION COMPLETE", if (a.phase == "RETENTION") UI.Cyan else C.Positive)
                Spacer(Modifier.weight(1f))
                ClubBadge(u, 22.dp); Spacer(Modifier.width(6.dp))
                Text("PURSE ${Money.fmt(Career.purse(s, u))} · SQUAD ${u.squad.size}/25 · O/S ${Career.overseasCount(s, u)}/8", color = C.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                if (a.phase != "RETENTION") HeaderBtn("HISTORY", false) { history = true }
            }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(GoldEdge))
            if (a.phase == "RETENTION") RetentionBoard(vm, s, a) else AuctionReport(vm, s, a) { history = true }
        }
    }
    }
    if (history) AuctionHistoryDialog(vm, s, a) { history = false }
}

@Composable
private fun RetentionBoard(vm: GameVM, s: CareerSave, a: AuctionState) {
    val u = s.userClub()
    val picks = remember { mutableStateListOf<String>() }
    val squad = u.squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KEEP UP TO 4 PLAYERS", fontFamily = Display, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Retention salaries slot 1→4: " + (0..3).joinToString(" · ") { Money.fmt(Auction.retentionCost(s, it, u.division)) } + ". Everyone else enters the auction pool.",
                    color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${picks.size}/4 · ${Money.fmt(picks.indices.sumOf { Auction.retentionCost(s, it, u.division) })}", color = C.GoldLight, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(C.GoldBrush).clickable { GameAudio.click(); vm.confirmRetention(picks.toList()) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center) {
                Text("CONFIRM & START AUCTION", color = Color(0xFF1A1200), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(squad.size) { i ->
                val p = squad[i]
                val sel = p.id in picks
                val slot = picks.indexOf(p.id)
                val shape = RoundedCornerShape(10.dp)
                Column(Modifier.clip(shape).background(if (sel) Brush.verticalGradient(listOf(Color(0xFF3A2E0B), Color(0xFF1A1406))) else Brush.verticalGradient(listOf(Navy2, Navy1)))
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) C.Gold else Color(0x333D5A99), shape)
                    .clickable { GameAudio.click(); if (sel) picks.remove(p.id) else if (picks.size < 4) picks.add(p.id) else vm.msg("Maximum 4 retentions") }
                    .padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerFace(p.id, p.name, 34.dp, if (sel) C.Gold else C.Sub)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = C.Text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Consts.ROLE_NAMES[p.role]} · ${p.age(s.year)}y", color = C.Sub, fontSize = 9.5.sp, maxLines = 1)
                        }
                        Text("${p.overall()}", color = C.GoldLight, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(if (sel) "RETAINED #${slot + 1} · ${Money.fmt(Auction.retentionCost(s, slot, u.division))}" else "Tap to retain", color = if (sel) C.Gold else C.Sub,
                        fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AuctionReport(vm: GameVM, s: CareerSave, a: AuctionState, onHistory: () -> Unit) {
    val u = s.userClub()
    val mine = a.sold.filter { it.club == u.id }.sortedByDescending { it.price }
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReportTile("SPENT", Money.fmt(mine.sumOf { it.price }), C.GoldLight, Modifier.weight(1f))
                Spacer(Modifier.width(5.dp)); ReportTile("BOUGHT", "${mine.size}", C.Text, Modifier.weight(1f))
                Spacer(Modifier.width(5.dp)); ReportTile("RETAINED", "${a.retained.count { it.club == u.id }}", UI.Cyan, Modifier.weight(1f))
                Spacer(Modifier.width(5.dp)); ReportTile("UNSOLD", "${a.unsold.size}", C.Red, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("YOUR SQUAD AFTER THE AUCTION", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            val boughtIds = mine.map { it.pid }.toSet()
            val retainedMine = a.retained.filter { it.club == u.id }
            val others = u.squad.filter { it !in boughtIds && retainedMine.none { r -> r.pid == it } }.mapNotNull { s.players[it] }
            val cards: List<Triple<String, String, Double?>> = mine.map { Triple(it.pid, "BOUGHT", it.price) } + retainedMine.map { Triple(it.pid, "RETAINED", it.price) } + others.map { Triple(it.id, "SQUAD", null) }
            if (cards.isEmpty()) Text("Your squad is empty — sign free agents in Transfers.", color = C.Sub, fontSize = 11.sp)
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(128.dp), Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cards.size) { i ->
                    val (pid, tag, price) = cards[i]; val p = s.players[pid]
                    val shape = RoundedCornerShape(10.dp)
                    val col = when (tag) { "BOUGHT" -> C.Positive; "RETAINED" -> UI.Cyan; else -> C.Sub }
                    Column(Modifier.clip(shape).background(Brush.verticalGradient(listOf(Navy2, Navy1))).border(1.dp, if (tag == "SQUAD") Color(0x333D5A99) else col.copy(alpha = 0.7f), shape)
                        .clickable { if (p != null) vm.nav(Screen.Profile(p.id)) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (p != null) PlayerFace(p.id, p.name, 40.dp)
                        Text(p?.name ?: "", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${Consts.ROLE_NAMES[p?.role] ?: ""} · ${p?.overall() ?: ""}", color = C.Sub, fontSize = 9.5.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagChip(tag, col)
                            if (price != null) { Spacer(Modifier.width(4.dp)); Text(Money.fmt(price), color = C.GoldLight, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(C.GoldBrush).clickable { GameAudio.click(); vm.back() }, contentAlignment = Alignment.Center) {
                    Text("DONE", color = Color(0xFF1A1200), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(6.dp))
                RailBtn("FREE AGENTS", Modifier.weight(1f)) { vm.replaceTop(Screen.Transfers) }
                Spacer(Modifier.width(6.dp))
                RailBtn("FULL HISTORY", Modifier.weight(1f)) { onHistory() }
            }
        }
        Spacer(Modifier.width(8.dp))
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.width(280.dp).fillMaxHeight().clip(shape).background(Color(0xE6060D24)).border(1.dp, GoldEdge, shape).padding(8.dp)) {
            Text("TOP BUYS", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            val top = a.sold.sortedByDescending { it.price }.take(20)
            LazyColumn(Modifier.weight(1f)) {
                items(top.size) { i ->
                    val sale = top[i]; val c = s.clubs[sale.club]
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(if (sale.club == u.id) Color(0x22E9B949) else Color.Transparent)
                        .clickable { vm.nav(Screen.Profile(sale.pid)) }.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = C.Sub, fontSize = 10.sp, modifier = Modifier.width(18.dp))
                        ClubBadge(c, 18.dp); Spacer(Modifier.width(5.dp))
                        Text(s.players[sale.pid]?.name ?: "", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.fmt(sale.price), color = C.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            a.log.lastOrNull()?.let { Text(it, color = C.Sub, fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ReportTile(label: String, value: String, col: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(Navy2, Navy1))).border(1.dp, Color(0x44E9B949), shape).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(label, color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = col, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
    }
}


/** SIM ALL confirmation: let the club auto-bid for the user (fills towards 18) or let only the AI bid. */
@Composable
private fun SimAllDialog(squad: Int, onDismiss: () -> Unit, onGo: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Navy1,
        title = { Text("SIM REST OF AUCTION", fontFamily = Display, color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Text("Your squad has $squad players. Auto-bid makes your club bid like the AI franchises (same valuations and purse reserve) " +
                "until you have ${Auction.AUTO_TARGET} players. AI only skips every remaining lot for you.", color = C.Text, fontSize = 13.sp)
        },
        confirmButton = { TextButton(onClick = { GameAudio.click(); onGo(true) }) { Text("AUTO-BID FOR ME", color = C.Gold, fontWeight = FontWeight.Black) } },
        dismissButton = {
            Row {
                TextButton(onClick = { GameAudio.click(); onDismiss() }) { Text("CANCEL", color = C.Sub, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { GameAudio.click(); onGo(false) }) { Text("AI ONLY", color = C.Text, fontWeight = FontWeight.Bold) }
            }
        })
}
