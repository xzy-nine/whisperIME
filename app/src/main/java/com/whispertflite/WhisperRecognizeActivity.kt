package com.whispertflite

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.whispertflite.ui.RecognizeScreen
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.utils.ModelConstants
import com.whispertflite.viewmodel.RecognizeCallback
import com.whispertflite.viewmodel.RecognizeViewModel
import java.util.Locale

class WhisperRecognizeActivity : AppCompatActivity(), RecognizeCallback {
    private lateinit var viewModel: RecognizeViewModel
    private var currentResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[RecognizeViewModel::class.java]
        viewModel.setCallback(this)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val sdcardDataFolder = getExternalFilesDir(null)
        val savedModelName = sp.getString("modelName", ModelConstants.MULTI_LINGUAL_TOP_WORLD_SLOW)
            ?: ModelConstants.MULTI_LINGUAL_TOP_WORLD_SLOW
        val modelFile = java.io.File(sdcardDataFolder, savedModelName)

        if (!modelFile.exists()) {
            val intent = Intent(this, DownloadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
            return
        }

        val targetLang = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        viewModel.initModelForRecognize(modelFile, targetLang)
        viewModel.restoreSettings()

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)

        setContent {
            WhisperTheme {
                RecognizeScreen(viewModel = viewModel)
            }
        }

        if (viewModel.autoMode.value) {
            viewModel.onRecordPressed()
        }
    }

    override fun sendResult(text: String) {
        currentResult = text
        val resultIntent = Intent().apply {
            val results = ArrayList<String>().apply { add(text) }
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results)
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1.0f))
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun finishActivity() {
        setResult(RESULT_CANCELED, null)
        finish()
    }

    override fun onDestroy() {
        if (currentResult == null) {
            setResult(RESULT_CANCELED, null)
        }
        super.onDestroy()
    }
}
