package com.cricketcoach.manager.game

import kotlin.random.Random

/** One commentary line (shown in the feed and optionally spoken with the phone's text-to-speech in [lang]). */
data class ComLine(val evId: Int, val over: String, val text: String, val lang: String, val kind: String)

/**
 * Ball-by-ball commentary in English or Hindi with three styles (0 Classic, 1 Excited, 2 Analytical).
 * Situation-aware (chase equation, death overs, hat-tricks, nervous nineties, collapses, big overs, milestones)
 * and non-repetitive: recently used templates are avoided.
 */
class Commentary(private val langPref: String, private val style: Int, private val rnd: Random = Random.Default) {
    private val lastUsed = HashMap<String, Int>()
    private var counter = 0
    private var mixFlip = false

    /** Least-recently-used pick: chooses randomly among the older half of the templates, so lines never repeat back-to-back. */
    private fun pick(list: List<String>): String {
        val ordered = list.sortedBy { lastUsed[it] ?: -1 }
        val pool = ordered.take(if (list.size >= 6) list.size / 3 else maxOf(1, (list.size + 1) / 2))
        val t = pool.random(rnd)
        lastUsed[t] = ++counter
        return t
    }

    fun line(ev: BallEv, sim: MatchSim, live: Boolean = true): ComLine {
        val lang = when (langPref) { "HI" -> "HI"; "MIX" -> { mixFlip = !mixFlip; if (mixFlip) "EN" else "HI" }; else -> "EN" }
        val hi = lang == "HI"
        val bat = sim.pn(ev.batter); val bowl = sim.pn(ev.bowler)
        val inn = sim.innings.getOrNull(ev.inn)
        val card = inn?.bc?.firstOrNull { it.pid == ev.batter }
        val how = ev.how
        val base = when (ev.kind) {
            "DOT" -> pick(if (hi) HI_DOT else EN_DOT)
            "RUN" -> pick(if (hi) (when (ev.bat) { 1 -> HI_ONE; 2 -> HI_TWO; else -> HI_THREE }) else (when (ev.bat) { 1 -> EN_ONE; 2 -> EN_TWO; else -> EN_THREE }))
            "FOUR" -> pick(if (hi) HI_FOUR else EN_FOUR)
            "SIX" -> pick(if (hi) HI_SIX else EN_SIX)
            "WIDE" -> pick(if (hi) HI_WIDE else EN_WIDE)
            "NB" -> pick(if (hi) HI_NB else EN_NB)
            "APPEAL" -> pick(if (hi) HI_APPEAL else EN_APPEAL)
            "WICKET" -> pick(when {
                how.startsWith("b ") -> if (hi) HI_BOWLED else EN_BOWLED
                how.startsWith("lbw") -> if (hi) HI_LBW else EN_LBW
                how.startsWith("run out") -> if (hi) HI_RUNOUT else EN_RUNOUT
                how.startsWith("st ") -> if (hi) HI_STUMPED else EN_STUMPED
                else -> if (hi) HI_CAUGHT else EN_CAUGHT
            })
            else -> ev.text
        }
        var text = base.replace("{bat}", bat).replace("{bowl}", bowl).replace("{r}", ev.bat.toString())
        if (ev.kind == "WICKET" && card != null) text += if (hi) " $bat ${card.runs} (${card.balls}) बनाकर लौटे।" else " $bat goes for ${card.runs} off ${card.balls}."
        // excitement / style
        val big = ev.kind == "SIX" || ev.kind == "FOUR" || ev.kind == "WICKET" || ev.milestone
        if (style == 1 && big) text = pick(if (hi) HI_HYPE else EN_HYPE) + " " + text
        if (style == 2 && card != null && card.balls >= 6 && (ev.kind == "FOUR" || ev.kind == "SIX" || rnd.nextInt(5) == 0)) {
            val sr = card.runs * 100 / card.balls.coerceAtLeast(1)
            text += if (hi) " स्ट्राइक रेट $sr।" else " Strike rate now $sr."
        }
        // milestones
        if (ev.milestone && card != null) {
            text += when {
                card.runs >= 200 -> if (hi) " दोहरा शतक! इतिहास रच दिया $bat ने!" else " A DOUBLE HUNDRED for $bat! Historic!"
                card.runs >= 100 -> pick(if (hi) HI_HUNDRED else EN_HUNDRED).replace("{bat}", bat).replace("{b}", card.balls.toString())
                card.runs >= 50 -> pick(if (hi) HI_FIFTY else EN_FIFTY).replace("{bat}", bat).replace("{b}", card.balls.toString())
                else -> ""
            }
        }
        if (live && inn != null) situation(ev, sim, inn, hi, card)?.let { text += " $it" }
        return ComLine(ev.id, "${ev.over}.${ev.ball}", text, lang, ev.kind)
    }

    private fun situation(ev: BallEv, sim: MatchSim, inn: Inns, hi: Boolean, card: BatCard?): String? {
        val evs = sim.events
        // hat-trick ball
        if (ev.kind == "WICKET") {
            val lastTwoByBowler = evs.filter { it.inn == ev.inn && it.bowler == ev.bowler && it.kind != "WIDE" && it.kind != "NB" }.takeLast(2)
            if (lastTwoByBowler.size == 2 && lastTwoByBowler.all { it.kind == "WICKET" }) return if (hi) "${sim.pn(ev.bowler)} हैट्रिक पर हैं! अगली गेंद पर सबकी नज़र!" else "${sim.pn(ev.bowler)} is on a HAT-TRICK!"
            val recentW = evs.filter { it.inn == ev.inn }.takeLast(18).count { it.kind == "WICKET" }
            if (recentW >= 3) return if (hi) "विकेटों की झड़ी! ${inn.runs}/${inn.wk}, बल्लेबाज़ी लड़खड़ा गई है।" else "A collapse is on! ${inn.runs}/${inn.wk} now."
        }
        // nervous nineties
        if (card != null && !card.out && card.runs in 90..99 && rnd.nextInt(3) == 0) return if (hi) "${sim.pn(card.pid)} ${card.runs} पर, शतक बस कुछ कदम दूर!" else "${sim.pn(card.pid)} into the nervous nineties on ${card.runs}."
        // big over
        val overRuns = evs.filter { it.inn == ev.inn && it.over == ev.over }.sumOf { it.runs }
        if (overRuns >= 18 && (ev.kind == "SIX" || ev.kind == "FOUR")) return if (hi) "इस ओवर में अब तक $overRuns रन!" else "$overRuns from the over already - carnage!"
        // chase equation
        val target = inn.target
        if (target != null && !sim.isTest && inn.maxBalls > 0) {
            val need = target - inn.runs; val left = inn.maxBalls - inn.balls
            if (need > 0 && left > 0) {
                val tense = left <= 12 || (left <= 30 && need * 6.0 / left > 9)
                if (tense || ev.ball == 6 && rnd.nextInt(2) == 0) {
                    return if (hi) "जीत के लिए $left गेंदों में $need रन चाहिए${if (tense) " - मुकाबला रोमांचक!" else "।"}"
                    else "$need needed from $left ball${if (left == 1) "" else "s"}${if (tense) " - this is tight!" else "."}"
                }
            }
        } else if (!sim.isTest && inn.maxBalls > 0 && ev.ball == 6 && rnd.nextInt(3) == 0) {
            val rr = if (inn.balls == 0) 0.0 else inn.runs * 6.0 / inn.balls
            val proj = (rr * inn.maxBalls / 6).toInt()
            return if (hi) "रन रेट ${"%.2f".format(rr)}, अनुमानित स्कोर करीब $proj।" else "Run rate ${"%.2f".format(rr)}, projecting around $proj."
        }
        if (!sim.isTest && inn.maxBalls > 0 && inn.maxBalls - inn.balls in 1..6 && ev.ball == 6) return if (hi) "आख़िरी ओवर बाकी!" else "Into the final over!"
        return null
    }

    companion object {
        val EN_DOT = listOf("{bowl} to {bat}, defended solidly back down the pitch.", "Good length from {bowl}, {bat} leaves it alone.", "Beaten! {bowl} finds the edge... no, just past it.",
            "Dot ball. {bat} pushes to cover, no run.", "Tight line from {bowl}, {bat} can only block.", "Full and straight, dug out by {bat}.", "Played and missed! Lovely bowling from {bowl}.",
            "{bat} shoulders arms - well judged.", "Pressure building, another dot.", "Quick bouncer from {bowl}, {bat} sways out of the way.", "Straight to the fielder, no run.", "{bowl} hits the deck hard, {bat} fends it down.")
        val EN_ONE = listOf("{bat} works it off the pads for a single.", "Pushed into the gap, they take one.", "Dabbed to third man, single.", "Quick single! Sharp running.", "Tapped to mid-on and they scamper through.",
            "Turned away fine, one run.", "{bat} rotates the strike.", "Driven to long-off for one.")
        val EN_TWO = listOf("Placed into the gap - they come back for two.", "Good running, two runs.", "Clipped through midwicket, they hustle back for the second.", "{bat} drives, the fielder cuts it off - two.", "Pulled away, comfortable couple.")
        val EN_THREE = listOf("Superb running - three!", "Chased down just inside the rope, they run three.", "Into the deep, three taken.")
        val EN_FOUR = listOf("FOUR! Glorious cover drive from {bat}!", "Cracked through point - four runs!", "Pulled away, that races to the fence!", "Edged... and it flies to the boundary! Four.", "Straight down the ground, no chance for the fielder. FOUR!",
            "Beautiful timing, threaded through the gap for four.", "Short and punished! {bat} cuts it for four.", "Flicked off the pads - one bounce into the rope.", "{bat} dances down and lofts it over mid-off. Four!", "Ramp shot! Over the keeper for four.")
        val EN_SIX = listOf("SIX! {bat} launches {bowl} into the stands!", "That's HUGE! Out of the ground almost!", "Maximum! Picked the length early and dispatched it.", "Smoked over long-on for six!", "Up, up and away... SIX!",
            "Pure power from {bat} - deep into the crowd!", "Slog sweep, flat and long. SIX!", "{bat} stands tall and pulls it for a massive six!", "Inside-out over extra cover - what a shot! Six.")
        val EN_WIDE = listOf("Wide called. {bowl} strays down leg.", "Too wide outside off - extra run.", "Wide! Loose delivery from {bowl}.")
        val EN_NB = listOf("No-ball! Overstepped - free hit coming.", "{bowl} oversteps. No-ball.", "No-ball called, the umpire's arm goes out.")
        val EN_APPEAL = listOf("Big appeal from {bowl}... not out!", "Loud shout for LBW - turned down.", "They go up for the catch - umpire unmoved.")
        val EN_BOWLED = listOf("BOWLED HIM! {bowl} crashes through the defence!", "Timber! The stumps are shattered!", "Clean bowled! {bat} played all around it.", "Through the gate - BOWLED!")
        val EN_LBW = listOf("Plumb in front! {bat} is LBW to {bowl}.", "That's out, LBW - trapped on the crease.", "Up goes the finger! Leg before.")
        val EN_CAUGHT = listOf("CAUGHT! {bat} picks out the fielder.", "In the air... and taken! {bowl} strikes.", "Edged and caught behind!", "Top edge... safely held. Out!", "Brilliant catch in the deep!", "Skied it - and it's caught!")
        val EN_RUNOUT = listOf("RUN OUT! Direct hit - {bat} is short of the crease!", "Mix-up in the middle and it's a run out!", "Superb throw, the bails are off - run out!")
        val EN_STUMPED = listOf("STUMPED! Lightning work behind the stumps!", "{bat} is lured out and stumped!", "Beaten in flight, stumped by a mile!")
        val EN_HYPE = listOf("OH MY WORD!", "WHAT A MOMENT!", "UNBELIEVABLE!", "ARE YOU KIDDING ME!", "THE CROWD ERUPTS!", "SENSATIONAL!")
        val EN_FIFTY = listOf(" FIFTY for {bat}, off {b} balls - raise the bat!", " Half-century up for {bat} ({b} balls).", " {bat} brings up a well-made fifty from {b} balls.")
        val EN_HUNDRED = listOf(" HUNDRED! {bat} reaches three figures off {b} balls - take a bow!", " A magnificent CENTURY for {bat} ({b} balls)!", " {bat} kisses the helmet - a century from {b} balls!")

        val HI_DOT = listOf("{bowl} की गेंद, {bat} ने सीधा बल्ला दिखाया, कोई रन नहीं।", "अच्छी लेंथ, {bat} ने गेंद छोड़ दी।", "बीट! गेंद बल्ले को छकाती हुई निकल गई।", "डॉट बॉल, दबाव बढ़ रहा है।",
            "{bat} ने रक्षात्मक शॉट खेला।", "सटीक लाइन, {bat} के पास कोई जवाब नहीं।", "बाउंसर! {bat} झुककर बचे।", "फील्डर के सीधे हाथ में, रन नहीं।", "{bowl} की कसी हुई गेंदबाज़ी।", "ऑफ स्टंप के बाहर, {bat} ने छोड़ा।",
            "{bat} ने आगे बढ़कर रोका, रन नहीं बना।", "यॉर्कर! {bat} ने किसी तरह बल्ला नीचे लाकर रोका।", "धीमी गेंद, {bat} के शॉट में जल्दबाज़ी, कोई रन नहीं।", "{bowl} ने लगातार एक ही जगह गेंद डाली, फिर डॉट।")
        val HI_ONE = listOf("{bat} ने एक रन लिया।", "गैप में धकेला, एक रन।", "तेज़ सिंगल, शानदार दौड़!", "स्ट्राइक रोटेट, एक रन।", "मिड-ऑन की ओर, एक रन पूरा।", "पैड से फ्लिक, एक रन।",
            "थर्ड मैन की ओर, आसान सिंगल।", "{bat} ने कवर की ओर खेलकर एक रन चुराया।", "लॉन्ग-ऑफ पर ड्राइव, एक रन।", "फाइन लेग की तरफ ग्लांस, एक रन।")
        val HI_TWO = listOf("गैप में शॉट, दो रन!", "अच्छी दौड़, दो रन पूरे।", "मिडविकेट की ओर, दो रन।", "फील्डर ने रोका, लेकिन दो रन मिल गए।", "{bat} ने तेज़ दौड़कर दूसरा रन भी पूरा किया।", "डीप स्क्वेयर लेग पर पुल, दो रन।")
        val HI_THREE = listOf("बाउंड्री से पहले रुकी, तीन रन!", "शानदार रनिंग, तीन रन।", "डीप में गई गेंद, {bat} ने तीन रन दौड़ लिए।", "फील्डर की धीमी वापसी, तीन रन मिले।")
        val HI_FOUR = listOf("चौका! {bat} का शानदार कवर ड्राइव!", "गेंद सीमा रेखा के पार, चार रन!", "पुल शॉट, गेंद तेज़ी से बाउंड्री पर!", "बेहतरीन टाइमिंग, चौका!", "किनारा लगा और चार रन!",
            "{bat} ने कट किया, चौका!", "सीधा बल्ला, सीधा चौका!", "क्या शॉट है! चार रन।")
        val HI_SIX = listOf("छक्का! {bat} ने गेंद को स्टैंड्स में भेज दिया!", "गगनचुंबी छक्का!", "ये गई... मैदान के बाहर! छह रन!", "{bat} का ज़ोरदार प्रहार, छक्का!", "क्या ताक़त है! सीधा दर्शकों के बीच!",
            "स्लॉग स्वीप, छक्का!", "{bowl} की गेंद पर लंबा छक्का!")
        val HI_WIDE = listOf("वाइड! {bowl} की गेंद लेग साइड पर भटकी।", "वाइड गेंद, अतिरिक्त रन।", "ऑफ स्टंप से बहुत बाहर, अंपायर ने वाइड दिया।", "{bowl} की लाइन बिगड़ी, वाइड।")
        val HI_NB = listOf("नो-बॉल! अगली गेंद फ्री हिट।", "{bowl} का पैर लाइन से आगे, नो-बॉल।", "अंपायर ने नो-बॉल का इशारा किया, फ्री हिट आएगी।")
        val HI_APPEAL = listOf("ज़ोरदार अपील... नॉट आउट!", "एलबीडब्ल्यू की अपील ठुकराई गई।", "{bowl} ने ज़ोर से अपील की, अंपायर ने सिर हिलाया - नॉट आउट।")
        val HI_BOWLED = listOf("बोल्ड! {bowl} ने गिल्लियाँ बिखेर दीं!", "क्लीन बोल्ड! {bat} पूरी तरह चूक गए।", "स्टंप्स उखड़ गए! बोल्ड!")
        val HI_LBW = listOf("एलबीडब्ल्यू! {bat} विकेट के सामने पकड़े गए।", "अंपायर की उंगली उठी, एलबीडब्ल्यू!")
        val HI_CAUGHT = listOf("कैच! {bat} आउट!", "हवा में गेंद... और लपक ली गई!", "किनारा और विकेटकीपर के दस्ताने में!", "शानदार कैच! {bowl} को विकेट।", "ऊँचा शॉट, सुरक्षित कैच!")
        val HI_RUNOUT = listOf("रन आउट! डायरेक्ट हिट!", "दोनों बल्लेबाज़ों में ग़लतफ़हमी, रन आउट!")
        val HI_STUMPED = listOf("स्टंप्ड! बिजली जैसी तेज़ी विकेट के पीछे!", "{bat} क्रीज़ से बाहर, स्टंप आउट!")
        val HI_HYPE = listOf("अरे वाह!", "क्या बात है!", "अविश्वसनीय!", "दर्शक झूम उठे!", "कमाल हो गया!")
        val HI_FIFTY = listOf(" {bat} का अर्धशतक, {b} गेंदों में!", " पचास रन पूरे! {bat} ने बल्ला उठाया।")
        val HI_HUNDRED = listOf(" शतक! {bat} ने {b} गेंदों में सैकड़ा पूरा किया!", " शानदार शतक {bat} का! पूरा स्टेडियम खड़ा है!")
    }
}
