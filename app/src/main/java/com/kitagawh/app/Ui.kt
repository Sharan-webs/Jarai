package com.kitagawh.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object C {
    val bg = Color(0xFF000000)
    val card = Color(0xFF0C0D14)
    val card2 = Color(0xFF151728)
    val line = Color(0xFF22243A)
    val text = Color(0xFFEEF0FF)
    val dim = Color(0xFF8D92B5)
    val a1 = Color(0xFF7C8CFF)
    val a2 = Color(0xFF4FD8FF)
    val ok = Color(0xFF38D39F)
    val bad = Color(0xFFFF5C7A)
}

@Composable
fun T(
    text: String, size: Int = 16, weight: FontWeight = FontWeight.Normal, color: Color = C.text,
    spacing: Float = 0f, align: TextAlign? = null, modifier: Modifier = Modifier
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight,
        letterSpacing = spacing.sp, textAlign = align, lineHeight = (size * 1.45f).sp
    )
}

fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "bounce")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Reveal(delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); show = true }
    AnimatedVisibility(show, modifier, enter = fadeIn(tween(650)) + slideInVertically(tween(650)) { it / 6 }) { content() }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "btn")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val brush = if (enabled)
        Brush.horizontalGradient(listOf(C.a1, C.a2, C.a1), startX = -300f * shift, endX = 700f + 300f * shift)
    else Brush.horizontalGradient(listOf(C.card2, C.card2))
    Box(
        modifier.fillMaxWidth().height(54.dp).bounceClick(enabled, onClick).clip(RoundedCornerShape(27.dp)).background(brush),
        contentAlignment = Alignment.Center
    ) { T(text, 16, FontWeight.SemiBold, if (enabled) Color.Black else C.dim, 0.6f) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.a1, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).bounceClick(onClick = onClick).clip(RoundedCornerShape(25.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(25.dp)),
        contentAlignment = Alignment.Center
    ) { T(text, 15, FontWeight.Medium, color, 0.4f) }
}

@Composable
fun BackArrow(onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).bounceClick(onClick = onClick).clip(CircleShape).background(C.card2),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width; val h = size.height; val sw = 2.5.dp.toPx()
            drawLine(C.text, Offset(w * 0.95f, h / 2), Offset(w * 0.08f, h / 2), sw, StrokeCap.Round)
            drawLine(C.text, Offset(w * 0.08f, h / 2), Offset(w * 0.5f, h * 0.08f), sw, StrokeCap.Round)
            drawLine(C.text, Offset(w * 0.08f, h / 2), Offset(w * 0.5f, h * 0.92f), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun GearButton(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "gear")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rot")
    Box(
        Modifier.size(46.dp).bounceClick(onClick = onClick).clip(CircleShape).background(C.card2),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val c = center; val r = size.minDimension / 2f
            rotate(rot, c) {
                drawCircle(C.text, r * 0.55f, c, style = Stroke(2.2.dp.toPx()))
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(
                        C.text,
                        Offset(c.x + (r * 0.62f) * cos(a).toFloat(), c.y + (r * 0.62f) * sin(a).toFloat()),
                        Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                        2.6.dp.toPx(), StrokeCap.Round
                    )
                }
            }
            drawCircle(C.a2, r * 0.18f, c)
        }
    }
}

@Composable
fun KCard(
    modifier: Modifier = Modifier, borderColor: Color = C.line, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = if (onClick != null) modifier.fillMaxWidth().bounceClick(onClick = onClick) else modifier.fillMaxWidth()
    Column(
        base.clip(RoundedCornerShape(22.dp)).background(C.card).border(1.dp, borderColor, RoundedCornerShape(22.dp)).padding(18.dp),
        content = content
    )
}

/** The animated -_- face. */
@Composable
fun Logo(s: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "logo")
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")
    val breathe by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier.size(s, s * 0.6f)) {
        val w = size.width; val h = size.height
        val sw = w * 0.075f
        drawRect(
            Brush.radialGradient(listOf(C.a1.copy(alpha = 0.22f * breathe), Color.Transparent), center = center, radius = w * 0.75f)
        )
        val dx = look * w * 0.03f
        drawLine(Color.White, Offset(w * 0.06f + dx, h * 0.36f), Offset(w * 0.30f + dx, h * 0.36f), sw, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.70f + dx, h * 0.36f), Offset(w * 0.94f + dx, h * 0.36f), sw, StrokeCap.Round)
        val mw = w * (0.10f + 0.03f * breathe)
        drawLine(Color.White, Offset(w / 2 - mw + dx * 0.4f, h * 0.80f), Offset(w / 2 + mw + dx * 0.4f, h * 0.80f), sw, StrokeCap.Round)
    }
}

@Composable
fun Aura(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aura")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "p")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pu")
    val stars = remember { List(46) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.5f + Random.nextFloat() * 1.6f, Random.nextFloat()) } }
    Canvas(modifier) {
        drawRect(C.bg)
        drawRect(Brush.radialGradient(listOf(C.a1.copy(alpha = 0.20f * pulse), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.1f), size.width))
        drawRect(Brush.radialGradient(listOf(C.a2.copy(alpha = 0.12f * pulse), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.75f), size.width * 0.9f))
        stars.forEach { s ->
            val cycles = if (s[3] > 0.6f) 2 else 1
            val y = (((s[1] - p * cycles) % 1f) + 1f) % 1f
            drawCircle(Color.White.copy(alpha = 0.12f + 0.3f * s[3]), s[2] * 1.3f, Offset(s[0] * size.width, y * size.height))
        }
    }
}

private fun fib(n: Int): FloatArray {
    val out = FloatArray(n * 3)
    val g = 2.399963f
    for (i in 0 until n) {
        val y = 1f - 2f * (i + 0.5f) / n
        val rr = sqrt(1f - y * y)
        val th = g * i
        out[i * 3] = cos(th) * rr; out[i * 3 + 1] = y; out[i * 3 + 2] = sin(th) * rr
    }
    return out
}

/** Always rotating point sphere with aura and orbit rings. */
@Composable
fun Sphere(modifier: Modifier = Modifier, scale: Float = 0.36f) {
    val t = rememberInfiniteTransition(label = "sphere")
    val rot by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "r")
    val pulse by t.animateFloat(0.82f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val ringRot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rr")
    val pts = remember { fib(280) }
    Canvas(modifier) {
        val r = size.minDimension * scale
        val c = center
        drawCircle(
            Brush.radialGradient(listOf(C.a1.copy(alpha = 0.38f * pulse), C.a2.copy(alpha = 0.10f), Color.Transparent), c, r * 2.2f),
            r * 2.2f, c
        )
        rotate(ringRot, c) {
            drawOval(C.a2.copy(alpha = 0.35f), Offset(c.x - r * 1.35f, c.y - r * 0.36f), androidx.compose.ui.geometry.Size(r * 2.7f, r * 0.72f), style = Stroke(1.4.dp.toPx()))
        }
        rotate(-ringRot * 0.7f + 40f, c) {
            drawOval(C.a1.copy(alpha = 0.30f), Offset(c.x - r * 1.2f, c.y - r * 0.28f), androidx.compose.ui.geometry.Size(r * 2.4f, r * 0.56f), style = Stroke(1.2.dp.toPx()))
        }
        val cr = cos(rot); val sr = sin(rot); val tilt = 0.38f; val ct = cos(tilt); val st = sin(tilt)
        for (i in 0 until pts.size / 3) {
            val x = pts[i * 3]; val y = pts[i * 3 + 1]; val z = pts[i * 3 + 2]
            val x1 = x * cr + z * sr; val z1 = -x * sr + z * cr
            val y2 = y * ct - z1 * st; val z2 = y * st + z1 * ct
            val depth = (z2 + 1f) / 2f
            drawCircle(
                lerp(C.a1, C.a2, depth).copy(alpha = 0.14f + 0.86f * depth),
                (0.8f + 2.4f * depth) * density * 0.9f,
                Offset(c.x + x1 * r, c.y + y2 * r)
            )
        }
    }
}

@Composable
fun KField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, hint: String = ""
) {
    var focus by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focus) C.a1 else C.line, label = "fb")
    Column(modifier) {
        T(label, 13, color = C.dim, spacing = 0.4f)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                .border(1.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            if (value.isEmpty() && hint.isNotEmpty()) T(hint, 16, color = C.dim.copy(alpha = 0.6f))
            BasicTextField(
                value, onChange, enabled = enabled, singleLine = true,
                textStyle = TextStyle(color = if (enabled) C.text else C.dim, fontSize = 16.sp),
                cursorBrush = SolidColor(C.a2),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus = it.isFocused }
            )
        }
    }
}

@Composable
fun PulseDot(color: Color) {
    val t = rememberInfiniteTransition(label = "dot")
    val s by t.animateFloat(0.7f, 1.25f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(9.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(color))
}

/** Slides and fades a page in when it is first shown. */
@Composable
fun PageIn(content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    AnimatedVisibility(
        show,
        enter = fadeIn(tween(380)) + androidx.compose.animation.slideInHorizontally(tween(380)) { it / 5 }
    ) { content() }
}
