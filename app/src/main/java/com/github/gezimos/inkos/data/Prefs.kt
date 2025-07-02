package com.github.gezimos.inkos.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import android.util.Log
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.edit
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.helper.getUserHandleFromString
import com.github.gezimos.inkos.helper.isSystemInDarkMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_FILENAME = "com.github.gezimos.inkos"

private const val APP_VERSION = "APP_VERSION"
private const val FIRST_OPEN = "FIRST_OPEN"
private const val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
private const val LOCK_MODE = "LOCK_MODE"
private const val HOME_APPS_NUM = "HOME_APPS_NUM"
private const val HOME_PAGES_NUM = "HOME_PAGES_NUM"
private const val HOME_PAGES_PAGER = "HOME_PAGES_PAGER"
private const val HOME_CLICK_AREA = "HOME_CLICK_AREA"
private const val STATUS_BAR = "STATUS_BAR"
private const val SHOW_BATTERY = "SHOW_BATTERY"
private const val HOME_LOCKED = "HOME_LOCKED"
private const val SETTINGS_LOCKED = "SETTINGS_LOCKED"
private const val SHOW_CLOCK = "SHOW_CLOCK"
private const val SWIPE_RIGHT_ACTION = "SWIPE_RIGHT_ACTION"
private const val SWIPE_LEFT_ACTION = "SWIPE_LEFT_ACTION"
private const val CLICK_CLOCK_ACTION = "CLICK_CLOCK_ACTION"
private const val DOUBLE_TAP_ACTION = "DOUBLE_TAP_ACTION"
private const val HIDDEN_APPS = "HIDDEN_APPS"
private const val LOCKED_APPS = "LOCKED_APPS"
private const val LAUNCHER_FONT = "LAUNCHER_FONT"
private const val APP_NAME = "APP_NAME"
private const val APP_PACKAGE = "APP_PACKAGE"
private const val APP_USER = "APP_USER"
private const val APP_ALIAS = "APP_ALIAS"
private const val APP_ACTIVITY = "APP_ACTIVITY"
private const val APP_THEME = "APP_THEME"
private const val SWIPE_LEFT = "SWIPE_LEFT"
private const val SWIPE_RIGHT = "SWIPE_RIGHT"
private const val CLICK_CLOCK = "CLICK_CLOCK"
private const val DOUBLE_TAP = "DOUBLE_TAP"
private const val APP_SIZE_TEXT = "APP_SIZE_TEXT"
private const val CLOCK_SIZE_TEXT = "CLOCK_SIZE_TEXT"
private const val BATTERY_SIZE_TEXT = "BATTERY_SIZE_TEXT"
private const val TEXT_SIZE_SETTINGS = "TEXT_SIZE_SETTINGS"
private const val TEXT_PADDING_SIZE = "TEXT_PADDING_SIZE"
private const val SHOW_NOTIFICATION_BADGE = "show_notification_badge"
private const val ONBOARDING_PAGE = "ONBOARDING_PAGE"

private const val BACKGROUND_COLOR = "BACKGROUND_COLOR"
private const val APP_COLOR = "APP_COLOR"
private const val CLOCK_COLOR = "CLOCK_COLOR"
private const val BATTERY_COLOR = "BATTERY_COLOR"

private const val APPS_FONT = "APPS_FONT"
private const val CLOCK_FONT = "CLOCK_FONT"
private const val STATUS_FONT = "STATUS_FONT"  // For Calendar, Alarm, Battery
private const val NOTIFICATION_FONT = "NOTIFICATION_FONT"

private const val EINK_REFRESH_ENABLED = "EINK_REFRESH_ENABLED"

class Prefs(val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    val sharedPrefs: SharedPreferences
        get() = prefs

    private val CUSTOM_FONT_PATH_MAP_KEY = "custom_font_path_map"
    private val gson = Gson()

    var customFontPathMap: MutableMap<String, String>
        get() {
            val json = prefs.getString(CUSTOM_FONT_PATH_MAP_KEY, "{}") ?: "{}"
            return gson.fromJson(json, object : TypeToken<MutableMap<String, String>>() {}.type)
                ?: mutableMapOf()
        }
        set(value) {
            prefs.edit { putString(CUSTOM_FONT_PATH_MAP_KEY, gson.toJson(value)) }
        }

    fun setCustomFontPath(context: String, path: String) {
        val map = customFontPathMap
        map[context] = path
        customFontPathMap = map
    }

    fun getCustomFontPath(context: String): String? {
        return customFontPathMap[context]
    }

    // Remove a custom font path from the context map
    fun removeCustomFontPath(context: String) {
        val map = customFontPathMap
        map.remove(context)
        customFontPathMap = map
    }

    // Remove a custom font path from the set of paths
    fun removeCustomFontPathByPath(path: String) {
        val set = customFontPaths
        set.remove(path)
        customFontPaths = set
    }

    var universalFontEnabled: Boolean
        get() = prefs.getBoolean("universal_font_enabled", true)
        set(value) {
            prefs.edit {
                putBoolean("universal_font_enabled", value)
            }
            if (value) {
                // Apply universal font to all elements when enabled
                val font = universalFont
                appsFont = font
                clockFont = font
                statusFont = font
                labelnotificationsFont = font
                batteryFont = font
                lettersFont = font
                lettersTitleFont = font
                notificationsFont = font
            }
        }

    var universalFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString("universal_font", Constants.FontFamily.System.name)!!
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) {
            prefs.edit {
                putString("universal_font", value.name)
            }
            if (universalFontEnabled) {
                // When universal font is enabled and changed, update all relevant preferences
                appsFont = value
                clockFont = value
                statusFont = value
                labelnotificationsFont = value
                batteryFont = value
                lettersFont = value
                lettersTitleFont = value
                notificationsFont = value
            }
        }

    var fontFamily: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(
                    LAUNCHER_FONT,
                    Constants.FontFamily.System.name
                ).toString()
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(LAUNCHER_FONT, value.name) }

    var customFontPath: String?
        get() = prefs.getString("custom_font_path", null)
        set(value) = prefs.edit { putString("custom_font_path", value) }

    // Store a set of custom font paths
    var customFontPaths: MutableSet<String>
        get() = prefs.getStringSet("custom_font_paths", mutableSetOf()) ?: mutableSetOf()
        set(value) = prefs.edit { putStringSet("custom_font_paths", value) }

    // Add a new custom font path
    fun addCustomFontPath(path: String) {
        val set = customFontPaths
        set.add(path)
        customFontPaths = set
    }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(NOTIFICATIONS_ENABLED, value) }

    var notificationsFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(NOTIFICATIONS_FONT, Constants.FontFamily.System.name)!!
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(NOTIFICATIONS_FONT, value.name) }

    var notificationsTextSize: Int
        get() = prefs.getInt(NOTIFICATIONS_TEXT_SIZE, 18)
        set(value) = prefs.edit { putInt(NOTIFICATIONS_TEXT_SIZE, value) }

    var showNotificationSenderFullName: Boolean
        get() = prefs.getBoolean("show_notification_sender_full_name", false)
        set(value) = prefs.edit { putBoolean("show_notification_sender_full_name", value) }

    var einkRefreshEnabled: Boolean
        get() = prefs.getBoolean(EINK_REFRESH_ENABLED, false)
        set(value) = prefs.edit { putBoolean(EINK_REFRESH_ENABLED, value) }

    // --- Push Notifications Master Switch ---
    private val _pushNotificationsEnabledFlow = MutableStateFlow(pushNotificationsEnabled)
    val pushNotificationsEnabledFlow: StateFlow<Boolean> get() = _pushNotificationsEnabledFlow

    var pushNotificationsEnabled: Boolean
        get() = prefs.getBoolean("push_notifications_enabled", false)
        set(value) {
            prefs.edit { putBoolean("push_notifications_enabled", value) }
            _pushNotificationsEnabledFlow.value = value
        }

    // Save/restore notification-related switches' state
    private val NOTIFICATION_SWITCHES_STATE_KEY = "notification_switches_state"

    fun saveNotificationSwitchesState() {
        val state = mapOf(
            "showNotificationBadge" to showNotificationBadge,
            "showNotificationText" to showNotificationText,
            "showNotificationSenderName" to showNotificationSenderName,
            "showNotificationGroupName" to showNotificationGroupName,
            "showNotificationMessage" to showNotificationMessage,
            "showMediaIndicator" to showMediaIndicator,
            "showMediaName" to showMediaName,
            "notificationsEnabled" to notificationsEnabled,
            "showNotificationSenderFullName" to showNotificationSenderFullName
        )
        prefs.edit { putString(NOTIFICATION_SWITCHES_STATE_KEY, gson.toJson(state)) }
    }

    fun restoreNotificationSwitchesState() {
        val json = prefs.getString(NOTIFICATION_SWITCHES_STATE_KEY, null) ?: return
        val type = object : TypeToken<Map<String, Boolean>>() {}.type
        val state: Map<String, Boolean> = gson.fromJson(json, type) ?: return
        state["showNotificationBadge"]?.let { showNotificationBadge = it }
        state["showNotificationText"]?.let { showNotificationText = it }
        state["showNotificationSenderName"]?.let { showNotificationSenderName = it }
        state["showNotificationGroupName"]?.let { showNotificationGroupName = it }
        state["showNotificationMessage"]?.let { showNotificationMessage = it }
        state["showMediaIndicator"]?.let { showMediaIndicator = it }
        state["showMediaName"]?.let { showMediaName = it }
        state["notificationsEnabled"]?.let { notificationsEnabled = it }
        state["showNotificationSenderFullName"]?.let { showNotificationSenderFullName = it }
    }

    fun disableAllNotificationSwitches() {
        showNotificationBadge = false
        showNotificationText = false
        showNotificationSenderName = false
        showNotificationGroupName = false
        showNotificationMessage = false
        showMediaIndicator = false
        showMediaName = false
        notificationsEnabled = false
        showNotificationSenderFullName = false
    }

    fun saveToString(): String {
        val all: HashMap<String, Any?> = HashMap(prefs.all)
        return Gson().toJson(all)
    }

    fun loadFromString(json: String) {
        prefs.edit {
            val all: HashMap<String, Any?> =
                Gson().fromJson(json, object : TypeToken<HashMap<String, Any?>>() {}.type)
            val pm = context.packageManager
            for ((key, value) in all) {
                // Explicitly handle allowlists as sets of strings, and filter out non-existent apps
                if (key == "allowed_notification_apps" || key == "allowed_badge_notification_apps") {
                    val set = when (value) {
                        is Collection<*> -> value.filterIsInstance<String>().toMutableSet()
                        is String -> mutableSetOf(value)
                        else -> mutableSetOf<String>()
                    }
                    // Filter out packages that are not installed
                    val filteredSet = set.filter { pkg ->
                        try {
                            pm.getPackageInfo(pkg, 0)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }.toMutableSet()
                    putStringSet(key, filteredSet)
                    continue
                }
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Number -> {
                        if (value.toDouble() == value.toInt().toDouble()) {
                            putInt(key, value.toInt())
                        } else {
                            putFloat(key, value.toFloat())
                        }
                    }

                    is MutableSet<*> -> {
                        val list = value.filterIsInstance<String>().toSet()
                        putStringSet(key, list)
                    }

                    else -> {
                        Log.d("backup error", "$value")
                    }
                }
            }
        }
    }

    fun getAppAlias(key: String): String {
        return prefs.getString(key, "").toString()
    }

    fun setAppAlias(packageName: String, appAlias: String) {
        prefs.edit { putString("app_alias_$packageName", appAlias) }
    }

    fun removeAppAlias(packageName: String) {
        prefs.edit { remove("app_alias_$packageName") }
    }

    var appVersion: Int
        get() = prefs.getInt(APP_VERSION, -1)
        set(value) = prefs.edit { putInt(APP_VERSION, value) }

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value) }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value) }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value) }

    var homePager: Boolean
        get() = prefs.getBoolean(HOME_PAGES_PAGER, true)
        set(value) = prefs.edit { putBoolean(HOME_PAGES_PAGER, value) }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 15)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value) }

    var homePagesNum: Int
        get() = prefs.getInt(HOME_PAGES_NUM, 3)
        set(value) = prefs.edit { putInt(HOME_PAGES_NUM, value) }

    var backgroundColor: Int
        get() = prefs.getInt(BACKGROUND_COLOR, getColor(context, getColorInt("bg")))
        set(value) = prefs.edit { putInt(BACKGROUND_COLOR, value) }

    var appColor: Int
        get() = prefs.getInt(APP_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefs.edit { putInt(APP_COLOR, value) }

    var clockColor: Int
        get() = prefs.getInt(CLOCK_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefs.edit { putInt(CLOCK_COLOR, value) }

    var batteryColor: Int
        get() = prefs.getInt(BATTERY_COLOR, getColor(context, getColorInt("txt")))
        set(value) = prefs.edit { putInt(BATTERY_COLOR, value) }

    var extendHomeAppsArea: Boolean
        get() = prefs.getBoolean(HOME_CLICK_AREA, false)
        set(value) = prefs.edit { putBoolean(HOME_CLICK_AREA, value) }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value) }

    var showClock: Boolean
        get() = prefs.getBoolean(SHOW_CLOCK, false)
        set(value) = prefs.edit { putBoolean(SHOW_CLOCK, value) }

    var showBattery: Boolean
        get() = prefs.getBoolean(SHOW_BATTERY, false)
        set(value) = prefs.edit { putBoolean(SHOW_BATTERY, value) }

    var showNotificationBadge: Boolean
        get() = prefs.getBoolean(SHOW_NOTIFICATION_BADGE, true)
        set(value) = prefs.edit { putBoolean(SHOW_NOTIFICATION_BADGE, value) }

    var showNotificationText: Boolean
        get() = prefs.getBoolean("showNotificationText", true)
        set(value) = prefs.edit { putBoolean("showNotificationText", value) }

    var labelnotificationsTextSize: Int
        get() = prefs.getInt("notificationTextSize", 16)
        set(value) = prefs.edit { putInt("notificationTextSize", value) }

    var showNotificationSenderName: Boolean
        get() = prefs.getBoolean("show_notification_sender_name", true)
        set(value) = prefs.edit { putBoolean("show_notification_sender_name", value) }

    var showNotificationGroupName: Boolean
        get() = prefs.getBoolean("show_notification_group_name", true)
        set(value) = prefs.edit { putBoolean("show_notification_group_name", value) }

    var showNotificationMessage: Boolean
        get() = prefs.getBoolean("show_notification_message", true)
        set(value) = prefs.edit { putBoolean("show_notification_message", value) }

    // Media indicator and media name toggles
    var showMediaIndicator: Boolean
        get() = prefs.getBoolean("show_media_indicator", true)
        set(value) = prefs.edit { putBoolean("show_media_indicator", value) }

    var showMediaName: Boolean
        get() = prefs.getBoolean("show_media_name", true)
        set(value) = prefs.edit { putBoolean("show_media_name", value) }

    var homeLocked: Boolean
        get() = prefs.getBoolean(HOME_LOCKED, false)
        set(value) = prefs.edit { putBoolean(HOME_LOCKED, value) }

    var settingsLocked: Boolean
        get() = prefs.getBoolean(SETTINGS_LOCKED, false)
        set(value) = prefs.edit { putBoolean(SETTINGS_LOCKED, value) }

    var swipeLeftAction: Constants.Action
        get() {
            return try {
                Constants.Action.valueOf(
                    prefs.getString(
                        SWIPE_LEFT_ACTION,
                        Constants.Action.OpenNotificationsScreen.name // changed default
                    ).toString()
                )
            } catch (_: Exception) {
                Constants.Action.OpenNotificationsScreen // changed default
            }
        }
        set(value) = prefs.edit { putString(SWIPE_LEFT_ACTION, value.name) }

    var swipeRightAction: Constants.Action
        get() {
            return try {
                Constants.Action.valueOf(
                    prefs.getString(
                        SWIPE_RIGHT_ACTION,
                        Constants.Action.OpenApp.name
                    ).toString()
                )
            } catch (_: Exception) {
                Constants.Action.OpenApp
            }
        }
        set(value) = prefs.edit { putString(SWIPE_RIGHT_ACTION, value.name) }

    var clickClockAction: Constants.Action
        get() {
            return try {
                Constants.Action.valueOf(
                    prefs.getString(
                        CLICK_CLOCK_ACTION,
                        Constants.Action.OpenApp.name
                    ).toString()
                )
            } catch (_: Exception) {
                Constants.Action.OpenApp
            }
        }
        set(value) = prefs.edit { putString(CLICK_CLOCK_ACTION, value.name) }

    var doubleTapAction: Constants.Action
        get() {
            return try {
                Constants.Action.valueOf(
                    prefs.getString(
                        DOUBLE_TAP_ACTION,
                        Constants.Action.RestartApp.name
                    ).toString()
                )
            } catch (_: Exception) {
                Constants.Action.RestartApp
            }
        }
        set(value) = prefs.edit { putString(DOUBLE_TAP_ACTION, value.name) }

    var appTheme: Constants.Theme
        get() {
            return try {
                Constants.Theme.valueOf(
                    prefs.getString(APP_THEME, Constants.Theme.System.name).toString()
                )
            } catch (_: Exception) {
                Constants.Theme.System
            }
        }
        set(value) = prefs.edit { putString(APP_THEME, value.name) }


    var appsFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(APPS_FONT, Constants.FontFamily.System.name).toString()
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(APPS_FONT, value.name) }

    var clockFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(CLOCK_FONT, Constants.FontFamily.System.name).toString()
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(CLOCK_FONT, value.name) }

    var statusFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(STATUS_FONT, Constants.FontFamily.System.name).toString()
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(STATUS_FONT, value.name) }

    var labelnotificationsFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(NOTIFICATION_FONT, Constants.FontFamily.System.name).toString()
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(NOTIFICATION_FONT, value.name) }

    var batteryFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(BATTERY_FONT, Constants.FontFamily.System.name)!!
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(BATTERY_FONT, value.name) }

    var lettersFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(LETTERS_FONT, Constants.FontFamily.System.name)!!
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(LETTERS_FONT, value.name) }

    var lettersTextSize: Int
        get() = prefs.getInt(LETTERS_TEXT_SIZE, 18)
        set(value) = prefs.edit { putInt(LETTERS_TEXT_SIZE, value) }

    var lettersTitle: String
        get() = prefs.getString(LETTERS_TITLE, "Letters") ?: "Letters"
        set(value) = prefs.edit { putString(LETTERS_TITLE, value) }

    var lettersTitleFont: Constants.FontFamily
        get() = try {
            Constants.FontFamily.valueOf(
                prefs.getString(LETTERS_TITLE_FONT, Constants.FontFamily.System.name)!!
            )
        } catch (_: Exception) {
            Constants.FontFamily.System
        }
        set(value) = prefs.edit { putString(LETTERS_TITLE_FONT, value.name) }

    var lettersTitleSize: Int
        get() = prefs.getInt(LETTERS_TITLE_SIZE, 36)
        set(value) = prefs.edit { putInt(LETTERS_TITLE_SIZE, value) }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value) }

    var lockedApps: MutableSet<String>
        get() = prefs.getStringSet(LOCKED_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(LOCKED_APPS, value) }

    /**
     * By the number in home app list, get the list item.
     * TODO why not just save it as a list?
     */
    fun getHomeAppModel(i: Int): AppListItem {
        return loadApp("$i")
    }

    fun setHomeAppModel(i: Int, appListItem: AppListItem) {
        storeApp("$i", appListItem)
    }

    var appSwipeLeft: AppListItem
        get() = loadApp(SWIPE_LEFT)
        set(appModel) = storeApp(SWIPE_LEFT, appModel)

    var appSwipeRight: AppListItem
        get() = loadApp(SWIPE_RIGHT)
        set(appModel) = storeApp(SWIPE_RIGHT, appModel)

    var appClickClock: AppListItem
        get() = loadApp(CLICK_CLOCK)
        set(appModel) = storeApp(CLICK_CLOCK, appModel)


    var appDoubleTap: AppListItem
        get() = loadApp(DOUBLE_TAP)
        set(appModel) = storeApp(DOUBLE_TAP, appModel)

    /**
     *  Restore an `AppListItem` from preferences.
     *
     *  We store not only application name, but everything needed to start the item.
     *  Because thus we save time to query the system about it?
     *
     *  TODO store with protobuf instead of serializing manually.
     */
    private fun loadApp(id: String): AppListItem {
        val appName = prefs.getString("${APP_NAME}_$id", "").toString()
        val appPackage = prefs.getString("${APP_PACKAGE}_$id", "").toString()
        val appActivityName = prefs.getString("${APP_ACTIVITY}_$id", "").toString()
        val customLabel = getAppAlias("app_alias_$appPackage")

        val userHandleString = try {
            prefs.getString("${APP_USER}_$id", "").toString()
        } catch (_: Exception) {
            ""
        }
        val userHandle: UserHandle = getUserHandleFromString(context, userHandleString)

        return AppListItem(
            activityLabel = appName,
            activityPackage = appPackage,
            customLabel = customLabel, // Set the custom label when loading the app
            activityClass = appActivityName,
            user = userHandle,
        )
    }

    private fun storeApp(id: String, app: AppListItem) {
        prefs.edit {

            putString("${APP_NAME}_$id", app.label)
            putString("${APP_PACKAGE}_$id", app.activityPackage)
            putString("${APP_ACTIVITY}_$id", app.activityClass)
            putString("${APP_ALIAS}_$id", app.customLabel)
            putString("${APP_USER}_$id", app.user.toString())
        }
    }

    var appSize: Int
        get() {
            return try {
                prefs.getInt(APP_SIZE_TEXT, 32)
            } catch (_: Exception) {
                18
            }
        }
        set(value) = prefs.edit { putInt(APP_SIZE_TEXT, value) }

    var clockSize: Int
        get() {
            return try {
                prefs.getInt(CLOCK_SIZE_TEXT, 64)
            } catch (_: Exception) {
                64
            }
        }
        set(value) = prefs.edit { putInt(CLOCK_SIZE_TEXT, value) }

    var batterySize: Int
        get() {
            return try {
                prefs.getInt(BATTERY_SIZE_TEXT, 18)
            } catch (_: Exception) {
                18
            }
        }
        set(value) = prefs.edit { putInt(BATTERY_SIZE_TEXT, value) }

    var settingsSize: Int
        get() {
            return try {
                prefs.getInt(TEXT_SIZE_SETTINGS, 16)
            } catch (_: Exception) {
                17
            }
        }
        set(value) = prefs.edit { putInt(TEXT_SIZE_SETTINGS, value) }

    var textPaddingSize: Int
        get() {
            return try {
                prefs.getInt(TEXT_PADDING_SIZE, 12)
            } catch (_: Exception) {
                12
            }
        }
        set(value) = prefs.edit { putInt(TEXT_PADDING_SIZE, value) }

    // Number of characters to show in Home App Name Notifications
    // Remove unused property
    // var homeNotificationCharLimit: Int
    //     get() = prefs.getInt("home_notification_char_limit", 30)
    //     set(value) = prefs.edit { putInt("home_notification_char_limit", value) }

    // Character limit for Home App labels
    var homeAppCharLimit: Int
        get() = prefs.getInt("home_app_char_limit", 20) // default to 20
        set(value) = prefs.edit { putInt("home_app_char_limit", value) }

    private fun getColorInt(type: String): Int {
        when (appTheme) {
            Constants.Theme.System -> {
                return if (isSystemInDarkMode(context)) {
                    if (type == "bg") R.color.black
                    else R.color.white
                } else {
                    if (type == "bg") R.color.white
                    else R.color.black
                }
            }

            Constants.Theme.Dark -> {
                return if (type == "bg") R.color.black
                else R.color.white
            }

            Constants.Theme.Light -> {
                return if (type == "bg") R.color.white  // #FFFFFF for background
                else R.color.black  // #000000 for app, date, clock, alarm, battery
            }
        }
    }

    // return app label
    fun getAppName(location: Int): String {
        return getHomeAppModel(location).activityLabel
    }

    fun remove(prefName: String) {
        prefs.edit { remove(prefName) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun getFontForContext(context: String): Constants.FontFamily {
        return if (universalFontEnabled) universalFont else when (context) {
            "settings" -> fontFamily
            "apps" -> appsFont
            "clock" -> clockFont
            "status" -> statusFont
            "notification" -> labelnotificationsFont
            "battery" -> batteryFont
            "letters" -> lettersFont
            "lettersTitle" -> lettersTitleFont
            "notifications" -> notificationsFont
            else -> Constants.FontFamily.System
        }
    }

    fun getCustomFontPathForContext(context: String): String? {
        return if (universalFontEnabled && universalFont == Constants.FontFamily.Custom) {
            getCustomFontPath("universal")
        } else {
            getCustomFontPath(context)
        }
    }

    // Per-app allowlist (was blocklist)
    var allowedNotificationApps: MutableSet<String>
        get() = prefs.getStringSet("allowed_notification_apps", mutableSetOf()) ?: mutableSetOf()
        set(value) = prefs.edit { putStringSet("allowed_notification_apps", value) }

    // Per-app allowlist for badge notifications
    var allowedBadgeNotificationApps: MutableSet<String>
        get() = prefs.getStringSet("allowed_badge_notification_apps", mutableSetOf())
            ?: mutableSetOf()
        set(value) = prefs.edit { putStringSet("allowed_badge_notification_apps", value) }

    // --- Volume keys for page navigation ---
    var useVolumeKeysForPages: Boolean
        get() = prefs.getBoolean("use_volume_keys_for_pages", false)
        set(value) = prefs.edit { putBoolean("use_volume_keys_for_pages", value) }

    var longPressAppInfoEnabled: Boolean
        get() = prefs.getBoolean("long_press_app_info_enabled", false)
        set(value) = prefs.edit { putBoolean("long_press_app_info_enabled", value) }

    // --- Vibration for paging ---
    var useVibrationForPaging: Boolean
        get() = prefs.getBoolean("use_vibration_for_paging", false)
        set(value) = prefs.edit { putBoolean("use_vibration_for_paging", value) }

    var onboardingPage: Int
        get() = prefs.getInt(ONBOARDING_PAGE, 0)
        set(value) = prefs.edit { putInt(ONBOARDING_PAGE, value) }

    companion object {
        private const val BATTERY_FONT = "battery_font"
        private const val LETTERS_FONT = "letters_font"
        private const val LETTERS_TEXT_SIZE = "letters_text_size"
        private const val LETTERS_TITLE = "letters_title"
        private const val LETTERS_TITLE_FONT = "letters_title_font"
        private const val LETTERS_TITLE_SIZE = "letters_title_size"
        private const val NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val NOTIFICATIONS_FONT = "notifications_font"
        private const val NOTIFICATIONS_TEXT_SIZE = "notifications_text_size"
    }
}