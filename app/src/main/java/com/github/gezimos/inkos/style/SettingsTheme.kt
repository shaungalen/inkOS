package com.github.gezimos.inkos.style

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getTrueSystemFont

@Immutable
data class ReplacementTypography(
    val header: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val item: TextStyle,
    val button: TextStyle,
    val buttonDisabled: TextStyle,
)

@Immutable
data class ReplacementColor(
    val settings: Color,
    val image: Color,
    val selector: Color,
    val border: Color,
    val horizontalPadding: Dp,
)

val LocalReplacementTypography = staticCompositionLocalOf {
    ReplacementTypography(
        header = TextStyle.Default,
        title = TextStyle.Default,
        body = TextStyle.Default,
        item = TextStyle.Default,
        button = TextStyle.Default,
        buttonDisabled = TextStyle.Default,
    )
}
val LocalReplacementColor = staticCompositionLocalOf {
    ReplacementColor(
        settings = Color.Unspecified,
        image = Color.Unspecified,
        selector = Color.Unspecified,
        border = Color.Unspecified,
        horizontalPadding = 0.dp,
    )
}

@Composable
fun SettingsTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = Prefs(context)

    // Get current font from preferences
    val customPath = if (prefs.fontFamily == Constants.FontFamily.Custom) {
        prefs.getCustomFontPath("settings")
    } else null
    val currentFont = prefs.fontFamily.getFont(context, customPath) ?: getTrueSystemFont()

    val replacementTypography = ReplacementTypography(
        header = TextStyle(
            fontSize = 16.sp,
            color = if (isDark) textLightHeader else textDarkHeader,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
        title = TextStyle(
            fontSize = 32.sp,
            color = if (isDark) textLight else textDark,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
        body = TextStyle(
            fontSize = 16.sp,
            color = if (isDark) textLight else textDark,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
        item = TextStyle(
            fontSize = 16.sp,
            color = if (isDark) textLight else textDark,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
        button = TextStyle(
            fontSize = 16.sp,
            color = if (isDark) textLight else textDark,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
        buttonDisabled = TextStyle(
            fontSize = 16.sp,
            color = textGray,
            fontFamily = androidx.compose.ui.text.font.FontFamily(currentFont)
        ),
    )
    val replacementColor = ReplacementColor(
        settings = if (isDark) Color.White else Color.Black,
        image = if (isDark) Color.White else Color.Black,
        selector = if (isDark) Color.White else Color.Black,
        border = if (isDark) Color.White else Color.Black,
        horizontalPadding = 24.dp
    )
    CompositionLocalProvider(
        LocalReplacementTypography provides replacementTypography,
        LocalReplacementColor provides replacementColor,
    ) {
        MaterialTheme(
            content = content
        )
    }
}

object SettingsTheme {
    val typography: ReplacementTypography
        @Composable
        get() = LocalReplacementTypography.current

    val color: ReplacementColor
        @Composable
        get() = LocalReplacementColor.current
}
