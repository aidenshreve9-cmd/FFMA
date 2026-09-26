package com.focusfriend.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.ui.theme.Type
import com.focusfriend.app.util.rememberReducedMotion
import kotlinx.coroutines.delay

val GlassShape = RoundedCornerShape(20.dp)
private val PrismBorder = Brush.linearGradient(listOf(Palette.PrismA, Palette.PrismB))

/** Dark glass panel; with [prism] it gets the thin rose-to-violet glowing edge. */
fun Modifier.glass(shape: Shape = GlassShape, prism: Boolean = true): Modifier = this
    .clip(shape)
    .background(Palette.Glass, shape)
    .then(if (prism) Modifier.border(BorderStroke(1.dp, PrismBorder), shape) else Modifier.border(1.dp, Palette.GlassLine, shape))

private val PillShape = RoundedCornerShape(999.dp)

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return scale
}

/** The rose-to-magenta button used for main actions. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .scale(pressScale(source))
            .heightIn(min = 52.dp)
            .clip(PillShape)
            .background(Brush.horizontalGradient(listOf(Palette.Rose, Palette.Magenta)), PillShape)
            .then(if (enabled) Modifier else Modifier.background(Color(0x99000000), PillShape))
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f))
    }
}

/** A quiet glass button for secondary actions. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .scale(pressScale(source))
            .heightIn(min = 52.dp)
            .clip(PillShape)
            .background(Color(0x8C0A0514), PillShape)
            .border(1.dp, Palette.GlassLine, PillShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button, color = Palette.Text)
    }
}

/** Start: dark purple and black, shaded unevenly, with the shading slowly drifting and a breathing glow. */
@Composable
fun VoidButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val still = rememberReducedMotion()
    val source = remember { MutableInteractionSource() }
    val drift = rememberInfiniteTransition(label = "void")
    val shift by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(12000), RepeatMode.Reverse), label = "shift")
    val breath by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "glow")
    val s = if (still) 0.5f else shift
    val g = if (still) 0.3f else breath
    Box(
        modifier
            .scale(pressScale(source))
            .heightIn(min = 52.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                // Outer glow, breathing.
                drawRoundRect(
                    brush = Brush.radialGradient(listOf(Color(0xFF7B2CBF).copy(alpha = 0.18f + 0.25f * g), Color.Transparent), center = center, radius = w * 0.6f),
                    topLeft = Offset(-w * 0.1f, -h * 0.6f),
                    size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 2.2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h, h),
                )
            }
            .clip(PillShape)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(Brush.linearGradient(
                    listOf(Color(0xFF07010D), Color(0xFF2A0A47), Color(0xFF040007), Color(0xFF1C0533), Color(0xFF0B0214)),
                    start = Offset(-w * s, 0f), end = Offset(w * (2f - s), h),
                ))
                drawRect(Brush.radialGradient(listOf(Color(0xE66A0DAD), Color.Transparent), center = Offset(w * (0.15f + 0.5f * s), h * (0.3f + 0.2f * s)), radius = w * 0.45f))
                drawRect(Brush.radialGradient(listOf(Color(0xF23A085C), Color.Transparent), center = Offset(w * (0.85f - 0.55f * s), h * (0.75f - 0.4f * s)), radius = w * 0.4f))
                drawRect(Brush.radialGradient(listOf(Color(0xE6000000), Color.Transparent), center = Offset(w * (0.55f - 0.3f * s), h * (0.4f + 0.3f * s)), radius = w * 0.25f))
                drawRoundRect(
                    color = Color(0xFF9B5DE5).copy(alpha = 0.30f + 0.25f * g),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Button.copy(letterSpacing = 0.06.sp * 15.5f), color = Palette.Text)
    }
}

/** On/off switch with a glowing ember knob. */
@Composable
fun EmberSwitch(checked: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val k by animateFloatAsState(if (checked) 1f else 0f, tween(500), label = "switch")
    Box(
        modifier
            .size(58.dp, 32.dp)
            .clip(PillShape)
            .background(Brush.horizontalGradient(listOf(Palette.Indigo2, Palette.Pitch)))
            .border(1.dp, if (checked) Palette.Rose.copy(alpha = 0.7f) else Palette.GlassLine, PillShape)
            .semantics { contentDescription = label }
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .drawBehind {
                val r = 12.dp.toPx()
                val x = 4.dp.toPx() + r + 26.dp.toPx() * k
                val c = Offset(x, size.height / 2)
                if (k > 0f) drawCircle(Brush.radialGradient(listOf(Palette.Copper.copy(alpha = 0.55f * k), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
                drawCircle(
                    Brush.radialGradient(
                        if (checked) listOf(Color.White, Color(0xFFFFE3CC), Color(0xFFFF9A5A), Palette.Copper) else listOf(Color(0xFF8B7AA3), Color(0xFF4A3B5E)),
                        center = c - Offset(r * 0.2f, r * 0.25f), radius = r * 1.2f,
                    ),
                    r, c,
                )
            },
    )
}

/** Dashed "＋ Add …" button. */
@Composable
fun AddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 12.dp)
            .heightIn(min = 44.dp)
            .clip(PillShape)
            .background(Color(0x590A0514))
            .drawBehind {
                drawRoundRect(
                    Palette.Halo.copy(alpha = 0.3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
                    style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋ $text", style = Type.Body.copy(fontSize = 15.sp), color = Palette.Text)
    }
}

/** A dark input field with a rose focus edge. */
@Composable
fun FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val style = TextStyle(color = Palette.Text, fontSize = if (mono) 14.sp else 15.sp, fontFamily = if (mono) Fonts.Mono else Fonts.Sans)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Palette.Rose),
        interactionSource = source,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x73000000))
            .border(1.dp, Palette.GlassLine, RoundedCornerShape(12.dp))
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = Palette.Faint))
                    inner()
                }
            }
        },
    )
}

/** A short message at the bottom that fades away after a few seconds. */
@Composable
fun ToastHost(text: String?, key: Any?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    // Keeps the last message on screen while it fades out.
    val last = remember { arrayOf("") }
    if (text != null) last[0] = text
    LaunchedEffect(key) {
        if (text != null) {
            delay(2800)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 3 },
        exit = fadeOut(tween(320)) + slideOutVertically(tween(320)) { it / 4 },
        modifier = modifier,
    ) {
        Text(
            last[0],
            style = Type.Body.copy(fontSize = 14.sp),
            modifier = Modifier
                .glass(PillShape, prism = false)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** A small ON/OFF tag. */
@Composable
fun OnOffTag(on: Boolean) {
    Text(
        if (on) "ON" else "OFF",
        style = Type.Mono.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
        color = if (on) Palette.Copper else Palette.Faint,
    )
}

/** Round glass icon button (Settings, Back). */
@Composable
fun IconCircle(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassLine, CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
