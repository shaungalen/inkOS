package com.github.gezimos.inkos.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants.Action
import com.github.gezimos.inkos.data.Constants.AppDrawerFlag
import com.github.gezimos.inkos.data.Constants.Theme.Dark
import com.github.gezimos.inkos.data.Constants.Theme.Light
import com.github.gezimos.inkos.data.Constants.Theme.System
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getHexForOpacity
import com.github.gezimos.inkos.helper.isSystemInDarkMode
import com.github.gezimos.inkos.helper.utils.EinkScrollBehavior
import com.github.gezimos.inkos.helper.utils.PrivateSpaceManager
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.ui.compose.SettingsComposable.DashedSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.FullLineSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.PageHeader
import com.github.gezimos.inkos.ui.compose.SettingsComposable.PageIndicator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSelect
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSwitch
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsTitle
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SolidSeparator
import com.github.gezimos.inkos.ui.dialogs.DialogManager

class GesturesFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private lateinit var dialogBuilder: DialogManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = Prefs(requireContext())
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        val isDark = when (prefs.appTheme) {
            Light -> false
            Dark -> true
            System -> isSystemInDarkMode(requireContext())
        }
        val settingsSize = (prefs.settingsSize - 3)
        val backgroundColor = getHexForOpacity(prefs)
        val context = requireContext()
        // --- Dot indicator state ---
        val currentPage = intArrayOf(0)
        val pageCount = intArrayOf(1)

        // Create a vertical LinearLayout to hold sticky header and scrollable content
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        var bottomInsetPx = 0
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            bottomInsetPx = navBarInset
            insets
        }

        // Add sticky header ComposeView
        val headerView = ComposeView(context).apply {
            setContent {
                val density = LocalDensity.current
                val bottomInsetDp = with(density) { bottomInsetPx.toDp() }
                SettingsTheme(isDark) {
                    Column(Modifier.fillMaxWidth()) {
                        PageHeader(
                            iconRes = R.drawable.ic_back,
                            title = stringResource(R.string.gestures_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                PageIndicator(
                                    currentPage = currentPage[0],
                                    pageCount = pageCount[0],
                                    titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                                )
                            },
                            titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                        )
                        SolidSeparator(isDark = isDark)
                        Spacer(modifier = Modifier.height(SettingsTheme.color.horizontalPadding))
                        if (bottomInsetDp > 0.dp) {
                            Spacer(modifier = Modifier.height(bottomInsetDp))
                        }
                    }
                }
            }
        }
        rootLayout.addView(headerView)

        // Add scrollable settings content
        val nestedScrollView = NestedScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
            addView(
                ComposeView(context).apply {
                    setContent {
                        val density = LocalDensity.current
                        val bottomInsetDp = with(density) { bottomInsetPx.toDp() }
                        SettingsTheme(isDark) {
                            Box(Modifier.fillMaxSize()) {
                                Column {
                                    GesturesSettingsAllInOne(settingsSize.sp)
                                    if (bottomInsetDp > 0.dp) {
                                        Spacer(modifier = Modifier.height(bottomInsetDp))
                                    }
                                }
                            }
                        }
                    }
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        EinkScrollBehavior(context).attachToScrollView(nestedScrollView)
        rootLayout.addView(
            nestedScrollView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // --- Calculate pages and listen for scroll changes ---
        fun getCurrentPageIndex(
            scrollY: Int,
            viewportHeight: Int,
            contentHeight: Int,
            pageCount: Int
        ): Int {
            if (contentHeight <= viewportHeight) return 0
            val overlap = (viewportHeight * 0.2).toInt()
            val scrollStep = viewportHeight - overlap
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1)
            val clampedScrollY = scrollY.coerceIn(0, maxScroll)
            val page = Math.round(clampedScrollY.toFloat() / scrollStep)
            return page.coerceIn(0, pageCount - 1)
        }
        nestedScrollView.viewTreeObserver.addOnGlobalLayoutListener {
            val contentHeight = nestedScrollView.getChildAt(0)?.height ?: 1
            val viewportHeight = nestedScrollView.height.takeIf { it > 0 } ?: 1
            val overlap = (viewportHeight * 0.2).toInt()
            val scrollStep = viewportHeight - overlap
            val pages =
                Math.ceil(((contentHeight - viewportHeight).toDouble() / scrollStep.toDouble()))
                    .toInt() + 1
            pageCount[0] = pages
            val scrollY = nestedScrollView.scrollY
            currentPage[0] = getCurrentPageIndex(scrollY, viewportHeight, contentHeight, pages)
            headerView.setContent {
                SettingsTheme(isDark) {
                    Column(Modifier.fillMaxWidth()) {
                        PageHeader(
                            iconRes = R.drawable.ic_back,
                            title = stringResource(R.string.gestures_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                PageIndicator(
                                    currentPage = currentPage[0],
                                    pageCount = pageCount[0],
                                    titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                                )
                            },
                            titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                        )
                        SolidSeparator(isDark = isDark)
                        Spacer(modifier = Modifier.height(SettingsTheme.color.horizontalPadding))
                    }
                }
            }
        }
        nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentHeight = nestedScrollView.getChildAt(0)?.height ?: 1
            val viewportHeight = nestedScrollView.height.takeIf { it > 0 } ?: 1
            val overlap = (viewportHeight * 0.2).toInt()
            val scrollStep = viewportHeight - overlap
            val pages =
                Math.ceil(((contentHeight - viewportHeight).toDouble() / scrollStep.toDouble()))
                    .toInt() + 1
            pageCount[0] = pages
            currentPage[0] = getCurrentPageIndex(scrollY, viewportHeight, contentHeight, pages)
            headerView.setContent {
                SettingsTheme(isDark) {
                    Column(Modifier.fillMaxWidth()) {
                        PageHeader(
                            iconRes = R.drawable.ic_back,
                            title = stringResource(R.string.gestures_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                PageIndicator(
                                    currentPage = currentPage[0],
                                    pageCount = pageCount[0],
                                    titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                                )
                            },
                            titleFontSize = if (settingsSize > 0) (settingsSize * 1.5).sp else TextUnit.Unspecified
                        )
                        SolidSeparator(isDark = isDark)
                        Spacer(modifier = Modifier.height(SettingsTheme.color.horizontalPadding))
                    }
                }
            }
        }
        return rootLayout
    }

    @Composable
    fun GesturesSettingsAllInOne(fontSize: TextUnit = TextUnit.Unspecified) {
        findNavController()
        val isDark = isSystemInDarkMode(requireContext())
        val titleFontSize = if (fontSize.isSpecified) (fontSize.value * 1.5).sp else fontSize
        var useVolumeKeys by remember { mutableStateOf(prefs.useVolumeKeysForPages) }
        var selectedDoubleTapAction by remember { mutableStateOf(prefs.doubleTapAction) }
        var selectedClickClockAction by remember { mutableStateOf(prefs.clickClockAction) }
        var selectedSwipeLeftAction by remember { mutableStateOf(prefs.swipeLeftAction) }
        var selectedSwipeRightAction by remember { mutableStateOf(prefs.swipeRightAction) }
        val actions = Action.entries
        val filteredActions =
            if (!PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
                actions.filter { it != Action.TogglePrivateSpace }
            } else actions
        // Remove OpenApp, NextPage, and PreviousPage from double tap gesture actions only
        val doubleTapGestureActions = filteredActions.filter { action ->
            action != Action.OpenApp &&
                    action != Action.NextPage &&
                    action != Action.PreviousPage &&
                    when (action) {
                        Action.OpenNotificationsScreen -> prefs.notificationsEnabled
                        Action.OpenNotificationsScreenAlt -> false
                        else -> true
                    }
        }.toMutableList()
        // Only remove NextPage and PreviousPage from click clock gesture actions
        val clickClockGestureActions = filteredActions.filter { action ->
            action != Action.NextPage &&
                    action != Action.PreviousPage &&
                    when (action) {
                        Action.OpenNotificationsScreen -> prefs.notificationsEnabled
                        Action.OpenNotificationsScreenAlt -> false
                        else -> true
                    }
        }.toMutableList()
        // For swipe left/right, do not filter out NextPage or PreviousPage
        val gestureActions = filteredActions.filter { action ->
            when (action) {
                Action.OpenNotificationsScreen -> prefs.notificationsEnabled
                Action.OpenNotificationsScreenAlt -> false
                else -> true
            }
        }.toMutableList()
        val doubleTapActionStrings =
            doubleTapGestureActions.map { it.getString(requireContext()) }.toTypedArray()
        val clickClockActionStrings =
            clickClockGestureActions.map { it.getString(requireContext()) }.toTypedArray()
        val actionStrings = gestureActions.map { it.getString(requireContext()) }.toTypedArray()
        val appLabelDoubleTapAction = prefs.appDoubleTap.activityLabel
        prefs.appClickClock.activityLabel.ifEmpty { "Clock" }
        val appLabelSwipeLeftAction = prefs.appSwipeLeft.activityLabel.ifEmpty { "Camera" }
        val appLabelSwipeRightAction = prefs.appSwipeRight.activityLabel.ifEmpty { "Phone" }
        Column(modifier = Modifier.fillMaxSize()) {
            FullLineSeparator(isDark)
            SettingsSwitch(
                text = stringResource(R.string.use_volume_keys_for_pages),
                fontSize = titleFontSize,
                defaultState = useVolumeKeys,
                onCheckedChange = {
                    useVolumeKeys = it
                    prefs.useVolumeKeysForPages = it
                }
            )
            // Tap/Click Actions Section
            FullLineSeparator(isDark)
            SettingsTitle(
                text = stringResource(R.string.tap_click_actions),
                fontSize = titleFontSize,
            )
            FullLineSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.double_tap),
                option = if (selectedDoubleTapAction == Action.OpenApp) {
                    // fallback, but OpenApp should not be selectable
                    "${stringResource(R.string.open)} $appLabelDoubleTapAction"
                } else {
                    selectedDoubleTapAction.string()
                },
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSingleChoiceDialog(
                        context = requireContext(),
                        options = doubleTapActionStrings,
                        titleResId = R.string.double_tap,
                        onItemSelected = { newDoubleTapAction: String ->
                            val selectedAction =
                                doubleTapGestureActions.firstOrNull { it.getString(requireContext()) == newDoubleTapAction }
                            if (selectedAction != null) {
                                selectedDoubleTapAction = selectedAction
                                setGesture(AppDrawerFlag.SetDoubleTap, selectedAction)
                            }
                        }
                    )
                }
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.clock_click_app),
                option = if (selectedClickClockAction == Action.OpenApp) {
                    stringResource(R.string.open_clock)
                } else {
                    selectedClickClockAction.string()
                },
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSingleChoiceDialog(
                        context = requireContext(),
                        options = clickClockActionStrings,
                        titleResId = R.string.clock_click_app,
                        onItemSelected = { newClickClock: String ->
                            val selectedAction =
                                clickClockGestureActions.firstOrNull { it.getString(requireContext()) == newClickClock }
                            if (selectedAction != null) {
                                selectedClickClockAction = selectedAction
                                setGesture(AppDrawerFlag.SetClickClock, selectedAction)
                            }
                        }
                    )
                }
            )
            // Swipe Actions Section
            FullLineSeparator(isDark)
            SettingsTitle(
                text = stringResource(R.string.swipe_movement),
                fontSize = titleFontSize,
            )
            FullLineSeparator(isDark)
            // Swipe Up/Down removed from UI
            SettingsSelect(
                title = stringResource(R.string.swipe_left_app),
                option = if (selectedSwipeLeftAction == Action.OpenApp) {
                    "${stringResource(R.string.open)} $appLabelSwipeLeftAction"
                } else {
                    selectedSwipeLeftAction.string()
                },
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSingleChoiceDialog(
                        context = requireContext(),
                        options = actionStrings,
                        titleResId = R.string.swipe_left_app,
                        onItemSelected = { newAction: String ->
                            val selectedAction =
                                gestureActions.firstOrNull { it.getString(requireContext()) == newAction }
                            if (selectedAction != null) {
                                selectedSwipeLeftAction = selectedAction
                                setGesture(AppDrawerFlag.SetSwipeLeft, selectedAction)
                            }
                        }
                    )
                }
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.swipe_right_app),
                option = if (selectedSwipeRightAction == Action.OpenApp) {
                    "${stringResource(R.string.open)} $appLabelSwipeRightAction"
                } else {
                    selectedSwipeRightAction.string()
                },
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSingleChoiceDialog(
                        context = requireContext(),
                        options = actionStrings,
                        titleResId = R.string.swipe_right_app,
                        onItemSelected = { newAction: String ->
                            val selectedAction =
                                gestureActions.firstOrNull { it.getString(requireContext()) == newAction }
                            if (selectedAction != null) {
                                selectedSwipeRightAction = selectedAction
                                setGesture(AppDrawerFlag.SetSwipeRight, selectedAction)
                            }
                        }
                    )
                }
            )
            DashedSeparator(isDark)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    private fun setGesture(flag: AppDrawerFlag, action: Action) {
        when (flag) {
            AppDrawerFlag.SetDoubleTap -> prefs.doubleTapAction = action
            AppDrawerFlag.SetClickClock -> prefs.clickClockAction = action
            AppDrawerFlag.SetSwipeLeft -> prefs.swipeLeftAction = action
            AppDrawerFlag.SetSwipeRight -> prefs.swipeRightAction = action
            AppDrawerFlag.LaunchApp,
            AppDrawerFlag.HiddenApps,
            AppDrawerFlag.PrivateApps,
            AppDrawerFlag.SetHomeApp -> {
                // No-op for these flags in this context
            }

            else -> {
                // No-op for unused/removed flags (SetSwipeUp, SetSwipeDown, SetAppUsage, SetFloating)
            }
        }
    }
}