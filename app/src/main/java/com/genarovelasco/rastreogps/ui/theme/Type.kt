package com.genarovelasco.rastreogps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.genarovelasco.rastreogps.R

// Fuentes variables (res/font/sora.ttf, res/font/manrope.ttf). El eje wght se
// aplica solo desde el FontWeight en API 26+.
val Sora = FontFamily(
    Font(R.font.sora, weight = FontWeight.Normal),
    Font(R.font.sora, weight = FontWeight.Medium),
    Font(R.font.sora, weight = FontWeight.SemiBold),
    Font(R.font.sora, weight = FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope, weight = FontWeight.Medium),
    Font(R.font.manrope, weight = FontWeight.SemiBold),
    Font(R.font.manrope, weight = FontWeight.Bold),
)

// Sora para títulos/números/botones; Manrope para texto y etiquetas.
val RastreoTipografia: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Sora),
        displayMedium = displayMedium.copy(fontFamily = Sora),
        displaySmall = displaySmall.copy(fontFamily = Sora),
        headlineLarge = headlineLarge.copy(fontFamily = Sora),
        headlineMedium = headlineMedium.copy(fontFamily = Sora),
        headlineSmall = headlineSmall.copy(fontFamily = Sora),
        titleLarge = titleLarge.copy(fontFamily = Sora),
        titleMedium = titleMedium.copy(fontFamily = Sora),
        titleSmall = titleSmall.copy(fontFamily = Sora),
        bodyLarge = bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = bodyMedium.copy(fontFamily = Manrope),
        bodySmall = bodySmall.copy(fontFamily = Manrope),
        labelLarge = labelLarge.copy(fontFamily = Manrope),
        labelMedium = labelMedium.copy(fontFamily = Manrope),
        labelSmall = labelSmall.copy(fontFamily = Manrope),
    )
}
