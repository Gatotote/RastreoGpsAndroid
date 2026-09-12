package com.genarovelasco.rastreogps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Acento de marca: degradado coral -> naranja.
// Oscuro por defecto: el mapa OSM lleva filtro oscuro y la hoja se monta encima.
val RastreoGradiente = Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFB86C)))
val RastreoGradienteInk = Color(0xFF1B1B20) // texto/icono encima del degradado

private val Oscuro = darkColorScheme(
    primary = Color(0xFFFF8A6B),
    onPrimary = Color(0xFF2A1710),
    primaryContainer = Color(0xFF3C2A22),
    onPrimaryContainer = Color(0xFFFFDACB),
    secondary = Color(0xFFA66CFF),        // etiquetas
    onSecondary = Color(0xFF241237),
    secondaryContainer = Color(0xFF33234D),
    onSecondaryContainer = Color(0xFFE7D6FF),
    tertiary = Color(0xFF4CD4E0),          // "en línea"
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF124349),
    onTertiaryContainer = Color(0xFF9CF0F7),
    background = Color(0xFF1B1B21),
    onBackground = Color(0xFFEDECF2),
    surface = Color(0xFF26262E),
    onSurface = Color(0xFFEDECF2),
    surfaceVariant = Color(0xFF2F2F39),
    onSurfaceVariant = Color(0xFFC3C3CE),
    surfaceContainerLowest = Color(0xFF16161B),
    surfaceContainerLow = Color(0xFF212129),
    surfaceContainer = Color(0xFF26262E),
    surfaceContainerHigh = Color(0xFF2F2F39),
    surfaceContainerHighest = Color(0xFF393943),
    outline = Color(0xFF8A8A97),
    outlineVariant = Color(0xFF3A3A46),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val Claro = lightColorScheme(
    primary = Color(0xFFB23A22),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF7A3DC7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDDCFF),
    onSecondaryContainer = Color(0xFF29104B),
    tertiary = Color(0xFF00696F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF0F7),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFF5F3F5),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFECE7EC),
    onSurfaceVariant = Color(0xFF4A4A52),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3EFF3),
    surfaceContainer = Color(0xFFEEEAEE),
    surfaceContainerHigh = Color(0xFFE8E4E8),
    surfaceContainerHighest = Color(0xFFE2DEE2),
    outline = Color(0xFF7B7B85),
    outlineVariant = Color(0xFFCDCAD2),
)

@Composable
fun RastreoTheme(oscuro: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) Oscuro else Claro,
        typography = RastreoTipografia,
        content = content,
    )
}
