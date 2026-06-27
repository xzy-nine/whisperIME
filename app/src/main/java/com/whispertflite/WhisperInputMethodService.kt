package com.whispertflite

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var micShapeDrawable: GradientDrawable? = null
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
        setupGlassWindow()
        val density = resources.displayMetrics.density
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sdcardDataFolder = getExternalFilesDir(null)
        modeAuto = sp.getBoolean("imeModeAuto", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = createGlassBackground()
            setPadding(
                dp(density, 0),
                dp(density, 8),
                dp(density, 0),
                dp(density, 0)
            )
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val navBottom = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                ).bottom
                v.setPadding(0, dp(density, 8), 0, navBottom + dp(density, 30))
                insets
            }
        }

        processingBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(density, 2)
            ).apply {
                topMargin = dp(density, 8)
                leftMargin = dp(density, 16)
                rightMargin = dp(density, 16)
            }
            max = 100
            if (Build.VERSION.SDK_INT >= 21) {
                progressTintList = ColorStateList.valueOf(
                    if (isNightMode()) 0xFFD0BCFF.toInt() else 0xFF6650a4.toInt()
                )
            }
        }
        root.addView(processingBar)

        tvStatus = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(density, 16)
                rightMargin = dp(density, 16)
            }
            gravity = Gravity.CENTER
            visibility = View.GONE
            textSize = 12f
            setTextColor(if (isNightMode()) 0xFFEFB8C8.toInt() else 0xFF7D5260.toInt())
        }
        root.addView(tvStatus)

        btnKeyboard = createToolButton(density, R.drawable.ic_keyboard_36dp,
            getString(R.string.return_button)) {
            if (mWhisper != null) stopTranscription()
            switchToPreviousInputMethod()
        }

        btnRecord = createMicButton(density)
        btnDel = createToolButton(density, R.drawable.ic_keyboard_del_48dp,
            getString(R.string.delete_button)) {
            currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        }

        btnModeAuto = createToolButton(density,
            if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp,
            getString(R.string.auto_button)) {
            modeAuto = !modeAuto
            sp.edit().putBoolean("imeModeAuto", modeAuto).apply()
            layoutButtons?.visibility = if (modeAuto) View.GONE else View.VISIBLE
            btnModeAuto?.setImageResource(
                if (modeAuto) R.drawable.ic_auto_on_36dp else R.drawable.ic_auto_off_36dp
            )
            switchToPreviousInputMethod()
        }

        btnTranslate = createToolButton(density,
            if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp,
            getString(R.string.translate)) {
            translate = !translate
            btnTranslate?.setImageResource(
                if (translate) R.drawable.ic_english_on_36dp else R.drawable.ic_english_off_36dp
            )
        }

        btnEnter = createToolButton(density, R.drawable.ic_keyboard_return_48dp,
            getString(R.string.return_button)) {
            currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        }

        setupDeleteLongPress(btnDel!!)
        setupMicTouch(btnRecord!!)

        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : Recorder.RecorderListener {
            override fun onUpdateReceived(message: String?) {
                if (message == Recorder.MSG_RECORDING) {
                    handler.post { updateMicState(true) }
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    unmuteMediaAudio()
                    HapticFeedback.vibrate(this@WhisperInputMethodService)
                    handler.post { updateMicState(false) }
                    startTranscription()
                } else if (message == Recorder.MSG_RECORDING_ERROR) {
                    unmuteMediaAudio()
                    HapticFeedback.vibrate(this@WhisperInputMethodService)
                    if (countDownTimer != null) countDownTimer!!.cancel()
                    handler.post {
                        updateMicState(false)
                        tvStatus!!.text = getString(R.string.error_no_input)
                        tvStatus!!.visibility = View.VISIBLE
                        processingBar!!.progress = 0
                    }
                }
            }
        })

        layoutButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(buildRow(density, btnKeyboard!!, btnRecord!!, btnDel!!))
            addView(buildRow(density, btnModeAuto!!, btnTranslate!!, btnEnter!!))
        }
        root.addView(layoutButtons)

        if (modeAuto) {
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

        return root
    }

    private fun isNightMode(): Boolean {
        val flags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return flags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(density: Float, value: Int): Int = (value * density).toInt()

    private fun setupGlassWindow() {
    }

    private fun createGlassBackground(): GradientDrawable {
        val isDark = isNightMode()
        val density = resources.displayMetrics.density
        val radius = 24f * density
        val baseColor = if (isDark) 0xFF111111.toInt() else 0xFFFAFAFA.toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(baseColor)
            if (Build.VERSION.SDK_INT >= 29) {
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            } else {
                cornerRadius = radius
            }
        }
    }

    private fun createMicButton(density: Float): ImageButton {
        val isDark = isNightMode()
        val idleColor = if (isDark) 0xFF4A4458.toInt() else 0xFFE7E0EC.toInt()
        micShapeDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(dp(density, 72), dp(density, 72))
            setColor(idleColor)
        }
        val rippleColor = if (isDark) 0x1FFFFFFF.toInt() else 0x1E000000.toInt()
        val ripple = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            micShapeDrawable,
            micShapeDrawable
        )
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(density, 96), dp(density, 96))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(density, 12), dp(density, 12), dp(density, 12), dp(density, 12))
            setImageResource(R.drawable.ic_mic_48dp)
            imageTintList = ColorStateList.valueOf(
                if (isDark) 0xFFE6E1E5.toInt() else 0xFF1D1B20.toInt()
            )
            background = ripple
            elevation = 4f * density
            if (Build.VERSION.SDK_INT >= 21) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
            }
            contentDescription = getString(R.string.record_button)
        }
    }

    private fun createToolButton(
        density: Float, iconRes: Int, contentDesc: String, onClick: () -> Unit
    ): ImageButton {
        val isDark = isNightMode()
        val rippleColor = if (isDark) 0x1FFFFFFF.toInt() else 0x1E000000.toInt()
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize(dp(density, 48), dp(density, 48))
            cornerRadius = dp(density, 12).toFloat()
            setColor(Color.WHITE)
        }
        val ripple = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            null,
            mask
        )
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(density, 48), dp(density, 48))
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                if (isDark) 0xFFE6E1E5.toInt() else 0xFF1D1B20.toInt()
            )
            background = ripple
            contentDescription = contentDesc
            setOnClickListener { onClick() }
        }
    }

    private fun updateMicState(isRecording: Boolean) {
        val isDark = isNightMode()
        val color = if (isRecording) {
            if (isDark) 0xFFD0BCFF.toInt() else 0xFF6650a4.toInt()
        } else {
            if (isDark) 0xFF4A4458.toInt() else 0xFFE7E0EC.toInt()
        }
        micShapeDrawable?.setColor(color)
        micShapeDrawable?.invalidateSelf()
    }

    private fun setupDeleteLongPress(btn: ImageButton) {
        btn.setOnTouchListener(object : View.OnTouchListener {
            private var initialDeleteRunnable: Runnable? = null
            private var repeatDeleteRunnable: Runnable? = null

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    currentInputConnection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    )
                    initialDeleteRunnable = Runnable {
                        currentInputConnection.sendKeyEvent(
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                        )
                        repeatDeleteRunnable = Runnable {
                            currentInputConnection.sendKeyEvent(
                                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                            )
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
    }

    private fun setupMicTouch(btn: ImageButton) {
        btn.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    handler.post { updateMicState(true) }
                    if (mWhisper != null && mWhisper!!.isInProgress) {
                        handler.post {
                            processingBar!!.isIndeterminate = true
                            tvStatus!!.text = getString(R.string.please_wait)
                            tvStatus!!.visibility = View.VISIBLE
                            updateMicState(false)
                        }
                    } else {
                        vibrate(this@WhisperInputMethodService)
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
                    handler.post { updateMicState(false) }
                    if (mRecorder != null && mRecorder!!.isInProgress) {
                        mRecorder!!.stop()
                        unmuteMediaAudio()
                    }
                }
                return true
            }
        })
    }

    private fun buildRow(density: Float, vararg views: View): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            for (i in views.indices) {
                if (i > 0) {
                    addView(SpacerView(this@WhisperInputMethodService, dp(density, 16)))
                }
                addView(views[i])
            }
        }
    }

    private class SpacerView(context: android.content.Context, width: Int) : View(context) {
        init {
            layoutParams = LinearLayout.LayoutParams(width, 0)
        }
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
