package com.chazc.steptracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val SamuraiFont = FontFamily(androidx.compose.ui.text.font.Font(R.font.rampart_one))

fun getGoogleFontFamily(name: String): FontFamily {
    return FontFamily(
        Font(googleFont = GoogleFont(name), fontProvider = provider)
    )
}

private fun createTypography(fontFamily: FontFamily, baseLetterSpacing: Float = 0f) = Typography(
    displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 52.sp, letterSpacing = (1f + baseLetterSpacing).sp),
    displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 44.sp, letterSpacing = baseLetterSpacing.sp),
    displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, letterSpacing = baseLetterSpacing.sp),
    headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, letterSpacing = baseLetterSpacing.sp),
    headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, letterSpacing = (2f + baseLetterSpacing).sp),
    headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, letterSpacing = baseLetterSpacing.sp),
    titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, letterSpacing = baseLetterSpacing.sp),
    titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = baseLetterSpacing.sp),
    titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = baseLetterSpacing.sp),
    bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = baseLetterSpacing.sp),
    bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = baseLetterSpacing.sp),
    bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = baseLetterSpacing.sp),
    labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = baseLetterSpacing.sp),
    labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = baseLetterSpacing.sp),
    labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = (1.5f + baseLetterSpacing).sp)
)

val ZoroTypography = createTypography(SamuraiFont)
val LuffyTypography = createTypography(getGoogleFontFamily("Luckiest Guy"))
val SanjiTypography = createTypography(getGoogleFontFamily("Cinzel"))
val NamiTypography = createTypography(getGoogleFontFamily("Montserrat"))
val LawTypography = createTypography(getGoogleFontFamily("Space Mono"), baseLetterSpacing = -0.5f)
val ChopperTypography = createTypography(getGoogleFontFamily("Fredoka"))
val ShanksTypography = createTypography(getGoogleFontFamily("Libre Baskerville"))
