package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

/** Competition that owns [f] (league competitions or the side tournament). */
fun compOf(s: CareerSave, f: Fixture): Competition? = s.league.firstOrNull { it.fixtures.contains(f) } ?: s.tournament?.takeIf { it.fixtures.contains(f) }

/**
 * Compact RESULT CARD for any played fixture (v2.6). Replaces the old plain text-list dialog. The full batting /
 * bowling scorecard lives on its own Match Report screen (explicit open only).
 */
@Composable
fun FixtureDetail(s: CareerSave, f: Fixture, vm: GameVM? = null, onClose: () -> Unit) {
    if (f.res == null) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) { ResultCard(s, f, vm, onClose) }
}

/** The result card itself (also used directly by the screen previews, which cannot capture dialog windows). */
@Composable
fun ResultCard(s: CareerSave, f: Fixture, vm: GameVM? = null, onClose: () -> Unit) {
    val r = f.res ?: return
    val c = compOf(s, f)
    run {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).clip(RoundedCornerShape(14.dp)).background(DS.Navy2).border(1.dp, DS.Line, RoundedCornerShape(14.dp))) {
            ResultHeader(s, c, f, r, vm?.settings?.dateStyle ?: "IN")
            val inns = remember(r) { Scorecard.parse(r.card) }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val bats = Scorecard.topBat(inns, 2); val bowls = Scorecard.topBowl(inns, 2)
                if (bats.isNotEmpty() || bowls.isNotEmpty()) {
                    DsLabel("Top performers", color = DS.Sub)
                    Spacer(Modifier.height(4.dp))
                    for ((team, b) in bats) PerfRow(b.name, team, "${b.runs}${if (b.notOut) "*" else ""} (${b.balls})")
                    for ((team, b) in bowls) if (b.wk > 0) PerfRow(b.name, team, "${b.wk}/${b.runs} (${b.overs})")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (vm != null && c != null && r.card.isNotEmpty()) DsButton("Full scorecard", Modifier.weight(1f), height = DS.BtnHSmall) { onClose(); vm.openReport(c.id, f.id) }
                    DsButton("Close", Modifier.weight(1f), kind = DsKind.SECONDARY, height = DS.BtnHSmall) { onClose() }
                }
            }
        }
    }
}

@Composable
private fun PerfRow(name: String, team: String, fig: String) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = DS.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(team, color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 8.dp))
        Text(fig, color = DS.Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}

/** Header of the result card / report: competition, date & venue, both teams with badges and scores, result and POTM. */
@Composable
private fun ResultHeader(s: CareerSave, c: Competition?, f: Fixture, r: MatchSummary, dateStyle: String) {
    val h = s.clubs[f.home]; val a = s.clubs[f.away]
    Column(Modifier.fillMaxWidth().background(DS.Navy3).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(((c?.name ?: "") + "  ·  " + f.stage).uppercase(), color = DS.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (c != null) DsTag(Schedule.formatLabel(c, f), DS.Sub)
        }
        if (c != null) {
            val d = runCatching { Schedule.fixtureDate(s, c, f) }.getOrNull()
            Text(listOfNotNull(d?.let { Schedule.fmt(it, dateStyle) }, Schedule.venue(s, f)).joinToString("  ·  "), color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        for ((club, score, won) in listOf(Triple(h, r.hScore, r.winner == f.home), Triple(a, r.aScore, r.winner == f.away))) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(22.dp).background(club?.let { Color(it.primary) } ?: DS.Line))
                Spacer(Modifier.width(8.dp))
                ClubBadge(club, 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(club?.name ?: "?", color = if (won) DS.White else DS.Sub, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(score.ifBlank { "-" }, color = if (won) DS.White else DS.Sub, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(r.text, color = DS.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (r.potm.isNotEmpty()) Text("Player of the match: ${s.players[r.potm]?.name ?: r.potm}", color = DS.Sub, fontSize = 11.sp, maxLines = 1)
    }
}

/** MATCH REPORT: result header + innings tabs with the full batting and bowling cards (parsed from the stored scorecard). */
@Composable
fun MatchReportScreen(vm: GameVM, compId: String, fxId: String) {
    val s = vm.save ?: return
    val c = vm.compById(compId)
    val f = c?.fixtures?.firstOrNull { it.id == fxId }
    val r = f?.res
    ScreenScaffold(vm, "Match Report") {
        if (c == null || f == null || r == null) { Text("This result is no longer available.", color = DS.Sub, modifier = Modifier.padding(16.dp)); return@ScreenScaffold }
        val inns = remember(r) { Scorecard.parse(r.card) }
        var sel by remember { mutableIntStateOf(0) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 760.dp
            if (wide) Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Box(Modifier.clip(DS.Card).border(1.dp, DS.Line, DS.Card)) { ResultHeader(s, c, f, r, vm.settings.dateStyle) }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) { InningsTabs(inns, sel) { sel = it }; InningsCard(inns.getOrNull(sel), Modifier.weight(1f)) }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Box(Modifier.clip(DS.Card).border(1.dp, DS.Line, DS.Card)) { ResultHeader(s, c, f, r, vm.settings.dateStyle) }
                Spacer(Modifier.height(10.dp))
                InningsTabs(inns, sel) { sel = it }
                InningsCard(inns.getOrNull(sel), Modifier, scroll = false)
            }
        }
    }
}

@Composable
private fun InningsTabs(inns: List<Scorecard.Inn>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        inns.forEachIndexed { i, inn ->
            val on = i == sel
            Column(Modifier.clip(DS.Btn).background(if (on) DS.Navy4 else DS.Navy3).border(1.dp, if (on) DS.Gold else DS.Line, DS.Btn)
                .clickable(role = Role.Tab) { GameAudio.click(); onSel(i) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(inn.team, color = if (on) DS.White else DS.Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${inn.score} (${inn.overs} ov)${if (inns.size > 2) " · " + inn.label.replace("Innings", "Inns") else ""}", color = if (on) DS.Gold else DS.Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InningsCard(inn: Scorecard.Inn?, modifier: Modifier, scroll: Boolean = true) {
    if (inn == null) { Text("No scorecard recorded for this match.", color = DS.Sub, fontSize = 12.sp); return }
    Column(modifier.fillMaxWidth().clip(DS.Card).background(DS.Navy3).border(1.dp, DS.Line, DS.Card).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(12.dp)) {
        TableHead(listOf("BATTER" to 3f, "" to 3.2f, "R" to 0.8f, "B" to 0.8f, "4s" to 0.7f, "6s" to 0.7f, "SR" to 1.1f))
        for (b in inn.bat) {
            Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name + if (b.notOut) "*" else "", color = DS.White, fontSize = 12.sp, fontWeight = if (b.notOut) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3f))
                Text(b.how, color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3.2f))
                Num("${b.runs}", 0.8f, bold = true); Num("${b.balls}", 0.8f); Num("${b.fours}", 0.7f); Num("${b.sixes}", 0.7f); Num("%.1f".format(b.sr), 1.1f)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line.copy(alpha = 0.5f)))
        }
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Extras", color = DS.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${inn.extras}", color = DS.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TOTAL", color = DS.Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("${inn.score} (${inn.overs} ov)", color = DS.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        TableHead(listOf("BOWLER" to 3.4f, "O" to 0.9f, "M" to 0.7f, "R" to 0.8f, "W" to 0.7f, "ECON" to 1.1f))
        for (b in inn.bowl) {
            Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, color = DS.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3.4f))
                Num(b.overs, 0.9f); Num("${b.maidens}", 0.7f); Num("${b.runs}", 0.8f); Num("${b.wk}", 0.7f, bold = true, gold = b.wk >= 3); Num("%.2f".format(b.econ), 1.1f)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun TableHead(cols: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        cols.forEachIndexed { i, (l, w) -> Text(l, color = DS.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, textAlign = if (i == 0 || l.isEmpty()) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(w), maxLines = 1) }
    }
}

@Composable
private fun RowScope.Num(v: String, w: Float, bold: Boolean = false, gold: Boolean = false) =
    Text(v, color = if (gold) DS.Gold else DS.White, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Black else FontWeight.Normal, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(w))
