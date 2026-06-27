package com.whispertflite.asr

import android.content.Context
import android.util.Log
import com.whispertflite.engine.WhisperEngine
import com.whispertflite.engine.WhisperEngineJava
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

class Whisper(context: Context?) {
    interface WhisperListener {
        fun onUpdateReceived(message: String?)
        fun onResultReceived(result: WhisperResult?)
    }

    var currentModelPath: String = ""
        private set

    enum class Action {
        TRANSLATE, TRANSCRIBE
    }

    private val mInProgress = AtomicBoolean(false)

    private val mWhisperEngine: WhisperEngine
    private var mAction: Action? = null
    private var mLangToken = -1
    private var mUpdateListener: WhisperListener? = null

    private val taskLock: Lock = ReentrantLock()
    private val hasTask: Condition = taskLock.newCondition()

    @Volatile
    private var taskAvailable = false

    init {
        this.mWhisperEngine = WhisperEngineJava(context)

        // Start thread for RecordBuffer transcription
        val threadProcessRecordBuffer = Thread(Runnable { this.processRecordBufferLoop() })
        threadProcessRecordBuffer.start()
    }

    fun setListener(listener: WhisperListener?) {
        this.mUpdateListener = listener
    }

    fun loadModel(modelPath: File, vocabPath: File, isMultilingual: Boolean) {
        loadModel(modelPath.getAbsolutePath(), vocabPath.getAbsolutePath(), isMultilingual)
        currentModelPath = modelPath.getAbsolutePath()
    }

    fun loadModel(modelPath: String?, vocabPath: String?, isMultilingual: Boolean) {
        try {
            mWhisperEngine.initialize(modelPath, vocabPath, isMultilingual)
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing model...", e)
            sendUpdate("Model initialization failed")
        }
    }

    fun unloadModel() {
        mWhisperEngine.deinitialize()
        currentModelPath = ""
    }

    fun setAction(action: Action?) {
        this.mAction = action
    }

    fun setLanguage(language: Int) {
        this.mLangToken = language
    }

    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...")
            return
        }
        taskLock.lock()
        try {
            taskAvailable = true
            hasTask.signal()
        } finally {
            taskLock.unlock()
        }
    }

    fun stop() {
        mInProgress.set(false)
    }

    val isInProgress: Boolean
        get() = mInProgress.get()

    private fun processRecordBufferLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            taskLock.lock()
            try {
                while (!taskAvailable) {
                    hasTask.await()
                }
                processRecordBuffer()
                taskAvailable = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                taskLock.unlock()
            }
        }
    }

    private fun processRecordBuffer() {
        try {
            if (mWhisperEngine.isInitialized && RecordBuffer.outputBuffer != null) {
                val startTime = System.currentTimeMillis()
                sendUpdate(MSG_PROCESSING)

                var whisperResult: WhisperResult? = null
                synchronized(mWhisperEngine) {
                    whisperResult = mWhisperEngine.processRecordBuffer(mAction, mLangToken)
                }
                sendResult(whisperResult)

                val timeTaken = System.currentTimeMillis() - startTime
                Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms")
                sendUpdate(MSG_PROCESSING_DONE)
            } else {
                sendUpdate("Engine not initialized or file path not set")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            sendUpdate("Transcription failed: " + e.message)
        } finally {
            mInProgress.set(false)
        }
    }

    private fun sendUpdate(message: String?) {
        if (mUpdateListener != null) {
            mUpdateListener!!.onUpdateReceived(message)
        }
    }

    private fun sendResult(whisperResult: WhisperResult?) {
        if (mUpdateListener != null) {
            mUpdateListener!!.onResultReceived(whisperResult)
        }
    }

    companion object {
        private const val TAG = "Whisper"
        const val MSG_PROCESSING: String = "Processing..."
        const val MSG_PROCESSING_DONE: String = "Processing done...!"

        @JvmField
        val ACTION_TRANSCRIBE: Action = Action.TRANSCRIBE
        @JvmField
        val ACTION_TRANSLATE: Action = Action.TRANSLATE
    }
}
