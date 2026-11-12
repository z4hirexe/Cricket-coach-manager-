package com.cricketcoach.manager.game

data class TeamDef(val id: String, val name: String, val short: String, val primary: Long, val secondary: Long, val country: String = "")

object Consts {
    val NATIONS = listOf(
        TeamDef("N_IND", "India", "IND", 0xFF1565C0, 0xFFFF9800, "India"),
        TeamDef("N_AUS", "Australia", "AUS", 0xFFFFD600, 0xFF1B5E20, "Australia"),
        TeamDef("N_ENG", "England", "ENG", 0xFF0D47A1, 0xFFD32F2F, "England"),
        TeamDef("N_PAK", "Pakistan", "PAK", 0xFF1B5E20, 0xFFFFFFFF, "Pakistan"),
        TeamDef("N_SA", "South Africa", "SA", 0xFF2E7D32, 0xFFFFC107, "South Africa"),
        TeamDef("N_NZ", "New Zealand", "NZ", 0xFF212121, 0xFFFFFFFF, "New Zealand"),
        TeamDef("N_BAN", "Bangladesh", "BAN", 0xFF00695C, 0xFFD32F2F, "Bangladesh"),
        TeamDef("N_SL", "Sri Lanka", "SL", 0xFF283593, 0xFFFFC107, "Sri Lanka"),
        TeamDef("N_AFG", "Afghanistan", "AFG", 0xFF1565C0, 0xFFD32F2F, "Afghanistan"),
        TeamDef("N_WI", "West Indies", "WI", 0xFF880E4F, 0xFFFFC107, "West Indies"),
        TeamDef("N_IRE", "Ireland", "IRE", 0xFF43A047, 0xFFFFFFFF, "Ireland"),
        TeamDef("N_ZIM", "Zimbabwe", "ZIM", 0xFFD32F2F, 0xFFFFD600, "Zimbabwe")
    )
    fun nationByCountry(c: String) = NATIONS.firstOrNull { it.country == c }

    /** Real IPL franchise names; badges are original in-game artwork (no official logos). */
    val IPL = listOf(
        TeamDef("F_CSK", "Chennai Super Kings", "CSK", 0xFFFDD835, 0xFF1565C0),
        TeamDef("F_MI", "Mumbai Indians", "MI", 0xFF0D47A1, 0xFFD4B786),
        TeamDef("F_RCB", "Royal Challengers Bengaluru", "RCB", 0xFFC62828, 0xFF212121),
        TeamDef("F_KKR", "Kolkata Knight Riders", "KKR", 0xFF4A148C, 0xFFFBC02D),
        TeamDef("F_DC", "Delhi Capitals", "DC", 0xFF1565C0, 0xFFE53935),
        TeamDef("F_RR", "Rajasthan Royals", "RR", 0xFFEC407A, 0xFF1A237E),
        TeamDef("F_PBKS", "Punjab Kings", "PBKS", 0xFFD32F2F, 0xFFBDBDBD),
        TeamDef("F_SRH", "Sunrisers Hyderabad", "SRH", 0xFFF57C00, 0xFF212121),
        TeamDef("F_LSG", "Lucknow Super Giants", "LSG", 0xFF0277BD, 0xFFFF7043),
        TeamDef("F_GT", "Gujarat Titans", "GT", 0xFF1A237E, 0xFFC5A880)
    )

    /** Original fictional city clubs for the 4-division career league. */
    val CLUBS: Map<String, List<Pair<String, String>>> = linkedMapOf(
        "India" to listOf("Mumbai Mavericks" to "MUM", "Delhi Dynamos" to "DEL", "Kolkata Comets" to "KOL", "Chennai Cheetahs" to "CHE",
            "Bengaluru Blasters" to "BLR", "Hyderabad Hawks" to "HYD", "Jaipur Jaguars" to "JAI"),
        "South Africa" to listOf("Cape Town Corsairs" to "CTC", "Johannesburg Jackals" to "JHB", "Durban Drifters" to "DUR"),
        "Pakistan" to listOf("Lahore Lancers" to "LAH", "Karachi Kestrels" to "KAR", "Multan Monarchs" to "MUL"),
        "West Indies" to listOf("Kingston Kraken" to "KIN", "Bridgetown Barracudas" to "BRI", "Port of Spain Pirates" to "POS"),
        "England" to listOf("London Lynx" to "LON", "Manchester Mariners" to "MAN", "Birmingham Bulldogs" to "BIR"),
        "Sri Lanka" to listOf("Colombo Crusaders" to "COL", "Kandy Kingfishers" to "KAN"),
        "Australia" to listOf("Sydney Stingrays" to "SYD", "Perth Pelicans" to "PER"),
        "Zimbabwe" to listOf("Harare Hornets" to "HAR", "Bulawayo Buffaloes" to "BUL"),
        "New Zealand" to listOf("Auckland Albatross" to "AUK", "Wellington Wanderers" to "WEL"),
        "Bangladesh" to listOf("Dhaka Defenders" to "DHA", "Chattogram Chargers" to "CTG"),
        "Afghanistan" to listOf("Kabul Kites" to "KAB", "Kandahar Knights" to "KDH"),
        "Ireland" to listOf("Dublin Druids" to "DUB")
    )

    val CLUB_COLORS = listOf(
        0xFF00C853 to 0xFFFFC107, 0xFF1565C0 to 0xFFFFFFFF, 0xFFD32F2F to 0xFFFFD600, 0xFF6A1B9A to 0xFFFFC107,
        0xFF00838F to 0xFFFF7043, 0xFF37474F to 0xFF00E676, 0xFFEF6C00 to 0xFF212121, 0xFFAD1457 to 0xFF90CAF9,
        0xFF2E7D32 to 0xFFFFFFFF, 0xFF283593 to 0xFFFF5252, 0xFF4E342E to 0xFFFFCC80, 0xFF0277BD to 0xFFFFEB3B
    )

    val LOGO_NAMES = listOf("Shield", "Crest", "Star", "Crossed Bats", "Ball", "Crown", "Hexagon", "Wicket")
    val JERSEY_NAMES = listOf("Classic", "Sash", "Stripes", "Split", "Hoops", "Chevron")

    val PITCHES = listOf("BALANCED", "FLAT", "GREEN", "DUSTY")
    val WEATHER = listOf("Sunny", "Cloudy", "Overcast", "Humid")

    val SPONSORS = listOf("Apex Telecom", "Monsoon Motors", "Brightline Bank", "Saffron Foods", "Nimbus Cloud",
        "Vertex Paints", "Orbit Airlines", "Coral Insurance", "Peak Energy Drinks", "Lotus Electronics", "Trident Cement", "Zephyr Fintech")

    // Name pools for FICTIONAL academy/youth prospects (clearly flagged in-game).
    private val SOUTH_ASIA_F = listOf("Aarav", "Vihaan", "Arjun", "Kabir", "Rohan", "Ishaan", "Dev", "Yash", "Aditya", "Karan", "Nikhil", "Pranav", "Siddharth", "Tanmay", "Ravi", "Harsh")
    private val INDIA_L = listOf("Sharma", "Patel", "Iyer", "Reddy", "Nair", "Singh", "Kulkarni", "Desai", "Chauhan", "Menon", "Bose", "Rawat", "Thakur", "Joshi", "Mishra", "Pillai")
    private val PAK_F = listOf("Hamza", "Usman", "Bilal", "Saad", "Zain", "Faizan", "Arsalan", "Haris", "Talha", "Junaid")
    private val PAK_L = listOf("Khan", "Malik", "Qureshi", "Butt", "Raza", "Chaudhry", "Abbasi", "Mirza", "Sheikh", "Javed")
    private val SL_F = listOf("Kasun", "Dilshan", "Tharindu", "Nuwan", "Charith", "Lahiru", "Pathum", "Dasun", "Ashen", "Sadeera")
    private val SL_L = listOf("Perera", "Fernando", "Silva", "Jayasuriya", "Bandara", "Wickramasinghe", "Rajapaksa", "Kumara", "Dissanayake", "Herath")
    private val BAN_F = listOf("Tanvir", "Rakib", "Sabbir", "Mahedi", "Nayeem", "Arif", "Shoriful", "Towhid", "Parvez", "Rishad")
    private val BAN_L = listOf("Hossain", "Rahman", "Islam", "Ahmed", "Hasan", "Chowdhury", "Uddin", "Karim", "Sarkar", "Mia")
    private val AFG_F = listOf("Rahmat", "Najib", "Farid", "Ibrahim", "Zubaid", "Wafadar", "Sediq", "Nangyal", "Bashir", "Ijaz")
    private val AFG_L = listOf("Zadran", "Ahmadzai", "Safi", "Stanikzai", "Shinwari", "Mangal", "Omarzai", "Naib", "Atal", "Sharifi")
    private val ANGLO_F = listOf("Oliver", "Jack", "Harry", "Lachlan", "Mitchell", "Cooper", "Finn", "Liam", "Ethan", "Noah", "Archie", "Tom", "Sam", "Ben", "Will", "Callum")
    private val ANGLO_L = listOf("Smith", "Taylor", "Brown", "Wilson", "Clarke", "Hughes", "Walker", "Turner", "Bennett", "Fletcher", "Harris", "Cole", "Ward", "Reid", "Morgan", "Baker")
    private val SA_F = listOf("Thabo", "Ruan", "Kyle", "Sipho", "Jaco", "Lungi", "Pieter", "Wiaan", "Andile", "Dewald")
    private val SA_L = listOf("van Wyk", "Botha", "Nkosi", "du Toit", "Mokoena", "Pretorius", "Ndlovu", "Steyn", "Coetzee", "Mahlangu")
    private val WI_F = listOf("Shai", "Kemar", "Jaden", "Akeem", "Shamar", "Romario", "Keacy", "Alick", "Jomari", "Tevin")
    private val WI_L = listOf("Joseph", "Charles", "Thomas", "Holder", "Greaves", "Lewis", "Bramble", "Phillip", "Ramdin", "Sinclair")
    private val ZIM_F = listOf("Tinashe", "Tendai", "Blessing", "Farai", "Tanaka", "Kudzai", "Takudzwa", "Wellington", "Brian", "Tafadzwa")
    private val ZIM_L = listOf("Moyo", "Ncube", "Chikwanha", "Mutasa", "Sibanda", "Dube", "Marumani", "Nyathi", "Chari", "Madziva")
    private val IRE_F = listOf("Conor", "Cian", "Sean", "Niall", "Ronan", "Eoin", "Darragh", "Fionn", "Oisin", "Liam")
    private val IRE_L = listOf("Murphy", "Kelly", "O'Brien", "Byrne", "Ryan", "Walsh", "Doyle", "McCarthy", "Gallagher", "Kennedy")

    fun namePool(country: String): Pair<List<String>, List<String>> = when (country) {
        "India" -> SOUTH_ASIA_F to INDIA_L
        "Pakistan" -> PAK_F to PAK_L
        "Sri Lanka" -> SL_F to SL_L
        "Bangladesh" -> BAN_F to BAN_L
        "Afghanistan" -> AFG_F to AFG_L
        "South Africa" -> SA_F to SA_L
        "West Indies" -> WI_F to WI_L
        "Zimbabwe" -> ZIM_F to ZIM_L
        "Ireland" -> IRE_F to IRE_L
        else -> ANGLO_F to ANGLO_L
    }

    val ROLE_NAMES = mapOf("BAT" to "Batter", "BOWL" to "Bowler", "AR" to "All-rounder", "WK" to "Wicket-keeper")
}
