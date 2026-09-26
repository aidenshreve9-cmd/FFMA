package com.focusfriend.app.ui.theme

import androidx.compose.ui.graphics.Color

/** The Quantum Nebula Void palette ("Warm Obsidian Event Horizon"). */
object Palette {
    val Pitch = Color(0xFF000000)
    val Obsidian = Color(0xFF08040C)
    val Indigo = Color(0xFF1E005B)
    val Indigo2 = Color(0xFF2A085C)
    val Copper = Color(0xFFFF5400)
    val Amber = Color(0xFFE65100)
    val Rose = Color(0xFFF72585)
    val Magenta = Color(0xFFD900FF)
    val Violet = Color(0xFF6A0DAD)
    val Violet2 = Color(0xFF8A2BE2)
    val Core = Color(0xFFFFFFFF)
    val Halo = Color(0xFFE0C3FC)
    val PrismA = Color(0xFFFF007F)
    val PrismB = Color(0xFF7B2CBF)

    val Text = Color(0xFFF4ECFF)
    val Muted = Color(0xFFB7A6CC)
    val Faint = Color(0xFF7E6C96)
    val Warn = Color(0xFFFFB38A)
    val Glass = Color(0x730A0514)
    val GlassLine = Color(0x29E0C3FC)
    val Divider = Color(0x14E0C3FC)

    // Welcome screen (the wormhole is teal and mint).
    val Mint = Color(0xFFBDF7D2)
    val MintText = Color(0xFFD4ECDD)
    val MintNote = Color(0xFF9DB8A8)
    val IntroText = Color(0xFFF1FFF6)
}

fun hex(value: String): Color = Color(("FF" + value.removePrefix("#")).toLong(16))
