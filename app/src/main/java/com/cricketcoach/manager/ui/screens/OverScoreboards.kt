package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.CareerSave
import com.cricketcoach.manager.game.MatchSim
import com.cricketcoach.manager.ui.*

/** The over-by-over control room; quiet deliveries advance without opening the 3D playback window. */
@Composable
fun OverScoreDashboard(m: MatchSim, modifier: Modifier = Modifier) {
    val inn = m.cur ?: return
    Column(modifier.widthIn(max = 740.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Color(0xE9081428)).border(1.dp, C.Gold.copy(alpha = .5f), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClubBadge(m.club(inn.batTeam), 38.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.club(inn.batTeam).name.uppercase(), color = C.Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
                Text("${inn.scoreText()}  ·  ${inn.oversText()} overs", color = C.Text, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
            }
            Text(boardInfo(m).situation, color = C.GoldLight, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = DS.Line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("BATTING", color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Black)
                for (i in listOf(inn.striker, inn.nonStriker)) inn.bc.getOrNull(i)?.let { c ->
                    Text("${m.pn(c.pid)}  ${c.runs} (${c.balls})${if (i == inn.striker) " *" else ""}", color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("BOWLING", color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Black)
                val b = inn.bw[inn.bowler]
                Text(if (b != null) "${m.pn(b.pid)}  ${b.wk}/${b.runs} (${b.oversText})" else "Waiting for bowler", color = C.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Tap OVER to play highlights · HISTORY to replay any ball", color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Match-end scoreboard: innings batting and bowling summaries, result and player of the match. */
@Composable
fun FinalScoreboard(m: MatchSim, s: CareerSave, onScorecard: () -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 780.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xF5081428))
        .border(1.dp, C.Gold.copy(alpha = .65f), RoundedCornerShape(13.dp)).verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FULL TIME", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Text(m.resultText, color = C.Text, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2, modifier = Modifier.weight(1f))
        }
        m.innings.forEachIndexed { index, inn ->
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().background(if (index % 2 == 0) Color(0xFF9A303C) else Color(0xFF9A7921)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.club(inn.batTeam).name.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${inn.oversText()} OVERS    ${inn.scoreText()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            val batters = inn.bc.filter { it.batted }.sortedByDescending { it.runs }.take(3)
            val bowlers = inn.bw.values.sortedWith(compareByDescending<com.cricketcoach.manager.game.BowlCard> { it.wk }.thenBy { it.runs }).take(3)
            val rows = maxOf(batters.size, bowlers.size)
            repeat(rows) { i ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)) {
                    val b = batters.getOrNull(i); val w = bowlers.getOrNull(i)
                    Text(b?.let { "${m.pn(it.pid)}  ${it.runs}${if (!it.out) "*" else ""} (${it.balls})" } ?: "", color = C.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(w?.let { "${m.pn(it.pid)}  ${it.wk}/${it.runs} (${it.oversText})" } ?: "", color = C.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        m.potm()?.let { pid ->
            val name = s.players[pid]?.name ?: pid
            val batting = m.innings.flatMap { it.bc }.filter { it.pid == pid && it.batted }.sumOf { it.runs }
            val wickets = m.innings.flatMap { it.bw.values }.filter { it.pid == pid }.sumOf { it.wk }
            Text("PLAYER OF THE MATCH  ·  $name  ·  $batting runs / $wickets wickets", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp), maxLines = 2)
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineBtn("Full scorecard", Modifier.weight(1f)) { onScorecard() }
            GoldButton("Continue", Modifier.weight(1f)) { onContinue() }
        }
    }
}
