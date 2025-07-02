package com.github.gezimos.inkos.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Constants.Theme.Dark
import com.github.gezimos.inkos.data.Constants.Theme.Light
import com.github.gezimos.inkos.data.Constants.Theme.System
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getHexForOpacity
import com.github.gezimos.inkos.helper.hideStatusBar
import com.github.gezimos.inkos.helper.isSystemInDarkMode
import com.github.gezimos.inkos.helper.setThemeMode
import com.github.gezimos.inkos.helper.showStatusBar
import com.github.gezimos.inkos.listener.DeviceAdmin
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.ui.compose.SettingsComposable.DashedSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.FullLineSeparator
import com.github.gezimos.inkos.ui.compose.SettingsComposable.PageHeader
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSelect
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSelectWithColorPreview
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsSwitch
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsTitle
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SolidSeparator
import com.github.gezimos.inkos.ui.dialogs.DialogManager

class LookFeelFragment : Fragment() {

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
        val backgroundColor = getHexForOpacity(prefs)
        val isDark = when (prefs.appTheme) {
            Light -> false
            Dark -> true
            System -> isSystemInDarkMode(requireContext())
        }
        val settingsSize = (prefs.settingsSize - 3)
        val context = requireContext()
        val currentPage = intArrayOf(0)
        val pageCount = intArrayOf(1)

        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        var bottomInsetPx = 0
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val navBarInset =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            bottomInsetPx = navBarInset
            insets
        }

        // Marked as @Composable for Compose usage
        @Composable
        fun HeaderContent() {
            androidx.compose.ui.platform.LocalDensity.current
            // Remove bottomInsetDp from header
            SettingsTheme(isDark) {
                Column(Modifier.fillMaxWidth()) {
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = stringResource(R.string.look_feel_settings_title),
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
                    // (No bottomInsetDp here)
                }
            }
        }

        val headerView = androidx.compose.ui.platform.ComposeView(context).apply {
            setContent { HeaderContent() }
        }
        rootLayout.addView(headerView)

        val nestedScrollView = androidx.core.widget.NestedScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
            addView(
                androidx.compose.ui.platform.ComposeView(context).apply {
                    setContent {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val bottomInsetDp = with(density) { bottomInsetPx.toDp() }
                        SettingsTheme(isDark) {
                            Box(Modifier.fillMaxSize()) {
                                Column {
                                    LookFeelSettingsAllInOne(settingsSize.sp, isDark)
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
        com.github.gezimos.inkos.helper.utils.EinkScrollBehavior(context)
            .attachToScrollView(nestedScrollView)
        rootLayout.addView(
            nestedScrollView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

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

        fun updateHeaderAndPages(scrollY: Int = nestedScrollView.scrollY) {
            val contentHeight = nestedScrollView.getChildAt(0)?.height ?: 1
            val viewportHeight = nestedScrollView.height.takeIf { it > 0 } ?: 1
            val overlap = (viewportHeight * 0.2).toInt()
            val scrollStep = viewportHeight - overlap
            val pages =
                Math.ceil(((contentHeight - viewportHeight).toDouble() / scrollStep.toDouble()))
                    .toInt() + 1
            pageCount[0] = pages
            currentPage[0] = getCurrentPageIndex(scrollY, viewportHeight, contentHeight, pages)
            headerView.setContent { HeaderContent() }
        }
        nestedScrollView.viewTreeObserver.addOnGlobalLayoutListener {
            updateHeaderAndPages()
        }
        nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateHeaderAndPages(scrollY)
        }
        return rootLayout
    }

    @Composable
    fun LookFeelSettingsAllInOne(fontSize: TextUnit = TextUnit.Unspecified, isDark: Boolean) {
        findNavController()
        val titleFontSize = if (fontSize.isSpecified) (fontSize.value * 1.5).sp else fontSize
        var toggledShowStatusBar = remember { mutableStateOf(prefs.showStatusBar) }
        var selectedTheme = remember { mutableStateOf(prefs.appTheme) }
        var selectedBackgroundColor = remember { mutableStateOf(prefs.backgroundColor) }
        var selectedAppColor = remember { mutableStateOf(prefs.appColor) }
        var selectedClockColor = remember { mutableStateOf(prefs.clockColor) }
        var selectedBatteryColor = remember { mutableStateOf(prefs.batteryColor) }
        var einkRefreshEnabled = remember { mutableStateOf(prefs.einkRefreshEnabled) }
        var vibrationForPaging = remember { mutableStateOf(prefs.useVibrationForPaging) }
        Constants.updateMaxHomePages(requireContext())
        Column(modifier = Modifier.fillMaxSize()) {
            // Theme Mode
            FullLineSeparator(isDark = isDark)

            // Visibility & Display
            SettingsTitle(
                text = stringResource(R.string.visibility_display),
                fontSize = titleFontSize,
            )
            FullLineSeparator(isDark)
            SettingsSelect(
                title = stringResource(R.string.theme_mode),
                option = selectedTheme.value.string(),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showSingleChoiceDialog(
                        context = requireContext(),
                        options = Constants.Theme.entries.toTypedArray(),
                        titleResId = R.string.theme_mode,
                        onItemSelected = { newTheme ->
                            selectedTheme.value = newTheme
                            prefs.appTheme = newTheme
                            val isDark = when (newTheme) {
                                Light -> false
                                Dark -> true
                                System -> isSystemInDarkMode(requireContext())
                            }
                            prefs.backgroundColor =
                                if (isDark) Color.Black.toArgb() else Color.White.toArgb()
                            prefs.appColor =
                                if (isDark) Color.White.toArgb() else Color.Black.toArgb()
                            prefs.clockColor =
                                if (isDark) Color.White.toArgb() else Color.Black.toArgb()
                            prefs.batteryColor =
                                if (isDark) Color.White.toArgb() else Color.Black.toArgb()
                            setThemeMode(
                                requireContext(),
                                isDark,
                                requireActivity().window.decorView
                            )
                            requireActivity().recreate()
                        }
                    )
                }
            )
            // --- Eink Refresh Switch inserted here ---
            DashedSeparator(isDark)
            SettingsSwitch(
                text = "E-Ink Refresh",
                fontSize = titleFontSize,
                defaultState = einkRefreshEnabled.value,
                onCheckedChange = {
                    einkRefreshEnabled.value = it
                    prefs.einkRefreshEnabled = it
                }
            )
            DashedSeparator(isDark)
            SettingsSwitch(
                text = "Vibration for Paging",
                fontSize = titleFontSize,
                defaultState = vibrationForPaging.value,
                onCheckedChange = {
                    vibrationForPaging.value = it
                    prefs.useVibrationForPaging = it
                }
            )
            DashedSeparator(isDark)
            SettingsSwitch(
                text = stringResource(R.string.show_status_bar),
                fontSize = titleFontSize,
                defaultState = toggledShowStatusBar.value,
                onCheckedChange = {
                    toggledShowStatusBar.value = !prefs.showStatusBar
                    prefs.showStatusBar = toggledShowStatusBar.value
                    if (toggledShowStatusBar.value) showStatusBar(requireActivity()) else hideStatusBar(
                        requireActivity()
                    )
                }
            )
            // Element Colors
            FullLineSeparator(isDark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsTitle(
                    text = stringResource(R.string.element_colors),
                    fontSize = titleFontSize,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.reset),
                    style = SettingsTheme.typography.button,
                    fontSize = if (titleFontSize.isSpecified) (titleFontSize.value * 0.7).sp else 14.sp,
                    modifier = Modifier
                        .padding(end = SettingsTheme.color.horizontalPadding)
                        .clickable {
                            val isDarkMode = when (prefs.appTheme) {
                                Dark -> true
                                Light -> false
                                System -> isSystemInDarkMode(requireContext())
                            }
                            selectedBackgroundColor.value =
                                if (isDarkMode) Color.Black.toArgb() else Color.White.toArgb()
                            selectedAppColor.value =
                                if (isDarkMode) Color.White.toArgb() else Color.Black.toArgb()
                            selectedClockColor.value =
                                if (isDarkMode) Color.White.toArgb() else Color.Black.toArgb()
                            selectedBatteryColor.value =
                                if (isDarkMode) Color.White.toArgb() else Color.Black.toArgb()
                            prefs.backgroundColor = selectedBackgroundColor.value
                            prefs.appColor = selectedAppColor.value
                            prefs.clockColor = selectedClockColor.value
                            prefs.batteryColor = selectedBatteryColor.value
                        }
                )
            }
            FullLineSeparator(isDark)
            val hexBackgroundColor =
                String.format("#%06X", (0xFFFFFF and selectedBackgroundColor.value))
            SettingsSelectWithColorPreview(
                title = stringResource(R.string.background_color),
                hexColor = hexBackgroundColor,
                previewColor = Color(selectedBackgroundColor.value),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showColorPickerDialog(
                        context = requireContext(),
                        color = selectedBackgroundColor.value,
                        titleResId = R.string.background_color,
                        onItemSelected = { selectedColor ->
                            selectedBackgroundColor.value = selectedColor
                            prefs.backgroundColor = selectedColor
                        })
                }
            )
            DashedSeparator(isDark)
            val hexAppColor = String.format("#%06X", (0xFFFFFF and selectedAppColor.value))
            SettingsSelectWithColorPreview(
                title = stringResource(R.string.app_color),
                hexColor = hexAppColor,
                previewColor = Color(selectedAppColor.value),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showColorPickerDialog(
                        context = requireContext(),
                        color = selectedAppColor.value,
                        titleResId = R.string.app_color,
                        onItemSelected = { selectedColor ->
                            selectedAppColor.value = selectedColor
                            prefs.appColor = selectedColor
                        })
                }
            )

            DashedSeparator(isDark)
            val hexClockColor = String.format("#%06X", (0xFFFFFF and selectedClockColor.value))
            SettingsSelectWithColorPreview(
                title = stringResource(R.string.clock_color),
                hexColor = hexClockColor,
                previewColor = Color(selectedClockColor.value),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showColorPickerDialog(
                        context = requireContext(),
                        color = selectedClockColor.value,
                        titleResId = R.string.clock_color,
                        onItemSelected = { selectedColor ->
                            selectedClockColor.value = selectedColor
                            prefs.clockColor = selectedColor
                        })
                }
            )
            DashedSeparator(isDark)
            val hexBatteryColor = String.format("#%06X", (0xFFFFFF and selectedBatteryColor.value))
            SettingsSelectWithColorPreview(
                title = stringResource(R.string.battery_color),
                hexColor = hexBatteryColor,
                previewColor = Color(selectedBatteryColor.value),
                fontSize = titleFontSize,
                onClick = {
                    dialogBuilder.showColorPickerDialog(
                        context = requireContext(),
                        color = selectedBatteryColor.value,
                        titleResId = R.string.battery_color,
                        onItemSelected = { selectedColor ->
                            selectedBatteryColor.value = selectedColor
                            prefs.batteryColor = selectedColor
                        })
                }
            )
            FullLineSeparator(isDark)

        }
    }

    private fun goBackToLastFragment() {
        findNavController().popBackStack()
    }

    private fun dismissDialogs() {
        dialogBuilder.colorPickerDialog?.dismiss()
        dialogBuilder.singleChoiceDialog?.dismiss()
        dialogBuilder.sliderDialog?.dismiss()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        viewModel.isinkosDefault()

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
    }

    override fun onStop() {
        super.onStop()
        dismissDialogs()
    }
}