package com.focusfriend.app.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusfriend.app.model.Catalog
import com.focusfriend.app.session.FocusViewModel
import com.focusfriend.app.session.Sheet
import com.focusfriend.app.ui.components.PrimaryButton
import com.focusfriend.app.ui.components.QuietButton
import com.focusfriend.app.ui.components.glass
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.ui.theme.Type

/** The dimmed backdrop and whichever sheet is open. */
@Composable
fun BoxScope.SheetLayer(vm: FocusViewModel) {
    val sheet = vm.sheet
    val last = remember { arrayOfNulls<Sheet>(1) }
    if (sheet != null) last[0] = sheet
    val shown = last[0]

    AnimatedVisibility(visible = sheet != null, enter = fadeIn(tween(320)), exit = fadeOut(tween(340))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x8C000000))
                .clickable(remember { MutableInteractionSource() }, indication = null) { vm.back() },
        )
    }

    val centered = shown == Sheet.CONFIRM_END
    AnimatedVisibility(
        visible = sheet != null,
        enter = if (centered) fadeIn(tween(320)) + scaleIn(tween(560), initialScale = 0.94f)
        else fadeIn(tween(320)) + slideInVertically(tween(560)) { it / 3 },
        exit = if (centered) fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 0.97f)
        else fadeOut(tween(320)) + slideOutVertically(tween(320)) { it / 4 },
        modifier = Modifier.align(if (centered) Alignment.Center else Alignment.BottomCenter),
    ) {
        val shape = if (centered) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .then(if (centered) Modifier.padding(20.dp) else Modifier)
                .fillMaxWidth()
                .glass(shape)
                .background(Color(0x990A0514))
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .then(if (centered) Modifier else Modifier.navigationBarsPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (shown) {
                Sheet.PERMISSION -> PermissionSheet(vm)
                Sheet.BARRIER -> BarrierSheet(vm)
                Sheet.CONFIRM_END -> ConfirmEndSheet(vm)
                Sheet.DONATE -> DonateSheet(vm)
                null -> Unit
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = Type.SheetTitle, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Para(text: String, color: Color = Palette.Muted) {
    Text(text, style = Type.Body, color = color)
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), style = Type.SectionTitle.copy(color = Palette.Halo, letterSpacing = 2.sp), modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Bullets(vararg items: Pair<String?, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { (bold, rest) ->
            Row {
                Text("•  ", style = Type.Body, color = Palette.Muted)
                Text(
                    buildAnnotatedString {
                        if (bold != null) withStyle(SpanStyle(color = Palette.Text, fontWeight = FontWeight.SemiBold)) { append(bold) }
                        append(rest)
                    },
                    style = Type.Body, color = Palette.Muted,
                )
            }
        }
    }
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun Callout(text: String) {
    Text(
        text,
        style = Type.Body.copy(fontSize = 13.5.sp),
        color = Palette.Warn,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Copper.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Copper.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PermissionSheet(vm: FocusViewModel) {
    val onOff: (Boolean) -> String = { if (it) "ON" else "OFF" }
    Title("Focus Friend needs Do Not Disturb access")
    Para("Once you allow it, every Focus session sets these rules automatically, and puts your phone back the way it was when the session ends.")
    Heading("Blocked")
    Bullets(null to "All notifications: sounds, pop-ups and vibration.", null to "Calls from everyone else.")
    Heading("Always gets through")
    Bullets(
        "Emergency alerts." to "",
        "Repeat callers: " to "the first call is silenced. If the same person calls again within ${Catalog.REPEAT_CALL_WINDOW_MIN} minutes, that second call rings.",
    )
    Heading("Only when turned on in Settings")
    Bullets(
        "Trusted Contacts: " to "OFF — not available in the Android app yet.",
        "Alarm Safety: " to "${onOff(vm.settings.alarmSafety)} — alarms ring during Focus.",
    )
    if (!vm.dndGranted) Para("Android will open its Do Not Disturb access page. Turn on Focus Friend there, then come back.")
    Actions {
        PrimaryButton(if (vm.dndGranted) "Continue" else "Allow in Settings", vm::permissionAllow, Modifier.fillMaxWidth())
        QuietButton("Not now", vm::permissionDeny, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BarrierSheet(vm: FocusViewModel) {
    Title("Focus can't start without access.")
    Para("Focus Friend needs Do Not Disturb access to reduce distractions.")
    Actions {
        PrimaryButton("Review the rules again", vm::barrierReview, Modifier.fillMaxWidth())
        QuietButton("Go back", vm::barrierBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConfirmEndSheet(vm: FocusViewModel) {
    Title("End focus early?")
    Text("About ${vm.minutesLeft} min left.", style = Type.Mono.copy(fontSize = 15.sp, color = Palette.Halo))
    Actions {
        PrimaryButton("Keep focusing", vm::keepFocusing, Modifier.fillMaxWidth())
        QuietButton("End session", vm::endEarly, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DonateSheet(vm: FocusViewModel) {
    Title("Support Focus Friend")
    Para("Focus Friend is free, and it stays free. If it helps you, you can buy the maker a coffee.")
    Callout("The Buy Me a Coffee page isn't set up yet, so this button doesn't open anything.")
    Actions {
        PrimaryButton("Open Buy Me a Coffee", {}, Modifier.fillMaxWidth(), enabled = false)
        QuietButton("Close", vm::closeDonate, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(2.dp))
}
