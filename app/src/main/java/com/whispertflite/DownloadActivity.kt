package com.whispertflite

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.whispertflite.ui.DownloadScreen
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.utils.Downloader
import com.whispertflite.viewmodel.DownloadViewModel

class DownloadActivity : AppCompatActivity() {
    private lateinit var viewModel: DownloadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        setContent {
            WhisperTheme {
                DownloadScreen(
                    viewModel = viewModel,
                    onStartMain = { startMain() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.checkModels(this)) {
            if (!Downloader.checkUpdate(this)) {
                startMain()
            }
        }
    }

    private fun startMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
