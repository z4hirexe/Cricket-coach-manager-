package com.cricketcoach.manager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Real player photos from Wikimedia Commons (free licences; file names come from Wikidata P18 in the bundled dataset).
 * Downloaded once when online, face-detected and cropped to a square portrait, then cached on device for offline play.
 */
object Faces {
    private var dir: File? = null
    var enabled = true
    private val mem = LruCache<String, ImageBitmap>(160)
    private val failed = HashSet<String>()
    private val gate = Semaphore(3)

    fun init(ctx: Context) { dir = File(ctx.filesDir, "faces").apply { mkdirs() } }

    fun fileFor(pid: String): String? = Repo.byId[pid]?.img

    fun cached(pid: String): ImageBitmap? = mem.get(pid)

    private fun key(name: String): String = MessageDigest.getInstance("MD5").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(pid: String): ImageBitmap? {
        mem.get(pid)?.let { return it }
        val file = fileFor(pid) ?: return null
        val d = dir ?: return null
        val f = File(d, key(file) + ".jpg")
        return withContext(Dispatchers.IO) {
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { mem.put(pid, it); return@withContext it }
            if (!enabled || file in failed) return@withContext null
            gate.withPermit {
                try {
                    val url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=360"
                    var conn = URL(url).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "CricketCoachManager/1.0 (Android game; offline player photos)")
                    conn.connectTimeout = 8000; conn.readTimeout = 12000; conn.instanceFollowRedirects = true
                    var hops = 0
                    while (conn.responseCode in 301..308 && hops < 5) {
                        val loc = conn.getHeaderField("Location"); conn.disconnect()
                        conn = URL(URL(url), loc).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "CricketCoachManager/1.0 (Android game; offline player photos)"); hops++
                    }
                    if (conn.responseCode != 200) { failed.add(file); return@withPermit null }
                    val bytes = conn.inputStream.use { it.readBytes() }; conn.disconnect()
                    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run { failed.add(file); return@withPermit null }
                    val out = cropFace(src)
                    f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    out.asImageBitmap().also { mem.put(pid, it) }
                } catch (e: Exception) { null }
            }
        }
    }

    /** Uses Android's built-in face detector to centre the crop on the face (group/ceremony photos otherwise look wrong). */
    private fun cropFace(src: Bitmap): Bitmap {
        var w = src.width; if (w % 2 == 1) w -= 1
        val h = src.height
        val rgb = src.copy(Bitmap.Config.RGB_565, false).let { if (it.width != w) Bitmap.createBitmap(it, 0, 0, w, h) else it }
        val faces = arrayOfNulls<FaceDetector.Face>(4)
        val n = try { FaceDetector(w, h, 4).findFaces(rgb, faces) } catch (_: Exception) { 0 }
        val side: Int; val cx: Float; val cy: Float
        if (n > 0) {
            val best = faces.filterNotNull().maxByOrNull { it.eyesDistance() }!!
            val p = PointF(); best.getMidPoint(p)
            side = (best.eyesDistance() * 3.4f).toInt().coerceIn(40, minOf(w, h))
            cx = p.x; cy = p.y + best.eyesDistance() * 0.35f
        } else {
            side = minOf(w, h, (maxOf(w, h) * 0.55f).toInt()).coerceAtLeast(1)
            cx = w / 2f; cy = side / 2f + h * 0.05f
        }
        val x = (cx - side / 2f).toInt().coerceIn(0, src.width - side)
        val y = (cy - side / 2f).toInt().coerceIn(0, src.height - side)
        val crop = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(crop, 192, 192, true)
    }
}

/** Circular player portrait: real photo if available (cached/online), otherwise a styled initials avatar. */
@Composable
fun PlayerFace(pid: String, name: String, size: Dp = 42.dp, ring: Color = C.Gold) {
    val hasImg = remember(pid) { Faces.fileFor(pid) != null }
    var img by remember(pid) { mutableStateOf(Faces.cached(pid)) }
    if (hasImg && img == null) LaunchedEffect(pid) { img = Faces.load(pid) }
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(C.Grass, C.CardHi))).border(maxOf(1.dp, size / 28), ring.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        val b = img
        if (b != null) Image(b, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Text(name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), color = C.Gold, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}
