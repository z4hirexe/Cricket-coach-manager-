package com.cricketcoach.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import com.cricketcoach.manager.R
import com.cricketcoach.manager.save.Settings
import java.util.Locale

/**
 * All game audio: SFX (SoundPool), looping music / layered stadium ambience (MediaPlayer) and spoken commentary (TextToSpeech).
 * Audio 2.0: per-event sound design (edge, pad, catch, near-miss "ooh", dhol, bugle), a three-layer crowd (bed / chant / tension)
 * that follows the match situation, an auction-room scene and a per-channel mixer (master, bat & ball, crowd, ambience, UI, music, voice).
 */
object GameAudio {
    enum class Scene { NONE, MENU, MATCH, AUCTION }
    private var ctx: Context? = null
    private var settings: Settings? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var music: MediaPlayer? = null
    private var ambience: MediaPlayer? = null
    private var chant: MediaPlayer? = null
    private var tension: MediaPlayer? = null
    private var roomAmb: MediaPlayer? = null
    private var musicRes = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val rnd = java.util.Random()
    /** crowd mood targets (0..1) and the smoothed values actually applied to the layers */
    private var moodExcite = 0.15f; private var moodTension = 0f
    private var curChant = 0f; private var curTension = 0f; private var curBed = 0.5f
    private var swellUntil = 0L
    private var alt = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var scene = Scene.NONE
    private var paused = false
    private var lastClick = 0L

    /** Bind the device-local settings to the mixer. */
    fun bind(s: Settings) { settings = s; refresh() }

    fun init(c: Context, s: Settings) {
        if (ctx != null) { bind(s); return }
        ctx = c.applicationContext; settings = s
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build()
        val map = mapOf("bat" to R.raw.sfx_bat, "batbig" to R.raw.sfx_bat_big, "stumps" to R.raw.sfx_stumps, "cheer" to R.raw.sfx_cheer,
            "cheer2" to R.raw.sfx_cheer2, "roar" to R.raw.sfx_roar, "roar2" to R.raw.sfx_roar2, "wicket" to R.raw.sfx_wicket,
            "applause" to R.raw.sfx_applause, "groan" to R.raw.sfx_groan, "ooh" to R.raw.sfx_ooh, "appeal" to R.raw.sfx_appeal,
            "catch" to R.raw.sfx_catch, "dhol" to R.raw.sfx_dhol, "bugle" to R.raw.sfx_bugle,
            "edge" to R.raw.sfx_edge, "pad" to R.raw.sfx_pad, "glove" to R.raw.sfx_glove,
            "click" to R.raw.sfx_click, "hammer" to R.raw.sfx_hammer, "coin" to R.raw.sfx_coin, "horn" to R.raw.sfx_horn,
            "whoosh" to R.raw.sfx_whoosh, "success" to R.raw.sfx_success, "notify" to R.raw.sfx_notify, "error" to R.raw.sfx_error,
            "gavel" to R.raw.sfx_gavel_tap, "sold" to R.raw.sfx_sold, "unsold" to R.raw.sfx_unsold, "paddle" to R.raw.sfx_paddle,
            "rivalbid" to R.raw.sfx_bid_rival)
        for ((k, r) in map) ids[k] = pool!!.load(ctx, r, 1)
        try {
            tts = TextToSpeech(ctx) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    val t = tts ?: return@TextToSpeech
                    val r = t.setLanguage(Locale("en", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.UK)
                    t.setSpeechRate(1.08f); ttsReady = true
                }
            }
        } catch (_: Exception) { tts = null }
    }

    private val vol: Float get() = settings?.let { if (it.mute) 0f else it.volume } ?: 0.8f
    private val BAT = setOf("bat", "batbig", "stumps", "edge", "pad", "glove")
    private val CROWD = setOf("cheer", "cheer2", "roar", "roar2", "applause", "groan", "wicket", "ooh", "appeal", "catch", "dhol", "bugle", "horn")

    /** Final volume for a sound category: master x channel (Bat & Ball, Crowd, Ambience, UI, Commentary, Music). */
    fun channel(name: String): Float {
        val s = settings ?: return 0.8f
        if (s.mute) return 0f
        return mix(s.volume, name, s.volBat, s.volCrowd, s.volMusic, s.volCommentary, s.volAmbience, s.volUi,
            s.muteBat, s.muteCrowd, s.muteMusic || !s.music, s.muteCom, s.muteUi)
    }

    /** Pure mixer maths (unit-tested): master x channel, a muted channel is silent. Stadium ambience follows the Crowd channel. */
    fun mix(master: Float, name: String, bat: Float, crowd: Float, music: Float, com: Float, amb: Float, ui: Float,
            mBat: Boolean, mCrowd: Boolean, mMusic: Boolean, mCom: Boolean, mUi: Boolean): Float = master * when (name) {
        in BAT -> if (mBat) 0f else bat
        in CROWD -> if (mCrowd) 0f else crowd
        "music" -> if (mMusic) 0f else music
        "voice" -> if (mCom) 0f else com
        "ambience" -> if (mCrowd) 0f else amb * (0.4f + 0.6f * crowd)
        else -> if (mUi) 0f else ui
    }

    /** Plays an SFX. [jitter] adds a small random pitch change so repeated sounds never feel copy-pasted. */
    fun play(name: String, gain: Float = 1f, rate: Float = 1f, jitter: Float = 0f) {
        if (settings == null || paused) return
        val id = ids[name] ?: return
        val v = (channel(name) * gain).coerceIn(0f, 1f)
        if (v <= 0.001f) return
        val r = if (jitter > 0f) rate * (1f + (rnd.nextFloat() * 2f - 1f) * jitter) else rate
        pool?.play(id, v, v, 1, 0, r.coerceIn(0.5f, 2f))
    }

    /** Same as [play] after [ms] milliseconds (layered stings: e.g. dhol after the six roar). */
    fun playLater(ms: Long, name: String, gain: Float = 1f, jitter: Float = 0f) { main.postDelayed({ play(name, gain, 1f, jitter) }, ms) }

    fun click() { val now = System.currentTimeMillis(); if (now - lastClick > 60) { lastClick = now; play("click", 0.5f) } }

    /* ---------------- auction room ---------------- */
    fun auctionPaddle() = play("paddle", 0.9f, 1f, 0.05f)
    fun auctionRivalBid() = play("rivalbid", 0.8f, 1f, 0.08f)
    fun auctionGoing(n: Int) = play("gavel", if (n >= 2) 0.9f else 0.7f, if (n >= 2) 0.94f else 1f)
    fun auctionSold(mine: Boolean) { play("sold"); if (mine) playLater(350, "success", 0.8f) }
    fun auctionUnsold() = play("unsold", 0.8f)

    private var ttsLang = "EN"

    /** Speaks a commentary line in English (en-IN) or Hindi (hi-IN) at the Commentary channel volume. */
    fun speak(text: String, lang: String = "EN") = speakAs(text, lang, null, true)

    private var ttsVoiceKey = ""
    /**
     * Speaks a commentary line as [caster] (distinct voice where the phone has several, plus the caster's pitch and pace).
     * [flush] = true interrupts whatever is being said (new delivery); false queues it (analyst follow-up).
     * Hinglish ("HG") is Roman-script, so it uses the Indian-English voice.
     */
    fun speakAs(text: String, lang: String, caster: com.cricketcoach.manager.game.Caster?, flush: Boolean) {
        val v = channel("voice")
        if (settings == null || !ttsReady || paused || v <= 0.01f) return
        val t = tts ?: return
        try {
            val l = if (lang == "HI") "HI" else "EN"
            if (l != ttsLang) {
                val r = t.setLanguage(if (l == "HI") Locale("hi", "IN") else Locale("en", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { if (l == "HI") return else t.setLanguage(Locale.UK) }
                ttsLang = l; ttsVoiceKey = ""
            }
            if (caster != null) {
                val key = "$l/${caster.female}"
                if (key != ttsVoiceKey) {
                    ttsVoiceKey = key
                    // pick an offline voice of this language; female casters get a different voice than male ones when available
                    val lc = if (l == "HI") "hi" else "en"
                    val voices = t.voices?.filter { it.locale.language == lc && (it.locale.country == "IN" || lc == "en") && !it.isNetworkConnectionRequired }
                        ?.sortedWith(compareBy({ it.locale.country != "IN" }, { it.name })) ?: emptyList()
                    val femaleHint = voices.filter { it.name.contains("female", true) || it.name.contains("-x-ene") || it.name.contains("-x-cxx") || it.name.contains("-x-hie") }
                    val maleHint = voices.filter { it.name.contains("male", true) && !it.name.contains("female", true) || it.name.contains("-x-end") || it.name.contains("-x-ahp") || it.name.contains("-x-hia") }
                    val chosen = (if (caster.female) femaleHint.firstOrNull() ?: voices.getOrNull(0) else maleHint.firstOrNull() ?: voices.getOrNull(1) ?: voices.getOrNull(0))
                    if (chosen != null) t.voice = chosen
                }
                t.setPitch(caster.pitch); t.setSpeechRate(caster.rate)
            } else { t.setPitch(1f); t.setSpeechRate(1f) }
            val b = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, v.coerceIn(0f, 1f)) }
            t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, b, "ccm${System.nanoTime()}")
        } catch (_: Exception) { }
    }

    fun hindiVoiceAvailable(): Boolean = try {
        val t = tts; t != null && t.isLanguageAvailable(Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE
    } catch (_: Exception) { false }

    /** Sound design for one delivery, played when the 3D animation reaches the matching moment. */
    /** Impact moment (bat on ball / ball on pad / edge / gloves). */
    fun onBat(kind: String, how: String = "") {
        when (kind) {
            "SIX", "FOUR" -> play("batbig", 1f, 1f, 0.04f)
            "RUN", "NB" -> play("bat", 0.8f, 1f, 0.06f)
            "DOT" -> if (rnd.nextFloat() < 0.25f) play("glove", 0.5f, 1f, 0.05f) else play("bat", 0.45f, 1.1f, 0.06f)
            "WIDE" -> play("glove", 0.55f, 1f, 0.05f)
            "APPEAL" -> play("pad", 0.9f, 1f, 0.05f)
            "WICKET" -> when {
                how.startsWith("c †") -> play("edge", 1f, 1f, 0.05f)   // caught behind: thin nick
                how.startsWith("lbw") -> play("pad", 1f, 1f, 0.04f)
                how.startsWith("c ") -> play("bat", 0.85f, 0.96f, 0.05f)                            // mistimed into the air
                how.startsWith("run out") -> play("bat", 0.6f, 1f, 0.05f)
                how.startsWith("st ") -> play("glove", 0.7f)
            }
        }
    }

    /** Outcome moment: the crowd reaction (alternating variants + layered stings) and the crowd mood swell. */
    fun onOutcome(kind: String, how: String, milestone: Boolean) {
        val react = settings?.crowdReact != false
        alt = !alt
        when (kind) {
            "SIX" -> { play(if (alt) "roar" else "roar2", 1f, 1f, 0.03f); if (react) { playLater(650, "dhol", 0.75f); swell(9000) } }
            "FOUR" -> { play(if (alt) "cheer" else "cheer2", 0.85f, 1f, 0.04f); if (react) { if (rnd.nextFloat() < 0.35f) playLater(500, "bugle", 0.55f); swell(5000) } }
            "WICKET" -> {
                when {
                    how.startsWith("b ") || how.startsWith("run out") -> play("stumps", 1f, 1f, 0.04f)
                    how.startsWith("st ") -> { play("stumps", 0.9f); }
                    how.startsWith("lbw") -> play("appeal", 0.8f)
                    how.startsWith("c ") -> play("catch", 0.9f, 1f, 0.03f)
                }
                playLater(if (how.startsWith("lbw")) 450 else 120, "wicket", 1f, 0.03f)
                if (react) swell(6000)
            }
            "APPEAL" -> { play("appeal", 0.8f, 1f, 0.04f); playLater(900, "groan", 0.6f) }
            "DOT" -> if (react && rnd.nextFloat() < 0.12f) play("ooh", 0.55f, 1f, 0.05f)   // beaten outside off
        }
        if (milestone) playLater(300, "applause")
    }

    /* ---------------- dynamic crowd ---------------- */
    /** Match situation -> crowd layers (see [CrowdMood]); called after every ball. */
    fun setMood(m: Mood) { moodExcite = m.excite; moodTension = m.tension; startFader() }
    private fun swell(ms: Long) { swellUntil = System.currentTimeMillis() + ms; startFader() }

    private var fading = false
    private val fader = object : Runnable {
        override fun run() {
            val live = scene == Scene.MATCH && !paused
            val react = settings?.crowdReact != false
            val boost = if (System.currentTimeMillis() < swellUntil) 0.55f else 0f
            val tChant = if (live && react) (moodExcite * 0.6f + boost).coerceIn(0f, 1f) else 0f
            val tTen = if (live && react) moodTension else 0f
            val tBed = if (live) (0.5f - 0.2f * tTen + 0.15f * boost).coerceIn(0.2f, 0.7f) else 0f
            val k = 0.12f
            curChant += (tChant - curChant) * k; curTension += (tTen - curTension) * k; curBed += (tBed - curBed) * k
            applyLayers()
            val settled = Math.abs(tChant - curChant) < 0.01f && Math.abs(tTen - curTension) < 0.01f && Math.abs(tBed - curBed) < 0.01f
            if (!settled || System.currentTimeMillis() < swellUntil) main.postDelayed(this, 100) else fading = false
        }
    }
    private fun startFader() { if (!fading) { fading = true; main.post(fader) } }

    private fun applyLayers() {
        val a = channel("ambience")
        ambience?.setVolume(a * curBed, a * curBed)
        chant?.let { val v = a * curChant * 0.8f; it.setVolume(v, v); if (v > 0.01f && !it.isPlaying && !paused) it.start() else if (v <= 0.01f && it.isPlaying) it.pause() }
        tension?.let { val v = a * curTension * 0.9f; it.setVolume(v, v); if (v > 0.01f && !it.isPlaying && !paused) it.start() else if (v <= 0.01f && it.isPlaying) it.pause() }
    }

    /** Current smoothed layer levels (bed, chant, tension) - for the settings meter / tests. */
    fun layerLevels(): FloatArray = floatArrayOf(curBed, curChant, curTension)

    fun setScene(sc: Scene) {
        scene = sc
        refresh()
    }

    private fun loop(c: Context, res: Int): MediaPlayer? = try { MediaPlayer.create(c, res)?.apply { isLooping = true } } catch (_: Exception) { null }

    fun refresh() {
        settings ?: return
        val c = ctx ?: return
        val wantRes = when (scene) { Scene.MENU -> R.raw.music_theme; Scene.AUCTION -> R.raw.music_auction; else -> 0 }
        val wantMusic = !paused && channel("music") > 0.01f && wantRes != 0
        if (wantMusic) {
            if (music != null && musicRes != wantRes) { music?.release(); music = null }
            if (music == null) { music = loop(c, wantRes); musicRes = wantRes }
            val mv = channel("music") * (if (scene == Scene.AUCTION) 0.6f else 0.9f)
            music?.setVolume(mv, mv)
            if (music?.isPlaying == false) music?.start()
        } else music?.let { if (it.isPlaying) it.pause() }
        // stadium: bed layer always on, chant / tension layers are faded by the mood fader
        val wantAmb = !paused && channel("ambience") > 0.01f && scene == Scene.MATCH
        if (wantAmb) {
            if (ambience == null) ambience = loop(c, R.raw.amb_crowd)
            if (chant == null) chant = loop(c, R.raw.amb_chant)
            if (tension == null) tension = loop(c, R.raw.amb_tension)
            if (ambience?.isPlaying == false) ambience?.start()
            applyLayers(); startFader()
        } else {
            for (p in listOf(ambience, chant, tension)) p?.let { if (it.isPlaying) it.pause() }
            if (scene != Scene.MATCH) { curChant = 0f; curTension = 0f; curBed = 0.5f; moodExcite = 0.15f; moodTension = 0f }
        }
        // auction room murmur under the music
        val wantRoom = !paused && channel("ambience") > 0.01f && scene == Scene.AUCTION
        if (wantRoom) {
            if (roomAmb == null) roomAmb = loop(c, R.raw.amb_auction)
            val rv = channel("ambience") * 0.5f; roomAmb?.setVolume(rv, rv)
            if (roomAmb?.isPlaying == false) roomAmb?.start()
        } else roomAmb?.let { if (it.isPlaying) it.pause() }
    }

    fun onPause() { paused = true; tts?.stop(); refresh() }
    fun onResume() { paused = false; refresh() }
}
