package com.github.gezimos.inkos.ui.compose

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.style.SettingsTheme

object OnboardingScreen {

    @Composable
    fun Show(
        onFinish: () -> Unit = {},
        onRequestNotificationPermission: (() -> Unit)? = null
    ) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        var pushNotificationsEnabled by remember { mutableStateOf(prefs.pushNotificationsEnabled) }
        var showClock by remember { mutableStateOf(prefs.showClock) }
        var showBattery by remember { mutableStateOf(prefs.showBattery) }
        var showStatusBar by remember { mutableStateOf(prefs.showStatusBar) }

        // State for onboarding page
        var page by remember { mutableStateOf(prefs.onboardingPage) }
        val totalPages = 3
        val settingsSize = (prefs.settingsSize - 3)
        val titleFontSize = (settingsSize * 1.5).sp

        // Persist onboarding page index when it changes
        LaunchedEffect(page) {
            prefs.onboardingPage = page
        }

        // Determine background color using the same logic as HomeFragment/Prefs
        val isDark = when (prefs.appTheme) {
            Constants.Theme.Light -> false
            Constants.Theme.Dark -> true
            Constants.Theme.System -> com.github.gezimos.inkos.helper.isSystemInDarkMode(
                context
            )
        }
        val backgroundColor = when (prefs.appTheme) {
            Constants.Theme.System ->
                if (com.github.gezimos.inkos.helper.isSystemInDarkMode(context)) Color.Black else Color.White

            Constants.Theme.Dark -> Color.Black
            Constants.Theme.Light -> Color.White
        }
        val topPadding = if (prefs.showStatusBar) 48.dp else 48.dp
        // Calculate bottom padding for nav bar/gestures
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        SettingsTheme(isDark = isDark) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(backgroundColor)
            ) {
                // Top-aligned welcome and description
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_ink),
                        contentDescription = "InkOS Logo",
                        colorFilter = ColorFilter.tint(SettingsTheme.typography.title.color ?: Color.Unspecified),
                        modifier = Modifier
                            .width(24.dp)
                            .padding(bottom = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "inkOS",
                        style = SettingsTheme.typography.title,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "A text based launcher.",
                        style = SettingsTheme.typography.body,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .padding(start = 36.dp, end = 36.dp)
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        textAlign = TextAlign.Center
                    )
                }
                // Vertically centered switches, 3 per page
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // FocusRequesters for first item on each page
                    val focusRequesterPage0 = remember { FocusRequester() }
                    val focusRequesterPage1 = remember { FocusRequester() }
                    val focusRequesterPage2 = remember { FocusRequester() }
                    // Move focus to first item on page change
                    var einkRefreshEnabled by remember { mutableStateOf(prefs.einkRefreshEnabled) }
                    var vibrationFeedback by remember { mutableStateOf(prefs.useVibrationForPaging) }
                    var volumeKeyNavigation by remember { mutableStateOf(prefs.useVolumeKeysForPages) }
                    var lastToggledSwitch by remember { mutableStateOf<String?>(null) }
                    when (page) {
                        0 -> {
                            // Page 1: Status Bar, Clock, Battery
                            SettingsComposable.SettingsSwitch(
                                text = "Show Status Bar",
                                fontSize = titleFontSize,
                                defaultState = showStatusBar,
                                modifier = Modifier.focusRequester(focusRequesterPage0),
                                onCheckedChange = {
                                    showStatusBar = it
                                    prefs.showStatusBar = it
                                }
                            )
                            LaunchedEffect(page) {
                                focusRequesterPage0.requestFocus()
                            }
                            SettingsComposable.FullLineSeparator(isDark = false)
                            SettingsComposable.SettingsSwitch(
                                text = "Show Clock",
                                fontSize = titleFontSize,
                                defaultState = showClock,
                                onCheckedChange = {
                                    showClock = it
                                    prefs.showClock = it
                                }
                            )
                            SettingsComposable.FullLineSeparator(isDark = false)
                            SettingsComposable.SettingsSwitch(
                                text = "Show Battery",
                                fontSize = titleFontSize,
                                defaultState = showBattery,
                                onCheckedChange = {
                                    showBattery = it
                                    prefs.showBattery = it
                                }
                            )
                        }
                        1 -> {
                            // Page 2: Einkrefresh, Vibration feedback, Volume key navigation
                            SettingsComposable.SettingsSwitch(
                                text = "Eink Refresh",
                                fontSize = titleFontSize,
                                defaultState = einkRefreshEnabled,
                                modifier = Modifier.focusRequester(focusRequesterPage1),
                                onCheckedChange = {
                                    einkRefreshEnabled = it
                                    prefs.einkRefreshEnabled = it
                                    if (it) lastToggledSwitch = "eink" else if (lastToggledSwitch == "eink") lastToggledSwitch = null
                                }
                            )
                            LaunchedEffect(page) {
                                focusRequesterPage1.requestFocus()
                            }
                            SettingsComposable.FullLineSeparator(isDark = false)
                            SettingsComposable.SettingsSwitch(
                                text = "Vibration Feedback",
                                fontSize = titleFontSize,
                                defaultState = vibrationFeedback,
                                onCheckedChange = {
                                    vibrationFeedback = it
                                    prefs.useVibrationForPaging = it
                                    if (it) lastToggledSwitch = "vibration" else if (lastToggledSwitch == "vibration") lastToggledSwitch = null
                                }
                            )
                            SettingsComposable.FullLineSeparator(isDark = false)
                            SettingsComposable.SettingsSwitch(
                                text = "Volume Key Navigation",
                                fontSize = titleFontSize,
                                defaultState = volumeKeyNavigation,
                                onCheckedChange = {
                                    volumeKeyNavigation = it
                                    prefs.useVolumeKeysForPages = it
                                    if (it) lastToggledSwitch = "volume" else if (lastToggledSwitch == "volume") lastToggledSwitch = null
                                }
                            )
                        }
                        2 -> {
                            // Page 3: Notifications first, then theme mode
                            SettingsComposable.SettingsSwitch(
                                text = "Enable Notifications",
                                fontSize = titleFontSize,
                                defaultState = pushNotificationsEnabled,
                                modifier = Modifier.focusRequester(focusRequesterPage2),
                                onCheckedChange = {
                                    pushNotificationsEnabled = it
                                    prefs.pushNotificationsEnabled = it
                                    if (it) {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        onRequestNotificationPermission?.invoke()
                                    }
                                }
                            )
                            LaunchedEffect(page) {
                                focusRequesterPage2.requestFocus()
                            }
                            SettingsComposable.FullLineSeparator(isDark = false)
                            // Theme Mode selector (cycles through System, Light, Dark)
                            var themeMode by remember { mutableStateOf(prefs.appTheme) }
                            SettingsComposable.SettingsSelect(
                                title = "Theme Mode",
                                option = themeMode.name,
                                fontSize = titleFontSize,
                                onClick = {
                                    // Cycle through System -> Light -> Dark -> System
                                    val next = when (themeMode) {
                                        Constants.Theme.System -> Constants.Theme.Light
                                        Constants.Theme.Light -> Constants.Theme.Dark
                                        Constants.Theme.Dark -> Constants.Theme.System
                                    }
                                    themeMode = next
                                    prefs.appTheme = next
                                }
                            )
                            SettingsComposable.FullLineSeparator(isDark = false)
                            SettingsComposable.SettingsHomeItem(
                                title = "Set as Default Launcher",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                },
                                titleFontSize = titleFontSize
                            )
                        }
                    }
                    SettingsComposable.FullLineSeparator(isDark = false)
                    // Dynamic tip based on enabled switches
                    val tipText = when {
                        page == 0 && showBattery -> "Shows the battery % at the bottom."
                        page == 0 && showClock -> "Clock widget appears above apps."
                        page == 0 && showStatusBar -> "This will show the status bar."
                        page == 1 && lastToggledSwitch == "eink" && einkRefreshEnabled -> "To clear ghosting in Mudita Komapkt"
                        page == 1 && lastToggledSwitch == "vibration" && vibrationFeedback -> "Vibration feedback on swipes"
                        page == 1 && lastToggledSwitch == "volume" && volumeKeyNavigation -> "Use vol. keys to change pages/values."
                        page == 1 && einkRefreshEnabled -> "Screen refresh to clear ghosting"
                        page == 1 && vibrationFeedback -> "Vibration feedback on swipes"
                        page == 1 && volumeKeyNavigation -> "Use vol. keys to change pages/values."
                        else -> null
                    }
                    val defaultTip = if (page == 2) "Tip: Longpress 9 in home for settings" else "Tip: Longpress in home for settings"
                    if (tipText != null) {
                        Text(
                            text = tipText,
                            style = SettingsTheme.typography.body,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 36.dp, end = 36.dp, top = 24.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = defaultTip,
                            style = SettingsTheme.typography.body,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 36.dp, end = 36.dp, top = 24.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // Bottom-aligned navigation buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    val backInteractionSource = remember { MutableInteractionSource() }
                    val backIsFocused = backInteractionSource.collectIsFocusedAsState().value
                    val focusColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .then(if (backIsFocused) Modifier.background(focusColor) else Modifier)
                            .clickable(
                                enabled = page > 0,
                                interactionSource = backInteractionSource,
                                indication = null
                            ) {
                                if (page > 0) page--
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (page > 0) {
                            Text(
                                text = "Back",
                                style = SettingsTheme.typography.title,
                                fontSize = titleFontSize,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp)
                            )
                        }
                    }
                    // Page indicator in the center
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SettingsComposable.PageIndicator(
                            currentPage = page,
                            pageCount = totalPages
                        )
                    }
                    // Next/Finish button
                    val nextInteractionSource = remember { MutableInteractionSource() }
                    val nextIsFocused = nextInteractionSource.collectIsFocusedAsState().value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .then(if (nextIsFocused) Modifier.background(focusColor) else Modifier)
                            .clickable(
                                interactionSource = nextInteractionSource,
                                indication = null
                            ) {
                                if (page < totalPages - 1) page++ else onFinish()
                            },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = if (page < totalPages - 1) "Next" else "Finish",
                            style = SettingsTheme.typography.title,
                            fontSize = titleFontSize,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp)
                        )
                    }
                }
            }
        }
    }
}