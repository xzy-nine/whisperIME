package com.whispertflite.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.whispertflite.utils.vibrator

object InputFeedbacks {

    enum class FeedbackMode { FollowingSystem, Enabled, Disabled }

    @Volatile
    private var systemHapticFeedback = false

    fun syncSystemPrefs(context: Context) {
        @Suppress("DEPRECATION")
        systemHapticFeedback =
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 1
    }

    // ---- 可配置参数 ----
    @Volatile
    var feedbackMode: FeedbackMode = FeedbackMode.FollowingSystem
    @Volatile
    var pressDurationMs: Long = 10
    @Volatile
    var longPressDurationMs: Long = 30
    @Volatile
    var pressAmplitude: Int = 255
    @Volatile
    var longPressAmplitude: Int = 255
    @Volatile
    var hapticOnKeyUp: Boolean = false

    private val hasAmplitudeControl: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                (appVibrator?.hasAmplitudeControl() == true)

    @Volatile
    private var appVibrator: android.os.Vibrator? = null

    fun init(context: Context) {
        appVibrator = context.applicationContext.vibrator
        syncSystemPrefs(context)
    }

    /**
     * 对 View 执行震动反馈，支持时长/幅度控制
     */
    fun hapticFeedback(view: View, longPress: Boolean = false, keyUp: Boolean = false) {
        when (feedbackMode) {
            FeedbackMode.Disabled -> return
            FeedbackMode.FollowingSystem -> if (!systemHapticFeedback) return
            FeedbackMode.Enabled -> {}
        }
        if (keyUp && !hapticOnKeyUp) return

        val duration: Long
        val amplitude: Int
        val hfc: Int
        if (longPress) {
            duration = longPressDurationMs
            amplitude = longPressAmplitude
            hfc = HapticFeedbackConstants.LONG_PRESS
        } else {
            duration = pressDurationMs
            amplitude = pressAmplitude
            hfc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyUp) {
                HapticFeedbackConstants.KEYBOARD_RELEASE
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }

        val vib = appVibrator ?: view.context.vibrator
        if (duration != 0L) {
            if (hasAmplitudeControl && amplitude != 0) {
                vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(duration)
            }
        } else {
            var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            if (feedbackMode == FeedbackMode.Enabled) {
                @Suppress("DEPRECATION")
                flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            }
            view.performHapticFeedback(hfc, flags)
        }
    }

    /** 简单单次震动（兼容旧 API） */
    fun vibrate(context: Context) {
        when (feedbackMode) {
            FeedbackMode.Disabled -> return
            FeedbackMode.FollowingSystem -> if (!systemHapticFeedback) return
            FeedbackMode.Enabled -> {}
        }
        val v = appVibrator ?: context.vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(pressDurationMs, pressAmplitude))
        }
    }
}
