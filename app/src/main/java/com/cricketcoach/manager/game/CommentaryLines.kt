package com.cricketcoach.manager.game

/**
 * Commentary text library: every situation has a pool of lines in English (EN), Hindi (HI, Devanagari) and
 * Hinglish (HG, Roman-script Hindi + English, spoken with the en-IN voice).
 *
 * Placeholders: {bat} batter, {nb} partner, {bowl} bowler, {fld} fielder, {dir} shot direction, {r} runs,
 * {runs}/{b} batter's runs/balls, {team}/{opp} team names, {score} team score, {n} milestone number,
 * {need}/{left} chase equation, {rr}/{rrr}/{proj} rates, {part}/{partb} partnership, {fig} bowling figures,
 * {econ} economy, {m} six distance in metres, {fours}/{sixes} boundaries in the innings, {dots} dot-ball streak.
 */
class Pool(val en: List<String>, val hi: List<String>, val hg: List<String>) {
    fun of(lang: String) = when (lang) { "HI" -> hi; "HG" -> hg; else -> en }
}

object ComText {
    /* ---------- shot directions (index = Commentary.region(angle)) ---------- */
    // 0 straight, 1 cover, 2 point, 3 third man, 4 behind the keeper, 5 fine leg, 6 square leg, 7 midwicket, 8 mid-on/long-on, 9 mid-off/long-off
    val GROUND = Pool(
        listOf("straight down the ground", "through the covers", "past point", "down to third man", "past the keeper", "fine down the leg side", "behind square on the leg side", "through midwicket", "wide of mid-on", "past mid-off"),
        listOf("सीधा मैदान पर", "कवर्स के बीच से", "पॉइंट के पास से", "थर्ड मैन की ओर", "विकेटकीपर के पीछे", "फाइन लेग की ओर", "स्क्वेयर लेग की ओर", "मिडविकेट से", "मिड-ऑन के पास से", "मिड-ऑफ के पास से"),
        listOf("seedha ground ke neeche", "covers ke beech se", "point ke paas se", "third man ki taraf", "keeper ke peeche", "fine leg ki taraf", "square leg ki taraf", "midwicket se", "mid-on ke paas se", "mid-off ke paas se"))
    val AIR = Pool(
        listOf("straight back over the bowler's head", "over extra cover", "over point", "over third man", "over the keeper's head", "over fine leg", "over deep square leg", "over midwicket", "over long-on", "over long-off"),
        listOf("सीधा गेंदबाज़ के सिर के ऊपर से", "एक्स्ट्रा कवर के ऊपर से", "पॉइंट के ऊपर से", "थर्ड मैन के ऊपर से", "विकेटकीपर के सिर के ऊपर से", "फाइन लेग के ऊपर से", "डीप स्क्वेयर लेग के ऊपर से", "मिडविकेट के ऊपर से", "लॉन्ग-ऑन के ऊपर से", "लॉन्ग-ऑफ के ऊपर से"),
        listOf("seedha bowler ke sar ke upar se", "extra cover ke upar se", "point ke upar se", "third man ke upar se", "keeper ke sar ke upar se", "fine leg ke upar se", "deep square leg ke upar se", "midwicket ke upar se", "long-on ke upar se", "long-off ke upar se"))

    /* ---------- deliveries ---------- */
    val DOT = Pool(
        listOf("{bowl} to {bat}, defended solidly back down the pitch.", "Good length from {bowl}, {bat} leaves it alone.", "Beaten! That kissed nothing but air past the outside edge.",
            "Dot ball. {bat} pushes to cover, straight to the fielder.", "Tight line from {bowl}, {bat} can only block.", "Full and straight, dug out by {bat}.", "Played and missed! Lovely bowling from {bowl}.",
            "{bat} shoulders arms - well judged.", "Pressure building, another dot.", "Quick bouncer from {bowl}, {bat} sways out of the way.", "Slower ball, {bat} is early on the shot. No run.",
            "{bowl} hits the deck hard, {bat} fends it down.", "Yorker! {bat} jams the bat down just in time.", "Angled across, {bat} lets it go through to the keeper.", "Soft hands from {bat}, it drops at his feet."),
        listOf("{bowl} की गेंद, {bat} ने सीधा बल्ला दिखाया, कोई रन नहीं।", "अच्छी लेंथ, {bat} ने गेंद छोड़ दी।", "बीट! गेंद बल्ले को छकाती हुई निकल गई।", "डॉट बॉल, दबाव बढ़ रहा है।",
            "{bat} ने रक्षात्मक शॉट खेला।", "सटीक लाइन, {bat} के पास कोई जवाब नहीं।", "बाउंसर! {bat} झुककर बचे।", "फील्डर के सीधे हाथ में, रन नहीं।", "{bowl} की कसी हुई गेंदबाज़ी।", "ऑफ स्टंप के बाहर, {bat} ने छोड़ा।",
            "{bat} ने आगे बढ़कर रोका, रन नहीं बना।", "यॉर्कर! {bat} ने किसी तरह बल्ला नीचे लाकर रोका।", "धीमी गेंद, {bat} के शॉट में जल्दबाज़ी, कोई रन नहीं।", "{bowl} ने लगातार एक ही जगह गेंद डाली, फिर डॉट।"),
        listOf("{bowl} ki ball, {bat} ne solid defence kiya. Koi run nahi.", "Achhi length, {bat} ne ball chhod di.", "Beaten! Ball edge ke bilkul paas se nikal gayi.", "Dot ball, pressure badh raha hai.",
            "{bat} ne cover ki taraf push kiya, seedha fielder ke haath mein.", "Tight line {bowl} ki, {bat} sirf block kar paaye.", "Bouncer! {bat} ne jhuk ke chhod diya.", "Played and missed! Kya ball daali hai {bowl} ne.",
            "Yorker! {bat} ne last moment pe bat neeche laaya.", "Slower ball, {bat} shot mein jaldi kar gaye. Koi run nahi.", "{bat} ne off stump ke bahar wali ball ko jaane diya.", "Ek aur dot, {bowl} ne bandh ke rakha hai."))
    val ONE = Pool(
        listOf("{bat} works it {dir} for a single.", "Pushed {dir}, they take one.", "Dabbed down to third man, single.", "Quick single! Sharp running between the wickets.", "Tapped to mid-on and they scamper through.",
            "Turned away fine, one run.", "{bat} rotates the strike.", "Driven to long-off for one.", "Nudged {dir}, easy single."),
        listOf("{bat} ने एक रन लिया।", "गैप में धकेला, एक रन।", "तेज़ सिंगल, शानदार दौड़!", "स्ट्राइक रोटेट, एक रन।", "मिड-ऑन की ओर, एक रन पूरा।", "पैड से फ्लिक, एक रन।",
            "थर्ड मैन की ओर, आसान सिंगल।", "{bat} ने {dir} खेलकर एक रन चुराया।", "लॉन्ग-ऑफ पर ड्राइव, एक रन।"),
        listOf("{bat} ne {dir} khel ke ek run liya.", "Gap mein push, ek run.", "Quick single! Badhiya running.", "Strike rotate, ek run.", "Mid-on ki taraf tap kiya aur bhaag liye.",
            "Third man ki taraf dab kiya, aasaan single.", "{bat} ne {dir} nudge kiya, ek run.", "Long-off pe drive, ek run."))
    val TWO = Pool(
        listOf("Placed {dir} - they come back for two.", "Good running, two runs.", "Clipped {dir}, they hustle back for the second.", "{bat} drives, the fielder cuts it off - two.", "Pulled away, comfortable couple."),
        listOf("{dir} शॉट, दो रन!", "अच्छी दौड़, दो रन पूरे।", "मिडविकेट की ओर, दो रन।", "फील्डर ने रोका, लेकिन दो रन मिल गए।", "{bat} ने तेज़ दौड़कर दूसरा रन भी पूरा किया।"),
        listOf("{dir} khela, do run!", "Badhiya running, do run poore.", "{bat} ne tez bhaag ke doosra run bhi le liya.", "Fielder ne roka, lekin do run mil gaye.", "Pull kiya, aaram se do run."))
    val THREE = Pool(
        listOf("Superb running - three!", "Chased down just inside the rope, they run three.", "Into the deep {dir}, three taken."),
        listOf("बाउंड्री से पहले रुकी, तीन रन!", "शानदार रनिंग, तीन रन।", "डीप में गई गेंद, {bat} ने तीन रन दौड़ लिए।"),
        listOf("Boundary se pehle ruk gayi, teen run!", "Kamaal ki running, teen run.", "Deep mein gayi ball, {bat} ne teen run bhaag liye."))
    val FOUR = Pool(
        listOf("FOUR! {bat} drives it {dir}!", "Cracked {dir} - four runs!", "That races away {dir}. Four!", "Beautiful timing, threaded {dir} for four.", "Short and punished! {bat} sends it {dir} for four.",
            "{bat} dances down and hits it {dir}. Four!", "No chance for the fielder - FOUR {dir}!", "Picked the gap {dir}, one bounce into the rope.", "Pure placement from {bat}, four {dir}."),
        listOf("चौका! {bat} ने गेंद {dir} भेज दी!", "{dir} शानदार शॉट, चार रन!", "बेहतरीन टाइमिंग, {dir} चौका!", "छोटी गेंद और सज़ा! {bat} का चौका {dir}।", "{bat} ने आगे बढ़कर {dir} चौका जड़ा!",
            "फील्डर के लिए कोई मौका नहीं, {dir} चौका!", "क्या प्लेसमेंट है, {dir} चार रन।"),
        listOf("CHAUKA! {bat} ne ball {dir} bhej di!", "{dir} zabardast shot, chaar run!", "Kya timing hai! {dir} chauka.", "Short ball aur saza! {bat} ka chauka {dir}.", "{bat} ne aage badh ke {dir} chauka jad diya!",
            "Fielder ke paas koi chance nahi, {dir} chauka!", "Perfect placement, {dir} chaar run."))
    val FOUR_EDGE = Pool(
        listOf("Edged... and it flies {dir}! Four lucky runs.", "Thick edge, wide of the slips and away for four.", "Outside edge, but it runs away {dir}. Four."),
        listOf("किनारा लगा और गेंद {dir} चार रन के लिए!", "मोटा किनारा, स्लिप के बगल से चौका।"),
        listOf("Edge laga aur ball {dir} chaar run ke liye!", "Mota edge, slip ke bagal se chauka. Kismat saath hai."))
    val SIX = Pool(
        listOf("SIX! {bat} launches {bowl} {dir}!", "That's HUGE! Sent {dir}!", "Maximum! Picked the length early and dispatched it {dir}.", "Smoked {dir} for six!", "Up, up and away {dir}... SIX!",
            "Pure power from {bat} - deep into the crowd {dir}!", "{bat} stands tall and clears the rope {dir}!", "Into the stands {dir}! What a strike."),
        listOf("छक्का! {bat} ने गेंद {dir} स्टैंड्स में भेज दी!", "गगनचुंबी छक्का, {dir}!", "ये गई... {dir} छह रन!", "{bat} का ज़ोरदार प्रहार, {dir} छक्का!", "क्या ताक़त है! {dir} सीधा दर्शकों के बीच!",
            "{bowl} की गेंद पर {dir} लंबा छक्का!"),
        listOf("CHHAKKA! {bat} ne ball {dir} stands mein bhej di!", "Bahut bada shot, {dir} chhakka!", "Ye gayi... {dir} chhe run!", "{bat} ka zordaar prahaar, {dir} chhakka!", "Kya power hai! {dir} seedha crowd mein!",
            "{bowl} ki ball pe {dir} lamba chhakka!"))
    val SIX_BIG = Pool(
        listOf(" That's gone {m} metres!", " Out of the ground almost - {m} metres!", " Into the second tier! {m} metres."),
        listOf(" ये {m} मीटर लंबा छक्का है!", " गेंद {m} मीटर दूर जाकर गिरी!"),
        listOf(" Ye {m} meter lamba chhakka hai!", " Ball {m} meter door jaake giri!"))
    val WIDE = Pool(
        listOf("Wide called. {bowl} strays down the leg side.", "Too wide outside off - extra run.", "Wide! Loose delivery from {bowl}.", "The umpire stretches his arms. Wide."),
        listOf("वाइड! {bowl} की गेंद लेग साइड पर भटकी।", "वाइड गेंद, अतिरिक्त रन।", "ऑफ स्टंप से बहुत बाहर, अंपायर ने वाइड दिया।", "{bowl} की लाइन बिगड़ी, वाइड।"),
        listOf("Wide! {bowl} ki ball leg side pe bhatak gayi.", "Wide ball, ek extra run.", "Off stump se bahut bahar, umpire ne wide diya.", "{bowl} ki line bigdi, wide."))
    val NB = Pool(
        listOf("No-ball! {bowl} has overstepped.", "{bowl} oversteps. No-ball, and an extra delivery.", "No-ball called, the umpire's arm goes out."),
        listOf("नो-बॉल! {bowl} का पैर लाइन से आगे।", "{bowl} ने ओवरस्टेप किया, नो-बॉल।", "अंपायर ने नो-बॉल का इशारा किया।"),
        listOf("No-ball! {bowl} ka pair line se aage.", "{bowl} ne overstep kiya, no-ball.", "Umpire ne no-ball ka ishara kiya."))
    val FREE_HIT_NEXT = Pool(listOf(" Free hit coming up!", " And that means a FREE HIT."), listOf(" अगली गेंद फ्री हिट!", " फ्री हिट आएगी!"), listOf(" Agli ball FREE HIT!", " Free hit aa rahi hai!"))
    val APPEAL = Pool(
        listOf("Big appeal from {bowl}... not out!", "Loud shout for LBW - turned down.", "They go up for the catch - umpire unmoved.", "Huge appeal! The umpire shakes his head."),
        listOf("ज़ोरदार अपील... नॉट आउट!", "एलबीडब्ल्यू की अपील ठुकराई गई।", "{bowl} ने ज़ोर से अपील की, अंपायर ने सिर हिलाया - नॉट आउट।"),
        listOf("Zordaar appeal... NOT OUT!", "LBW ki appeal, umpire ne thukra di.", "{bowl} ne poore dil se appeal kiya, lekin umpire ne sar hila diya."))

    /* ---------- dismissals ---------- */
    val BOWLED = Pool(
        listOf("BOWLED HIM! {bowl} crashes through the defence!", "Timber! The stumps are shattered!", "Clean bowled! {bat} played all around it.", "Through the gate - BOWLED!", "Off stump cartwheeling! {bowl} gets his man."),
        listOf("बोल्ड! {bowl} ने गिल्लियाँ बिखेर दीं!", "क्लीन बोल्ड! {bat} पूरी तरह चूक गए।", "स्टंप्स उखड़ गए! बोल्ड!", "बल्ले और पैड के बीच से - बोल्ड!"),
        listOf("BOLD! {bowl} ne gilliyan bikher di!", "Clean bowled! {bat} poori tarah chook gaye.", "Stumps ukhad gaye! Bowled!", "Bat aur pad ke beech se - BOLD!"))
    val LBW = Pool(
        listOf("Plumb in front! {bat} is LBW to {bowl}.", "That's out, LBW - trapped on the crease.", "Up goes the finger! Leg before.", "Struck on the pad in line - {bat} has to go."),
        listOf("एलबीडब्ल्यू! {bat} विकेट के सामने पकड़े गए।", "अंपायर की उंगली उठी, एलबीडब्ल्यू!", "पैड पर लगी, सीधे स्टंप्स के सामने - आउट!"),
        listOf("LBW! {bat} wicket ke saamne pakde gaye.", "Umpire ki ungli uthi, LBW!", "Pad pe lagi, seedha stumps ke saamne - OUT!"))
    val CAUGHT = Pool(
        listOf("CAUGHT! {bat} picks out {fld}.", "In the air... and taken by {fld}! {bowl} strikes.", "Top edge... {fld} settles under it. Out!", "Skied it - and {fld} holds on!", "Straight down the throat of {fld}!"),
        listOf("कैच! {bat} ने गेंद सीधी {fld} के हाथ में दे दी।", "हवा में गेंद... और {fld} ने लपक ली!", "ऊँचा शॉट, {fld} का सुरक्षित कैच!", "{fld} के हाथों में आसान कैच - आउट!"),
        listOf("CATCH! {bat} ne ball seedha {fld} ke haath mein de di.", "Hawa mein ball... aur {fld} ne pakad li!", "Top edge, {fld} neeche khade the - OUT!", "Aasaan catch {fld} ka, {bowl} ko wicket!"))
    val CAUGHT_DEEP = Pool(
        listOf("Holed out! {fld} takes a fine catch in the deep.", "Went for the big one... caught on the boundary by {fld}!", "{fld} judges it perfectly at the rope. Gone!"),
        listOf("बड़ा शॉट खेलने गए... बाउंड्री पर {fld} ने कैच लपका!", "डीप में {fld} का शानदार कैच!"),
        listOf("Bada shot khelne gaye... boundary pe {fld} ne catch le liya!", "Deep mein {fld} ka shaandaar catch!"))
    val CAUGHT_KEEPER = Pool(
        listOf("Edged and taken! {fld} makes no mistake behind the stumps.", "Feathered through to {fld}. Out caught behind!", "Nick and gone - {fld} gloves it cleanly."),
        listOf("किनारा और विकेटकीपर {fld} के दस्ताने में!", "हल्का सा किनारा, {fld} ने कोई गलती नहीं की - आउट!"),
        listOf("Edge laga aur keeper {fld} ke gloves mein!", "Halka sa nick, {fld} ne koi galti nahi ki - OUT!"))
    val CAUGHT_BOWLER = Pool(
        listOf("Caught and bowled! Sharp reflexes from {bowl}!", "Straight back to {bowl} - he holds on!"),
        listOf("कॉट एंड बोल्ड! {bowl} की ज़बरदस्त फुर्ती!", "सीधा {bowl} के हाथ में, शानदार कैच!"),
        listOf("Caught and bowled! {bowl} ki zabardast phurti!", "Seedha {bowl} ke haath mein - kya catch hai!"))
    val RUNOUT = Pool(
        listOf("RUN OUT! Direct hit from {fld} - {bat} is short!", "Mix-up in the middle and {fld} completes the run out!", "Superb throw from {fld}, the bails are off - run out!"),
        listOf("रन आउट! {fld} का डायरेक्ट हिट!", "दोनों बल्लेबाज़ों में ग़लतफ़हमी, {fld} ने रन आउट कर दिया!"),
        listOf("RUN OUT! {fld} ka direct hit!", "Dono batsmen mein confusion, {fld} ne run out kar diya!"))
    val STUMPED = Pool(
        listOf("STUMPED! Lightning work from {fld}!", "{bat} is lured out and {fld} whips the bails off!", "Beaten in flight, stumped by a mile!"),
        listOf("स्टंप्ड! {fld} की बिजली जैसी फुर्ती!", "{bat} क्रीज़ से बाहर, स्टंप आउट!"),
        listOf("STUMPED! {fld} ki bijli jaisi phurti!", "{bat} crease se bahar, stumping!"))
    val DEPART = Pool(
        listOf("{bat} departs for {runs} off {b}.", "{bat} walks back with {runs} ({b} balls).", "End of a {runs}-run stay for {bat}."),
        listOf("{bat} {runs} ({b}) बनाकर लौटे।", "{bat} की {runs} रनों की पारी ख़त्म।"),
        listOf("{bat} {runs} ({b}) bana ke wapas.", "{bat} ki {runs} run ki innings khatam."))
    val DUCK = Pool(
        listOf("{bat} goes for a duck.", "Nought for {bat} - a duck."),
        listOf("{bat} बिना खाता खोले लौटे।", "{bat} शून्य पर आउट।"),
        listOf("{bat} zero pe out - duck!", "{bat} khaata bhi nahi khol paaye."))
    val GOLDEN_DUCK = Pool(
        listOf("First ball! A golden duck for {bat}.", "{bat} out first ball - golden duck."),
        listOf("पहली ही गेंद पर! {bat} गोल्डन डक।"),
        listOf("Pehli hi ball pe! {bat} ka golden duck."))

    /* ---------- special calls ---------- */
    val HYPE = Pool(listOf("OH MY WORD!", "WHAT A MOMENT!", "UNBELIEVABLE!", "ARE YOU KIDDING ME!", "THE CROWD ERUPTS!", "SENSATIONAL!"),
        listOf("अरे वाह!", "क्या बात है!", "अविश्वसनीय!", "दर्शक झूम उठे!", "कमाल हो गया!"),
        listOf("ARRE WAAH!", "KYA BAAT HAI!", "UNBELIEVABLE!", "Crowd paagal ho gaya!", "KAMAAL HO GAYA!"))
    val FIFTY = Pool(
        listOf("FIFTY for {bat}, off {b} balls - raise the bat!", "Half-century up for {bat} ({b} balls).", "{bat} brings up a well-made fifty from {b} balls."),
        listOf("{bat} का अर्धशतक, {b} गेंदों में!", "पचास रन पूरे! {bat} ने बल्ला उठाया।"),
        listOf("FIFTY! {bat} ki half-century {b} balls mein!", "Pachaas poore! {bat} ne bat utha ke crowd ko salaam kiya."))
    val HUNDRED = Pool(
        listOf("HUNDRED! {bat} reaches three figures off {b} balls - take a bow!", "A magnificent CENTURY for {bat} ({b} balls)!", "{bat} kisses the helmet - a century from {b} balls!"),
        listOf("शतक! {bat} ने {b} गेंदों में सैकड़ा पूरा किया!", "शानदार शतक {bat} का! पूरा स्टेडियम खड़ा है!"),
        listOf("SHATAK! {bat} ne {b} balls mein century poori ki!", "Kya century hai {bat} ki! Poora stadium khada ho gaya!"))
    val BIG_HUNDRED = Pool(
        listOf("{n} for {bat}! This is a monumental innings.", "{bat} goes past {n} - relentless!"),
        listOf("{bat} के {n} रन पूरे! ऐतिहासिक पारी!"),
        listOf("{bat} ke {n} run poore! Itihaasik innings!"))
    val TEAM_MS = Pool(
        listOf("That brings up {n} for {team}.", "{n} up for {team}.", "{team} reach {n}."),
        listOf("{team} के {n} रन पूरे।", "{team} का स्कोर {n} के पार।"),
        listOf("{team} ke {n} run poore.", "{team} ka score {n} ke paar."))
    val PART = Pool(
        listOf("That's a {n}-run partnership between {bat} and {nb}, off {partb} balls.", "{bat} and {nb} have now added {n} together."),
        listOf("{bat} और {nb} के बीच {n} रनों की साझेदारी, {partb} गेंदों में।", "{bat} और {nb} ने मिलकर {n} रन जोड़ लिए।"),
        listOf("{bat} aur {nb} ke beech {n} run ki partnership, {partb} balls mein.", "{bat} aur {nb} ne milke {n} run jod liye."))
    val THREE_FOR = Pool(
        listOf("That's three for {bowl} - figures of {fig}.", "{bowl} has his third. {fig}."),
        listOf("{bowl} का तीसरा विकेट! आंकड़े {fig}।"),
        listOf("{bowl} ka teesra wicket! Figures {fig}."))
    val FIVE_FOR = Pool(
        listOf("FIVE-WICKET HAUL for {bowl}! {fig} - what a spell!", "{bowl} has five! Take a bow - {fig}."),
        listOf("पांच विकेट! {bowl} का शानदार स्पेल - {fig}!"),
        listOf("PAANCH WICKET! {bowl} ka kamaal ka spell - {fig}!"))
    val HATTRICK_ON = Pool(
        listOf("{bowl} is on a HAT-TRICK!", "Two in two! {bowl} on a hat-trick - everyone in close!"),
        listOf("{bowl} हैट्रिक पर हैं! अगली गेंद पर सबकी नज़र!"),
        listOf("{bowl} HAT-TRICK pe hain! Agli ball pe sabki nazar!"))
    val HATTRICK = Pool(
        listOf("HAT-TRICK!!! {bowl} has done it! Three in three!", "History! A HAT-TRICK for {bowl}!"),
        listOf("हैट्रिक!!! {bowl} ने कर दिखाया! तीन गेंदों में तीन विकेट!"),
        listOf("HAT-TRICK!!! {bowl} ne kar dikhaya! Teen ball, teen wicket!"))
    val COLLAPSE = Pool(
        listOf("A collapse is on! {team} are {score} now.", "Wickets tumbling - {team} slip to {score}."),
        listOf("विकेटों की झड़ी! {team} {score}, बल्लेबाज़ी लड़खड़ा गई है।"),
        listOf("Wicket pe wicket! {team} {score}, batting ladkhada gayi hai."))
    val NINETIES = Pool(
        listOf("{bat} into the nervous nineties on {runs}.", "{runs} for {bat} - the hundred is in sight."),
        listOf("{bat} {runs} पर, शतक बस कुछ कदम दूर!"),
        listOf("{bat} {runs} pe, century bas thodi door!"))
    val FREE_HIT_PUNISH = Pool(listOf("Free hit, and it's been punished!", "Made the free hit count!"), listOf("फ्री हिट का पूरा फायदा उठाया!"), listOf("Free hit ka poora fayda uthaya!"))
    val FREE_HIT_WASTED = Pool(listOf("Free hit wasted - great comeback from {bowl}.", "{bowl} nails the yorker on the free hit."), listOf("फ्री हिट बेकार गई, {bowl} की शानदार वापसी।"), listOf("Free hit waste ho gayi, {bowl} ki zabardast wapsi."))
    val CHASE = Pool(
        listOf("{need} needed from {left} balls.", "The equation: {need} off {left}."),
        listOf("जीत के लिए {left} गेंदों में {need} रन चाहिए।"),
        listOf("Jeet ke liye {left} balls mein {need} run chahiye."))
    val CHASE_TIGHT = Pool(
        listOf("{need} needed from {left} balls - this is getting tight!", "{need} off {left}. Hearts in mouths!", "It's down to {need} from {left}!"),
        listOf("{left} गेंदों में {need} रन - मुकाबला रोमांचक!", "{need} रन, {left} गेंदें - सांसें थमी हुई हैं!"),
        listOf("{left} balls mein {need} run - match bahut tight ho gaya hai!", "{need} run, {left} balls - saansein ruki hui hain!"))
    val PROJ = Pool(
        listOf("Run rate {rr}, projecting around {proj}.", "At {rr} an over, they're on course for {proj}."),
        listOf("रन रेट {rr}, अनुमानित स्कोर करीब {proj}।"),
        listOf("Run rate {rr}, projected score lagbhag {proj}."))
    val WIN = Pool(
        listOf("And that's it! {team} win it!", "That's the winning runs - {team} get there!", "{team} have done it!"),
        listOf("और यह रहा! {team} ने मुकाबला जीत लिया!", "जीत का शॉट! {team} विजेता!"),
        listOf("Aur ye raha! {team} ne match jeet liya!", "Winning shot! {team} jeet gaye!"))

    /* ---------- end of over calls ---------- */
    val MAIDEN = Pool(
        listOf("A maiden from {bowl}! Six balls, no runs.", "Excellent over - {bowl} bowls a maiden."),
        listOf("{bowl} का मेडन ओवर! छह गेंदें, एक भी रन नहीं।"),
        listOf("{bowl} ka MAIDEN over! Chhe ball, ek bhi run nahi."))
    val WICKET_MAIDEN = Pool(
        listOf("A WICKET MAIDEN from {bowl}! Outstanding.", "No runs and a wicket - a wicket maiden for {bowl}."),
        listOf("विकेट मेडन! {bowl} का कमाल का ओवर।"),
        listOf("Wicket maiden! {bowl} ka kamaal ka over."))
    val BIG_OVER = Pool(
        listOf("{r} from that over - expensive for {bowl}!", "That over went for {r}. Carnage!"),
        listOf("इस ओवर में {r} रन, {bowl} पर भारी पड़ा!"),
        listOf("Is over mein {r} run, {bowl} ki pitaai ho gayi!"))
    val PP_END = Pool(
        listOf("End of the powerplay: {team} {score}.", "Powerplay done - {team} are {score}."),
        listOf("पावरप्ले ख़त्म: {team} {score}।"),
        listOf("Powerplay khatam: {team} {score}."))
    val DEATH = Pool(
        listOf("Into the death overs now - {team} {score}.", "Here come the death overs. Big hitting expected."),
        listOf("अब डेथ ओवर्स शुरू - {team} {score}।"),
        listOf("Ab death overs shuru - {team} {score}. Ab lagenge bade shots!"))
    val LAST_OVER = Pool(
        listOf("Into the final over!", "One over to go!"),
        listOf("आख़िरी ओवर बाकी!"),
        listOf("Bas aakhri over baaki!"))

    /* ---------- expert analyst (second voice) ---------- */
    val X_WICKET_SET = Pool(
        listOf("{bat} will be kicking himself. He was set on {runs} and gave it away.", "That's a poor shot. {bat} had done the hard work and thrown it away on {runs}.", "Big wicket. {bat} looked in total control on {runs}."),
        listOf("{bat} ख़ुद से नाराज़ होंगे, {runs} पर सेट थे और विकेट गंवा दिया।", "ख़राब शॉट चयन। {bat} {runs} पर सेट हो चुके थे।"),
        listOf("{bat} khud se naraaz honge, {runs} pe set the aur wicket de diya.", "Kharaab shot selection, {bat} {runs} pe set ho chuke the."))
    val X_WICKET_PART = Pool(
        listOf("Big breakthrough. That stand was worth {part}, and {opp} needed that.", "That ends a dangerous partnership of {part}."),
        listOf("बड़ी सफलता। यह साझेदारी {part} रनों की थी।", "{part} रनों की ख़तरनाक साझेदारी टूटी।"),
        listOf("Badi breakthrough. Ye partnership {part} run ki thi.", "{part} run ki khatarnaak partnership toot gayi."))
    val X_WICKET = Pool(
        listOf("{bowl} set that up beautifully over the last few balls.", "The pressure of the dot balls told there.", "Good captaincy - the fielder was put there for exactly that shot.",
            "{team} are under pressure now. A new batter has to start from scratch.", "That's the wicket-taking delivery - right in the channel."),
        listOf("{bowl} ने पिछली कुछ गेंदों से इस विकेट की ज़मीन तैयार की थी।", "डॉट गेंदों का दबाव काम कर गया।", "बेहतरीन कप्तानी - फील्डर ठीक उसी शॉट के लिए लगाया गया था।", "अब नए बल्लेबाज़ को शुरुआत से पारी बनानी होगी।"),
        listOf("{bowl} ne pichhli kuch balls se is wicket ka plan banaya tha.", "Dot balls ka pressure kaam kar gaya.", "Badhiya captaincy - fielder bilkul usi shot ke liye lagaya tha.", "Ab naye batsman ko zero se shuru karna padega."))
    val X_DOTS = Pool(
        listOf("That's {dots} dot balls in a row. The pressure is building on {bat}.", "{dots} dots on the bounce - {bat} needs to find a way to rotate the strike."),
        listOf("लगातार {dots} डॉट गेंदें। {bat} पर दबाव बढ़ रहा है।"),
        listOf("Lagaataar {dots} dot balls. {bat} pe pressure badh raha hai."))
    val X_BOUNDARY_CHASE = Pool(
        listOf("That eases things. The asking rate is down to {rrr}.", "Exactly what the chase needed. {rrr} an over now."),
        listOf("इससे दबाव कम हुआ। अब आवश्यक रन रेट {rrr}।"),
        listOf("Isse pressure kam hua. Ab required rate {rrr}."))
    val X_FIG = Pool(
        listOf("{bowl}'s figures now read {fig}, economy {econ}.", "Lovely spell from {bowl}: {fig}.", "{bowl} has gone at {econ} an over so far."),
        listOf("{bowl} के आंकड़े अब {fig}, इकॉनमी {econ}।"),
        listOf("{bowl} ke figures ab {fig}, economy {econ}."))
    val X_PART = Pool(
        listOf("This partnership is now {part} off {partb} balls.", "{part} together now off {partb} - this stand is building nicely."),
        listOf("यह साझेदारी अब {partb} गेंदों में {part} रनों की हो गई है।"),
        listOf("Ye partnership ab {partb} balls mein {part} run ki ho gayi hai."))
    val X_RR = Pool(
        listOf("Current run rate {rr}. At this rate, {proj} is on the cards.", "They're scoring at {rr}. A total around {proj} looks likely."),
        listOf("मौजूदा रन रेट {rr}। इसी रफ़्तार से स्कोर {proj} तक जा सकता है।"),
        listOf("Current run rate {rr}. Isi speed se {proj} tak ja sakte hain."))
    val X_BAT = Pool(
        listOf("{bat} has {runs} off {b} with {fours} fours and {sixes} sixes so far.", "{bat} is looking good on {runs} from {b} balls."),
        listOf("{bat} {b} गेंदों में {runs} रन पर, {fours} चौके और {sixes} छक्के।"),
        listOf("{bat} {b} balls mein {runs} pe, {fours} chauke aur {sixes} chhakke."))
    val X_OVER_CHASE = Pool(
        listOf("They need {rrr} an over from here - {need} off {left}.", "Required rate {rrr}. {team} can't let this drift."),
        listOf("अब हर ओवर में {rrr} रन चाहिए - {left} गेंदों में {need}।"),
        listOf("Ab har over mein {rrr} chahiye - {left} balls mein {need}."))
    val X_MAIDEN = Pool(
        listOf("Brilliant discipline. Nothing to hit in that over.", "That's how you build pressure - line and length, over after over."),
        listOf("शानदार अनुशासन। उस ओवर में मारने लायक कोई गेंद नहीं थी।"),
        listOf("Kamaal ka discipline. Us over mein maarne layak ek bhi ball nahi thi."))
    val X_MILESTONE = Pool(
        listOf("What a knock. {bat} has paced it perfectly - {fours} fours and {sixes} sixes.", "Class is permanent. {bat} has played the conditions superbly."),
        listOf("क्या पारी है। {bat} ने इसे बेहतरीन ढंग से संवारा - {fours} चौके, {sixes} छक्के।"),
        listOf("Kya innings hai! {bat} ne perfectly pace kiya - {fours} chauke, {sixes} chhakke."))
    val X_HATTRICK = Pool(
        listOf("Remarkable! Three different deliveries, three wickets. That's a spell people will talk about for years."),
        listOf("अद्भुत! तीन अलग गेंदें, तीन विकेट। यह स्पेल सालों याद रखा जाएगा।"),
        listOf("Adbhut! Teen alag balls, teen wicket. Ye spell saalon yaad rahega."))

    /* ---------- match frame ---------- */
    val INTRO = Pool(
        listOf("Welcome to {venue} for {comp}. {team} take on {opp}.", "Hello and welcome to {venue}! It's {team} against {opp} in {comp}."),
        listOf("{venue} से नमस्कार! {comp} में आज {team} का मुकाबला {opp} से।"),
        listOf("Namaskar aur swagat hai {venue} se! {comp} mein aaj {team} vs {opp}."))
    val TOSS = Pool(
        listOf("{team} won the toss and chose to {dec}. {weather}, and a {pitch} pitch.", "{team} called right and will {dec} first. Conditions: {weather}, {pitch} surface."),
        listOf("{team} ने टॉस जीतकर पहले {dec} का फैसला किया। मौसम {weather}, पिच {pitch}।"),
        listOf("{team} ne toss jeet ke pehle {dec} ka faisla kiya. Mausam {weather}, pitch {pitch}."))
    val INNINGS_END = Pool(
        listOf("That's the end of the {team} innings: {score}.", "{team} finish on {score}."),
        listOf("{team} की पारी {score} पर समाप्त।"),
        listOf("{team} ki innings {score} pe khatam."))
    val TARGET = Pool(
        listOf("{opp} need {n} to win.", "The target for {opp}: {n}."),
        listOf("{opp} को जीत के लिए {n} रन चाहिए।"),
        listOf("{opp} ko jeet ke liye {n} run chahiye."))
    val NEW_INNINGS = Pool(
        listOf("{team} begin their innings.", "Here come {team} to bat."),
        listOf("{team} की बल्लेबाज़ी शुरू।"),
        listOf("{team} ki batting shuru."))
}
