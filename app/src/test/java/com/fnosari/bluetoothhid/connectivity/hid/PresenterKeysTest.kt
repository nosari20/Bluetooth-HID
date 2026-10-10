package com.fnosari.bluetoothhid.connectivity.hid

import android.view.KeyEvent
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenterKeysTest {

    @Test
    fun slideNavigationUsesPageKeysOnEveryLayout() {
        for (id in listOf("us", "fr-win", "fr-linux", "fr-mac")) {
            assertEquals(KeyStroke(HIDKeys.PAGE_DOWN), PresenterKeys.strokeFor(PresenterAction.NEXT, id))
            assertEquals(KeyStroke(HIDKeys.PAGE_UP), PresenterKeys.strokeFor(PresenterAction.PREVIOUS, id))
        }
    }

    @Test
    fun playVideoIsAltPOnPcAndOptionCommandPOnMac() {
        val p = HIDKeys.letter('p')
        assertEquals(KeyStroke(p, HIDModifiers.L_ALT), PresenterKeys.strokeFor(PresenterAction.PLAY_VIDEO, "fr-win"))
        assertEquals(KeyStroke(p, HIDModifiers.L_ALT), PresenterKeys.strokeFor(PresenterAction.PLAY_VIDEO, "us"))
        assertEquals(
            KeyStroke(p, HIDModifiers.L_ALT or HIDModifiers.L_GUI),
            PresenterKeys.strokeFor(PresenterAction.PLAY_VIDEO, "fr-mac")
        )
    }

    @Test
    fun volumeKeysDriveSlides() {
        assertEquals(PresenterAction.NEXT, PresenterKeys.actionForKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(PresenterAction.PREVIOUS, PresenterKeys.actionForKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(PresenterKeys.actionForKeyCode(KeyEvent.KEYCODE_BACK))
    }
}
