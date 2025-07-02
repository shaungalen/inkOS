package com.github.gezimos.inkos.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Constants.Theme.Dark
import com.github.gezimos.inkos.data.Constants.Theme.Light
import com.github.gezimos.inkos.data.Constants.Theme.System
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getTrueSystemFont
import com.github.gezimos.inkos.helper.isSystemInDarkMode
import com.github.gezimos.inkos.helper.utils.EinkScrollBehavior
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.ui.compose.SettingsComposable.DashedSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.FullLineSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.PageHeader
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSelect
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSwitch
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsTitle
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SolidSeparator
import com.github.gezimos.inkos.ui.dialogs.DialogManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FontsFragment : Fragment() {
    private lateinit var prefs: Prefs
    private lateinit var dialogBuilder: DialogManager
    private var fontChanged = false
    private val PICK_FONT_FILE_REQUEST_CODE = 1001
    private var onCustomFontSelected: ((Typeface, String) -> Unit)? = null

    private fun getCurrentPageIndex(
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = Prefs(requireContext())
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        val backgroundColor = com.github.gezimos.inkos.helper.getHexForOpacity(prefs)
        val isDark = when (prefs.appTheme) {
            Light -> false
            Dark -> true
            System -> isSystemInDarkMode(requireContext())
        }
        val settingsSize = (prefs.settingsSize - 3)

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
                            title = stringResource(R.string.fonts_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                com.github.gezimos.inkos.ui.compose.SettingsComposable.PageIndicator(
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
                                    FontsSettingsAllInOne(settingsSize.sp, isDark)
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
                            title = stringResource(R.string.fonts_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                com.github.gezimos.inkos.ui.compose.SettingsComposable.PageIndicator(
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
                            title = stringResource(R.string.fonts_settings_title),
                            onClick = { findNavController().popBackStack() },
                            showStatusBar = prefs.showStatusBar,
                            pageIndicator = {
                                com.github.gezimos.inkos.ui.compose.SettingsComposable.PageIndicator(
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
    fun FontsSettingsAllInOne(fontSize: TextUnit = TextUnit.Unspecified, isDark: Boolean) {
        findNavController()
        val titleFontSize = if (fontSize.isSpecified) (fontSize.value * 1.5).sp else fontSize
        // Universal Font Section State
        var universalFontState by remember { mutableStateOf(prefs.universalFont) }
        var universalFontEnabledState by remember { mutableStateOf(prefs.universalFontEnabled) }
        var settingsFontState by remember { mutableStateOf(prefs.fontFamily) }
        var settingsSize by remember { mutableStateOf(prefs.settingsSize) }
        // Home Fonts Section State
        val universalFontEnabledHome = universalFontEnabledState
        var appsFontState by remember { mutableStateOf(if (universalFontEnabledHome) prefs.universalFont else prefs.appsFont) }
        var appSize by remember { mutableStateOf(prefs.appSize) }
        var clockFontState by remember { mutableStateOf(if (universalFontEnabledHome) prefs.universalFont else prefs.clockFont) }
        var clockSize by remember { mutableStateOf(prefs.clockSize) }
        var batteryFontState by remember { mutableStateOf(if (universalFontEnabledHome) prefs.universalFont else prefs.batteryFont) }
        var batterySize by remember { mutableStateOf(prefs.batterySize) }
        // Remove notificationFontState and notificationTextSize (redundant)
        // Notification Fonts Section State
        val universalFontEnabledNotif = universalFontEnabledState
        var labelnotificationsFontState by remember { mutableStateOf(prefs.labelnotificationsFont) }
        var labelnotificationsFontSize by remember { mutableStateOf(prefs.labelnotificationsTextSize) }
        var notificationsFontState by remember { mutableStateOf(prefs.notificationsFont) }
        var notificationsTitleFontState by remember { mutableStateOf(prefs.lettersTitleFont) }
        var notificationsTitle by remember { mutableStateOf(prefs.lettersTitle) }
        var notificationsTitleSize by remember { mutableStateOf(prefs.lettersTitleSize) }
        var notificationsTextSize by remember { mutableStateOf(prefs.notificationsTextSize) }

        // --- Sync all font states when universal font or its enabled state changes ---
        LaunchedEffect(universalFontState, universalFontEnabledState) {
            if (universalFontEnabledState) {
                val font = universalFontState
                appsFontState = font
                clockFontState = font
                batteryFontState = font
                labelnotificationsFontState = font
                notificationsFontState = font
                notificationsTitleFontState = font
            } else {
                appsFontState = prefs.appsFont
                clockFontState = prefs.clockFont
                batteryFontState = prefs.batteryFont
                labelnotificationsFontState = prefs.labelnotificationsFont
                notificationsFontState = prefs.notificationsFont
                notificationsTitleFontState = prefs.lettersTitleFont
            }
        }

        // Use Column instead of LazyColumn (let parent NestedScrollView handle scrolling)
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- Universal Custom Font Section (top, with Reset All on right) ---
            FullLineSeparator(isDark = isDark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsTitle(
                    text = stringResource(R.string.universal_custom_font),
                    fontSize = titleFontSize,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Reset All",
                    style = SettingsTheme.typography.button,
                    fontSize = if (titleFontSize.isSpecified) (titleFontSize.value * 0.7).sp else 14.sp,
                    modifier = Modifier
                        .padding(end = SettingsTheme.color.horizontalPadding)
                        .clickable {
                            prefs.fontFamily = Constants.FontFamily.System
                            prefs.universalFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("universal")
                            prefs.universalFontEnabled = false
                            prefs.appsFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("apps")
                            prefs.clockFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("clock")
                            prefs.statusFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("status")
                            prefs.labelnotificationsFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("notification")
                            prefs.removeCustomFontPath("date")
                            prefs.batteryFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("battery")
                            prefs.lettersFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("letters")
                            prefs.lettersTitleFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("lettersTitle")
                            prefs.notificationsFont = Constants.FontFamily.System
                            prefs.removeCustomFontPath("notifications")
                            prefs.settingsSize = 16
                            prefs.appSize = 32
                            prefs.clockSize = 64
                            prefs.labelnotificationsTextSize = 16
                            prefs.batterySize = 18
                            prefs.lettersTextSize = 18
                            prefs.lettersTitleSize = 36
                            prefs.lettersTitle = "Letters"
                            universalFontState = Constants.FontFamily.System
                            universalFontEnabledState = false
                            settingsFontState = Constants.FontFamily.System
                            settingsSize = 16
                            appsFontState = Constants.FontFamily.System
                            appSize = 32
                            clockFontState = Constants.FontFamily.System
                            clockSize = 64
                            batteryFontState = Constants.FontFamily.System
                            batterySize = 18
                            labelnotificationsFontState = Constants.FontFamily.System
                            labelnotificationsFontSize = 16
                            notificationsFontState = Constants.FontFamily.System
                            notificationsTitleFontState = Constants.FontFamily.System
                            notificationsTitle = "Letters"
                            notificationsTitleSize = 36
                            notificationsTextSize = 16
                        }
                )
            }
            FullLineSeparator(isDark)
            SettingsSwitch(
                text = stringResource(R.string.universal_custom_font),
                fontSize = titleFontSize,
                defaultState = universalFontEnabledState,
                onCheckedChange = { enabled ->
                    prefs.universalFontEnabled = enabled
                    universalFontEnabledState = enabled
                    if (enabled) {
                        val font = prefs.universalFont
                        val fontPath =
                            if (font == Constants.FontFamily.Custom) prefs.getCustomFontPath("universal") else null
                        prefs.fontFamily = font
                        prefs.appsFont = font
                        prefs.clockFont = font
                        prefs.statusFont = font
                        prefs.labelnotificationsFont = font
                        // prefs.dateFont = font
                        prefs.batteryFont = font
                        prefs.lettersFont = font
                        prefs.lettersTitleFont = font
                        if (font == Constants.FontFamily.Custom && fontPath != null) {
                            val keys = listOf(
                                "universal",
                                "settings",
                                "apps",
                                "clock",
                                "status",
                                "notification",
                                // "date",
                                "battery",
                                "letters",
                                "lettersTitle"
                            )
                            for (key in keys) prefs.setCustomFontPath(key, fontPath)
                        }
                        settingsFontState = font
                    }
                }
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.universal_custom_font_selector),
                option = getFontDisplayName(universalFontState, "universal"),
                fontSize = titleFontSize,
                onClick = {
                    showFontSelectionDialogWithCustoms(
                        R.string.universal_custom_font_selector,
                        "universal"
                    ) { newFont, customPath ->
                        prefs.universalFont = newFont
                        universalFontState = newFont
                        val fontPath =
                            if (newFont == Constants.FontFamily.Custom) customPath else null
                        if (prefs.universalFontEnabled) {
                            prefs.fontFamily = newFont
                            prefs.appsFont = newFont
                            prefs.clockFont = newFont
                            prefs.statusFont = newFont
                            prefs.labelnotificationsFont = newFont
                            // prefs.dateFont = newFont
                            prefs.batteryFont = newFont
                            prefs.lettersFont = newFont
                            prefs.lettersTitleFont = newFont
                            if (newFont == Constants.FontFamily.Custom && fontPath != null) {
                                val keys = listOf(
                                    "universal",
                                    "settings",
                                    "apps",
                                    "clock",
                                    "status",
                                    "notification",
                                    // "date",
                                    "battery",
                                    "letters",
                                    "lettersTitle"
                                )
                                for (key in keys) prefs.setCustomFontPath(key, fontPath)
                            }
                            settingsFontState = newFont
                        }
                        // Refresh the fragment to show changes immediately
                        activity?.recreate()
                    }
                },
                enabled = prefs.universalFontEnabled
            )
            FullLineSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.settings_font_section),
                option = getFontDisplayName(settingsFontState, "settings"),
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledState) {
                        showFontSelectionDialogWithCustoms(
                            R.string.settings_font_section,
                            "settings"
                        ) { newFont, customPath ->
                            prefs.fontFamily = newFont
                            settingsFontState = newFont
                        }
                    }
                },
                fontColor = if (!universalFontEnabledState)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledState
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.settings_text_size),
                option = settingsSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = getString(R.string.settings_text_size),
                        minValue = Constants.MIN_SETTINGS_TEXT_SIZE,
                        maxValue = Constants.MAX_SETTINGS_TEXT_SIZE,
                        currentValue = prefs.settingsSize,
                        onValueSelected = { newSize ->
                            prefs.settingsSize = newSize
                            settingsSize = newSize
                        }
                    )
                }
            )

            // --- Home Fonts Section ---
            FullLineSeparator(isDark)
            SettingsTitle(
                text = "Home Fonts",
                fontSize = titleFontSize
            )
            FullLineSeparator(isDark)

            // Apps Font
            SettingsSelect(
                title = stringResource(R.string.apps_font),
                option = getFontDisplayName(appsFontState, "apps"),
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledHome) {
                        showFontSelectionDialogWithCustoms(
                            R.string.apps_font,
                            "apps"
                        ) { newFont, customPath ->
                            prefs.appsFont = newFont
                            appsFontState = newFont
                        }
                    }
                },
                fontColor = if (!universalFontEnabledHome)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledHome
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.app_text_size),
                option = appSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = requireContext().getString(R.string.app_text_size),
                        minValue = Constants.MIN_APP_SIZE,
                        maxValue = Constants.MAX_APP_SIZE,
                        currentValue = prefs.appSize,
                        onValueSelected = { newAppSize ->
                            prefs.appSize = newAppSize
                            appSize = newAppSize
                        }
                    )
                }
            )
            FullLineSeparator(isDark)

            // Clock Font
            SettingsSelect(
                title = stringResource(R.string.clock_font),
                option = getFontDisplayName(clockFontState, "clock"),
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledHome) {
                        showFontSelectionDialogWithCustoms(
                            R.string.clock_font,
                            "clock"
                        ) { newFont, customPath ->
                            prefs.clockFont = newFont
                            clockFontState = newFont
                        }
                    }
                },
                fontColor = if (!universalFontEnabledHome)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledHome
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.clock_text_size),
                option = clockSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = requireContext().getString(R.string.clock_text_size),
                        minValue = Constants.MIN_CLOCK_SIZE,
                        maxValue = Constants.MAX_CLOCK_SIZE,
                        currentValue = prefs.clockSize,
                        onValueSelected = { newClockSize ->
                            prefs.clockSize = newClockSize
                            clockSize = newClockSize
                        }
                    )
                }
            )
            FullLineSeparator(isDark)

            // Date Font (removed)
            // SettingsSelect(
            //     title = stringResource(R.string.date_font),
            //     option = getFontDisplayName(dateFontState, "date"),
            //     fontSize = titleFontSize,
            //     onClick = { ... },
            //     fontColor = ...,
            //     enabled = ...
            // )
            // DashedSeparator(isDark)
            // SettingsSelect(
            //     title = stringResource(R.string.date_text_size),
            //     option = dateSize.toString(),
            //     fontSize = titleFontSize,
            //     onClick = { ... }
            // )
            // FullLineSeparator(isDark)

            // Battery Font
            SettingsSelect(
                title = stringResource(R.string.battery_font),
                option = getFontDisplayName(batteryFontState, "battery"),
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledHome) {
                        showFontSelectionDialogWithCustoms(
                            R.string.battery_font,
                            "battery"
                        ) { newFont, customPath ->
                            prefs.batteryFont = newFont
                            batteryFontState = newFont
                        }
                    }
                },
                fontColor = if (!universalFontEnabledHome)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledHome
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.battery_text_size),
                option = batterySize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = requireContext().getString(R.string.battery_text_size),
                        minValue = Constants.MIN_BATTERY_SIZE,
                        maxValue = Constants.MAX_BATTERY_SIZE,
                        currentValue = prefs.batterySize,
                        onValueSelected = { newBatterySize ->
                            prefs.batterySize = newBatterySize
                            batterySize = newBatterySize
                        }
                    )
                }
            )
            FullLineSeparator(isDark)

            SettingsTitle(
                text = "Label Notifications",
                fontSize = titleFontSize
            )
            FullLineSeparator(isDark)
            SettingsSelect(
                title = "Label Notifications Font",
                option = if (labelnotificationsFontState == Constants.FontFamily.Custom)
                    getFontDisplayName(labelnotificationsFontState, "notification")
                else labelnotificationsFontState.name,
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledNotif) {
                        showFontSelectionDialogWithCustoms(
                            R.string.app_notification_font,
                            "notification"
                        ) { newFont, customPath ->
                            prefs.labelnotificationsFont = newFont
                            labelnotificationsFontState = newFont
                            if (newFont == Constants.FontFamily.Custom && customPath != null) {
                                prefs.setCustomFontPath("notification", customPath)
                            }
                        }
                    }
                },
                fontColor = if (!universalFontEnabledNotif)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledNotif
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = "Label Notifications Size",
                option = labelnotificationsFontSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = "Label Notifications Size",
                        minValue = Constants.MIN_LABEL_NOTIFICATION_TEXT_SIZE,
                        maxValue = Constants.MAX_LABEL_NOTIFICATION_TEXT_SIZE,
                        currentValue = prefs.labelnotificationsTextSize,
                        onValueSelected = { newSize ->
                            prefs.labelnotificationsTextSize = newSize
                            labelnotificationsFontSize = newSize
                        }
                    )
                }
            )
            FullLineSeparator(isDark)

            SettingsTitle(
                text = "Notifications Window",
                fontSize = titleFontSize
            )
            FullLineSeparator(isDark)
            SettingsSelect(
                title = "Window Title",
                option = notificationsTitle,
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showInputDialog(
                        context = requireContext(),
                        title = "Title",
                        initialValue = notificationsTitle,
                        onValueEntered = { newTitle ->
                            // Remove any newline characters to enforce single line
                            val singleLineTitle = newTitle.replace("\n", "")
                            prefs.lettersTitle = singleLineTitle
                            notificationsTitle =
                                singleLineTitle // <-- Add this line to update state
                        }
                    )
                }
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = "Title Font",
                option = if (notificationsTitleFontState == Constants.FontFamily.Custom)
                    getFontDisplayName(notificationsTitleFontState, "lettersTitle")
                else notificationsTitleFontState.name,
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledNotif) {
                        showFontSelectionDialogWithCustoms(
                            R.string.notifications_font,
                            "lettersTitle"
                        ) { newFont, customPath ->
                            prefs.lettersTitleFont = newFont
                            notificationsTitleFontState = newFont
                            if (newFont == Constants.FontFamily.Custom && customPath != null) {
                                prefs.setCustomFontPath("lettersTitle", customPath)
                            }
                        }
                    }
                },
                fontColor = if (!universalFontEnabledNotif)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledNotif
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = "Title Size",
                option = notificationsTitleSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = "Title Size",
                        minValue = 10,
                        maxValue = 60,
                        currentValue = notificationsTitleSize,
                        onValueSelected = { newSize ->
                            prefs.lettersTitleSize = newSize
                            notificationsTitleSize = newSize
                        }
                    )
                }
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = "Body Font",
                option = if (universalFontEnabledNotif) {
                    val universalFont = prefs.universalFont
                    if (universalFont == Constants.FontFamily.Custom)
                        getFontDisplayName(universalFont, "universal")
                    else universalFont.name
                } else if (notificationsFontState == Constants.FontFamily.Custom)
                    getFontDisplayName(notificationsFontState, "notifications")
                else notificationsFontState.name,
                fontSize = titleFontSize,
                onClick = {
                    if (!universalFontEnabledNotif) {
                        showFontSelectionDialogWithCustoms(
                            R.string.notifications_font,
                            "notifications"
                        ) { newFont, customPath ->
                            prefs.notificationsFont = newFont
                            notificationsFontState = newFont
                            if (newFont == Constants.FontFamily.Custom && customPath != null) {
                                prefs.setCustomFontPath("notifications", customPath)
                            }
                        }
                    }
                },
                fontColor = if (!universalFontEnabledNotif)
                    SettingsTheme.typography.title.color
                else Color.Gray,
                enabled = !universalFontEnabledNotif
            )
            DashedSeparator(isDark)
            SettingsSelect(
                title = "Body Text Size",
                option = notificationsTextSize.toString(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSliderDialog(
                        context = requireContext(),
                        title = "Body Text Size",
                        minValue = Constants.MIN_LABEL_NOTIFICATION_TEXT_SIZE,
                        maxValue = Constants.MAX_LABEL_NOTIFICATION_TEXT_SIZE,
                        currentValue = notificationsTextSize,
                        onValueSelected = { newSize ->
                            prefs.notificationsTextSize = newSize
                            notificationsTextSize = newSize
                        }
                    )
                }
            )
            FullLineSeparator(isDark)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    private fun getFontDisplayName(font: Constants.FontFamily, contextKey: String): String {
        return if (font == Constants.FontFamily.Custom) {
            val path = if (contextKey == "notifications") {
                // Use the correct custom font path for notifications
                prefs.getCustomFontPath("notifications")
                    ?: prefs.getCustomFontPath("universal")
            } else {
                prefs.getCustomFontPathForContext(contextKey)
            }
            path?.let { File(it).name } ?: font.name
        } else {
            font.name
        }
    }

    private fun showFontSelectionDialogWithCustoms(
        titleResId: Int,
        contextKey: String,
        onFontSelected: (Constants.FontFamily, String?) -> Unit
    ) {
        val fontFamilyEntries = Constants.FontFamily.entries
            .filter { it != Constants.FontFamily.Custom }
        val context = requireContext()
        val prefs = Prefs(context)

        val builtInFontOptions = fontFamilyEntries.map { it.getString(context) }
        val builtInFonts = fontFamilyEntries.map { it.getFont(context) ?: getTrueSystemFont() }

        val customFonts = Constants.FontFamily.getAllCustomFonts(context)
        val customFontOptions = customFonts.map { it.first }
        val customFontPaths = customFonts.map { it.second }
        val customFontTypefaces = customFontPaths.map { path ->
            Constants.FontFamily.Custom.getFont(context, path) ?: getTrueSystemFont()
        }

        val addCustomFontOption = "Add Custom Font..."

        val options = builtInFontOptions + customFontOptions + addCustomFontOption
        val fonts = builtInFonts + customFontTypefaces + getTrueSystemFont()

        dialogBuilder.showSingleChoiceDialog(
            context = context,
            options = options.toTypedArray(),
            fonts = fonts,
            titleResId = titleResId,
            isCustomFont = { option ->
                customFontOptions.contains(option)
            },
            onItemSelected = { selectedName ->
                // Use string comparison to handle reordered options
                if (selectedName.toString() == addCustomFontOption) {
                    pickCustomFontFile { typeface, path ->
                        prefs.setCustomFontPath(
                            contextKey,
                            path
                        )
                        prefs.addCustomFontPath(path)
                        onFontSelected(Constants.FontFamily.Custom, path)
                        activity?.recreate()
                    }
                } else {
                    val builtInIndex = builtInFontOptions.indexOf(selectedName)
                    if (builtInIndex != -1) {
                        onFontSelected(fontFamilyEntries[builtInIndex], null)
                        return@showSingleChoiceDialog
                    }
                    val customIndex = customFontOptions.indexOf(selectedName)
                    if (customIndex != -1) {
                        val path = customFontPaths[customIndex]
                        prefs.setCustomFontPath(
                            contextKey,
                            path
                        )
                        onFontSelected(Constants.FontFamily.Custom, path)
                        return@showSingleChoiceDialog
                    }
                }
            },
            onItemDeleted = { deletedName ->
                val customIndex = customFontOptions.indexOf(deletedName)
                if (customIndex != -1) {
                    val path = customFontPaths[customIndex]
                    prefs.removeCustomFontPathByPath(path)
                    val allKeys = prefs.customFontPathMap.filterValues { it == path }.keys
                    for (key in allKeys) {
                        prefs.removeCustomFontPath(key)
                    }
                    showFontSelectionDialogWithCustoms(titleResId, contextKey, onFontSelected)
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun pickCustomFontFile(onFontPicked: (Typeface, String) -> Unit) {
        onCustomFontSelected = onFontPicked
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "font/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/octet-stream"
                )
            )
        }
        startActivityForResult(intent, PICK_FONT_FILE_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FONT_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val fontFile = copyFontToInternalStorage(uri)
                if (fontFile != null) {
                    try {
                        val typeface = Typeface.createFromFile(fontFile)
                        // Try to access a property to force load
                        typeface.style
                        onCustomFontSelected?.invoke(typeface, fontFile.absolutePath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Show error dialog to user
                        dialogBuilder.showErrorDialog(
                            requireContext(),
                            title = "Invalid Font File",
                            message = "The selected file could not be loaded as a font. Please choose a valid font file."
                        )
                    }
                } else {
                    dialogBuilder.showErrorDialog(
                        requireContext(),
                        title = "File Error",
                        message = "Could not copy the selected file. Please try again."
                    )
                }
            }
        }
    }

    private fun copyFontToInternalStorage(uri: Uri): File? {
        val fileName = getFileName(uri) ?: "custom_font.ttf"
        val file = File(requireContext().filesDir, fileName)
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // No binding to clean up in Compose
    }

    override fun onStop() {
        super.onStop()
        dialogBuilder.singleChoiceDialog?.dismiss()
    }
}