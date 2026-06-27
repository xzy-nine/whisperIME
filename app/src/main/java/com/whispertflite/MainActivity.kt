package com.whispertflite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.whispertflite.ui.MainScreen
import com.whispertflite.viewmodel.MainViewModel
import org.woheller69.freeDroidWarn.FreeDroidWarn

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, granted) ->
            if (!granted) {
                Toast.makeText(this, R.string.need_record_audio_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        checkInputMethodEnabled()
        checkPermissions()
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
        if (GithubStar.shouldShowStarDialog(this)) {
            GithubStar.starDialog(this, "https://github.com/woheller69/whisperIME")
        }

        setContent {
            MainScreen(viewModel)
        }
    }

    private fun checkInputMethodEnabled() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethodList = imm.enabledInputMethodList
        val myInputMethodId = "$packageName/${WhisperInputMethodService::class.java.name}"
        val inputMethodEnabled = enabledInputMethodList.any { it.id == myInputMethodId }

        if (!inputMethodEnabled) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}
