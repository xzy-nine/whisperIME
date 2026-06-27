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
    const val modelMultiLingualBaseOLD: String =
        "whisper-base.tflite" //Todo Remove ...OLD... stuff later
    const val modelMultiLingualBaseOLD2: String =
        "whisper-base.EUROPEAN_UNION.tflite" //Todo Remove ...OLD... stuff later
    const val modelMultiLingualBase: String = "whisper-base.TOP_WORLD.tflite"
    const val modelMultiLingualSmallOLD: String = "whisper-small.tflite"
    const val modelMultiLingualSmall: String = "whisper-small.TOP_WORLD.tflite"
    const val modelEnglishOnly: String = "whisper-tiny.en.tflite"
    const val modelMultiLingualBaseURL: String =
        "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-base.TOP_WORLD.tflite"
    const val modelMultiLingualSmallURL: String =
        "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small.TOP_WORLD.tflite"
    const val modelEnglishOnlyURL: String =
        "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-tiny.en.tflite"
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
            Log.d("WhisperASR", "multi-lingual base model file does not exist")
            val thread = Thread(Runnable {
                try {
                    val url: URL?

                    url = URL(modelMultiLingualBaseURL)

                    Log.d("WhisperASR", "Download model")

                    val ucon = url.openConnection()
                    ucon.setReadTimeout(5000)
                    ucon.setConnectTimeout(10000)

                    val `is` = ucon.getInputStream()
                    val inStream = BufferedInputStream(`is`, 1024 * 5)

                    modelMultiLingualBaseFile.createNewFile()

                    val outStream = FileOutputStream(modelMultiLingualBaseFile)
                    val buff = ByteArray(5 * 1024)

                    var len: Int
                    while ((inStream.read(buff).also { len = it }) != -1) {
                        outStream.write(buff, 0, len)
                        if (modelMultiLingualBaseFile.exists()) downloadModelMultiLingualBaseSize =
                            modelMultiLingualBaseFile.length()
                        activity.runOnUiThread(Runnable {
                            binding.downloadSize.setText(((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize) / 1024 / 1024).toString() + " MB")
                            binding.downloadProgress.setProgress((((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize).toDouble() / (modelEnglishOnlySize + modelMultiLingualSmallSize + modelMultiLingualBaseSize)) * 100).toInt())
                        })
                    }
                    outStream.flush()
                    outStream.close()
                    inStream.close()
                    var calcModelMultiLingualBaseMD5 = ""
                    if (modelMultiLingualBaseFile.exists()) {
                        calcModelMultiLingualBaseMD5 =
                            calculateMD5(Paths.get(modelMultiLingualBaseFile.getPath()).toString())
                    } else {
                        throw IOException() //throw exception if there is no modelMultiLingualSmallFile at this point
                    }

                    if (!(calcModelMultiLingualBaseMD5 == modelMultiLingualBaseMD5)) {
                        modelMultiLingualBaseFile.delete()
                        modelMultiLingualBaseFinished = false
                        activity.runOnUiThread(Runnable {
                            Toast.makeText(
                                activity,
                                activity.getResources().getString(R.string.error_download),
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.downloadButton.setEnabled(true)
                        })
                    } else {
                        modelMultiLingualBaseFinished = true
                        activity.runOnUiThread(Runnable {
                            if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                                View.VISIBLE
                            )
                        })
                    }
                } catch (i: NoSuchAlgorithmException) {
                    modelMultiLingualBaseFile.delete()
                    modelMultiLingualBaseFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                } catch (i: IOException) {
                    modelMultiLingualBaseFile.delete()
                    modelMultiLingualBaseFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                }
            })
            thread.start()
        } else {
            downloadModelMultiLingualBaseSize = modelMultiLingualBaseSize
            modelMultiLingualBaseFinished = true
            activity.runOnUiThread(Runnable {
                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                    View.VISIBLE
                )
            })
        }

        val modelMultiLingualSmallFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelMultiLingualSmall)
        if (!modelMultiLingualSmallFile.exists()) {
            modelMultiLingualSmallFinished = false
            Log.d("WhisperASR", "multi-lingual small model file does not exist")
            val thread = Thread(Runnable {
                try {
                    val url: URL?

                    url = URL(modelMultiLingualSmallURL)

                    Log.d("WhisperASR", "Download model")

                    val ucon = url.openConnection()
                    ucon.setReadTimeout(5000)
                    ucon.setConnectTimeout(10000)

                    val `is` = ucon.getInputStream()
                    val inStream = BufferedInputStream(`is`, 1024 * 5)

                    modelMultiLingualSmallFile.createNewFile()

                    val outStream = FileOutputStream(modelMultiLingualSmallFile)
                    val buff = ByteArray(5 * 1024)

                    var len: Int
                    while ((inStream.read(buff).also { len = it }) != -1) {
                        outStream.write(buff, 0, len)
                        if (modelMultiLingualSmallFile.exists()) downloadModelMultiLingualSmallSize =
                            modelMultiLingualSmallFile.length()
                        activity.runOnUiThread(Runnable {
                            binding.downloadSize.setText(((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize) / 1024 / 1024).toString() + " MB")
                            binding.downloadProgress.setProgress((((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize).toDouble() / (modelEnglishOnlySize + modelMultiLingualSmallSize + modelMultiLingualBaseSize)) * 100).toInt())
                        })
                    }
                    outStream.flush()
                    outStream.close()
                    inStream.close()
                    var calcModelMultiLingualSmallMD5 = ""
                    if (modelMultiLingualSmallFile.exists()) {
                        calcModelMultiLingualSmallMD5 =
                            calculateMD5(Paths.get(modelMultiLingualSmallFile.getPath()).toString())
                    } else {
                        throw IOException() //throw exception if there is no modelMultiLingualSmallFile at this point
                    }

                    if (!(calcModelMultiLingualSmallMD5 == modelMultiLingualSmallMD5)) {
                        modelMultiLingualSmallFile.delete()
                        modelMultiLingualSmallFinished = false
                        activity.runOnUiThread(Runnable {
                            Toast.makeText(
                                activity,
                                activity.getResources().getString(R.string.error_download),
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.downloadButton.setEnabled(true)
                        })
                    } else {
                        modelMultiLingualSmallFinished = true
                        activity.runOnUiThread(Runnable {
                            if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                                View.VISIBLE
                            )
                        })
                    }
                } catch (i: NoSuchAlgorithmException) {
                    modelMultiLingualSmallFile.delete()
                    modelMultiLingualSmallFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                } catch (i: IOException) {
                    modelMultiLingualSmallFile.delete()
                    modelMultiLingualSmallFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                }
            })
            thread.start()
        } else {
            downloadModelMultiLingualSmallSize = modelMultiLingualSmallSize
            modelMultiLingualSmallFinished = true
            activity.runOnUiThread(Runnable {
                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                    View.VISIBLE
                )
            })
        }

        val modelEnglishOnlyFile =
            File(activity.getExternalFilesDir(null).toString() + "/" + modelEnglishOnly)
        if (!modelEnglishOnlyFile.exists()) {
            modelEnglishOnlyFinished = false
            Log.d("WhisperASR", "English only model file does not exist")
            val thread = Thread(Runnable {
                try {
                    val url = URL(modelEnglishOnlyURL)
                    Log.d("WhisperASR", "Download English only model")

                    val ucon = url.openConnection()
                    ucon.setReadTimeout(5000)
                    ucon.setConnectTimeout(10000)

                    val `is` = ucon.getInputStream()
                    val inStream = BufferedInputStream(`is`, 1024 * 5)

                    modelEnglishOnlyFile.createNewFile()

                    val outStream = FileOutputStream(modelEnglishOnlyFile)
                    val buff = ByteArray(5 * 1024)

                    var len: Int
                    while ((inStream.read(buff).also { len = it }) != -1) {
                        outStream.write(buff, 0, len)
                        if (modelEnglishOnlyFile.exists()) downloadModelEnglishOnlySize =
                            modelEnglishOnlyFile.length()
                        activity.runOnUiThread(Runnable {
                            binding.downloadSize.setText(((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize) / 1024 / 1024).toString() + " MB")
                            binding.downloadProgress.setProgress((((downloadModelEnglishOnlySize + downloadModelMultiLingualSmallSize + downloadModelMultiLingualBaseSize).toDouble() / (modelEnglishOnlySize + modelMultiLingualSmallSize + modelMultiLingualBaseSize)) * 100).toInt())
                        })
                    }
                    outStream.flush()
                    outStream.close()
                    inStream.close()

                    var calcEnglishOnlyModelMD5 = ""
                    if (modelEnglishOnlyFile.exists()) {
                        calcEnglishOnlyModelMD5 =
                            calculateMD5(Paths.get(modelEnglishOnlyFile.getPath()).toString())
                    } else {
                        throw IOException() //throw exception if there is no modelMultiLingualSmallFile at this point
                    }

                    if (calcEnglishOnlyModelMD5 != modelEnglishOnlyMD5) {
                        modelEnglishOnlyFile.delete()
                        modelEnglishOnlyFinished = false
                        activity.runOnUiThread(Runnable {
                            Toast.makeText(
                                activity,
                                activity.getResources().getString(R.string.error_download),
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.downloadButton.setEnabled(true)
                        })
                    } else {
                        modelEnglishOnlyFinished = true
                        activity.runOnUiThread(Runnable {
                            if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                                View.VISIBLE
                            )
                        })
                    }
                } catch (i: NoSuchAlgorithmException) {
                    modelEnglishOnlyFile.delete()
                    modelEnglishOnlyFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                } catch (i: IOException) {
                    modelEnglishOnlyFile.delete()
                    modelEnglishOnlyFinished = false
                    activity.runOnUiThread(Runnable {
                        Toast.makeText(
                            activity,
                            activity.getResources().getString(R.string.error_download),
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.downloadButton.setEnabled(true)
                    })
                    Log.w(
                        "WhisperASR",
                        activity.getResources().getString(R.string.error_download),
                        i
                    )
                }
            })
            thread.start()
        } else {
            downloadModelEnglishOnlySize = modelEnglishOnlySize
            modelEnglishOnlyFinished = true
            activity.runOnUiThread(Runnable {
                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished) binding.buttonStart.setVisibility(
                    View.VISIBLE
                )
            })
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