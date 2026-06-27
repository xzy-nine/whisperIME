package com.whispertflite.utils

import android.content.Context
import com.whispertflite.data.InputFeedbacks

object HapticFeedback {
    @JvmStatic
    fun vibrate(context: Context) {
        InputFeedbacks.vibrate(context)
    }
}
