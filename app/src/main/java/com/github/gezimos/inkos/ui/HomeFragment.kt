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
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.ImageSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        @JvmStatic
        var isHomeVisible: Boolean = false

        @JvmStatic
        fun sendGoToFirstPageSignal() {
            // This will be set by MainActivity to trigger going to first page
            goToFirstPageSignal = true
        }

        @JvmStatic
        var goToFirstPageSignal: Boolean = false
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

    // Add a BroadcastReceiver for user present (unlock)
    private var userPresentReceiver: android.content.BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())
        batteryReceiver = BatteryReceiver()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometricHelper = BiometricHelper(this)

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        viewModel.isinkosDefault()

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        initObservers()
        initClickListeners()
        initSwipeTouchListener()

        // Observe home app UI state and update UI accordingly
        viewModel.homeAppsUiState.observe(viewLifecycleOwner) { homeAppsUiState ->
            updateHomeAppsUi(homeAppsUiState)
        }

        // Add observer for notification info and refresh UI state
        NotificationManager.getInstance(requireContext()).notificationInfoLiveData.observe(
            viewLifecycleOwner
        ) { notifications ->
            viewModel.refreshHomeAppsUiState(requireContext())
            // --- Media playback notification observer logic ---
            // Find any active media playback notification
            val mediaNotification = notifications.values.firstOrNull {
                it.category == android.app.Notification.CATEGORY_TRANSPORT
            }
            viewModel.updateMediaPlaybackInfo(mediaNotification)
        }

        // Add key listener for DPAD_DOWN to move selection down
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        moveSelectionDown()
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // DPAD_LEFT acts as swipe right
                        when (val action = prefs.swipeRightAction) {
                            Action.OpenApp -> openSwipeRightApp()
                            else -> handleOtherAction(action)
                        }
                        CrashHandler.logUserAction("DPAD_LEFT Gesture (SwipeRight)")
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // DPAD_RIGHT acts as swipe left
                        when (val action = prefs.swipeLeftAction) {
                            Action.OpenApp -> openSwipeLeftApp()
                            else -> handleOtherAction(action)
                        }
                        CrashHandler.logUserAction("DPAD_RIGHT Gesture (SwipeLeft)")
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (event.isLongPress) {
                            // Simulate long press on selected app
                            val view = binding.homeAppsLayout.getChildAt(selectedAppIndex)
                            if (view != null) onLongClick(view)
                            true
                        } else {
                            false
                        }
                    }

                    android.view.KeyEvent.KEYCODE_9 -> {
                        if (event.isLongPress) {
                            trySettings()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            } else {
                false
            }
        }
    }

    private fun updateHomeAppsUi(homeAppsUiState: List<HomeAppUiState>) {
        val notifications =
            NotificationManager.getInstance(requireContext()).notificationInfoLiveData.value
                ?: emptyMap()
        homeAppsUiState.forEach { uiState ->
            val view = binding.homeAppsLayout.findViewWithTag<TextView>(uiState.activityPackage)
            if (view != null) {
                NotificationBadgeUtil.updateNotificationForView(
                    requireContext(),
                    prefs,
                    view,
                    notifications
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideKeyboard() // Hide keyboard when returning to HomeFragment
        isHomeVisible = true
        // Eink refresh: flash overlay if enabled
        com.github.gezimos.inkos.helper.utils.EinkRefreshHelper.refreshEink(
            requireContext(), prefs, binding.root as? ViewGroup
        )
        // Centralized reset logic for home button
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
        // Refresh home app UI state on resume
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
        totalApps - 1

        currentPage * appsPerPage
        val endIdx = minOf((currentPage + 1) * appsPerPage, totalApps) - 1

        if (selectedAppIndex < endIdx) {
            // Move to next app in current page
            selectedAppIndex++
        } else {
            // At last app of current page
            if (currentPage < totalPages - 1) {
                // Move to first app of next page
                currentPage++
                selectedAppIndex = currentPage * appsPerPage
            } else {
                // Wrap to first app of first page
                currentPage = 0
                selectedAppIndex = 0
            }
        }
        updateAppsVisibility(totalPages)
        focusAppButton(selectedAppIndex)
    }

    private fun focusAppButton(index: Int) {
        val view = binding.homeAppsLayout.getChildAt(index)
        view?.requestFocus()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStart() {
        super.onStart()
        if (prefs.showStatusBar) showStatusBar(requireActivity()) else hideStatusBar(requireActivity())

        batteryReceiver = BatteryReceiver()
        /* register battery changes */
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            requireContext().registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Register user present receiver to refresh on unlock
        if (userPresentReceiver == null) {
            userPresentReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_PRESENT) {
                        // Trigger the same refresh as onResume
                        if (isAdded) {
                            // Eink refresh: flash overlay if enabled
                            if (prefs.einkRefreshEnabled) {
                                val isDark = when (prefs.appTheme) {
                                    com.github.gezimos.inkos.data.Constants.Theme.Light -> false
                                    com.github.gezimos.inkos.data.Constants.Theme.Dark -> true
                                    com.github.gezimos.inkos.data.Constants.Theme.System -> com.github.gezimos.inkos.helper.isSystemInDarkMode(
                                        requireContext()
                                    )
                                }
                                val overlayColor =
                                    if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                val overlay = View(requireContext())
                                overlay.setBackgroundColor(overlayColor)
                                overlay.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                (binding.root as ViewGroup).addView(overlay)
                                overlay.bringToFront()
                                overlay.postDelayed({
                                    (binding.root as ViewGroup).removeView(overlay)
                                }, 120)
                            }
                            // Centralized reset logic for home button
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
                            // Refresh home app UI state
                            viewModel.refreshHomeAppsUiState(requireContext())
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            requireContext().registerReceiver(userPresentReceiver, filter)
        }

        binding.apply {
            val is24HourFormat = DateFormat.is24HourFormat(requireContext())
            // Use a fixed pattern to guarantee no AM/PM
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
                    view.typeface = prefs.getFontForContext("apps")
                        .getFont(requireContext(), prefs.getCustomFontPathForContext("apps"))
                }
            }
            clock.typeface = prefs.getFontForContext("clock")
                .getFont(requireContext(), prefs.getCustomFontPathForContext("clock"))
            battery.typeface = prefs.getFontForContext("battery")
                .getFont(requireContext(), prefs.getCustomFontPathForContext("battery"))


        }

        binding.homeAppsLayout.children.forEach { view ->
            if (view is TextView) {
                val appModel = prefs.getHomeAppModel(view.id)
                val customLabel = prefs.getAppAlias("app_alias_${appModel.activityPackage}")
                view.text = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel
                view.typeface = prefs.getFontForContext("apps")
                    .getFont(requireContext(), prefs.getCustomFontPathForContext("apps"))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            /* unregister battery changes if the receiver is registered */
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Unregister user present receiver
        if (userPresentReceiver != null) {
            try {
                requireContext().unregisterReceiver(userPresentReceiver)
            } catch (_: Exception) {}
            userPresentReceiver = null
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> {
                when (val action = prefs.clickClockAction) {
                    Action.OpenApp -> openClickClockApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("Clock Clicked")
            }

            R.id.setDefaultLauncher -> {
                // Open system Default Home App settings for proper launcher selection UI
                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
                CrashHandler.logUserAction("SetDefaultLauncher Clicked")
            }

            R.id.battery -> {
                requireContext().openBatteryManager()
                CrashHandler.logUserAction("Battery Clicked")
            }

            else -> {
                try { // Launch app
                    val appLocation = view.id
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (prefs.homeLocked) {
            if (prefs.longPressAppInfoEnabled) {
                // Open app info for the long-pressed app
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
            override fun onSwipeLeft() {
                when (val action = prefs.swipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Gesture")
            }

            override fun onSwipeRight() {
                when (val action = prefs.swipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Gesture")
            }

            override fun onSwipeUp() {
                // Hardcoded: always go to next page
                handleSwipeRight(prefs.homePagesNum)
                CrashHandler.logUserAction("SwipeUp Gesture (NextPage)")
            }

            override fun onSwipeDown() {
                // Hardcoded: always go to previous page
                handleSwipeLeft(prefs.homePagesNum)
                CrashHandler.logUserAction("SwipeDown Gesture (PreviousPage)")
            }

            override fun onLongClick() {
                CrashHandler.logUserAction("Launcher Settings Opened")
                trySettings()
            }

            override fun onDoubleClick() {
                when (val action = prefs.doubleTapAction) {
                    Action.OpenApp -> openDoubleTapApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("DoubleClick Gesture")
            }

            override fun onLongSwipe(direction: String) {
                when (direction) {
                    "up" -> handleSwipeRight(prefs.homePagesNum)
                    "down" -> handleSwipeLeft(prefs.homePagesNum)
                    "left" -> when (val action = prefs.swipeLeftAction) {
                        Action.OpenApp -> openSwipeLeftApp()
                        else -> handleOtherAction(action)
                    }

                    "right" -> when (val action = prefs.swipeRightAction) {
                        Action.OpenApp -> openSwipeRightApp()
                        else -> handleOtherAction(action)
                    }
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
            // Remove firstRunTips logic
            // if (prefs.firstSettingsOpen) firstRunTips.visibility = View.VISIBLE
            // else firstRunTips.visibility = View.GONE

            if (!isinkosDefault(requireContext())) setDefaultLauncher.visibility = View.VISIBLE
            else setDefaultLauncher.visibility = View.GONE

            clock.gravity = Gravity.CENTER
            //homeAppsLayout.gravity = Gravity.CENTER
            clock.layoutParams = (clock.layoutParams as LinearLayout.LayoutParams).apply {
                gravity = Gravity.CENTER
            }
            homeAppsLayout.children.forEach { view ->
                (view as? TextView)?.gravity = Gravity.CENTER
            }
        }

        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) {
                updateAppCount(it)
            }
            showClock.observe(viewLifecycleOwner) {
                binding.clock.visibility = if (it) View.VISIBLE else View.GONE
            }
            launcherDefault.observe(viewLifecycleOwner) {
                binding.setDefaultLauncher.visibility = if (it) View.VISIBLE else View.GONE
            }
            // --- LiveData observers for all preferences ---
            appTheme.observe(viewLifecycleOwner) {
                // Optionally, trigger theme change if needed
            }
            appColor.observe(viewLifecycleOwner) { color ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) {
                        view.setTextColor(color)
                        view.setHintTextColor(color)
                    }
                }
                binding.homeScreenPager.setTextColor(color)
            }
            backgroundColor.observe(viewLifecycleOwner) { color ->
                binding.mainLayout.setBackgroundColor(color)
            }
            clockColor.observe(viewLifecycleOwner) { color ->
                binding.clock.setTextColor(color)
            }
            batteryColor.observe(viewLifecycleOwner) { color ->
                binding.battery.setTextColor(color)
            }
            appsFont.observe(viewLifecycleOwner) { font ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) {
                        view.typeface = prefs.getFontForContext("apps")
                            .getFont(requireContext(), prefs.getCustomFontPathForContext("apps"))
                    }
                }
            }
            clockFont.observe(viewLifecycleOwner) { font ->
                binding.clock.typeface = prefs.getFontForContext("clock")
                    .getFont(requireContext(), prefs.getCustomFontPathForContext("clock"))
            }
            batteryFont.observe(viewLifecycleOwner) { font ->
                binding.battery.typeface = prefs.getFontForContext("battery")
                    .getFont(requireContext(), prefs.getCustomFontPathForContext("battery"))
            }
            textPaddingSize.observe(viewLifecycleOwner) { padding ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) {
                        view.setPadding(0, padding, 0, padding)
                    }
                }
            }
            appSize.observe(viewLifecycleOwner) { size ->
                binding.homeAppsLayout.children.forEach { view ->
                    if (view is TextView) {
                        view.textSize = size.toFloat()
                    }
                }
            }
            clockSize.observe(viewLifecycleOwner) { size ->
                binding.clock.textSize = size.toFloat()
            }
            batterySize.observe(viewLifecycleOwner) { size ->
                binding.battery.textSize = size.toFloat()
            }

        }
    }

    private fun homeAppClicked(location: Int) {
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else {
            val packageName = prefs.getHomeAppModel(location).activityPackage
            val notificationManager = NotificationManager.getInstance(requireContext())
            val notifications = notificationManager.notificationInfoLiveData.value ?: emptyMap()
            val notificationInfo = notifications[packageName]
            // Only clear notification if not a media playback notification
            val isMediaPlayback =
                notificationInfo?.category == android.app.Notification.CATEGORY_TRANSPORT
            if (!isMediaPlayback) {
                notificationManager.updateBadgeNotification(packageName, null)
            }
            viewModel.launchApp(prefs.getHomeAppModel(location), this)
        }
    }

    private fun showAppList(flag: AppDrawerFlag, includeHiddenApps: Boolean = false, n: Int = 0) {
        viewModel.getAppList(includeHiddenApps)
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(
                    R.id.action_mainFragment_to_appListFragment,
                    bundleOf("flag" to flag.toString(), "n" to n)
                )
            }
        } catch (e: Exception) {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(
                    R.id.appListFragment,
                    bundleOf("flag" to flag.toString())
                )
            }
            e.printStackTrace()
        }
    }

    private fun openSwipeLeftApp() {
        if (prefs.appSwipeLeft.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appSwipeLeft, this)
        else
            requireContext().openCameraApp()
    }

    private fun openSwipeRightApp() {
        if (prefs.appSwipeRight.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appSwipeRight, this)
        else
            requireContext().openDialerApp()
    }

    private fun openClickClockApp() {
        if (prefs.appClickClock.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickClock, this)
        else
            requireContext().openAlarmApp()
    }

    private fun openDoubleTapApp() {
        when (prefs.doubleTapAction) {
            Action.OpenApp -> {
                if (prefs.appDoubleTap.activityPackage.isNotEmpty())
                    viewModel.launchApp(prefs.appDoubleTap, this)
                else
                    AppReloader.restartApp(requireContext())
            }

            Action.OpenNotificationsScreen, Action.OpenNotificationsScreenAlt -> {
                // Ensure navigation is on the main thread
                requireActivity().runOnUiThread {
                    findNavController().navigate(R.id.action_mainFragment_to_notificationsFragment)
                }
            }

            Action.RestartApp -> AppReloader.restartApp(requireContext())
            Action.TogglePrivateSpace -> handleOtherAction(Action.TogglePrivateSpace)
            Action.NextPage -> handleOtherAction(Action.NextPage)
            Action.PreviousPage -> handleOtherAction(Action.PreviousPage)
            Action.Disabled -> {}
        }
    }

    @SuppressLint("NewApi")
    private fun handleOtherAction(action: Action) {
        when (action) {
            Action.TogglePrivateSpace -> PrivateSpaceManager(requireContext()).togglePrivateSpaceLock(
                showToast = true,
                launchSettings = true
            )

            Action.OpenApp -> {} // this should be handled in the respective onSwipe[Up,Down,Right,Left] functions
            Action.NextPage -> handleSwipeLeft(prefs.homePagesNum)
            Action.PreviousPage -> handleSwipeRight(prefs.homePagesNum)
            Action.RestartApp -> AppReloader.restartApp(requireContext())
            Action.OpenNotificationsScreenAlt -> {
                requireActivity().runOnUiThread {
                    findNavController().navigate(R.id.action_mainFragment_to_notificationsFragment)
                }
            }

            Action.OpenNotificationsScreen -> {
                requireActivity().runOnUiThread {
                    findNavController().navigate(R.id.action_mainFragment_to_notificationsFragment)
                }
            }

            Action.Disabled -> {}
        }
    }

    private fun showLongPressToast() = showShortToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getHomeAppsGestureListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onLongClick(view: View) {
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                textOnClick(view)
            }

            override fun onSwipeLeft() {
                when (val action = prefs.swipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Gesture")
            }

            override fun onSwipeRight() {
                when (val action = prefs.swipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Gesture")
            }

            override fun onSwipeUp() {
                // Hardcoded: always go to next page
                handleSwipeRight(prefs.homePagesNum)
                CrashHandler.logUserAction("SwipeUp Gesture (NextPage)")
            }

            override fun onSwipeDown() {
                // Hardcoded: always go to previous page
                handleSwipeLeft(prefs.homePagesNum)
                CrashHandler.logUserAction("SwipeDown Gesture (PreviousPage)")
            }

            override fun onLongSwipe(direction: String) {
                when (direction) {
                    "up" -> handleSwipeRight(prefs.homePagesNum)
                    "down" -> handleSwipeLeft(prefs.homePagesNum)
                    "left" -> when (val action = prefs.swipeLeftAction) {
                        Action.OpenApp -> openSwipeLeftApp()
                        else -> handleOtherAction(action)
                    }

                    "right" -> when (val action = prefs.swipeRightAction) {
                        Action.OpenApp -> openSwipeRightApp()
                        else -> handleOtherAction(action)
                    }
                }
                CrashHandler.logUserAction("LongSwipe_${direction} Gesture")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun updateAppCount(newAppsNum: Int) {
        val oldAppsNum = binding.homeAppsLayout.childCount
        val diff = newAppsNum - oldAppsNum

        // Add dynamic padding based on visible widgets
        updateHomeAppsPadding()

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                val view = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                view.apply {
                    textSize = prefs.appSize.toFloat()
                    id = i
                    val appModel = prefs.getHomeAppModel(i)
                    val customLabel = prefs.getAppAlias("app_alias_${appModel.activityPackage}")
                    text = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel
                    setTextColor(prefs.appColor)
                    setHintTextColor(prefs.appColor)
                    setOnTouchListener(getHomeAppsGestureListener(context, this))
                    setOnClickListener(this@HomeFragment)
                    // Add key listener for DPAD_DOWN to each app button
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    moveSelectionDown()
                                    true
                                }

                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    // DPAD_LEFT acts as swipe right
                                    when (val action = prefs.swipeRightAction) {
                                        Action.OpenApp -> openSwipeRightApp()
                                        else -> handleOtherAction(action)
                                    }
                                    CrashHandler.logUserAction("DPAD_LEFT Gesture (SwipeRight)")
                                    true
                                }

                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    // DPAD_RIGHT acts as swipe left
                                    when (val action = prefs.swipeLeftAction) {
                                        Action.OpenApp -> openSwipeLeftApp()
                                        else -> handleOtherAction(action)
                                    }
                                    CrashHandler.logUserAction("DPAD_RIGHT Gesture (SwipeLeft)")
                                    true
                                }

                                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                                    if (event.isLongPress) {
                                        onLongClick(this)
                                        true
                                    } else {
                                        false
                                    }
                                }

                                android.view.KeyEvent.KEYCODE_9 -> {
                                    if (event.isLongPress) {
                                        trySettings()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> false
                            }
                        } else {
                            false
                        }
                    }

                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    gravity = Gravity.CENTER // Always use center gravity
                    isFocusable = true
                    isFocusableInTouchMode = true

                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)

                    // Apply apps font
                    typeface = prefs.getFontForContext("apps")
                        .getFont(context, prefs.getCustomFontPathForContext("apps"))

                    tag = appModel.activityPackage // Assign unique tag
                }
                binding.homeAppsLayout.addView(view)
            }
        } else if (diff < 0) {
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        // Update the total number of pages and calculate maximum apps per page
        updatePagesAndAppsPerPage(prefs.homeAppsNum, prefs.homePagesNum)
    }

    private fun updateHomeAppsPadding() {
        binding.apply {
            // Check if any of the main widgets are visible
            val hasVisibleWidgets = clock.isVisible

            // Apply padding only if widgets are visible
            val defaultPadding = resources.getDimensionPixelSize(R.dimen.home_apps_default_padding)

            // Apply the padding
            homeAppsLayout.setPadding(
                homeAppsLayout.paddingLeft,
                if (hasVisibleWidgets) defaultPadding else 0,
                homeAppsLayout.paddingRight,
                homeAppsLayout.paddingBottom
            )
        }
    }

    // updates number of apps visible on home screen
    // does nothing if number has not changed
    private var currentPage = 0
    private var appsPerPage = 0

    private fun updatePagesAndAppsPerPage(totalApps: Int, totalPages: Int) {
        appsPerPage = if (totalPages > 0) {
            (totalApps + totalPages - 1) / totalPages
        } else {
            0
        }
        updateAppsVisibility(totalPages)

        // Ensure proper positioning of pager after updating visibility
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

        val sizeInDp = 12 // Ensure this matches your vector drawable's intended size
        val density = requireContext().resources.displayMetrics.density
        val sizeInPx = (sizeInDp * density).toInt()

        pageSelectorIcons.forEach { drawableRes ->
            val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)?.apply {
                setBounds(0, 0, sizeInPx, sizeInPx) // Use fixed square size for perfect circle
                val colorFilterColor: ColorFilter =
                    PorterDuffColorFilter(prefs.appColor, PorterDuff.Mode.SRC_IN)
                colorFilter = colorFilterColor
            }
            val imageSpan = drawable?.let { ImageSpan(it, ImageSpan.ALIGN_BOTTOM) }

            val placeholder = SpannableString(" ") // Placeholder for the image
            imageSpan?.let { placeholder.setSpan(it, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }

            spannable.append(placeholder)
            spannable.append("\u2006\u2009") // Add tiny space between icons using hair space
        }

        // Set the text for the page selector corresponding to each page
        binding.homeScreenPager.text = spannable
        if (prefs.homePagesNum > 1 && prefs.homePager) binding.homeScreenPager.visibility =
            View.VISIBLE
    }

    private fun handleSwipeLeft(totalPages: Int) {
        if (totalPages <= 0) return // Prevent issues if totalPages is 0 or negative

        currentPage = if (currentPage == 0) {
            totalPages - 1 // Wrap to last page if on the first page
        } else {
            currentPage - 1 // Move to the previous page
        }

        updateAppsVisibility(totalPages)
        vibratePaging()
    }

    private fun handleSwipeRight(totalPages: Int) {
        if (totalPages <= 0) return // Prevent issues if totalPages is 0 or negative

        currentPage = if (currentPage == totalPages - 1) {
            0 // Wrap to first page if on the last page
        } else {
            currentPage + 1 // Move to the next page
        }

        updateAppsVisibility(totalPages)
        vibratePaging()
    }

    private fun vibratePaging() {
        if (prefs.useVibrationForPaging) {
            try {
                // No need to check SDK_INT, always use VibrationEffect
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        30,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun getTotalAppsCount(): Int {
        return binding.homeAppsLayout.childCount
    }

    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (prefs.settingsLocked) {
                biometricHelper.startBiometricSettingsAuth(object :
                    BiometricHelper.CallbackSettings {
                    override fun onAuthenticationSucceeded() {
                        sendToSettingFragment()
                    }

                    override fun onAuthenticationFailed() {
                        Log.e(
                            "Authentication",
                            getString(R.string.text_authentication_failed)
                        )
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errorMessage: CharSequence?
                    ) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> Log.e(
                                "Authentication",
                                getString(R.string.text_authentication_cancel)
                            )

                            else ->
                                Log.e(
                                    "Authentication",
                                    getString(R.string.text_authentication_error).format(
                                        errorMessage,
                                        errorCode
                                    )
                                )
                        }
                    }
                })
            } else {
                sendToSettingFragment()
            }
        }
    }

    private fun sendToSettingFragment() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            // Remove firstOpen(false) call, as it is only for first run tip
            // viewModel.firstOpen(false)
        } catch (e: java.lang.Exception) {
            Log.d("onLongClick", e.toString())
        }
    }

    // --- Volume key navigation for pages ---
    fun handleVolumeKeyNavigation(keyCode: Int): Boolean {
        if (!prefs.useVolumeKeysForPages) return false
        val totalPages = prefs.homePagesNum
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                handleSwipeLeft(totalPages)
                vibratePaging()
                true
            }

            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleSwipeRight(totalPages)
                vibratePaging()
                true
            }

            else -> false
        }
    }

    // Called from MainActivity when window regains focus (e.g., overlay closed)
    fun onWindowFocusGained() {
        com.github.gezimos.inkos.helper.utils.EinkRefreshHelper.refreshEink(
            requireContext(), prefs, binding.root as? ViewGroup
        )
        // Optionally, refresh UI state if needed
        viewModel.refreshHomeAppsUiState(requireContext())
    }
}
