package com.whispertflite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import java.util.Locale

class WhisperRecognizeActivity : AppCompatActivity() {
    private var btnRecord: ImageButton? = null
    private var btnCancel: ImageButton? = null
    private var btnModeAuto: ImageButton? = null
    private var processingBar: ProgressBar? = null
    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null
    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null
    private var sp: SharedPreferences? = null
    private var mContext: Context? = null
    private var countDownTimer: CountDownTimer? = null
    private var modeAuto = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContext = this
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        sdcardDataFolder = this.getExternalFilesDir(null)
        selectedTfliteFile = File(
            sdcardDataFolder,
            sp!!.getString("modelName", MainActivity.Companion.MULTI_LINGUAL_TOP_WORLD_SLOW)
        )
        if (!selectedTfliteFile!!.exists()) {
            val intent = Intent(this, DownloadActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        val targetLang = getIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        var langCode: String = sp!!.getString("language", "auto")!!
        var langToken = InputLang.getIdForLanguage(langList, langCode)
        Log.d("WhisperRecognition", "default langToken " + langToken)

        if (targetLang != null) {
            Log.d("WhisperRecognition", "StartListening in " + targetLang)
            langCode = targetLang.split("[-_]".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0].lowercase(
                Locale.getDefault()
            ) //support both de_DE and de-DE
            langToken = InputLang.getIdForLanguage(langList, langCode)
        } else {
            Log.d("WhisperRecognition", "StartListening, no language specified")
        }

        initModel(selectedTfliteFile!!, langToken)

        setContentView(R.layout.activity_recognize)

        // Set the window layout parameters
        val params = getWindow().getAttributes()
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.BOTTOM // Position at the bottom of the screen

        btnCancel = findViewById<ImageButton>(R.id.btnCancel)
        btnRecord = findViewById<ImageButton>(R.id.btnRecord)
        btnModeAuto = findViewById<ImageButton>(R.id.btnModeAuto)
        processingBar = findViewById<ProgressBar?>(R.id.processing_bar)

        modeAuto = sp!!.getBoolean("imeModeAuto", false)
        btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)

        // Audio recording functionality
        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : RecorderListener {
            override fun onUpdateReceived(message: String?) {
                if (message == Recorder.MSG_RECORDING) {
                    runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    HapticFeedback.vibrate(mContext!!)
                    runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })
                    startTranscription()
                } else if (message == Recorder.MSG_RECORDING_ERROR) {
                    HapticFeedback.vibrate(mContext!!)
                    if (countDownTimer != null) {
                        countDownTimer!!.cancel()
                    }
                    runOnUiThread(Runnable {
                        btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background)
                        processingBar!!.setProgress(0)
                        Toast.makeText(mContext, R.string.error_no_input, Toast.LENGTH_SHORT).show()
                    })
                }
            }
        })

        if (modeAuto) {
            btnRecord!!.setVisibility(View.GONE)
            vibrate(this)
            startRecording()
            runOnUiThread(Runnable { processingBar!!.setProgress(100) })
            countDownTimer = object : CountDownTimer(30000, 1000) {
                override fun onTick(l: Long) {
                    runOnUiThread(Runnable { processingBar!!.setProgress((l / 300).toInt()) })
                }

                override fun onFinish() {}
            }
            countDownTimer!!.start()
        }

        btnModeAuto!!.setOnClickListener(View.OnClickListener { v: View? ->
            modeAuto = !modeAuto
            val editor = sp!!.edit()
            editor.putBoolean("imeModeAuto", modeAuto)
            editor.apply()
            btnRecord!!.setVisibility(if (modeAuto) View.GONE else View.VISIBLE)
            btnModeAuto!!.setImageResource(if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp)
            if (mWhisper != null) stopTranscription()
            setResult(RESULT_CANCELED, null)
            finish()
        })

        btnRecord!!.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event!!.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                if (checkRecordPermission()) {
                    if (!mWhisper!!.isInProgress) {
                        vibrate(this)
                        startRecording()
                        runOnUiThread(Runnable { processingBar!!.setProgress(100) })
                        countDownTimer = object : CountDownTimer(30000, 1000) {
                            override fun onTick(l: Long) {
                                runOnUiThread(Runnable { processingBar!!.setProgress((l / 300).toInt()) })
                            }

                            override fun onFinish() {}
                        }
                        countDownTimer!!.start()
                    } else {
                        runOnUiThread(Runnable {
                            Toast.makeText(
                                this,
                                getString(R.string.please_wait),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })
                if (mRecorder != null && mRecorder!!.isInProgress) {
                    mRecorder!!.stop()
                }
            }
            true
        })

        btnCancel!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (mWhisper != null) stopTranscription()
            setResult(RESULT_CANCELED, null)
            finish()
        })
    }

    private fun startRecording() {
        if (modeAuto) mRecorder!!.initVad()
        mRecorder!!.start()
    }

    // Model initialization
    private fun initModel(modelFile: File, langToken: Int) {
        val isMultilingualModel: Boolean =
            !(modelFile.getName().endsWith(MainActivity.Companion.ENGLISH_ONLY_MODEL_EXTENSION))
        val vocabFileName: String =
            if (isMultilingualModel) MainActivity.Companion.MULTILINGUAL_VOCAB_FILE else MainActivity.Companion.ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelFile, vocabFile, isMultilingualModel)
        Log.d(TAG, "Initialized: " + modelFile.getName())
        mWhisper!!.setLanguage(langToken)
        Log.d(TAG, "Language token " + langToken)
        mWhisper!!.setListener(object : WhisperListener {
            override fun onUpdateReceived(message: String?) {}

            override fun onResultReceived(whisperResult: WhisperResult?) {
                runOnUiThread(Runnable { processingBar!!.setIndeterminate(false) })

                var result = whisperResult!!.result
                if (whisperResult!!.language == "zh") {
                    val simpleChinese = sp!!.getBoolean("simpleChinese", false)
                    result =
                        if (simpleChinese) ZhConverterUtil.toSimple(result) else ZhConverterUtil.toTraditional(
                            result
                        )
                }
                if (result!!.trim { it <= ' ' }.length > 0) {
                    sendResult(result.trim { it <= ' ' })
                }
            }
        })
    }

    private fun sendResult(result: String?) {
        val sendResultIntent = Intent()
        val results = ArrayList<String?>()
        results.add(result)
        sendResultIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results)
        sendResultIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1.0f))
        setResult(RESULT_OK, sendResultIntent)
        finish()
    }

    private fun startTranscription() {
        if (countDownTimer != null) {
            countDownTimer!!.cancel()
        }
        runOnUiThread(Runnable {
            processingBar!!.setProgress(0)
            processingBar!!.setIndeterminate(true)
        })
        if (mWhisper != null) {
            mWhisper!!.setAction(Whisper.ACTION_TRANSCRIBE)
            mWhisper!!.start()
            Log.d(TAG, "Start Transcription")
        }
    }

    private fun stopTranscription() {
        runOnUiThread(Runnable { processingBar!!.setIndeterminate(false) })
        mWhisper!!.stop()
    }

    private fun checkRecordPermission(): Boolean {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                getString(R.string.need_record_audio_permission),
                Toast.LENGTH_SHORT
            ).show()
        }
        return (permission == PackageManager.PERMISSION_GRANTED)
    }

    private fun deinitModel() {
        if (mWhisper != null) {
            mWhisper!!.unloadModel()
            mWhisper = null
        }
    }

    public override fun onDestroy() {
        deinitModel()
        if (mRecorder != null && mRecorder!!.isInProgress) {
            mRecorder!!.stop()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperRecognizeActivity"
    }
}
