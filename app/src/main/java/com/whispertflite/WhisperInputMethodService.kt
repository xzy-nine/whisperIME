package com.whispertflite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Recorder.RecorderListener
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.Whisper.WhisperListener
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.HapticFeedback.vibrate
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
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
    private var sp: SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())
    private var mContext: Context? = null
    private var countDownTimer: CountDownTimer? = null
    private var modeAuto = false
    private var layoutButtons: LinearLayout? = null

    override fun onCreate() {
        mContext = this
        super.onCreate()
    }

    override fun onDestroy() {
        deinitModel()
        if (mRecorder != null && mRecorder!!.isInProgress) {
            mRecorder!!.stop()
        }
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        if (attribute.inputType == EditorInfo.TYPE_NULL) {
            Log.d(
                TAG,
                "Cancelling: onStartInput: inputType=" + attribute.inputType + ", package=" + attribute.packageName + ", fieldId=" + attribute.fieldId
            )
            deinitModel()
            if (mRecorder != null && mRecorder!!.isInProgress) {
                mRecorder!!.stop()
            }
        }
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        selectedTfliteFile = File(
            sdcardDataFolder,
            sp!!.getString("modelName", MainActivity.Companion.MULTI_LINGUAL_TOP_WORLD_SLOW)
        )

        if (!selectedTfliteFile!!.exists()) {
            switchToPreviousInputMethod() //switch back and download models first
            val intent = Intent(this, DownloadActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            if (mWhisper == null) initModel(selectedTfliteFile!!)
            else {
                if (mWhisper!!.currentModelPath != selectedTfliteFile!!.getAbsolutePath()) {
                    deinitModel()
                    initModel(selectedTfliteFile!!)
                }
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {  //runs before onStartInputView
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        val view = getLayoutInflater().inflate(R.layout.voice_service, null)
        btnRecord = view.findViewById<ImageButton>(R.id.btnRecord)
        btnKeyboard = view.findViewById<ImageButton>(R.id.btnKeyboard)
        btnTranslate = view.findViewById<ImageButton>(R.id.btnTranslate)
        btnModeAuto = view.findViewById<ImageButton>(R.id.btnModeAuto)
        btnEnter = view.findViewById<ImageButton>(R.id.btnEnter)
        btnDel = view.findViewById<ImageButton>(R.id.btnDel)
        processingBar = view.findViewById<ProgressBar?>(R.id.processing_bar)
        tvStatus = view.findViewById<TextView>(R.id.tv_status)
        sdcardDataFolder = this.getExternalFilesDir(null)
        btnTranslate!!.setImageResource(if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp)
        modeAuto = sp!!.getBoolean("imeModeAuto", false)
        btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)
        layoutButtons = view.findViewById<LinearLayout>(R.id.layout_buttons)
        checkRecordPermission()

        // Audio recording functionality
        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : RecorderListener {
            override fun onUpdateReceived(message: String?) {
                if (message == Recorder.MSG_RECORDING) {
                    handler.post(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    HapticFeedback.vibrate(mContext!!)
                    handler.post(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })
                    startTranscription()
                } else if (message == Recorder.MSG_RECORDING_ERROR) {
                    HapticFeedback.vibrate(mContext!!)
                    if (countDownTimer != null) {
                        countDownTimer!!.cancel()
                    }
                    handler.post(Runnable {
                        btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background)
                        tvStatus!!.setText(getString(R.string.error_no_input))
                        tvStatus!!.setVisibility(View.VISIBLE)
                        processingBar!!.setProgress(0)
                    })
                }
            }
        })

        if (modeAuto) {
            layoutButtons!!.setVisibility(View.GONE)
            vibrate(this)
            startRecording()
            handler.post(Runnable { processingBar!!.setProgress(100) })
            countDownTimer = object : CountDownTimer(30000, 1000) {
                override fun onTick(l: Long) {
                    handler.post(Runnable { processingBar!!.setProgress((l / 300).toInt()) })
                }

                override fun onFinish() {}
            }
            countDownTimer!!.start()
            handler.post(Runnable {
                tvStatus!!.setText("")
                tvStatus!!.setVisibility(View.GONE)
            })
        }

        btnDel!!.setOnTouchListener(object : OnTouchListener {
            private var initialDeleteRunnable: Runnable? = null
            private var repeatDeleteRunnable: Runnable? = null

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    getCurrentInputConnection().sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_DEL
                        )
                    )
                    // Post the initial delay of 500ms
                    initialDeleteRunnable = object : Runnable {
                        override fun run() {
                            getCurrentInputConnection().sendKeyEvent(
                                KeyEvent(
                                    KeyEvent.ACTION_DOWN,
                                    KeyEvent.KEYCODE_DEL
                                )
                            )
                            // Start repeating every 100ms
                            repeatDeleteRunnable = object : Runnable {
                                override fun run() {
                                    getCurrentInputConnection().sendKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_DEL
                                        )
                                    )
                                    handler.postDelayed(this, 100)
                                }
                            }
                            handler.postDelayed(repeatDeleteRunnable!!, 100)
                        }
                    }
                    handler.postDelayed(initialDeleteRunnable!!, 500)
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Remove both callbacks
                    if (initialDeleteRunnable != null) {
                        handler.removeCallbacks(initialDeleteRunnable!!)
                    }
                    if (repeatDeleteRunnable != null) {
                        handler.removeCallbacks(repeatDeleteRunnable!!)
                    }
                    initialDeleteRunnable = null
                    repeatDeleteRunnable = null
                }
                return true
            }
        })

        btnRecord!!.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event!!.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                handler.post(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                if (checkRecordPermission()) {
                    if (!mWhisper!!.isInProgress) {
                        vibrate(this)
                        startRecording()
                        handler.post(Runnable { processingBar!!.setProgress(100) })
                        countDownTimer = object : CountDownTimer(30000, 1000) {
                            override fun onTick(l: Long) {
                                handler.post(Runnable { processingBar!!.setProgress((l / 300).toInt()) })
                            }

                            override fun onFinish() {}
                        }
                        countDownTimer!!.start()
                        handler.post(Runnable {
                            tvStatus!!.setText("")
                            tvStatus!!.setVisibility(View.GONE)
                        })
                    } else {
                        handler.post(Runnable {
                            tvStatus!!.setText(getString(R.string.please_wait))
                            tvStatus!!.setVisibility(View.VISIBLE)
                        })
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                handler.post(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })
                if (mRecorder != null && mRecorder!!.isInProgress) {
                    mRecorder!!.stop()
                }
            }
            true
        })

        btnKeyboard!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (mWhisper != null) stopTranscription()
            switchToPreviousInputMethod()
        })

        btnTranslate!!.setOnClickListener(View.OnClickListener { v: View? ->
            translate = !translate
            btnTranslate!!.setImageResource(if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp)
        })

        btnEnter!!.setOnClickListener(View.OnClickListener { v: View? ->
            getCurrentInputConnection().sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
        })

        btnModeAuto!!.setOnClickListener(View.OnClickListener { v: View? ->
            modeAuto = !modeAuto
            val editor = sp!!.edit()
            editor.putBoolean("imeModeAuto", modeAuto)
            editor.apply()
            layoutButtons!!.setVisibility(if (modeAuto) View.GONE else View.VISIBLE)
            btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)
            switchToPreviousInputMethod()
        })
        return view
    }

    private fun startRecording() {
        if (modeAuto) mRecorder!!.initVad()
        mRecorder!!.start()
    }

    // Model initialization
    private fun initModel(modelFile: File) {
        val isMultilingualModel: Boolean =
            !(modelFile.getName().endsWith(MainActivity.Companion.ENGLISH_ONLY_MODEL_EXTENSION))
        val vocabFileName: String =
            if (isMultilingualModel) MainActivity.Companion.MULTILINGUAL_VOCAB_FILE else MainActivity.Companion.ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelFile, vocabFile, isMultilingualModel)
        Log.d(TAG, "Initialized: " + modelFile.getName())
        mWhisper!!.setListener(object : WhisperListener {
            override fun onUpdateReceived(message: String?) {
            }

            override fun onResultReceived(whisperResult: WhisperResult?) {
                handler.post(Runnable { processingBar!!.setIndeterminate(false) })
                handler.post(Runnable {
                    tvStatus!!.setText("")
                    tvStatus!!.setVisibility(View.GONE)
                })

                var result = whisperResult!!.result
                if (whisperResult!!.language == "zh") {
                    val simpleChinese = sp!!.getBoolean("simpleChinese", false)
                    result =
                        if (simpleChinese) ZhConverterUtil.toSimple(result) else ZhConverterUtil.toTraditional(
                            result
                        )
                }
                var commitSuccess = false
                if (result!!.trim { it <= ' ' }.length > 0) commitSuccess =
                    getCurrentInputConnection().commitText(result.trim { it <= ' ' } + " ", 1)
                if (modeAuto && commitSuccess) handler.postDelayed(
                    Runnable { switchToPreviousInputMethod() },
                    100
                ) //slightly delayed, otherwise some apps, e.g. WhatsApp, do not accept the committed text (commitText on inactive InputConnection)
            }
        })
    }

    private fun startTranscription() {
        if (countDownTimer != null) {
            countDownTimer!!.cancel()
        }
        handler.post(Runnable { processingBar!!.setProgress(0) })
        handler.post(Runnable { processingBar!!.setIndeterminate(true) })
        if (mWhisper != null) {
            if (translate) mWhisper!!.setAction(Whisper.ACTION_TRANSLATE)
            else mWhisper!!.setAction(Whisper.ACTION_TRANSCRIBE)

            val langCode: String = sp!!.getString("language", "auto")!!
            val langToken = InputLang.getIdForLanguage(langList, langCode)
            Log.d("WhisperIME", "default langToken " + langToken)
            mWhisper!!.setLanguage(langToken)
            mWhisper!!.start()
        }
    }

    private fun stopTranscription() {
        handler.post(Runnable { processingBar!!.setIndeterminate(false) })
        mWhisper!!.stop()
    }

    private fun checkRecordPermission(): Boolean {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            tvStatus!!.setVisibility(View.VISIBLE)
            tvStatus!!.setText(getString(R.string.need_record_audio_permission))
        }
        return (permission == PackageManager.PERMISSION_GRANTED)
    }

    private fun deinitModel() {
        if (mWhisper != null) {
            mWhisper!!.unloadModel()
            mWhisper = null
        }
    }

    companion object {
        private const val TAG = "WhisperInputMethodService"
        private var translate = false
    }
}