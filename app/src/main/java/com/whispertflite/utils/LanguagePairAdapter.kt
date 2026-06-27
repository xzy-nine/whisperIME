package com.whispertflite.utils

import android.content.Context
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.whispertflite.R
import java.util.Locale

class LanguagePairAdapter(
    context: Context,
    resource: Int,
    private val languagePairs: MutableList<Pair<String?, String?>?>
) : ArrayAdapter<Pair<String?, String?>?>(
    context, resource,
    languagePairs
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        if (position < languagePairs.size) {
            view.setText(languagePairs.get(position)!!.second) // Display name
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        if (position < languagePairs.size) {
            view.setText(languagePairs.get(position)!!.second) // Display name
        }
        return view
    }

    // Helper method to find index by language code
    fun getIndexByCode(langCode: String?): Int {
        for (i in languagePairs.indices) {
            if (languagePairs.get(i)!!.first == langCode) {
                return i
            }
        }
        return 0 // Default to first item
    }

    companion object {
        @JvmStatic
        fun getLanguagePairs(context: Context): MutableList<Pair<String?, String?>?> {
            // Create pairs of code and display name, then sort by display name
            val languagePairs: MutableList<Pair<String?, String?>?> =
                ArrayList<Pair<String?, String?>?>()
            val sortedLanguages = context.getResources().getStringArray(R.array.top40_languages)
            for (code in sortedLanguages) {
                val locale = Locale(code)
                languagePairs.add(Pair<String?, String?>(code, locale.getDisplayLanguage()))
            }

            // Sort by display name
            languagePairs.sortWith(Comparator { pair1: Pair<String?, String?>?, pair2: Pair<String?, String?>? ->
                pair1!!.second!!.compareTo(
                    pair2!!.second!!,
                    ignoreCase = true
                )
            })

            // Add auto at first position
            languagePairs.add(
                0,
                Pair<String?, String?>("auto", context.getString(R.string.auto_lang))
            )
            return languagePairs
        }
    }
}
