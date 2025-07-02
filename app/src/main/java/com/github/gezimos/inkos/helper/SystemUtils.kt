package com.github.gezimos.inkos.helper

import android.app.Activity
import android.app.UiModeManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.github.gezimos.common.openAccessibilitySettings
import com.github.gezimos.common.showLongToast
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.AppListItem
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.services.ActionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

suspend fun getAppsList(
    context: Context,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppListItem> {
    return withContext(Dispatchers.Main) {
        val appList: MutableList<AppListItem> = mutableListOf()
        val combinedList: MutableList<AppListItem> = mutableListOf()

        try {
            val hiddenApps = Prefs(context).hiddenApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            for (profile in userManager.userProfiles) {
                for (launcherActivityInfo in launcherApps.getActivityList(null, profile)) {
                    val activityName = launcherActivityInfo.name
                    val appPackage = launcherActivityInfo.applicationInfo.packageName
                    val label = launcherActivityInfo.label.toString()

                    if (includeHiddenApps && hiddenApps.contains(appPackage) ||
                        includeRegularApps && !hiddenApps.contains(appPackage)
                    ) {
                        appList.add(
                            AppListItem(
                                activityLabel = label,
                                activityPackage = appPackage,
                                activityClass = activityName,
                                user = profile,
                                customLabel = "" // Add empty string as default custom label
                            )
                        )
                    }
                }
            }
            appList.sort()
            combinedList.addAll(appList)
        } catch (e: Exception) {
            Log.d("appList", e.toString())
        }

        combinedList
    }
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return Process.myUserHandle()
}

fun isinkosDefault(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        val testIntent = Intent(Intent.ACTION_MAIN)
        testIntent.addCategory(Intent.CATEGORY_HOME)
        val defaultHome = testIntent.resolveActivity(context.packageManager)?.packageName
        return defaultHome == context.packageName
    }
}

fun setDefaultHomeScreen(context: Context, checkDefault: Boolean = false) {
    val isDefault = isinkosDefault(context)
    if (checkDefault && isDefault) {
        // Launcher is already the default home app
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && context is Activity
        && !isDefault // using role manager only works when µLauncher is not already the default.
    ) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        context.startActivityForResult(
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
            Constants.REQUEST_SET_DEFAULT_HOME
        )
        return
    }

    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
    context.startActivity(intent)
}

@Suppress("DEPRECATION")
fun checkWhoInstalled(context: Context): String {
    val appName = context.getString(R.string.app_name)
    val descriptionTemplate =
        context.getString(R.string.advanced_settings_share_application_description)
    val descriptionTemplate2 =
        context.getString(R.string.advanced_settings_share_application_description_addon)

    // Get the installer package name
    val installer: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // For Android 11 (API 30) and above
        val installSourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
        installSourceInfo.installingPackageName
    } else {
        // For older versions
        context.packageManager.getInstallerPackageName(context.packageName)
    }

    // Handle null installer package name
    val installSource = when (installer) {
        "com.android.vending" -> "Google Play Store"
        "org.fdroid.fdroid" -> "F-Droid"
        null -> "GitHub" // In case installer is null
        else -> installer // Default to the installer package name
    }

    val installURL = when (installer) {
        "com.android.vending" -> "https://play.google.com/store/apps/details?id=app.mlauncher"
        "org.fdroid.fdroid" -> "https://f-droid.org/packages/app.mlauncher"
        null -> "https://github.com/DroidWorksStudio/mLauncher" // In case installer is null
        else -> "https://play.google.com/store/apps/details?id=app.mlauncher" // Default to the Google Play Store
    }

    // Format the description with the app name and install source
    return String.format(
        "%s %s",
        String.format(descriptionTemplate, appName),
        String.format(descriptionTemplate2, installSource, installURL)
    )
}


fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        launcher.startAppDetailsActivity(intent.component, userHandle, null, null)
    } ?: context.showLongToast("Unable to to open app info")
}


fun isTablet(context: Context): Boolean {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getMetrics(metrics)
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    if (diagonalInches >= 7.0) return true
    return false
}

fun initActionService(context: Context): ActionService? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val actionService = ActionService.instance()
        if (actionService != null) {
            return actionService
        } else {
            context.openAccessibilitySettings()
        }
    } else {
        context.showLongToast("This action requires Android P (9) or higher")
    }

    return null
}


fun showStatusBar(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        activity.window.insetsController?.show(WindowInsets.Type.statusBars())
    else
        @Suppress("DEPRECATION", "InlinedApi")
        activity.window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
}

fun hideStatusBar(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        activity.window.insetsController?.hide(WindowInsets.Type.statusBars())
    else {
        @Suppress("DEPRECATION")
        activity.window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}

fun dp2px(resources: Resources, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun storeFile(activity: Activity, backupType: Constants.BackupType) {
    // Generate a unique filename with a timestamp
    when (backupType) {
        Constants.BackupType.FullSystem -> {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "backup_$timeStamp.json"

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            activity.startActivityForResult(intent, Constants.BACKUP_WRITE, null)
        }

        Constants.BackupType.Theme -> {
            val timeStamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val fileName = "theme_$timeStamp.mtheme"

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            activity.startActivityForResult(intent, Constants.THEME_BACKUP_WRITE, null)
        }

    }

}

fun loadFile(activity: Activity, backupType: Constants.BackupType) {
    when (backupType) {
        Constants.BackupType.FullSystem -> {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Use generic type for compatibility
            }
            if (intent.resolveActivity(activity.packageManager) == null) {
                // Show error dialog if no file picker is available
                try {
                    val dialogManagerClass =
                        Class.forName("com.github.gezimos.inkos.ui.dialogs.DialogManager")
                    val constructor =
                        dialogManagerClass.getConstructor(Context::class.java, Activity::class.java)
                    val dialogManager = constructor.newInstance(activity, activity)
                    val showErrorDialog = dialogManagerClass.getMethod(
                        "showErrorDialog",
                        Context::class.java,
                        String::class.java,
                        String::class.java
                    )
                    showErrorDialog.invoke(
                        dialogManager,
                        activity,
                        activity.getString(R.string.error_no_file_picker_title),
                        activity.getString(R.string.error_no_file_picker_message)
                    )
                } catch (e: Exception) {
                    // fallback: show toast
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.error_no_file_picker_message),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            activity.startActivityForResult(intent, Constants.BACKUP_READ, null)
        }

        Constants.BackupType.Theme -> {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
            }
            if (intent.resolveActivity(activity.packageManager) == null) {
                try {
                    val dialogManagerClass =
                        Class.forName("com.github.gezimos.inkos.ui.dialogs.DialogManager")
                    val constructor =
                        dialogManagerClass.getConstructor(Context::class.java, Activity::class.java)
                    val dialogManager = constructor.newInstance(activity, activity)
                    val showErrorDialog = dialogManagerClass.getMethod(
                        "showErrorDialog",
                        Context::class.java,
                        String::class.java,
                        String::class.java
                    )
                    showErrorDialog.invoke(
                        dialogManager,
                        activity,
                        activity.getString(R.string.error_no_file_picker_title),
                        activity.getString(R.string.error_no_file_picker_message)
                    )
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.error_no_file_picker_message),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            activity.startActivityForResult(intent, Constants.THEME_BACKUP_READ, null)
        }
    }

}

fun getHexForOpacity(prefs: Prefs): Int {
    val backgroundColor = prefs.backgroundColor
    return backgroundColor // Just return the background color without opacity modification
}

fun isSystemInDarkMode(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
}

fun setThemeMode(context: Context, isDark: Boolean, view: View) {
    // Retrieve background color based on the theme
    val backgroundAttr = if (isDark) R.attr.backgroundDark else R.attr.backgroundLight

    val typedValue = TypedValue()
    val theme: Resources.Theme = context.theme
    theme.resolveAttribute(backgroundAttr, typedValue, true)

    // Apply the background color from styles.xml
    view.setBackgroundResource(typedValue.resourceId)
}

fun parseBlacklistXML(context: Context): List<String> {
    val packageNames = mutableListOf<String>()

    // Obtain an XmlPullParser for the blacklist.xml file
    context.resources.getXml(R.xml.blacklist).use { parser ->
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "app") {
                val packageName = parser.getAttributeValue(null, "packageName")
                packageNames.add(packageName)
            }
            parser.next()
        }
    }

    return packageNames
}

fun getTrueSystemFont(): Typeface {
    val possibleSystemFonts = listOf(
        "/system/fonts/Roboto-Regular.ttf",      // Stock Android (Pixel, AOSP)
        "/system/fonts/NotoSans-Regular.ttf",    // Some Android One devices
        "/system/fonts/SamsungOne-Regular.ttf",  // Samsung
        "/system/fonts/MiSans-Regular.ttf",      // Xiaomi MIUI
        "/system/fonts/OPSans-Regular.ttf"       // OnePlus
    )

    for (fontPath in possibleSystemFonts) {
        val fontFile = File(fontPath)
        if (fontFile.exists()) {
            return Typeface.createFromFile(fontFile)
        }
    }

    // Fallback to Roboto as a default if no system font is found
    return Typeface.DEFAULT
}

fun formatLongToCalendar(longTimestamp: Long): String {
    // Create a Calendar instance and set its time to the given timestamp (in milliseconds)
    val calendar = Calendar.getInstance().apply {
        timeInMillis = longTimestamp
    }

    // Format the calendar object to a readable string
    val dateFormat = SimpleDateFormat(
        "MMMM dd, yyyy, HH:mm:ss",
        Locale.getDefault()
    ) // You can modify the format
    return dateFormat.format(calendar.time) // Return the formatted date string
}

fun formatMillisToHMS(millis: Long, showSeconds: Boolean): String {
    val hours = millis / (1000 * 60 * 60)
    val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (millis % (1000 * 60)) / 1000

    val formattedString = StringBuilder()
    if (hours > 0) {
        formattedString.append("$hours h ")
    }
    if (minutes > 0 || hours > 0) {
        formattedString.append("$minutes m ")
    }
    // Only append seconds if showSeconds is true
    if (showSeconds) {
        formattedString.append("$seconds s")
    }

    return formattedString.toString().trim()
}
