package com.whispertflite

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.HapticFeedback.vibrate
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import com.whispertflite.utils.ModelConstants
import java.io.File

class WhisperInputMethodService : InputMethodService() {
    private var btnRecord: ImageButton? = null
    private var btnKeyboard: ImageButton? = null
    private var btnTranslate: ImageButton? = null
    private var btnModeAuto: ImageButton? = null
    private var btnEnter: ImageButton? = null
    private var btnDel: ImageButton? = null
    private var tvStatus: TextView? = null
    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null
    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null
    private var processingBar: ProgressBar? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countDownTimer: CountDownTimer? = null
    private var modeAuto = false
    private var translate = false
    private var layoutButtons: LinearLayout? = null
    private var mSavedMediaVolume = -1

    override fun onDestroy() {
        deinitModel()
        unmuteMediaAudio()
        if (mRecorder != null && mRecorder!!.isInProgress) {
            mRecorder!!.stop()
        }
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        if (attribute.inputType == EditorInfo.TYPE_NULL) {
            Log.d(TAG, "Cancelling: onStartInput: inputType=${attribute.inputType}, package=${attribute.packageName}")
            unmuteMediaAudio()
            deinitModel()
            if (mRecorder != null && mRecorder!!.isInProgress) {
                mRecorder!!.stop()
            }
        }
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        selectedTfliteFile = File(
            sdcardDataFolder,
            sp.getString("modelName", ModelConstants.MULTI_LINGUAL_TOP_WORLD_SLOW)
        )

        if (!selectedTfliteFile!!.exists()) {
            switchToPreviousInputMethod()
            val intent = Intent(this, DownloadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            if (mWhisper == null) initModel(selectedTfliteFile!!)
            else {
                if (mWhisper!!.currentModelPath != selectedTfliteFile!!.absolutePath) {
                    deinitModel()
                    initModel(selectedTfliteFile!!)
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val view = layoutInflater.inflate(R.layout.voice_service, null)
        btnRecord = view.findViewById(R.id.btnRecord)
        btnKeyboard = view.findViewById(R.id.btnKeyboard)
        btnTranslate = view.findViewById(R.id.btnTranslate)
        btnModeAuto = view.findViewById(R.id.btnModeAuto)
        btnEnter = view.findViewById(R.id.btnEnter)
        btnDel = view.findViewById(R.id.btnDel)
        processingBar = view.findViewById(R.id.processing_bar)
        tvStatus = view.findViewById(R.id.tv_status)
        sdcardDataFolder = getExternalFilesDir(null)

        btnTranslate!!.setImageResource(if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp)
        modeAuto = sp.getBoolean("imeModeAuto", false)
        btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)
        layoutButtons = view.findViewById(R.id.layout_buttons)

        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : Recorder.RecorderListener {
            override fun onUpdateReceived(message: String?) {
                if (message == Recorder.MSG_RECORDING) {
                    handler.post { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) }
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    unmuteMediaAudio()
                    HapticFeedback.vibrate(this@WhisperInputMethodService)
                    handler.post { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) }
                    startTranscription()
                } else if (message == Recorder.MSG_RECORDING_ERROR) {
                    unmuteMediaAudio()
                    HapticFeedback.vibrate(this@WhisperInputMethodService)
                    if (countDownTimer != null) countDownTimer!!.cancel()
                    handler.post {
                        btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background)
                        tvStatus!!.text = getString(R.string.error_no_input)
                        tvStatus!!.visibility = View.VISIBLE
                        processingBar!!.progress = 0
                    }
                }
            }
        })

        if (modeAuto) {
            layoutButtons!!.visibility = View.GONE
            vibrate(this)
            startRecording()
            handler.post { processingBar!!.progress = 100 }
            countDownTimer = object : CountDownTimer(30000, 1000) {
                override fun onTick(l: Long) {
                    handler.post { processingBar!!.progress = (l / 300).toInt() }
                }
                override fun onFinish() {}
            }
            countDownTimer!!.start()
            handler.post {
                tvStatus!!.text = ""
                tvStatus!!.visibility = View.GONE
            }
        }

        btnDel!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialDeleteRunnable: Runnable? = null
            private var repeatDeleteRunnable: Runnable? = null

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    initialDeleteRunnable = Runnable {
                        currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                        repeatDeleteRunnable = Runnable {
                            currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                            handler.postDelayed(repeatDeleteRunnable!!, 100)
                        }
                        handler.postDelayed(repeatDeleteRunnable!!, 100)
                    }
                    handler.postDelayed(initialDeleteRunnable!!, 500)
                } else if (event.action == MotionEvent.ACTION_UP) {
                    if (initialDeleteRunnable != null) handler.removeCallbacks(initialDeleteRunnable!!)
                    if (repeatDeleteRunnable != null) handler.removeCallbacks(repeatDeleteRunnable!!)
                    initialDeleteRunnable = null
                    repeatDeleteRunnable = null
                }
                return true
            }
        })

        btnRecord!!.setOnTouchListener { v: View?, event: MotionEvent? ->
            if (event!!.action == MotionEvent.ACTION_DOWN) {
                handler.post { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) }
                if (mWhisper != null && mWhisper!!.isInProgress) {
                    handler.post {
                        processingBar!!.isIndeterminate = true
                        tvStatus!!.text = getString(R.string.please_wait)
                        tvStatus!!.visibility = View.VISIBLE
                        btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background)
                    }
                } else {
                    vibrate(this)
                    startRecording()
                    handler.post { processingBar!!.progress = 100 }
                    countDownTimer = object : CountDownTimer(30000, 1000) {
                        override fun onTick(l: Long) {
                            handler.post { processingBar!!.progress = (l / 300).toInt() }
                        }
                        override fun onFinish() {}
                    }
                    countDownTimer!!.start()
                    handler.post {
                        tvStatus!!.text = ""
                        tvStatus!!.visibility = View.GONE
                    }
                }
            } else if (event.action == MotionEvent.ACTION_UP) {
                handler.post { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) }
                if (mRecorder != null && mRecorder!!.isInProgress) {
                    mRecorder!!.stop()
                    unmuteMediaAudio()
                }
            }
            true
        }

        btnKeyboard!!.setOnClickListener {
            if (mWhisper != null) stopTranscription()
            switchToPreviousInputMethod()
        }

        btnTranslate!!.setOnClickListener {
            translate = !translate
            btnTranslate!!.setImageResource(if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp)
        }

        btnEnter!!.setOnClickListener {
            currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }

        btnModeAuto!!.setOnClickListener {
            modeAuto = !modeAuto
            sp.edit().putBoolean("imeModeAuto", modeAuto).apply()
            layoutButtons!!.visibility = if (modeAuto) View.GONE else View.VISIBLE
            btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)
            switchToPreviousInputMethod()
        }
        return view
    }

    private fun muteMediaAudio() {
        val audioManager = getSystemService(AudioManager::class.java)
        mSavedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (mSavedMediaVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }

    private fun unmuteMediaAudio() {
        if (mSavedMediaVolume >= 0) {
            val audioManager = getSystemService(AudioManager::class.java)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mSavedMediaVolume, 0)
            mSavedMediaVolume = -1
        }
    }

    private fun startRecording() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getBoolean("muteDuringRecording", false)) muteMediaAudio()
        if (modeAuto) mRecorder!!.initVad()
        mRecorder!!.start()
    }

    private fun initModel(modelFile: File) {
        val isMultilingual = !modelFile.name.endsWith(ModelConstants.ENGLISH_ONLY_MODEL_EXTENSION)
        val vocabFileName = if (isMultilingual) ModelConstants.MULTILINGUAL_VOCAB_FILE else ModelConstants.ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelFile, vocabFile, isMultilingual)
        Log.d(TAG, "Initialized: " + modelFile.name)
        mWhisper!!.setListener(object : Whisper.WhisperListener {
            override fun onUpdateReceived(message: String?) {}

            override fun onResultReceived(whisperResult: WhisperResult?) {
                handler.post {
                    processingBar!!.isIndeterminate = false
                    processingBar!!.progress = 0
                }
                handler.post {
                    tvStatus!!.text = ""
                    tvStatus!!.visibility = View.GONE
                }
                var result = whisperResult!!.result
                if (whisperResult.language == "zh") {
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@WhisperInputMethodService)
                    val simpleChinese = sp.getBoolean("simpleChinese", false)
                    result = if (simpleChinese) com.github.houbb.opencc4j.util.ZhConverterUtil.toSimple(result)
                    else com.github.houbb.opencc4j.util.ZhConverterUtil.toTraditional(result)
                }
                var commitSuccess = false
                if (!result.isNullOrBlank()) {
                    commitSuccess = getCurrentInputConnection()?.commitText(result.trim() + " ", 1) ?: false
                }
                if (modeAuto && commitSuccess) {
                    handler.postDelayed({ switchToPreviousInputMethod() }, 100)
                }
            }
        })
    }

    private fun startTranscription() {
        if (countDownTimer != null) countDownTimer!!.cancel()
        handler.post {
            processingBar!!.progress = 0
            processingBar!!.isIndeterminate = true
        }
        if (mWhisper != null) {
            if (translate) mWhisper!!.setAction(Whisper.ACTION_TRANSLATE)
            else mWhisper!!.setAction(Whisper.ACTION_TRANSCRIBE)
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val langCode = sp.getString("language", "auto") ?: "auto"
            val langToken = InputLang.getIdForLanguage(langList, langCode)
            mWhisper!!.setLanguage(langToken)
            mWhisper!!.start()
        }
    }

    private fun stopTranscription() {
        handler.post { processingBar!!.isIndeterminate = false }
        mWhisper!!.stop()
    }

    private fun deinitModel() {
        if (mWhisper != null) {
            mWhisper!!.unloadModel()
            mWhisper = null
        }
    }

    companion object {
        private const val TAG = "WhisperInputMethodService"
    }
}
