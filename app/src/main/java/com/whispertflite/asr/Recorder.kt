package com.whispertflite.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.konovalov.vad.webrtc.Vad.Companion.builder
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import com.whispertflite.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

class Recorder(private val mContext: Context) {
    interface RecorderListener {
        fun onUpdateReceived(message: String?)
    }

    private val mInProgress = AtomicBoolean(false)

    private var mListener: RecorderListener? = null
    private val lock: Lock = ReentrantLock()
    private val hasTask: Condition = lock.newCondition()
    private val fileSavedLock = Any() // Lock object for wait/notify

    @Volatile
    private var shouldStartRecording = false
    private var useVAD = false
    private var vad: VadWebRTC? = null
    private val workerThread: Thread

    init {
        // Initialize and start the worker thread
        workerThread = Thread(Runnable { this.recordLoop() })
        workerThread.start()
    }

    fun setListener(listener: RecorderListener?) {
        this.mListener = listener
    }


    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...")
            return
        }
        lock.lock()
        try {
            Log.d(TAG, "Recording starts now")
            shouldStartRecording = true
            hasTask.signal()
        } finally {
            lock.unlock()
        }
    }

    fun initVad() {
        vad = builder()
            .setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(FrameSize.FRAME_SIZE_480)
            .setMode(Mode.VERY_AGGRESSIVE)
            .setSilenceDurationMs(800)
            .setSpeechDurationMs(200)
            .build()
        useVAD = true
        Log.d(TAG, "VAD initialized")
    }


    fun stop() {
        Log.d(TAG, "Recording stopped")
        mInProgress.set(false)

        // Wait for the recording thread to finish
        synchronized(fileSavedLock) {
            try {
                (fileSavedLock as Object).wait() // Wait until notified by the recording thread
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interrupted status
            }
        }
    }

    val isInProgress: Boolean
        get() = mInProgress.get()

    private fun sendUpdate(message: String?) {
        if (mListener != null) mListener!!.onUpdateReceived(message)
    }


    private fun recordLoop() {
        while (true) {
            lock.lock()
            try {
                while (!shouldStartRecording) {
                    hasTask.await()
                }
                shouldStartRecording = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } finally {
                lock.unlock()
            }

            // Start recording process
            try {
                recordAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Recording error...", e)
                sendUpdate(e.message)
            } finally {
                mInProgress.set(false)
            }
        }
    }

    private fun recordAudio() {
        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "AudioRecord permission is not granted")
            sendUpdate(mContext.getString(R.string.need_record_audio_permission))
            return
        }

        val channels = 1
        val bytesPerSample = 2
        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION

        var bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (bufferSize < VAD_FRAME_SIZE * 2) bufferSize = VAD_FRAME_SIZE * 2

        val audioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.startBluetoothSco()
        audioManager.setBluetoothScoOn(true)

        val builder = AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRateInHz)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)

        val audioRecord = builder.build()
        audioRecord.startRecording()

        // Calculate maximum byte counts for 30 seconds (for saving)
        val bytesForThirtySeconds = sampleRateInHz * bytesPerSample * channels * 30

        val outputBuffer = ByteArrayOutputStream() // Buffer for saving data RecordBuffer

        val audioData = ByteArray(bufferSize)
        var totalBytesRead = 0

        var isSpeech: Boolean
        var isRecording = false
        val vadAudioBuffer = ByteArray(VAD_FRAME_SIZE * 2) //VAD needs 16 bit

        while (mInProgress.get() && totalBytesRead < bytesForThirtySeconds) {
            val bytesRead = audioRecord.read(audioData, 0, VAD_FRAME_SIZE * 2)
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead) // Save all bytes read up to 30 seconds
                totalBytesRead += bytesRead
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead)
                break
            }

            if (useVAD) {
                val outputBufferByteArray = outputBuffer.toByteArray()
                if (outputBufferByteArray.size >= VAD_FRAME_SIZE * 2) {
                    // Always use the last VAD_FRAME_SIZE * 2 bytes (16 bit) from outputBuffer for VAD
                    System.arraycopy(
                        outputBufferByteArray,
                        outputBufferByteArray.size - VAD_FRAME_SIZE * 2,
                        vadAudioBuffer,
                        0,
                        VAD_FRAME_SIZE * 2
                    )

                    isSpeech = vad!!.isSpeech(vadAudioBuffer)
                    if (isSpeech) {
                        if (!isRecording) {
                            Log.d(TAG, "VAD Speech detected: recording starts")
                            sendUpdate(MSG_RECORDING)
                        }
                        isRecording = true
                    } else {
                        if (isRecording) {
                            isRecording = false
                            mInProgress.set(false)
                        }
                    }
                }
            } else {
                if (!isRecording) sendUpdate(MSG_RECORDING)
                isRecording = true
            }
        }
        Log.d(TAG, "Total bytes recorded: " + totalBytesRead)

        if (useVAD) {
            useVAD = false
            vad!!.close()
            vad = null
            Log.d(TAG, "Closing VAD")
        }
        audioRecord.stop()
        audioRecord.release()
        audioManager.stopBluetoothSco()
        audioManager.setBluetoothScoOn(false)

        // Save recorded audio data to BufferStore (up to 30 seconds)
        RecordBuffer.outputBuffer = outputBuffer.toByteArray()
        if (totalBytesRead > 6400) {  //min 0.2s
            sendUpdate(MSG_RECORDING_DONE)
        } else {
            sendUpdate(MSG_RECORDING_ERROR)
        }

        // Notify the waiting thread that recording is complete
        synchronized(fileSavedLock) {
            (fileSavedLock as Object).notify() // Notify that recording is finished
        }
    }

    companion object {
        private const val TAG = "Recorder"
        const val ACTION_STOP: String = "Stop"
        const val ACTION_RECORD: String = "Record"
        const val MSG_RECORDING: String = "Recording..."
        const val MSG_RECORDING_DONE: String = "Recording done...!"
        const val MSG_RECORDING_ERROR: String = "Recording error..."

        private const val VAD_FRAME_SIZE = 480
    }
}
