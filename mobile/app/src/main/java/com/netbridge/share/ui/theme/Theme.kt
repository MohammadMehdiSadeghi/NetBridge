package com.netbridge.share.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.netbridge.share.R

val BonyadeKoodak = FontFamily(
    Font(R.font.bonyade_koodak_regular, FontWeight.Normal),
    Font(R.font.bonyade_koodak_medium, FontWeight.Medium),
    Font(R.font.bonyade_koodak_bold, FontWeight.Bold),
    Font(R.font.bonyade_koodak_extrabold, FontWeight.ExtraBold)
)

val Bg0 = Color(0xFF0B0F14)
val Bg1 = Color(0xFF101722)
val Bg2 = Color(0xFF162030)
val Stroke = Color(0xFF1F2B3D)
val TextPrimary = Color(0xFFE8EEF7)
val TextSecondary = Color(0xFF8FA0B8)
val Brand = Color(0xFF22D3EE)
val BrandDim = Color(0xFF0E7490)
val Accent = Color(0xFFA78BFA)
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)

private val NetBridgeColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color(0xFF041018),
    secondary = Accent,
    onSecondary = Color(0xFF12081F),
    background = Bg0,
    onBackground = TextPrimary,
    surface = Bg1,
    onSurface = TextPrimary,
    surfaceVariant = Bg2,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    error = Danger
)

private val NetBridgeTypography = Typography(
    displayLarge = TextStyle(fontFamily = BonyadeKoodak, fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = BonyadeKoodak, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = BonyadeKoodak, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = BonyadeKoodak, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = BonyadeKoodak, fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = BonyadeKoodak, fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = BonyadeKoodak, fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = BonyadeKoodak, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = BonyadeKoodak, fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NetBridgeTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember { com.netbridge.share.data.SettingsStore(context) }
    val isRtl = store.appLang != "en"
    val layoutDir = if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDir
    ) {
        MaterialTheme(
            colorScheme = NetBridgeColors,
            typography = NetBridgeTypography,
            content = content
        )
    }
}
