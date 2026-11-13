package com.cricketcoach.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.ui.CcmTheme
import com.cricketcoach.manager.ui.Faces
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.screens.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: GameVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // True fullscreen: draw edge-to-edge, behind system bars and into the display cutout (notch / punch-hole)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30) android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        immersive()
        GameAudio.init(this, vm.settings)
        Faces.init(this); Faces.enabled = vm.settings.photos
        GameAudio.setScene(GameAudio.Scene.MENU)
        setContent {
            CcmTheme {
                // v2.6 Display settings: interface text size + keep-awake during matches (the live match registers its own
                // BackHandler that opens the pause menu, so Back can never drop the user out of a running match)
                val d = androidx.compose.ui.platform.LocalDensity.current
                val scale = when (vm.settings.uiScale) { 0 -> 0.9f; 2 -> 1.12f; else -> 1f }
                val inMatch = vm.screen is com.cricketcoach.manager.ui.Screen.Live
                androidx.compose.runtime.LaunchedEffect(inMatch, vm.tick) { keepAwake(inMatch && vm.settings.keepScreenOn); immersive() }
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * scale)) {
                    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
    }

    /** Settings > Display > Fullscreen: hide the system bars (swipe to reveal) or keep them visible. */
    private fun immersive() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vm.settings.immersive) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun keepAwake(on: Boolean) {
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onResume() { super.onResume(); immersive(); GameAudio.onResume() }
    override fun onPause() { super.onPause(); GameAudio.onPause(); vm.manualSaveSilent() }
}
