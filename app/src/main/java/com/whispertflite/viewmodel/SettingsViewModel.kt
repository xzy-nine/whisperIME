package com.whispertflite.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.whispertflite.utils.InputLang
import com.whispertflite.utils.InputLang.Companion.langList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val sdcardDataFolder = application.getExternalFilesDir(null)

    val modelFiles = MutableStateFlow<List<File>>(emptyList())
    val selectedModel = MutableStateFlow<File?>(null)
    val selectedModelIndex = MutableStateFlow(0)
    val languagePairs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val selectedLanguageIndex = MutableStateFlow(0)
    val simpleChinese = MutableStateFlow(false)
    val swipeCutPasteEnabled = MutableStateFlow(true)

    fun loadModelFiles() {
        val files = sdcardDataFolder?.let {
            it.listFiles()?.filter { f -> f.isFile && f.name.endsWith(".tflite") }?.toList()
        } ?: emptyList()
        modelFiles.value = files
    }

    fun loadLanguagePairs(context: android.content.Context) {
        val pairs = mutableListOf<Pair<String, String>>()
        val sortedLanguages = context.resources.getStringArray(com.whispertflite.R.array.top40_languages)
        for (code in sortedLanguages) {
            val locale = Locale(code)
            pairs.add(Pair(code, locale.displayLanguage))
        }
        pairs.sortBy { it.second }
        pairs.add(0, Pair("auto", context.getString(com.whispertflite.R.string.auto_lang)))
        languagePairs.value = pairs
    }

    fun restoreSettings() {
        val savedModelName = sp.getString("recognitionServiceModelName", "whisper-small.TOP_WORLD.tflite")
            ?: "whisper-small.TOP_WORLD.tflite"
        val savedLang = sp.getString("recognitionServiceLanguage", "auto") ?: "auto"
        simpleChinese.value = sp.getBoolean("RecognitionServiceSimpleChinese", false)
        swipeCutPasteEnabled.value = sp.getBoolean("swipeCutPasteEnabled", true)

        selectedModel.value = modelFiles.value.find { it.name == savedModelName }
        val modelIdx = modelFiles.value.indexOfFirst { it.name == savedModelName }
        selectedModelIndex.value = if (modelIdx >= 0) modelIdx else 0

        val langIdx = languagePairs.value.indexOfFirst { it.first == savedLang }
        selectedLanguageIndex.value = if (langIdx >= 0) langIdx else 0
    }

    fun onModelSelected(index: Int) {
        if (index < modelFiles.value.size) {
            selectedModelIndex.value = index
            selectedModel.value = modelFiles.value[index]
            sp.edit().putString("recognitionServiceModelName", modelFiles.value[index].name).apply()

            val modelName = modelFiles.value[index].name
            val isMultilingual = modelName == "whisper-base.EUROPEAN_UNION.tflite" ||
                    modelName == "whisper-base.TOP_WORLD.tflite" ||
                    modelName == "whisper-small.TOP_WORLD.tflite"
            if (!isMultilingual) {
                selectedLanguageIndex.value = 0
                sp.edit().putString("recognitionServiceLanguage", "auto").apply()
            }
        }
    }

    fun onLanguageSelected(index: Int) {
        if (index < languagePairs.value.size) {
            selectedLanguageIndex.value = index
            val code = languagePairs.value[index].first
            sp.edit().putString("recognitionServiceLanguage", code).apply()
        }
    }

    fun toggleSimpleChinese(checked: Boolean) {
        simpleChinese.value = checked
        sp.edit().putBoolean("RecognitionServiceSimpleChinese", checked).apply()
    }

    fun toggleSwipeCutPaste(checked: Boolean) {
        swipeCutPasteEnabled.value = checked
        sp.edit().putBoolean("swipeCutPasteEnabled", checked).apply()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
