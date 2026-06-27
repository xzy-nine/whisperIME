package com.whispertflite

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Recorder.RecorderListener
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.Whisper.WhisperListener
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback
import com.whispertflite.utils.HapticFeedback.vibrate
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import com.whispertflite.utils.LanguagePairAdapter
import com.whispertflite.utils.LanguagePairAdapter.Companion.getLanguagePairs
import com.whispertflite.utils.ThemeUtils.setStatusBarAppearance
import org.woheller69.freeDroidWarn.FreeDroidWarn
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var mContext: Context? = null
    private var tvStatus: TextView? = null
    private var tvResult: EditText? = null
    private var fabCopy: FloatingActionButton? = null
    private var btnRecord: ImageButton? = null
    private var layoutModeChinese: LinearLayout? = null
    private var layoutTTS: LinearLayout? = null
    private var append: CheckBox? = null
    private var translate: CheckBox? = null
    private var modeSimpleChinese: CheckBox? = null
    private var modeTTS: CheckBox? = null
    private var processingBar: ProgressBar? = null
    private var btnInfo: ImageButton? = null

    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null

    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null
    private var sp: SharedPreferences? = null
    private var spinnerTflite: Spinner? = null
    private var countDownTimer: CountDownTimer? = null
    private var spinnerLanguage: Spinner? = null
    private var langToken = -1
    private var startTime: Long = 0
    private var tts: TextToSpeech? = null

    override fun onDestroy() {
        deinitModel()
        deinitTTS()
        super.onDestroy()
    }

    override fun onPause() {
        stopProcessing()
        super.onPause()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContext = this
        setContentView(R.layout.activity_main)
        setStatusBarAppearance(this)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        checkInputMethodEnabled()
        processingBar = findViewById<ProgressBar>(R.id.processing_bar)
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        append = findViewById<CheckBox>(R.id.mode_append)

        layoutTTS = findViewById<LinearLayout>(R.id.layout_tts)
        modeTTS = findViewById<CheckBox>(R.id.mode_tts)
        modeTTS!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                tts = TextToSpeech(mContext, OnInitListener { status: Int ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts!!.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            runOnUiThread(Runnable {
                                Toast.makeText(
                                    mContext,
                                    mContext!!.getString(R.string.tts_language_not_supported),
                                    Toast.LENGTH_SHORT
                                ).show()
                                modeTTS!!.setChecked(false)
                            })
                        }
                    } else {
                        runOnUiThread(Runnable {
                            Toast.makeText(
                                mContext,
                                mContext!!.getString(R.string.tts_initialization_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                    }
                })
            } else {
                deinitTTS()
            }
        })

        translate = findViewById<CheckBox>(R.id.mode_translate)
        translate!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            layoutTTS!!.setVisibility(if (isChecked) View.VISIBLE else View.GONE)
            if (layoutTTS!!.getVisibility() == View.GONE) modeTTS!!.setChecked(false)
        })

        // Call the method to copy specific file types from assets to data folder
        sdcardDataFolder = this.getExternalFilesDir(null)

        val tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite")

        // Initialize default model to use
        initModel()

        btnInfo = findViewById<ImageButton>(R.id.btnInfo)
        btnInfo!!.setOnClickListener(View.OnClickListener { view: View? ->
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/woheller69/whisperIME#Donate")
                )
            )
        })

        spinnerLanguage = findViewById<Spinner>(R.id.spnrLanguage)
        val languagePairs = getLanguagePairs(this)
        val languagePairAdapter =
            LanguagePairAdapter(this, android.R.layout.simple_spinner_item, languagePairs)
        languagePairAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage!!.setAdapter(languagePairAdapter)

        spinnerLanguage!!.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                i: Int,
                l: Long
            ) {
                langToken = InputLang.getIdForLanguage(langList, languagePairs.get(i)!!.first)
                val editor = sp!!.edit()
                editor.putString("language", languagePairs.get(i)!!.first)
                editor.apply()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
            }
        })

        selectedTfliteFile =
            File(sdcardDataFolder, sp!!.getString("modelName", MULTI_LINGUAL_TOP_WORLD_SLOW))
        val tfliteAdapter = getFileArrayAdapter(tfliteFiles)
        val position = tfliteAdapter.getPosition(selectedTfliteFile)
        spinnerTflite = findViewById<Spinner>(R.id.spnrTfliteFiles)
        spinnerTflite!!.setAdapter(tfliteAdapter)
        spinnerTflite!!.setSelection(position, false)
        if (selectedTfliteFile!!.getName() == MULTI_LINGUAL_EU_MODEL_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_SLOW) {
            spinnerLanguage!!.setEnabled(true)
            val langCode: String = sp!!.getString("language", "auto")!!
            spinnerLanguage!!.setSelection(languagePairAdapter.getIndexByCode(langCode))
        } else {
            spinnerLanguage!!.setSelection(0)
            spinnerLanguage!!.setEnabled(false)
        }
        spinnerTflite!!.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                deinitModel()
                selectedTfliteFile = parent.getItemAtPosition(position) as File?
                val editor = sp!!.edit()
                editor.putString("modelName", selectedTfliteFile!!.getName())
                editor.apply()
                initModel()
                if (selectedTfliteFile!!.getName() == MULTI_LINGUAL_EU_MODEL_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_SLOW) {
                    spinnerLanguage!!.setEnabled(true)
                    val langCode: String = sp!!.getString("language", "auto")!!
                    spinnerLanguage!!.setSelection(languagePairAdapter.getIndexByCode(langCode))
                } else {
                    spinnerLanguage!!.setSelection(0)
                    spinnerLanguage!!.setEnabled(false)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle case when nothing is selected, if needed
            }
        })


        // Implementation of record button functionality
        btnRecord = findViewById<ImageButton>(R.id.btnRecord)

        btnRecord!!.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event!!.getAction() == MotionEvent.ACTION_DOWN) {
                // Pressed
                runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                Log.d(TAG, "Start recording...")
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
                } else (Toast.makeText(
                    this,
                    getString(R.string.please_wait),
                    Toast.LENGTH_SHORT
                )).show()
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Released
                runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })
                if (mRecorder != null && mRecorder!!.isInProgress) {
                    Log.d(TAG, "Recording is in progress... stopping...")
                    stopRecording()
                }
            }
            true
        })

        layoutModeChinese = findViewById<LinearLayout>(R.id.layout_mode_chinese)
        modeSimpleChinese = findViewById<CheckBox>(R.id.mode_simple_chinese)
        modeSimpleChinese!!.setChecked(
            sp!!.getBoolean(
                "simpleChinese",
                false
            )
        ) //default to traditional Chinese
        modeSimpleChinese!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            val editor = sp!!.edit()
            editor.putBoolean("simpleChinese", isChecked)
            editor.apply()
            tvResult!!.setText("")
        })

        tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvResult = findViewById<EditText>(R.id.tvResult)
        tvResult!!.setOnClickListener(View.OnClickListener { view: View? ->
            tvResult!!.setCursorVisible(
                true
            )
        })
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tvResult!!.isCursorVisible()) tvResult!!.setCursorVisible(false)
                else finish()
            }
        })
        fabCopy = findViewById<FloatingActionButton>(R.id.fabCopy)
        fabCopy!!.setOnClickListener(View.OnClickListener { v: View? ->
            // Get the text from tvResult
            val textToCopy = tvResult!!.getText().toString().trim { it <= ' ' }

            // Copy the text to the clipboard
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.model_output), textToCopy)
            clipboard.setPrimaryClip(clip)
        })

        // Audio recording functionality
        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : RecorderListener {
            override fun onUpdateReceived(message: String?) {
                Log.d(TAG, "Update is received, Message: " + message)
                if (message == Recorder.MSG_RECORDING) {
                    runOnUiThread(Runnable { tvStatus!!.setText(getString(R.string.record_button) + "…") })
                    if (!append!!.isChecked()) runOnUiThread(Runnable { tvResult!!.setText("") })
                    runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background_pressed) })
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    HapticFeedback.vibrate(mContext!!)
                    runOnUiThread(Runnable { btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background) })

                    if (translate!!.isChecked()) startProcessing(Whisper.ACTION_TRANSLATE)
                    else startProcessing(Whisper.ACTION_TRANSCRIBE)
                } else if (message == Recorder.MSG_RECORDING_ERROR) {
                    HapticFeedback.vibrate(mContext!!)
                    if (countDownTimer != null) {
                        countDownTimer!!.cancel()
                    }
                    runOnUiThread(Runnable {
                        btnRecord!!.setBackgroundResource(R.drawable.rounded_button_background)
                        processingBar!!.setProgress(0)
                        tvStatus!!.setText(getString(R.string.error_no_input))
                    })
                }
            }
        })
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(
            this,
            "https://github.com/woheller69/whisperIME"
        )
        // Assume this Activity is the current activity, check record permission
        checkPermissions()
    }

    private fun checkInputMethodEnabled() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethodList = imm.getEnabledInputMethodList()

        val myInputMethodId =
            getPackageName() + "/" + WhisperInputMethodService::class.java.getName()
        var inputMethodEnabled = false
        for (imi in enabledInputMethodList) {
            if (imi.getId() == myInputMethodId) {
                inputMethodEnabled = true
                break
            }
        }
        if (!inputMethodEnabled) {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
    }

    // Model initialization
    private fun initModel() {
        val modelFile =
            File(sdcardDataFolder, sp!!.getString("modelName", MULTI_LINGUAL_TOP_WORLD_SLOW))
        val isMultilingualModel: Boolean = !(modelFile.getName().endsWith(
            ENGLISH_ONLY_MODEL_EXTENSION
        ))
        val vocabFileName: String =
            if (isMultilingualModel) MULTILINGUAL_VOCAB_FILE else ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelFile, vocabFile, isMultilingualModel)
        Log.d(TAG, "Initialized: " + modelFile.getName())
        mWhisper!!.setListener(object : WhisperListener {
            override fun onUpdateReceived(message: String?) {
                Log.d(TAG, "Update is received, Message: " + message)

                if (message == Whisper.MSG_PROCESSING) {
                    runOnUiThread(Runnable { tvStatus!!.setText(getString(R.string.processing)) })
                    startTime = System.currentTimeMillis()
                    runOnUiThread(Runnable { spinnerTflite!!.setEnabled(false) })
                }
            }

            override fun onResultReceived(whisperResult: WhisperResult?) {
                val timeTaken = System.currentTimeMillis() - startTime
                runOnUiThread(Runnable {
                    tvStatus!!.setText(
                        getString(R.string.processing_done) + timeTaken + "\u2009ms" + "\n" + getString(
                            R.string.language
                        ) + " " + Locale(whisperResult!!.language).getDisplayLanguage() + " " + (if (whisperResult!!.task == Whisper.Action.TRANSCRIBE) getString(
                            R.string.mode_transcription
                        ) else getString(R.string.mode_translation))
                    )
                })
                runOnUiThread(Runnable { processingBar!!.setIndeterminate(false) })
                Log.d(
                    TAG,
                    "Result: " + whisperResult!!.result + " " + whisperResult!!.language + " " + (if (whisperResult!!.task == Whisper.Action.TRANSCRIBE) "transcribing" else "translating")
                )
                if ((whisperResult!!.language == "zh") && (whisperResult!!.task == Whisper.Action.TRANSCRIBE)) {
                    runOnUiThread(Runnable { layoutModeChinese!!.setVisibility(View.VISIBLE) })
                    val simpleChinese =
                        sp!!.getBoolean("simpleChinese", false) //convert to desired Chinese mode
                    val result =
                        if (simpleChinese) ZhConverterUtil.toSimple(whisperResult!!.result) else ZhConverterUtil.toTraditional(
                            whisperResult!!.result
                        )
                    runOnUiThread(Runnable { tvResult!!.append(result) })
                } else {
                    runOnUiThread(Runnable { layoutModeChinese!!.setVisibility(View.GONE) })
                    runOnUiThread(Runnable { tvResult!!.append(whisperResult!!.result) })
                }
                runOnUiThread(Runnable { spinnerTflite!!.setEnabled(true) })
                if (modeTTS!!.isChecked()) {
                    tts!!.speak(whisperResult!!.result, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        })
    }

    private fun deinitModel() {
        if (mWhisper != null) {
            mWhisper!!.unloadModel()
            mWhisper = null
        }
    }

    private fun deinitTTS() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
    }

    private fun getFileArrayAdapter(tfliteFiles: ArrayList<File?>): ArrayAdapter<File?> {
        val adapter: ArrayAdapter<File?> =
            object : ArrayAdapter<File?>(this, android.R.layout.simple_spinner_item, tfliteFiles) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == ENGLISH_ONLY_MODEL) textView.setText(
                        R.string.english_only_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_EU_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else textView.setText(
                        getItem(position)!!.getName()
                            .substring(0, getItem(position)!!.getName().length - ".tflite".length)
                    )

                    return view
                }

                override fun getDropDownView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == ENGLISH_ONLY_MODEL) textView.setText(
                        R.string.english_only_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_EU_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else textView.setText(
                        getItem(position)!!.getName()
                            .substring(0, getItem(position)!!.getName().length - ".tflite".length)
                    )

                    return view
                }
            }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun checkPermissions() {
        val perms: MutableList<String?> = ArrayList<String?>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.RECORD_AUDIO)
            Toast.makeText(
                this,
                getString(R.string.need_record_audio_permission),
                Toast.LENGTH_SHORT
            ).show()
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted")
        } else {
            Log.d(TAG, "Record permission is not granted")
        }
    }

    // Recording calls
    private fun startRecording() {
        checkPermissions()
        mRecorder!!.start()
    }

    private fun stopRecording() {
        mRecorder!!.stop()
    }

    // Transcription calls
    private fun startProcessing(action: Whisper.Action?) {
        if (countDownTimer != null) {
            countDownTimer!!.cancel()
        }
        runOnUiThread(Runnable {
            processingBar!!.setProgress(0)
            processingBar!!.setIndeterminate(true)
        })
        mWhisper!!.setAction(action)
        mWhisper!!.setLanguage(langToken)
        mWhisper!!.start()
    }

    private fun stopProcessing() {
        processingBar!!.setIndeterminate(false)
        if (mWhisper != null && mWhisper!!.isInProgress) mWhisper!!.stop()
    }

    fun getFilesWithExtension(directory: File?, extension: String): ArrayList<File?> {
        val filteredFiles = ArrayList<File?>()

        // Check if the directory is accessible
        if (directory != null && directory.exists()) {
            val files = directory.listFiles()

            // Filter files by the provided extension
            if (files != null) {
                for (file in files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file)
                    }
                }
            }
        }

        return filteredFiles
    }

    companion object {
        private const val TAG = "MainActivity"

        // whisper-small.tflite works well for multi-lingual
        const val MULTI_LINGUAL_EU_MODEL_FAST: String = "whisper-base.EUROPEAN_UNION.tflite"
        const val MULTI_LINGUAL_TOP_WORLD_FAST: String = "whisper-base.TOP_WORLD.tflite"
        const val MULTI_LINGUAL_TOP_WORLD_SLOW: String = "whisper-small.TOP_WORLD.tflite"
        const val MULTI_LINGUAL_MODEL_FAST: String = "whisper-base.tflite"
        const val MULTI_LINGUAL_MODEL_SLOW: String = "whisper-small.tflite"
        const val ENGLISH_ONLY_MODEL: String = "whisper-tiny.en.tflite"

        // English only model ends with extension ".en.tflite"
        const val ENGLISH_ONLY_MODEL_EXTENSION: String = ".en.tflite"
        const val ENGLISH_ONLY_VOCAB_FILE: String = "filters_vocab_en.bin"
        const val MULTILINGUAL_VOCAB_FILE: String = "filters_vocab_multilingual.bin"
    }
}