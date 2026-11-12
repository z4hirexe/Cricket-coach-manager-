package com.cricketcoach.manager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.cricketcoach.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Social photo posts (v2.6): seven original moment photos (century, fifty, hat-trick, five-for, team win, trophy,
 * auction signing). Faces are never shown, and the kit is a neutral grey layer with a separate alpha mask, so every
 * photo is recoloured to the poster's team colour at runtime and matches any player in any team.
 */
object PhotoArt {
    val KEYS = listOf("century", "fifty", "hattrick", "fivefor", "win", "trophy", "signing")

    fun base(key: String): Int? = when (key) {
        "century" -> R.drawable.ph_century; "fifty" -> R.drawable.ph_fifty; "hattrick" -> R.drawable.ph_hattrick; "fivefor" -> R.drawable.ph_fivefor
        "win" -> R.drawable.ph_win; "trophy" -> R.drawable.ph_trophy; "signing" -> R.drawable.ph_signing; else -> null
    }
    fun mask(key: String): Int? = when (key) {
        "century" -> R.drawable.ph_century_kit; "fifty" -> R.drawable.ph_fifty_kit; "hattrick" -> R.drawable.ph_hattrick_kit; "fivefor" -> R.drawable.ph_fivefor_kit
        "win" -> R.drawable.ph_win_kit; "trophy" -> R.drawable.ph_trophy_kit; "signing" -> R.drawable.ph_signing_kit; else -> null
    }

    /**
     * Multiply-tint (pure, unit-tested): inside the mask the grey kit pixel is multiplied by the team colour, blended by
     * the mask alpha; outside the mask the photo is untouched. Very dark team colours are lifted a little so the kit
     * folds stay visible.
     */
    fun tint(base: IntArray, mask: IntArray, color: Int): IntArray {
        var tr = (color shr 16) and 0xFF; var tg = (color shr 8) and 0xFF; var tb = color and 0xFF
        val lum = (tr * 299 + tg * 587 + tb * 114) / 1000
        if (lum < 60) { val k = 60 - lum; tr = minOf(255, tr + k); tg = minOf(255, tg + k); tb = minOf(255, tb + k) }
        val out = IntArray(base.size)
        for (i in base.indices) {
            val p = base[i]; val a = (mask[i] ushr 24) and 0xFF
            if (a == 0) { out[i] = p; continue }
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val mr = r * tr / 255; val mg = g * tg / 255; val mb = b * tb / 255
            val nr = (mr * a + r * (255 - a)) / 255; val ng = (mg * a + g * (255 - a)) / 255; val nb = (mb * a + b * (255 - a)) / 255
            out[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return out
    }

    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 10
    }

    /** Tinted photo for [key] in [color] (ARGB). Cached; decodes off the main thread. */
    suspend fun load(ctx: Context, key: String, color: Int): ImageBitmap? {
        val ck = "$key:$color"
        synchronized(cache) { cache[ck] }?.let { return it }
        val b = base(key) ?: return null; val m = mask(key) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
                val bb = BitmapFactory.decodeResource(ctx.resources, b, opts) ?: return@runCatching null
                val mb = BitmapFactory.decodeResource(ctx.resources, m, opts) ?: return@runCatching null
                val w = bb.width; val h = bb.height
                val mm = if (mb.width != w || mb.height != h) Bitmap.createScaledBitmap(mb, w, h, true) else mb
                val px = IntArray(w * h); bb.getPixels(px, 0, w, 0, 0, w, h)
                val mp = IntArray(w * h); mm.getPixels(mp, 0, w, 0, 0, w, h)
                val out = Bitmap.createBitmap(tint(px, mp, color), w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                synchronized(cache) { cache[ck] = out }
                out
            }.getOrNull()
        }
    }

    fun cached(key: String, color: Int): ImageBitmap? = synchronized(cache) { cache["$key:$color"] }
}

/** Square photo moment recoloured to [teamColor]; shows the untinted photo until the tinted one is ready. */
@Composable
fun PhotoMoment(key: String, teamColor: Long, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val ctx = LocalContext.current
    val col = teamColor.toInt()
    var img by remember(key, col) { mutableStateOf(PhotoArt.cached(key, col)) }
    LaunchedEffect(key, col) { if (img == null) img = PhotoArt.load(ctx, key, col) }
    Box(modifier) {
        val b = img
        if (b != null) Image(b, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else PhotoArt.base(key)?.let { Image(painterResource(it), contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
