package com.focusfriend.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    ) {
        // Text is light on the dark app unless a style says otherwise.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
