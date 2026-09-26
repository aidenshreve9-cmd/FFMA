package com.focusfriend.app.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.ui.background.SceneView
import com.focusfriend.app.ui.components.PrimaryButton
import com.focusfriend.app.ui.components.glass
import com.focusfriend.app.ui.home.DialStack
import com.focusfriend.app.ui.home.dialSize
import com.focusfriend.app.ui.orb.Orb
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.ui.theme.Type

private val Glow = Shadow(Color(0x8CE0C3FC), blurRadius = 28f)

@Composable
fun SessionScreen(vm: FocusViewModel, time: State<Float>) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val act by animateFloatAsState(if (pressed) 1f else 0f, tween(600), label = "act")
    val m = vm.minutesLeft
    val word = if (m == 1) "minute" else "minutes"

    Box(Modifier.fillMaxSize()) {
        SceneView(vm.sessionScene, time, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    0f to Color(0x1A000000), .8f to Color(0xA8000000),
                    center = center.copy(y = size.height * .46f), radius = size.maxDimension * .62f,
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (vm.silenced) "NOTIFICATIONS SILENCED · EMERGENCY CALLS UNAFFECTED" else "DO NOT DISTURB COULDN'T TURN ON · NOTIFICATIONS NOT SILENCED",
                style = TextStyle(fontFamily = Fonts.Mono, fontSize = 10.sp, letterSpacing = 0.07.em, textAlign = TextAlign.Center),
                color = if (vm.silenced) Palette.Halo else Palette.Warn,
                modifier = Modifier.padding(top = 4.dp).glass(RoundedCornerShape(999.dp), prism = false).padding(horizontal = 16.dp, vertical = 9.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val session = vm.session
                DialStack(
                    fraction = vm.remainingMs / 3_600_000f,
                    activeMinutes = session?.minutes ?: 0,
                    showHandle = false,
                    dragging = false,
                    pressed = pressed,
                    act = { act },
                    time = time,
                    modifier = Modifier
                        .size(dialSize())
                        .clearAndSetSemantics {
                            contentDescription = "$m $word left. Tap to end early."
                            onClick(label = "End early") { vm.tapTimer(); true }
                        }
                        .clickable(source, indication = null) { vm.tapTimer() },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$m", style = TextStyle(fontFamily = Fonts.Mono, fontSize = 54.sp, color = Color.White, shadow = Glow))
                            Text("min", style = TextStyle(fontFamily = Fonts.Mono, fontSize = 18.sp, color = Palette.Halo), modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                        }
                        Text("TAP TO END EARLY", style = TextStyle(fontFamily = Fonts.Sans, fontSize = 11.sp, letterSpacing = 0.16.em, color = Palette.Text.copy(alpha = .75f)))
                    }
                }
            }
            val sound = vm.sessionSound
            Text(
                if (sound != null) "${sound.name} · ${vm.sessionScene.name}" else vm.sessionScene.name,
                style = TextStyle(fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 0.08.em, color = Palette.Halo.copy(alpha = .62f)),
                modifier = Modifier.heightIn(min = 18.dp),
            )
        }
    }
}

@Composable
fun DoneScreen(vm: FocusViewModel, time: State<Float>) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
    ) {
        Orb(time, { 0f }, Modifier.size(150.dp).padding(bottom = 0.dp))
        Text(
            if (vm.finishedNaturally) "DONE" else "SESSION ENDED",
            style = Type.Kicker.copy(fontSize = 11.sp, letterSpacing = 0.24.em, color = Palette.Muted),
            modifier = Modifier.semantics { heading() },
        )
        Column(
            Modifier.widthIn(max = 360.dp).glass().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("“${vm.quote.text}”", style = Type.Quote, textAlign = TextAlign.Center)
            Text("— ${vm.quote.author}", style = Type.Note.copy(fontSize = 13.sp, letterSpacing = 0.06.em), modifier = Modifier.padding(top = 14.dp))
        }
        PrimaryButton("Done", vm::done)
    }
}
