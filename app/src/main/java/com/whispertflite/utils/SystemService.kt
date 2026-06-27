package com.whispertflite.utils

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import androidx.core.content.getSystemService

val Context.vibrator: Vibrator
    get() = getSystemService()!!

val Context.audioManager: AudioManager
    get() = getSystemService()!!
