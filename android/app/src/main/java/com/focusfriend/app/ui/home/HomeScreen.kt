package com.focusfriend.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusfriend.app.model.Catalog
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.ui.components.IconCircle
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import kotlin.math.min
import kotlin.math.roundToInt

private val Glow = Shadow(Color(0x8CE0C3FC), blurRadius = 28f)

@Composable
fun dialSize() = min(LocalConfiguration.current.screenWidthDp * 0.82f, 320f).dp

@Composable
fun HomeScreen(vm: FocusViewModel, time: State<Float>) {
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val act by animateFloatAsState(if (pressed) 1f else 0f, tween(600), label = "act")
    val minutes by rememberUpdatedState(vm.minutes)
    val slop = with(LocalDensity.current) { 7.dp.toPx() }
    val m = vm.minutes
    val word = if (m == 1) "minute" else "minutes"
    val hintAlpha by animateFloatAsState(if (vm.hintVisible) 1f else 0f, tween(800), label = "hint")

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconCircle("Settings", vm::openSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().offset(y = (-24).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically),
        ) {
            DialStack(
                fraction = m / 60f,
                activeMinutes = m,
                showHandle = true,
                dragging = dragging,
                pressed = pressed,
                act = { act },
                time = time,
                modifier = Modifier
                    .size(dialSize())
                    .clearAndSetSemantics {
                        contentDescription = "Focus, $m $word. Drag around the ring, or swipe up or down, to change the time. Double-tap to start."
                        progressBarRangeInfo = ProgressBarRangeInfo(m.toFloat(), Catalog.MIN_MINUTES.toFloat()..Catalog.MAX_MINUTES.toFloat(), Catalog.MAX_MINUTES - Catalog.MIN_MINUTES - 1)
                        setProgress { v -> vm.setMinutes(v.roundToInt()); vm.hideHint(); true }
                        onClick(label = "Start focus") { vm.requestStart(); true }
                    }
                    .dialInput(
                        minutes = { minutes },
                        onSet = { vm.setMinutes(it); vm.hideHint() },
                        onTap = vm::requestStart,
                        onPress = { pressed = it },
                        onDrag = { dragging = it },
                        slopPx = slop,
                    ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Focus", style = TextStyle(fontFamily = Fonts.Serif, fontSize = 40.sp, color = Color.White, shadow = Glow))
                    Text("$m", style = TextStyle(fontFamily = Fonts.Mono, fontSize = 30.sp, color = Color.White, shadow = Glow), modifier = Modifier.padding(top = 6.dp))
                    Text("MIN", style = TextStyle(fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 0.2.em, color = Palette.Halo), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(
                "Drag around the ring to set the time",
                style = TextStyle(fontFamily = Fonts.Sans, fontSize = 13.5.sp, letterSpacing = 0.02.em, color = Palette.Muted),
                modifier = Modifier.alpha(hintAlpha),
            )
        }
    }
}
