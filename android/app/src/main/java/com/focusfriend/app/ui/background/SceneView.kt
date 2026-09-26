package com.focusfriend.app.ui.background

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.focusfriend.app.session.SceneChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A scene filling its space: one of the painted scenes, moving, or your own picture. */
@Composable
fun SceneView(choice: SceneChoice, time: State<Float>, modifier: Modifier = Modifier) {
    when (choice) {
        is SceneChoice.Painted -> Canvas(modifier) { Scenes.paint(this, choice.id, time.value) }
        is SceneChoice.Picture -> {
            val image by rememberImage(choice.file)
            Canvas(modifier) {
                val img = image
                if (img != null) drawCover(img) else drawRect(Color.Black)
            }
        }
    }
}

/** Loads a picture off the main thread; null until it's ready (or if it can't be read). */
@Composable
fun rememberImage(file: File, maxSide: Int = 1600): State<ImageBitmap?> {
    val image = remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) { image.value = load(file, maxSide) }
    return image
}

private suspend fun load(file: File, maxSide: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

/** Scales the picture to cover the whole area, cropping the edges that don't fit. */
fun DrawScope.drawCover(img: ImageBitmap) {
    val s = max(size.width / img.width, size.height / img.height)
    val w = img.width * s
    val h = img.height * s
    drawRect(Color.Black)
    drawImage(
        img,
        dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
    )
}
