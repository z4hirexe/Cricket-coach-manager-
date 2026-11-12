package com.cricketcoach.manager.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import com.cricketcoach.manager.R
import com.cricketcoach.manager.save.Settings
import java.util.Locale

/** All game audio: SFX (SoundPool), looping music / stadium ambience (MediaPlayer) and spoken commentary (TextToSpeech). */
object GameAudio {
    enum class Scene { NONE, MENU, MATCH }
    private var ctx: Context? = null
    private var settings: Settings? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var music: MediaPlayer? = null
    private var ambience: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var scene = Scene.NONE
    private var paused = false
    private var lastClick = 0L

    /** Switch to another account's settings (each user has their own mixer). */
    fun bind(s: Settings) { settings = s; refresh() }

    fun init(c: Context, s: Settings) {
        if (ctx != null) { bind(s); return }
        ctx = c.applicationContext; settings = s
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        val map = mapOf("bat" to R.raw.sfx_bat, "batbig" to R.raw.sfx_bat_big, "stumps" to R.raw.sfx_stumps, "cheer" to R.raw.sfx_cheer,
            "roar" to R.raw.sfx_roar, "wicket" to R.raw.sfx_wicket, "applause" to R.raw.sfx_applause, "groan" to R.raw.sfx_groan,
            "click" to R.raw.sfx_click, "hammer" to R.raw.sfx_hammer, "coin" to R.raw.sfx_coin, "horn" to R.raw.sfx_horn,
            "whoosh" to R.raw.sfx_whoosh, "success" to R.raw.sfx_success)
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
    private val BAT = setOf("bat", "batbig", "stumps")
    private val CROWD = setOf("cheer", "roar", "applause", "groan", "wicket")

    /** Final volume for a sound category: master x channel (Bat & Ball, Crowd, Commentary, Music; UI uses master only). */
    fun channel(name: String): Float {
        val s = settings ?: return 0.8f
        if (s.mute) return 0f
        return s.volume * when (name) { in BAT -> s.volBat; in CROWD -> s.volCrowd; "music" -> if (s.music) s.volMusic else 0f; "voice" -> s.volCommentary; "ambience" -> s.volCrowd; else -> 1f }
    }

    fun play(name: String, gain: Float = 1f, rate: Float = 1f) {
        if (settings == null || paused) return
        val id = ids[name] ?: return
        val v = (channel(name) * gain).coerceIn(0f, 1f)
        if (v <= 0.001f) return
        pool?.play(id, v, v, 1, 0, rate)
    }

    fun click() { val now = System.currentTimeMillis(); if (now - lastClick > 60) { lastClick = now; play("click", 0.5f) } }

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
    fun onBat(kind: String) { when (kind) { "SIX", "FOUR" -> play("batbig"); "RUN", "NB" -> play("bat", 0.8f); "DOT" -> play("bat", 0.45f, 1.1f) } }
    fun onOutcome(kind: String, how: String, milestone: Boolean) {
        when (kind) {
            "SIX" -> play("roar")
            "FOUR" -> play("cheer", 0.8f)
            "WICKET" -> { if (how.startsWith("b ") || how.startsWith("lbw")) play("stumps"); play("wicket") }
            "APPEAL" -> play("groan", 0.7f)
        }
        if (milestone) play("applause")
    }

    fun setScene(sc: Scene) {
        scene = sc
        refresh()
    }

    fun refresh() {
        val s = settings ?: return
        val c = ctx ?: return
        val wantMusic = !paused && channel("music") > 0.01f && scene == Scene.MENU
        val wantAmb = !paused && channel("ambience") > 0.01f && scene == Scene.MATCH
        if (wantMusic) {
            if (music == null) music = MediaPlayer.create(c, R.raw.music_theme)?.apply { isLooping = true }
            music?.setVolume(channel("music") * 0.9f, channel("music") * 0.9f)
            if (music?.isPlaying == false) music?.start()
        } else music?.let { if (it.isPlaying) it.pause() }
        if (wantAmb) {
            if (ambience == null) ambience = MediaPlayer.create(c, R.raw.amb_crowd)?.apply { isLooping = true }
            ambience?.setVolume(channel("ambience") * 0.5f, channel("ambience") * 0.5f)
            if (ambience?.isPlaying == false) ambience?.start()
        } else ambience?.let { if (it.isPlaying) it.pause() }
    }

    fun onPause() { paused = true; tts?.stop(); refresh() }
    fun onResume() { paused = false; refresh() }
}
