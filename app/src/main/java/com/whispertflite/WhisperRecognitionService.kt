package com.whispertflite

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Recorder.RecorderListener
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.Whisper.WhisperListener
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.HapticFeedback.vibrate
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import com.whispertflite.utils.ModelConstants
import java.io.File
import java.util.Locale

class WhisperRecognitionService : RecognitionService() {
    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null
    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null
    private var recognitionCancelled = false
    private var sp: SharedPreferences? = null

    override fun onStartListening(recognizerIntent: Intent, callback: Callback) {
        val targetLang = recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        var langCode: String = sp!!.getString("recognitionServiceLanguage", "auto")!!
        var langToken = InputLang.getIdForLanguage(langList, langCode)
        Log.d(TAG, "default langToken " + langToken)

        if (targetLang != null) {
            Log.d(TAG, "StartListening in " + targetLang)
            langCode = targetLang.split("[-_]".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0].lowercase(
                Locale.getDefault()
            ) //support both de_DE and de-DE
            langToken = InputLang.getIdForLanguage(langList, langCode)
        } else {
            Log.d(TAG, "StartListening, no language specified")
        }

        checkRecordPermission(callback)

        sdcardDataFolder = this.getExternalFilesDir(null)
        selectedTfliteFile = File(
            sdcardDataFolder,
            sp!!.getString(
                "recognitionServiceModelName",
                ModelConstants.MULTI_LINGUAL_TOP_WORLD_SLOW
            )
        )

        if (!selectedTfliteFile!!.exists()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    callback.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                } else {
                    callback.error(SpeechRecognizer.ERROR_CLIENT)
                }
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        } else {
            initModel(selectedTfliteFile!!, callback, langToken)

            mRecorder = Recorder(this)
            mRecorder!!.setListener(object : RecorderListener {
                override fun onUpdateReceived(message: String?) {
                    if (message == Recorder.MSG_RECORDING) {
                        try {
                            callback.rmsChanged(10f)
                        } catch (e: RemoteException) {
                            throw RuntimeException(e)
                        }
                    } else if (message == Recorder.MSG_RECORDING_DONE) {
                        vibrate(this@WhisperRecognitionService)
                        try {
                            callback.rmsChanged(-20.0f)
                        } catch (e: RemoteException) {
                            throw RuntimeException(e)
                        }
                        startTranscription()
                    } else if (message == Recorder.MSG_RECORDING_ERROR) {
                        try {
                            callback.error(SpeechRecognizer.ERROR_CLIENT)
                        } catch (e: RemoteException) {
                            throw RuntimeException(e)
                        }
                    }
                }
            })

            if (!mWhisper!!.isInProgress) {
                vibrate(this)
                startRecording()
                try {
                    callback.beginningOfSpeech()
                } catch (e: RemoteException) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    private fun stopRecording() {
        if (mRecorder != null && mRecorder!!.isInProgress) {
            mRecorder!!.stop()
        }
    }

    override fun onCancel(callback: Callback?) {
        Log.d(TAG, "cancel")
        stopRecording()
        deinitModel()
        recognitionCancelled = true
    }

    override fun onStopListening(callback: Callback?) {
        Log.d(TAG, "StopListening")
        stopRecording()
    }

    // Model initialization
    private fun initModel(modelFile: File, callback: Callback, langToken: Int) {
        val isMultilingualModel: Boolean =
            !(modelFile.getName().endsWith(ModelConstants.ENGLISH_ONLY_MODEL_EXTENSION))
        val vocabFileName: String =
            if (isMultilingualModel) ModelConstants.MULTILINGUAL_VOCAB_FILE else ModelConstants.ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelFile, vocabFile, isMultilingualModel)
        Log.d(TAG, "Initialized: " + modelFile.getName())
        mWhisper!!.setLanguage(langToken)
        Log.d(TAG, "Language token " + langToken)
        mWhisper!!.setListener(object : WhisperListener {
            override fun onUpdateReceived(message: String?) {}

            override fun onResultReceived(whisperResult: WhisperResult?) {
                if (whisperResult!!.result!!.trim { it <= ' ' }.length > 0) {
                    Log.d(TAG, whisperResult!!.result!!.trim { it <= ' ' })
                    try {
                        callback.endOfSpeech()
                        deinitModel()
                        val results = Bundle()
                        val resultList = ArrayList<String?>()

                        var result: String = whisperResult!!.result!!
                        if (whisperResult!!.language == "zh") {
                            val simpleChinese =
                                sp!!.getBoolean("RecognitionServiceSimpleChinese", false)
                            result =
                                if (simpleChinese) ZhConverterUtil.toSimple(result) else ZhConverterUtil.toTraditional(
                                    result
                                )
                        }

                        resultList.add(result.trim { it <= ' ' })
                        results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, resultList)
                        callback.results(results)
                    } catch (e: RemoteException) {
                        throw RuntimeException(e)
                    }
                }
            }
        })
    }

    private fun startRecording() {
        mRecorder!!.initVad()
        mRecorder!!.start()
        recognitionCancelled = false
    }

    private fun startTranscription() {
        if (!recognitionCancelled) {
            val handler = Handler(Looper.getMainLooper())
            handler.post(Runnable {
                val toast = Toast(this)
                toast.setDuration(Toast.LENGTH_SHORT)
                toast.setText(R.string.processing)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    toast.addCallback(object : Toast.Callback() {
                        override fun onToastHidden() {
                            super.onToastHidden()
                            if (mWhisper != null) toast.show()
                        }
                    })
                }
                toast.show()
            })
            mWhisper!!.setAction(Whisper.ACTION_TRANSCRIBE)
            mWhisper!!.start()
            Log.d(TAG, "Start Transcription")
        }
    }

    override fun onDestroy() {
        deinitModel()
    }

    private fun deinitModel() {
        if (mWhisper != null) {
            mWhisper!!.unloadModel()
            mWhisper = null
        }
    }

    private fun checkRecordPermission(callback: Callback) {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.need_record_audio_permission))
            try {
                callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        private const val TAG = "WhisperRecognitionService"
    }
}
