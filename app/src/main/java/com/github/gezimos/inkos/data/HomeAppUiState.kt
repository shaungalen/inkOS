package com.github.gezimos.inkos.data

import android.graphics.Typeface
import androidx.annotation.ColorInt
import com.github.gezimos.inkos.services.NotificationManager

/**
 * UI state for a home screen app button, including label, font, color, and notification badge info.
 */
data class HomeAppUiState(
    val id: Int, // position on home screen
    val label: String,
    val font: Typeface?,
    @ColorInt val color: Int,
    val notificationInfo: NotificationManager.NotificationInfo? = null,
    val activityPackage: String // Add unique identifier for the app
)

