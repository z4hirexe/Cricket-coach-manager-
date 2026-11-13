package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cricketcoach.manager.game.Schedule
import com.cricketcoach.manager.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A compact, live calendar; stop is cooperative and never interrupts a fixture halfway through. */
@Composable
fun SimulationPopup(vm: GameVM) {
    val save = vm.save ?: return
    val date = runCatching { LocalDate.parse(vm.simulationDate) }.getOrElse { Schedule.today(save) }
    val start = date.withDayOfMonth(1)
    val offset = start.dayOfWeek.value - 1
    val count = start.lengthOfMonth()
    val matchDates = save.league.flatMap { c -> c.fixtures.filter { f -> !f.played && c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam) }.map { Schedule.fixtureDate(save, c, it) } }.toSet()
    val selectDates = save.league.filter { !it.regDone }.mapNotNull { runCatching { LocalDate.parse(it.selectionDate) }.getOrNull() }.toSet()
    Dialog(onDismissRequest = { vm.stopCalendarSimulation() }) {
        Surface(shape = RoundedCornerShape(18.dp), color = C.Surface, tonalElevation = 8.dp,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SIMULATING", color = C.Gold, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(vm.simulationStatus, color = C.Sub, fontSize = 10.sp, maxLines = 1)
                    }
                    CircularProgressIndicator(Modifier.size(22.dp), color = C.Gold, strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = C.Sub, fontSize = 10.sp, textAlign = TextAlign.Center) } }
                val rows = (offset + count + 6) / 7
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(7) { col ->
                            val n = row * 7 + col - offset + 1
                            Box(Modifier.weight(1f).height(27.dp), contentAlignment = Alignment.Center) {
                                if (n in 1..count) {
                                    val d = start.withDayOfMonth(n)
                                    val current = d == date
                                    val fill by animateColorAsState(if (current) C.Gold else Color.Transparent, label = "date")
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(22.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                                            Text("$n", color = if (current) Color.Black else C.Text, fontSize = 11.sp, fontWeight = if (current) FontWeight.Black else FontWeight.Normal)
                                        }
                                        Box(Modifier.size(3.dp).clip(CircleShape).background(if (d in selectDates) C.Gold else if (d in matchDates) C.Green else Color.Transparent))
                                    }
                                }
                            }
                        }
                    }
                }
                Text("● Selection     ● Your match", color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp))
                OutlineBtn("Stop simulation", Modifier.fillMaxWidth()) { vm.stopCalendarSimulation() }
            }
        }
    }
}
