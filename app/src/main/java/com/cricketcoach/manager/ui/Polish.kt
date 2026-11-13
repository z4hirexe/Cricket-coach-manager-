package com.cricketcoach.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio

/**
 * Shared polish primitives for the v2.6 screen redesign. One spacing rhythm (8dp grid), one type scale,
 * restrained accents. These keep the four management screens (Fixtures / Inbox / My Club / Selection)
 * consistent with each other and with the rest of the shell.
 */

/** Small uppercase section label with an optional trailing slot (counts, links). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = DS.Sub, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

/** Compact KPI tile: label, big value, optional sub-line and accent. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color = DS.Gold) {
    Column(modifier.clip(DS.Card).background(DS.Navy3).border(1.dp, DS.Line, DS.Card).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label.uppercase(), color = DS.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, color = accent, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
        if (sub != null) Text(sub, color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Centred empty-state placeholder. */
@Composable
fun EmptyState(glyph: String, title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(DS.Navy3).border(1.dp, DS.Line, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(glyph, DS.Muted, 22.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = DS.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (sub != null) { Spacer(Modifier.height(3.dp)); Text(sub, color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

/** Role tag with a consistent colour per cricket role. */
fun roleColor(role: String): Color = when (role) {
    "BAT" -> DS.Gold
    "BOWL" -> DS.Accent
    "AR" -> DS.Good
    "WK" -> Color(0xFFB08CFF)
    else -> DS.Sub
}

@Composable
fun RoleTag(role: String, small: Boolean = false) {
    val col = roleColor(role)
    Text(role, color = col, fontSize = if (small) 9.sp else 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = 0.14f)).border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = if (small) 1.dp else 2.dp))
}

/** Facility / staff level shown as filled pips. */
@Composable
fun LevelDots(level: Int, max: Int = 5, modifier: Modifier = Modifier, color: Color = DS.Gold) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..max) Box(Modifier.size(7.dp).clip(CircleShape).background(if (i <= level) color else DS.Navy4).border(1.dp, if (i <= level) color else DS.Line, CircleShape))
    }
}

/** Thin progress track. */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, color: Color = DS.Gold, height: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(DS.Navy4)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** Compact 40dp search field with a leading icon and a clear button. */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(40.dp).clip(DS.Btn).background(DS.Navy2).border(1.dp, DS.Line, DS.Btn).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, tint = DS.Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = DS.Muted, fontSize = 13.sp, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = DS.White, fontSize = 13.sp),
                cursorBrush = SolidColor(DS.Gold), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(DS.Navy4).semantics { contentDescription = "Clear search" }
                .clickable(role = Role.Button) { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = DS.Sub, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** Compact labelled toggle pill (replaces the oversized default Material switch on list screens). */
@Composable
fun TogglePill(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) DS.Gold.copy(alpha = 0.16f) else DS.Navy2)
        .border(1.dp, if (on) DS.Gold.copy(alpha = 0.7f) else DS.Line, RoundedCornerShape(16.dp))
        .semantics { contentDescription = text }
        .clickable(role = Role.Switch) { GameAudio.click(); onClick() }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) DS.Gold else DS.Navy4).border(1.dp, if (on) DS.Gold else DS.Line, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(6.dp).clip(CircleShape).background(DS.GoldInk))
        }
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (on) DS.Gold else DS.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
