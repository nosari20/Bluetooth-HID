package com.fnosari.bluetoothhid.utils

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Short pulse confirming a slide change. Uses the media vibration channel so
 * it still works when the system "touch feedback" setting is off.
 */
class SlideVibrator(context: Context) {

    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)

    fun pulse() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), attributes)
    }
}
