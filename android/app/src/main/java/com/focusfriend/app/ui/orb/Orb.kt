package com.focusfriend.app.ui.orb

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import com.focusfriend.app.ui.background.glow
import com.focusfriend.app.ui.theme.hex
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Quantum Focus orb: a glowing core, swirling filaments rotating in 3D, and a rippling glass
 * shell that bends light. [act] (0–1) is how strongly it's being touched: it grows and brightens.
 */
@Composable
fun Orb(time: State<Float>, act: () -> Float, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { createShader() }
        if (shader != null) {
            ShaderOrb(shader, time, act, modifier)
            return
        }
    }
    GlowOrb(time, act, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createShader(): RuntimeShader? = try {
    RuntimeShader(ORB_AGSL)
} catch (e: Exception) {
    null // a GPU driver that can't build it gets the simpler orb
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderOrb(shader: RuntimeShader, time: State<Float>, act: () -> Float, modifier: Modifier) {
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", time.value)
        shader.setFloatUniform("uAct", act())
        drawRect(brush)
    }
}

private val PLASMA = listOf("#8A2BE2", "#F72585", "#1E3AA8", "#D900FF", "#6A0DAD", "#F72585")
    .mapIndexed { i, c -> Triple(hex(c), floatArrayOf(.05f + i * .013f, .07f + i * .009f), i * 1.3f) }

/** For phones before Android 13: layered plasma glows inside a glass circle. */
@Composable
private fun GlowOrb(time: State<Float>, act: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val t = time.value
        val a = act()
        val w = min(size.width, size.height)
        val c = Offset(size.width / 2, size.height / 2)
        val r = w * .35f * (1 + .022f * sin(t * .698f)) * (1 + .055f * a)
        glow(c.x, c.y, r * 1.45f, hex("#8A2BE2"), .35f + .2f * a, BlendMode.Plus)
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(circle) {
            drawCircle(Brush.radialGradient(listOf(hex("#0b1450"), hex("#02030f")), c, r), r, c)
            PLASMA.forEach { (col, speed, p) ->
                glow(c.x + sin(t * speed[0] + p) * r * .45f, c.y + cos(t * speed[1] + p) * r * .45f, r * .8f, col, .5f, BlendMode.Plus)
            }
            glow(c.x, c.y, r * (.45f + .15f * a), Color.White, .55f + .3f * a, BlendMode.Plus)
            drawCircle(Brush.radialGradient(.7f to Color.Transparent, 1f to Color(0x59D900FF), center = c, radius = r), r, c)
        }
    }
}

private const val ORB_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform float uAct;

float hash(float3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise(float3 x) {
    float3 i = floor(x); float3 f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + float3(1, 0, 0)), f.x), mix(hash(i + float3(0, 1, 0)), hash(i + float3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + float3(0, 0, 1)), hash(i + float3(1, 0, 1)), f.x), mix(hash(i + float3(0, 1, 1)), hash(i + float3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm(float3 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 3; i++) { s += a * noise(p); p = p * 2.03 + float3(1.7, 9.2, 3.1); a *= 0.5; } return s / 0.875; }
float3x3 rY(float a) { float c = cos(a); float s = sin(a); return float3x3(c, 0, s, 0, 1, 0, -s, 0, c); }
float3x3 rX(float a) { float c = cos(a); float s = sin(a); return float3x3(1, 0, 0, 0, c, -s, 0, s, c); }

const float3 DEEP = float3(0.008, 0.012, 0.08);
const float3 COBALT = float3(0.035, 0.07, 0.34);
const float3 VIOLET = float3(0.54, 0.17, 0.89);
const float3 MAGENTA = float3(0.97, 0.16, 0.55);
const float3 LAV = float3(0.88, 0.76, 0.99);
const float3 HOT = float3(1.0, 0.97, 1.0);

half4 main(float2 frag) {
    float2 uv = float2(frag.x * 2.0 - uRes.x, uRes.y - frag.y * 2.0) / uRes.y;
    float t = uTime;
    float R = 0.70 * (1.0 + 0.022 * sin(t * 0.698)) * (1.0 + 0.055 * uAct);
    float r = length(uv);
    float d = max(r - R, 0.0);
    float3 haloC = mix(VIOLET, MAGENTA, clamp(0.3 + 0.25 * sin(t * 0.21) + 0.35 * uAct, 0.0, 1.0));
    float halo = (exp(-d * 10.0) * 0.55 + exp(-d * 4.0) * 0.22) * (0.85 + 0.6 * uAct) * smoothstep(1.0, 0.72, r);
    float3 col = haloC * halo;
    float alpha = halo;
    if (r < R + 0.01) {
        float rc = min(r, R - 0.0001);
        float z = sqrt(R * R - rc * rc);
        float3 n = normalize(float3(uv, z));
        float3 sp = n * 2.6 + float3(0.0, 0.0, t * 0.06);
        float3 np = normalize(n + 0.16 * float3(fbm(sp) - 0.5, fbm(sp + float3(5.2, 1.3, 2.7)) - 0.5, 0.0));
        float3 ro = float3(uv, z) + (np - n) * 0.22 * R;
        float dt = 2.0 * z / 16.0;
        float3x3 m1 = rY(t * 0.045) * rX(0.4 + 0.25 * sin(t * 0.027));
        float3x3 m2 = rY(-t * 0.031 + 1.3) * rX(-0.3);
        float3 acc = float3(0.0);
        float T = 1.0;
        for (int i = 0; i < 16; i++) {
            float3 q = (ro - float3(0.0, 0.0, (float(i) + 0.5) * dt)) / R;
            float rr = length(q);
            float n1 = fbm(m1 * q * 2.0 + float3(0.0, t * 0.02, 0.0));
            float n2 = noise(m2 * q * 3.4 + float3(t * 0.025));
            float fil = (pow(1.0 - abs(n1 * 2.0 - 1.0), 6.0) + 0.7 * pow(1.0 - abs(n2 * 2.0 - 1.0), 9.0)) * smoothstep(1.05, 0.25, rr);
            float core = exp(-rr * rr * (10.0 - 3.5 * uAct)) * (1.0 + 0.9 * uAct);
            float3 c = mix(COBALT, VIOLET, smoothstep(0.05, 0.45, fil));
            c = mix(c, MAGENTA, smoothstep(0.35, 0.95, fil) * (0.55 + 0.45 * sin(q.y * 3.0 + q.x * 2.0 + t * 0.15)));
            c += mix(LAV, HOT, smoothstep(0.3, 1.1, core)) * core * 1.6;
            float a = clamp((fil * 1.5 + core * 0.9) * dt * 3.0, 0.0, 1.0);
            acc += T * c * a * 1.5;
            T *= 1.0 - a * 0.6;
        }
        float3 inside = acc + T * mix(DEEP, COBALT, 0.35 + 0.3 * n.y);
        float fres = pow(1.0 - max(np.z, 0.0), 2.6);
        inside += mix(VIOLET, MAGENTA, 0.5 + 0.5 * sin(atan(uv.y, uv.x) * 2.0 + t * 0.12)) * fres * 0.85;
        float3 L = normalize(float3(-0.45, 0.55, 0.75));
        float3 H = normalize(L + float3(0.0, 0.0, 1.0));
        float nh = max(dot(np, H), 0.0);
        float spec = pow(nh, 140.0) * 1.3 + pow(nh, 20.0) * 0.08;
        float glint = pow(fbm(np * 9.0 + float3(t * 0.08, 0.0, -t * 0.05)), 11.0) * 3.0 * smoothstep(0.35, 0.95, dot(np, L));
        inside += HOT * (spec + glint);
        inside = 1.0 - exp(-inside * 1.25);
        float e = smoothstep(R + 0.004, R - 0.006, r);
        col = mix(col, inside, e);
        alpha = mix(alpha, 1.0, e);
    }
    return half4(half3(col), half(alpha));
}
"""
