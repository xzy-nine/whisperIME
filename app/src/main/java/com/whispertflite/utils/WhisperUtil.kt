package com.whispertflite.utils

import android.util.Log
import java.io.IOException
import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.Int
import kotlin.String
import kotlin.Throws
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.run

class WhisperUtil {
    private val vocab = WhisperVocab()
    private val filters = WhisperFilter()
    private val mel = WhisperMel()

    val tokenTranslate: Int
        // Helper functions definitions
        get() = vocab.tokenTRANSLATE

    val tokenTranscribe: Int
        get() = vocab.tokenTRANSCRIBE

    val tokenEOT: Int
        get() = vocab.tokenEOT

    val tokenSOT: Int
        get() = vocab.tokenSOT

    val tokenPREV: Int
        get() = vocab.tokenPREV

    val tokenSOLM: Int
        get() = vocab.tokenSOLM

    val tokenNOT: Int
        get() = vocab.tokenNOT

    val tokenBEG: Int
        get() = vocab.tokenBEG

    fun getWordFromToken(token: Int): ByteArray? {
        return vocab.tokenToWord.get(token)
    }

    // Load filters and vocab data from pre-generated filters_vocab_en.bin file
    @Throws(IOException::class)
    fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String?): Boolean {
        // Read vocab file

        val bytes = Files.readAllBytes(Paths.get(vocabPath))
        val vocabBuf = ByteBuffer.wrap(bytes)
        vocabBuf.order(ByteOrder.nativeOrder())
        Log.d(TAG, "Vocab file size: " + vocabBuf.limit())

        // @magic:USEN
        val magic = vocabBuf.getInt()
        if (magic == 0x5553454e) {
            Log.d(TAG, "Magic number: " + magic)
        } else {
            Log.d(TAG, "Invalid vocab file (bad magic: " + magic + "), " + vocabPath)
            return false
        }

        // Load mel filters
        filters.nMel = vocabBuf.getInt()
        filters.nFft = vocabBuf.getInt()
        Log.d(TAG, "n_mel:" + filters.nMel + ", n_fft:" + filters.nFft)

        val filterData = ByteArray(filters.nMel * filters.nFft * Float.BYTES)
        vocabBuf.get(filterData, 0, filterData.size)
        val filterBuf = ByteBuffer.wrap(filterData)
        filterBuf.order(ByteOrder.nativeOrder())

        filters.data = FloatArray(filters.nMel * filters.nFft)
        run {
            var i = 0
            while (filterBuf.hasRemaining()) {
                filters.data[i] = filterBuf.getFloat()
                i++
            }
        }

        // Load vocabulary
        val nVocab = vocabBuf.getInt()
        Log.d(TAG, "nVocab: " + nVocab)
        for (i in 0..<nVocab) {
            val len = vocabBuf.getInt()
            val wordBytes = ByteArray(len)
            vocabBuf.get(wordBytes, 0, wordBytes.size)
            vocab.tokenToWord.put(i, wordBytes)
        }

        // Add additional vocab ids
        val nVocabAdditional: Int
        if (!multilingual) {
            nVocabAdditional = vocab.nVocabEnglish
        } else {
            nVocabAdditional = vocab.nVocabMultilingual
            vocab.tokenEOT++
            vocab.tokenSOT++
            vocab.tokenPREV++
            vocab.tokenSOLM++
            vocab.tokenNOT++
            vocab.tokenBEG++
        }

        for (i in nVocab..<nVocabAdditional) {
            val word: String?
            if (i > vocab.tokenBEG) {
                word = "[_TT_" + (i - vocab.tokenBEG) + "]"
            } else if (i == vocab.tokenEOT) {
                word = "[_EOT_]"
            } else if (i == vocab.tokenSOT) {
                word = "[_SOT_]"
            } else if (i == vocab.tokenPREV) {
                word = "[_PREV_]"
            } else if (i == vocab.tokenNOT) {
                word = "[_NOT_]"
            } else if (i == vocab.tokenBEG) {
                word = "[_BEG_]"
            } else {
                word = "[_extra_token_" + i + "]"
            }

            vocab.tokenToWord.put(i, word.toByteArray(StandardCharsets.UTF_8))
            //Log.d(TAG, "i= " + i + ", word= " + word);
        }

        return true
    }

    // nSamples size => WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE => 480000
    // meaningfulSamples is the part which contains recorded data
    fun getMelSpectrogram(
        samples: FloatArray,
        nSamples: Int,
        meaningfulSamples: Int,
        nThreads: Int
    ): FloatArray {
        val fftSize: Int = WHISPER_N_FFT
        val fftStep: Int = WHISPER_HOP_LENGTH

        mel.nMel = WHISPER_N_MEL
        mel.nLen = nSamples / fftStep
        mel.data = FloatArray(mel.nMel * mel.nLen)

        // Calculate the number of meaningful frames
        val meaningfulFrames = meaningfulSamples / fftStep

        val hann = FloatArray(fftSize)
        for (i in 0..<fftSize) {
            hann[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()
        }

        val nFft = 1 + fftSize / 2

        // Calculate mel values using multiple threads
        val workers: MutableList<Thread> = ArrayList<Thread>()
        for (iw in 0..<nThreads) {
            val ith = iw // Capture iw in a final variable for use in the lambda
            val thread = Thread(Runnable {
                // Inside the thread, ith will have the same value as iw (first value is 0)
                Log.d(TAG, "Thread " + ith + " started.")

                val fftIn = FloatArray(fftSize)
                Arrays.fill(fftIn, 0.0f)
                val fftOut = FloatArray(fftSize * 2)

                run {
                    var i = ith
                    while (i < meaningfulFrames) {
                        // Limit to meaningful frames
                        val offset = i * fftStep

                        // apply Hanning window
                        for (j in 0..<fftSize) {
                            if (offset + j < meaningfulSamples) { // Limit to meaningful samples
                                fftIn[j] = hann[j] * samples[offset + j]
                            } else {
                                fftIn[j] = 0.0f
                            }
                        }

                        // FFT -> mag^2
                        fft(fftIn, fftOut)
                        for (j in 0..<fftSize) {
                            fftOut[j] =
                                fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
                        }

                        for (j in 1..<fftSize / 2) {
                            fftOut[j] += fftOut[fftSize - j]
                        }

                        // mel spectrogram
                        for (j in 0..<mel.nMel) {
                            var sum = 0.0
                            for (k in 0..<nFft) {
                                sum += (fftOut[k] * filters.data[j * nFft + k]).toDouble()
                            }

                            if (sum < 1e-10) {
                                sum = 1e-10
                            }

                            sum = log10(sum)
                            mel.data[j * mel.nLen + i] = sum.toFloat()
                        }
                        i += nThreads
                    }
                }

                // Pad the remaining frames with a default value (e.g., -8.0)
                var i = ith + meaningfulFrames
                while (i < mel.nLen) {
                    for (j in 0..<mel.nMel) {
                        mel.data[j * mel.nLen + i] = -8.0f // Default padding value
                    }
                    i += nThreads
                }
            })
            workers.add(thread)
            thread.start()
        }

        // Wait for all threads to finish
        for (worker in workers) {
            try {
                worker.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // clamping and normalization
        var mmax = -1e20
        for (i in 0..<mel.nMel * mel.nLen) {
            if (mel.data[i] > mmax) {
                mmax = mel.data[i].toDouble()
            }
        }

        mmax -= 8.0
        for (i in 0..<mel.nMel * mel.nLen) {
            if (mel.data[i] < mmax) {
                mel.data[i] = mmax.toFloat()
            }
            mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
        }

        return mel.data
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0..<inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0..<inSize) {
                val angle = (2 * Math.PI * k * n / inSize).toFloat()
                re += (input[n] * cos(angle.toDouble())).toFloat()
                im -= (input[n] * sin(angle.toDouble())).toFloat()
            }
            output[k * 2 + 0] = re
            output[k * 2 + 1] = im
        }
    }

    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }

        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)

        var indxEven = 0
        var indxOdd = 0
        for (i in 0..<inSize) {
            if (i % 2 == 0) {
                even[indxEven] = input[i]
                indxEven++
            } else {
                odd[indxOdd] = input[i]
                indxOdd++
            }
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)

        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0..<inSize / 2) {
            val theta = (2 * Math.PI * k / inSize).toFloat()
            val re = cos(theta.toDouble()).toFloat()
            val im = -sin(theta.toDouble()).toFloat()
            val reOdd = oddFft[2 * k + 0]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }

    // Helper class definitions
    private class WhisperVocab {
        // Token types
        var tokenEOT: Int = 50256 // end of transcript
        var tokenSOT: Int = 50257 // start of transcript
        var tokenPREV: Int = 50360
        var tokenSOLM: Int = 50361 // ??
        var tokenNOT: Int = 50362 // no timestamps
        var tokenBEG: Int = 50363

        // Available tasks
        val tokenTRANSLATE: Int = 50358
        val tokenTRANSCRIBE: Int = 50359

        // Vocab types
        val nVocabEnglish: Int = 51864 // for english only vocab
        val nVocabMultilingual: Int = 51865 // for multilingual vocab
        var tokenToWord: MutableMap<Int?, ByteArray?> = HashMap<Int?, ByteArray?>()
    }

    private class WhisperFilter {
        var nMel: Int = 0
        var nFft: Int = 0
        lateinit var data: FloatArray
    }

    private class WhisperMel {
        var nLen: Int = 0
        var nMel: Int = 0
        lateinit var data: FloatArray
    }

    companion object {
        private const val TAG = "WhisperUtil"

        const val WHISPER_SAMPLE_RATE: Int = 16000
        const val WHISPER_N_FFT: Int = 400
        const val WHISPER_N_MEL: Int = 80
        const val WHISPER_HOP_LENGTH: Int = 160
        const val WHISPER_CHUNK_SIZE: Int = 30
    }
}
