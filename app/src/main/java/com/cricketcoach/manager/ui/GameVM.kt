package com.cricketcoach.manager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.save.SaveManager
import com.cricketcoach.manager.save.SaveMeta
import com.cricketcoach.manager.save.Settings
import com.cricketcoach.manager.audio.GameAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed class Screen(val title: String) {
    object Main : Screen(Brand.FULL)
    object Career : Screen("Career Mode")
    object NewCareer : Screen("New Career")
    object Dashboard : Screen("Dashboard")
    object MyClub : Screen("My Club")
    data class Squad(val clubId: String? = null) : Screen("Squad")
    object Database : Screen("Player Database")
    data class Profile(val pid: String) : Screen("Player Profile")
    object AuctionScr : Screen("Auction")
    object Transfers : Screen("Transfers")
    object Fixtures : Screen("Fixtures")
    object PreMatch : Screen("Match Centre")
    object Live : Screen("Live Match")
    object Table : Screen("League Table")
    object TournamentsScr : Screen("Tournaments")
    object Statistics : Screen("Statistics")
    object Finances : Screen("Club Finances")
    object SettingsScr : Screen("Settings")
    object SeasonSummary : Screen("Season Review")
    // career types, social
    object CareerType : Screen("Career Type")
    data class ChooseTeam(val mode: String) : Screen("Choose Team")
    object NewTeam : Screen("New Team")
    object Selection : Screen("Squad Selection")
    object Social : Screen("Stumpstagram")
    object Inbox : Screen("Inbox")
    // v2.5: cricket calendar / match schedule centre
    object Calendar : Screen("Cricket Calendar")
    // v2.5 Phase D: Stumpstagram player profile
    data class SocialProfile(val pid: String) : Screen("Stumpstagram")
    // v2.5 Phase E: tournament squad registration
    data class SquadRegistration(val compId: String) : Screen("Squad Registration")
    // v2.6 mega overhaul: Yorker (text/photo social network), match report, about, new-game hub
    object YorkerHome : Screen("Yorker")
    data class YorkerProfile(val key: String) : Screen("Yorker")
    data class YorkerThread(val postId: Int) : Screen("Yorker")
    data class MatchReport(val compId: String, val fxId: String) : Screen("Match Report")
    object About : Screen("About")
    object NewGame : Screen("New Game")
    object MultiTeamSetup : Screen("Multi-Team Career")
    object SquadHub : Screen("Squad Hub")
    data class SeriesSelection(val compId: String) : Screen("Series Selection")
    object TournamentHub : Screen("Tournament")
}

class PendingMatch(val compId: String, val fixtureId: String)

class GameVM(app: Application) : AndroidViewModel(app) {
    /** Device-local storage: three career slots, no account or cloud dependency. */
    var sm by mutableStateOf(SaveManager(app))
        private set
    var settings by mutableStateOf(Settings(app))
        private set
    var coins by mutableIntStateOf(1000)
    var xp by mutableIntStateOf(0)
    // live commentary
    val comLines = mutableStateListOf<ComLine>()
    private var commentary: Commentary? = null
    private var comFor: MatchSim? = null
    private var comDone = 0
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)
    var save by mutableStateOf<CareerSave?>(null)
    var tick by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>(Screen.Main)
    var toast by mutableStateOf<String?>(null)
    var pending by mutableStateOf<PendingMatch?>(null)
    var sim by mutableStateOf<MatchSim?>(null)
    var simTick by mutableIntStateOf(0)
    var autoPlay by mutableStateOf(false)
    var matchApplied by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var simulationVisible by mutableStateOf(false)
    var simulationDate by mutableStateOf("")
    var simulationStatus by mutableStateOf("")
    var simulationMatchDates by mutableStateOf<Set<String>>(emptySet())
    var simulationSelectionDates by mutableStateOf<Set<String>>(emptySet())
    private var simulationStopRequested = false
    var saves by mutableStateOf<List<SaveMeta>>(emptyList())
    /** Standalone tournaments run in memory and never write to the three career slots. */
    var standalone by mutableStateOf(false)
    private val rnd = Random.Default

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Repo.load(app) }
                coins = settings.coins; xp = settings.xp
                saves = withContext(Dispatchers.IO) { sm.list() }
            } catch (e: Exception) {
                loadError = "Could not load dataset: ${e.message}"
            }
            loading = false
        }
    }

    val screen: Screen get() = stack.last()
    fun nav(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replaceTop(s: Screen) { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); stack.add(s) }
    fun goHome() { stack.clear(); stack.add(Screen.Main); if (save != null) stack.add(if (standalone) Screen.TournamentsScr else Screen.Dashboard) }
    /** Sidebar navigation: keep the back stack shallow (Main > Home > section). */
    fun navRoot(s: Screen) { stack.clear(); stack.add(Screen.Main); if (save != null) stack.add(if (standalone) Screen.TournamentsScr else Screen.Dashboard); if (s != Screen.Dashboard && s != Screen.Main && !(standalone && s == Screen.TournamentsScr)) stack.add(s) }
    /** Inbox: news items newer than the last one the user opened. */
    fun unreadNews(): Int { val s = save ?: return 0; if (s.news.isEmpty()) return 0; val i = s.news.indexOf(s.newsSeen); return if (i >= 0) i else minOf(s.news.size, 99) }
    fun markNewsRead() { val s = save ?: return; val top = s.news.firstOrNull() ?: return; if (s.newsSeen != top) { s.newsSeen = top; bump() } }
    fun toggleMusic() { settings.music = !settings.music; com.cricketcoach.manager.audio.GameAudio.refresh(); bump() }
    /** Yorker posts newer than the last time the feed was opened. */
    fun yorkerUnread(): Int { val s = save ?: return 0; return s.yorker.posts.count { it.id > s.yorker.seenId }.coerceAtMost(99) }
    fun markYorkerRead() { val s = save ?: return; val top = s.yorker.posts.maxOfOrNull { it.id } ?: return; if (s.yorker.seenId != top) { s.yorker.seenId = top; bump() } }
    /** Open the result card + scorecard of any played fixture (explicit open only - other teams' matches never auto-open). */
    fun openReport(compId: String, fxId: String) = nav(Screen.MatchReport(compId, fxId))

    /**
     * Pause menu > Exit Match (after confirmation). Nothing is lost: the started match stays in memory as the pending
     * fixture and the career is saved; "Resume match" in the Match Centre / Calendar continues from the same ball.
     */
    fun exitMatch() {
        autoPlay = false
        persist()
        navRoot(Screen.Calendar)
        if (sim?.started == true && sim?.finished != true) msg("Match paused - resume it from the Match Centre or Calendar.")
    }
    fun bump() { tick++ }
    fun msg(s: String?) { if (s != null) toast = s }

    /* ---------------- saving ---------------- */
    fun persist() {
        bump()
        val s = save ?: return
        if (standalone) return
        drainRewards(s)
        if (!settings.autosave) return
        val json = sm.toJson(s)
        val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) {
            sm.write(meta, json)
            val list = sm.list()
            withContext(Dispatchers.Main) { saves = list }
        }
    }

    fun manualSave() {
        val s = save ?: return
        if (standalone) { toast = "Standalone tournaments are not saved to career slots."; return }
        val json = sm.toJson(s); val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) {
            sm.write(meta, json)
            val list = sm.list()
            withContext(Dispatchers.Main) { saves = list; toast = "Game saved to slot ${s.slot}" }
        }
    }

    fun manualSaveSilent() {
        val s = save ?: return
        if (standalone) return
        val json = sm.toJson(s); val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) { sm.write(meta, json) }
    }

    /** Settings changed: keep everything on this device. */
    fun saveSettings() { bump() }

    fun refreshSaves() { viewModelScope.launch(Dispatchers.IO) { val l = sm.list(); withContext(Dispatchers.Main) { saves = l } } }

    /** All three career slots are available locally; no account is required. */
    fun slotAllowed(slot: Int) = slot in 1..3
    val level: Int get() = 1 + xp / 500

    /* ---------------- coins / xp ---------------- */
    private fun drainRewards(s: CareerSave) {
        if (s.pendingCoins == 0 && s.pendingXp == 0) return
        val gained = s.pendingCoins
        coins = (coins + s.pendingCoins).coerceAtLeast(0); xp += s.pendingXp
        s.pendingCoins = 0; s.pendingXp = 0
        settings.coins = coins; settings.xp = xp
        if (gained >= 100) toast = "+$gained coins earned!"
    }

    fun spendCoins(n: Int, what: String): Boolean {
        if (coins < n) { toast = "Not enough coins ($coins / $n)"; return false }
        coins -= n; settings.coins = coins
        GameAudio.play("coin"); toast = "Spent $n coins: $what"
        return true
    }

    /* ---------------- commentary ---------------- */
    /** Generates commentary for new deliveries of [m]; returns the newest line (to be spoken). */
    fun updateCommentary(m: MatchSim): ComLine? {
        if (comFor !== m) {
            comFor = m; comDone = 0; comLines.clear()
            commentary = Commentary(settings.comLang, settings.comStyle, expert = settings.comExpert)
        }
        val c = commentary ?: return null
        val evs = m.events
        if (evs.size <= comDone) return null
        val start = if (evs.size - comDone > 8) evs.size - 8 else comDone // fast-forwarded: describe only the latest balls (stats still tracked)
        var last: ComLine? = null
        for (i in comDone until evs.size) {
            val lines = c.process(evs[i], m, emit = i >= start)
            for (l in lines) { if (l.kind == "OVER" && !settings.comOverCards) continue; comLines.add(0, l) }
            lines.firstOrNull { it.kind == evs[i].kind }?.let { last = it }
        }
        while (comLines.size > 400) comLines.removeAt(comLines.size - 1)
        comDone = evs.size
        return last
    }

    /** Commentators on air for the current match (lead, analyst). */
    fun booth(): List<Caster> = commentary?.booth ?: Commentary.boothFor(settings.comLang, settings.comStyle)

    /** Speaks everything said about delivery [evId]: the lead's call, then queued special calls and the analyst. */
    fun speakBall(evId: Int) {
        val c = commentary ?: return
        val lines = comLines.filter { it.evId == evId && it.spoken }.reversed()
        var first = true
        for (l in lines) { GameAudio.speakAs(l.text, l.lang, c.booth.getOrNull(l.speaker), first); first = false }
    }

    /* ---------------- career lifecycle ---------------- */
    fun newModeCareer(slot: Int, manager: String, mode: String, teamId: String) {
        if (!slotAllowed(slot)) { msg("Invalid career slot."); return }
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) { Modes.newCareer(slot, manager.ifBlank { "Coach" }, mode, teamId, settings.economy.toDouble(), settings.difficulty) }
            save = s; standalone = false; settings.lastSlot = slot; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
            persist()
        }
    }

    /** v2.6 Stage 1: one career, three managed teams (IPL + International + Domestic). */
    fun newMultiCareer(slot: Int, manager: String, nationId: String, stateId: String, franchiseId: String) {
        if (!slotAllowed(slot)) { msg("Invalid career slot."); return }
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) { Modes.newMultiCareer(slot, manager.ifBlank { "Coach" }, nationId, stateId, franchiseId, settings.economy.toDouble(), settings.difficulty) }
            save = s; standalone = false; settings.lastSlot = slot; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
            persist()
        }
    }

    /** Switch the active team in a multi-team career. The merged calendar and pending match are unchanged. */
    fun switchTeam(id: String) {
        val s = save ?: return
        if (!s.isManaged(id) || s.userClubId == id) return
        s.setActiveTeam(id)
        persist()
        msg("Active team: ${s.userClub().name}")
    }

    fun newCareer(slot: Int, manager: String, club: String, short: String, country: String, primary: Long, secondary: Long, logo: Int, jersey: Int, setup: String,
                  stadium: String = "", logoFile: String = "", boost: Boolean = false) {
        if (!slotAllowed(slot)) { msg("Invalid career slot."); return }
        if (boost && !spendCoins(500, "transfer budget boost")) return
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) {
                Career.newCareer(slot, manager.ifBlank { "Coach" }, club.ifBlank { "My Club" }, short.ifBlank { "MYC" }, country,
                    primary, secondary, logo, jersey, setup, settings.economy.toDouble(), settings.difficulty)
            }
            s.userClub().let { u ->
                if (stadium.isNotBlank()) u.stadium = stadium.take(40)
                if (logoFile.isNotBlank()) u.logoFile = logoFile
                if (boost) u.balance += 10.0 * s.economy
            }
            Social.post(s, Social.OFFICIAL, "NEW CLUB: ${s.userClub().name} are founded with ${s.manager} as head coach. Next stop: Division 1! #${s.userClub().short}", "NEWS", 600, rnd)
            save = s; standalone = false; settings.lastSlot = slot; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
            persist()
            if (s.auction != null) nav(Screen.AuctionScr)
        }
    }

    fun loadCareer(slot: Int) {
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { sm.load(slot) }
            busy = false
            if (s == null) { toast = "Could not load slot $slot"; return@launch }
            save = s; standalone = false; settings.lastSlot = slot; pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
        }
    }

    fun deleteSlot(slot: Int) {
        val m = sm
        viewModelScope.launch(Dispatchers.IO) {
            m.delete(slot)
            val l = m.list(); withContext(Dispatchers.Main) { saves = l; if (save?.slot == slot) save = null }
        }
    }

    fun exitToMenu() {
        if (standalone) { standalone = false; save = null; pending = null; sim = null; stack.clear(); stack.add(Screen.Main); return }
        manualSave(); stack.clear(); stack.add(Screen.Main)
    }

    /** Stage 2: standalone tournament from the main menu (no career slot used). */
    fun startStandaloneTournament(type: String, userTeam: String) {
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) {
                val mode = if (type == "IPL") Modes.IPL else Modes.INTL
                val sv = Modes.newCareer(0, "Tournament", mode, userTeam, settings.economy.toDouble(), settings.difficulty)
                sv.league.clear(); sv.phase = "LEAGUE"; sv.day = 0
                Tournaments.create(sv, type, userTeam, null)
                sv.tournament?.startDate = Schedule.today(sv).plusDays(1).toString()
                sv
            }
            save = s; standalone = true; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.TournamentsScr)
        }
    }

    /** One tap: simulate other fixtures until a user selection window or user match. */
    fun startCalendarSimulation() {
        val s = save ?: return
        if (busy) return
        if (standalone) { playNextTournament(); return }
        if (pending != null) { nav(Screen.PreMatch); return }
        if (s.phase == "OFFSEASON") { nav(Screen.SeasonSummary); return }
        if (s.phase == "PRESEASON") {
            val err = Career.startSeason(s)
            if (err != null) { msg(err); return }
        }
        simulationStopRequested = false
        simulationDate = Schedule.today(s).toString()
        // Snapshot event markers before background simulation mutates fixture lists.
        simulationMatchDates = s.league.flatMap { c -> c.fixtures.filter { f -> !f.played && c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam) }.map { Schedule.fixtureDate(s, c, it).toString() } }.toSet()
        simulationSelectionDates = s.league.filter { !it.regDone && it.selectionDate.isNotEmpty() }.map { it.selectionDate }.toSet()
        simulationStatus = "Simulating to your next selection or match"
        simulationVisible = true; busy = true
        viewModelScope.launch {
            var outcome: DayOut = DayOut.Next
            try {
                var days = 0
                while (!simulationStopRequested && days++ < 800 && save === s) {
                    outcome = withContext(Dispatchers.Default) { advanceDay(s, false, stopForSelection = true) }
                    simulationDate = Schedule.today(s).toString()
                    tick++
                    if (outcome !is DayOut.Next) break
                    delay(180)
                }
            } finally { busy = false; simulationVisible = false }
            if (save !== s) return@launch
            persist()
            when (val out = outcome) {
                is DayOut.Selection -> openSelection(out.compId)
                is DayOut.Match -> { pending = out.pm; sim = null; matchApplied = false; persist(); nav(Screen.PreMatch) }
                is DayOut.Register -> openRegistration(out.compId)
                is DayOut.SeasonEnd -> nav(Screen.SeasonSummary)
                else -> if (simulationStopRequested) msg("Simulation stopped.")
            }
        }
    }

    fun stopCalendarSimulation() { simulationStopRequested = true; simulationStatus = "Stopping after this date…" }

    /* ---------------- calendar ---------------- */
    fun continueAction() {
        val s = save ?: return
        if (standalone) { playNextTournament(); return }
        if (pending != null) { nav(Screen.PreMatch); return }
        when (s.phase) {
            "PRESEASON" -> {
                val next = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: 1
                val sel = selectionStop(s, next)
                if (sel != null) { s.selectionToday = sel.selectionDate; persist(); openSelection(sel.id); return }
                s.selectionToday = ""
                val err = Career.startSeason(s); if (err != null) msg(err) else msg("Season ${s.season} is under way!"); persist()
            }
            "LEAGUE", "PLAYOFFS" -> runDays(false, 1)
            "OFFSEASON" -> nav(Screen.SeasonSummary)
        }
    }

    private sealed class DayOut { object Next : DayOut(); object SeasonEnd : DayOut(); class Match(val pm: PendingMatch) : DayOut(); class Register(val compId: String) : DayOut(); class Selection(val compId: String) : DayOut() }

    /** v2.5 Phase G: a registration-gated competition whose user fixture is on [day] and isn't registered yet (the sim stops for it). */
    private fun registrationStop(s: CareerSave, list: List<Pair<Competition, Fixture>>): Competition? =
        list.firstOrNull { (c, f) -> c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam) && Registration.required(s, c) && !Registration.registered(s, c) }?.first

    /** Stage 2: a selection window that opens on/before [nextDay] and has not been completed yet. */
    private fun selectionStop(s: CareerSave, nextDay: Int): Competition? =
        s.league.filter { c -> c.userTeam != null && Registration.required(s, c) && !Registration.registered(s, c) && c.selectionDate.isNotEmpty() && c.selectionDate <= Schedule.dateOf(s, nextDay).toString() &&
            c.fixtures.any { !it.played && (it.home == c.userTeam || it.away == c.userTeam) } }
            .minByOrNull { it.selectionDate }

    private fun openSelection(compId: String) {
        val c = compById(compId) ?: return
        msg("Selection window open: pick your ${Registration.short(c)} squad (15-16 players).")
        nav(Screen.SeriesSelection(compId))
    }

    private fun openRegistration(compId: String) {
        val c = compById(compId) ?: return
        val r = save?.let { Registration.rule(it, c) }
        msg("Squad registration: pick your ${Registration.short(c)} squad${r?.let { " (${it.sizeText()})" } ?: ""} before the first match.")
        nav(Screen.SquadRegistration(compId))
    }

    /** Engine-only day step (safe off the main thread). */
    private fun advanceDay(s: CareerSave, simUser: Boolean, stopForReg: Boolean = false, stopForSelection: Boolean = true): DayOut {
        if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        val nextDay = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day }
            ?: run { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        // Stage 2: stop on the selection date (5 days before the opener) so the popup can open.
        if (stopForSelection) selectionStop(s, nextDay)?.let {
            val current = Schedule.today(s).toString()
            s.selectionToday = if (s.day <= 0) it.selectionDate else maxOf(current, it.selectionDate)
            return DayOut.Selection(it.id)
        }
        s.selectionToday = ""
        val list = Career.fixturesOnDay(s, nextDay)
        // stop before the day is played when the user's squad for that competition still has to be registered
        // (only when the user's match would be auto-simulated; otherwise the Match Centre's registration gate handles it)
        if (stopForReg && simUser) registrationStop(s, list)?.let { return DayOut.Register(it.id) }
        s.day = nextDay
        if (s.mode == Modes.CLUB && nextDay > Career.LEAGUE_DAYS) s.phase = "PLAYOFFS"
        val hosts = list.map { it.second.home }.toSet()
        var userFx: Pair<Competition, Fixture>? = null
        for ((c, f) in list) {
            val mine = c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam)
            if (mine) { if (simUser || userFx != null) Career.quickSim(s, c, f, rnd) else userFx = c to f }
            else Career.quickSim(s, c, f, rnd)
        }
        if (s.weeklyDoneDay != nextDay) { Career.weeklyUpdate(s, hosts, rnd); s.weeklyDoneDay = nextDay }
        if (userFx != null) return DayOut.Match(PendingMatch(userFx.first.id, userFx.second.id))
        if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        return DayOut.Next
    }

    private fun runDays(simUser: Boolean, maxDays: Int, stopSelection: Boolean = true) {
        val s = save ?: return
        if (busy) return
        busy = true
        val resultBefore = s.lastResult
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) {
                var o: DayOut = DayOut.Next; var n = 0
                while (n++ < maxDays && s.phase != "OFFSEASON") { o = advanceDay(s, simUser, stopForSelection = stopSelection); if (o !is DayOut.Next) break }
                o
            }
            busy = false
            if (save !== s) return@launch
            // Settings > Notifications: toast the user's simulated result (never for a match that is about to be played)
            if (settings.notifyResults && out !is DayOut.Match && s.lastResult.isNotEmpty() && s.lastResult != resultBefore) msg(s.lastResult)
            when (out) {
                is DayOut.SeasonEnd -> { persist(); nav(Screen.SeasonSummary) }
                is DayOut.Match -> { pending = out.pm; sim = null; matchApplied = false; persist(); nav(Screen.PreMatch) }
                is DayOut.Register -> { persist(); openRegistration(out.compId) }
                is DayOut.Selection -> { persist(); openSelection(out.compId) }
                else -> { persist(); if (s.phase == "OFFSEASON") nav(Screen.SeasonSummary) }
            }
        }
    }

    fun simToSeasonEnd() {
        val s = save ?: return
        if (standalone) { simWholeTournament(); return }
        if (s.phase == "PRESEASON") { val err = Career.startSeason(s); if (err != null) { msg(err); return } }
        if (s.phase == "OFFSEASON") { nav(Screen.SeasonSummary); return }
        runDays(true, 500, stopSelection = false)
    }

    /* ---------------- calendar actions (Phase A) ---------------- */
    /** Result of a calendar action, used by the calendar UI to open the right view afterwards. */
    var calendarFocus by mutableStateOf<PendingMatch?>(null)

    /** User fixtures that would be auto-simulated before reaching [f] (shown in a confirmation dialog). */
    fun userMatchesBefore(compId: String, fxId: String): Int {
        val s = save ?: return 0
        val c = compById(compId) ?: return 0; val f = c.fixtures.firstOrNull { it.id == fxId } ?: return 0
        if (s.tournament?.id == c.id) return c.fixtures.count { !it.played && it.id != f.id && it.day < f.day && (it.home == c.userTeam || it.away == c.userTeam) }
        return s.league.sumOf { lc -> lc.fixtures.count { !it.played && it.id != f.id && it.day < f.day && lc.userTeam != null && (it.home == lc.userTeam || it.away == lc.userTeam) } } +
            (if (pending != null && pending?.fixtureId != fxId) 1 else 0)
    }

    /** Engine loop: advance game days up to [target]; earlier user matches are simulated. */
    private fun advanceTo(s: CareerSave, target: Int, simUserOnTarget: Boolean, stopSelection: Boolean = true): DayOut {
        var guard = 0
        while (guard++ < 600) {
            if (s.phase == "OFFSEASON") return DayOut.SeasonEnd
            val nd = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: break
            if (nd > target) break
            val o = advanceDay(s, nd < target || simUserOnTarget, stopForReg = true, stopForSelection = stopSelection)
            if (o !is DayOut.Next) return o
        }
        return DayOut.Next
    }

    /** Clears a pending (not yet started) user match by quick-simulating it, so the calendar can move on. */
    private fun resolvePending(s: CareerSave) {
        val c = pendingComp(); val f = pendingFixture()
        if (c != null && f != null && !f.played) {
            val m = sim
            if (m != null && m.started && !m.finished) { m.simToEnd(); Career.finishFixture(s, c, f, m) }
            else if (m != null && m.finished && !matchApplied) Career.finishFixture(s, c, f, m)
            else if (m == null || !m.finished) Career.quickSim(s, c, f, rnd)
        }
        pending = null; sim = null; matchApplied = false; autoPlay = false
    }

    /**
     * Calendar "Play" / "Simulate" on any fixture.
     *  - play = true on a user fixture: advance to that date (earlier user games are simulated) and open the Match Centre.
     *  - otherwise: advance through that date, simulating the fixture, then focus its result.
     */
    fun calendarGo(compId: String, fxId: String, play: Boolean) {
        val s = save ?: return
        if (standalone) { if (play) playNextTournament() else simTournamentUntilMine(); return }
        if (busy) return
        val c = compById(compId) ?: return
        val f = c.fixtures.firstOrNull { it.id == fxId } ?: return
        if (f.played) { calendarFocus = PendingMatch(compId, fxId); return }
        val isMine = c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam)
        // already the pending match
        if (pending?.fixtureId == fxId) { if (play) nav(Screen.PreMatch) else {
            prepareSim()?.let { if (!it.finished) it.simToEnd() }; simTick++; applyMatch()
            pending = null; sim = null; autoPlay = false; calendarFocus = PendingMatch(compId, fxId)
            if (s.tournament?.id != c.id && Career.leagueFinished(s) && s.phase != "OFFSEASON") { Career.endSeason(s, rnd); nav(Screen.SeasonSummary) }
            persist()
        }; return }
        // side tournament: simple ordered list
        if (s.tournament?.id == c.id) {
            if (pending != null && pendingComp()?.id == c.id) resolvePending(s)
            var g = 0
            while (g++ < 300) {
                val nx = CompEngine.nextUnplayed(c) ?: break
                if (nx.id == f.id || nx.day >= f.day) break
                Career.quickSim(s, c, nx, rnd)
            }
            if (play && isMine) { pending = PendingMatch(c.id, f.id); sim = null; matchApplied = false; persist(); nav(Screen.PreMatch) }
            else { if (!f.played) Career.quickSim(s, c, f, rnd); calendarFocus = PendingMatch(compId, fxId); persist() }
            return
        }
        if (s.phase == "OFFSEASON") { nav(Screen.SeasonSummary); return }
        if (s.phase == "PRESEASON") { val err = Career.startSeason(s); if (err != null) { msg(err); return } }
        if (pending != null) resolvePending(s)
        busy = true
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { advanceTo(s, f.day, !(play && isMine)) }
            busy = false
            if (save !== s) return@launch
            when (out) {
                is DayOut.Match -> {
                    pending = out.pm; sim = null; matchApplied = false; persist()
                    if (out.pm.fixtureId == fxId) nav(Screen.PreMatch) else { msg("Match day! Your next match comes first."); nav(Screen.PreMatch) }
                }
                is DayOut.Register -> { persist(); openRegistration(out.compId) }
                is DayOut.Selection -> { persist(); openSelection(out.compId) }
                is DayOut.SeasonEnd -> { calendarFocus = PendingMatch(compId, fxId); persist(); msg("Season complete.") }
                else -> { calendarFocus = PendingMatch(compId, fxId); persist() }
            }
        }
    }

    /** Calendar: advance exactly one game day (the existing SIMULATE behaviour). */
    fun calendarAdvanceDay() = continueAction()

    /** Calendar: simulate every day until the user's next match (opens the Match Centre for it). */
    fun simToNextUserMatch() {
        val s = save ?: return
        if (standalone) { simTournamentUntilMine(); return }
        if (pending != null) { nav(Screen.PreMatch); return }
        if (s.phase == "OFFSEASON") { nav(Screen.SeasonSummary); return }
        if (s.phase == "PRESEASON") { val err = Career.startSeason(s); if (err != null) { msg(err); return } }
        runDays(false, 600)
    }

    fun beginNextSeason() {
        val s = save ?: return
        Career.beginNextSeason(s)
        persist()
        goHome()
        if (s.auction != null) nav(Screen.AuctionScr)
    }

    /* ---------------- matches ---------------- */
    fun compById(id: String): Competition? { val s = save ?: return null; return s.league.firstOrNull { it.id == id } ?: s.tournament?.takeIf { it.id == id } }
    fun pendingComp(): Competition? = pending?.let { compById(it.compId) }
    fun pendingFixture(): Fixture? { val p = pending ?: return null; return pendingComp()?.fixtures?.firstOrNull { it.id == p.fixtureId } }
    fun userSideId(): String? = pendingComp()?.userTeam

    fun prepareSim(): MatchSim? {
        val s = save ?: return null
        val c = pendingComp() ?: return null; val f = pendingFixture() ?: return null
        if (sim == null) {
            // only reached with a registered squad from the Match Centre; calendar "simulate" registers a default squad
            Registration.ensureForAutoSim(s, c)
            sim = Career.makeSim(s, c, f, rnd).also { it.userTeam = c.userTeam ?: "" }; matchApplied = false
        }
        return sim
    }

    /** Rebuild the (not yet started) match after team changes, keeping venue / pitch / weather. */
    fun rebuildSim(): MatchSim? {
        val s = save ?: return null
        val c = pendingComp() ?: return null; val f = pendingFixture() ?: return null
        val old = sim
        if (old != null && old.started) return old
        sim = Career.makeSim(s, c, f, rnd, old?.cfg).also { it.userTeam = c.userTeam ?: "" }; matchApplied = false
        return sim
    }

    /* ---------------- Phase E: mandatory pre-match selection ---------------- */
    fun pendingRegistrationOk(): Boolean { val s = save ?: return true; val c = pendingComp() ?: return true; return Registration.registered(s, c) }
    fun sheetConfirmed(): Boolean { val s = save ?: return false; val f = pendingFixture() ?: return false; return s.sheetFx == f.id }
    fun sheetEdited() { val s = save ?: return; if (sim?.started != true) s.sheetFx = ""; persist(); tick++ }

    /** Confirm the user's team sheet for the pending match. Returns an error or null. */
    fun confirmTeamSheet(): String? {
        val s = save ?: return "No career"
        val c = pendingComp() ?: return "No match"; val f = pendingFixture() ?: return "No match"
        val me = s.clubs[c.userTeam ?: s.userClubId] ?: return "No team"
        if (!Registration.registered(s, c)) return "Register your ${Registration.short(c)} squad first."
        Registration.sheetErrors(s, c, me).firstOrNull()?.let { return it }
        s.sheetFx = f.id
        rebuildSim()
        persist(); tick++
        return null
    }

    /** Guard for toss / Watch live / Quick sim from the Match Centre. */
    private fun preMatchGate(): Boolean {
        val c = pendingComp() ?: return false
        if (!pendingRegistrationOk()) { msg("Register your ${Registration.short(c)} squad before the match."); nav(Screen.SquadRegistration(c.id)); return false }
        if (sim?.started != true && !sheetConfirmed()) { msg("Confirm your team sheet first."); return false }
        return true
    }

    /** User called the toss. Returns true if the user won. */
    fun callToss(heads: Boolean): Boolean {
        if (!preMatchGate()) return false
        GameAudio.play("coin")
        val m = (if (sim?.started == true) sim else rebuildSim()) ?: return false
        val coin = rnd.nextBoolean()
        val won = coin == heads
        if (!won) {
            val other = m.other(userSideId() ?: m.home.id)
            m.doToss(other, m.autoTossDecision(other)); simTick++
        }
        return won
    }

    fun chooseToss(bat: Boolean) {
        val m = prepareSim() ?: return
        m.doToss(userSideId() ?: m.home.id, if (bat) "BAT" else "BOWL"); simTick++
    }

    fun watchLive() { if (!preMatchGate()) return; prepareSim()?.let { if (!it.started) it.autoToss() }; autoPlay = false; nav(Screen.Live) }

    fun quickSimMatch() {
        if (!preMatchGate()) return
        val m = prepareSim() ?: return
        if (!m.started) m.autoToss()
        m.simToEnd(); simTick++
        applyMatch()
        nav(Screen.Live)
    }

    fun stepBall() { sim?.nextBall(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun stepOver() { sim?.nextOver(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun stepInnings() { sim?.endInnings(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun simRest() { sim?.simToEnd(); simTick++; autoPlay = false; applyMatch() }
    fun declare() { sim?.declare(); simTick++; if (sim?.finished == true) applyMatch() }
    fun setAggression(a: Int) { val m = sim ?: return; val id = userSideId() ?: m.userTeam.takeIf { it.isNotEmpty() } ?: return; m.club(id).aggression = a; simTick++; bump() }

    fun applyMatch() {
        if (matchApplied) return
        val s = save ?: return
        val m = sim ?: return
        if (!m.finished) return
        val c = pendingComp() ?: return; val f = pendingFixture() ?: return
        Career.finishFixture(s, c, f, m)
        matchApplied = true
        persist()
    }

    fun leaveMatch() {
        applyMatch()
        val wasTournament = pendingComp()?.let { it.id == save?.tournament?.id } == true
        pending = null; sim = null; autoPlay = false
        goHome()
        if (wasTournament) nav(Screen.TournamentsScr)
        val s = save
        if (s != null && !wasTournament && Career.leagueFinished(s) && s.phase != "OFFSEASON") { Career.endSeason(s, rnd); persist(); nav(Screen.SeasonSummary) }
    }

    /* ---------------- tournaments ---------------- */
    fun startTournament(type: String, userTeam: String, format: String?, teams: List<String>, matches: Int, double: Boolean, knockout: String) {
        val s = save ?: return
        Tournaments.create(s, type, userTeam, format, teams, matches, double, knockout)
        s.tournament?.let { it.startDate = Schedule.today(s).plusDays(1).toString() }
        persist()
    }

    fun playNextTournament() {
        val s = save ?: return
        val t = s.tournament ?: return
        val fx = CompEngine.nextUnplayed(t) ?: run { msg("Tournament complete!"); return }
        if (fx.home == t.userTeam || fx.away == t.userTeam) {
            pending = PendingMatch(t.id, fx.id); sim = null; matchApplied = false; nav(Screen.PreMatch)
        } else { Career.quickSim(s, t, fx, rnd); persist() }
    }

    fun simTournamentUntilMine() {
        val s = save ?: return
        val t = s.tournament ?: return
        var g = 0
        while (g++ < 200) {
            val fx = CompEngine.nextUnplayed(t) ?: break
            if (fx.home == t.userTeam || fx.away == t.userTeam) break
            Career.quickSim(s, t, fx, rnd)
        }
        persist()
    }

    fun simWholeTournament() {
        val s = save ?: return
        val t = s.tournament ?: return
        var g = 0
        while (g++ < 300) { val fx = CompEngine.nextUnplayed(t) ?: break; Career.quickSim(s, t, fx, rnd) }
        persist()
    }

    fun closeTournament() { save?.tournament = null; persist() }

    /* ---------------- auction ---------------- */
    fun startMiniAuction() {
        val s = save ?: return
        if (s.phase != "PRESEASON") { msg("Auctions happen in pre-season."); return }
        if (s.auctionDoneThisSeason) { msg("This season's auction is complete."); return }
        if (s.auction == null) s.auction = Auction.start(s, "MINI")
        persist(); nav(Screen.AuctionScr)
    }
    fun confirmRetention(ids: List<String>) { val s = save ?: return; val a = s.auction ?: return; Auction.confirmRetention(s, a, ids); persist() }
    fun auctionBid() {
        val s = save ?: return; val a = s.auction ?: return
        val err = Auction.userBid(s, a)
        if (err == null) GameAudio.auctionPaddle() else GameAudio.play("error", 0.7f)
        msg(err); bump()
    }
    /** Sound cue for whatever the last auction step did (rival paddle, going once/twice, SOLD / UNSOLD). */
    private fun auctionCue(s: CareerSave, a: AuctionState, bidSeq: Int, countdown: Int, lastSeq: Int) {
        when {
            a.lastSeq != lastSeq -> if (a.lastClub.isNotEmpty()) GameAudio.auctionSold(a.lastClub == Auction.team(s, a).id) else GameAudio.auctionUnsold()
            a.bidSeq != bidSeq && a.curBidder != Auction.team(s, a).id -> GameAudio.auctionRivalBid()
            a.countdown != countdown && a.countdown > 0 -> GameAudio.auctionGoing(a.countdown)
        }
    }
    fun auctionTick() {
        val s = save ?: return; val a = s.auction ?: return
        val b = a.bidSeq; val c = a.countdown; val l = a.lastSeq
        val hammered = Auction.tick(s, a, rnd)
        auctionCue(s, a, b, c, l)
        if (hammered) persist() else bump()
    }
    fun auctionPass() { val s = save ?: return; val a = s.auction ?: return; val b = a.bidSeq; val c = a.countdown; val l = a.lastSeq; Auction.resolveLot(s, a, rnd); auctionCue(s, a, b, c, l); persist() }
    fun auctionAuto(forMe: Boolean = true) { val s = save ?: return; val a = s.auction ?: return; Auction.autoComplete(s, a, rnd, bidForUser = forMe); GameAudio.play("sold"); persist() }

    /* ---------------- generic actions ---------------- */
    fun act(f: (CareerSave) -> String?) { val s = save ?: return; msg(f(s)); persist() }
}
