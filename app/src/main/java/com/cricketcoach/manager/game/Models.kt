package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.Ratings

/* All game-state classes use default values for every field so Gson can
 * construct them reliably when loading a save. */

class GPlayer(
    var id: String = "", var name: String = "", var country: String = "", var role: String = "BAT",
    var bowlType: String = "PACE", var birthYear: Int = 2000, var ageEst: Boolean = false,
    var tech: Int = 50, var power: Int = 50, var bowl: Int = 30, var field: Int = 60, var keep: Int = 30,
    var potential: Int = 70, var scouted: Boolean = false,
    var fitness: Int = 100, var morale: Int = 70, var form: Int = 60, var injuryDays: Int = 0, var injury: String = "",
    var clubId: String = "", var salary: Double = 0.2, var contractYears: Int = 2, var retired: Boolean = false,
    var fictional: Boolean = false, var forSale: Boolean = false, var trainXp: Double = 0.0, var benchCount: Int = 0,
    var academy: Boolean = false, var real: Boolean = true,
    var history: MutableList<String> = mutableListOf(),
    var careerM: Int = 0, var careerRuns: Int = 0, var careerWk: Int = 0
) {
    fun overall(): Int = Ratings.overall(role, tech, power, bowl)
    fun age(year: Int): Int = year - birthYear
    fun batSkill(): Int = ((tech + power) / 2.0).toInt()
    val injured: Boolean get() = injuryDays > 0
    fun short(): String {
        val parts = name.split(" ")
        return if (parts.size >= 2) parts.last() else name
    }
}

class Club(
    var id: String = "", var name: String = "", var short: String = "",
    var primary: Long = 0xFF00C853, var secondary: Long = 0xFFFFC107,
    var logo: Int = 0, var jersey: Int = 0, var kind: String = "CLUB", var country: String = "India",
    var division: Int = 4, var isUser: Boolean = false, var reputation: Int = 30, var balance: Double = 20.0,
    var stadium: String = "", var capacity: Int = 15000, var ticketPrice: Int = 500,
    var trainingLvl: Int = 1, var academyLvl: Int = 1, var medicalLvl: Int = 1,
    var coachBat: Int = 1, var coachBowl: Int = 1, var coachFit: Int = 1, var scoutLvl: Int = 1,
    var sponsor: String = "", var sponsorValue: Double = 0.0,
    var squad: MutableList<String> = mutableListOf(), var xi: MutableList<String> = mutableListOf(),
    var bowlers: MutableList<String> = mutableListOf(),
    var captain: String = "", var vice: String = "", var keeper: String = "", var impact: String = "",
    var aggression: Int = 1, var trainIntensity: Int = 1, var trainFocus: Int = 0,
    var academyList: MutableList<String> = mutableListOf(),
    var income: MutableMap<String, Double> = mutableMapOf(), var expense: MutableMap<String, Double> = mutableMapOf(),
    var lastIncome: MutableMap<String, Double> = mutableMapOf(), var lastExpense: MutableMap<String, Double> = mutableMapOf(),
    var lastAttendance: Int = 0, var trophies: MutableList<String> = mutableListOf(),
    /** Optional custom badge uploaded by the user (path inside the app's private storage). */
    var logoFile: String = "",
    /** v2.5 Phase E: bowling role per player (NEW new ball / powerplay, MID middle overs, DEATH death overs, PART part-timer; missing = AUTO) */
    var bowlRoles: MutableMap<String, String> = mutableMapOf()
)

class MatchSummary(
    var winner: String? = null, var text: String = "", var draw: Boolean = false, var noResult: Boolean = false,
    var hR: Int = 0, var hW: Int = 0, var hB: Int = 0, var hMaxB: Int = 120,
    var aR: Int = 0, var aW: Int = 0, var aB: Int = 0, var aMaxB: Int = 120,
    var hScore: String = "", var aScore: String = "", var potm: String = "",
    var card: MutableList<String> = mutableListOf()
)

class Fixture(
    var id: String = "", var home: String = "", var away: String = "", var stage: String = "League",
    var group: Int = 0, var day: Int = 0, var played: Boolean = false, var res: MatchSummary? = null
)

class BatAgg(
    var m: Int = 0, var inn: Int = 0, var runs: Int = 0, var balls: Int = 0, var no: Int = 0, var hs: Int = 0,
    var hsNo: Boolean = false, var f50: Int = 0, var f100: Int = 0, var fours: Int = 0, var sixes: Int = 0, var team: String = ""
) {
    fun avg(): Double? = if (inn - no > 0) runs.toDouble() / (inn - no) else null
    fun sr(): Double? = if (balls > 0) runs * 100.0 / balls else null
}

class BowlAgg(
    var m: Int = 0, var balls: Int = 0, var runs: Int = 0, var wk: Int = 0, var bbW: Int = 0, var bbR: Int = 999,
    var w5: Int = 0, var team: String = "", var ct: Int = 0
) {
    fun econ(): Double? = if (balls > 0) runs * 6.0 / balls else null
    fun avg(): Double? = if (wk > 0) runs.toDouble() / wk else null
}

class RecEntry(
    var who: String = "", var team: String = "", var opp: String = "", var a: Int = 0, var b: Int = 0,
    var flag: Boolean = false, var season: Int = 0, var comp: String = ""
)

class RecordBook(
    var label: String = "", var format: String = "T20",
    var bat: MutableMap<String, BatAgg> = mutableMapOf(), var bowl: MutableMap<String, BowlAgg> = mutableMapOf(),
    var highScores: MutableList<RecEntry> = mutableListOf(), var bestBowl: MutableList<RecEntry> = mutableListOf(),
    var teamHigh: MutableList<RecEntry> = mutableListOf(), var teamLow: MutableList<RecEntry> = mutableListOf()
)

class Award(
    var season: Int = 0, var comp: String = "", var champion: String = "", var runnerUp: String = "",
    var orange: String = "", var orangeRuns: Int = 0, var purple: String = "", var purpleWk: Int = 0,
    var mvp: String = "", var mvpPts: Int = 0
)

class Competition(
    var id: String = "", var name: String = "", var key: String = "", var format: String = "T20",
    var kind: String = "CLUB", var teams: MutableList<String> = mutableListOf(),
    var groups: MutableList<MutableList<String>> = mutableListOf(),
    var fixtures: MutableList<Fixture> = mutableListOf(), var knockout: String = "NONE",
    var stage: String = "LEAGUE", var champion: String? = null, var runnerUp: String? = null,
    var userTeam: String? = null, var division: Int = 0, var season: Int = 1,
    var pointsWin: Int = 2, var pointsDraw: Int = 1, var pointsNR: Int = 1,
    var bat: MutableMap<String, BatAgg> = mutableMapOf(), var bowlS: MutableMap<String, BowlAgg> = mutableMapOf(),
    var series: Boolean = false, var allowImpact: Boolean = false, var nextFixtureId: Int = 1,
    // v2.5 calendar: ISO start date for side tournaments (league comps derive dates from game days)
    var startDate: String = "",
    // v2.5 Phase E: the user team's registered tournament squad (kept separate from the club squad and the XI)
    var reg: MutableList<String> = mutableListOf(), var regDone: Boolean = false, var regAuto: Boolean = false,
    var regRepl: Int = 0, var regLog: MutableList<String> = mutableListOf()
)

class AuctionSale(var pid: String = "", var club: String = "", var price: Double = 0.0)

class AuctionState(
    var type: String = "MINI", var phase: String = "LIVE", var lots: MutableList<String> = mutableListOf(),
    var index: Int = 0, var curBid: Double = 0.0, var curBidder: String = "", var countdown: Int = 0,
    var log: MutableList<String> = mutableListOf(), var sold: MutableList<AuctionSale> = mutableListOf(),
    var unsold: MutableList<String> = mutableListOf(), var userPassed: Boolean = false,
    var aiVal: MutableMap<String, Double> = mutableMapOf(), var done: Boolean = false,
    /** presentation (Phase 6): bid counter, clubs that raised a paddle on the current lot, and the last hammer result */
    var bidSeq: Int = 0, var lotBidders: MutableList<String> = mutableListOf(),
    var lastSeq: Int = 0, var lastPid: String = "", var lastClub: String = "", var lastPrice: Double = 0.0,
    /** v2.5 broadcast: players kept by their clubs in the retention phase (price = retention salary) */
    var retained: MutableList<AuctionSale> = mutableListOf()
)

class TransferOffer(var id: Int = 0, var pid: String = "", var fromClub: String = "", var fee: Double = 0.0, var swapPid: String = "")
class SponsorOffer(var name: String = "", var value: Double = 0.0, var bonus: String = "")

class CareerSave(
    var slot: Int = 1, var manager: String = "", var userClubId: String = "", var season: Int = 1, var year: Int = 2027,
    var day: Int = 0, var phase: String = "PRESEASON", var economy: Double = 1.0,
    var players: MutableMap<String, GPlayer> = mutableMapOf(), var clubs: MutableMap<String, Club> = mutableMapOf(),
    var league: MutableList<Competition> = mutableListOf(), var tournament: Competition? = null,
    var records: MutableMap<String, RecordBook> = mutableMapOf(), var awards: MutableList<Award> = mutableListOf(),
    var news: MutableList<String> = mutableListOf(), var history: MutableList<String> = mutableListOf(),
    var auction: AuctionState? = null, var offers: MutableList<TransferOffer> = mutableListOf(),
    var sponsorOffers: MutableList<SponsorOffer> = mutableListOf(), var nextId: Int = 1,
    var auctionDoneThisSeason: Boolean = false, var lastResult: String = "", var seasonSummary: MutableList<String> = mutableListOf(),
    var created: Long = 0L, var difficulty: Int = 1, var weeklyDoneDay: Int = -1,
    // v2 career types + social
    var mode: String = "CLUB", var followers: Int = 5000, var posts: MutableList<SocialPost> = mutableListOf(),
    var boardRating: Int = 60, var guestPros: Int = 0, var pendingCoins: Int = 0, var pendingXp: Int = 0,
    // v2.2 inbox: newest news item the user has already seen
    var newsSeen: String = "",
    // v2.5 Phase D: Stumpstagram (followed players, earned followers, post counters)
    var stumps: StumpsState = StumpsState(),
    // v2.5 Phase E: fixture id whose team sheet the user confirmed in the Match Centre
    var sheetFx: String = ""
) {
    fun userClub(): Club = clubs[userClubId]!!
    fun p(id: String): GPlayer? = players[id]
    fun addNews(s: String) { news.add(0, "S$season D$day • $s"); if (news.size > 120) news.removeAt(news.size - 1) }
}

class SocialComment(var author: String = "", var handle: String = "", var text: String = "", var likes: Int = 0)

class SocialPost(
    var id: Int = 0, var author: String = "", var handle: String = "", var verified: Boolean = false, var kind: String = "NEWS",
    var text: String = "", var likes: Int = 0, var reposts: Int = 0, var comments: MutableList<SocialComment> = mutableListOf(),
    var season: Int = 1, var day: Int = 0, var liked: Boolean = false, var tags: MutableList<String> = mutableListOf(),
    var shared: Boolean = false, var mine: Boolean = false,
    // v2.2 poster card: featured player / team, big headline + stat, and a small stat table ("left|right" rows)
    var pid: String = "", var club: String = "", var opp: String = "", var headline: String = "", var stat: String = "",
    var lines: MutableList<String> = mutableListOf(),
    // v2.5 Stumpstagram: the player who wrote the post ("" = media / fan / coach account) and the total comment count
    var authorPid: String = "", var commentCount: Int = 0
)
