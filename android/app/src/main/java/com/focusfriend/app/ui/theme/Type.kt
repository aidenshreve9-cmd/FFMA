package com.focusfriend.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The web version used Playfair Display, Plus Jakarta Sans and JetBrains Mono; these are Android's built-in equivalents. */
object Fonts {
    val Serif = FontFamily.Serif
    val Sans = FontFamily.SansSerif
    val Mono = FontFamily.Monospace
}

object Type {
    val Body = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.Text)
    val Note = TextStyle(fontFamily = Fonts.Sans, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.Muted)
    val Kicker = TextStyle(fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 0.28.em)
    val Mono = TextStyle(fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 0.08.em, color = Palette.Muted)
    val SectionTitle = TextStyle(fontFamily = Fonts.Sans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.em, color = Palette.Muted)
    val SheetTitle = TextStyle(fontFamily = Fonts.Serif, fontSize = 24.sp, lineHeight = 30.sp, color = Palette.Text)
    val Quote = TextStyle(fontFamily = Fonts.Serif, fontStyle = FontStyle.Italic, fontSize = 25.sp, lineHeight = 33.sp, color = Palette.Text)
    val Button = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
}
