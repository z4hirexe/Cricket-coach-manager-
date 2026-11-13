package com.cricketcoach.manager.save

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cricketcoach.manager.game.CareerSave
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SaveMeta(val slot: Int, val manager: String, val club: String, val season: Int, val division: Int, val updated: Long, val mode: String = "CLUB")

/**
 * Offline save system: SQLite index + gzipped JSON career files (atomic writes).
 * Everything stays on this device; there is no account, login or cloud backup.
 */
class SaveManager(private val ctx: Context) : SQLiteOpenHelper(ctx, "ccm_saves.db", null, 2) {
    private val gson: Gson = GsonBuilder().serializeSpecialFloatingPointValues().create()
    private val dir = File(ctx.filesDir, "saves").apply { mkdirs() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE saves(slot INTEGER PRIMARY KEY, manager TEXT, club TEXT, season INTEGER, division INTEGER, updated INTEGER, file TEXT, dirty INTEGER DEFAULT 1, mode TEXT DEFAULT 'CLUB')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE saves ADD COLUMN mode TEXT DEFAULT 'CLUB'")
    }

    private fun fileFor(slot: Int) = File(dir, "career_slot_$slot.json.gz")

    fun gzip(s: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter().use { it.write(s) }
        return bos.toByteArray()
    }

    fun gunzip(b: ByteArray): String = GZIPInputStream(b.inputStream()).bufferedReader().use { it.readText() }

    fun toJson(s: CareerSave): String = gson.toJson(s)

    fun metaOf(s: CareerSave): SaveMeta {
        val u = s.clubs[s.userClubId]
        val name = if (s.isMultiTeam) (u?.name ?: "") + " +${s.managedTeamIds.size - 1} teams" else u?.name ?: ""
        return SaveMeta(s.slot, s.manager, name, s.season, u?.division ?: 4, System.currentTimeMillis(), s.mode)
    }

    fun save(s: CareerSave) = write(metaOf(s), toJson(s))

    /** Disk write (safe to call from a background thread with a pre-serialised JSON string). */
    @Synchronized
    fun write(m: SaveMeta, json: String) {
        val f = fileFor(m.slot)
        val tmp = File(dir, "career_slot_${m.slot}.tmp")
        tmp.writeBytes(gzip(json))
        if (f.exists()) f.delete()
        tmp.renameTo(f)
        val cv = ContentValues().apply {
            put("slot", m.slot); put("manager", m.manager); put("club", m.club); put("season", m.season)
            put("division", m.division); put("updated", m.updated); put("file", f.name); put("dirty", 1); put("mode", m.mode)
        }
        writableDatabase.insertWithOnConflict("saves", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun load(slot: Int): CareerSave? {
        val f = fileFor(slot)
        if (!f.exists()) return null
        return try { com.cricketcoach.manager.game.SaveMigration.migrate(gson.fromJson(gunzip(f.readBytes()), CareerSave::class.java)) } catch (e: Exception) { null }
    }

    fun rawJson(slot: Int): String? = fileFor(slot).takeIf { it.exists() }?.let { gunzip(it.readBytes()) }

    fun importJson(slot: Int, json: String): CareerSave? = try {
        val s = com.cricketcoach.manager.game.SaveMigration.migrate(gson.fromJson(json, CareerSave::class.java)); s.slot = slot; save(s); s
    } catch (e: Exception) { null }

    fun list(): List<SaveMeta> {
        val out = mutableListOf<SaveMeta>()
        readableDatabase.rawQuery("SELECT slot, manager, club, season, division, updated, mode FROM saves ORDER BY slot", null).use { c ->
            while (c.moveToNext()) if (fileFor(c.getInt(0)).exists()) out.add(SaveMeta(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getLong(5), c.getString(6) ?: "CLUB"))
        }
        return out
    }

    fun delete(slot: Int) { fileFor(slot).delete(); writableDatabase.delete("saves", "slot=?", arrayOf(slot.toString())) }

}

/** Device-local game settings (shared preferences). */
class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ccm_settings", Context.MODE_PRIVATE)
    var simSpeedMs: Int get() = sp.getInt("speed", 350); set(v) = sp.edit().putInt("speed", v).apply()
    var economy: Float get() = sp.getFloat("economy", 1f); set(v) = sp.edit().putFloat("economy", v).apply()
    var difficulty: Int get() = sp.getInt("difficulty", 1); set(v) = sp.edit().putInt("difficulty", v).apply()
    var autosave: Boolean get() = sp.getBoolean("autosave", true); set(v) = sp.edit().putBoolean("autosave", v).apply()
    var lastSlot: Int get() = sp.getInt("lastSlot", 0); set(v) = sp.edit().putInt("lastSlot", v).apply()
    // legacy switches (kept for old saves / tests) - the mixer below is what the game uses now
    var sfx: Boolean get() = sp.getBoolean("sfx", true); set(v) = sp.edit().putBoolean("sfx", v).apply()
    var music: Boolean get() = sp.getBoolean("music", true); set(v) = sp.edit().putBoolean("music", v).apply()
    var crowd: Boolean get() = sp.getBoolean("crowd", true); set(v) = sp.edit().putBoolean("crowd", v).apply()
    var voice: Boolean get() = sp.getBoolean("voice", true); set(v) = sp.edit().putBoolean("voice", v).apply()
    var voiceSpeed: Float get() = sp.getFloat("voiceSpeed", 1f); set(v) = sp.edit().putFloat("voiceSpeed", v.coerceIn(0.65f, 1.6f)).apply()
    /** Master volume 0..1 */
    var volume: Float get() = sp.getFloat("volume", 0.8f); set(v) = sp.edit().putFloat("volume", v).apply()
    var volCommentary: Float get() = sp.getFloat("volCom", 0.9f); set(v) = sp.edit().putFloat("volCom", v).apply()
    var volCrowd: Float get() = sp.getFloat("volCrowd", 0.7f); set(v) = sp.edit().putFloat("volCrowd", v).apply()
    var volBat: Float get() = sp.getFloat("volBat", 0.9f); set(v) = sp.edit().putFloat("volBat", v).apply()
    var volMusic: Float get() = sp.getFloat("volMusic", 0.5f); set(v) = sp.edit().putFloat("volMusic", v).apply()
    /** Stadium ambience layers (crowd bed / chant / tension) and the auction-room murmur. */
    var volAmbience: Float get() = sp.getFloat("volAmb", 0.7f); set(v) = sp.edit().putFloat("volAmb", v).apply()
    /** Menus, buttons, auction gavel & paddles, coins. */
    var volUi: Float get() = sp.getFloat("volUi", 0.8f); set(v) = sp.edit().putFloat("volUi", v).apply()
    /** Dynamic crowd: chants after boundaries, nervous hush in tight chases, dhol & bugle stings. */
    var crowdReact: Boolean get() = sp.getBoolean("crowdReact", true); set(v) = sp.edit().putBoolean("crowdReact", v).apply()
    var mute: Boolean get() = sp.getBoolean("mute", false); set(v) = sp.edit().putBoolean("mute", v).apply()
    // v2.6 per-channel mutes (Audio settings). Music is controlled ONLY from Settings > Audio.
    var muteCom: Boolean get() = sp.getBoolean("muteCom", false); set(v) = sp.edit().putBoolean("muteCom", v).apply()
    var muteCrowd: Boolean get() = sp.getBoolean("muteCrowd", false); set(v) = sp.edit().putBoolean("muteCrowd", v).apply()
    var muteBat: Boolean get() = sp.getBoolean("muteBat", false); set(v) = sp.edit().putBoolean("muteBat", v).apply()
    var muteMusic: Boolean get() = sp.getBoolean("muteMusic", false); set(v) = sp.edit().putBoolean("muteMusic", v).apply()
    var muteUi: Boolean get() = sp.getBoolean("muteUi", false); set(v) = sp.edit().putBoolean("muteUi", v).apply()
    // v2.6 display / match / notification settings (all wired to real behaviour)
    /** Hide the Android status + navigation bars (swipe to reveal). */
    var immersive: Boolean get() = sp.getBoolean("immersive", true); set(v) = sp.edit().putBoolean("immersive", v).apply()
    /** Keep the screen awake while a match is on screen. */
    var keepScreenOn: Boolean get() = sp.getBoolean("keepOn", true); set(v) = sp.edit().putBoolean("keepOn", v).apply()
    /** Interface text size: 0 compact (90%), 1 standard, 2 large (112%). */
    var uiScale: Int get() = sp.getInt("uiScale", 1); set(v) = sp.edit().putInt("uiScale", v.coerceIn(0, 2)).apply()
    /** Auto-play stops after every wicket so you can change tactics. */
    var autoPauseWicket: Boolean get() = sp.getBoolean("autoPauseWk", false); set(v) = sp.edit().putBoolean("autoPauseWk", v).apply()
    /** Pop-up toast with your result / important news after simulated days. */
    var notifyResults: Boolean get() = sp.getBoolean("notifyRes", true); set(v) = sp.edit().putBoolean("notifyRes", v).apply()
    /** Unread badges for Inbox / Yorker in the sidebar. */
    var notifySocial: Boolean get() = sp.getBoolean("notifySocial", true); set(v) = sp.edit().putBoolean("notifySocial", v).apply()
    /** Date format: "IN" = 12 Nov 2026, "US" = Nov 12, 2026. */
    var dateStyle: String get() = sp.getString("dateStyle", "IN") ?: "IN"; set(v) = sp.edit().putString("dateStyle", v).apply()
    /** Commentary language: "EN", "HI" (Hindi) or "MIX" (alternating); style: 0 Classic, 1 Excited, 2 Analytical */
    var comLang: String get() = sp.getString("comLang", "EN") ?: "EN"; set(v) = sp.edit().putString("comLang", v).apply()
    var comStyle: Int get() = sp.getInt("comStyle", 0); set(v) = sp.edit().putInt("comStyle", v).apply()
    /** Second voice in the booth (expert analysis after wickets, milestones, every few overs). */
    var comExpert: Boolean get() = sp.getBoolean("comExpert", true); set(v) = sp.edit().putBoolean("comExpert", v).apply()
    /** End-of-over scoreboard cards in the commentary feed. */
    var comOverCards: Boolean get() = sp.getBoolean("comOverCards", true); set(v) = sp.edit().putBoolean("comOverCards", v).apply()
    var view3d: Boolean get() = sp.getBoolean("view3d", true); set(v) = sp.edit().putBoolean("view3d", v).apply()
    var camMode: Int get() = sp.getInt("cam", 0); set(v) = sp.edit().putInt("cam", v).apply()
    /** live-match playback speed 1/2/4/6/8x - always free (3D animation and simulation both follow it) */
    var playSpeed: Int get() = sp.getInt("playSpeed", 1); set(v) = sp.edit().putInt("playSpeed", v).apply()
    /** 0 = Low (lightweight 2.5D canvas), 1 = Medium (Filament, dynamic resolution), 2 = High (Filament, MSAA, SSAO, 2k shadows, bloom). */
    var gfx: Int get() = sp.getInt("gfx", 1); set(v) = sp.edit().putInt("gfx", v).apply()
    var photos: Boolean get() = sp.getBoolean("photos", true); set(v) = sp.edit().putBoolean("photos", v).apply()
    /** Local reward counters. */
    var coins: Int get() = sp.getInt("coins", 1000); set(v) = sp.edit().putInt("coins", v.coerceIn(0, 100_000_000)).apply()
    var xp: Int get() = sp.getInt("xp", 0); set(v) = sp.edit().putInt("xp", v.coerceAtLeast(0)).apply()

    private val settingKeys = listOf("speed", "economy", "difficulty", "autosave", "volume", "volCom", "volCrowd", "volBat", "volMusic", "volAmb", "volUi", "crowdReact", "mute",
        "comLang", "comStyle", "comExpert", "comOverCards", "view3d", "cam", "gfx", "photos",
        "muteCom", "muteCrowd", "muteBat", "muteMusic", "muteUi", "immersive", "keepOn", "uiScale", "autoPauseWk", "notifyRes", "notifySocial", "dateStyle", "playSpeed")

    /** Serialisable settings (used for local export / tests). */
    fun toJson(): JsonObject {
        val o = JsonObject()
        for (k in settingKeys) when (val v = sp.all[k]) {
            is Boolean -> o.addProperty(k, v); is Int -> o.addProperty(k, v); is Float -> o.addProperty(k, v); is String -> o.addProperty(k, v)
        }
        return o
    }

    fun applyJson(o: JsonObject?) {
        if (o == null) return
        val e = sp.edit()
        for (k in settingKeys) {
            val v = o[k] ?: continue
            if (!v.isJsonPrimitive) continue
            val p = v.asJsonPrimitive
            when {
                p.isBoolean -> e.putBoolean(k, p.asBoolean)
                p.isString -> e.putString(k, p.asString)
                k in setOf("economy", "volume", "volCom", "volCrowd", "volBat", "volMusic", "volAmb", "volUi") -> e.putFloat(k, p.asFloat)
                else -> e.putInt(k, p.asInt)
            }
        }
        e.apply()
    }
}
