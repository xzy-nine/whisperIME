package com.whispertflite.viewmodel

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import com.whispertflite.utils.Downloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    val downloadProgress = MutableStateFlow(0)
    val downloadSizeText = MutableStateFlow("")
    val isDownloading = MutableStateFlow(false)
    val showStartButton = MutableStateFlow(false)
    val showUpdateButton = MutableStateFlow(false)
    val downloadEnabled = MutableStateFlow(true)

    val mirrorSources = Downloader.MirrorSource.entries.toList()
    val selectedMirror = MutableStateFlow(Downloader.getMirrorSource(application))

    fun onMirrorSelected(source: Downloader.MirrorSource) {
        selectedMirror.value = source
        Downloader.setMirrorSource(getApplication(), source)
    }

    fun checkModels(activity: Activity): Boolean {
        val ready = Downloader.checkModels(activity)
        if (ready) {
            downloadProgress.value = 100
            showStartButton.value = true
            if (Downloader.checkUpdate(activity)) {
                showUpdateButton.value = true
            }
        }
        return ready
    }

    fun startDownload(activity: Activity) {
        isDownloading.value = true
        downloadEnabled.value = false
        showStartButton.value = false
        showUpdateButton.value = false
        downloadProgress.value = 0
        downloadSizeText.value = ""

        Downloader.downloadModels(
            activity = activity,
            onProgress = { bytes, percent ->
                downloadProgress.value = percent
                downloadSizeText.value = "${bytes / 1024 / 1024} MB"
            },
            onModelReady = {
                downloadProgress.value = 100
                isDownloading.value = false
                downloadEnabled.value = true
                showStartButton.value = true
            },
            onError = {
                isDownloading.value = false
                downloadEnabled.value = true
            }
        )
    }

    fun startUpdate(activity: Activity) {
        Downloader.deleteOldModels(activity)
        startDownload(activity)
    }
}
