package com.github.gezimos.inkos.data

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.res.ResourcesCompat
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.helper.getTrueSystemFont

interface EnumOption {
    @Composable
    fun string(): String
}


object Constants {

    const val REQUEST_SET_DEFAULT_HOME = 777

    const val TRIPLE_TAP_DELAY_MS = 300
    const val LONG_PRESS_DELAY_MS = 500

    const val MIN_HOME_APPS = 0
    const val MAX_HOME_APPS = 30

    const val MIN_HOME_PAGES = 1

    // These are for the App Text Size (home screen app labels)
    const val MIN_APP_SIZE = 10
    const val MAX_APP_SIZE = 50

    // Add for settings text size
    const val MIN_SETTINGS_TEXT_SIZE = 8
    const val MAX_SETTINGS_TEXT_SIZE = 27

    // Add for notification text size
    const val MIN_LABEL_NOTIFICATION_TEXT_SIZE = 10
    const val MAX_LABEL_NOTIFICATION_TEXT_SIZE = 40

    const val BACKUP_WRITE = 1
    const val BACKUP_READ = 2

    const val THEME_BACKUP_WRITE = 11
    const val THEME_BACKUP_READ = 12

    const val MIN_BATTERY_SIZE = 10
    const val MAX_BATTERY_SIZE = 40

    const val MIN_TEXT_PADDING = 0
    const val MAX_TEXT_PADDING = 80

    // Restore for clock_date_size (not gap)
    const val MIN_CLOCK_SIZE = 12
    const val MAX_CLOCK_SIZE = 80

    // Update SWIPE_DISTANCE_THRESHOLD dynamically based on screen dimensions
    var SWIPE_DISTANCE_THRESHOLD = 0f

    // Update MAX_HOME_PAGES dynamically based on MAX_HOME_APPS
    var MAX_HOME_PAGES = 10

    fun updateMaxHomePages(context: Context) {
        val prefs = Prefs(context)

        MAX_HOME_PAGES = if (prefs.homeAppsNum < MAX_HOME_PAGES) {
            prefs.homeAppsNum
        } else {
            MAX_HOME_PAGES
        }

    }

    enum class BackupType {
        FullSystem,
        Theme
    }

    enum class AppDrawerFlag {
        LaunchApp,
        HiddenApps,
        PrivateApps,
        SetHomeApp,
        SetSwipeUp,
        SetSwipeDown,
        SetSwipeLeft,
        SetSwipeRight,
        SetClickClock,
        SetDoubleTap,

    }

    enum class Action : EnumOption {
        OpenApp,
        TogglePrivateSpace,
        NextPage,
        PreviousPage,
        RestartApp,
        OpenNotificationsScreen,
        OpenNotificationsScreenAlt, // Renamed from OpenLettersScreen
        Disabled;

        fun getString(context: Context): String {
            return when (this) {
                OpenApp -> context.getString(R.string.open_app)
                TogglePrivateSpace -> context.getString(R.string.private_space)
                NextPage -> context.getString(R.string.next_page)
                PreviousPage -> context.getString(R.string.previous_page)
                RestartApp -> context.getString(R.string.restart_launcher)
                OpenNotificationsScreen -> context.getString(R.string.notifications_screen_title)
                OpenNotificationsScreenAlt -> context.getString(R.string.notifications_screen_title) // Use same string for alt
                Disabled -> context.getString(R.string.disabled)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                OpenApp -> stringResource(R.string.open_app)
                TogglePrivateSpace -> stringResource(R.string.private_space)
                NextPage -> stringResource(R.string.next_page)
                PreviousPage -> stringResource(R.string.previous_page)
                RestartApp -> stringResource(R.string.restart_launcher)
                OpenNotificationsScreen -> stringResource(R.string.notifications_screen_title)
                OpenNotificationsScreenAlt -> stringResource(R.string.notifications_screen_title)
                Disabled -> stringResource(R.string.disabled)
            }
        }
    }

    enum class Theme : EnumOption {
        System,
        Dark,
        Light;

        // Function to get a string from a context (for non-Composable use)
        fun getString(context: Context): String {
            return when (this) {
                System -> context.getString(R.string.system_default)
                Dark -> context.getString(R.string.dark)
                Light -> context.getString(R.string.light)
            }
        }

        // Keep this for Composable usage
        @Composable
        override fun string(): String {
            return when (this) {
                System -> stringResource(R.string.system_default)
                Dark -> stringResource(R.string.dark)
                Light -> stringResource(R.string.light)
            }
        }
    }

    enum class FontFamily : EnumOption {
        System,
        Hoog,
        Merriweather,
        Osdmono,
        Manrope,
        Custom; // Add Custom for user-uploaded font

        fun getFont(context: Context, customPath: String? = null): Typeface? {
            val prefs = Prefs(context)
            return when (this) {
                System -> getTrueSystemFont()
                Custom -> {
                    val path = customPath ?: prefs.customFontPath
                    if (!path.isNullOrBlank()) {
                        val file = java.io.File(path)
                        if (file.exists()) {
                            try {
                                Typeface.createFromFile(path)
                            } catch (e: Exception) {
                                getTrueSystemFont()
                            }
                        } else {
                            getTrueSystemFont()
                        }
                    } else getTrueSystemFont()
                }

                Hoog -> ResourcesCompat.getFont(context, R.font.hoog)
                Merriweather -> ResourcesCompat.getFont(context, R.font.merriweather)
                Osdmono -> ResourcesCompat.getFont(context, R.font.osdmono)
                Manrope -> ResourcesCompat.getFont(context, R.font.manropemedium)
            }
        }

        fun getString(context: Context): String {
            return when (this) {
                System -> context.getString(R.string.system_default)
                Custom -> "Custom Font"
                Hoog -> context.getString(R.string.settings_font_hoog)
                Merriweather -> context.getString(R.string.settings_font_merriweather)
                Osdmono -> context.getString(R.string.settings_font_osdmono)
                Manrope -> "Manrope Medium"
            }
        }

        companion object {
            // Helper to get all custom font display names and paths
            fun getAllCustomFonts(context: Context): List<Pair<String, String>> {
                val prefs = Prefs(context)
                return prefs.customFontPaths.map { path ->
                    // Remove "Custom:" and limit to 24 chars
                    path.substringAfterLast('/').take(24) to path
                }
            }

            // Centralized: get the correct custom font path for each widget/context
            fun getCustomFontPathForWidget(context: Context, widget: String): String? {
                val prefs = Prefs(context)
                return prefs.getCustomFontPath(widget)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                System -> stringResource(R.string.system_default)
                Custom -> "Custom Font"
                Hoog -> stringResource(R.string.settings_font_hoog)
                Merriweather -> stringResource(R.string.settings_font_merriweather)
                Osdmono -> stringResource(R.string.settings_font_osdmono)
                Manrope -> "Manrope Medium"
            }
        }
    }
}