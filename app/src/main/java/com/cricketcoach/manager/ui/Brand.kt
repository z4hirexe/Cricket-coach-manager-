package com.cricketcoach.manager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.audio.GameAudio

/**
 * Cricket Manager Pro 2027(TM) brand + design system (mega overhaul H).
 * One palette (navy / gold / white, controlled accents), one type scale, compact controls with >= 40dp tap targets.
 */
object Brand {
    const val NAME = "Cricket Manager Pro"
    const val YEAR = "2027"
    const val TM = "\u2122"
    /** Full product name, used for the app label, accessibility and About. */
    const val FULL = "$NAME $YEAR$TM"
    const val EDITION = "Beta"
    /** Shown in the menu / About; keep in sync with app/build.gradle.kts versionName. */
    const val VERSION = "2.6.0-beta2"
    const val TAGLINE = "Manage \u2022 Compete \u2022 Make History"
    /** Careers start in the 2026 season; the product brand stays 2027. */
    const val CAREER_START = 2026
}

/** Design tokens. */
object DS {
    val Navy = Color(0xFF060B1A)       // app background
    val Navy2 = Color(0xFF0A1224)      // surfaces / bars
    val Navy3 = Color(0xFF101A31)      // cards
    val Navy4 = Color(0xFF16233F)      // raised / selected
    val Line = Color(0xFF1E2B47)       // hairlines
    val Gold = Color(0xFFE9B949)
    val GoldSoft = Color(0xFFF3D27A)
    val GoldInk = Color(0xFF1A1200)    // text on gold
    val White = Color(0xFFF4F6FC)
    val Sub = Color(0xFF9AA8C4)
    val Muted = Color(0xFF66748F)
    val Accent = Color(0xFF4F8EF7)     // single cool accent (links, info)
    val Good = Color(0xFF3DBE7A)
    val Bad = Color(0xFFE5484D)
    val Live = Color(0xFFE5484D)
    val Card = RoundedCornerShape(10.dp)
    val Btn = RoundedCornerShape(8.dp)
    val BtnH = 40.dp          // compact, still a comfortable tap target
    val BtnHSmall = 34.dp
}

/** "CRICKET MANAGER PRO 2027(TM)" with the (TM) small at the upper right of the year. */
fun brandText(gold: Color = DS.Gold, white: Color = DS.White, upper: Boolean = true): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = white)) { append(if (upper) Brand.NAME.uppercase() else Brand.NAME); append(" ") }
    withStyle(SpanStyle(color = gold)) { append(Brand.YEAR) }
    withStyle(SpanStyle(color = white, fontSize = 0.42f.em(), baselineShift = BaselineShift(0.9f), fontWeight = FontWeight.SemiBold)) { append(Brand.TM) }
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

/** Single-line wordmark. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, size: TextUnit = 18.sp, gold: Color = DS.Gold) {
    Text(brandText(gold), modifier = modifier.semantics { contentDescription = Brand.FULL }, fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = size, letterSpacing = 1.sp, maxLines = 1, softWrap = false, lineHeight = size * 1.1f)
}

/** Two-line lockup: CRICKET MANAGER PRO / 2027(TM) (centered or start-aligned). */
@Composable
fun BrandStack(modifier: Modifier = Modifier, size: TextUnit = 22.sp, center: Boolean = true) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = Brand.FULL }, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(Brand.NAME.uppercase(), color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = 2.sp, maxLines = 1, softWrap = false, lineHeight = size * 1.05f)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = DS.Gold)) { append(Brand.YEAR) }
            withStyle(SpanStyle(color = DS.White, fontSize = 0.34f.em(), baselineShift = BaselineShift(1.15f), fontWeight = FontWeight.SemiBold)) { append(Brand.TM) }
        }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size * 1.6f, letterSpacing = 3.sp, maxLines = 1, softWrap = false, lineHeight = size * 1.6f)
    }
}

/** Round portrait mark (original artwork, gold ring). */
@Composable
fun BrandMark(size: Dp = 36.dp, modifier: Modifier = Modifier) =
    Image(painterResource(R.drawable.brand_mark), Brand.FULL, modifier.size(size))

/** Full logo artwork (portrait + lockup). */
@Composable
fun BrandLogo(size: Dp, modifier: Modifier = Modifier) =
    Image(painterResource(R.drawable.logo_cmp), Brand.FULL, modifier.size(size))

enum class DsKind { PRIMARY, SECONDARY, GHOST, DANGER }

/** Compact button used everywhere in the redesign. */
@Composable
fun DsButton(text: String, modifier: Modifier = Modifier, kind: DsKind = DsKind.PRIMARY, icon: ImageVector? = null, enabled: Boolean = true,
             height: Dp = DS.BtnH, onClick: () -> Unit) {
    val (bg, fg, border) = when (kind) {
        DsKind.PRIMARY -> Triple(DS.Gold, DS.GoldInk, DS.Gold)
        DsKind.SECONDARY -> Triple(DS.Navy4, DS.White, DS.Line)
        DsKind.GHOST -> Triple(Color.Transparent, DS.Gold, DS.Gold.copy(alpha = 0.55f))
        DsKind.DANGER -> Triple(Color(0xFF3A1418), Color(0xFFFFB4B6), DS.Bad.copy(alpha = 0.6f))
    }
    Row(modifier.heightIn(min = height).clip(DS.Btn).background(if (enabled) bg else bg.copy(alpha = 0.35f))
        .border(1.dp, if (enabled) border else border.copy(alpha = 0.3f), DS.Btn)
        .semantics { contentDescription = text }
        .clickable(enabled = enabled, role = Role.Button) { GameAudio.click(); onClick() }
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text.uppercase(), color = if (enabled) fg else fg.copy(alpha = 0.5f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.8.sp,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Flat card: navy surface, hairline border, optional gold accent edge. */
@Composable
fun DsCard(modifier: Modifier = Modifier, accent: Color? = null, selected: Boolean = false, onClick: (() -> Unit)? = null, pad: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(DS.Card).background(if (selected) DS.Navy4 else DS.Navy3)
        .border(1.dp, if (selected) DS.Gold.copy(alpha = 0.7f) else DS.Line, DS.Card)
        .then(if (onClick != null) Modifier.clickable { GameAudio.click(); onClick() } else Modifier)
    Row(if (accent != null) m.height(IntrinsicSize.Min) else m) {
        if (accent != null) Box(Modifier.width(3.dp).fillMaxHeightCompat().background(accent))
        Column(Modifier.weight(1f).padding(pad), content = content)
    }
}

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.fillMaxHeight())

@Composable
fun DsLabel(text: String, modifier: Modifier = Modifier, color: Color = DS.Gold) =
    Text(text.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1, softWrap = false, modifier = modifier)

@Composable
fun DsTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = 18.sp) =
    Text(text, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier, lineHeight = size * 1.15f)

/** Segmented control (compact). */
@Composable
fun DsSegmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(modifier.clip(DS.Btn).background(DS.Navy2).border(1.dp, DS.Line, DS.Btn).padding(3.dp)) {
        for ((k, label) in options) {
            val on = k == selected
            Box(Modifier.weight(1f).heightIn(min = 34.dp).clip(RoundedCornerShape(6.dp)).background(if (on) DS.Gold else Color.Transparent)
                .semantics { contentDescription = label }
                .clickable(enabled = enabled, role = Role.RadioButton) { GameAudio.click(); onSelect(k) }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (on) DS.GoldInk else if (enabled) DS.White else DS.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Setting row with a switch. */
@Composable
fun DsSwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) DS.White else DS.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = DS.Sub, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, { onChange(it) }, enabled = enabled, colors = SwitchDefaults.colors(checkedThumbColor = DS.GoldInk, checkedTrackColor = DS.Gold, uncheckedTrackColor = DS.Navy2, uncheckedBorderColor = DS.Line))
    }
}

/** Volume row: label, slider, value, and a per-channel mute toggle. */
@Composable
fun DsVolumeRow(label: String, value: Float, muted: Boolean, enabled: Boolean = true, onValue: (Float) -> Unit, onDone: () -> Unit = {}, onMute: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) DS.White else DS.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(112.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(value, onValue, valueRange = 0f..1f, enabled = enabled && !muted, onValueChangeFinished = onDone, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = DS.Gold, activeTrackColor = DS.Gold, inactiveTrackColor = DS.Line))
        Text(if (muted) "MUTED" else "${(value * 100).toInt()}%", color = if (muted) DS.Bad else DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (muted) Color(0xFF3A1418) else DS.Navy2).border(1.dp, if (muted) DS.Bad.copy(alpha = 0.6f) else DS.Line, CircleShape)
            .semantics { contentDescription = (if (muted) "Unmute " else "Mute ") + label }
            .clickable(enabled = enabled, role = Role.Switch) { onMute(!muted) }, contentAlignment = Alignment.Center) {
            Text(if (muted) "\uD83D\uDD07" else "\uD83D\uDD0A", fontSize = 14.sp)
        }
    }
}

/** Confirmation dialog in the brand style. */
@Composable
fun DsConfirm(title: String, body: String, confirm: String, dismiss: String = "Cancel", danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = DS.Navy2, shape = RoundedCornerShape(14.dp),
        title = { Text(title, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = { Text(body, color = DS.Sub, fontSize = 13.sp, lineHeight = 18.sp) },
        confirmButton = { DsButton(confirm, kind = if (danger) DsKind.DANGER else DsKind.PRIMARY, height = DS.BtnHSmall + 4.dp) { onConfirm() } },
        dismissButton = { DsButton(dismiss, kind = DsKind.SECONDARY, height = DS.BtnHSmall + 4.dp) { onDismiss() } })
}

/** Small status tag. */
@Composable
fun DsTag(text: String, color: Color = DS.Gold, filled: Boolean = false) {
    Text(text.uppercase(), color = if (filled) DS.Navy else color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (filled) color else color.copy(alpha = 0.12f)).border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp))
}
