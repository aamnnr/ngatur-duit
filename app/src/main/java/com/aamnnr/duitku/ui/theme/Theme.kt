package com.aamnnr.duitku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aamnnr.duitku.R

// Sistem desain v2 "Soft Neumorphism" — nilai dan aturannya ada di DESIGN.md.
val Espresso = Color(0xFF2A2421)
val Caramel = Color(0xFFC08A5B)

/** True bila tema gelap aktif (mengikuti pilihan tema di Pengaturan, bukan hanya sistem). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Latar elemen gelap (kartu dompet, header form): espresso di kedua mode. */
val HeaderBg: Color
    @Composable @ReadOnlyComposable
    get() = Espresso

val OnHeader: Color
    @Composable @ReadOnlyComposable
    get() = Color(0xFFF8F6F3)

val OnHeaderMuted: Color
    @Composable @ReadOnlyComposable
    get() = OnHeader.copy(alpha = 0.66f)

/** Warna bayangan lembut kartu. */
val CardShadow: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) Color.Black else Espresso

val IncomeGreen: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) Color(0xFF7FBF95) else Color(0xFF3F7D58)

val ExpenseRed: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) Color(0xFFE38B72) else Color(0xFFB4533A)

val TransferBlue: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) Color(0xFF8FAACB) else Color(0xFF4E6E8E)

private val LightColors = lightColorScheme(
    primary = Espresso,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFEAE6),
    onPrimaryContainer = Espresso,
    secondary = Caramel,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E9DE),
    onSecondaryContainer = Color(0xFF6B4626),
    tertiary = Color(0xFF3F7D58),
    error = Color(0xFFB4533A),
    background = Color(0xFFF8F6F3),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF8F6F3),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0EEEB),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF2F0ED),
    surfaceContainerHigh = Color(0xFFECEAE7),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
)

private val DarkColors = darkColorScheme(
    // Di mode gelap, aksi utama memakai karamel agar kontras.
    primary = Caramel,
    onPrimary = Color(0xFF1C1C1E),
    primaryContainer = Color(0xFF2E2622),
    onPrimaryContainer = Color(0xFFF8F6F3),
    secondary = Caramel,
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF3A2D22),
    onSecondaryContainer = Color(0xFFF2E3D5),
    tertiary = Color(0xFF7FBF95),
    error = Color(0xFFE38B72),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF8F6F3),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF8F6F3),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA1A1A1),
    surfaceContainerLowest = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF1E1E1E),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val JakartaSans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = JakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    displayLarge = style(40, FontWeight.Bold, 48),
    displayMedium = style(34, FontWeight.Bold, 42),
    displaySmall = style(30, FontWeight.Bold, 38, -0.5f),
    headlineLarge = style(28, FontWeight.Bold, 36),
    headlineMedium = style(26, FontWeight.Bold, 34, -0.3f),
    headlineSmall = style(22, FontWeight.SemiBold, 30),
    titleLarge = style(20, FontWeight.SemiBold, 28),
    titleMedium = style(17, FontWeight.SemiBold, 24),
    titleSmall = style(15, FontWeight.SemiBold, 22),
    bodyLarge = style(16, FontWeight.Medium, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.2f),
    labelSmall = style(11, FontWeight.Medium, 14, 0.2f),
)

@Composable
fun DuitkuTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
