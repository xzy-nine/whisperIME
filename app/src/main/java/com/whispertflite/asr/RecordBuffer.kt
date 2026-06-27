package com.whispertflite.asr

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object RecordBuffer {
    // Synchronized method to set the byte array
    // Synchronized method to get the byte array
    // Static variable to store the byte array
    @get:Synchronized
    @set:Synchronized
    var outputBuffer: ByteArray? = null

    @JvmStatic
    val samples: FloatArray
        get() {
            val numSamples = outputBuffer!!.size / 2
            val byteBuffer =
                ByteBuffer.wrap(outputBuffer)
            byteBuffer.order(ByteOrder.nativeOrder())

            // Convert audio data to PCM_FLOAT format
            val samples = FloatArray(numSamples)
            var maxAbsValue = 0.0f

            for (i in 0..<numSamples) {
                samples[i] = (byteBuffer.getShort() / 32768.0).toFloat()
                // Track the maximum absolute value
                if (abs(samples[i]) > maxAbsValue) {
                    maxAbsValue = abs(samples[i])
                }
            }

            // Normalize the samples
            if (maxAbsValue > 0.0f) {
                for (i in 0..<numSamples) {
                    samples[i] /= maxAbsValue
                }
            }

            return samples
        }
}
