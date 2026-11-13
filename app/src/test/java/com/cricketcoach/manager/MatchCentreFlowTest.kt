package com.cricketcoach.manager

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.PendingMatch
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Integration (Phase E): Match Centre gates - registration -> team sheet -> toss -> live, through the real GameVM. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MatchCentreFlowTest {
    private fun waitIdle(vm: GameVM) {
        var i = 0
        do { Thread.sleep(40); shadowOf(Looper.getMainLooper()).idle() } while (vm.busy && i++ < 500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun registrationAndTeamSheetAreMandatory() {
        if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        val vm = GameVM(ApplicationProvider.getApplicationContext<Application>())
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_RCB", 1.0, 1, Random(8))
        s.auction = null
        assertNull(Career.startSeason(s))
        vm.save = s; vm.goHome()
        vm.simToNextUserMatch(); waitIdle(vm)
        val opening = s.league.first { it.key == "IPL" }
        assertEquals(Screen.SeriesSelection(opening.id), vm.screen)
        assertEquals(opening.selectionDate, Schedule.today(s).toString())
        // Exercise the fallback gate for old saves that already have a pending match but no registration.
        val fixture = opening.fixtures.filter { it.home == opening.userTeam || it.away == opening.userTeam }.minByOrNull { it.day }!!
        vm.pending = PendingMatch(opening.id, fixture.id)
        // Reopening with a pending match goes to the Match Centre
        Thread.sleep(200); shadowOf(Looper.getMainLooper()).idle()
        vm.goHome(); vm.simToNextUserMatch()
        assertEquals(Screen.PreMatch, vm.screen)
        val comp = vm.pendingComp()!!; val c = s.userClub()

        // 1) not registered: Watch live / toss send the user to the registration screen
        assertFalse(vm.pendingRegistrationOk())
        vm.watchLive()
        assertEquals(Screen.SquadRegistration(comp.id), vm.screen)
        assertFalse(vm.callToss(true))
        assertNull("no match built before registration", vm.sim)
        vm.back(); vm.back()
        assertEquals(Screen.PreMatch, vm.screen)

        // 2) registered but team sheet not confirmed: still blocked
        assertNull(Registration.register(s, comp, Registration.autoSelect(s, comp)))
        assertTrue(vm.pendingRegistrationOk())
        vm.quickSimMatch()
        assertEquals(Screen.PreMatch, vm.screen)
        assertFalse(vm.pendingFixture()!!.played)

        // 3) confirm the team sheet (XI from the registered squad), edits re-open it
        vm.prepareSim()
        TeamSelector.autoPick(c, s.players, pool = Registration.xiPool(s, comp))
        if (c.vice !in c.xi || c.vice == c.captain) c.vice = c.xi.first { it != c.captain }
        val cfg = vm.sim!!.cfg
        assertNull(vm.confirmTeamSheet())
        assertTrue(vm.sheetConfirmed())
        assertSame("venue / pitch / weather survive the rebuild", cfg, vm.sim!!.cfg)
        vm.sheetEdited()
        assertFalse(vm.sheetConfirmed())
        assertNull(vm.confirmTeamSheet())

        // 4) toss + watch live: the match uses only registered players
        if (vm.callToss(true)) vm.chooseToss(true)
        assertTrue(vm.sim!!.tossWinner.isNotEmpty())
        vm.watchLive()
        assertEquals(Screen.Live, vm.screen)
        val reg = comp.reg.toSet()
        assertTrue(vm.sim!!.xiOf(c.id).all { it in reg })
        vm.simRest()
        assertTrue(vm.pendingFixture()!!.played)
        assertTrue(Registration.locked(s, comp))
    }
}
