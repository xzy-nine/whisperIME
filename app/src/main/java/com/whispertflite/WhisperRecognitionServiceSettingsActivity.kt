package com.whispertflite

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.whispertflite.ui.SettingsScreen
import com.whispertflite.ui.theme.WhisperTheme
import com.whispertflite.utils.Downloader
import com.whispertflite.viewmodel.SettingsViewModel

class WhisperRecognitionServiceSettingsActivity : AppCompatActivity() {
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (!Downloader.checkModels(this)) {
            startActivity(Intent(this, DownloadActivity::class.java))
            finish()
            return
        }

        setContent {
            WhisperTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
