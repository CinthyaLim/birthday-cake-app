package com.cinthya.birthdaycake.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The game's palette, in Material's terms.
 *
 * Every surface the app draws is either the pink page or the cream dialog panel, so the
 * scheme is built out of [GameColors] rather than left on the template. That matters most
 * for `onSurface`: it is what an unstyled [androidx.compose.material3.Text] inherits, and
 * on the wallpaper-derived scheme it could land anywhere - including near-white, which is
 * invisible on the cream panel.
 */
private val GameColorScheme = lightColorScheme(
    primary = GameColors.AccentOne,
    onPrimary = GameColors.MainBlack,
    secondary = GameColors.PastelPink,
    onSecondary = GameColors.MainBlack,
    tertiary = GameColors.AccentTwo,
    onTertiary = GameColors.MainBlack,
    background = GameColors.PastelPink,
    onBackground = GameColors.MainBlack,
    surface = GameColors.OffWhite,
    onSurface = GameColors.MainBlack,
    surfaceVariant = GameColors.AccentOne,
    onSurfaceVariant = GameColors.MainBlack,
    outline = GameColors.MainBlack,
    scrim = GameColors.Scrim,
)

/**
 * The game is a fixed illustration, so it has one palette and one palette only - no dark
 * variant, and dynamic colour deliberately off. Wallpaper-derived colours would repaint
 * the dialog panels a different shade on every device.
 */
@Composable
fun BirthdayCakeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = Typography,
        content = content
    )
}