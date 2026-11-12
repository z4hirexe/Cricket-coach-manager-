package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.FmtStats
import com.cricketcoach.manager.data.OffStats
import com.cricketcoach.manager.data.RealPlayer
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

private fun n(d: Double?): String = d?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.2f".format(it) } ?: "-"

/** In-game rating rank among all real players of the same role (an in-game ranking, not ICC). */
private fun ratingRank(pid: String, role: String, ovr: Int, s: CareerSave?): Pair<Int, Int> {
    return if (s != null) {
        val same = s.players.values.filter { it.role == role && !it.retired && !it.academy }
        same.count { it.overall() > ovr } + 1 to same.size
    } else {
        val same = Repo.ds.players.filter { it.role == role }
        same.count { (Repo.attrs[it.id]?.overall ?: 0) > ovr } + 1 to same.size
    }
}

@Composable
fun ProfileScreen(vm: GameVM, pid: String) {
    val s = vm.save
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val rp: RealPlayer? = Repo.byId[pid]
    val gp: GPlayer? = s?.players?.get(pid)
    val a = Repo.attrs[pid]
    var page by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var bidDialog by remember { mutableStateOf(false) }
    var swapDialog by remember { mutableStateOf(false) }
    val name = gp?.name ?: rp?.name ?: pid
    val ovr = gp?.overall() ?: a?.overall ?: 50
    val role = gp?.role ?: rp?.role ?: "BAT"
    val country = gp?.country ?: rp?.country ?: ""
    val club = gp?.let { g -> s?.clubs?.get(g.clubId) }
    val nat = Consts.nationByCountry(country)
    val primary = club?.let { Color(it.primary) } ?: nat?.let { Color(it.primary) } ?: Color(0xFF1B5E20)
    val secondary = club?.let { Color(it.secondary) } ?: nat?.let { Color(it.secondary) } ?: C.Gold
    val attrs = ProfileAttrs(
        tech = gp?.tech ?: a?.tech ?: 0, power = gp?.power ?: a?.power ?: 0, bowl = gp?.bowl ?: a?.bowl ?: 0,
        field = gp?.field ?: a?.field ?: 0, keep = gp?.keep ?: a?.keep ?: 0, form = gp?.form ?: 60
    )
    ScreenScaffold(vm, name) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            ProfileHero(pid, name, rp, gp, s, ovr, role, country, club, primary, secondary)
            var show3d by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    if (vm.settings.gfx == 0 && !show3d) vm.msg("Set Graphics to Medium or High in Settings for the 3D view") else show3d = !show3d
                }) { Text(if (show3d) "Hide 3D view" else "View in 3D (stadium close-up)", color = C.Gold, fontSize = 12.sp) }
            }
            if (show3d) Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, secondary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
                com.cricketcoach.manager.render3d.Player3DShowcase(pid, country, club?.primary ?: nat?.primary ?: 0xFF1565C0, club?.secondary ?: nat?.secondary ?: 0xFFFFC107,
                    vm.settings.gfx, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(C.Card), contentAlignment = Alignment.Center) { Sub("3D view is not available on this device.") }
                }
                Text("Generic 3D model in team kit — real likenesses are not used", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            QuickStats(rp, gp, s, pid)
            Spacer(Modifier.height(6.dp))
            Tabs(listOf("Overview", "Real stats", "Career mode"), page) { page = it }
            when (page) {
                0 -> {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        CcmCard(Modifier.weight(1f).fillMaxHeight()) {
                            CardTitle("Skill radar")
                            AttributeRadar(attrs, role, primary, Modifier.fillMaxWidth().height(190.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        CcmCard(Modifier.weight(1.25f).fillMaxHeight()) {
                            CardTitle("Attributes")
                            AttrBar("Batting technique", attrs.tech, C.Gold); AttrBar("Power hitting", attrs.power, Color(0xFFFF8A3D))
                            AttrBar("Bowling", attrs.bowl, UI.Cyan); AttrBar("Fielding", attrs.field, C.Positive)
                            if (role == "WK") AttrBar("Wicket-keeping", attrs.keep, UI.Violet)
                            if (gp != null) {
                                val own = s != null && gp.clubId == s.userClubId || gp.academy
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Potential", color = C.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    if (gp.scouted || own) PotentialStars(gp.potential, ovr) else Text("Unknown — scout him", color = C.Sub, fontSize = 12.sp)
                                }
                                if (gp.injured) KV("Injury", "${gp.injury} (${gp.injuryDays} days)", C.Red)
                            }
                            Sub("Ratings are derived from the real career statistics (game abstraction).", size = 10)
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        if (gp != null) {
                            CcmCard(Modifier.weight(1f).fillMaxHeight()) {
                                CardTitle("Condition")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    RingGauge("FITNESS", gp.fitness, C.Positive); RingGauge("MORALE", gp.morale, UI.Cyan); RingGauge("FORM", gp.form, C.Gold)
                                }
                                Sub(moraleLine(gp), size = 11)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        CcmCard(Modifier.weight(1.25f).fillMaxHeight()) {
                            CardTitle("Recent form")
                            if (rp != null && (rp.form.isNotEmpty() || rp.bform.isNotEmpty())) FormChart(rp, Modifier.fillMaxWidth().height(120.dp))
                            else Sub("No recent real-world innings in the Cricsheet data.")
                        }
                    }
                    TeamsCard(rp, gp, s)
                    if (s != null && gp != null) ActionsCard(vm, s, gp, { bidDialog = true }, { swapDialog = true })
                }
                1 -> {
                    if (rp == null) CcmCard { Sub("Fictional player — no real-world statistics.") }
                    else {
                        val tabs = listOf("TEST", "ODI", "T20I", "IPL")
                        Tabs(tabs, tab) { tab = it }
                        val f = tabs[tab]
                        val off = rp.off?.get(f)
                        if (off != null && f != "IPL") OfficialCard(off, rp.offSrc, rp.offDate)
                        val st = rp.stats[f]
                        if (st != null && st.m > 0) DetailedCard(f, st, off != null)
                        if (off == null && (st == null || st.m == 0)) CcmCard { Sub(if (f == "IPL") "No IPL matches in the Cricsheet IPL dataset." else "No $f appearances recorded in the verified sources.") }
                        if (rp.ms.isNotEmpty()) CcmCard { CardTitle("Milestones"); rp.ms.forEach { Text("• $it", color = C.Text, fontSize = 12.sp) } }
                        if (rp.form.isNotEmpty() || rp.bform.isNotEmpty()) CcmCard {
                            CardTitle("Recent real-world innings (Cricsheet)")
                            rp.form.forEach { Text("${it.d} ${it.f} v ${it.o}: ${it.r}${if (it.no) "*" else ""} (${it.b})", color = C.Text, fontSize = 12.sp) }
                            if (rp.bform.isNotEmpty()) Spacer(Modifier.height(4.dp))
                            rp.bform.forEach { Text("${it.d} ${it.f} v ${it.o}: ${it.w}/${it.r}", color = C.Sub, fontSize = 12.sp) }
                        }
                    }
                }
                else -> {
                    if (gp == null || s == null) CcmCard { Sub("Start a career to track this player's career-mode statistics.") }
                    else CareerModeCard(s, gp, pid)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (bidDialog && s != null && gp != null) BidDialog(vm, s, gp) { bidDialog = false }
    if (swapDialog && s != null && gp != null) SwapDialog(vm, s, gp) { swapDialog = false }
}

/* ----------------------------------------------------------------- Phase 7: profile building blocks */

internal class ProfileAttrs(val tech: Int, val power: Int, val bowl: Int, val field: Int, val keep: Int, val form: Int)

@Composable
private fun CardTitle(text: String) = Text(text.uppercase(), color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 4.dp))

private fun moraleLine(p: GPlayer): String = when {
    p.injured -> "Injured — in the medical room."
    p.form >= 80 && p.morale >= 75 -> "In the form of his life and loving it."
    p.form >= 65 -> "Hitting the ball well in the nets."
    p.morale < 40 -> "Unsettled — a chat or a game could help."
    p.fitness < 60 -> "Needs rest: workload is high."
    else -> "Steady. Ready when called upon."
}

/** Broadcast-style hero header in the team (or national) colours. */
@Composable
private fun ProfileHero(pid: String, name: String, rp: RealPlayer?, gp: GPlayer?, s: CareerSave?, ovr: Int, role: String, country: String,
                        club: Club?, primary: Color, secondary: Color) {
    val (rk, tot) = ratingRank(pid, role, ovr, s)
    Box(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.95f), primary.copy(alpha = 0.45f), Color(0xFF070C1C)), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(1400f, 500f)))
        .border(1.dp, Brush.linearGradient(listOf(secondary.copy(alpha = 0.8f), Color.Transparent)), RoundedCornerShape(18.dp))) {
        // diagonal kit stripes + giant rating watermark
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            for (i in 0..5) {
                val x0 = w * (0.42f + i * 0.09f)
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(x0, 0f); lineTo(x0 + w * 0.035f, 0f); lineTo(x0 - w * 0.12f, h); lineTo(x0 - w * 0.155f, h); close() }
                drawPath(path, secondary.copy(alpha = 0.06f + 0.015f * (i % 2)))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99050A18)), h * 0.55f, h))
        }
        Text("$ovr", color = Color.White.copy(alpha = 0.07f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 170.sp, lineHeight = 170.sp,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-110).dp))
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                PlayerFace(pid, name, 118.dp, secondary)
                club?.let { Box(Modifier.offset(x = 4.dp, y = 4.dp)) { ClubBadge(it, 36.dp) } }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Consts.ROLE_NAMES[role]?.uppercase() ?: role, color = secondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text(name.uppercase(), color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 36.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                rp?.full?.let { if (it != name) Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Consts.nationByCountry(country)?.let { if (it.short in Flags.CODES) { NationFlag(it.short, 24.dp); Spacer(Modifier.width(6.dp)) } }
                    Text(country, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val age = when { gp != null && s != null -> "${gp.age(s.year)}${if (gp.ageEst) "*" else ""} yrs"; rp?.dob != null -> "b. ${rp.dob}"; else -> null }
                    age?.let { Text("  •  $it", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val bat = rp?.bat?.takeIf { it.isNotBlank() }
                    val bowl = rp?.bowl?.takeIf { it.isNotBlank() } ?: gp?.bowlType?.let { if (it == "SPIN") "Spin" else "Pace" }
                    bat?.let { TagChip(it.replace(" bat", ""), Color.White) }
                    bowl?.let { TagChip(it, UI.Cyan) }
                    if (gp?.fictional == true) TagChip("Fictional", UI.Violet)
                    if (gp?.forSale == true) TagChip("Transfer listed", UI.Amber)
                }
                Spacer(Modifier.height(6.dp))
                Text(when {
                    club != null -> "${club.name}${gp?.let { if (it.clubId.isNotEmpty()) "  •  ${Money.fmt(it.salary)}/season, ${it.contractYears} yr" else "" } ?: ""}"
                    gp?.academy == true -> "Academy"; gp != null -> "Free agent"; else -> rp?.ipl?.let { "IPL: $it" } ?: ""
                }, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xCC050A18)).border(3.dp, C.GoldBrush, CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$ovr", color = C.Gold, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp)
                        Text("OVR", color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("#$rk", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp)
                Text("of $tot ${(Consts.ROLE_NAMES[role] ?: role).lowercase()}s", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                if (gp != null && s != null) Text(Money.fmt(Money.value(gp.overall(), gp.age(s.year), s.economy)), color = C.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    rp?.img?.let { Text("Photo: Wikimedia Commons (free licence) • $it", color = C.Sub, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) }
}

/** Six key numbers under the hero: real international totals when known, else career-mode totals. */
@Composable
private fun QuickStats(rp: RealPlayer?, gp: GPlayer?, s: CareerSave?, pid: String) {
    val tiles = mutableListOf<Pair<String, String>>()
    if (rp != null) {
        var m = 0.0; var r = 0.0; var w = 0.0; var h100 = 0.0
        for (fm in listOf("TEST", "ODI", "T20I")) {
            val o = rp.off?.get(fm); val c = rp.stats[fm]
            if (o?.m != null) { m += o.m; r += o.runs ?: 0.0; w += o.wk ?: 0.0; h100 += o.h100 ?: 0.0 } else if (c != null) { m += c.m; r += c.runs; w += c.wk; h100 += c.f100 }
        }
        val ipl = rp.stats["IPL"]
        tiles += "INTL MATCHES" to n(m); tiles += "INTL RUNS" to n(r); tiles += "INTL WICKETS" to n(w); tiles += "HUNDREDS" to n(h100)
        tiles += "IPL RUNS" to (ipl?.runs?.toString() ?: "-"); tiles += "IPL WKTS" to (ipl?.wk?.toString() ?: "-")
    } else if (gp != null && s != null) {
        val all = listOf("T20", "ODI", "TEST").mapNotNull { s.records["ALL|$it"] }
        val inn = all.sumOf { it.bat[pid]?.inn ?: 0 }; val hs = all.maxOfOrNull { it.bat[pid]?.hs ?: 0 } ?: 0
        tiles += "INNINGS" to "$inn"; tiles += "RUNS" to "${gp.careerRuns}"; tiles += "WICKETS" to "${gp.careerWk}"; tiles += "HIGH SCORE" to "$hs"
        tiles += "POTENTIAL" to (if (gp.scouted || gp.clubId == s.userClubId) "${gp.potential}" else "?"); tiles += "AGE" to "${gp.age(s.year)}"
    }
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEach { (k, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.Card).border(1.dp, Color(0x223D5A99), RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 22.sp, maxLines = 1)
                Text(k, color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
            }
        }
    }
}

/** Hexagonal skill radar (technique, power, bowling, fielding, keeping, form). */
@Composable
private fun AttributeRadar(a: ProfileAttrs, role: String, tint: Color, modifier: Modifier) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(800)) }
    val grow = anim.value
    val labels = listOf("TECH", "POWER", "BOWL", "FIELD", "KEEP", "FORM")
    val vals = listOf(a.tech, a.power, a.bowl, a.field, a.keep, a.form).map { (it / 100f).coerceIn(0f, 1f) }
    val raw = listOf(a.tech, a.power, a.bowl, a.field, a.keep, a.form)
    Canvas(modifier) {
        val ctr = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 + 4f)
        val r = minOf(size.width, size.height) * 0.36f
        fun pt(i: Int, k: Float): androidx.compose.ui.geometry.Offset {
            val ang = Math.toRadians(-90.0 + i * 60.0)
            return androidx.compose.ui.geometry.Offset(ctr.x + (kotlin.math.cos(ang) * r * k).toFloat(), ctr.y + (kotlin.math.sin(ang) * r * k).toFloat())
        }
        for (ring in 1..4) {
            val path = androidx.compose.ui.graphics.Path(); for (i in 0..5) { val p = pt(i, ring / 4f); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }; path.close()
            drawPath(path, Color.White.copy(alpha = if (ring == 4) 0.18f else 0.07f), style = Stroke(1.2f))
        }
        for (i in 0..5) drawLine(Color.White.copy(alpha = 0.08f), ctr, pt(i, 1f), 1f)
        val poly = androidx.compose.ui.graphics.Path(); for (i in 0..5) { val p = pt(i, vals[i] * grow); if (i == 0) poly.moveTo(p.x, p.y) else poly.lineTo(p.x, p.y) }; poly.close()
        drawPath(poly, Brush.radialGradient(listOf(C.Gold.copy(alpha = 0.55f), tint.copy(alpha = 0.35f)), ctr, r))
        drawPath(poly, C.GoldLight, style = Stroke(2.5f))
        for (i in 0..5) drawCircle(C.GoldLight, 3.5f, pt(i, vals[i] * grow))
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = 10.sp.toPx(); isFakeBoldText = true }
        val paintV = android.graphics.Paint(paint).apply { textSize = 12.sp.toPx(); color = android.graphics.Color.WHITE }
        for (i in 0..5) {
            val p = pt(i, 1.28f)
            paint.color = android.graphics.Color.argb(255, 154, 172, 207)
            drawContext.canvas.nativeCanvas.drawText(labels[i], p.x, p.y, paint)
            drawContext.canvas.nativeCanvas.drawText("${raw[i]}", p.x, p.y + 13.sp.toPx(), paintV)
        }
    }
}

@Composable
private fun AttrBar(label: String, v: Int, col: Color, max: Int = 99) {
    val f by animateFloatAsState((v / max.toFloat()).coerceIn(0f, 1f), tween(700), label = "attr")
    Column(Modifier.padding(vertical = 3.dp)) {
        Row { Text(label, color = C.Text, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("$v", color = ratingColor(v), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 16.sp) }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x1FFFFFFF))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.55f), col))))
        }
    }
}

private fun ratingColor(v: Int) = when { v >= 85 -> C.Gold; v >= 70 -> C.Positive; v >= 50 -> C.Text; else -> C.Sub }

@Composable
private fun PotentialStars(pot: Int, ovr: Int) {
    val stars = ((pot - 40) / 12f).coerceIn(0.5f, 5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val fill = (stars - (i - 1)).coerceIn(0f, 1f)
            Text("★", color = if (fill >= 0.5f) C.Gold else Color(0x33FFFFFF), fontSize = 16.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text("$pot${if (pot > ovr) "  (+${pot - ovr})" else ""}", color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RingGauge(label: String, v: Int, col: Color) {
    val f by animateFloatAsState((v / 100f).coerceIn(0f, 1f), tween(900), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(Color(0x1FFFFFFF), 135f, 270f, false, style = st)
                drawArc(Brush.sweepGradient(listOf(col.copy(alpha = 0.5f), col, col.copy(alpha = 0.5f))), 135f, 270f * f, false, style = st)
            }
            Text("$v", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Text(label, color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}

/** Last innings as bars (gold = 50+, star = not out) with the bowling figures as dots underneath. */
@Composable
private fun FormChart(rp: RealPlayer, modifier: Modifier) {
    val inns = rp.form.take(10).reversed()
    val bw = rp.bform.take(10).reversed()
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(700)) }
    val grow = anim.value
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val base = h * (if (bw.isNotEmpty()) 0.72f else 0.88f)
        val n = maxOf(inns.size, 1); val slot = w / n; val maxR = maxOf(60, inns.maxOfOrNull { it.r } ?: 0).toFloat()
        drawLine(Color.White.copy(alpha = 0.15f), androidx.compose.ui.geometry.Offset(0f, base), androidx.compose.ui.geometry.Offset(w, base), 1f)
        val y50 = base - (50f / maxR) * (base - 14f)
        drawLine(C.Gold.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, y50), androidx.compose.ui.geometry.Offset(w, y50), 1f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = 10.sp.toPx(); isFakeBoldText = true; color = android.graphics.Color.WHITE }
        inns.forEachIndexed { i, e ->
            val bh = (e.r / maxR) * (base - 14f) * grow
            val x = i * slot + slot * 0.2f; val bwid = slot * 0.6f
            val col = when { e.r >= 100 -> C.GoldLight; e.r >= 50 -> C.Gold; e.r >= 25 -> C.Positive; e.r >= 10 -> C.Blue; else -> C.Red.copy(alpha = 0.8f) }
            drawRoundRect(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.45f)), base - bh, base), androidx.compose.ui.geometry.Offset(x, base - bh), androidx.compose.ui.geometry.Size(bwid, maxOf(bh, 2f)), androidx.compose.ui.geometry.CornerRadius(3f))
            drawContext.canvas.nativeCanvas.drawText("${e.r}${if (e.no) "*" else ""}", x + bwid / 2, base - bh - 3f, paint)
        }
        if (bw.isNotEmpty()) {
            val bslot = w / bw.size
            val p2 = android.graphics.Paint(paint).apply { textSize = 9.sp.toPx(); color = android.graphics.Color.argb(255, 154, 172, 207) }
            bw.forEachIndexed { i, e ->
                val cx = i * bslot + bslot / 2; val cy = h * 0.86f
                val col = if (e.w >= 3) UI.Cyan else if (e.w >= 1) UI.Cyan.copy(alpha = 0.6f) else Color(0x33FFFFFF)
                drawCircle(col, 4f + e.w * 2.2f, androidx.compose.ui.geometry.Offset(cx, cy))
                drawContext.canvas.nativeCanvas.drawText("${e.w}/${e.r}", cx, h - 1f, p2)
            }
        }
    }
    Row { Text("Bars: runs in the last ${minOf(10, rp.form.size)} innings (oldest → latest)", color = C.Sub, fontSize = 9.sp, modifier = Modifier.weight(1f)); if (rp.bform.isNotEmpty()) Text("Dots: wickets", color = UI.Cyan, fontSize = 9.sp) }
}

@Composable
private fun TeamsCard(rp: RealPlayer?, gp: GPlayer?, s: CareerSave?) {
    CcmCard {
        CardTitle("Teams & contract")
        if (gp != null && s != null) {
            KV("Career-mode club", s.clubs[gp.clubId]?.name ?: if (gp.academy) "Academy" else "Free agent")
            if (gp.clubId.isNotEmpty()) KV("Contract", "${Money.fmt(gp.salary)}/season • ${gp.contractYears} yr")
            KV("Market value", Money.fmt(Money.value(gp.overall(), gp.age(s.year), s.economy)))
        }
        val nat = Consts.nationByCountry(gp?.country ?: rp?.country ?: "")
        KV("National team", nat?.name ?: "-")
        if (rp != null) {
            val intlTeams = rp.stats.filterKeys { it != "IPL" }.values.mapNotNull { it.team }.distinct()
            if (intlTeams.isNotEmpty()) KV("International side(s) in data", intlTeams.joinToString())
            KV("IPL franchise (real)", rp.ipl?.let { "$it${rp.iplLast?.let { d -> " • last match $d" } ?: ""}" } ?: "-")
            if (rp.bat.isNotBlank()) KV("Batting", rp.bat)
            if (rp.bowl.isNotBlank()) KV("Bowling", rp.bowl)
            if (rp.cInf) Sub("Uncapped domestic player: country inferred as India from IPL registration.")
        }
        if (gp?.fictional == true) Sub("FICTIONAL player generated by the game (youth intake / scouting).")
    }
}

@Composable
private fun CareerModeCard(s: CareerSave, gp: GPlayer, pid: String) {
    CcmCard {
        CardTitle("Career-mode statistics")
        var any = false
        for (fm in listOf("T20", "ODI", "TEST")) {
            val bk = s.records["ALL|$fm"] ?: continue
            val b = bk.bat[pid]; val w = bk.bowl[pid]
            if (b == null && w == null) continue
            any = true
            Text(fm, color = C.Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth()) {
                if (b != null) Column(Modifier.weight(1f)) {
                    KV("Innings / Runs", "${b.inn} / ${b.runs}"); KV("HS / Avg", "${b.hs}${if (b.hsNo) "*" else ""} / ${f2(b.avg())}")
                    KV("SR", f1(b.sr())); KV("50s / 100s", "${b.f50} / ${b.f100}"); KV("4s / 6s", "${b.fours} / ${b.sixes}")
                }
                if (b != null && w != null) Spacer(Modifier.width(12.dp))
                if (w != null) Column(Modifier.weight(1f)) {
                    KV("Wickets", "${w.wk}"); KV("Avg / Econ", "${f2(w.avg())} / ${f2(w.econ())}")
                    KV("Best", if (w.bbW > 0) "${w.bbW}/${w.bbR}" else "-"); KV("5w", "${w.w5}"); KV("Catches", "${w.ct}")
                }
            }
        }
        if (!any) Sub("No career-mode matches yet.")
        KV("Total career-mode runs / wickets", "${gp.careerRuns} / ${gp.careerWk}")
    }
    if (gp.history.isNotEmpty()) CcmCard {
        CardTitle("Timeline")
        val items = gp.history.takeLast(15).reversed()
        items.forEachIndexed { i, h ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(if (i == 0) C.Gold else C.Blue))
                    if (i < items.size - 1) Box(Modifier.width(2.dp).weight(1f).background(Color(0x333D5A99)))
                }
                Spacer(Modifier.width(6.dp))
                Text(h, color = if (i == 0) C.Text else C.Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun OfficialCard(o: OffStats, src: String?, date: String?) {
    CcmCard(highlight = true) {
        Text("Official career totals", color = C.Gold, fontWeight = FontWeight.Bold)
        Row {
            Column(Modifier.weight(1f)) {
                Text("Batting", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Matches", n(o.m)); KV("Runs", n(o.runs)); KV("Average", n(o.avg)); KV("High score", o.hs ?: "-"); KV("100s / 50s", "${n(o.h100)} / ${n(o.h50)}")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bowling & fielding", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Wickets", n(o.wk)); KV("Average", n(o.bavg)); KV("BBI", o.bb ?: "-"); KV("5w / 10w", "${n(o.w5)} / ${n(o.w10)}"); KV("Catches", n(o.ct) + (o.st?.let { " / ${n(it)} st" } ?: ""))
            }
        }
        Sub("Source: ${src ?: "Wikipedia infobox (ESPNcricinfo)"} • as of ${date ?: "n/a"}")
    }
}

@Composable
private fun DetailedCard(f: String, s: FmtStats, hasOfficial: Boolean) {
    CcmCard {
        Text(if (hasOfficial) "Detailed / partial (Cricsheet ball-by-ball)" else "Statistics (Cricsheet ball-by-ball)", color = C.Gold, fontWeight = FontWeight.Bold)
        Row {
            Column(Modifier.weight(1f)) {
                Text("Batting", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("M / Inn", "${s.m} / ${s.inn}"); KV("Runs", "${s.runs}"); KV("HS", s.hsText); KV("Avg", f2(s.avg)); KV("SR", f1(s.sr))
                KV("50s/100s/200s", "${s.f50}/${s.f100}/${s.f200}"); KV("4s / 6s", "${s.fours} / ${s.sixes}")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bowling", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Wkts", "${s.wk}"); KV("Avg", f2(s.bowlAvg)); KV("Econ", f2(s.econ)); KV("SR", f1(s.bowlSr)); KV("BBI", s.bbText)
                KV("5w / 10w", "${s.w5} / ${s.w10}"); KV("Ct / St", "${s.ct} / ${s.st}")
            }
        }
        Sub("${s.first ?: "?"} → ${s.last ?: "?"}${s.team?.let { " • $it" } ?: ""} • Cricsheet coverage: ${Repo.ds.meta.coverage[f]?.let { "${it.first} → ${it.last}" } ?: "-"}")
        if (hasOfficial) Sub("Cricsheet does not cover every match (esp. pre-2002 and Afghanistan games) — official totals above are authoritative.")
    }
}

@Composable
private fun ActionsCard(vm: GameVM, s: CareerSave, p: GPlayer, onBid: () -> Unit, onSwap: () -> Unit) {
    val mine = p.clubId == s.userClubId
    CcmCard {
        Text("Actions", color = C.Gold, fontWeight = FontWeight.Bold)
        when {
            p.retired -> Sub("Retired.")
            p.academy && s.userClub().academyList.contains(p.id) -> Row {
                GreenButton("Promote") { vm.act { Career.promoteYouth(it, p.id) } }; Spacer(Modifier.width(8.dp)); OutlineBtn("Release") { vm.act { Career.releaseYouth(it, p.id) } }
            }
            mine -> {
                Row {
                    GreenButton("Extra training", Modifier.weight(1f)) { vm.act { Career.extraTraining(it, p.id) } }
                    Spacer(Modifier.width(8.dp))
                    OutlineBtn(if (p.forSale) "Remove from list" else "List for transfer", Modifier.weight(1f)) { p.forSale = !p.forSale; vm.persist(); vm.msg(if (p.forSale) "${p.name} listed — AI clubs may make offers" else "Removed from transfer list") }
                }
                Spacer(Modifier.height(6.dp))
                var confirm by remember { mutableStateOf(false) }
                OutlineBtn("Release (pays 50% of salary)", Modifier.fillMaxWidth()) { confirm = true }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = C.Surface, title = { Text("Release ${p.name}?") },
                    text = { Text("You pay ${Money.fmt(p.salary * 0.5)} compensation and he becomes a free agent.") },
                    confirmButton = { TextButton(onClick = { confirm = false; vm.act { Career.release(it, p.id) }; vm.back() }) { Text("Release", color = C.Red) } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
            }
            p.clubId.isEmpty() -> Row {
                GreenButton("Sign free agent (${Money.fmt(maxOf(0.2 * s.economy, Money.value(p.overall(), p.age(s.year), s.economy)))}/yr)", Modifier.weight(1f)) { vm.act { Career.signFreeAgent(it, p.id) } }
            }
            else -> Row {
                GreenButton("Make transfer bid", Modifier.weight(1f)) { onBid() }
                Spacer(Modifier.width(8.dp))
                OutlineBtn("Propose swap", Modifier.weight(1f)) { onSwap() }
            }
        }
        if (!p.scouted && !mine && !p.retired) { Spacer(Modifier.height(6.dp)); OutlineBtn("Scout potential (${Money.fmt(0.1 * s.economy)})", Modifier.fillMaxWidth()) { vm.act { Career.scoutPlayer(it, p.id) } } }
    }
}

@Composable
private fun BidDialog(vm: GameVM, s: CareerSave, p: GPlayer, onClose: () -> Unit) {
    val ask = Career.askingPrice(s, p).coerceAtLeast(0.2)
    var fee by remember { mutableFloatStateOf(ask.toFloat()) }
    AlertDialog(onDismissRequest = onClose, containerColor = C.Surface, title = { Text("Bid for ${p.name}") },
        text = {
            Column {
                Sub("Club: ${s.clubs[p.clubId]?.name} • valuation ≈ ${Money.fmt(ask)} • his salary ${Money.fmt(p.salary)}")
                Sub("Your balance ${Money.fmt(s.userClub().balance)} • cap room ${Money.fmt(Career.purse(s, s.userClub()))}")
                Spacer(Modifier.height(8.dp))
                Text("Offer: ${Money.fmt(fee.toDouble())}", color = C.Gold, fontWeight = FontWeight.Bold)
                Slider(fee, { fee = (Math.round(it * 20) / 20f) }, valueRange = (ask * 0.4).toFloat()..(ask * 2).toFloat())
            }
        },
        confirmButton = { TextButton(onClick = { vm.act { Career.bidForPlayer(it, p.id, fee.toDouble()) }; onClose() }) { Text("Submit bid", color = C.Gold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun SwapDialog(vm: GameVM, s: CareerSave, p: GPlayer, onClose: () -> Unit) {
    val mine = s.userClub().squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() }
    AlertDialog(onDismissRequest = onClose, containerColor = C.Surface, title = { Text("Swap for ${p.name} (${p.overall()})") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Sub("Choose one of your players to offer in exchange. AI clubs accept if the value is fair and it fits their needs.")
                for (m in mine) TextButton(onClick = { vm.act { Career.proposeSwap(it, m.id, p.id) }; onClose() }) {
                    Text("${m.name} • ${m.role} • ${m.overall()}", color = C.Text, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
