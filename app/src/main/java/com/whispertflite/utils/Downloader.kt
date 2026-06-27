package com.whispertflite.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.whispertflite.R
import com.whispertflite.databinding.ActivityDownloadBinding
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object Downloader {
    enum class MirrorSource(val labelResId: Int, val baseUrl: String) {
        HUGGINGFACE(R.string.mirror_huggingface, "https://huggingface.co"),
        HF_MIRROR(R.string.mirror_hf_mirror, "https://hf-mirror.com"),
        SJTU_MIRROR(R.string.mirror_sjtu, "https://mirror.sjtu.edu.cn/huggingface");

        fun getDisplayName(context: Context): String = context.getString(labelResId)
    }

    private const val MODEL_REPO_PATH = "/DocWolle/whisper_tflite_models/resolve/main/"
    private const val PREF_MIRROR_SOURCE = "pref_mirror_source"
    private const val TAG = "WhisperASR"

    const val modelMultiLingualBaseOLD: String =
        "whisper-base.tflite" //Todo Remove ...OLD... stuff later
    const val modelMultiLingualBaseOLD2: String =
        "whisper-base.EUROPEAN_UNION.tflite" //Todo Remove ...OLD... stuff later
    const val modelMultiLingualBase: String = "whisper-base.TOP_WORLD.tflite"
    const val modelMultiLingualSmallOLD: String = "whisper-small.tflite"
    const val modelMultiLingualSmall: String = "whisper-small.TOP_WORLD.tflite"
    const val modelEnglishOnly: String = "whisper-tiny.en.tflite"
    const val modelMultiLingualBaseOLDMD5: String = "4b4fddfac6a24ffecc4972bc2137ba04"
    const val modelMultiLingualBaseOLD2MD5: String = "82adc0d42761f6d83fecd76d0325bcf5"
    const val modelMultiLingualBaseMD5: String = "9e43f385a916ac4b2e48760ce1fa70fc"
    const val modelMultiLingualSmallOLDMD5: String = "c4f948b3b42e7536bcedf78eec9481a6"
    const val modelMultiLingualSmallMD5: String = "d3badbb86c9bcc7312c19167acac7133"
    const val modelEnglishOnlyMD5: String = "2e745cdd5dfe2f868f47caa7a199f91a"
    const val modelMultiLingualBaseSize: Long = 107564368
    const val modelMultiLingualSmallSize: Long = 307408944
    const val modelEnglishOnlySize: Long = 41486616
    var downloadModelMultiLingualBaseSize: Long = 0L
    var downloadModelMultiLingualSmallSize: Long = 0L
    var downloadModelEnglishOnlySize: Long = 0L
    var modelMultiLingualBaseFinished: Boolean = false
    var modelEnglishOnlyFinished: Boolean = false
    var modelMultiLingualSmallFinished: Boolean = false

    fun getMirrorSource(context: Context): MirrorSource {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val name = sp.getString(PREF_MIRROR_SOURCE, MirrorSource.HUGGINGFACE.name)
            ?: MirrorSource.HUGGINGFACE.name
        return try {
            MirrorSource.valueOf(name)
        } catch (e: IllegalArgumentException) {
            MirrorSource.HUGGINGFACE
        }
    }

    fun setMirrorSource(context: Context, source: MirrorSource) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_MIRROR_SOURCE, source.name)
            .apply()
    }

    private fun getMirrorTryOrder(preferred: MirrorSource): List<MirrorSource> {
        val ordered = mutableListOf(preferred)
        for (m in MirrorSource.values()) {
            if (m != preferred) ordered.add(m)
        }
        return ordered
    }

    fun checkUpdate(activity: Activity): Boolean {
        val modelMultiLingualBaseFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBase)
        val modelMultiLingualSmallFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmall)
        if (!modelMultiLingualBaseFile.exists() || !modelMultiLingualSmallFile.exists()) {
            return true //update available
        } else {
            return false //no update
        }
    }

    @JvmStatic
    fun checkModels(activity: Activity): Boolean {
        copyAssetsToSdcard(activity)
        val modelMultiLingualBaseFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBase)
        val modelMultiLingualBaseOLDFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBaseOLD)
        val modelMultiLingualBaseOLD2File =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBaseOLD2)
        val modelMultiLingualSmallOLDFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmallOLD)
        val modelMultiLingualSmallFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmall)
        val modelEnglishOnlyFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelEnglishOnly)
        var calcModelMultiLingualBaseMD5 = ""
        var calcModelMultiLingualBaseOLDMD5 = ""
        var calcModelMultiLingualBaseOLD2MD5 = ""
        var calcModelMultiLingualSmallMD5 = ""
        var calcModelMultiLingualSmallOLDMD5 = ""
        var calcModelEnglishOnlyMD5 = ""
        if (modelMultiLingualBaseFile.exists()) {
            try {
                calcModelMultiLingualBaseMD5 =
                    calculateMD5(Paths.get(modelMultiLingualBaseFile.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }
        if (modelMultiLingualBaseOLDFile.exists()) {
            try {
                calcModelMultiLingualBaseOLDMD5 =
                    calculateMD5(Paths.get(modelMultiLingualBaseOLDFile.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }
        if (modelMultiLingualBaseOLD2File.exists()) {
            try {
                calcModelMultiLingualBaseOLD2MD5 =
                    calculateMD5(Paths.get(modelMultiLingualBaseOLD2File.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }
        if (modelMultiLingualSmallFile.exists()) {
            try {
                calcModelMultiLingualSmallMD5 =
                    calculateMD5(Paths.get(modelMultiLingualSmallFile.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }
        if (modelMultiLingualSmallOLDFile.exists()) {
            try {
                calcModelMultiLingualSmallOLDMD5 =
                    calculateMD5(Paths.get(modelMultiLingualSmallOLDFile.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }
        if (modelEnglishOnlyFile.exists()) {
            try {
                calcModelEnglishOnlyMD5 =
                    calculateMD5(Paths.get(modelEnglishOnlyFile.getPath()).toString())
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }

        if (modelMultiLingualBaseOLDFile.exists() && !(calcModelMultiLingualBaseOLDMD5 == modelMultiLingualBaseOLDMD5)) {
            modelMultiLingualBaseOLDFile.delete()
        }
        if (modelMultiLingualBaseOLD2File.exists() && !(calcModelMultiLingualBaseOLD2MD5 == modelMultiLingualBaseOLD2MD5)) {
            modelMultiLingualBaseOLD2File.delete()
        }
        if (modelMultiLingualSmallOLDFile.exists() && !(calcModelMultiLingualSmallOLDMD5 == modelMultiLingualSmallOLDMD5)) {
            modelMultiLingualSmallOLDFile.delete()
        }
        if (modelMultiLingualBaseFile.exists() && !(calcModelMultiLingualBaseMD5 == modelMultiLingualBaseMD5)) {
            modelMultiLingualBaseFile.delete()
            modelMultiLingualBaseFinished = false
        }
        if (modelMultiLingualSmallFile.exists() && !(calcModelMultiLingualSmallMD5 == modelMultiLingualSmallMD5)) {
            modelMultiLingualSmallFile.delete()
            modelMultiLingualSmallFinished = false
        }
        if (modelEnglishOnlyFile.exists() && calcModelEnglishOnlyMD5 != modelEnglishOnlyMD5) {
            modelEnglishOnlyFile.delete()
            modelEnglishOnlyFinished = false
        }

        return (calcModelMultiLingualSmallMD5 == modelMultiLingualSmallMD5 || calcModelMultiLingualSmallOLDMD5 == modelMultiLingualSmallOLDMD5) && (calcModelMultiLingualBaseMD5 == modelMultiLingualBaseMD5 || calcModelMultiLingualBaseOLDMD5 == modelMultiLingualBaseOLDMD5 || calcModelMultiLingualBaseOLD2MD5 == modelMultiLingualBaseOLD2MD5) && calcModelEnglishOnlyMD5 == modelEnglishOnlyMD5
    }

    fun deleteOldModels(activity: Activity) {
        val modelMultiLingualBaseOLDFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBaseOLD)
        if (modelMultiLingualBaseOLDFile.exists()) modelMultiLingualBaseOLDFile.delete()
        val modelMultiLingualBaseOLD2File =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBaseOLD2)
        if (modelMultiLingualBaseOLD2File.exists()) modelMultiLingualBaseOLD2File.delete()
        val modelMultiLingualSmallOLDFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmallOLD)
        if (modelMultiLingualSmallOLDFile.exists()) modelMultiLingualSmallOLDFile.delete()
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        sp.edit().remove("modelName").apply()
        sp.edit().remove("recognitionServiceModelName").apply()
    }

    fun downloadModels(activity: Activity, binding: ActivityDownloadBinding) {
        checkModels(activity)

        binding.downloadProgress.setProgress(0)
        binding.downloadButton.setEnabled(false)

        val modelMultiLingualBaseFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualBase)
        if (!modelMultiLingualBaseFile.exists()) {
            modelMultiLingualBaseFinished = false
            Log.d(TAG, "multi-lingual base model file does not exist")
            downloadSingleModel(
                activity = activity,
                binding = binding,
                modelFile = modelMultiLingualBaseFile,
                fileName = modelMultiLingualBase,
                expectedMD5 = modelMultiLingualBaseMD5,
                onProgress = { size ->
                    downloadModelMultiLingualBaseSize = size
                    activity.runOnUiThread { updateProgressUI(binding) }
                },
                onSuccess = {
                    modelMultiLingualBaseFinished = true
                    activity.runOnUiThread { showStartIfAllReady(binding) }
                }
            )
        } else {
            downloadModelMultiLingualBaseSize = modelMultiLingualBaseSize
            modelMultiLingualBaseFinished = true
            activity.runOnUiThread { showStartIfAllReady(binding) }
        }

        val modelMultiLingualSmallFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmall)
        if (!modelMultiLingualSmallFile.exists()) {
            modelMultiLingualSmallFinished = false
            Log.d(TAG, "multi-lingual small model file does not exist")
            downloadSingleModel(
                activity = activity,
                binding = binding,
                modelFile = modelMultiLingualSmallFile,
                fileName = modelMultiLingualSmall,
                expectedMD5 = modelMultiLingualSmallMD5,
                onProgress = { size ->
                    downloadModelMultiLingualSmallSize = size
                    activity.runOnUiThread { updateProgressUI(binding) }
                },
                onSuccess = {
                    modelMultiLingualSmallFinished = true
                    activity.runOnUiThread { showStartIfAllReady(binding) }
                }
            )
        } else {
            downloadModelMultiLingualSmallSize = modelMultiLingualSmallSize
            modelMultiLingualSmallFinished = true
            activity.runOnUiThread { showStartIfAllReady(binding) }
        }

        val modelEnglishOnlyFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelEnglishOnly)
        if (!modelEnglishOnlyFile.exists()) {
            modelEnglishOnlyFinished = false
            Log.d(TAG, "English only model file does not exist")
            downloadSingleModel(
                activity = activity,
                binding = binding,
                modelFile = modelEnglishOnlyFile,
                fileName = modelEnglishOnly,
                expectedMD5 = modelEnglishOnlyMD5,
                onProgress = { size ->
                    downloadModelEnglishOnlySize = size
                    activity.runOnUiThread { updateProgressUI(binding) }
                },
                onSuccess = {
                    modelEnglishOnlyFinished = true
                    activity.runOnUiThread { showStartIfAllReady(binding) }
                }
            )
        } else {
            downloadModelEnglishOnlySize = modelEnglishOnlySize
            modelEnglishOnlyFinished = true
            activity.runOnUiThread { showStartIfAllReady(binding) }
        }
    }

    private fun downloadSingleModel(
        activity: Activity,
        binding: ActivityDownloadBinding,
        modelFile: File,
        fileName: String,
        expectedMD5: String,
        onProgress: (Long) -> Unit,
        onSuccess: () -> Unit
    ) {
        val preferredMirror = getMirrorSource(activity)
        val mirrors = getMirrorTryOrder(preferredMirror)
        val errorMsg = activity.getString(R.string.error_download)

        Thread(Runnable {
            for (mirror in mirrors) {
                try {
                    val urlString = "${mirror.baseUrl}$MODEL_REPO_PATH$fileName"
                    Log.d(TAG, "Downloading $fileName from ${mirror.getDisplayName(activity)}: $urlString")

                    val url = URL(urlString)
                    val ucon = url.openConnection()
                    ucon.setReadTimeout(5000)
                    ucon.setConnectTimeout(10000)

                    val inputStream = ucon.getInputStream()
                    val inStream = BufferedInputStream(inputStream, 1024 * 5)

                    modelFile.createNewFile()
                    val outStream = FileOutputStream(modelFile)
                    val buff = ByteArray(5 * 1024)
                    var len: Int
                    while ((inStream.read(buff).also { len = it }) != -1) {
                        outStream.write(buff, 0, len)
                        if (modelFile.exists()) onProgress(modelFile.length())
                    }
                    outStream.flush()
                    outStream.close()
                    inStream.close()

                    var calcMD5 = ""
                    if (modelFile.exists()) {
                        calcMD5 = calculateMD5(modelFile.path)
                    } else {
                        throw IOException("File does not exist after download")
                    }

                    if (calcMD5 == expectedMD5) {
                        setMirrorSource(activity, mirror)
                        onSuccess()
                        return@Runnable
                    } else {
                        modelFile.delete()
                        Log.w(TAG, "MD5 mismatch for $fileName from ${mirror.getDisplayName(activity)}")
                    }
                } catch (e: NoSuchAlgorithmException) {
                    modelFile.delete()
                    Log.w(TAG, "Download failed for $fileName from ${mirror.getDisplayName(activity)}", e)
                } catch (e: IOException) {
                    modelFile.delete()
                    Log.w(TAG, "Download failed for $fileName from ${mirror.getDisplayName(activity)}", e)
                }
            }

            activity.runOnUiThread {
                Toast.makeText(activity, errorMsg, Toast.LENGTH_SHORT).show()
                binding.downloadButton.setEnabled(true)
            }
        }).start()
    }

    private fun updateProgressUI(binding: ActivityDownloadBinding) {
        val total = downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize
        binding.downloadSize.setText("${total / 1024 / 1024} MB")
        binding.downloadProgress.setProgress(((total.toDouble() / (modelEnglishOnlySize + modelMultiLingualSmallSize + modelMultiLingualBaseSize)) * 100).toInt())
    }

    private fun showStartIfAllReady(binding: ActivityDownloadBinding) {
        if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) {
            binding.buttonStart.setVisibility(View.VISIBLE)
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun calculateMD5(filePath: String?): String {
        val md = MessageDigest.getInstance("MD5")
        BufferedInputStream(FileInputStream(filePath)).use { `is` ->
            val buffer = ByteArray(8192) // 8KB buffer
            var bytesRead: Int
            while ((`is`.read(buffer).also { bytesRead = it }) != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        val hash = md.digest()
        return BigInteger(1, hash).toString(16)
    }

    // Copy assets to destination folder
    fun copyAssetsToSdcard(context: Context) {
        val extensions = arrayOf<String?>("bin")
        val sdcardDataFolder = context.getExternalFilesDir(null)
        val assetManager = context.getAssets()

        try {
            // List all files in the assets folder once
            val assetFiles = assetManager.list("")
            if (assetFiles == null) return

            for (assetFileName in assetFiles) {
                // Check if file matches any of the provided extensions
                for (extension in extensions) {
                    if (assetFileName.endsWith("." + extension)) {
                        val outFile = File(sdcardDataFolder, assetFileName)

                        // Skip if file already exists
                        if (outFile.exists()) break

                        assetManager.open(assetFileName).use { inputStream ->
                            FileOutputStream(outFile).use { outputStream ->
                                val buffer = ByteArray(1024)
                                var bytesRead: Int
                                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        break // No need to check further extensions
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
