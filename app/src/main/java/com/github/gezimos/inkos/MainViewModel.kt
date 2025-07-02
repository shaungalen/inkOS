package com.github.gezimos.inkos

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import com.github.gezimos.common.CrashHandler
import com.github.gezimos.common.hideKeyboard
import com.github.gezimos.common.showShortToast
import com.github.gezimos.inkos.data.AppListItem
import com.github.gezimos.inkos.data.Constants.AppDrawerFlag
import com.github.gezimos.inkos.data.HomeAppUiState
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getAppsList
import com.github.gezimos.inkos.helper.isinkosDefault
import com.github.gezimos.inkos.helper.setDefaultHomeScreen
import com.github.gezimos.inkos.helper.utils.BiometricHelper
import com.github.gezimos.inkos.services.NotificationManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private lateinit var biometricHelper: BiometricHelper

    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    // setup variables with initial values
    val firstOpen = MutableLiveData<Boolean>()

    val appList = MutableLiveData<List<AppListItem>?>()
    val hiddenApps = MutableLiveData<List<AppListItem>?>()
    val homeAppsOrder = MutableLiveData<List<AppListItem>>()  // Store actual app items
    val launcherDefault = MutableLiveData<Boolean>()

    val showClock = MutableLiveData(prefs.showClock)
    val homeAppsNum = MutableLiveData(prefs.homeAppsNum)
    val homePagesNum = MutableLiveData(prefs.homePagesNum)

    val appTheme = MutableLiveData(prefs.appTheme)
    val appColor = MutableLiveData(prefs.appColor)
    val backgroundColor = MutableLiveData(prefs.backgroundColor)
    val clockColor = MutableLiveData(prefs.clockColor)
    val batteryColor = MutableLiveData(prefs.batteryColor)
    val appsFont = MutableLiveData(prefs.appsFont)
    val clockFont = MutableLiveData(prefs.clockFont)
    val batteryFont = MutableLiveData(prefs.batteryFont)
    val notificationsFont = MutableLiveData(prefs.notificationsFont)
    val notificationFont = MutableLiveData(prefs.labelnotificationsFont)
    val statusFont = MutableLiveData(prefs.statusFont)
    val lettersFont = MutableLiveData(prefs.lettersFont)
    val lettersTitleFont = MutableLiveData(prefs.lettersTitleFont)
    val textPaddingSize = MutableLiveData(prefs.textPaddingSize)
    val appSize = MutableLiveData(prefs.appSize)
    val clockSize = MutableLiveData(prefs.clockSize)
    val batterySize = MutableLiveData(prefs.batterySize)

    // --- Home screen UI state ---
    private val _homeAppsUiState = MutableLiveData<List<HomeAppUiState>>()
    val homeAppsUiState: LiveData<List<HomeAppUiState>> = _homeAppsUiState

    fun updateMediaPlaybackInfo(info: NotificationManager.NotificationInfo?) {
        // _mediaPlaybackInfo.postValue(info)
    }

    // Listen for preference changes and update LiveData
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "APP_THEME" -> appTheme.postValue(prefs.appTheme)
            "APP_COLOR" -> appColor.postValue(prefs.appColor)
            "BACKGROUND_COLOR" -> backgroundColor.postValue(prefs.backgroundColor)
            "CLOCK_COLOR" -> clockColor.postValue(prefs.clockColor)
            "BATTERY_COLOR" -> batteryColor.postValue(prefs.batteryColor)
            "APPS_FONT" -> appsFont.postValue(prefs.appsFont)
            "CLOCK_FONT" -> clockFont.postValue(prefs.clockFont)
            "BATTERY_FONT" -> batteryFont.postValue(prefs.batteryFont)
            "NOTIFICATIONS_FONT" -> notificationsFont.postValue(prefs.notificationsFont)
            "NOTIFICATION_FONT" -> notificationFont.postValue(prefs.labelnotificationsFont)
            "STATUS_FONT" -> statusFont.postValue(prefs.statusFont)
            "LETTERS_FONT" -> lettersFont.postValue(prefs.lettersFont)
            "LETTERS_TITLE_FONT" -> lettersTitleFont.postValue(prefs.lettersTitleFont)
            "TEXT_PADDING_SIZE" -> textPaddingSize.postValue(prefs.textPaddingSize)
            "APP_SIZE_TEXT" -> appSize.postValue(prefs.appSize)
            "CLOCK_SIZE_TEXT" -> clockSize.postValue(prefs.clockSize)
            "BATTERY_SIZE_TEXT" -> batterySize.postValue(prefs.batterySize)
        }
    }

    init {
        prefs.sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        prefs.sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    // Call this to refresh home app UI state (labels, fonts, colors, badges)
    fun refreshHomeAppsUiState(context: Context) {
        val notifications =
            NotificationManager.getInstance(context).notificationInfoLiveData.value ?: emptyMap()
        val appColor = prefs.appColor
        val appFont = prefs.getFontForContext("apps")
            .getFont(context, prefs.getCustomFontPathForContext("apps"))
        val homeApps = (0 until prefs.homeAppsNum).map { i ->
            val appModel = prefs.getHomeAppModel(i)
            val customLabel = prefs.getAppAlias("app_alias_${appModel.activityPackage}")
            val label = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel
            val notificationInfo = notifications[appModel.activityPackage]
            HomeAppUiState(
                id = i,
                label = label,
                font = appFont,
                color = appColor,
                notificationInfo = notificationInfo,
                activityPackage = appModel.activityPackage // Pass unique identifier
            )
        }
        _homeAppsUiState.postValue(homeApps)
    }

    fun selectedApp(fragment: Fragment, app: AppListItem, flag: AppDrawerFlag, n: Int = 0) {
        when (flag) {
            AppDrawerFlag.LaunchApp,
            AppDrawerFlag.HiddenApps,
            AppDrawerFlag.PrivateApps -> {
                launchApp(app, fragment)
            }

            AppDrawerFlag.SetHomeApp -> {
                prefs.setHomeAppModel(n, app)
                findNavController(fragment).popBackStack()
            }

            AppDrawerFlag.SetSwipeLeft -> prefs.appSwipeLeft = app
            AppDrawerFlag.SetSwipeRight -> prefs.appSwipeRight = app
            AppDrawerFlag.SetDoubleTap -> prefs.appDoubleTap = app
            AppDrawerFlag.SetClickClock -> { /* no-op or implement if needed */
            }

            AppDrawerFlag.SetSwipeUp, AppDrawerFlag.SetSwipeDown -> { /* no-op, removed */
            }
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }


    fun setShowClock(visibility: Boolean) {
        showClock.value = visibility
    }

    fun setDefaultLauncher(visibility: Boolean) {
        launcherDefault.value = visibility
    }

    fun launchApp(appListItem: AppListItem, fragment: Fragment) {
        biometricHelper = BiometricHelper(fragment)

        val packageName = appListItem.activityPackage
        val currentLockedApps = prefs.lockedApps

        if (currentLockedApps.contains(packageName)) {
            fragment.hideKeyboard()
            biometricHelper.startBiometricAuth(appListItem, object : BiometricHelper.CallbackApp {
                override fun onAuthenticationSucceeded(appListItem: AppListItem) {
                    launchUnlockedApp(appListItem)
                }

                override fun onAuthenticationFailed() {
                    Log.e(
                        "Authentication",
                        appContext.getString(R.string.text_authentication_failed)
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> Log.e(
                            "Authentication",
                            appContext.getString(R.string.text_authentication_cancel)
                        )

                        else -> Log.e(
                            "Authentication",
                            appContext.getString(R.string.text_authentication_error).format(
                                errorMessage,
                                errorCode
                            )
                        )
                    }
                }
            })
        } else {
            launchUnlockedApp(appListItem)
        }
    }

    private fun launchUnlockedApp(appListItem: AppListItem) {
        val packageName = appListItem.activityPackage
        val userHandle = appListItem.user
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        if (activityInfo.isNotEmpty()) {
            val component = ComponentName(packageName, activityInfo.first().name)
            launchAppWithPermissionCheck(component, packageName, userHandle, launcher)
        } else {
            appContext.showShortToast("App not found")
        }
    }

    private fun launchAppWithPermissionCheck(
        component: ComponentName,
        packageName: String,
        userHandle: UserHandle,
        launcher: LauncherApps
    ) {
        try {
            launcher.startMainActivity(component, userHandle, null, null)
            CrashHandler.logUserAction("${component.packageName} App Launched")
        } catch (_: SecurityException) {
            try {
                launcher.startMainActivity(component, Process.myUserHandle(), null, null)
                CrashHandler.logUserAction("${component.packageName} App Launched")
            } catch (_: Exception) {
                appContext.showShortToast("Unable to launch app")
            }
        } catch (_: Exception) {
            appContext.showShortToast("Unable to launch app")
        }
    }

    fun getAppList(includeHiddenApps: Boolean = true) {
        viewModelScope.launch {
            val apps = getAppsList(
                appContext,
                includeRegularApps = true,
                includeHiddenApps = includeHiddenApps
            )
            // Load custom labels for each app
            apps.forEach { app ->
                val customLabel = prefs.getAppAlias("app_alias_${app.activityPackage}")
                if (customLabel.isNotEmpty()) {
                    app.customLabel = customLabel
                }
            }

            // Filter out hidden apps if not including them
            val filteredApps = if (!includeHiddenApps) {
                val hiddenApps = prefs.hiddenApps
                apps.filter { app ->
                    !hiddenApps.contains("${app.activityPackage}|${app.user}")
                }
            } else {
                apps
            }

            appList.value = filteredApps
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            val hiddenSet = prefs.hiddenApps
            val hiddenAppsList = mutableListOf<AppListItem>()

            // Get all installed apps
            val allApps =
                getAppsList(appContext, includeRegularApps = true, includeHiddenApps = true)

            // For each hidden app package+user combination
            for (hiddenApp in hiddenSet) {
                try {
                    // Split the stored string into package name and user handle
                    val parts = hiddenApp.split("|")
                    val packageName = parts[0]

                    // Find matching app
                    val app = if (parts.size > 1) {
                        allApps.find { app ->
                            app.activityPackage == packageName &&
                                    app.user.toString() == parts[1]
                        }
                    } else {
                        allApps.find { app ->
                            app.activityPackage == packageName
                        }
                    }

                    // Load custom label if it exists
                    app?.let {
                        val customLabel = prefs.getAppAlias("app_alias_${it.activityPackage}")
                        if (customLabel.isNotEmpty()) {
                            it.customLabel = customLabel
                        }
                        hiddenAppsList.add(it)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error processing hidden app: $hiddenApp", e)
                    continue
                }
            }

            hiddenApps.postValue(hiddenAppsList)
        }
    }

    fun isinkosDefault() {
        val isDefault = isinkosDefault(appContext)
        launcherDefault.value = !isDefault
    }

    fun resetDefaultLauncherApp(context: Context) {
        setDefaultHomeScreen(context)
    }

    fun updateAppOrder(fromPosition: Int, toPosition: Int) {
        val currentOrder = homeAppsOrder.value?.toMutableList() ?: return

        // Move the actual app object in the list
        val app = currentOrder.removeAt(fromPosition)
        currentOrder.add(toPosition, app)

        homeAppsOrder.postValue(currentOrder)
        saveAppOrder(currentOrder)  // Save new order in preferences
    }

    private fun saveAppOrder(order: List<AppListItem>) {
        order.forEachIndexed { index, app ->
            prefs.setHomeAppModel(index, app)  // Save app in its new order
        }
    }

    fun loadAppOrder() {
        val savedOrder = (0 until prefs.homeAppsNum)
            .mapNotNull { i ->
                prefs.getHomeAppModel(i).let { app ->
                    // Check for custom label
                    val customLabel = prefs.getAppAlias("app_alias_${app.activityPackage}")
                    if (customLabel.isNotEmpty()) {
                        app.customLabel = customLabel
                    }
                    app
                }
            }
        homeAppsOrder.postValue(savedOrder)
    }

    // --- App Drawer actions ---
    fun refreshAppListAfterUninstall(includeHiddenApps: Boolean = false) {
        getAppList(includeHiddenApps)
    }

    fun renameApp(packageName: String, newName: String) {
        if (newName.isEmpty()) {
            prefs.removeAppAlias(packageName)
        } else {
            prefs.setAppAlias(packageName, newName)
        }
        // Refresh app list to update labels
        getAppList(includeHiddenApps = false)
        getHiddenApps()
    }

    fun hideOrShowApp(flag: AppDrawerFlag, appModel: AppListItem) {
        val newSet = mutableSetOf<String>()
        newSet.addAll(prefs.hiddenApps)
        if (flag == AppDrawerFlag.HiddenApps) {
            newSet.remove(appModel.activityPackage)
            newSet.remove(appModel.activityPackage + "|" + appModel.user.toString())
        } else {
            newSet.add(appModel.activityPackage + "|" + appModel.user.toString())
        }
        prefs.hiddenApps = newSet
        getAppList(includeHiddenApps = (flag == AppDrawerFlag.HiddenApps))
        getHiddenApps()
    }
}