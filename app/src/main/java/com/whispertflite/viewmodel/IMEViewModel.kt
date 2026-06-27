package com.whispertflite.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.whispertflite.R
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

class IMEViewModel(application: Application) : SpeechViewModel(application) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    val translateMode = MutableStateFlow(false)
    val autoMode = MutableStateFlow(false)
    val statusText = MutableStateFlow("")
    val showStatus = MutableStateFlow(false)

    private var savedMediaVolume = -1
    private var inputConnection: InputConnection? = null
    private var langToken = -1
    private var lastAutoMode = false
    var onSwitchAway: (() -> Unit)? = null

    fun setInputConnection(connection: InputConnection?) {
        inputConnection = connection
    }

    fun restoreSettings() {
        lastAutoMode = sp.getBoolean("imeModeAuto", false)
        autoMode.value = lastAutoMode
    }

    fun checkModel(): File? {
        val savedModelName = sp.getString("modelName", "whisper-small.TOP_WORLD.tflile")
            ?: "whisper-small.TOP_WORLD.tflite"
        val modelFile = File(sdcardDataFolder, savedModelName)
        return if (modelFile.exists()) modelFile else null
    }

    fun initModelIfNeeded(modelFile: File) {
        if (_whisper?.currentModelPath != modelFile.absolutePath) {
            initWhisper(modelFile)
        }
    }

    fun onRecordPressed() {
        if (isProcessingInProgress) {
            statusText.value = getApplication<Application>().getString(R.string.please_wait)
            showStatus.value = true
            return
        }
        initRecorder()
        HapticFeedback.vibrate(getApplication())
        if (autoMode.value) _recorder?.initVad()
        startRecording()
        startCountDown()
        showStatus.value = false
    }

    fun onRecordReleased() {
        if (isRecordingInProgress) {
            stopRecording()
            unmuteMediaAudio()
        }
    }

    fun commitDelete() {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
    }

    fun sendEnter() {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    }

    fun toggleTranslate() {
        translateMode.update { !it }
    }

    fun toggleAutoMode() {
        autoMode.update { !it }
        sp.edit().putBoolean("imeModeAuto", autoMode.value).apply()
    }

    fun muteMediaAudio() {
        if (!sp.getBoolean("muteDuringRecording", false)) return
        val audioManager = getApplication<Application>().getSystemService(AudioManager::class.java)
        savedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (savedMediaVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }

    fun unmuteMediaAudio() {
        if (savedMediaVolume >= 0) {
            val audioManager = getApplication<Application>().getSystemService(AudioManager::class.java)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolume, 0)
            savedMediaVolume = -1
        }
    }

    override fun onRecordingStateChanged() {
        when (_recordingState.value) {
            RecordingState.DONE -> {
                HapticFeedback.vibrate(getApplication())
                startTranscription()
            }
            RecordingState.ERROR -> {
                unmuteMediaAudio()
                cancelCountDown()
                HapticFeedback.vibrate(getApplication())
                statusText.value = getApplication<Application>().getString(R.string.error_no_input)
                showStatus.value = true
            }
            else -> {}
        }
    }

    private fun startTranscription() {
        cancelCountDown()
        _isIndeterminate.value = true
        _progress.value = 0

        val action = if (translateMode.value) Whisper.ACTION_TRANSLATE else Whisper.ACTION_TRANSCRIBE
        val langCode = sp.getString("language", "auto") ?: "auto"
        langToken = InputLang.getIdForLanguage(langList, langCode)
        startProcessing(action, langToken)
    }

    override fun onProcessingStateChanged() {
        if (_processingState.value == ProcessingState.DONE) {
            _isIndeterminate.value = false
            val whisperResult = _result.value
            if (whisperResult != null) {
                commitResult(whisperResult)
            }
        }
    }

    private fun commitResult(whisperResult: WhisperResult) {
        var result = whisperResult.result ?: ""
        if (whisperResult.language == "zh") {
            val simpleChinese = sp.getBoolean("simpleChinese", false)
            result = if (simpleChinese) ZhConverterUtil.toSimple(result)
            else ZhConverterUtil.toTraditional(result)
        }
        result = result.trim()
        if (result.isNotEmpty()) {
            val success = inputConnection?.commitText("$result ", 1) ?: false
            if (autoMode.value && success) {
                statusText.value = ""
                showStatus.value = false
                Handler(Looper.getMainLooper()).postDelayed({
                    onSwitchAway?.invoke()
                }, 100)
            }
        }
        if (!autoMode.value) {
            showStatus.value = false
        }
    }

    fun shouldAutoStart(): Boolean = lastAutoMode

    override fun onCleared() {
        unmuteMediaAudio()
        super.onCleared()
    }
}
