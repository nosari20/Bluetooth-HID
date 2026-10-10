package com.fnosari.bluetoothhid.connectivity.hid

import android.view.KeyEvent
import com.fnosari.bluetoothhid.connectivity.hid.layouts.KeyStroke

/** Actions of the presenter remote. */
enum class PresenterAction { NEXT, PREVIOUS, PLAY_VIDEO }

/**
 * Key strokes sent by the presenter remote. Slides use PageDown/PageUp like
 * commercial clickers (PowerPoint, Keynote, Google Slides, PDF viewers).
 */
object PresenterKeys {

    /** Stroke for [action] on a host whose layout id is [layoutId]. */
    fun strokeFor(action: PresenterAction, layoutId: String): KeyStroke = when (action) {
        PresenterAction.NEXT -> KeyStroke(HIDKeys.PAGE_DOWN)
        PresenterAction.PREVIOUS -> KeyStroke(HIDKeys.PAGE_UP)
        // PowerPoint media play/pause: Alt+P on Windows/Linux, Option+Command+P on Mac.
        // 'p' sits at the same position on QWERTY and AZERTY.
        PresenterAction.PLAY_VIDEO -> if (layoutId == "fr-mac") {
            KeyStroke(HIDKeys.letter('p'), HIDModifiers.L_ALT or HIDModifiers.L_GUI)
        } else {
            KeyStroke(HIDKeys.letter('p'), HIDModifiers.L_ALT)
        }
    }

    /** Phone hardware key → action (volume down = next, volume up = previous). */
    fun actionForKeyCode(keyCode: Int): PresenterAction? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> PresenterAction.NEXT
        KeyEvent.KEYCODE_VOLUME_UP -> PresenterAction.PREVIOUS
        else -> null
    }
}
