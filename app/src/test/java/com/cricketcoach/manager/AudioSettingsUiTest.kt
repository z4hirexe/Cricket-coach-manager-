package com.cricketcoach.manager

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.ui.CcmTheme
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.screens.SettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w891dp-h411dp-land-xxhdpi")
class AudioSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun masterMuteCanAlwaysBeUndoneAndVoiceSpeedPersists() {
        val vm = GameVM(ApplicationProvider.getApplicationContext<Application>())
        vm.settings.mute = false
        vm.settings.voiceSpeed = 1.25f
        compose.setContent { CcmTheme { SettingsScreen(vm) } }
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithContentDescription("Mute Master").performClick()
        assertTrue(vm.settings.mute)
        compose.onNodeWithContentDescription("Unmute Master").assertIsEnabled().performClick()
        assertFalse(vm.settings.mute)
        assertEquals(1.25f, vm.settings.voiceSpeed, 0.001f)
        vm.settings.voiceSpeed = 1f
    }
}
