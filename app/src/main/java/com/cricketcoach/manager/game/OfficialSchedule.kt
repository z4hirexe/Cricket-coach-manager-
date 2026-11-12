package com.cricketcoach.manager.game

import java.time.LocalDate

/**
 * Verified official fixture lists bundled with the game (v2.6).
 *
 * IPL 2026: the 70 league fixtures + 4 playoff slots exactly as published by the BCCI (full league schedule announced
 * 26 March 2026; dates, IST start times and venues as listed on the 2026 Indian Premier League article / ESPNcricinfo).
 * Only the SCHEDULE is official - every result in a career is simulated by the game engine. Competitions without a
 * verifiable official list (international tours, domestic seasons, later years) stay GENERATED and are labelled so.
 */
object OfficialSchedule {
    const val IPL_2026_SOURCE = "Official IPL 2026 schedule (BCCI, announced 26 Mar 2026) - results are simulated"
    const val GENERATED = "Generated / fictional schedule - not an official fixture list"

    class OFx(val no: String, val date: LocalDate, val time: String, val home: String, val away: String, val venue: String) {
        val playoff get() = home.isEmpty()
    }

    private val IPL_2026_RAW = listOf(
        "1|2026-03-28|19:30|F_RCB|F_SRH|M. Chinnaswamy Stadium, Bengaluru",
        "2|2026-03-29|19:30|F_MI|F_KKR|Wankhede Stadium, Mumbai",
        "3|2026-03-30|19:30|F_RR|F_CSK|ACA Cricket Stadium, Guwahati",
        "4|2026-03-31|19:30|F_PBKS|F_GT|Maharaja Yadavindra Singh Stadium, Mullanpur",
        "5|2026-04-01|19:30|F_LSG|F_DC|Ekana Cricket Stadium, Lucknow",
        "6|2026-04-02|19:30|F_KKR|F_SRH|Eden Gardens, Kolkata",
        "7|2026-04-03|19:30|F_CSK|F_PBKS|M. A. Chidambaram Stadium, Chennai",
        "8|2026-04-04|15:30|F_DC|F_MI|Arun Jaitley Stadium, Delhi",
        "9|2026-04-04|19:30|F_GT|F_RR|Narendra Modi Stadium, Ahmedabad",
        "10|2026-04-05|15:30|F_SRH|F_LSG|Rajiv Gandhi Stadium, Hyderabad",
        "11|2026-04-05|19:30|F_RCB|F_CSK|M. Chinnaswamy Stadium, Bengaluru",
        "12|2026-04-06|19:30|F_KKR|F_PBKS|Eden Gardens, Kolkata",
        "13|2026-04-07|19:30|F_RR|F_MI|ACA Cricket Stadium, Guwahati",
        "14|2026-04-08|19:30|F_DC|F_GT|Arun Jaitley Stadium, Delhi",
        "15|2026-04-09|19:30|F_KKR|F_LSG|Eden Gardens, Kolkata",
        "16|2026-04-10|19:30|F_RR|F_RCB|ACA Cricket Stadium, Guwahati",
        "17|2026-04-11|15:30|F_PBKS|F_SRH|Maharaja Yadavindra Singh Stadium, Mullanpur",
        "18|2026-04-11|19:30|F_CSK|F_DC|M. A. Chidambaram Stadium, Chennai",
        "19|2026-04-12|15:30|F_LSG|F_GT|Ekana Cricket Stadium, Lucknow",
        "20|2026-04-12|19:30|F_MI|F_RCB|Wankhede Stadium, Mumbai",
        "21|2026-04-13|19:30|F_SRH|F_RR|Rajiv Gandhi Stadium, Hyderabad",
        "22|2026-04-14|19:30|F_CSK|F_KKR|M. A. Chidambaram Stadium, Chennai",
        "23|2026-04-15|19:30|F_RCB|F_LSG|M. Chinnaswamy Stadium, Bengaluru",
        "24|2026-04-16|19:30|F_MI|F_PBKS|Wankhede Stadium, Mumbai",
        "25|2026-04-17|19:30|F_GT|F_KKR|Narendra Modi Stadium, Ahmedabad",
        "26|2026-04-18|15:30|F_RCB|F_DC|M. Chinnaswamy Stadium, Bengaluru",
        "27|2026-04-18|19:30|F_SRH|F_CSK|Rajiv Gandhi Stadium, Hyderabad",
        "28|2026-04-19|15:30|F_KKR|F_RR|Eden Gardens, Kolkata",
        "29|2026-04-19|19:30|F_PBKS|F_LSG|Maharaja Yadavindra Singh Stadium, Mullanpur",
        "30|2026-04-20|19:30|F_GT|F_MI|Narendra Modi Stadium, Ahmedabad",
        "31|2026-04-21|19:30|F_SRH|F_DC|Rajiv Gandhi Stadium, Hyderabad",
        "32|2026-04-22|19:30|F_LSG|F_RR|Ekana Cricket Stadium, Lucknow",
        "33|2026-04-23|19:30|F_MI|F_CSK|Wankhede Stadium, Mumbai",
        "34|2026-04-24|19:30|F_RCB|F_GT|M. Chinnaswamy Stadium, Bengaluru",
        "35|2026-04-25|15:30|F_DC|F_PBKS|Arun Jaitley Stadium, Delhi",
        "36|2026-04-25|19:30|F_RR|F_SRH|Sawai Mansingh Stadium, Jaipur",
        "37|2026-04-26|15:30|F_CSK|F_GT|M. A. Chidambaram Stadium, Chennai",
        "38|2026-04-26|19:30|F_LSG|F_KKR|Ekana Cricket Stadium, Lucknow",
        "39|2026-04-27|19:30|F_DC|F_RCB|Arun Jaitley Stadium, Delhi",
        "40|2026-04-28|19:30|F_PBKS|F_RR|Maharaja Yadavindra Singh Stadium, Mullanpur",
        "41|2026-04-29|19:30|F_MI|F_SRH|Wankhede Stadium, Mumbai",
        "42|2026-04-30|19:30|F_GT|F_RCB|Narendra Modi Stadium, Ahmedabad",
        "43|2026-05-01|19:30|F_RR|F_DC|Sawai Mansingh Stadium, Jaipur",
        "44|2026-05-02|19:30|F_CSK|F_MI|M. A. Chidambaram Stadium, Chennai",
        "45|2026-05-03|15:30|F_SRH|F_KKR|Rajiv Gandhi Stadium, Hyderabad",
        "46|2026-05-03|19:30|F_GT|F_PBKS|Narendra Modi Stadium, Ahmedabad",
        "47|2026-05-04|19:30|F_MI|F_LSG|Wankhede Stadium, Mumbai",
        "48|2026-05-05|19:30|F_DC|F_CSK|Arun Jaitley Stadium, Delhi",
        "49|2026-05-06|19:30|F_SRH|F_PBKS|Rajiv Gandhi Stadium, Hyderabad",
        "50|2026-05-07|19:30|F_LSG|F_RCB|Ekana Cricket Stadium, Lucknow",
        "51|2026-05-08|19:30|F_DC|F_KKR|Arun Jaitley Stadium, Delhi",
        "52|2026-05-09|19:30|F_RR|F_GT|Sawai Mansingh Stadium, Jaipur",
        "53|2026-05-10|15:30|F_CSK|F_LSG|M. A. Chidambaram Stadium, Chennai",
        "54|2026-05-10|19:30|F_RCB|F_MI|Shaheed Veer Narayan Singh Stadium, Raipur",
        "55|2026-05-11|19:30|F_PBKS|F_DC|HPCA Cricket Stadium, Dharamshala",
        "56|2026-05-12|19:30|F_GT|F_SRH|Narendra Modi Stadium, Ahmedabad",
        "57|2026-05-13|19:30|F_RCB|F_KKR|Shaheed Veer Narayan Singh Stadium, Raipur",
        "58|2026-05-14|19:30|F_PBKS|F_MI|HPCA Cricket Stadium, Dharamshala",
        "59|2026-05-15|19:30|F_LSG|F_CSK|Ekana Cricket Stadium, Lucknow",
        "60|2026-05-16|19:30|F_KKR|F_GT|Eden Gardens, Kolkata",
        "61|2026-05-17|15:30|F_PBKS|F_RCB|HPCA Cricket Stadium, Dharamshala",
        "62|2026-05-17|19:30|F_DC|F_RR|Arun Jaitley Stadium, Delhi",
        "63|2026-05-18|19:30|F_CSK|F_SRH|M. A. Chidambaram Stadium, Chennai",
        "64|2026-05-19|19:30|F_RR|F_LSG|Sawai Mansingh Stadium, Jaipur",
        "65|2026-05-20|19:30|F_KKR|F_MI|Eden Gardens, Kolkata",
        "66|2026-05-21|19:30|F_GT|F_CSK|Narendra Modi Stadium, Ahmedabad",
        "67|2026-05-22|19:30|F_SRH|F_RCB|Rajiv Gandhi Stadium, Hyderabad",
        "68|2026-05-23|19:30|F_LSG|F_PBKS|Ekana Cricket Stadium, Lucknow",
        "69|2026-05-24|15:30|F_MI|F_RR|Wankhede Stadium, Mumbai",
        "70|2026-05-24|19:30|F_KKR|F_DC|Eden Gardens, Kolkata",
        "Qualifier 1|2026-05-26|19:30|||HPCA Cricket Stadium, Dharamshala",
        "Eliminator|2026-05-27|19:30|||Maharaja Yadavindra Singh International Cricket Stadium, Mullanpur",
        "Qualifier 2|2026-05-29|19:30|||Maharaja Yadavindra Singh International Cricket Stadium, Mullanpur",
        "Final|2026-05-31|19:30|||Narendra Modi Stadium, Ahmedabad",
    )

    val ipl2026: List<OFx> by lazy {
        IPL_2026_RAW.map { r -> val p = r.split('|'); OFx(p[0], LocalDate.parse(p[1]), p[2], p[3], p[4], p[5]) }
    }

    fun hasOfficial(c: Competition) = c.source.isNotEmpty()
    fun label(c: Competition) = if (c.source.isNotEmpty()) c.source else GENERATED

    /**
     * Replace a freshly created IPL league with the official 2026 fixture list. Game days follow the real match dates
     * (one game day per distinct date) so the calendar shows the exact published dates.
     */
    fun applyIpl(c: Competition, year: Int): Boolean {
        if (year != 2026 || c.key != "IPL") return false
        val league = ipl2026.filter { !it.playoff }
        if (league.any { it.home !in c.teams || it.away !in c.teams }) return false
        val dates = league.map { it.date }.distinct().sorted()
        c.fixtures.clear(); c.nextFixtureId = 1
        for (o in league.sortedWith(compareBy({ it.date }, { it.time }))) {
            c.fixtures.add(Fixture("${c.id}-${c.nextFixtureId++}", o.home, o.away, "League", 0, dates.indexOf(o.date) + 1,
                date = o.date.toString(), time = o.time, venue = o.venue))
        }
        c.source = IPL_2026_SOURCE
        return true
    }

    /** Stamp the official date / time / venue on a playoff fixture when the engine creates it. */
    fun stamp(c: Competition, f: Fixture) {
        if (c.source != IPL_2026_SOURCE || f.date.isNotEmpty()) return
        val o = ipl2026.firstOrNull { it.playoff && it.no == f.stage } ?: return
        f.date = o.date.toString(); f.time = o.time; f.venue = o.venue
    }
}
