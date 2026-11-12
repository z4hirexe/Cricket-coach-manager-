package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.Caster
import com.cricketcoach.manager.game.ComLine
import com.cricketcoach.manager.ui.*

private fun initials(n: String) = n.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

/** Ball-outcome chip: W / 4 / 6 / • / 1 / 2 / wd / nb. */
@Composable
private fun BallChip(l: ComLine) {
    val (txt, bg, fg) = when (l.kind) {
        "WICKET" -> Triple("W", Color(0xFFEF4444), Color.White)
        "SIX" -> Triple("6", Color(0xFFA855F7), Color.White)
        "FOUR" -> Triple("4", Color(0xFF3B82F6), Color.White)
        "WIDE" -> Triple("wd", Color(0xFF334155), Color(0xFFE2E8F0))
        "NB" -> Triple("nb", Color(0xFF334155), Color(0xFFE2E8F0))
        "DOT", "APPEAL" -> Triple("•", Color(0xFF1E293B), Color(0xFF94A3B8))
        else -> Triple(l.runs.coerceAtLeast(1).toString(), Color(0xFF14532D), Color(0xFFBBF7D0))
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(txt, color = fg, fontWeight = FontWeight.Black, fontSize = if (txt.length > 1) 10.sp else 13.sp)
    }
}

@Composable
private fun CasterAvatar(c: Caster?, size: Int = 22) {
    val col = Color(c?.color ?: 0xFF64748B)
    Box(Modifier.size(size.dp).clip(CircleShape).background(col.copy(alpha = 0.22f)).border(1.5.dp, col, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials(c?.name ?: "?"), color = col, fontWeight = FontWeight.Black, fontSize = (size * 0.4f).sp)
    }
}

/** Little animated "on air" level meter. */
@Composable
private fun OnAirMeter(color: Color) {
    val tr = rememberInfiniteTransition(label = "air")
    val ph by tr.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
    Canvas(Modifier.size(18.dp, 14.dp)) {
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(ph + i * 1.3f)))
            drawRoundRect(color, Offset(i * size.width / 4f + 1f, size.height - h), Size(size.width / 4f - 2f, h), CornerRadius(2f))
        }
    }
}

/** Tag colours for special calls. */
private fun tagColor(tag: String): Color = when {
    tag.contains("HAT") -> Color(0xFFF43F5E)
    tag == "HUNDRED" || tag.all { it.isDigit() } -> Color(0xFFFACC15)
    tag == "FIFTY" -> Color(0xFFFBBF24)
    tag.contains("FOR") -> Color(0xFF22D3EE)
    tag.contains("MAIDEN") -> Color(0xFF34D399)
    tag == "PARTNERSHIP" -> Color(0xFF60A5FA)
    tag == "COLLAPSE" || tag == "BIG OVER" -> Color(0xFFF97316)
    tag == "WIN" || tag == "RESULT" -> Color(0xFF4ADE80)
    else -> Color(0xFFA78BFA)
}

/** Live commentary feed (newest first): on-air booth, ball chips, speaker avatars, special-call badges, end-of-over cards. */
@Composable
fun CommentaryFeed(vm: GameVM) {
    val booth = vm.booth()
    Column(Modifier.fillMaxSize()) {
        // on-air strip
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("ON AIR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFDC2626)).padding(horizontal = 6.dp, vertical = 2.dp))
            Spacer(Modifier.width(6.dp)); OnAirMeter(Color(0xFFF87171)); Spacer(Modifier.width(10.dp))
            booth.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.width(12.dp))
                CasterAvatar(c, 24); Spacer(Modifier.width(6.dp))
                Column {
                    Text(c.name, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(c.role.uppercase(), color = Color(c.color), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(vm.comLines.take(260), key = { it.key }) { l -> ComRow(l, booth) }
        }
    }
}

@Composable
private fun ComRow(l: ComLine, booth: List<Caster>) {
    when (l.kind) {
        "OVER" -> Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0C4A6E), Color(0xFF111827)))).border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(l.text, color = Color(0xFF7DD3FC), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            l.detail.split('\n').forEach { Text(it, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
        "INTRO", "INNINGS", "RESULT" -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (l.kind == "RESULT") Color(0xFF14532D) else Color(0xFF1F2937)).padding(horizontal = 10.dp, vertical = 7.dp)) {
            if (l.tag.isNotEmpty()) Text(l.tag, color = if (l.kind == "RESULT") Color(0xFF86EFAC) else C.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (l.kind != "RESULT") { CasterAvatar(booth.getOrNull(l.speaker), 20); Spacer(Modifier.width(8.dp)) }
                Text(l.text, color = Color.White, fontSize = 12.sp, fontWeight = if (l.kind == "RESULT") FontWeight.Black else FontWeight.Medium)
            }
            if (l.detail.isNotEmpty()) Text(l.detail, color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        "EXPERT", "CALL" -> Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
            val c = booth.getOrNull(l.speaker); val col = Color(c?.color ?: 0xFF94A3B8)
            CasterAvatar(c, 20); Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 10.dp))
                .background(if (l.kind == "EXPERT") col.copy(alpha = 0.10f) else Color(0xFF111827)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                if (l.tag.isNotEmpty()) {
                    val tc = tagColor(l.tag)
                    Text(l.tag, color = Color(0xFF0B0F19), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 3.dp).clip(RoundedCornerShape(4.dp)).background(tc).padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Text(l.text, color = if (l.kind == "EXPERT") Color(0xFFE2E8F0) else Color.White, fontSize = 12.sp,
                    fontStyle = if (l.kind == "EXPERT") FontStyle.Italic else FontStyle.Normal, fontWeight = if (l.kind == "CALL") FontWeight.Bold else FontWeight.Normal)
            }
        }
        else -> {
            val col = when (l.kind) { "WICKET" -> Color(0xFFFCA5A5); "SIX" -> Color(0xFFE9D5FF); "FOUR" -> Color(0xFFBFDBFE); else -> C.Text }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.over, color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                BallChip(l); Spacer(Modifier.width(8.dp))
                Text(l.text, color = col, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f),
                    fontWeight = if (l.kind == "WICKET" || l.kind == "SIX" || l.kind == "FOUR") FontWeight.Bold else FontWeight.Normal)
            }
            HorizontalDivider(color = Color(0xFF1B2433))
        }
    }
}
