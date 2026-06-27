package com.whispertflite.utils

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsetsController

object ThemeUtils {
    @JvmStatic
    fun setStatusBarAppearance(activity: Activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nightModeFlags = activity.getResources()
                .getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES)
            val insetsController = activity.getWindow().getInsetsController()
            if (insetsController != null) {
                if (isDarkMode) {
                    // Dark mode: remove light status bar appearance (use light icons)
                    insetsController.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else {
                    // Light mode: enable light status bar appearance (dark icons)
                    insetsController.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            }
        }
    }
}
