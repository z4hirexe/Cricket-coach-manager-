package com.cricketcoach.manager.render3d

import android.content.Context
import android.view.Surface
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Graphics quality presets. LOW uses the lightweight Canvas renderer (ui/Stadium3D.kt); MEDIUM/HIGH use Filament. */
object Gfx { const val LOW = 0; const val MEDIUM = 1; const val HIGH = 2 }

/** Lighting presets: day (Test), day-night (ODI), night under floodlights (T20). */
object Lighting { const val DAY = 0; const val DUSK = 1; const val NIGHT = 2; const val OVERCAST = 3 }

/** Deterministic, generic appearance for a player id (no real-person likeness in 3D - real faces only via credited photos). */
class Look(val skin: FloatArray, val hair: Int, val hairCol: FloatArray, val beard: Boolean, val heavy: Boolean) {
    companion object {
        private val SKINS = arrayOf(floatArrayOf(0.62f, 0.43f, 0.31f), floatArrayOf(0.48f, 0.31f, 0.21f), floatArrayOf(0.36f, 0.23f, 0.15f),
            floatArrayOf(0.25f, 0.16f, 0.11f), floatArrayOf(0.78f, 0.60f, 0.48f), floatArrayOf(0.70f, 0.52f, 0.40f))
        private val HAIRS = arrayOf(floatArrayOf(0.03f, 0.025f, 0.02f), floatArrayOf(0.08f, 0.05f, 0.03f), floatArrayOf(0.20f, 0.13f, 0.07f), floatArrayOf(0.45f, 0.33f, 0.18f))
        fun of(id: String, country: String = ""): Look {
            val h = id.hashCode().toLong() and 0xffffffffL
            val c = country.lowercase()
            // broad regional skin-tone ranges keep crowds of generic models plausible; individual pick is hashed
            val pool = when {
                c in listOf("india", "pakistan", "sri lanka", "bangladesh", "afghanistan", "nepal", "uae", "oman") -> intArrayOf(0, 1, 2, 5)
                c in listOf("west indies", "zimbabwe") -> intArrayOf(2, 3, 1)
                c in listOf("england", "australia", "new zealand", "ireland", "scotland", "netherlands") -> intArrayOf(4, 5, 0, 4)
                c == "south africa" -> intArrayOf(4, 2, 3, 5, 0)
                else -> intArrayOf(0, 1, 2, 3, 4, 5)
            }
            val skin = SKINS[pool[(h % pool.size).toInt()]]
            val hairCol = if (skin === SKINS[4] || skin === SKINS[5]) HAIRS[((h shr 5) % 4).toInt()] else HAIRS[((h shr 5) % 2).toInt()]
            return Look(skin, ((h shr 9) % 3).toInt(), hairCol, (h shr 12) % 3 == 0L, (h shr 15) % 4 == 0L)
        }
    }
}

/** Kit colours for a side. */
class Kit(val primary: Int, val secondary: Int, val whites: Boolean)

private fun lin(c: Int, i: Int): Float { val v = ((c shr i) and 0xff) / 255f; return v.pow(2.2f) }
private fun MaterialInstance.color(r: Float, g: Float, b: Float) {
    if (material.hasParameter("baseColorFactor")) setParameter("baseColorFactor", Colors.RgbaType.LINEAR, r, g, b, 1f)
}
private fun MaterialInstance.colorInt(c: Int, k: Float = 1f) = color(lin(c, 16) * k, lin(c, 8) * k, lin(c, 0) * k)

/** One animated cricketer (a gltfio instance of cricketer.glb). */
class Actor(private val arena: Arena3D, val inst: FilamentInstance) {
    val parts = HashMap<String, Int>()
    private val visible = HashSet<String>()
    private val mats = HashMap<String, MaterialInstance>()
    private val animator: Animator = inst.animator
    var x = 0f; var y = 0f; var z = 0f; var yaw = 0f; var mirror = false
    private var clip = -1; private var t = 0f; private var loop = true; private var speed = 1f
    private var prev = -1; private var prevT = 0f; private var fade = 1f; private var fadeDur = 0.2f
    var active = false

    init {
        val asset = inst.asset
        for (e in inst.entities) asset.getName(e)?.let { parts[it] = e }
        for (mi in inst.materialInstances) mi.name?.let { mats[it] = mi }
    }

    fun show(names: Collection<String>) {
        val want = names.toHashSet()
        for ((n, e) in parts) {
            val isMesh = n.startsWith("body_") || n in MESHES
            if (!isMesh) continue
            val on = active && n in want
            if (on && n !in visible) { arena.scene.addEntity(e); visible.add(n) }
            if (!on && n in visible) { arena.scene.removeEntity(e); visible.remove(n) }
        }
    }

    fun dress(look: Look, kit: Kit?, role: Role, lod: Int) {
        val body = "body_${if (look.heavy) "heavy" else "athletic"}_lod$lod"
        val list = ArrayList<String>(); list += body; list += "eyes"
        val batting = role == Role.STRIKER || role == Role.NONSTRIKER
        when (role) {
            Role.STRIKER, Role.NONSTRIKER -> { list += listOf("helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "bat") }
            Role.KEEPER -> { list += listOf("pad_L", "pad_R", "glove_L", "glove_R", "cap") }
            Role.UMPIRE -> list += "cap"
            Role.BOWLER, Role.FIELDER, Role.STAR -> if (look.hair == 0 && role == Role.FIELDER) list += "cap" else list += if (look.hair == 2) "hair_long" else "hair_short"
        }
        if (!batting && look.beard) list += "beard"
        if (batting && look.beard) list += "beard"
        show(list)
        mats["skin"]?.color(look.skin[0], look.skin[1], look.skin[2])
        mats["hair"]?.color(look.hairCol[0], look.hairCol[1], look.hairCol[2])
        if (role == Role.UMPIRE) {
            mats["jersey"]?.color(0.55f, 0.72f, 0.88f); mats["trim"]?.color(0.05f, 0.05f, 0.08f)
            mats["trousers"]?.color(0.03f, 0.03f, 0.04f); mats["cap"]?.color(0.04f, 0.04f, 0.05f); mats["shoe"]?.color(0.05f, 0.05f, 0.05f); return
        }
        val k = kit ?: return
        if (k.whites) {
            mats["jersey"]?.color(0.86f, 0.84f, 0.76f); mats["trousers"]?.color(0.84f, 0.82f, 0.74f); mats["trim"]?.colorInt(k.primary)
            mats["cap"]?.colorInt(k.primary, 0.6f); mats["helmet"]?.colorInt(k.primary, 0.45f)
        } else {
            mats["jersey"]?.colorInt(k.primary); mats["trim"]?.colorInt(k.secondary); mats["trousers"]?.colorInt(k.primary, 0.42f)
            mats["cap"]?.colorInt(k.primary); mats["helmet"]?.colorInt(k.primary, 0.5f)
        }
        mats["shoe"]?.color(0.85f, 0.85f, 0.85f)
    }

    fun play(name: String, loop: Boolean = true, fade: Float = 0.2f, speed: Float = 1f, from: Float = 0f) {
        val idx = arena.clipIndex[name] ?: return
        if (idx == clip && this.loop == loop) { this.speed = speed; return }
        prev = clip; prevT = t; clip = idx; t = from; this.loop = loop; this.speed = speed; this.fade = 0f; fadeDur = max(fade, 0.001f)
    }
    val clipTime get() = t

    fun update(dt: Float) {
        if (!active || clip < 0) return
        val d = arena.clipDur[clip]
        t += dt * speed
        t = if (loop) (if (d > 0) t % d else 0f) else min(t, d)
        fade = min(1f, fade + dt / fadeDur)
        animator.applyAnimation(clip, t)
        if (prev >= 0 && fade < 1f) animator.applyCrossFade(prev, prevT, 1f - fade)
        animator.updateBoneMatrices()
        arena.setTransform(inst.root, x, y, z, yaw, if (mirror) -1f else 1f)
    }

    companion object {
        val MESHES = setOf("helmet", "grille", "cap", "hair_short", "hair_long", "beard", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
    }
}

enum class Role { STRIKER, NONSTRIKER, BOWLER, KEEPER, FIELDER, UMPIRE, STAR }

/**
 * Filament scene: engine, swap chain, stadium + instanced cricketers, IBL, sun/floodlight, quality presets.
 * Must be used on the main thread.
 */
class Arena3D(private val ctx: Context, var quality: Int) {
    val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val camEntity = EntityManager.get().create()
    val camera: Camera = engine.createCamera(camEntity)
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private val stadium: FilamentAsset
    private val people: FilamentAsset
    val actors: List<Actor>
    val clipIndex = HashMap<String, Int>()
    lateinit var clipDur: FloatArray
    private val sun = EntityManager.get().create()
    private var ibl: IndirectLight? = null
    private val tm = engine.transformManager
    private val mat16 = FloatArray(16)
    private var aspect = 16.0 / 9.0
    private var fov = 40.0
    var lighting = -1; private set
    private val stadiumMats = HashMap<String, MaterialInstance>()
    val ball: Int; val stumps = HashMap<String, Int>()
    var onFirstFrame: (() -> Unit)? = null
    private var frames = 0

    init {
        view.scene = scene; view.camera = camera
        camera.setExposure(1.0f)
        stadium = load("models/stadium.glb")
        resources.loadResources(stadium); stadium.releaseSourceData()
        scene.addEntities(stadium.entities)
        for (mi in stadium.instance.materialInstances) mi.name?.let { stadiumMats[it] = mi }
        for (n in listOf("sky_day", "sky_dusk", "sky_night")) stadium.getFirstEntityByName(n).let { e ->
            val rm = engine.renderableManager; if (rm.hasComponent(e)) { val ri = rm.getInstance(e); rm.setCastShadows(ri, false); rm.setReceiveShadows(ri, false); rm.setPriority(ri, 0) }
        }
        // flat ground never casts (avoids acne / wasted shadow-map space); it only receives
        for (n in FLAT) { val e = stadium.getFirstEntityByName(n); val rm = engine.renderableManager
            if (e != 0 && rm.hasComponent(e)) { val ri = rm.getInstance(e); rm.setCastShadows(ri, false); rm.setReceiveShadows(ri, true) } }
        ball = stadium.getFirstEntityByName("ball")
        for (end in listOf("bat", "bowl")) { for (i in 0..2) stumps["stump_${end}_$i"] = stadium.getFirstEntityByName("stump_${end}_$i"); for (i in 0..1) stumps["bail_${end}_$i"] = stadium.getFirstEntityByName("bail_${end}_$i") }
        val n = 26
        val insts = arrayOfNulls<FilamentInstance>(n)
        people = loader.createInstancedAsset(readAsset("models/cricketer.glb"), insts) ?: error("cricketer.glb failed")
        resources.loadResources(people); people.releaseSourceData()
        // add skeleton (non-renderable) entities; meshes are added on demand by Actor.show
        actors = insts.map { Actor(this, it!!) }
        val a0 = actors[0].inst.animator
        clipDur = FloatArray(a0.animationCount) { a0.getAnimationDuration(it) }
        for (i in 0 until a0.animationCount) clipIndex[a0.getAnimationName(i)] = i
        for (a in actors) for ((name, e) in a.parts) if (!(name.startsWith("body_") || name in Actor.MESHES)) scene.addEntity(e)
        applyQuality(quality)
        setLighting(Lighting.NIGHT)
    }

    private fun readAsset(path: String): ByteBuffer {
        val bytes = ctx.assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
    }
    private fun load(path: String): FilamentAsset = loader.createAsset(readAsset(path)) ?: error("$path failed")

    fun attach(sv: android.view.SurfaceView) {
        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, ui.swapChainFlags)
            }
            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }; swapChain = null
            }
            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height); aspect = width.toDouble() / max(1, height)
                camera.setProjection(fov, aspect, 0.1, 900.0, Camera.Fov.VERTICAL)
            }
        }
        ui.attachTo(sv)
    }

    fun applyQuality(q: Int) {
        quality = q
        val high = q >= Gfx.HIGH
        view.antiAliasing = View.AntiAliasing.FXAA
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = high; sampleCount = 4 }
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply { enabled = !high; minScale = 0.6f; maxScale = 1f; homogeneousScaling = true }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = high; radius = 0.4f; intensity = 1.0f }
        view.setShadowingEnabled(true)
        view.bloomOptions = view.bloomOptions.apply { enabled = high && lighting != Lighting.DAY; strength = 0.12f; levels = 6; threshold = true }
        // big structures cast shadows across the outfield on MEDIUM/HIGH; LOW keeps only players/stumps casting and drops the city/trees
        val rm = engine.renderableManager
        for (n in BIG_CASTERS) { val e = stadium.getFirstEntityByName(n); if (e != 0 && rm.hasComponent(e)) rm.setCastShadows(rm.getInstance(e), q >= Gfx.MEDIUM) }
        stadium.getFirstEntityByName("surroundings").let { e -> if (e != 0) { if (q >= Gfx.MEDIUM) scene.addEntity(e) else scene.removeEntity(e) } }
        rebuildSun()
        applyFog()
    }

    private fun applyFog() {
        val fo = view.fogOptions
        fo.enabled = quality >= Gfx.MEDIUM
        fo.distance = 80f; fo.heightFalloff = 0.004f; fo.height = 0f; fo.cutOffDistance = 2000f
        when (lighting) {
            Lighting.DUSK -> { fo.density = 0.0012f; fo.maximumOpacity = 0.35f; fo.color = floatArrayOf(0.55f, 0.40f, 0.38f) }
            Lighting.NIGHT -> { fo.density = 0.0015f; fo.maximumOpacity = 0.40f; fo.color = floatArrayOf(0.03f, 0.04f, 0.07f) }
            Lighting.OVERCAST -> { fo.density = 0.0018f; fo.maximumOpacity = 0.45f; fo.color = floatArrayOf(0.62f, 0.65f, 0.70f) }
            else -> { fo.density = 0.0011f; fo.maximumOpacity = 0.32f; fo.color = floatArrayOf(0.62f, 0.72f, 0.85f) }
        }
        view.fogOptions = fo
    }

    private fun rebuildSun() {
        engine.lightManager.destroy(sun)
        val so = LightManager.ShadowOptions().apply {
            mapSize = if (quality >= Gfx.HIGH) 2048 else 1024; shadowCascades = when (quality) { Gfx.HIGH -> 3; Gfx.MEDIUM -> 2; else -> 1 }
            shadowFar = when (quality) { Gfx.HIGH -> 220f; Gfx.MEDIUM -> 140f; else -> 70f }; stable = false; constantBias = 0.002f; normalBias = 1.2f
        }
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f, 1f, 1f).intensity(3f).direction(0.3f, -0.8f, 0.5f)
            .castShadows(true).shadowOptions(so).build(engine, sun)
        scene.addEntity(sun)
        if (lighting >= 0) { val l = lighting; lighting = -1; setLighting(l) }
    }

    /** Day / dusk / night / overcast: sky dome, sun or floodlight key light, ambient SH, emissive lamps, bloom. */
    fun setLighting(mode: Int) {
        if (mode == lighting) return
        lighting = mode
        val sky = when (mode) { Lighting.DAY, Lighting.OVERCAST -> "sky_day"; Lighting.DUSK -> "sky_dusk"; else -> "sky_night" }
        for (n in listOf("sky_day", "sky_dusk", "sky_night")) { val e = stadium.getFirstEntityByName(n); if (n == sky) scene.addEntity(e) else scene.removeEntity(e) }
        val lm = engine.lightManager; val li = lm.getInstance(sun)
        // sky/ground SH (band 0 = average, band 1 y = sky minus ground), pre-scaled for Filament's irradiance evaluation
        val (skyC, gndC, key, keyCol, dir, lamp) = when (mode) {
            Lighting.DAY -> L6(floatArrayOf(0.55f, 0.66f, 0.85f), floatArrayOf(0.22f, 0.28f, 0.16f), 3.2f, floatArrayOf(1f, 0.96f, 0.88f), floatArrayOf(0.62f, -0.58f, 0.52f), 0f)
            Lighting.OVERCAST -> L6(floatArrayOf(0.55f, 0.58f, 0.62f), floatArrayOf(0.20f, 0.24f, 0.17f), 1.1f, floatArrayOf(0.9f, 0.92f, 0.95f), floatArrayOf(0.2f, -0.95f, 0.25f), 0f)
            Lighting.DUSK -> L6(floatArrayOf(0.40f, 0.36f, 0.45f), floatArrayOf(0.20f, 0.17f, 0.12f), 2.2f, floatArrayOf(1f, 0.70f, 0.45f), floatArrayOf(-0.75f, -0.35f, 0.55f), 1.5f)
            else -> L6(floatArrayOf(0.16f, 0.18f, 0.24f), floatArrayOf(0.10f, 0.13f, 0.09f), 2.6f, floatArrayOf(0.95f, 0.97f, 1f), floatArrayOf(0.15f, -0.9f, 0.4f), 6f)
        }
        lm.setColor(li, keyCol[0], keyCol[1], keyCol[2]); lm.setIntensity(li, key)
        val dl = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]); lm.setDirection(li, dir[0] / dl, dir[1] / dl, dir[2] / dl)
        val sh = FloatArray(27)
        for (c in 0..2) { sh[c] = (skyC[c] + gndC[c]) * 0.5f; sh[3 + c] = (skyC[c] - gndC[c]) * 0.5f }
        ibl?.let { scene.indirectLight = null; engine.destroyIndirectLight(it) }
        ibl = IndirectLight.Builder().irradiance(3, sh).intensity(1f).build(engine).also { scene.indirectLight = it }
        // emissive surfaces (LED boards, ribbons, lamps, glass boxes, city windows) follow the time of day - same table as tools/assets/render_hq.py
        val table = EMISSIVE[mode] ?: EMISSIVE.getValue(Lighting.DAY)
        for ((name, base) in EMISSIVE_BASE) stadiumMats[name]?.let { m ->
            if (m.material.hasParameter("emissiveFactor")) { val k = table[name] ?: table.getValue("*")
                m.setParameter("emissiveFactor", Colors.RgbType.LINEAR, base[0] * k, base[1] * k, base[2] * k) }
        }
        applyFog()
        view.bloomOptions = view.bloomOptions.apply { enabled = this@Arena3D.quality >= Gfx.HIGH && mode != Lighting.DAY && mode != Lighting.OVERCAST }
    }
    private data class L6(val a: FloatArray, val b: FloatArray, val c: Float, val d: FloatArray, val e: FloatArray, val f: Float)

    /** Red ball (Tests) -> white sight screens; white ball -> black sight screens, as in real cricket. */
    fun setBallColor(red: Boolean) {
        stadiumMats["ball"]?.let { if (red) it.color(0.45f, 0.03f, 0.03f) else it.color(0.92f, 0.92f, 0.88f) }
        stadiumMats["sight_screen"]?.let { if (red) it.color(0.90f, 0.90f, 0.90f) else it.color(0.012f, 0.012f, 0.014f) }
    }

    /** Pitch wear 0..1 (footholes, follow-through rough, ball marks) grows as the match ages. */
    var pitchWear = -1f; private set
    fun setPitchWear(k: Float) {
        val w = k.coerceIn(0f, 1f); if (abs(w - pitchWear) < 0.01f) return; pitchWear = w
        stadiumMats["pitch_wear"]?.let { m -> if (m.material.hasParameter("baseColorFactor")) m.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, 1f, 1f, 1f, w) }
    }

    fun setTransform(entity: Int, x: Float, y: Float, z: Float, yawDeg: Float = 0f, sx: Float = 1f, pitchDeg: Float = 0f, rollDeg: Float = 0f) {
        val ti = tm.getInstance(entity); if (ti == 0) return
        val cy = cos(Math.toRadians(yawDeg.toDouble())).toFloat(); val sy = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
        val cp = cos(Math.toRadians(pitchDeg.toDouble())).toFloat(); val sp = sin(Math.toRadians(pitchDeg.toDouble())).toFloat()
        val cr = cos(Math.toRadians(rollDeg.toDouble())).toFloat(); val sr = sin(Math.toRadians(rollDeg.toDouble())).toFloat()
        // R = Ry * Rx * Rz ; columns
        val m = mat16
        m[0] = (cy * cr + sy * sp * sr) * sx; m[1] = (cp * sr) * sx; m[2] = (-sy * cr + cy * sp * sr) * sx; m[3] = 0f
        m[4] = -cy * sr + sy * sp * cr; m[5] = cp * cr; m[6] = sy * sr + cy * sp * cr; m[7] = 0f
        m[8] = sy * cp; m[9] = -sp; m[10] = cy * cp; m[11] = 0f
        m[12] = x; m[13] = y; m[14] = z; m[15] = 1f
        tm.setTransform(ti, m)
    }

    fun lookAt(ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float, fovDeg: Float) {
        if (abs(fovDeg - fov) > 0.01) { fov = fovDeg.toDouble(); camera.setProjection(fov, aspect, 0.1, 900.0, Camera.Fov.VERTICAL) }
        camera.lookAt(ex.toDouble(), ey.toDouble(), ez.toDouble(), tx.toDouble(), ty.toDouble(), tz.toDouble(), 0.0, 1.0, 0.0)
    }

    fun render(frameTimeNanos: Long) {
        val sc = swapChain ?: return
        if (!ui.isReadyToRender) return
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view); renderer.endFrame()
            if (++frames == 3) onFirstFrame?.invoke()
        }
    }

    fun destroy() {
        ui.detach()
        swapChain?.let { engine.destroySwapChain(it) }; swapChain = null
        engine.lightManager.destroy(sun); EntityManager.get().destroy(sun)
        scene.indirectLight = null; ibl?.let { engine.destroyIndirectLight(it) }; ibl = null
        scene.removeEntities(stadium.entities); for (a in actors) scene.removeEntities(a.inst.entities)
        loader.destroyAsset(people); loader.destroyAsset(stadium)
        materials.destroyMaterials(); materials.destroy(); resources.destroy(); loader.destroy()
        engine.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity)
        engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
        engine.destroy()
    }

    companion object {
        /** stadium.glb v2 geometry (see tools/assets/stadium.py): boundary rope ellipse around (0, CZ). */
        const val CZ = 10.06f; const val BX = 63f; const val BZ = 69f
        /** distance along (dx,dz) from (x0,z0) to the rope */
        fun boundaryDist(x0: Float, z0: Float, dx: Float, dz: Float): Float {
            val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f); val ux = dx / l; val uz = dz / l
            val a = (ux / BX).pow(2) + (uz / BZ).pow(2); val b = 2f * (x0 * ux / (BX * BX) + (z0 - CZ) * uz / (BZ * BZ))
            val c = (x0 / BX).pow(2) + ((z0 - CZ) / BZ).pow(2) - 1f
            return (-b + sqrt((b * b - 4 * a * c).coerceAtLeast(0f))) / (2 * a)
        }
        private val FLAT = listOf("outfield", "pitch", "pitch_square", "pitch_wear", "field_markings", "boundary_rope")
        private val BIG_CASTERS = listOf("stands", "roof", "pavilion", "floodlight_towers", "sight_screens", "big_screen", "facade")
        private val EMISSIVE_BASE = mapOf(
            "flood_lamp" to floatArrayOf(1f, 0.97f, 0.9f), "ad_boards" to floatArrayOf(0.85f, 0.85f, 0.85f), "ribbon" to floatArrayOf(0.9f, 0.9f, 0.9f),
            "rope" to floatArrayOf(0.25f, 0.25f, 0.25f), "vip_glass" to floatArrayOf(0.35f, 0.3f, 0.25f), "facade" to floatArrayOf(1f, 1f, 1f),
            "pavilion_front" to floatArrayOf(0.25f, 0.22f, 0.18f), "roof_led" to floatArrayOf(1f, 0.95f, 0.85f), "big_screen" to floatArrayOf(1f, 1f, 1f),
            "city" to floatArrayOf(1f, 1f, 1f))
        private val EMISSIVE = mapOf(
            Lighting.DAY to mapOf("*" to 0.6f, "flood_lamp" to 0.15f, "facade" to 0f, "city" to 0f, "pavilion_front" to 0.2f, "vip_glass" to 0.35f, "roof_led" to 0.2f),
            Lighting.OVERCAST to mapOf("*" to 0.7f, "flood_lamp" to 0.6f, "facade" to 0.1f, "city" to 0.1f, "pavilion_front" to 0.4f, "vip_glass" to 0.5f, "roof_led" to 0.6f),
            Lighting.DUSK to mapOf("*" to 1.0f, "flood_lamp" to 1.5f, "facade" to 0.6f, "city" to 0.5f, "pavilion_front" to 0.8f, "vip_glass" to 0.8f, "roof_led" to 1.2f),
            Lighting.NIGHT to mapOf("*" to 1.3f, "flood_lamp" to 6.0f, "facade" to 1.2f, "city" to 1.0f, "pavilion_front" to 1.4f, "vip_glass" to 1.3f, "roof_led" to 3.0f,
                "ad_boards" to 1.6f, "ribbon" to 1.6f))
        @Volatile private var libs = false
        fun initLibs() { if (!libs) { Filament.init(); Gltfio.init(); libs = true } }
    }
}
