package pl.polsatgranie.smartmegane.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CockpitColorScheme = darkColorScheme(
    primary = Color(0xFF31C986),
    secondary = Color(0xFF4A91DC),
    tertiary = Color(0xFFD99A38),
    background = Color(0xFF080B0D),
    surface = Color(0xFF11171A),
    onPrimary = Color(0xFF04110C),
    onBackground = Color(0xFFE9EEF0),
    onSurface = Color(0xFFE9EEF0),
)

@Composable
fun SmartMeganeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CockpitColorScheme,
        typography = Typography,
        content = content,
    )
}
