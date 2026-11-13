package com.cricketcoach.manager.game

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Cricket calendar (Phase A).
 *
 * The match engine schedules fixtures on abstract "game days" (1, 2, 3...). This object maps those days onto real
 * calendar dates and adds the off-field events (auction, transfer window, training / rest days, season milestones)
 * so the UI can show a month-by-month schedule centre.
 *
 * IMPORTANT: every date produced here is a GENERATED, FICTIONAL schedule for the game. It is not an official
 * BCCI / ICC / IPL fixture list and the UI must always label it that way ([DISCLAIMER]).
 */
object Schedule {
    const val DISCLAIMER = "Generated / fictional schedule — not an official fixture list"
    /** Calendar footer: official lists are named, everything else is generated. */
    fun sourceNote(s: CareerSave): String {
        val off = s.league.filter { it.source.isNotEmpty() }
        return if (off.isEmpty()) DISCLAIMER else off.joinToString(" · ") { it.source } + if (off.size < s.league.size) " · other fixtures generated" else ""
    }

    /** Date text in the user's chosen style (Settings > Language > Date format): "IN" 28 Mar 2026, "US" Mar 28, 2026, "ISO" 2026-03-28. */
    fun fmt(d: LocalDate, style: String = "IN", dow: Boolean = false): String {
        val pat = when (style) { "US" -> if (dow) "EEE, MMM d, yyyy" else "MMM d, yyyy"; "ISO" -> if (dow) "EEE yyyy-MM-dd" else "yyyy-MM-dd"; else -> if (dow) "EEE d MMM yyyy" else "d MMM yyyy" }
        return d.format(java.time.format.DateTimeFormatter.ofPattern(pat, java.util.Locale.ENGLISH))
    }

    // categories (filter chips)
    const val INTL = "INTL"; const val IPL = "IPL"; const val DOMESTIC = "DOMESTIC"; const val TOURN = "TOURN"
    const val AUCTION = "AUCTION"; const val TRANSFER = "TRANSFER"; const val TRAINING = "TRAINING"; const val EVENT = "EVENT"
    val CATEGORIES = listOf(INTL, IPL, DOMESTIC, TOURN, AUCTION, TRANSFER, TRAINING, EVENT)
    fun catLabel(c: String) = when (c) {
        INTL -> "International"; IPL -> "IPL"; DOMESTIC -> "Domestic"; TOURN -> "Tournaments"; AUCTION -> "Auction"
        TRANSFER -> "Transfers"; TRAINING -> "Training / Rest"; else -> "Key events"
    }

    private val ICC = setOf("ODI_WC", "T20_WC", "CT", "WTC")

    /** One calendar entry. Match entries carry the competition + fixture; others are plain events. */
    class Entry(
        val date: LocalDate, val cat: String, val title: String, val sub: String = "",
        val comp: Competition? = null, val fx: Fixture? = null, val mine: Boolean = false, val cats: Set<String> = setOf(cat),
        val endDate: LocalDate = date
    ) { val isMatch get() = fx != null }

    /** First day of the season for each career type (fictional but plausible windows). */
    fun seasonStart(s: CareerSave): LocalDate = officialStart(s) ?: when (s.mode) {
        Modes.IPL -> LocalDate.of(s.year, 3, 22)
        Modes.INTL -> LocalDate.of(s.year, 6, 1)
        Modes.DOMESTIC -> LocalDate.of(s.year, 9, 20)
        else -> LocalDate.of(s.year, 4, 10)
    }

    /** First official fixture date of the season (game day 1), when the season uses a verified fixture list. */
    private fun officialStart(s: CareerSave): LocalDate? {
        val f = s.league.asSequence().filter { it.source.isNotEmpty() }.flatMap { it.fixtures.asSequence() }.filter { it.date.isNotEmpty() && it.day >= 1 }.minByOrNull { it.day } ?: return null
        return runCatching { LocalDate.parse(f.date).minusDays((f.day - 1).toLong()) }.getOrNull()
    }

    /** Length of a fixture in calendar days (Tests 5, first-class 4, everything else 1). */
    fun duration(c: Competition, f: Fixture): Int = when {
        Modes.fourDay(c, f) -> 4
        c.format == "TEST" -> 5
        else -> 1
    }

    /** Calendar days to the next game day after a match of this kind (match length + travel / rest). */
    private fun gapAfter(c: Competition, f: Fixture): Int = when {
        c.kind == "CLUB" -> if (c.format == "TEST") 6 else if (c.format == "ODI") 4 else 3
        c.format == "TEST" -> duration(c, f) + if (c.kind == "NATION") 2 else 1
        c.format == "ODI" -> if (c.kind == "NATION") 3 else 2
        c.kind == "FRANCHISE" -> 2
        c.kind == "NATION" -> 2
        else -> 1
    }

    // cumulative offsets (in calendar days) of each game day from the season start; cached per save state
    private var cacheKey: Any? = null
    private var cacheOffsets = IntArray(0)

    private fun offsets(s: CareerSave): IntArray {
        val nFx = s.league.sumOf { it.fixtures.size }
        val key = listOf(System.identityHashCode(s), s.season, s.mode, nFx)
        if (key == cacheKey) return cacheOffsets
        val gap = HashMap<Int, Int>()
        var maxDay = 1
        for (c in s.league) for (f in c.fixtures) {
            val d = f.day.coerceAtLeast(1); maxDay = maxOf(maxDay, d)
            gap[d] = maxOf(gap[d] ?: 1, gapAfter(c, f))
        }
        // official fixtures pin their game day to the real date (offset from the season start)
        val start = officialStart(s)
        val pinned = HashMap<Int, Int>()
        if (start != null) for (c in s.league) if (c.source.isNotEmpty()) for (f in c.fixtures) if (f.date.isNotEmpty() && f.day >= 1)
            runCatching { pinned[f.day] = ChronoUnit.DAYS.between(start, LocalDate.parse(f.date)).toInt() }
        val off = IntArray(maxDay + 2)
        for (d in 2..maxDay + 1) {
            val natural = off[d - 1] + (gap[d - 1] ?: 1)
            off[d] = pinned[d]?.takeIf { it > off[d - 1] } ?: natural
        }
        cacheKey = key; cacheOffsets = off
        return off
    }

    fun dateOf(s: CareerSave, day: Int): LocalDate {
        val d = day.coerceAtLeast(1)
        val off = offsets(s)
        val o = if (d < off.size) off[d] else off.last() + (d - (off.size - 1))
        return seasonStart(s).plusDays(o.toLong())
    }

    /** Inverse of [dateOf]: the last game day that starts on or before the date. */
    fun dayOf(s: CareerSave, d: LocalDate): Int {
        val off = offsets(s)
        val o = ChronoUnit.DAYS.between(seasonStart(s), d).toInt()
        if (o < 0) return o
        if (o > off.last()) return off.size - 1 + (o - off.last())
        var best = 1
        for (i in 1 until off.size) if (off[i] <= o) best = i else break
        return best
    }

    /** "Today" in the career: pre-season sits a week before the opener, off-season a week after the last match. */
    fun today(s: CareerSave): LocalDate = runCatching { LocalDate.parse(s.selectionToday) }.getOrNull() ?: when (s.phase) {
        "PRESEASON" -> seasonStart(s).minusDays(7)
        "OFFSEASON" -> lastSeasonDate(s).plusDays(7)
        else -> if (s.day <= 0) seasonStart(s).minusDays(1) else dateOf(s, s.day)
    }

    fun lastSeasonDate(s: CareerSave): LocalDate = dateOf(s, s.league.flatMap { it.fixtures }.maxOfOrNull { it.day } ?: 1)

    /** Side tournaments (Tournaments screen) are not on game days; they start on their stored date (or after the season). */
    fun tournamentStart(s: CareerSave, t: Competition): LocalDate =
        runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: today(s).plusDays(1)

    fun category(s: CareerSave, c: Competition): Set<String> {
        if (s.tournament?.id == c.id) return setOf(TOURN) + (if (c.kind == "NATION") setOf(INTL) else if (c.kind == "FRANCHISE") setOf(IPL) else emptySet())
        return when {
            c.key in ICC -> setOf(INTL, TOURN)
            c.kind == "NATION" -> setOf(INTL)
            c.kind == "FRANCHISE" -> setOf(IPL)
            else -> setOf(DOMESTIC)
        }
    }

    fun formatLabel(c: Competition, f: Fixture): String = when {
        Modes.fourDay(c, f) -> "4-DAY"
        c.format == "TEST" -> if (c.kind == "NATION") "TEST" else "FIRST-CLASS"
        c.format == "ODI" -> if (c.kind == "NATION") "ODI" else "50 OVERS"
        c.format == "T20" -> if (c.kind == "NATION") "T20I" else "T20"
        else -> c.format
    }

    /** Fictional start time (local) by format; IPL double-headers get an afternoon slot. */
    fun startTime(s: CareerSave, c: Competition, f: Fixture): String {
        if (f.time.isNotEmpty()) return f.time
        if (c.format == "TEST") return "09:30"
        if (c.format == "ODI") return if (c.kind == "NATION") "14:00" else "09:00"
        if (c.kind == "FRANCHISE") {
            val same = c.fixtures.filter { it.day == f.day }.sortedBy { it.id }
            return if (same.size > 1 && same.first().id == f.id) "15:30" else "19:30"
        }
        return if (c.kind == "NATION") "19:00" else "16:30"
    }

    fun venue(s: CareerSave, f: Fixture): String {
        if (f.venue.isNotEmpty()) return f.venue
        val h = s.clubs[f.home]
        return h?.stadium?.takeIf { it.isNotBlank() } ?: Tournaments.venueOf(f.home) ?: Modes.stateOf(f.home)?.venue ?: "${h?.name ?: "Home"} ground"
    }

    /** Match status shown on cards. */
    fun status(s: CareerSave, c: Competition, f: Fixture, pendingFixtureId: String?): String = when {
        f.played -> if (f.res?.noResult == true) "NO RESULT" else "RESULT"
        f.id == pendingFixtureId -> "MATCH DAY"
        s.tournament?.id != c.id && s.phase != "PRESEASON" && f.day == s.day -> "TODAY"
        else -> "UPCOMING"
    }

    fun fixtureDate(s: CareerSave, c: Competition, f: Fixture): LocalDate =
        if (s.tournament?.id == c.id) tournamentStart(s, c).plusDays(((f.day.coerceAtLeast(1) - 1) * gapAfter(c, f)).toLong()) else dateOf(s, f.day)

    /** All matches of the career (league competitions + the side tournament). */
    fun matches(s: CareerSave): List<Entry> {
        val out = ArrayList<Entry>()
        val comps = s.league + listOfNotNull(s.tournament)
        for (c in comps) {
            val cats = category(s, c)
            for (f in c.fixtures) {
                val h = s.clubs[f.home]; val a = s.clubs[f.away]
                val mine = c.userTeam != null && (f.home == c.userTeam || f.away == c.userTeam)
                val d0 = fixtureDate(s, c, f)
                out.add(Entry(d0, cats.first(), "${h?.short ?: f.home} v ${a?.short ?: f.away}", "${c.name} · ${f.stage}",
                    c, f, mine, cats, endDate = d0.plusDays((duration(c, f) - 1).toLong())))
            }
        }
        return out
    }

    /** Off-field events: auction, transfer window, season milestones, plus training / rest days for the user's team. */
    fun events(s: CareerSave, matches: List<Entry> = matches(s)): List<Entry> {
        val out = ArrayList<Entry>()
        val start = seasonStart(s)
        if (Modes.hasTransfers(s)) out.add(Entry(start.minusDays(30), TRANSFER, "Pre-season transfer window opens",
            "Sign free agents, list players and answer offers before the opener", endDate = start.minusDays(1)))
        if (Modes.hasAuction(s)) {
            val done = s.auctionDoneThisSeason || s.auction?.done == true
            val live = s.auction != null && s.auction?.done != true
            val name = if (s.mode == Modes.IPL) "IPL player auction" else "Player auction"
            out.add(Entry(start.minusDays(12), AUCTION, name, when { live -> "In progress — open the auction room"; done -> "Completed"; else -> "Optional mini auction (pre-season)" }))
        }
        out.add(Entry(start, EVENT, "Season ${s.season} opener", "First match day of ${s.year}"))
        // Stage 2: selection windows (match date - 5 days) are shown on the calendar alongside match dates.
        for (c in s.league) {
            if (c.userTeam == null || c.selectionDate.isEmpty()) continue
            val d = runCatching { LocalDate.parse(c.selectionDate) }.getOrNull() ?: continue
            val done = c.regDone
            out.add(Entry(d, EVENT, if (done) "Squad registered · ${Registration.short(c)}" else "Squad selection · ${Registration.short(c)}",
                if (done) "${c.reg.size} players locked in" else "Pick 15-16 players · 5 days before the opener", c, cats = setOf(EVENT) + category(s, c)))
        }
        for (c in s.league) {
            val ko = c.fixtures.filter { !(it.stage.startsWith("League") || it.stage.startsWith("Group") || it.stage.startsWith("Match")) }
            val first = c.fixtures.minByOrNull { it.day }
            if (first != null && s.league.size > 1) out.add(Entry(dateOf(s, first.day), EVENT, "${c.name} begins", "${c.teams.size} teams", c, cats = setOf(EVENT) + category(s, c)))
            ko.minByOrNull { it.day }?.let { out.add(Entry(dateOf(s, it.day), EVENT, "${c.name}: knockouts", it.stage, c)) }
            c.fixtures.firstOrNull { it.stage == "Final" }?.let { out.add(Entry(dateOf(s, it.day), EVENT, "${c.name} FINAL", if (c.champion != null) "Champions: ${s.clubs[c.champion!!]?.name}" else "Winner lifts the trophy", c)) }
        }
        if (s.league.isNotEmpty()) out.add(Entry(lastSeasonDate(s).plusDays(2), EVENT, "Season awards & review", "Player of the season, records and board review"))
        s.tournament?.let { t -> out.add(Entry(tournamentStart(s, t), EVENT, "${t.name.ifBlank { "Tournament" }} starts", "Side tournament", t, cats = setOf(EVENT, TOURN))) }
        // training / rest days for the user's side between the first and last of their matches
        val mineDates = matches.filter { it.mine && s.tournament?.id != it.comp?.id }
            .flatMap { e -> (0..ChronoUnit.DAYS.between(e.date, e.endDate)).map { e.date.plusDays(it) } }.toSortedSet()
        if (mineDates.isNotEmpty()) {
            var d = minOf(start, mineDates.first()); val end = mineDates.last()
            var guard = 0
            while (!d.isAfter(end) && guard++ < 800) {
                if (d !in mineDates) {
                    val rest = d.minusDays(1) in mineDates
                    out.add(Entry(d, TRAINING, if (rest) "Rest & recovery" else "Training day", if (rest) "Light session, physio and ice baths" else "Nets, fielding drills and fitness"))
                }
                d = d.plusDays(1)
            }
        }
        return out
    }

    fun all(s: CareerSave): List<Entry> { val m = matches(s); return (m + events(s, m)).sortedWith(compareBy<Entry>({ it.date }, { if (it.isMatch) 0 else 1 })) }

    /** Entries touching a given date (multi-day events included). */
    fun on(list: List<Entry>, d: LocalDate): List<Entry> = list.filter { !d.isBefore(it.date) && !d.isAfter(it.endDate) }

    fun inMonth(list: List<Entry>, ym: YearMonth): List<Entry> = list.filter { YearMonth.from(it.date) == ym || YearMonth.from(it.endDate) == ym }

    /** Month to open the calendar on: the month of "today", or the month of the user's next match when that is sooner to look at. */
    fun openMonth(s: CareerSave): YearMonth = YearMonth.from(today(s))
}
