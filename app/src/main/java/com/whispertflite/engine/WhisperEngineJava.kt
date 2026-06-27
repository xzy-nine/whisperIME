package com.whispertflite.engine

import android.content.Context
import android.util.Log
import com.whispertflite.asr.RecordBuffer.samples
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.WhisperUtil
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.Arrays
import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Exception
import kotlin.FloatArray
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Throws
import kotlin.math.min

class WhisperEngineJava(private val mContext: Context?) : WhisperEngine {
    private val TAG = "WhisperEngineJava"
    private val mWhisperUtil = WhisperUtil()

    private var mIsInitialized = false
    private var mInterpreter: Interpreter? = null

    override val isInitialized: Boolean
        get() = mIsInitialized

    @Throws(IOException::class)
    override fun initialize(modelPath: String?, vocabPath: String?, multilingual: Boolean) {
        // Load model
        loadModel(modelPath)
        Log.d(TAG, "Model is loaded..." + modelPath)

        // Load filters and vocab
        val ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath)
        if (ret) {
            mIsInitialized = true
            Log.d(TAG, "Filters and Vocab are loaded..." + vocabPath)
        } else {
            mIsInitialized = false
            Log.d(TAG, "Failed to load Filters and Vocab...")
        }
    }

    // Unload the model by closing the interpreter
    override fun deinitialize() {
        if (mInterpreter != null) {
            mInterpreter!!.setCancelled(true)
            mInterpreter!!.close()
            mInterpreter = null // Optional: Set to null to avoid accidental reuse
        }
    }

    override fun processRecordBuffer(mAction: Whisper.Action?, mLangToken: Int): WhisperResult {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...")
        val melSpectrogram = this.melSpectrogram
        Log.d(TAG, "Mel spectrogram is calculated...!")

        // Perform inference
        val whisperResult = runInference(melSpectrogram, mAction, mLangToken)
        Log.d(TAG, "Inference is executed...!")

        return whisperResult
    }


    // Load TFLite model
    @Throws(IOException::class)
    private fun loadModel(modelPath: String?) {
        val fileInputStream = FileInputStream(modelPath)
        val fileChannel = fileInputStream.getChannel()
        val startOffset: Long = 0
        val declaredLength = fileChannel.size()
        val tfliteModel: ByteBuffer =
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        // Set the number of threads for inference
        val options = Interpreter.Options()
        options.setUseXNNPACK(false) //cannot be used due to dynamic tensors
        options.setNumThreads(Runtime.getRuntime().availableProcessors())
        options.setCancellable(true)

        mInterpreter = Interpreter(tfliteModel, options)
    }

    private val melSpectrogram: FloatArray
        get() {
            // Get samples in PCM_FLOAT format
            val samples = samples

            val fixedInputSize =
                WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
            val inputSamples = FloatArray(fixedInputSize)
            val copyLength = min(samples.size, fixedInputSize)
            System.arraycopy(samples, 0, inputSamples, 0, copyLength)

            val cores = Runtime.getRuntime().availableProcessors()
            return mWhisperUtil.getMelSpectrogram(
                inputSamples,
                inputSamples.size,
                copyLength,
                cores
            )
        }

    private fun runInference(
        inputData: FloatArray,
        mAction: Whisper.Action?,
        mLangToken: Int
    ): WhisperResult {
        Log.d("Whisper", "Signatures " + mInterpreter!!.getSignatureKeys().contentToString())

        // Create input tensor
        val inputTensor = mInterpreter!!.getInputTensor(0)

        // Create output tensor
        val outputTensor = mInterpreter!!.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)

        // Load input data
        val inputSize =
            inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES
        val inputBuffer = ByteBuffer.allocateDirect(inputSize)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (input in inputData) {
            inputBuffer.putFloat(input)
        }

        var signature_key = "serving_default"
        if (mAction == Whisper.Action.TRANSLATE) {
            if (Arrays.asList<String?>(*mInterpreter!!.getSignatureKeys())
                    .contains("serving_translate")
            ) signature_key = "serving_translate"
        } else if (mAction == Whisper.ACTION_TRANSCRIBE) {
            if (Arrays.asList<String?>(*mInterpreter!!.getSignatureKeys())
                    .contains("serving_transcribe_lang") && mLangToken != -1
            ) signature_key = "serving_transcribe_lang"
            else if (Arrays.asList<String?>(*mInterpreter!!.getSignatureKeys())
                    .contains("serving_transcribe")
            ) signature_key = "serving_transcribe"
        }

        val inputsMap: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        val inputs = mInterpreter!!.getSignatureInputs(signature_key)
        inputsMap.put(inputs[0], inputBuffer)
        if (signature_key == "serving_transcribe_lang") {
            Log.d(TAG, "Serving_transcribe_lang " + mLangToken)
            val langTokenBuffer = IntBuffer.allocate(1)
            langTokenBuffer.put(mLangToken)
            langTokenBuffer.rewind()
            inputsMap.put(inputs[1], langTokenBuffer)
        }

        val outputsMap: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        val outputs = mInterpreter!!.getSignatureOutputs(signature_key)
        outputsMap.put(outputs[0], outputBuffer.getBuffer())

        // Run inference
        try {
            mInterpreter!!.runSignature(inputsMap, outputsMap, signature_key)
        } catch (e: Exception) {
            return WhisperResult("", "", mAction)
        }

        // Retrieve the results
        val inputLangList = InputLang.langList
        var language: String? = ""
        var task: Whisper.Action? = null
        val outputLen = outputBuffer.getIntArray().size
        Log.d(TAG, "output_len: " + outputLen)
        val resultArray: MutableList<ByteArray> = ArrayList<ByteArray>()
        for (i in 0..<outputLen) {
            val token = outputBuffer.getBuffer().getInt()
            if (token == mWhisperUtil.tokenEOT) break

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.tokenEOT) {
                val wordBytes = mWhisperUtil.getWordFromToken(token)
                resultArray.add(wordBytes!!)
            } else {
                if (token == mWhisperUtil.tokenTranscribe) {
                    Log.d(TAG, "It is Transcription...")
                    task = Whisper.Action.TRANSCRIBE
                }

                if (token == mWhisperUtil.tokenTranslate) {
                    Log.d(TAG, "It is Translation...")
                    task = Whisper.Action.TRANSLATE
                }

                if (token >= 50259 && token <= 50357) {
                    language = InputLang.getLanguageCodeById(inputLangList, token)
                    Log.d(TAG, "Detected language code: " + language)
                }
                val wordBytes = mWhisperUtil.getWordFromToken(token)
                Log.d(
                    TAG,
                    "Skipping token: " + token + ", word: " + kotlin.text.String(
                        wordBytes!!,
                        StandardCharsets.UTF_8
                    )
                )
            }
        }

        // Calculate the total length of the combined byte array
        var totalLength = 0
        for (byteArray in resultArray) {
            totalLength += byteArray.size
        }

        // Combine the byte arrays into a single byte array
        val combinedBytes = ByteArray(totalLength)
        var offset = 0
        for (byteArray in resultArray) {
            System.arraycopy(byteArray, 0, combinedBytes, offset, byteArray.size)
            offset += byteArray.size
        }

        return WhisperResult(String(combinedBytes, StandardCharsets.UTF_8), language, task)
    }
}
