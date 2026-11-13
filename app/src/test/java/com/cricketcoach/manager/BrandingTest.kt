package com.cricketcoach.manager

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The owner supplied exact logo / loading artwork. These tests fail if someone replaces the supplied files with
 * generated or redrawn art, or if the launcher stops pointing at the supplied-logo derivatives.
 */
class BrandingTest {
    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun suppliedArtworkIsUsedByteForByte() {
        val logo = File("src/main/res/drawable-nodpi/logo_cmp.jpg")
        val loading = File("src/main/res/drawable-nodpi/loading_hero.png")
        assertTrue("missing supplied game logo drawable", logo.isFile)
        assertTrue("missing supplied loading drawable", loading.isFile)
        assertEquals("game logo must match the owner-supplied file",
            "002644397fc3fc4081c8545dc3fc710147fbbd5827be38dab371abc2ec6737c2", sha256(logo))
        assertEquals("loading screen must match the owner-supplied file",
            "2d29ef571f5b08e7ebb2ee9a49aac20c89ef31d2a216b0ebb9ccb6bf62152c78", sha256(loading))
        assertEquals("design copy of logo must stay identical", sha256(logo), sha256(File("../design/user_provided/game_logo.jpg")))
        assertEquals("design copy of loading art must stay identical", sha256(loading), sha256(File("../design/user_provided/loading_screen.png")))
    }

    @Test fun launcherUsesSuppliedLogoDerivatives() {
        for (dpi in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            assertTrue("missing launcher icon $dpi", File("src/main/res/mipmap-$dpi/ic_launcher.webp").isFile)
            assertTrue("missing round launcher icon $dpi", File("src/main/res/mipmap-$dpi/ic_launcher_round.webp").isFile)
            assertTrue("missing adaptive foreground $dpi", File("src/main/res/mipmap-$dpi/ic_launcher_foreground.webp").isFile)
        }
        val adaptive = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(adaptive.contains("@mipmap/ic_launcher_foreground"))
        assertTrue(adaptive.contains("@color/ic_launcher_background"))
        assertFalse("old generated brand mark must not be referenced", File("src/main/java").walkTopDown().any { it.isFile && it.readText().contains("R.drawable.brand_mark") })
    }
}
