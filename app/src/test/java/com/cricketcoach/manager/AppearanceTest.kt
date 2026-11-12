package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.render3d.*
import com.cricketcoach.manager.ui.screens.kitNumber
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Phase C: per-player 3D appearance — curated descriptors, fictional looks, clips and anti-clipping data. */
class AppearanceTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun everyCuratedCodeParsesAndBelongsToARealPlayer() {
        assertTrue("expected 100+ curated players, got ${Appearance.codes.size}", Appearance.codes.size >= 100)
        val missing = Appearance.codes.keys.filter { Repo.byId[it] == null }
        assertTrue("curated ids not in the dataset: $missing", missing.isEmpty())
        for ((id, code) in Appearance.codes) {
            val l = Appearance.parse(id, code)
            assertTrue("$id height ${l.heightCm}", l.heightCm in 160..206)
            assertTrue(l.isCurated)
            assertEquals(kitNumber(id), l.number)
        }
    }

    @Test fun looksAreDeterministicAndVaried() {
        val ids = Repo.ds.players.map { it.id }
        val a = ids.map { Look.of(it, Repo.byId[it]!!.country).describe() }
        val b = ids.map { Look.of(it, Repo.byId[it]!!.country).describe() }
        assertEquals(a, b)
        // fictional looks are spread over builds, hair styles and facial hair (no army of clones)
        val gen = (1..400).map { Look.generated("gen-$it", "India") }
        assertTrue(gen.map { it.build }.toSet().size == 3)
        assertTrue(gen.map { it.hair }.toSet().size >= 5)
        assertTrue(gen.map { it.facial }.toSet().size >= 4)
        assertTrue(gen.map { it.heightCm }.toSet().size >= 15)
        gen.forEach { assertTrue(it.heightCm in 160..200); assertTrue(it.number in 1..98); assertFalse(it.isCurated) }
        // match scale stays within ±3 % so bat sweet spots / ball-in-hand stay exact
        (gen + Appearance.codes.map { Appearance.parse(it.key, it.value) }).forEach { assertTrue(it.matchScale in 0.97f..1.03f) }
    }

    @Test fun everyShowcaseClipExistsAndNothingClipsIntoTheGround() {
        val missing = Showcase.ALL_CLIPS - ClipData.DUR.keys - setOf("keep_crouch", "keep_collect", "keep_stump", "ump_idle")
        assertTrue("viewer clips missing from cricketer.glb: $missing", missing.isEmpty())
        for (sc in Showcase.values()) assertTrue(sc.name, sc.clips.isNotEmpty())
        // per-clip lowest skinned point (body + pads) computed by the asset pipeline
        assertTrue(ClipData.GROUND_MIN.size >= 30)
        for ((clip, lo) in ClipData.GROUND_MIN) assertTrue("$clip dips ${lo}m into the turf", lo >= 0f && lo < 0.03f)
        val needed = listOf("bat_cover_drive", "bowl_fast", "bowl_spin", "run", "field_dive", "field_catch", "field_throw", "keep_collect", "celebrate")
        assertTrue(needed.all { it in ClipData.GROUND_MIN })
    }
}
