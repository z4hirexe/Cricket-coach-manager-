package com.cricketcoach.manager.ui

import androidx.compose.runtime.remember

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.cricketcoach.manager.audio.GameAudio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.cricketcoach.manager.game.Club
import com.cricketcoach.manager.game.Consts
import com.cricketcoach.manager.game.GPlayer
import kotlin.math.cos
import kotlin.math.sin

/** Condensed broadcast display face (Barlow Condensed, SIL Open Font Licence) for heroes, tickers and big numbers. */
val Display = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.cricketcoach.manager.R.font.barlow_condensed_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.cricketcoach.manager.R.font.barlow_condensed_semibold, FontWeight.SemiBold)
)

object C {
    // Cricket Manager Pro: dark navy + metallic gold
    val Bg = Color(0xFF050A18)
    val Surface = Color(0xFF0A1530)
    val Card = Color(0xFF0F1D3D)
    val CardHi = Color(0xFF172B57)
    val Green = Color(0xFF2D6BFF)      // primary action (royal blue)
    val Positive = Color(0xFF43C66B)
    val Grass = Color(0xFF2E7D32)
    val Gold = Color(0xFFE9B949)
    val GoldLight = Color(0xFFFFE7A6)
    val GoldDeep = Color(0xFFB8862B)
    val Text = Color(0xFFEEF2FA)
    val Sub = Color(0xFF9AACCF)
    val Red = Color(0xFFEF5350)
    val Blue = Color(0xFF5B9BFF)
    val GoldBrush get() = Brush.verticalGradient(listOf(GoldLight, Gold, GoldDeep))
    val BlueBrush get() = Brush.verticalGradient(listOf(Color(0xFF4D86FF), Color(0xFF2D6BFF), Color(0xFF1A3F99)))
}

private val scheme = darkColorScheme(
    primary = C.Gold, onPrimary = Color.Black, secondary = C.Blue, onSecondary = Color.Black,
    background = C.Bg, onBackground = C.Text, surface = C.Surface, onSurface = C.Text,
    surfaceVariant = C.Card, onSurfaceVariant = C.Sub, error = C.Red
)

@Composable
fun CcmTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(vm: GameVM, title: String, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    if (LocalInShell.current) {
        Column(Modifier.fillMaxSize()) {
            ShellTitleRow(vm, title, actions)
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
        return
    }
    Scaffold(
        containerColor = C.Bg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (landscape) {
                Row(Modifier.fillMaxWidth().height(44.dp).background(Brush.horizontalGradient(listOf(Color(0xFF0D1C40), C.Surface, Color(0xFF0D1C40))))
                    .drawBehind { drawLine(Brush.horizontalGradient(listOf(Color.Transparent, C.Gold, Color.Transparent)), androidx.compose.ui.geometry.Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Offset(size.width, size.height - 1f), 2f) }
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.stack.size > 1) IconButton(onClick = { GameAudio.click(); vm.back() }, modifier = Modifier.size(38.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
                    Image(painterResource(com.cricketcoach.manager.R.drawable.logo_cmp), null, Modifier.size(34.dp).padding(start = 2.dp))
                    Text(title.uppercase(), color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    actions()
                    if (vm.stack.size > 2) IconButton(onClick = { GameAudio.click(); vm.goHome() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Filled.Home, "Home", tint = C.Text) }
                }
            } else TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (vm.stack.size > 1) IconButton(onClick = { GameAudio.click(); vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    actions()
                    if (vm.stack.size > 2) IconButton(onClick = { GameAudio.click(); vm.goHome() }) { Icon(Icons.Filled.Home, "Home") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.Surface, titleContentColor = C.Gold, navigationIconContentColor = C.Text, actionIconContentColor = C.Text)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Image(painterResource(com.cricketcoach.manager.R.drawable.stadium_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.16f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC050A18), Color(0xF2050A18)))))
            Column(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
fun CcmCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().padding(vertical = 4.dp)
    val colors = CardDefaults.cardColors(containerColor = (if (highlight) C.CardHi else C.Card).copy(alpha = 0.92f))
    val border = if (highlight) BorderStroke(1.dp, C.Gold.copy(alpha = 0.7f)) else BorderStroke(1.dp, Color(0x223D5A99))
    if (onClick != null) Card(onClick = onClick, modifier = m, colors = colors, border = border, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp), content = content) }
    else Card(modifier = m, colors = colors, border = border, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp), content = content) }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = { GameAudio.click(); onClick() }, enabled = enabled, modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) C.GoldBrush else SolidColor(Color(0x33E9B949))), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF1A1200), disabledContainerColor = Color.Transparent)) {
        Text(text.uppercase(), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun GreenButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = { GameAudio.click(); onClick() }, enabled = enabled, modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) C.BlueBrush else SolidColor(Color(0x332D6BFF))), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Color.Transparent)) {
        Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = { GameAudio.click(); onClick() }, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, C.Gold.copy(alpha = if (enabled) 0.8f else 0.3f))) {
        Text(text, color = if (enabled) C.Gold else C.Sub, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 2.sp, modifier = modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, size: Int = 12) = Text(text, color = C.Sub, fontSize = size.sp, modifier = modifier)

@Composable
fun KV(label: String, value: String, valueColor: Color = C.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
fun RatingPill(r: Int, label: String? = null) {
    val col = when { r >= 88 -> C.Gold; r >= 80 -> C.Green; r >= 70 -> C.Blue; r >= 60 -> Color(0xFF90A4AE); else -> Color(0xFF607D8B) }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text((label ?: "") + "$r", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun Chip(text: String, color: Color = C.CardHi, textColor: Color = C.Text) {
    Box(Modifier.padding(end = 4.dp, bottom = 2.dp).clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = textColor, fontSize = 11.sp)
    }
}

@Composable
fun StatBar(label: String, value: Int, max: Int = 99) {
    val anim by animateFloatAsState(targetValue = value / max.toFloat(), animationSpec = tween(700), label = "bar")
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row { Text(label, color = C.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("$value", color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E3D31))) {
            Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(C.Green, if (value >= 85) C.Gold else C.Green))))
        }
    }
}

@Composable
fun Tabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selected, containerColor = C.Surface, contentColor = C.Gold, edgePadding = 8.dp) {
        tabs.forEachIndexed { i, t -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(t, color = if (i == selected) C.Gold else C.Sub) }) }
    }
}

/** Original in-game badge artwork drawn in code (no official logos). */
@Composable
fun Badge(short: String, primary: Long, secondary: Long, logo: Int = 0, size: Dp = 44.dp) {
    val p = Color(primary); val s = Color(secondary)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            when (logo % 8) {
                0 -> { // shield
                    val path = Path().apply { moveTo(w * 0.1f, h * 0.08f); lineTo(w * 0.9f, h * 0.08f); lineTo(w * 0.9f, h * 0.5f); quadraticBezierTo(w * 0.9f, h * 0.85f, w * 0.5f, h * 0.98f); quadraticBezierTo(w * 0.1f, h * 0.85f, w * 0.1f, h * 0.5f); close() }
                    drawPath(path, p); drawPath(path, s, style = Stroke(w * 0.07f))
                }
                1 -> { drawCircle(s, w / 2); drawCircle(p, w * 0.42f) }
                2 -> { // star
                    val path = Path(); val cx = w / 2; val cy = h / 2
                    for (i in 0 until 10) { val r = if (i % 2 == 0) w * 0.5f else w * 0.24f; val a = Math.toRadians((i * 36 - 90).toDouble()); val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat(); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.close(); drawPath(path, p); drawPath(path, s, style = Stroke(w * 0.05f))
                }
                3 -> { drawCircle(p, w / 2); drawLine(s, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), w * 0.12f); drawLine(s, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), w * 0.12f) }
                4 -> { drawCircle(p, w / 2); drawArc(s, -60f, 120f, false, topLeft = Offset(w * 0.15f, h * 0.05f), size = Size(w * 0.7f, h * 0.9f), style = Stroke(w * 0.05f)) }
                5 -> { // crown
                    drawRoundRect(p, size = this.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
                    val path = Path().apply { moveTo(w * 0.15f, h * 0.75f); lineTo(w * 0.15f, h * 0.3f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.5f, h * 0.22f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.85f, h * 0.3f); lineTo(w * 0.85f, h * 0.75f); close() }
                    drawPath(path, s.copy(alpha = 0.55f))
                }
                6 -> { // hexagon
                    val path = Path(); for (i in 0 until 6) { val a = Math.toRadians((i * 60 - 90).toDouble()); val x = w / 2 + w / 2 * cos(a).toFloat(); val y = h / 2 + h / 2 * sin(a).toFloat(); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.close(); drawPath(path, p); drawPath(path, s, style = Stroke(w * 0.06f))
                }
                else -> { // wicket
                    drawCircle(p, w / 2)
                    for (i in 0..2) drawLine(s.copy(alpha = 0.5f), Offset(w * (0.35f + i * 0.15f), h * 0.18f), Offset(w * (0.35f + i * 0.15f), h * 0.82f), w * 0.06f)
                }
            }
        }
        Text(short.take(4), color = if (Color(primary).luminanceApprox() > 0.6f) Color.Black else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 3.6).sp)
    }
}

fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
fun ClubBadge(c: Club?, size: Dp = 44.dp) {
    if (c == null) { Box(Modifier.size(size).clip(CircleShape).background(C.CardHi)); return }
    val custom = remember(c.logoFile) { if (c.logoFile.isBlank()) null else runCatching { android.graphics.BitmapFactory.decodeFile(c.logoFile)?.asImageBitmap() }.getOrNull() }
    if (custom != null) Image(custom, c.name, Modifier.size(size).clip(CircleShape).border(2.dp, Color(c.secondary), CircleShape), contentScale = ContentScale.Crop)
    else Badge(c.short, c.primary, c.secondary, c.logo, size)
}

@Composable
fun JerseyPreview(primary: Long, secondary: Long, style: Int, size: Dp = 90.dp) {
    val p = Color(primary); val s = Color(secondary)
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.3f, h * 0.08f); lineTo(w * 0.42f, h * 0.14f); lineTo(w * 0.58f, h * 0.14f); lineTo(w * 0.7f, h * 0.08f)
            lineTo(w * 0.95f, h * 0.25f); lineTo(w * 0.85f, h * 0.42f); lineTo(w * 0.75f, h * 0.36f); lineTo(w * 0.75f, h * 0.95f)
            lineTo(w * 0.25f, h * 0.95f); lineTo(w * 0.25f, h * 0.36f); lineTo(w * 0.15f, h * 0.42f); lineTo(w * 0.05f, h * 0.25f); close()
        }
        drawPath(body, p)
        when (style % 6) {
            1 -> drawLine(s, Offset(w * 0.25f, h * 0.3f), Offset(w * 0.75f, h * 0.85f), w * 0.1f)
            2 -> for (i in 0..3) drawLine(s, Offset(w * (0.32f + i * 0.12f), h * 0.2f), Offset(w * (0.32f + i * 0.12f), h * 0.95f), w * 0.035f)
            3 -> drawRect(s, topLeft = Offset(w * 0.5f, h * 0.14f), size = Size(w * 0.25f, h * 0.81f))
            4 -> for (i in 0..2) drawRect(s, topLeft = Offset(w * 0.25f, h * (0.4f + i * 0.18f)), size = Size(w * 0.5f, h * 0.06f))
            5 -> { drawLine(s, Offset(w * 0.25f, h * 0.45f), Offset(w * 0.5f, h * 0.62f), w * 0.07f); drawLine(s, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.75f, h * 0.45f), w * 0.07f) }
        }
        drawPath(body, s, style = Stroke(w * 0.025f))
    }
}

@Composable
fun StadiumGraphic(capacity: Int, primary: Long, modifier: Modifier = Modifier) {
    val tiers = (capacity / 20000).coerceIn(1, 4)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val w = size.width; val h = size.height
        for (t in tiers downTo 1) {
            val inset = (tiers - t) * 10f
            drawOval(Color(primary).copy(alpha = 0.25f + t * 0.12f), topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2))
        }
        val gi = tiers * 10f + 8f
        drawOval(Brush.radialGradient(listOf(Color(0xFF43A047), Color(0xFF1B5E20))), topLeft = Offset(gi, gi), size = Size(w - gi * 2, h - gi * 2))
        drawOval(Color.White.copy(alpha = 0.3f), topLeft = Offset(w * 0.3f, h * 0.25f), size = Size(w * 0.4f, h * 0.5f), style = Stroke(2f))
        drawRect(Color(0xFFD7CCC8), topLeft = Offset(w * 0.47f, h * 0.36f), size = Size(w * 0.06f, h * 0.28f))
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 + 30)
            drawCircle(C.Gold.copy(alpha = 0.8f), 4f, Offset(w / 2 + (w / 2 - 6) * cos(a).toFloat(), h / 2 + (h / 2 - 6) * sin(a).toFloat()))
        }
    }
}

@Composable
fun PlayerRow(p: GPlayer, year: Int, onClick: () -> Unit, extra: String? = null, trailing: @Composable (() -> Unit)? = null, highlight: Boolean = false) {
    CcmCard(onClick = onClick, highlight = highlight) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerFace(p.id, p.name, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Sub("${Consts.ROLE_NAMES[p.role]} • ${p.country} • ${p.age(year)}${if (p.ageEst) "*" else ""}y${if (p.fictional) " • Fictional" else ""}")
                Row {
                    if (p.injured) Chip("Injured ${p.injuryDays}d", Color(0xFF5D1F1F))
                    if (p.forSale) Chip("For sale", Color(0xFF4E3B00))
                    if (extra != null) Sub(extra)
                }
            }
            if (trailing != null) trailing() else RatingPill(p.overall())
        }
    }
}

@Composable
fun TableHeader(cols: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth().background(C.Surface).padding(vertical = 6.dp, horizontal = 6.dp)) {
        cols.forEach { (t, w) -> Text(t, color = C.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w), maxLines = 1) }
    }
}

@Composable
fun TableRowView(cols: List<Pair<String, Float>>, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    val m = Modifier.fillMaxWidth().background(if (highlight) C.CardHi else Color.Transparent)
    Row((if (onClick != null) m.clickable { onClick() } else m).padding(vertical = 6.dp, horizontal = 6.dp)) {
        cols.forEach { (t, w) -> Text(t, color = if (highlight) C.Gold else C.Text, fontSize = 12.sp, modifier = Modifier.weight(w), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    HorizontalDivider(color = Color(0xFF1E3D31))
}

fun f1(d: Double?): String = d?.let { "%.1f".format(it) } ?: "-"
fun f2(d: Double?): String = d?.let { "%.2f".format(it) } ?: "-"
