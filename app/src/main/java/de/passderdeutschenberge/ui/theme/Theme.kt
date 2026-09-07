package de.passderdeutschenberge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farbwelt: Fels, Wald, Höhenlinien-Braun. Bewusst kein dynamic color, damit die
// Fortschrittsfarben auf der Karte auf jedem Geraet gleich lesbar bleiben.
private val Stone = Color(0xFF3F4A54)
private val StoneLight = Color(0xFF6B7885)
private val Forest = Color(0xFF2F6B4F)
private val ForestLight = Color(0xFF7FBF9B)
private val Sand = Color(0xFF8A6A3B)
private val SandLight = Color(0xFFD9BC8C)

private val LightColors = lightColorScheme(
    primary = Stone,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E0EA),
    onPrimaryContainer = Color(0xFF16202A),
    secondary = Forest,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBDB),
    onSecondaryContainer = Color(0xFF0A2418),
    tertiary = Sand,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E3CB),
    onTertiaryContainer = Color(0xFF2C1D06),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1B1C1D),
    surface = Color(0xFFFAFAF7),
    onSurface = Color(0xFF1B1C1D),
    surfaceVariant = Color(0xFFE2E4E6),
    onSurfaceVariant = Color(0xFF44474A),
    outline = Color(0xFF74787C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC2D4),
    onPrimary = Color(0xFF1B2732),
    primaryContainer = Color(0xFF33404C),
    onPrimaryContainer = Color(0xFFD7E0EA),
    secondary = ForestLight,
    onSecondary = Color(0xFF0A2418),
    secondaryContainer = Color(0xFF23503B),
    onSecondaryContainer = Color(0xFFCDEBDB),
    tertiary = SandLight,
    onTertiary = Color(0xFF2C1D06),
    tertiaryContainer = Color(0xFF5F4726),
    onTertiaryContainer = Color(0xFFF3E3CB),
    background = Color(0xFF121415),
    onBackground = Color(0xFFE3E2E1),
    surface = Color(0xFF121415),
    onSurface = Color(0xFFE3E2E1),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CD),
    outline = Color(0xFF8B9198),
)

/** Farbcodierung des Fortschritts - identisch auf Karte, Listen und Ringen. */
object ProgressColors {
    val done: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val started: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val open: Color @Composable get() = StoneLight
}

@Composable
fun PassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
