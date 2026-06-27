package com.whispertflite.engine

import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import java.io.IOException

interface WhisperEngine {
    val isInitialized: Boolean

    @Throws(IOException::class)
    fun initialize(modelPath: String?, vocabPath: String?, multilingual: Boolean)
    fun deinitialize()
    fun processRecordBuffer(mAction: Whisper.Action?, mLangToken: Int): WhisperResult?
}
