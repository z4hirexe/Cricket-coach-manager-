package com.cricketcoach.manager

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Integration: calendar Play / Simulate buttons drive the real engine through GameVM. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarFlowTest {
    private fun waitIdle(vm: GameVM) {
        var i = 0
        do { Thread.sleep(40); shadowOf(Looper.getMainLooper()).idle() } while (vm.busy && i++ < 500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun simulateAndPlayFromTheCalendar() {
        if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        val vm = GameVM(ApplicationProvider.getApplicationContext<Application>())
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_KKR", 1.0, 1, Random(4))
        s.auction = null
        assertNull(Career.startSeason(s))
        vm.save = s; vm.goHome(); vm.navRoot(Screen.Calendar)
        val ipl = s.league.first()
        val u = ipl.userTeam!!

        // 1) Simulate a future fixture of two other teams: every earlier game (incl. ours) is played, the target too
        // (createSeason uses an unseeded schedule: pick a target after our first game so the test is deterministic)
        val firstMine = ipl.fixtures.filter { it.home == u || it.away == u }.minOf { it.day }
        val target = ipl.fixtures.filter { it.home != u && it.away != u && it.day >= 6 && it.day > firstMine }.minByOrNull { it.day }!!
        assertTrue(vm.userMatchesBefore(ipl.id, target.id) > 0)
        vm.calendarGo(ipl.id, target.id, false)
        waitIdle(vm)
        assertTrue("target simulated", target.played)
        assertNull(vm.pending)
        assertTrue(ipl.fixtures.filter { it.day < target.day }.all { it.played })
        assertEquals(target.day, s.day)
        assertEquals(Schedule.dateOf(s, target.day), Schedule.today(s))

        // 2) Play our next match: the calendar opens the Match Centre with it pending
        val mine = ipl.fixtures.filter { !it.played && (it.home == u || it.away == u) }.minByOrNull { it.day }!!
        vm.calendarGo(ipl.id, mine.id, true)
        waitIdle(vm)
        assertEquals(mine.id, vm.pending?.fixtureId)
        assertEquals(Screen.PreMatch, vm.screen)
        assertFalse(mine.played)

        // 3) Simulate that pending match straight from the calendar
        vm.calendarGo(ipl.id, mine.id, false)
        waitIdle(vm)
        assertTrue(mine.played)
        assertNull(vm.pending)
        assertNotNull(mine.res)

        // 4) the classic one-day advance still works
        val before = ipl.fixtures.count { it.played }
        vm.calendarAdvanceDay(); waitIdle(vm)
        assertTrue(ipl.fixtures.count { it.played } > before || vm.pending != null)
    }
}
