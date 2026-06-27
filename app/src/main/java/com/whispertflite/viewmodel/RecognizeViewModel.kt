package com.whispertflite.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface RecognizeCallback {
    fun sendResult(text: String)
    fun finishActivity()
}

class RecognizeViewModel(application: Application) : SpeechViewModel(application) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private var callback: RecognizeCallback? = null

    val autoMode = MutableStateFlow(false)

    private var langToken = -1

    fun setCallback(cb: RecognizeCallback) {
        callback = cb
    }

    fun restoreSettings() {
        autoMode.value = sp.getBoolean("imeModeAuto", false)
    }

    fun checkModel(): File? {
        val savedModelName = sp.getString("modelName", "whisper-small.TOP_WORLD.tflite")
            ?: "whisper-small.TOP_WORLD.tflite"
        val modelFile = File(sdcardDataFolder, savedModelName)
        return if (modelFile.exists()) modelFile else null
    }

    fun initModelForRecognize(modelFile: File, targetLang: String?) {
        var langCode = sp.getString("language", "auto") ?: "auto"
        if (targetLang != null) {
            langCode = targetLang.split("[-_]".toRegex()).firstOrNull()?.lowercase() ?: langCode
        }
        langToken = InputLang.getIdForLanguage(langList, langCode)

        initWhisper(modelFile)
    }

    fun onRecordPressed() {
        if (isProcessingInProgress) return
        initRecorder()
        if (autoMode.value) _recorder?.initVad()
        if (!isRecordingInProgress) {
            HapticFeedback.vibrate(getApplication())
            startRecording()
            startCountDown()
        }
    }

    fun onRecordReleased() {
        if (isRecordingInProgress) {
            stopRecording()
        }
    }

    override fun onRecordingStateChanged() {
        when (_recordingState.value) {
            RecordingState.DONE -> {
                HapticFeedback.vibrate(getApplication())
                startTranscription()
            }
            RecordingState.ERROR -> {
                HapticFeedback.vibrate(getApplication())
                cancelCountDown()
            }
            else -> {}
        }
    }

    private fun startTranscription() {
        cancelCountDown()
        _isIndeterminate.value = true
        _progress.value = 0
        startProcessing(Whisper.ACTION_TRANSCRIBE, langToken)
    }

    override fun onProcessingStateChanged() {
        if (_processingState.value == ProcessingState.DONE) {
            _isIndeterminate.value = false
            val whisperResult = _result.value
            if (whisperResult != null) {
                val result = processResultText(whisperResult)
                if (result.isNotEmpty()) {
                    callback?.sendResult(result)
                }
            }
        }
    }

    private fun processResultText(whisperResult: WhisperResult): String {
        var result = whisperResult.result ?: ""
        if (whisperResult.language == "zh") {
            val simpleChinese = sp.getBoolean("simpleChinese", false)
            result = if (simpleChinese) ZhConverterUtil.toSimple(result)
            else ZhConverterUtil.toTraditional(result)
        }
        return result.trim()
    }

    fun toggleAutoMode() {
        autoMode.value = !autoMode.value
        sp.edit().putBoolean("imeModeAuto", autoMode.value).apply()
    }

    fun cancel() {
        if (isProcessingInProgress) stopProcessing()
        callback?.finishActivity()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
