package com.focusfriend.app.util

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext

/** Android's "Remove animations" setting: everything that would move is shown still instead. */
fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { reducedMotion(context) }
}

/**
 * Seconds since this first appeared, updated every frame. Read it only inside drawing code so a
 * new frame redraws without recomposing. With reduced motion it stays at a fixed moment.
 */
@Composable
fun rememberFrameTime(animate: Boolean = !rememberReducedMotion()): State<Float> {
    val time = remember { mutableFloatStateOf(if (animate) 0f else STILL_TIME) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { n -> time.floatValue = (n - start) / 1_000_000_000f }
        }
    }
    return time
}

const val STILL_TIME = 20f
