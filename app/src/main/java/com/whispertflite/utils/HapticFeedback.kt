package com.whispertflite.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

object HapticFeedback {
    @JvmStatic
    fun vibrate(context: Context) {
        if (hapticEnabled(context)) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                val vibrationEffect = VibrationEffect.createOneShot(10, 255)
                vibrator.vibrate(vibrationEffect)
            }
        }
    }

    private fun hapticEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            return vibratorManager.getDefaultVibrator().hasVibrator()
        } else {
            return Settings.System.getInt(
                context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                0
            ) == 1
        }
    }
}
