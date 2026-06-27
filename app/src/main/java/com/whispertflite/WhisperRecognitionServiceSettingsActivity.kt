package com.whispertflite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.whispertflite.utils.Downloader.checkModels
import com.whispertflite.utils.LanguagePairAdapter
import com.whispertflite.utils.LanguagePairAdapter.Companion.getLanguagePairs
import com.whispertflite.utils.ThemeUtils.setStatusBarAppearance
import java.io.File

class WhisperRecognitionServiceSettingsActivity : AppCompatActivity() {
    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null
    private var sp: SharedPreferences? = null
    private var spinnerTflite: Spinner? = null
    private var spinnerLanguage: Spinner? = null
    private var modeSimpleChinese: CheckBox? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognition_service_settings)
        setStatusBarAppearance(this)
        val actionBar = getSupportActionBar()
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        if (!checkModels(this)) {
            val intent = Intent(this, DownloadActivity::class.java)
            startActivity(intent)
            finish()
        }

        sp = PreferenceManager.getDefaultSharedPreferences(this)

        spinnerLanguage = findViewById<Spinner>(R.id.spnrLanguage)
        val languagePairs = getLanguagePairs(this)
        val languagePairAdapter =
            LanguagePairAdapter(this, android.R.layout.simple_spinner_item, languagePairs)
        languagePairAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage!!.setAdapter(languagePairAdapter)

        spinnerLanguage!!.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                i: Int,
                l: Long
            ) {
                val editor = sp!!.edit()
                editor.putString("recognitionServiceLanguage", languagePairs.get(i)!!.first)
                editor.apply()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
            }
        })

        // Call the method to copy specific file types from assets to data folder
        sdcardDataFolder = this.getExternalFilesDir(null)

        val tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite")

        selectedTfliteFile = File(
            sdcardDataFolder,
            sp!!.getString("recognitionServiceModelName", MULTI_LINGUAL_TOP_WORLD_SLOW)
        )
        val tfliteAdapter = getFileArrayAdapter(tfliteFiles)
        val position = tfliteAdapter.getPosition(selectedTfliteFile)
        spinnerTflite = findViewById<Spinner>(R.id.spnrTfliteFiles)
        spinnerTflite!!.setAdapter(tfliteAdapter)
        spinnerTflite!!.setSelection(position, false)
        if (selectedTfliteFile!!.getName() == MULTI_LINGUAL_EU_MODEL_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_SLOW) {
            spinnerLanguage!!.setEnabled(true)
            val langCode: String = sp!!.getString("recognitionServiceLanguage", "auto")!!
            spinnerLanguage!!.setSelection(languagePairAdapter.getIndexByCode(langCode))
        } else {
            spinnerLanguage!!.setSelection(0)
            spinnerLanguage!!.setEnabled(false)
        }
        spinnerTflite!!.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedTfliteFile = parent.getItemAtPosition(position) as File?
                val editor = sp!!.edit()
                editor.putString("recognitionServiceModelName", selectedTfliteFile!!.getName())
                editor.apply()
                if (selectedTfliteFile!!.getName() == MULTI_LINGUAL_EU_MODEL_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_FAST || selectedTfliteFile!!.getName() == MULTI_LINGUAL_TOP_WORLD_SLOW) {
                    spinnerLanguage!!.setEnabled(true)
                    val langCode: String = sp!!.getString("recognitionServiceLanguage", "auto")!!
                    spinnerLanguage!!.setSelection(languagePairAdapter.getIndexByCode(langCode))
                } else {
                    spinnerLanguage!!.setSelection(0)
                    spinnerLanguage!!.setEnabled(false)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        modeSimpleChinese = findViewById<CheckBox>(R.id.mode_simple_chinese)
        modeSimpleChinese!!.setChecked(
            sp!!.getBoolean(
                "RecognitionServiceSimpleChinese",
                false
            )
        ) //default to traditional Chinese
        modeSimpleChinese!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            val editor = sp!!.edit()
            editor.putBoolean("RecognitionServiceSimpleChinese", isChecked)
            editor.apply()
        })

        checkPermissions()
    }


    private fun getFileArrayAdapter(tfliteFiles: ArrayList<File?>): ArrayAdapter<File?> {
        val adapter: ArrayAdapter<File?> =
            object : ArrayAdapter<File?>(this, android.R.layout.simple_spinner_item, tfliteFiles) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == ENGLISH_ONLY_MODEL) textView.setText(
                        R.string.english_only_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_EU_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else textView.setText(
                        getItem(position)!!.getName()
                            .substring(0, getItem(position)!!.getName().length - ".tflite".length)
                    )

                    return view
                }

                override fun getDropDownView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_SLOW) textView.setText(
                        R.string.multi_lingual_slow
                    )
                    else if ((getItem(position)!!.getName()) == ENGLISH_ONLY_MODEL) textView.setText(
                        R.string.english_only_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_EU_MODEL_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else if ((getItem(position)!!.getName()) == MULTI_LINGUAL_TOP_WORLD_FAST) textView.setText(
                        R.string.multi_lingual_fast
                    )
                    else textView.setText(
                        getItem(position)!!.getName()
                            .substring(0, getItem(position)!!.getName().length - ".tflite".length)
                    )

                    return view
                }
            }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun checkPermissions() {
        val perms: MutableList<String?> = ArrayList<String?>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.RECORD_AUDIO)
            Toast.makeText(
                this,
                getString(R.string.need_record_audio_permission),
                Toast.LENGTH_SHORT
            ).show()
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted")
        } else {
            Log.d(TAG, "Record permission is not granted")
        }
    }

    fun getFilesWithExtension(directory: File?, extension: String): ArrayList<File?> {
        val filteredFiles = ArrayList<File?>()

        // Check if the directory is accessible
        if (directory != null && directory.exists()) {
            val files = directory.listFiles()

            // Filter files by the provided extension
            if (files != null) {
                for (file in files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file)
                    }
                }
            }
        }

        return filteredFiles
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { //handle "back click" on action bar
        if (item.getItemId() == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "MainActivity"

        // whisper-small.tflite works well for multi-lingual
        const val MULTI_LINGUAL_EU_MODEL_FAST: String = "whisper-base.EUROPEAN_UNION.tflite"
        const val MULTI_LINGUAL_TOP_WORLD_FAST: String = "whisper-base.TOP_WORLD.tflite"
        const val MULTI_LINGUAL_TOP_WORLD_SLOW: String = "whisper-small.TOP_WORLD.tflite"
        const val MULTI_LINGUAL_MODEL_FAST: String = "whisper-base.tflite"
        const val MULTI_LINGUAL_MODEL_SLOW: String = "whisper-small.tflite"
        const val ENGLISH_ONLY_MODEL: String = "whisper-tiny.en.tflite"
    }
}