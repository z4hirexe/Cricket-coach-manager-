package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.render3d.*
import com.cricketcoach.manager.ui.*
import kotlin.math.pow

/* ============================================================================================
 *  Phase C — profile 3D panel: the player's own model (build / height / skin / hair / facial hair / shirt number / kit)
 *  with animation selector.  Filament viewer when available, a matching 2D kit figure otherwise.
 * ============================================================================================ */

private fun srgb(l: FloatArray, k: Float = 1f) = Color((l[0] * k).coerceIn(0f, 1f).pow(1 / 2.2f), (l[1] * k).coerceIn(0f, 1f).pow(1 / 2.2f), (l[2] * k).coerceIn(0f, 1f).pow(1 / 2.2f))

/** Default animation for a role. */
fun defaultShowcase(role: String, bowlSpin: Boolean): Showcase = when (role) {
    "BOWL" -> if (bowlSpin) Showcase.BOWL_SPIN else Showcase.BOWL_FAST
    "WK" -> Showcase.KEEP
    else -> Showcase.BAT
}

@Composable
fun Player3DPanel(
    vm: GameVM, pid: String, name: String, country: String, role: String, bowlSpin: Boolean,
    primary: Long, secondary: Long, teamName: String, startIn3d: Boolean = false,
) {
    val look = remember(pid, country) { Look.of(pid, country) }
    var sc by remember(pid) { mutableStateOf(defaultShowcase(role, bowlSpin)) }
    var live by remember(pid) { mutableStateOf(startIn3d && vm.settings.gfx > 0) }
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(304.dp).padding(vertical = 4.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF0E1E45), Color(0xFF060D22)))).border(1.dp, Color(0x66E9B949), shape)) {
        // ---------------- viewer
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (live) {
                Player3DViewer(pid, country, primary, secondary, false, vm.settings.gfx, sc, Modifier.fillMaxSize()) {
                    KitFigure(look, Color(primary), Color(secondary), sc.gear, Modifier.fillMaxSize().padding(10.dp))
                }
            } else {
                KitFigure(look, Color(primary), Color(secondary), sc.gear, Modifier.fillMaxSize().padding(10.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(9.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFE7A6), Color(0xFFE9B949), Color(0xFFB8862B))))
                    .clickable {
                        GameAudio.click()
                        if (vm.settings.gfx == 0) vm.msg("Set Graphics to Medium or High in Settings for the 3D model") else live = true
                    }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text("▶ VIEW 3D MODEL", color = Color(0xFF1A1205), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
            Text(if (live) "Drag to rotate · ${sc.label}" else sc.label.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            Text(if (look.isCurated) "Approximate look from public style descriptors · not a scan" else "Fictional generated look",
                color = Color.White.copy(alpha = 0.55f), fontSize = 8.5.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        // ---------------- details + animation selector
        Column(Modifier.width(268.dp).fillMaxHeight().background(Color(0x66050A18)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3D MODEL", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                val badge = if (look.isCurated) "APPROX. LOOK" else "FICTIONAL"
                val bc = if (look.isCurated) UI.Cyan else C.Sub
                Box(Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, bc.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(badge, color = bc, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(srgb(look.skin)).border(1.dp, Color(0x55FFFFFF), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("#${look.number} · $teamName", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spec("Build", look.build.name.lowercase().replaceFirstChar { it.uppercase() } + " · ~${look.heightCm} cm")
            Spec("Hair", Hair.NAMES[look.hair] + if (look.hair != Hair.NONE) " · " + Look.HAIR_NAMES[look.hairColIndex].lowercase() else "")
            Spec("Face", Facial.NAMES[look.facial])
            Spec("Kit", when (sc.gear) { Gear.BATTING -> "Helmet, pads, gloves, bat"; Gear.KEEPING -> "Keeper gloves, pads, cap"; else -> "Shirt #${look.number}, cap, spikes" })
            Spacer(Modifier.height(6.dp))
            Text("ANIMATIONS", color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            val all = Showcase.values().toList()
            for (row in all.chunked(4)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (o in row) AnimChip(o.label, o == sc, Modifier.weight(1f)) { GameAudio.click(); sc = o }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Spec(k: String, v: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text(k.uppercase(), color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.width(42.dp))
        Text(v, color = C.Text, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnimChip(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(30.dp).padding(horizontal = 0.dp).clip(RoundedCornerShape(7.dp))
        .background(if (on) Brush.horizontalGradient(listOf(Color(0xFFFFE7A6), Color(0xFFE9B949))) else Brush.horizontalGradient(listOf(Color(0xFF13234A), Color(0xFF0D1A3A))))
        .border(1.dp, if (on) Color(0xFFFFE7A6) else Color(0x44E9B949), RoundedCornerShape(7.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Color(0xFF1A1205) else C.Text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * 2D twin of the 3D model (used before the viewer starts, on Low graphics and in screenshots): same build, height,
 * skin tone, hair style/colour, facial hair, shirt number and gear.
 */
@Composable
fun KitFigure(look: Look, primary: Color, secondary: Color, gear: Gear, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val hs = (h * 0.9f).coerceAtMost(w * 1.7f) * (look.heightCm / 200f)   // figure height in px, taller players stand taller
        val ground = h * 0.95f
        val top = ground - hs
        val cx = w / 2
        val bw = when (look.build) { Build.SLIM -> 0.90f; Build.HEAVY -> 1.16f; else -> 1f }
        val skin = srgb(look.skin); val skinD = srgb(look.skin, 0.8f); val hair = srgb(look.hairCol, 1.4f)
        val u = hs / 1.8f                       // 1 model metre in px
        fun y(m: Float) = ground - m * u
        // shadow + turntable
        drawOval(Color(0x55000000), Offset(cx - 0.42f * u, ground - 0.05f * u), Size(0.84f * u, 0.1f * u))
        drawOval(Color(0x66E9B949), Offset(cx - 0.55f * u, ground - 0.07f * u), Size(1.1f * u, 0.14f * u), style = Stroke(1.5f))
        // legs: trousers (or whites) + pads
        val trousers = primary.copy(red = primary.red * 0.45f, green = primary.green * 0.45f, blue = primary.blue * 0.45f)
        for (s in listOf(-1f, 1f)) {
            val lx = cx + s * 0.1f * u * bw
            drawRoundRect(trousers, Offset(lx - 0.065f * u * bw, y(0.95f)), Size(0.13f * u * bw, 0.88f * u), CornerRadius(0.05f * u))
            if (gear == Gear.BATTING || gear == Gear.KEEPING) {
                drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFD5DBE7))), Offset(lx - 0.08f * u, y(0.62f)), Size(0.16f * u, 0.56f * u), CornerRadius(0.04f * u))
                for (k in -1..1) drawLine(Color(0xFFB7C1D6), Offset(lx + k * 0.035f * u, y(0.6f)), Offset(lx + k * 0.035f * u, y(0.1f)), 2f)
            }
            drawRoundRect(Color(0xFFEDEFF4), Offset(lx - 0.075f * u, y(0.07f)), Size(0.17f * u, 0.07f * u), CornerRadius(0.03f * u))
        }
        // torso
        val sh = 0.21f * u * bw
        val torso = Path().apply {
            moveTo(cx - sh, y(1.44f)); quadraticBezierTo(cx, y(1.49f), cx + sh, y(1.44f))
            lineTo(cx + 0.17f * u * bw, y(0.93f)); lineTo(cx - 0.17f * u * bw, y(0.93f)); close()
        }
        drawPath(torso, Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.92f)), y(1.5f), y(0.9f)))
        drawRect(secondary, Offset(cx - 0.17f * u * bw, y(1.30f)), Size(0.34f * u * bw, 0.025f * u))
        drawArc(secondary, 0f, 180f, false, Offset(cx - 0.06f * u, y(1.49f)), Size(0.12f * u, 0.06f * u), style = Stroke(3f))
        // shirt number on the chest
        drawContext.canvas.nativeCanvas.drawText(look.number.toString(), cx, y(1.12f), android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, (secondary.red * 255).toInt(), (secondary.green * 255).toInt(), (secondary.blue * 255).toInt())
            textSize = 0.16f * u; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true; isAntiAlias = true
        })
        // arms
        for (s in listOf(-1f, 1f)) {
            val sx = cx + s * sh
            drawLine(primary, Offset(sx, y(1.40f)), Offset(sx + s * 0.08f * u, y(1.16f)), 0.11f * u * bw)
            drawLine(skin, Offset(sx + s * 0.08f * u, y(1.16f)), Offset(sx + s * 0.09f * u, y(0.90f)), 0.075f * u * bw)
            val glove = gear == Gear.BATTING || gear == Gear.KEEPING
            drawCircle(if (glove) Color(0xFFF4F5F8) else skin, (if (gear == Gear.KEEPING) 0.07f else 0.05f) * u, Offset(sx + s * 0.09f * u, y(0.86f)))
        }
        if (gear == Gear.BATTING) {
            val hx = cx + sh + 0.09f * u; val hy = y(0.86f)
            drawRoundRect(Color(0xFF7A2E2E), Offset(hx - 0.015f * u, hy - 0.05f * u), Size(0.03f * u, 0.2f * u), CornerRadius(4f))
            drawRoundRect(Color(0xFFE2C48E), Offset(hx - 0.05f * u, hy + 0.14f * u), Size(0.1f * u, 0.55f * u), CornerRadius(6f))
        }
        // head
        drawRect(skinD, Offset(cx - 0.045f * u, y(1.6f)), Size(0.09f * u, 0.14f * u))
        val hc = Offset(cx, y(1.70f)); val hr = 0.105f * u
        drawOval(skin, Offset(hc.x - hr * 0.86f, hc.y - hr), Size(hr * 1.72f, hr * 2.05f))
        drawHair(look, hc, hr, hair, gear)
        drawFacial(look, hc, hr, hair, skin)
        // eyes
        for (s in listOf(-1f, 1f)) drawCircle(Color(0xFF1A120C), hr * 0.08f, Offset(hc.x + s * hr * 0.33f, hc.y - hr * 0.05f))
        // headgear
        when {
            gear == Gear.BATTING -> {
                drawArc(primary.copy(red = primary.red * 0.6f, green = primary.green * 0.6f, blue = primary.blue * 0.6f), 180f, 180f, true, Offset(hc.x - hr * 1.08f, hc.y - hr * 1.18f), Size(hr * 2.16f, hr * 1.9f))
                for (k in 0..2) drawLine(Color(0xFFC8CCD2), Offset(hc.x - hr * 0.85f, hc.y + hr * (0.15f + k * 0.28f)), Offset(hc.x + hr * 0.85f, hc.y + hr * (0.15f + k * 0.28f)), 2.5f)
            }
            gear == Gear.KEEPING -> drawArc(primary, 180f, 180f, true, Offset(hc.x - hr * 0.98f, hc.y - hr * 1.12f), Size(hr * 1.96f, hr * 1.5f))
        }
    }
}

private fun DrawScope.drawHair(look: Look, c: Offset, r: Float, col: Color, gear: Gear) {
    if (gear == Gear.BATTING && look.hair != Hair.LONG) return
    when (look.hair) {
        Hair.BUZZ -> drawArc(col.copy(alpha = 0.75f), 190f, 160f, true, Offset(c.x - r * 0.9f, c.y - r * 1.05f), Size(r * 1.8f, r * 1.3f))
        Hair.SHORT -> drawArc(col, 185f, 170f, true, Offset(c.x - r * 0.95f, c.y - r * 1.12f), Size(r * 1.9f, r * 1.45f))
        Hair.LONG -> { drawArc(col, 180f, 180f, true, Offset(c.x - r * 0.98f, c.y - r * 1.15f), Size(r * 1.96f, r * 1.5f)); drawRect(col, Offset(c.x - r * 0.98f, c.y - r * 0.4f), Size(r * 0.25f, r * 1.1f)); drawRect(col, Offset(c.x + r * 0.73f, c.y - r * 0.4f), Size(r * 0.25f, r * 1.1f)) }
        Hair.CURLY -> for (k in 0..6) { val a = Math.toRadians(190.0 + k * 26.6); drawCircle(col, r * 0.33f, Offset(c.x + (kotlin.math.cos(a) * r * 0.82f).toFloat(), c.y - r * 0.25f + (kotlin.math.sin(a) * r * 0.85f).toFloat())) }
        Hair.BUN -> { drawArc(col, 185f, 170f, true, Offset(c.x - r * 0.95f, c.y - r * 1.12f), Size(r * 1.9f, r * 1.45f)); drawCircle(col, r * 0.32f, Offset(c.x, c.y - r * 1.15f)) }
        else -> {}
    }
}

private fun DrawScope.drawFacial(look: Look, c: Offset, r: Float, col: Color, skin: Color) {
    when (look.facial) {
        Facial.BEARD -> drawArc(col, 10f, 160f, true, Offset(c.x - r * 0.84f, c.y - r * 0.55f), Size(r * 1.68f, r * 1.6f))
        Facial.STUBBLE -> drawArc(col.copy(alpha = 0.35f), 10f, 160f, true, Offset(c.x - r * 0.82f, c.y - r * 0.5f), Size(r * 1.64f, r * 1.5f))
        Facial.GOATEE -> { drawOval(col, Offset(c.x - r * 0.25f, c.y + r * 0.55f), Size(r * 0.5f, r * 0.42f)); drawRect(col, Offset(c.x - r * 0.3f, c.y + r * 0.3f), Size(r * 0.6f, r * 0.09f)) }
        Facial.MOUSTACHE -> drawRect(col, Offset(c.x - r * 0.32f, c.y + r * 0.3f), Size(r * 0.64f, r * 0.1f))
        else -> {}
    }
    if (look.facial == Facial.BEARD) drawOval(skin, Offset(c.x - r * 0.2f, c.y + r * 0.35f), Size(r * 0.4f, r * 0.14f))   // mouth gap
}
