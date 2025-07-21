package com.github.gezimos.inkos.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.ImageSpan
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.gezimos.common.CrashHandler
import com.github.gezimos.common.hideKeyboard
import com.github.gezimos.common.openAlarmApp
import com.github.gezimos.common.openBatteryManager
import com.github.gezimos.common.openCameraApp
import com.github.gezimos.common.openDialerApp
import com.github.gezimos.common.showShortToast
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants.Action
import com.github.gezimos.inkos.data.Constants.AppDrawerFlag
import com.github.gezimos.inkos.data.HomeAppUiState
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.databinding.FragmentHomeBinding
import com.github.gezimos.inkos.helper.hideStatusBar
import com.github.gezimos.inkos.helper.isinkosDefault
import com.github.gezimos.inkos.helper.openAppInfo
import com.github.gezimos.inkos.helper.receivers.BatteryReceiver
import com.github.gezimos.inkos.helper.showStatusBar
import com.github.gezimos.inkos.helper.utils.AppReloader
import com.github.gezimos.inkos.helper.utils.BiometricHelper
import com.github.gezimos.inkos.helper.utils.NotificationBadgeUtil
import com.github.gezimos.inkos.helper.utils.PrivateSpaceManager
import com.github.gezimos.inkos.listener.OnSwipeTouchListener
import com.github.gezimos.inkos.listener.ViewSwipeTouchListener
import com.github.gezimos.inkos.services.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    companion object {
        @JvmStatic var isHomeVisible: Boolean = false
        @JvmStatic var goToFirstPageSignal: Boolean = false
        @JvmStatic fun sendGoToFirstPageSignal() { goToFirstPageSignal = true }
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var vibrator: Vibrator

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var selectedAppIndex = 0
    private var userPresentReceiver: android.content.BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        prefs = Prefs(requireContext())
        batteryReceiver = BatteryReceiver()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometricHelper = BiometricHelper(this)
        viewModel = activity?.run { ViewModelProvider(this)[MainViewModel::class.java] } ?: throw Exception("Invalid Activity")
        viewModel.isinkosDefault()
        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        initObservers()
        initClickListeners()
        initSwipeTouchListener()

        viewModel.homeAppsUiState.observe(viewLifecycleOwner) { homeAppsUiState ->
            updateHomeAppsUi(homeAppsUiState)
        }

        NotificationManager.getInstance(requireContext()).notificationInfoLiveData.observe(viewLifecycleOwner) { notifications ->
            viewModel.refreshHomeAppsUiState(requireContext())
            val mediaNotification = notifications.values.firstOrNull { it.category == android.app.Notification.CATEGORY_TRANSPORT }
            viewModel.updateMediaPlaybackInfo(mediaNotification)
        }

        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { moveSelectionDown(); true }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { handleOtherAction(prefs.swipeRightAction); CrashHandler.logUserAction("DPAD_LEFT Gesture (SwipeRight)"); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { handleOtherAction(prefs.swipeLeftAction); CrashHandler.logUserAction("DPAD_RIGHT Gesture (SwipeLeft)"); true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (event.isLongPress) { binding.homeAppsLayout.getChildAt(selectedAppIndex)?.let { onLongClick(it) }; true } else false
                    }
                    android.view.KeyEvent.KEYCODE_9 -> { if (event.isLongPress) { trySettings(); true } else false }
                    else -> false
                }
            } else false
        }
    }

    private fun updateHomeAppsUi(homeAppsUiState: List<HomeAppUiState>) {
        val notifications = NotificationManager.getInstance(requireContext()).notificationInfoLiveData.value ?: emptyMap()
        homeAppsUiState.forEach { uiState ->
            val view = binding.homeAppsLayout.findViewWithTag<TextView>(uiState.activityPackage)
            if (view != null) {
                NotificationBadgeUtil.updateNotificationForView(requireContext(), prefs, view, notifications)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideKeyboard()
        isHomeVisible = true
        com.github.gezimos.inkos.helper.utils.EinkRefreshHelper.refreshEink(requireContext(), prefs, binding.root as? ViewGroup)
        if (goToFirstPageSignal) {
            currentPage = 0
            selectedAppIndex = 0
            goToFirstPageSignal = false
            updateAppsVisibility(prefs.homePagesNum)
            focusAppButton(selectedAppIndex)
        } else {
            updateAppsVisibility(prefs.homePagesNum)
            focusAppButton(selectedAppIndex)
        }
        viewModel.refreshHomeAppsUiState(requireContext())
    }

    override fun onPause() {
        super.onPause()
        isHomeVisible = false
    }

    private fun moveSelectionDown() {
        val totalApps = getTotalAppsCount()
        val totalPages = prefs.homePagesNum
        val appsPerPage = if (totalPages > 0) (totalApps + totalPages - 1) / totalPages else 0
        val endIdx = minOf((currentPage + 1) * appsPerPage, totalApps) - 1
        if (selectedAppIndex < endIdx) selectedAppIndex++ else if (currentPage < totalPages - 1) { currentPage++; selectedAppIndex = currentPage * appsPerPage } else { currentPage = 0; selectedAppIndex = 0 }
        updateAppsVisibility(totalPages)
        focusAppButton(selectedAppIndex)
    }

    private fun focusAppButton(index: Int) {
        binding.homeAppsLayout.getChildAt(index)?.requestFocus()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStart() {
        super.onStart()
        if (prefs.showStatusBar) showStatusBar(requireActivity()) else hideStatusBar(requireActivity())

        batteryReceiver = BatteryReceiver()
        try { requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { e.printStackTrace() }

        if (userPresentReceiver == null) {
            userPresentReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_PRESENT) {
                        if (isAdded) {
                            if (prefs.einkRefreshEnabled) {
                                val isDark = when (prefs.appTheme) {
                                    com.github.gezimos.inkos.data.Constants.Theme.Light -> false
                                    com.github.gezimos.inkos.data.Constants.Theme.Dark -> true
                                    com.github.gezimos.inkos.data.Constants.Theme.System -> com.github.gezimos.inkos.helper.isSystemInDarkMode(requireContext())
                                }
                                val overlayColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                val overlay = View(requireContext())
                                overlay.setBackgroundColor(overlayColor)
                                overlay.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                (binding.root as ViewGroup).addView(overlay)
                                overlay.bringToFront()
                                overlay.postDelayed({ (binding.root as ViewGroup).removeView(overlay) }, 120)
                            }
                            if (goToFirstPageSignal) {
                                currentPage = 0
                                selectedAppIndex = 0
                                goToFirstPageSignal = false
                                updateAppsVisibility(prefs.homePagesNum)
                                focusAppButton(selectedAppIndex)
                            } else {
                                updateAppsVisibility(prefs.homePagesNum)
                                focusAppButton(selectedAppIndex)
                            }
                            viewModel.refreshHomeAppsUiState(requireContext())
                        }
                    }
                }
            }
            requireContext().registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }

        binding.apply {
            val is24HourFormat = DateFormat.is24HourFormat(requireContext())
            val timePattern = if (is24HourFormat) "HH:mm" else "hh:mm"
            clock.format12Hour = timePattern
            clock.format24Hour = timePattern

            battery.textSize = prefs.batterySize.toFloat()
            homeScreenPager.textSize = prefs.appSize.toFloat()
            battery.visibility = if (prefs.showBattery) View.VISIBLE else View.GONE
            mainLayout.setBackgroundColor(prefs.backgroundColor)
            clock.setTextColor(prefs.clockColor)
            battery.setTextColor(prefs.batteryColor)

            homeAppsLayout.children.forEach { view ->
                if (view is TextView) {
                    view.setTextColor(prefs.appColor)
                    view.typeface = prefs.getFontForContext("apps").getFont(requireContext(), prefs.getCustomFontPathForContext("apps"))
                }
            }
            clock.typeface = prefs.getFontForContext("clock").getFont(requireContext(), prefs.getCustomFontPathForContext("clock"))
            battery.typeface = prefs.getFontForContext("battery").getFont(requireContext(), prefs.getCustomFontPathForContext("battery"))
        }

        binding.homeAppsLayout.children.forEach { view ->
            if (view is TextView) {
                val appModel = prefs.getHomeAppModel(view.id)
                val customLabel = prefs.getAppAlias("app_alias_${appModel.activityPackage}")
                view.text = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel
                view.typeface = prefs.getFontForContext("apps").getFont(requireContext(), prefs.getCustomFontPathForContext("apps"))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try { requireContext().unregisterReceiver(batteryReceiver) } catch (e: Exception) { e.printStackTrace() }
        if (userPresentReceiver != null) {
            try { requireContext().unregisterReceiver(userPresentReceiver) } catch (_: Exception) {}
            userPresentReceiver = null
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> { handleOtherAction(prefs.clickClockAction); CrashHandler.logUserAction("Clock Clicked") }
            R.id.setDefaultLauncher -> { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)); CrashHandler.logUserAction("SetDefaultLauncher Clicked") }
            R.id.battery -> { requireContext().openBatteryManager(); CrashHandler.logUserAction("Battery Clicked") }
            else -> { try { homeAppClicked(view.id) } catch (e: Exception) { e.printStackTrace() } }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (prefs.homeLocked) {
            if (prefs.longPressAppInfoEnabled) {
                val n = view.id
                val appModel = prefs.getHomeAppModel(n)
                if (appModel.activityPackage.isNotEmpty()) {
                    openAppInfo(requireContext(), appModel.user, appModel.activityPackage)
                    CrashHandler.logUserAction("Show App Info")
                }
                return true
            }
            return true
        }
        val n = view.id
        showAppList(AppDrawerFlag.SetHomeApp, includeHiddenApps = true, n)
        CrashHandler.logUserAction("Show App List")
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSwipeTouchListener() {
        binding.touchArea.setOnTouchListener(object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeLeft() { handleOtherAction(prefs.swipeLeftAction); CrashHandler.logUserAction("SwipeLeft Gesture") }
            override fun onSwipeRight() { handleOtherAction(prefs.swipeRightAction); CrashHandler.logUserAction("SwipeRight Gesture") }
            override fun onSwipeUp() { handleSwipeRight(prefs.homePagesNum); CrashHandler.logUserAction("SwipeUp Gesture (NextPage)") }
            override fun onSwipeDown() { handleSwipeLeft(prefs.homePagesNum); CrashHandler.logUserAction("SwipeDown Gesture (PreviousPage)") }
            override fun onLongClick() { CrashHandler.logUserAction("Launcher Settings Opened"); trySettings() }
            override fun onDoubleClick() { handleOtherAction(prefs.doubleTapAction); CrashHandler.logUserAction("DoubleClick Gesture") }
            override fun onLongSwipe(direction: String) {
                when (direction) {
                    "up" -> handleSwipeRight(prefs.homePagesNum)
                    "down" -> handleSwipeLeft(prefs.homePagesNum)
                    "left" -> handleOtherAction(prefs.swipeLeftAction)
                    "right" -> handleOtherAction(prefs.swipeRightAction)
                }
                CrashHandler.logUserAction("LongSwipe_${direction} Gesture")
            }
        })
    }

    private fun initClickListeners() {
        binding.apply {
            clock.setOnClickListener(this@HomeFragment)
            setDefaultLauncher.setOnClickListener(this@HomeFragment)
            battery.setOnClickListener(this@HomeFragment)
        }
    }

    private fun initObservers() {
        binding.apply {
            if (!isinkosDefault(requireContext())) setDefaultLauncher.visibility = View.VISIBLE
            else setDefaultLauncher.visibility = View.GONE
            clock.gravity = Gravity.CENTER
            clock.layoutParams = (clock.layoutParams as LinearLayout.LayoutParams).apply { gravity = Gravity.CENTER }
            homeAppsLayout.children.forEach { (it as? TextView)?.gravity = Gravity.CENTER }
        }
        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) { updateAppCount(it) }
            showClock.observe(viewLifecycleOwner) { binding.clock.visibility = if (it) View.VISIBLE else View.GONE }
            launcherDefault.observe(viewLifecycleOwner) { binding.setDefaultLauncher.visibility = if (it) View.VISIBLE else View.GONE }
            appTheme.observe(viewLifecycleOwner) { }
            appColor.observe(viewLifecycleOwner) { color ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) { view.setTextColor(color); view.setHintTextColor(color) }
                }
                binding.homeScreenPager.setTextColor(color)
            }
            backgroundColor.observe(viewLifecycleOwner) { color -> binding.mainLayout.setBackgroundColor(color) }
            clockColor.observe(viewLifecycleOwner) { color -> binding.clock.setTextColor(color) }
            batteryColor.observe(viewLifecycleOwner) { color -> binding.battery.setTextColor(color) }
            appsFont.observe(viewLifecycleOwner) { font ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) { view.typeface = prefs.getFontForContext("apps").getFont(requireContext(), prefs.getCustomFontPathForContext("apps")) }
                }
            }
            clockFont.observe(viewLifecycleOwner) { font -> binding.clock.typeface = prefs.getFontForContext("clock").getFont(requireContext(), prefs.getCustomFontPathForContext("clock")) }
            batteryFont.observe(viewLifecycleOwner) { font -> binding.battery.typeface = prefs.getFontForContext("battery").getFont(requireContext(), prefs.getCustomFontPathForContext("battery")) }
            textPaddingSize.observe(viewLifecycleOwner) { padding ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) { view.setPadding(0, padding, 0, padding) }
                }
            }
            appSize.observe(viewLifecycleOwner) { size ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) { view.textSize = size.toFloat() }
                }
            }
            clockSize.observe(viewLifecycleOwner) { size -> binding.clock.textSize = size.toFloat() }
            batterySize.observe(viewLifecycleOwner) { size -> binding.battery.textSize = size.toFloat() }
        }
    }

    // --- SWIPE-TO-DISMISS LOGIC for notifications ---
    @SuppressLint("InflateParams")
    private fun updateAppCount(newAppsNum: Int) {
        val oldAppsNum = binding.homeAppsLayout.childCount
        val diff = newAppsNum - oldAppsNum
        updateHomeAppsPadding()
        val context = requireContext()
        val notifications = NotificationManager.getInstance(context).notificationInfoLiveData.value ?: emptyMap()
        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                val view = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                view.apply {
                    textSize = prefs.appSize.toFloat()
                    id = i
                    val appModel = prefs.getHomeAppModel(i)
                    val customLabel = prefs.getAppAlias("app_alias_${appModel.activityPackage}")
                    val displayText = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel
                    NotificationBadgeUtil.updateNotificationForView(context, prefs, this, notifications)
                    setTextColor(prefs.appColor)
                    setHintTextColor(prefs.appColor)
                    setOnClickListener(this@HomeFragment)
                    setOnLongClickListener(this@HomeFragment)
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    typeface = prefs.getFontForContext("apps").getFont(context, prefs.getCustomFontPathForContext("apps"))
                    tag = appModel.activityPackage

                    // SWIPE-TO-DISMISS
                    val notificationInfo = notifications[appModel.activityPackage]
                    if (notificationInfo != null && notificationInfo.count > 0) {
                        setOnTouchListener(object : View.OnTouchListener {
                            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                private val SWIPE_THRESHOLD = 100
                                private val SWIPE_VELOCITY_THRESHOLD = 100
                                override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                                    if (e1 != null && e2 != null) {
                                        val diffX = e2.x - e1.x
                                        if (diffX < -SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                            NotificationManager.getInstance(context).updateBadgeNotification(appModel.activityPackage, null)
                                            // Update badge immediately
                                            val updatedNotifications = NotificationManager.getInstance(context).notificationInfoLiveData.value ?: emptyMap()
                                            NotificationBadgeUtil.updateNotificationForView(context, prefs, this@apply, updatedNotifications)
                                            return true
                                        }
                                    }
                                    return false
                                }
                            })
                            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                                return gestureDetector.onTouchEvent(event)
                            }
                        })
                    }
                }
                binding.homeAppsLayout.addView(view)
            }
        } else if (diff < 0) {
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }
        updatePagesAndAppsPerPage(prefs.homeAppsNum, prefs.homePagesNum)
    }
    // --- END SWIPE-TO-DISMISS LOGIC ---

    private fun updateHomeAppsPadding() {
        binding.apply {
            val hasVisibleWidgets = clock.isVisible
            val defaultPadding = resources.getDimensionPixelSize(R.dimen.home_apps_default_padding)
            homeAppsLayout.setPadding(
                homeAppsLayout.paddingLeft,
                if (hasVisibleWidgets) defaultPadding else 0,
                homeAppsLayout.paddingRight,
                homeAppsLayout.paddingBottom
            )
        }
    }

    private var currentPage = 0
    private var appsPerPage = 0

    private fun updatePagesAndAppsPerPage(totalApps: Int, totalPages: Int) {
        appsPerPage = if (totalPages > 0) (totalApps + totalPages - 1) / totalPages else 0
        updateAppsVisibility(totalPages)
        binding.homeScreenPager.apply {
            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                bottomMargin = 0
                topMargin = 0
                marginEnd = 16
            }
        }
    }

    private fun updateAppsVisibility(totalPages: Int) {
        val startIdx = currentPage * appsPerPage
        val endIdx = minOf((currentPage + 1) * appsPerPage, getTotalAppsCount())
        for (i in 0 until getTotalAppsCount()) {
            val view = binding.homeAppsLayout.getChildAt(i)
            view.visibility = if (i in startIdx until endIdx) View.VISIBLE else View.GONE
        }
        val pageSelectorIcons = MutableList(totalPages) { _ -> R.drawable.ic_new_page }
        pageSelectorIcons[currentPage] = R.drawable.ic_current_page

        val spannable = SpannableStringBuilder()
        val sizeInDp = 12
        val density = requireContext().resources.displayMetrics.density
        val sizeInPx = (sizeInDp * density).toInt()
        pageSelectorIcons.forEach { drawableRes ->
            val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)?.apply {
                setBounds(0, 0, sizeInPx, sizeInPx)
                val colorFilterColor: ColorFilter = PorterDuffColorFilter(prefs.appColor, PorterDuff.Mode.SRC_IN)
                colorFilter = colorFilterColor
            }
            val imageSpan = drawable?.let { ImageSpan(it, ImageSpan.ALIGN_BOTTOM) }
            val placeholder = SpannableStringBuilder(" ")
            imageSpan?.let { placeholder.setSpan(it, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            spannable.append(placeholder)
            spannable.append("\u2006\u2009")
        }
        binding.homeScreenPager.text = spannable
        if (prefs.homePagesNum > 1 && prefs.homePager) binding.homeScreenPager.visibility = View.VISIBLE
    }

    private fun handleSwipeLeft(totalPages: Int) {
        if (totalPages <= 0) return
        currentPage = if (currentPage == 0) totalPages - 1 else currentPage - 1
        updateAppsVisibility(totalPages)
        vibratePaging()
    }

    private fun handleSwipeRight(totalPages: Int) {
        if (totalPages <= 0) return
        currentPage = if (currentPage == totalPages - 1) 0 else currentPage + 1
        updateAppsVisibility(totalPages)
        vibratePaging()
    }

    private fun vibratePaging() {
        if (prefs.useVibrationForPaging) {
            try {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }
    }

    private fun getTotalAppsCount(): Int = binding.homeAppsLayout.childCount

    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (prefs.settingsLocked) {
                biometricHelper.startBiometricSettingsAuth(object : BiometricHelper.CallbackSettings {
                    override fun onAuthenticationSucceeded() { sendToSettingFragment() }
                    override fun onAuthenticationFailed() { Log.e("Authentication", getString(R.string.text_authentication_failed)) }
                    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> Log.e("Authentication", getString(R.string.text_authentication_cancel))
                            else -> Log.e("Authentication", getString(R.string.text_authentication_error).format(errorMessage, errorCode))
                        }
                    }
                })
            } else sendToSettingFragment()
        }
    }

    private fun sendToSettingFragment() {
        try { findNavController().navigate(R.id.action_mainFragment_to_settingsFragment) } catch (e: Exception) { Log.d("onLongClick", e.toString()) }
    }

    fun handleVolumeKeyNavigation(keyCode: Int): Boolean {
        if (!prefs.useVolumeKeysForPages) return false
        val totalPages = prefs.homePagesNum
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> { handleSwipeLeft(totalPages); vibratePaging(); true }
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> { handleSwipeRight(totalPages); vibratePaging(); true }
            else -> false
        }
    }

    fun onWindowFocusGained() {
        com.github.gezimos.inkos.helper.utils.EinkRefreshHelper.refreshEink(requireContext(), prefs, binding.root as? ViewGroup)
        viewModel.refreshHomeAppsUiState(requireContext())
    }
    // ... rest of class logic unchanged ...
}
