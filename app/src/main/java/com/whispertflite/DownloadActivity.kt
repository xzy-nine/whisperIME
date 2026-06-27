package com.whispertflite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.whispertflite.databinding.ActivityDownloadBinding
import com.whispertflite.utils.Downloader
import com.whispertflite.utils.ThemeUtils

class DownloadActivity  : AppCompatActivity() {
    private lateinit var binding: ActivityDownloadBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeUtils.setStatusBarAppearance(this)
        setupMirrorSpinner()
    }

    private fun setupMirrorSpinner() {
        val sources = Downloader.MirrorSource.values()
        val labels = sources.map { it.getDisplayName(this) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.mirrorSpinner.adapter = adapter

        val current = Downloader.getMirrorSource(this)
        binding.mirrorSpinner.setSelection(current.ordinal)

        binding.mirrorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Downloader.setMirrorSource(this@DownloadActivity, sources[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (Downloader.checkModels(this)){
            // call Main Activity
            binding.downloadProgress?.setProgress(100)
            binding.downloadProgress?.setVisibility(View.VISIBLE)
            binding.buttonStart?.setVisibility(View.VISIBLE)
            if (!Downloader.checkUpdate(this)){
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                binding.buttonUpdate?.setVisibility(View.VISIBLE)
            }
        }
    }

    fun download(view: View) {
        binding.downloadSize?.setVisibility(View.VISIBLE)
        binding.downloadProgress?.setVisibility(View.VISIBLE)
        binding.buttonStart?.setVisibility(View.INVISIBLE)
        Downloader.downloadModels(this, binding)
    }

    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun updateModels(view: View) {
        binding.downloadSize?.setVisibility(View.VISIBLE)
        binding.downloadProgress?.setVisibility(View.VISIBLE)
        binding.buttonStart?.setVisibility(View.INVISIBLE)
        binding.buttonUpdate?.setVisibility(View.GONE)
        Downloader.deleteOldModels(this);
        Downloader.downloadModels(this, binding)
    }
}
