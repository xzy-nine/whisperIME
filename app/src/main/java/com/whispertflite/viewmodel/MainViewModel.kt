package com.whispertflite.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.whispertflite.R
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import com.whispertflite.utils.ModelConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

class MainViewModel(application: Application) : SpeechViewModel(application) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private var tts: TextToSpeech? = null
    private var startTime: Long = 0
    private var langToken = -1

    val modelFiles = MutableStateFlow<List<File>>(emptyList())
    val selectedModel = MutableStateFlow<File?>(null)
    val languagePairs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val selectedLanguageIndex = MutableStateFlow(0)

    val appendMode = MutableStateFlow(false)
    val translateMode = MutableStateFlow(false)
    val ttsMode = MutableStateFlow(false)
    val simpleChinese = MutableStateFlow(false)
    val muteMode = MutableStateFlow(false)

    val resultText = MutableStateFlow("")
    val statusText = MutableStateFlow("")
    val showChineseLayout = MutableStateFlow(false)

    fun loadLanguagePairs(context: Context) {
        val pairs = mutableListOf<Pair<String, String>>()
        val sortedLanguages = context.resources.getStringArray(R.array.top40_languages)
        for (code in sortedLanguages) {
            val locale = Locale(code)
            pairs.add(Pair(code, locale.displayLanguage))
        }
        pairs.sortBy { it.second }
        pairs.add(0, Pair("auto", context.getString(R.string.auto_lang)))
        languagePairs.value = pairs
    }

    fun loadModelFiles() {
        val files = sdcardDataFolder?.let {
            it.listFiles()?.filter { f -> f.isFile && f.name.endsWith(".tflite") }?.toList()
        } ?: emptyList()
        modelFiles.value = files
    }

    fun restoreSettings() {
        simpleChinese.value = sp.getBoolean("simpleChinese", false)
        muteMode.value = sp.getBoolean("muteDuringRecording", false)
        appendMode.value = false
        translateMode.value = false
        ttsMode.value = false

        val savedModelName = sp.getString("modelName", MULTI_LINGUAL_TOP_WORLD_SLOW) ?: MULTI_LINGUAL_TOP_WORLD_SLOW
        val savedLang = sp.getString("language", "auto") ?: "auto"

        val match = modelFiles.value.find { it.name == savedModelName }
        selectedModel.value = match ?: modelFiles.value.firstOrNull()

        if (match != null) {
            val idx = languagePairs.value.indexOfFirst { it.first == savedLang }
            selectedLanguageIndex.value = if (idx >= 0) idx else 0
            val code = if (idx >= 0) languagePairs.value[idx].first else "auto"
            langToken = InputLang.getIdForLanguage(langList, code)
            updateLanguageState(match)
        }
    }

    fun onModelSelected(model: File) {
        stopProcessing()
        selectedModel.value = model
        sp.edit().putString("modelName", model.name).apply()
        updateLanguageState(model)
    }

    private fun updateLanguageState(model: File) {
        val isMultilingual = model.name == MULTI_LINGUAL_EU_MODEL_FAST ||
                model.name == MULTI_LINGUAL_TOP_WORLD_FAST ||
                model.name == MULTI_LINGUAL_TOP_WORLD_SLOW
        if (!isMultilingual) {
            selectedLanguageIndex.value = 0
            langToken = InputLang.getIdForLanguage(langList, "auto")
        }
    }

    fun onLanguageSelected(index: Int) {
        if (index < languagePairs.value.size) {
            val code = languagePairs.value[index].first
            selectedLanguageIndex.value = index
            langToken = InputLang.getIdForLanguage(langList, code)
            sp.edit().putString("language", code).apply()
        }
    }

    fun toggleSimpleChinese(checked: Boolean) {
        simpleChinese.value = checked
        sp.edit().putBoolean("simpleChinese", checked).apply()
        resultText.value = ""
    }

    fun toggleMute(checked: Boolean) {
        muteMode.value = checked
        sp.edit().putBoolean("muteDuringRecording", checked).apply()
    }

    fun toggleTTS(checked: Boolean) {
        ttsMode.value = checked
        if (checked) {
            val ctx = getApplication<Application>()
            tts = TextToSpeech(ctx) { status ->
                if (status != TextToSpeech.SUCCESS || tts?.setLanguage(Locale.US) == TextToSpeech.LANG_MISSING_DATA) {
                    ttsMode.value = false
                }
            }
        } else {
            deinitTTS()
        }
    }

    fun onRecordPressed() {
        if (isProcessingInProgress) return
        initRecorder()
        val model = selectedModel.value ?: return
        if (_whisper?.currentModelPath != model.absolutePath && model.exists()) {
            initWhisper(model)
        }
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
        val ctx = getApplication<Application>()
        when (_recordingState.value) {
            RecordingState.RECORDING -> {
                statusText.value = "${ctx.getString(R.string.record_button)}…"
                if (!appendMode.value) resultText.value = ""
            }
            RecordingState.DONE -> {
                HapticFeedback.vibrate(ctx)
                val action = if (translateMode.value) Whisper.ACTION_TRANSLATE else Whisper.ACTION_TRANSCRIBE
                startProcessing(action, langToken)
            }
            RecordingState.ERROR -> {
                cancelCountDown()
                _progress.value = 0
                HapticFeedback.vibrate(ctx)
                statusText.value = ctx.getString(R.string.error_no_input)
            }
            else -> {}
        }
    }

    override fun onProcessingStateChanged() {
        when (_processingState.value) {
            ProcessingState.PROCESSING -> {
                statusText.value = getApplication<Application>().getString(R.string.processing)
                startTime = System.currentTimeMillis()
            }
            ProcessingState.DONE -> {
                val timeTaken = System.currentTimeMillis() - startTime
                _isIndeterminate.value = false
                val whisperResult = _result.value
                if (whisperResult != null) {
                    appendResult(whisperResult)
                    updateStatusAfterResult(timeTaken, whisperResult)
                }
                _progress.value = 0
                _processingState.value = ProcessingState.IDLE
            }
            else -> {}
        }
    }

    private fun appendResult(whisperResult: WhisperResult) {
        val isChinese = whisperResult.language == "zh" && whisperResult.task == Whisper.Action.TRANSCRIBE
        showChineseLayout.value = isChinese
        val text = if (isChinese) {
            if (simpleChinese.value) ZhConverterUtil.toSimple(whisperResult.result)
            else ZhConverterUtil.toTraditional(whisperResult.result)
        } else {
            whisperResult.result
        }
        if (!appendMode.value) resultText.value = ""
        resultText.value = resultText.value + (text ?: "")
        if (ttsMode.value && whisperResult.result != null) {
            tts?.speak(whisperResult.result, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun updateStatusAfterResult(timeTaken: Long, result: WhisperResult) {
        val ctx = getApplication<Application>()
        val lang = Locale(result.language ?: "").displayLanguage
        val task = if (result.task == Whisper.Action.TRANSCRIBE)
            ctx.getString(R.string.mode_transcription)
        else ctx.getString(R.string.mode_translation)
        statusText.value = "${ctx.getString(R.string.processing_done)}$timeTaken\u2009ms\n${ctx.getString(R.string.language)} $lang $task"
    }

    fun copyToClipboard() {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(ctx.getString(R.string.model_output), resultText.value)
        clipboard.setPrimaryClip(clip)
    }

    fun openInfo() {
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/whisperIME#Donate")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun deinitTTS() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onCleared() {
        deinitTTS()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MainViewModel"
        const val MULTI_LINGUAL_EU_MODEL_FAST = ModelConstants.MULTI_LINGUAL_EU_MODEL_FAST
        const val MULTI_LINGUAL_TOP_WORLD_FAST = ModelConstants.MULTI_LINGUAL_TOP_WORLD_FAST
        const val MULTI_LINGUAL_TOP_WORLD_SLOW = ModelConstants.MULTI_LINGUAL_TOP_WORLD_SLOW
        const val MULTI_LINGUAL_MODEL_FAST = ModelConstants.MULTI_LINGUAL_MODEL_FAST
        const val MULTI_LINGUAL_MODEL_SLOW = ModelConstants.MULTI_LINGUAL_MODEL_SLOW
        const val ENGLISH_ONLY_MODEL = ModelConstants.ENGLISH_ONLY_MODEL
    }
}
