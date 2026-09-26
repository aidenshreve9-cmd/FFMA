package com.focusfriend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun FocusFriendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Rose,
            secondary = Palette.Magenta,
            background = Palette.Pitch,
            surface = Palette.Obsidian,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        ),
        content = content,
    )
}
