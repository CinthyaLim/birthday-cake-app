package com.cinthya.birthdaycake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cinthya.birthdaycake.R

object AppFont {
    val PoppinsTyphography = FontFamily(
        Font(R.font.poppins_reg),
        Font(R.font.poppins_med, FontWeight.Medium),
        Font(R.font.poppins_bold, FontWeight.Bold),
        Font(R.font.poppins_black, FontWeight.Black),)
}

// Set of Material typography styles to start with
private val defaultTypography = Typography()
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppFont.PoppinsTyphography),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppFont.PoppinsTyphography),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppFont.PoppinsTyphography),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppFont.PoppinsTyphography),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppFont.PoppinsTyphography),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppFont.PoppinsTyphography),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = AppFont.PoppinsTyphography),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = AppFont.PoppinsTyphography),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = AppFont.PoppinsTyphography),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = AppFont.PoppinsTyphography),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = AppFont.PoppinsTyphography),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = AppFont.PoppinsTyphography),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = AppFont.PoppinsTyphography),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = AppFont.PoppinsTyphography),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = AppFont.PoppinsTyphography)
)