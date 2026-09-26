package com.focusfriend.app.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusfriend.app.ui.components.VoidButton
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.util.rememberFrameTime
import com.focusfriend.app.util.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/** When the Start button has finished rising in (ms). */
private const val INTRO_END = 8700f

private fun ease(k: Float) = (0.5 - 0.5 * cos(PI * k.coerceIn(0f, 1f))).toFloat()

/** A line that fades and drifts in, holds, then fades and drifts out. */
private class Beat(val alpha: Float, val dy: Float, val blur: Float)

private fun line(clock: Float, delay: Float, duration: Float): Beat {
    val p = (clock - delay) / duration
    return when {
        p <= 0f || p >= 1f -> Beat(0f, 0f, 0f)
        p < 0.3f -> ease(p / 0.3f).let { Beat(it, 12f * (1 - it), 6f * (1 - it)) }
        p < 0.7f -> Beat(1f, 0f, 0f)
        else -> ease((p - 0.7f) / 0.3f).let { Beat(1 - it, -10f * it, 6f * it) }
    }
}

private fun rise(clock: Float, delay: Float, duration: Float): Beat {
    val e = ease((clock - delay) / duration)
    return Beat(e, 14f * (1 - e), 6f * (1 - e))
}

private fun Modifier.beat(get: () -> Beat): Modifier = graphicsLayer {
    val b = get()
    alpha = b.alpha
    translationY = b.dy * density
    renderEffect = if (b.blur > 0.1f) BlurEffect(b.blur * density, b.blur * density) else null
}

private val DarkShadow = Shadow(Color(0xF2000000), blurRadius = 32f)

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val still = rememberReducedMotion()
    val time = rememberFrameTime(!still)
    val clock = remember { Animatable(if (still) INTRO_END else 0f) }
    val introDone by remember { derivedStateOf { clock.value >= INTRO_END } }
    val scope = rememberCoroutineScope()
    val skip: () -> Unit = { scope.launch { clock.snapTo(INTRO_END) } }
    LaunchedEffect(Unit) {
        if (!still) clock.animateTo(INTRO_END, tween(INTRO_END.toInt(), easing = LinearEasing))
    }
    val topGap = (LocalConfiguration.current.screenHeightDp * 0.18f).dp

    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !introDone, onClickLabel = "Skip the intro") { skip() },
    ) {
        Wormhole(time, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = topGap),
                contentAlignment = Alignment.Center,
            ) {
                // A soft dark haze behind the text, so it reads over the bright rings.
                Canvas(Modifier.size(420.dp, 340.dp)) {
                    drawOval(
                        Brush.radialGradient(
                            0f to Color(0xA8010810), 0.55f to Color(0x61010810), 1f to Color(0x00010810),
                            center = center, radius = size.minDimension / 2,
                        ),
                    )
                }
                Text(
                    "Take a slow breath.",
                    style = IntroStyle,
                    modifier = Modifier.clearAndSetSemantics { }.beat { line(clock.value, 500f, 3000f) },
                )
                Text(
                    "Let the noise fall away.",
                    style = IntroStyle,
                    modifier = Modifier.clearAndSetSemantics { }.beat { line(clock.value, 3400f, 3200f) },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "WELCOME TO",
                        style = TextStyle(fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 0.28.em, color = Palette.Mint, shadow = DarkShadow),
                        modifier = Modifier.beat { rise(clock.value, 6500f, 1400f) },
                    )
                    Text(
                        "Focus Friend",
                        style = TextStyle(fontFamily = Fonts.Serif, fontSize = 46.sp, lineHeight = 48.sp, color = Color.White, shadow = DarkShadow),
                        modifier = Modifier.semantics { heading() }.beat { rise(clock.value, 6760f, 1400f) },
                    )
                    Text(
                        "Quiet your phone, pick a time, and give one thing your full attention.",
                        style = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.MintText, textAlign = TextAlign.Center, shadow = DarkShadow),
                        modifier = Modifier.widthIn(max = 280.dp).padding(top = 4.dp).beat { rise(clock.value, 7020f, 1400f) },
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().beat { rise(clock.value, 7400f, 1300f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // During the intro, a tap on Start only skips ahead.
                VoidButton("Start", onClick = { if (introDone) onStart() else skip() }, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
                Text(
                    "Free · No account · Stays on this device",
                    style = TextStyle(fontFamily = Fonts.Mono, fontSize = 10.5.sp, letterSpacing = 0.08.em, color = Palette.MintNote, shadow = DarkShadow),
                )
            }
        }
    }
}

private val IntroStyle = TextStyle(
    fontFamily = Fonts.Serif,
    fontStyle = FontStyle.Italic,
    fontSize = 27.sp,
    lineHeight = 34.sp,
    color = Palette.IntroText,
    textAlign = TextAlign.Center,
    shadow = DarkShadow,
)
