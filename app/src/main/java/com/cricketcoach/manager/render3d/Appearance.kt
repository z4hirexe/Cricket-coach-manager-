package com.cricketcoach.manager.render3d

/* ============================================================================================
 *  Phase C — individual 3D look per player.
 *
 *  Every player gets his own parametric cricketer: skin tone, hair style + colour, facial hair, build, height and
 *  shirt number, all applied to the shared procedural rig (cricketer.glb) at runtime.
 *
 *  - Well-known real players use a short list of coarse, publicly observable STYLE DESCRIPTORS (e.g. "short black hair,
 *    full beard, slim, ~178 cm").  These give a loose, recognisable resemblance only: no photos, scans, textures or
 *    likeness assets are used or shipped, and the face geometry is the same generic head for everybody.
 *  - Everyone else (generated/fictional players, domestic squads, umpires, crowd) gets a deterministic FICTIONAL look
 *    hashed from the player id.
 * ============================================================================================ */

enum class Build(val mesh: String) { SLIM("slim"), ATHLETIC("athletic"), HEAVY("heavy") }

object Hair {
    const val NONE = 0; const val BUZZ = 1; const val SHORT = 2; const val LONG = 3; const val CURLY = 4; const val BUN = 5
    val NAMES = arrayOf("Shaved", "Buzz cut", "Short", "Long", "Curly", "Top knot / patka")
    /** mesh in cricketer.glb for a style (null = none) */
    fun mesh(style: Int): String? = when (style) { BUZZ -> "hair_buzz"; SHORT -> "hair_short"; LONG -> "hair_long"; CURLY -> "hair_curly"; BUN -> "hair_bun"; else -> null }
}

object Facial {
    const val NONE = 0; const val STUBBLE = 1; const val BEARD = 2; const val GOATEE = 3; const val MOUSTACHE = 4
    val NAMES = arrayOf("Clean shaven", "Stubble", "Full beard", "Goatee", "Moustache")
    fun mesh(style: Int): String? = when (style) { STUBBLE -> "beard_stubble"; BEARD -> "beard"; GOATEE -> "beard_goatee"; MOUSTACHE -> "moustache"; else -> null }
}

/**
 * Appearance of one player.
 * [source] is [Look.CURATED] for real players with style descriptors, [Look.GENERATED] for fictional/hashed looks.
 */
class Look(
    val skin: FloatArray, val hair: Int, val hairCol: FloatArray, val facial: Int, val build: Build,
    val heightCm: Int, val number: Int, val source: Int, val skinIndex: Int = 0, val hairColIndex: Int = 0,
) {
    val heavy get() = build == Build.HEAVY
    val beard get() = facial != Facial.NONE
    /** uniform model scale (the rig is 1.80 m tall) */
    val scale get() = heightCm / 180f
    /** in-match scale is kept within ±3 % so bat sweet spots and ball-in-hand positions stay exact */
    val matchScale get() = scale.coerceIn(0.97f, 1.03f)
    val isCurated get() = source == CURATED

    fun describe(): String = "${Hair.NAMES[hair]} ${HAIR_NAMES[hairColIndex].lowercase()} hair · ${Facial.NAMES[facial].lowercase()} · " +
        "${build.name.lowercase()} build · ~$heightCm cm"

    companion object {
        const val GENERATED = 0; const val CURATED = 1

        val SKINS = arrayOf(floatArrayOf(0.62f, 0.43f, 0.31f), floatArrayOf(0.48f, 0.31f, 0.21f), floatArrayOf(0.36f, 0.23f, 0.15f),
            floatArrayOf(0.25f, 0.16f, 0.11f), floatArrayOf(0.78f, 0.60f, 0.48f), floatArrayOf(0.70f, 0.52f, 0.40f))
        val HAIRS = arrayOf(floatArrayOf(0.03f, 0.025f, 0.02f), floatArrayOf(0.08f, 0.05f, 0.03f), floatArrayOf(0.20f, 0.13f, 0.07f),
            floatArrayOf(0.55f, 0.42f, 0.24f), floatArrayOf(0.42f, 0.17f, 0.06f), floatArrayOf(0.42f, 0.41f, 0.40f))
        val HAIR_NAMES = arrayOf("Black", "Dark brown", "Brown", "Blond", "Ginger", "Grey")

        private fun h(id: String): Long { var x = 1125899906842597L; for (c in id) x = 31 * x + c.code; return x and 0x7fffffffffffffffL }

        /** Shirt number 1..98, stable per player (also printed on the 2D kit and the auction spotlight). */
        fun number(id: String): Int = (id.hashCode().let { if (it < 0) -it else it } % 98) + 1

        /** Look for a player: curated style descriptors if we have them, otherwise a deterministic fictional look. */
        fun of(id: String, country: String = ""): Look = Appearance.curated(id) ?: generated(id, country)

        fun generated(id: String, country: String = ""): Look {
            val hh = h(id)
            val c = country.lowercase()
            // broad regional skin-tone ranges keep crowds of generic models plausible; the individual pick is hashed
            val pool = when {
                c in listOf("india", "pakistan", "sri lanka", "bangladesh", "afghanistan", "nepal", "uae", "oman") -> intArrayOf(0, 1, 2, 5)
                c in listOf("west indies", "zimbabwe") -> intArrayOf(2, 3, 1)
                c in listOf("england", "australia", "new zealand", "ireland", "scotland", "netherlands") -> intArrayOf(4, 5, 0, 4)
                c == "south africa" -> intArrayOf(4, 2, 3, 5, 0)
                else -> intArrayOf(0, 1, 2, 3, 4, 5)
            }
            val si = pool[(hh % pool.size).toInt()]
            val fair = si == 4 || si == 5
            val hci = if (fair) ((hh shr 5) % 5).toInt() else ((hh shr 5) % 2).toInt()
            val hair = intArrayOf(Hair.SHORT, Hair.SHORT, Hair.SHORT, Hair.BUZZ, Hair.BUZZ, Hair.LONG, Hair.CURLY, Hair.NONE)[((hh shr 9) % 8).toInt()]
            val facial = intArrayOf(Facial.NONE, Facial.NONE, Facial.STUBBLE, Facial.STUBBLE, Facial.BEARD, Facial.BEARD, Facial.GOATEE, Facial.MOUSTACHE)[((hh shr 13) % 8).toInt()]
            val build = when ((hh shr 17) % 10) { 0L, 1L, 2L -> Build.SLIM; 3L, 4L -> Build.HEAVY; else -> Build.ATHLETIC }
            val height = 166 + ((hh shr 21) % 28).toInt() + (if (build == Build.HEAVY) 2 else 0)
            return Look(SKINS[si], hair, HAIRS[hci], facial, build, height, number(id), GENERATED, si, hci)
        }
    }
}

/**
 * Curated style descriptors for well-known real players, keyed by dataset id.
 * Code: skin(0-5) hair(N B S L C U) hairColour(0-5) facial(N S B G M) build(S A H) heightCm.
 *   skin 0 light-brown · 1 medium-brown · 2 dark-brown · 3 deep · 4 fair · 5 olive
 *   hair N shaved · B buzz · S short · L long · C curly · U top knot/patka
 * Approximate public appearance only (no likeness assets) — see file header.
 */
object Appearance {
    private val RAW = mapOf(
        // India
        "ba607b88" to "0 S 0 B A 175", "740742ef" to "0 S 0 B H 173", "462411b3" to "1 S 0 S S 178", "919a3be2" to "0 S 0 S H 170",
        "b4b99816" to "0 S 0 S A 178", "4a8a2e3b" to "1 S 0 B A 175", "dbe50b21" to "1 S 0 B A 183", "fe93fd9d" to "1 S 0 B S 173",
        "b17e2f24" to "0 S 0 B A 180", "271f83cd" to "1 B 0 B A 180", "8cf9814c" to "1 S 0 B A 173", "2f49c897" to "1 S 0 B S 178",
        "8d2c70ad" to "1 S 0 S S 168", "57ee1fde" to "1 S 0 S S 168", "495d42a5" to "1 S 0 S S 188", "2e171977" to "1 S 0 B A 183",
        "85ec8e33" to "0 S 0 B A 178", "a4cc73aa" to "1 S 0 B A 175", "752f7486" to "0 S 0 S S 168", "6c19c6e5" to "1 S 0 S S 172",
        "244048f6" to "0 U 0 B S 190", "0a509d6b" to "1 S 0 B A 175", "45a43fe2" to "0 S 0 S S 178", "f29185a1" to "0 S 0 S S 178",
        "b0482a1d" to "1 S 0 S S 175", "f19ccfad" to "1 S 0 S S 185", "a4e37e47" to "0 S 0 B H 183", "23eeb873" to "0 S 0 S A 178",
        "2e81a32d" to "0 S 0 S S 178", "d4f9dbd4" to "0 S 0 B A 180", "29e95537" to "0 S 0 S S 168",
        // Australia
        "ded9240e" to "4 S 1 S S 192", "3fb19989" to "4 S 1 S S 197", "03806cf8" to "4 S 2 S S 196", "30a45b23" to "4 S 1 N S 175",
        "dcce6f09" to "4 S 1 S A 170", "12b610c2" to "4 S 2 B A 180", "b681e71e" to "4 S 1 S A 180", "fa433be6" to "4 S 1 S A 178",
        "96a6a7ad" to "4 B 2 S S 181", "3d8feaf8" to "4 S 2 B H 193", "eaa76d3c" to "4 S 1 N S 198", "14f96089" to "4 U 1 B S 180",
        "331ea488" to "0 S 0 B S 177", "69d03465" to "4 S 2 S A 178", "f1f99156" to "4 S 2 B H 196", "9eb1455b" to "4 S 1 B A 183",
        "989889ff" to "4 S 1 S A 175",
        // England
        "a343262c" to "4 S 1 S S 183", "e087956b" to "4 B 4 B A 185", "99b75528" to "4 S 1 S A 180", "5574750c" to "3 S 3 S S 188",
        "8d92a2c3" to "4 S 1 S S 183", "4ae1755b" to "4 S 2 N A 183", "abb83e27" to "4 S 4 S A 178", "bb351c23" to "0 B 0 B S 183",
        "249d60c9" to "0 S 0 B S 173", "50c6bc2b" to "4 B 1 B H 185", "3d284ca3" to "4 S 3 S A 178", "e94915e6" to "4 S 1 N S 175",
        "922e1b19" to "4 S 1 N S 196", "e94bc520" to "4 S 1 N S 175", "d12143bf" to "4 S 5 S S 188", "eba6a852" to "4 S 3 S S 196",
        "9caf69a1" to "4 S 2 B A 183", "0f3ee070" to "4 S 1 S A 196",
        // New Zealand
        "d027ba9f" to "4 C 1 B S 173", "a818c1be" to "4 S 1 S S 180", "13c35c9e" to "4 S 1 B A 191", "df5a6881" to "4 S 1 S S 180",
        "ba5e1069" to "0 S 0 S S 185", "eade4650" to "4 S 2 B H 191", "e4a0deae" to "4 S 2 S S 183", "9a46c4e5" to "4 S 1 S A 180",
        "e824e6ee" to "4 S 1 S A 175", "2f9d0389" to "4 S 1 B A 183", "e84ac20c" to "4 S 1 B A 188",
        // South Africa
        "e62dd25d" to "3 B 0 S A 191", "372455c4" to "4 S 1 B S 170", "6a26221c" to "4 S 1 B A 178", "d67d5f00" to "4 S 2 B A 180",
        "235c2bb6" to "4 S 1 B A 178", "acdc62f5" to "4 B 1 B A 191", "81c36ee9" to "4 S 2 N S 206", "0b60eb09" to "1 S 0 S S 175",
        "9ffd1ac1" to "3 B 0 S S 162", "3355b542" to "4 S 1 B A 180", "844e79d1" to "4 S 1 N A 180", "85b3fab2" to "4 S 2 S S 188",
        "3204c99f" to "4 S 2 S A 188",
        // Pakistan / Afghanistan
        "8a75e999" to "5 S 0 B S 180", "2f26ac1a" to "5 S 0 B S 173", "45a7e761" to "5 S 0 S S 198", "1777c020" to "5 S 0 B A 180",
        "9de62878" to "5 S 0 B S 178", "9c9af282" to "5 S 0 N S 180", "24bb1c2f" to "5 S 0 B S 180", "54c58a5e" to "5 S 0 B A 175",
        "5f547c8b" to "5 S 0 S S 168", "62af8546" to "5 S 0 B A 183", "0bacade8" to "5 S 0 S A 175",
        // Sri Lanka / Bangladesh
        "a97c8ec2" to "1 C 0 B S 173", "5d1e7582" to "1 S 0 B S 170", "8ee36b18" to "1 S 0 S S 178", "64839cb3" to "1 L 0 N S 180",
        "7dc35884" to "1 S 0 S A 175", "a94e08ea" to "1 S 0 B S 160", "0404d43c" to "1 S 0 S S 175", "0a8fce53" to "1 S 0 B S 180",
        // West Indies
        "3241e3fd" to "3 B 0 B A 173", "bbd41817" to "3 B 0 B H 185", "9d430b40" to "3 B 0 S S 180", "48a1d7b7" to "3 C 0 B A 175",
        "0f721006" to "3 B 0 B A 201", "a757b0d8" to "3 N 0 B H 193", "1fc6ef83" to "3 B 0 S S 178", "b0946605" to "3 B 0 B S 191",
        "650d5e49" to "3 B 0 B H 188", "78eb4223" to "3 B 0 B A 175",
        // others
        "26d041c4" to "5 B 0 B A 180", "cf494ffe" to "4 S 4 B A 178",
    )

    private val cache = HashMap<String, Look?>()

    /** number of players with curated descriptors */
    val size get() = RAW.size
    val ids: Set<String> get() = RAW.keys
    /** raw codes (tests validate every one) */
    internal val codes: Map<String, String> get() = RAW

    @Synchronized
    fun curated(id: String): Look? = cache.getOrPut(id) { RAW[id]?.let { parse(id, it) } }

    internal fun parse(id: String, code: String): Look {
        val p = code.trim().split(Regex("\\s+"))
        require(p.size == 6) { "bad appearance code for $id: $code" }
        val si = p[0].toInt().coerceIn(0, 5)
        val hair = when (p[1]) { "N" -> Hair.NONE; "B" -> Hair.BUZZ; "S" -> Hair.SHORT; "L" -> Hair.LONG; "C" -> Hair.CURLY; "U" -> Hair.BUN; else -> error("hair ${p[1]}") }
        val hci = p[2].toInt().coerceIn(0, 5)
        val facial = when (p[3]) { "N" -> Facial.NONE; "S" -> Facial.STUBBLE; "B" -> Facial.BEARD; "G" -> Facial.GOATEE; "M" -> Facial.MOUSTACHE; else -> error("facial ${p[3]}") }
        val build = when (p[4]) { "S" -> Build.SLIM; "A" -> Build.ATHLETIC; "H" -> Build.HEAVY; else -> error("build ${p[4]}") }
        val height = p[5].toInt().coerceIn(150, 210)
        return Look(Look.SKINS[si], hair, Look.HAIRS[hci], facial, build, height, Look.number(id), Look.CURATED, si, hci)
    }
}

/** Gear sets for the 3D viewer / match roles. */
enum class Gear { BATTING, FIELDING, KEEPING, BOWLING }

/** Animation categories exposed in the profile 3D viewer (each maps to clips in cricketer.glb). */
enum class Showcase(val label: String, val clips: List<String>, val gear: Gear) {
    IDLE("Idle", listOf("idle"), Gear.FIELDING),
    BAT("Batting", listOf("bat_stance", "bat_cover_drive", "bat_stance", "bat_pull", "bat_stance", "bat_straight_drive", "bat_stance", "bat_loft", "bat_sweep", "bat_celebrate"), Gear.BATTING),
    BOWL_FAST("Pace", listOf("bowl_fast"), Gear.BOWLING),
    BOWL_SPIN("Spin", listOf("bowl_spin"), Gear.BOWLING),
    RUN("Running", listOf("run"), Gear.FIELDING),
    FIELD("Fielding", listOf("field_ready", "field_dive", "field_ready"), Gear.FIELDING),
    CATCH("Catching", listOf("field_ready", "field_catch"), Gear.FIELDING),
    THROW("Throwing", listOf("field_ready", "field_throw"), Gear.FIELDING),
    KEEP("Keeping", listOf("keep_crouch", "keep_collect", "keep_crouch", "keep_stump"), Gear.KEEPING),
    CELEBRATE("Celebrate", listOf("celebrate", "appeal", "celebrate"), Gear.FIELDING);

    companion object {
        /** every clip the viewer and the match need — verified against cricketer.glb by a unit test */
        val ALL_CLIPS get() = values().flatMap { it.clips }.toSet()
    }
}
