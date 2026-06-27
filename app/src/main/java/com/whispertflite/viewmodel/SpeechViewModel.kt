package com.whispertflite.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class RecordingState { IDLE, RECORDING, DONE, ERROR }
enum class ProcessingState { IDLE, PROCESSING, DONE }

open class SpeechViewModel(application: Application) : AndroidViewModel(application) {
    protected val sdcardDataFolder: File? = application.getExternalFilesDir(null)

    protected var _recorder: Recorder? = null
    protected var _whisper: Whisper? = null

    protected val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    protected val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    protected val _result = MutableStateFlow<WhisperResult?>(null)
    val result: StateFlow<WhisperResult?> = _result.asStateFlow()

    protected val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    protected val _isIndeterminate = MutableStateFlow(false)
    val isIndeterminate: StateFlow<Boolean> = _isIndeterminate.asStateFlow()

    private var countDownJob: Job? = null

    fun initRecorder() {
        if (_recorder == null) {
            _recorder = Recorder(getApplication())
            _recorder!!.setListener(object : Recorder.RecorderListener {
                override fun onUpdateReceived(message: String?) {
                    when (message) {
                        Recorder.MSG_RECORDING -> {
                            _recordingState.value = RecordingState.RECORDING
                            onRecordingStateChanged()
                        }
                        Recorder.MSG_RECORDING_DONE -> {
                            _recordingState.value = RecordingState.DONE
                            onRecordingStateChanged()
                        }
                        Recorder.MSG_RECORDING_ERROR -> {
                            _recordingState.value = RecordingState.ERROR
                            cancelCountDown()
                            onRecordingStateChanged()
                        }
                    }
                }
            })
        }
    }

    fun initWhisper(modelFile: File) {
        _whisper?.unloadModel()
        val isMultilingual = !modelFile.name.endsWith(".en.tflite")
        val vocabFile = if (isMultilingual)
            File(sdcardDataFolder, "filters_vocab_multilingual.bin")
        else
            File(sdcardDataFolder, "filters_vocab_en.bin")

        _whisper = Whisper(getApplication())
        _whisper!!.loadModel(modelFile, vocabFile, isMultilingual)
        _whisper!!.setListener(object : Whisper.WhisperListener {
            override fun onUpdateReceived(message: String?) {
                if (message == Whisper.MSG_PROCESSING) {
                    _processingState.value = ProcessingState.PROCESSING
                    onProcessingStateChanged()
                }
            }

            override fun onResultReceived(whisperResult: WhisperResult?) {
                _processingState.value = ProcessingState.DONE
                _isIndeterminate.value = false
                if (whisperResult != null) {
                    _result.value = whisperResult
                }
                onProcessingStateChanged()
            }
        })
    }

    fun startRecording(withVad: Boolean = false) {
        if (withVad) _recorder!!.initVad()
        _recorder!!.start()
    }

    fun stopRecording() {
        _recorder?.stop()
    }

    fun startProcessing(action: Whisper.Action, langToken: Int) {
        cancelCountDown()
        _isIndeterminate.value = true
        _progress.value = 0
        _whisper?.setAction(action)
        _whisper?.setLanguage(langToken)
        _whisper?.start()
    }

    fun stopProcessing() {
        _isIndeterminate.value = false
        _whisper?.stop()
    }

    fun startCountDown(durationMs: Long = 30000) {
        countDownJob?.cancel()
        val step = 1000L
        val totalSteps = durationMs / step
        countDownJob = viewModelScope.launch {
            for (i in totalSteps downTo 0) {
                _progress.value = ((i * 100) / totalSteps).toInt()
                delay(step)
            }
        }
    }

    fun cancelCountDown() {
        countDownJob?.cancel()
        countDownJob = null
        _progress.value = 0
    }

    val isRecordingInProgress: Boolean get() = _recorder?.isInProgress == true
    val isProcessingInProgress: Boolean get() = _whisper?.isInProgress == true

    protected open fun onRecordingStateChanged() {}
    protected open fun onProcessingStateChanged() {}

    override fun onCleared() {
        cancelCountDown()
        _whisper?.stop()
        _whisper?.unloadModel()
        _recorder?.stop()
        super.onCleared()
    }
}
