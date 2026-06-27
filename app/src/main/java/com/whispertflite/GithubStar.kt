package com.whispertflite

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager

object GithubStar {
    fun setAskForStar(askForStar: Boolean, context: Context) {
        val prefManager = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefManager.edit()
        editor.putBoolean("askForStar", askForStar)
        editor.apply()
    }

    fun shouldShowStarDialog(context: Context): Boolean {
        val prefManager = PreferenceManager.getDefaultSharedPreferences(context)
        val versionCode = prefManager.getInt("versionCode", 0)
        val askForStar = prefManager.getBoolean("askForStar", true)

        if (prefManager.contains("versionCode") && BuildConfig.VERSION_CODE > versionCode && askForStar) { //not at first start, only after upgrade and only if use has not yet given a star or has declined
            val editor = prefManager.edit()
            editor.putInt("versionCode", BuildConfig.VERSION_CODE)
            editor.apply()
            return true
        } else {
            val editor = prefManager.edit()
            editor.putInt("versionCode", BuildConfig.VERSION_CODE)
            editor.apply()
            return false
        }
    }

    fun starDialog(context: Context, url: String?) {
        val prefManager = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefManager.getBoolean("askForStar", true)) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            alertDialogBuilder.setMessage(R.string.dialog_StarOnGitHub)
            alertDialogBuilder.setPositiveButton(
                context.getString(android.R.string.ok),
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        setAskForStar(false, context)
                    }
                })
            alertDialogBuilder.setNegativeButton(
                context.getString(android.R.string.no),
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        setAskForStar(false, context)
                    }
                })
            alertDialogBuilder.setNeutralButton(
                context.getString(R.string.dialog_Later_button),
                null
            )

            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
    }
}