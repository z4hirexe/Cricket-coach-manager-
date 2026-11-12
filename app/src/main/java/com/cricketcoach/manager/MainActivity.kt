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
        intent?.data?.let { if (savedInstanceState == null) vm.handleDeepLink(it.toString()) }
        setContent {
            CcmTheme {
                BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let { vm.handleDeepLink(it.toString()) }
    }

    private fun immersive() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onResume() { super.onResume(); immersive(); GameAudio.onResume() }
    override fun onPause() { super.onPause(); GameAudio.onPause(); vm.manualSaveSilent() }
}
