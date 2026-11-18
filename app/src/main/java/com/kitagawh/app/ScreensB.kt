package com.kitagawh.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.system.exitProcess

// ------------------------------------------------------------------ HOME

@Composable
private fun Chevron(open: Boolean) {
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(350), label = "chev")
    Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = rot }) {
        val w = size.width; val h = size.height; val sw = 2.4.dp.toPx()
        drawLine(C.text, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.5f, h * 0.7f), sw, StrokeCap.Round)
        drawLine(C.text, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.9f, h * 0.35f), sw, StrokeCap.Round)
    }
}

@Composable
private fun ScrollHint() {
    val t = rememberInfiniteTransition(label = "hint")
    val y by t.animateFloat(0f, 10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "y")
    Column(Modifier.offset(y = y.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("Scroll down", 13, color = C.dim, spacing = 1f)
        Chevron(false)
    }
}

@Composable
private fun LazyItemScope.Hero() {
    Column(
        Modifier.fillMaxWidth().fillParentMaxHeight(0.94f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Sphere(Modifier.fillMaxWidth().height(380.dp))
        Row {
            "Kitagawh!".forEachIndexed { i, ch -> Reveal(500 + i * 90) { T(ch.toString(), 42, FontWeight.Light, spacing = 2f) } }
        }
        Spacer(Modifier.height(8.dp))
        Reveal(1500) { T("Your Master Bot. Say the word.", 15, color = C.dim, spacing = 0.6f) }
        Spacer(Modifier.height(36.dp))
        Reveal(2100) { ScrollHint() }
    }
}

@Composable
fun HomeScreen(ctx: Context, onSettings: () -> Unit, onJarvis: () -> Unit, onEtUi: () -> Unit, onEtDesign: () -> Unit) {
    var etOn by remember { mutableStateOf(Store.bool(ctx, "et_on")) }
    var jarvisOn by remember { mutableStateOf(Store.bool(ctx, "jarvis_on")) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(700); jarvisOn = Store.bool(ctx, "jarvis_on"); etOn = Store.bool(ctx, "et_on") } }

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 70.dp)) {
            item { Hero() }
            item {
                Reveal {
                    KCard(borderColor = C.a1.copy(alpha = 0.35f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(if (jarvisOn) C.ok else C.dim)
                            Spacer(Modifier.width(10.dp))
                            T(if (jarvisOn) "Jarvis is online" else "Jarvis is offline", 13, color = if (jarvisOn) C.ok else C.dim)
                        }
                        Spacer(Modifier.height(14.dp))
                        T("Start AI", 28, FontWeight.Light)
                        Spacer(Modifier.height(8.dp))
                        T("Power on Jarvis, then call it by voice and let it run your phone.", 14, color = C.dim)
                        Spacer(Modifier.height(20.dp))
                        PrimaryButton("Start AI", onClick = onJarvis)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
            item {
                Reveal {
                    KCard(onClick = { expanded = !expanded }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T("Easy Touch", 28, FontWeight.Light)
                                Spacer(Modifier.height(6.dp))
                                T(if (etOn) "On, floating on your screen" else "Off", 13, color = if (etOn) C.ok else C.dim)
                            }
                            Chevron(expanded)
                        }
                        AnimatedVisibility(expanded, enter = expandVertically(tween(450)) + fadeIn(tween(450)), exit = shrinkVertically(tween(350)) + fadeOut(tween(250))) {
                            Column {
                                Spacer(Modifier.height(18.dp))
                                T("A floating icon that opens your own ten slot menu over any app.", 14, color = C.dim)
                                Spacer(Modifier.height(18.dp))
                                GhostButton("Easy touch UI", onClick = onEtUi)
                                Spacer(Modifier.height(12.dp))
                                GhostButton("Easy touch design", color = C.a2, onClick = onEtDesign)
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton(if (etOn) "Power off Easy Touch" else "Power on Easy Touch") {
                                    etOn = !etOn
                                    Store.putBool(ctx, "et_on", etOn)
                                    CoreService.sync(ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp)) { GearButton(onSettings) }
    }
}

// ------------------------------------------------------------------ JARVIS

@Composable
private fun PowerSequence(turningOn: Boolean) {
    val steps = if (turningOn)
        listOf("Waking the core", "Linking accessibility", "Tuning the microphone", "Connecting the brain", "Jarvis is online")
    else listOf("Saving state", "Releasing the microphone", "Powering down", "Jarvis is off")
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { for (i in steps.indices) { idx = i; delay(if (turningOn) 950L else 700L) } }
    val progress by animateFloatAsState((idx + 1f) / steps.size, tween(600), label = "pp")
    val t = rememberInfiniteTransition(label = "pw")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ph")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "sp")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp)) {
            val c = center; val r = size.minDimension / 2f
            for (i in 0..2) {
                val k = (ph + i / 3f) % 1f
                drawCircle(C.a1.copy(alpha = 0.5f * (1 - k)), r * (0.35f + 0.65f * k), c, style = Stroke(2.dp.toPx()))
            }
            drawCircle(C.card2, r * 0.42f, c)
            drawArc(C.line, 0f, 360f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(4.dp.toPx()))
            drawArc(
                C.a2, -90f, 360f * progress, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
            rotate(spin, c) {
                drawArc(C.a1, 0f, 60f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawArc(C.a2, 180f, 30f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(C.a2.copy(alpha = 0.5f + 0.5f * ph), 6.dp.toPx(), c)
        }
        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
            Crossfade(idx, animationSpec = tween(350), label = "step") { i -> T(steps[i.coerceIn(0, steps.size - 1)], 18, FontWeight.Light, C.text, 1f, TextAlign.Center) }
        }
    }
}

@Composable
fun JarvisScreen(ctx: Context, onBack: () -> Unit, onApi: () -> Unit) {
    var on by remember { mutableStateOf(Store.bool(ctx, "jarvis_on")) }
    var running by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var needApi by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) {
            delay(if (target) 5000L else 2900L)
            on = target
            Store.putBool(ctx, "jarvis_on", target)
            CoreService.sync(ctx)
            running = false
        }
    }

    fun press() {
        if (running) return
        if (!on) {
            needApi = false
            if (!Perms.granted(ctx, Perm.ACCESS)) { err = "Turn on Accessibility in the permissions page first."; return }
            if (Store.apiKey(ctx, "groq").isBlank()) { err = "Jarvis needs its AI brain. Add your Groq API key first."; needApi = true; return }
            err = null; target = true; running = true
        } else {
            err = null; target = false; running = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            BackArrow(onBack)
            Spacer(Modifier.height(8.dp))
            Sphere(Modifier.fillMaxWidth().height(300.dp), 0.32f)
            Reveal {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Jarvis", 36, FontWeight.Light, spacing = 2f)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(if (on) C.ok else C.dim)
                        Spacer(Modifier.width(10.dp))
                        T(if (on) "Online and listening" else "Offline", 14, color = if (on) C.ok else C.dim)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Reveal(150) { PrimaryButton(if (on) "Power off Jarvis" else "Power on Jarvis") { press() } }
            AnimatedVisibility(err != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.bad.copy(alpha = 0.12f))
                            .border(1.dp, C.bad.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp)
                    ) { T(err ?: "", 14, color = C.bad) }
                    if (needApi) { Spacer(Modifier.height(12.dp)); GhostButton("Open API settings", onClick = onApi) }
                }
            }
            AnimatedVisibility(on, enter = fadeIn(tween(600)) + expandVertically(tween(600)), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(26.dp))
                    KCard {
                        T("Vocal call", 20, FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        T("Say Jarvis from anywhere. It appears at the bottom of your screen, then speak your task.", 14, color = C.dim)
                    }
                    Spacer(Modifier.height(12.dp))
                    KCard {
                        T("Easy Touch call", 20, FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        T("Put Call Jarvis in a slot of your Easy Touch menu and tap it. Jarvis answers the same way.", 14, color = C.dim)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        AnimatedVisibility(running, enter = fadeIn(tween(500)), exit = fadeOut(tween(700))) { PowerSequence(target) }
    }
}

// ------------------------------------------------------------------ EASY TOUCH

@Composable
fun EtUiScreen(ctx: Context, onBack: () -> Unit) {
    val slots = remember { mutableStateListOf<String?>().also { it.addAll(Store.slots(ctx)) } }
    var selected by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val accent = Store.etColor(ctx)

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Spacer(Modifier.width(14.dp))
                T("Easy touch UI", 26, FontWeight.Light)
            }
            Spacer(Modifier.height(10.dp))
            T(
                if (selected == null) "Choose a feature below, then tap an empty slot. Tap a filled slot to clear it."
                else "Now tap the slot where " + (Features.byId(selected)?.name ?: "") + " should go.",
                14, color = if (selected == null) C.dim else C.a2
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.size(330.dp),
                    factory = { ShapeMenuView(it) },
                    update = { v ->
                        v.accent = accent
                        v.slots = slots.toList()
                        v.onSlot = { i ->
                            if (i in 0..9) {
                                val s = selected
                                if (s != null) {
                                    for (k in slots.indices) if (slots[k] == s) slots[k] = null
                                    slots[i] = s
                                    selected = null
                                } else slots[i] = null
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            T("Features", 18, FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Features.all.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { f ->
                        val isSel = selected == f.id
                        val placed = slots.contains(f.id)
                        val bd by animateColorAsState(if (isSel) C.a2 else if (placed) C.a1.copy(alpha = 0.5f) else C.line, label = "chip")
                        Box(
                            Modifier.weight(1f).height(52.dp).bounceClick { selected = if (isSel) null else f.id }
                                .clip(RoundedCornerShape(16.dp)).background(if (isSel) C.a2.copy(alpha = 0.14f) else C.card)
                                .border(1.dp, bd, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) { T(f.name, 14, FontWeight.Medium, if (isSel) C.a2 else C.text) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (saved) "Saved" else "Save") {
                Store.putSlots(ctx, slots.toList())
                CoreService.sync(ctx)
                saved = true
                scope.launch { delay(700); onBack() }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun EtDesignScreen(ctx: Context, onBack: () -> Unit) {
    var size by remember { mutableFloatStateOf(Store.etSize(ctx).toFloat()) }
    var color by remember { mutableIntStateOf(Store.etColor(ctx)) }
    var anim by remember { mutableIntStateOf(Store.etAnim(ctx)) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val palette = listOf(0xFF7C8CFF, 0xFF4FD8FF, 0xFF38D39F, 0xFFFF5C7A, 0xFFFFB84F, 0xFFC27CFF, 0xFFFFFFFF, 0xFFFF7CE0).map { it.toInt() }
    val names = listOf("Pulse", "Breathe", "Orbit", "Ripple", "Spin")

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Spacer(Modifier.width(14.dp))
                T("Easy touch design", 26, FontWeight.Light)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                key(size.toInt() / 4, color, anim) {
                    AndroidView(factory = { FloatingIconView(it, (size * density).toInt(), color, anim) })
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T("Icon size", 18, FontWeight.SemiBold)
                T("${size.toInt()} dp", 14, color = C.dim)
            }
            Slider(
                size, { size = it }, valueRange = 36f..90f,
                colors = SliderDefaults.colors(thumbColor = C.a2, activeTrackColor = C.a1, inactiveTrackColor = C.line)
            )
            Spacer(Modifier.height(12.dp))
            T("Colour", 18, FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                palette.forEach { col ->
                    val sel = col == color
                    val ring by animateColorAsState(if (sel) Color.White else Color.Transparent, label = "ring")
                    Box(
                        Modifier.size(34.dp).bounceClick { color = col }.clip(CircleShape).background(Color(col))
                            .border(2.dp, ring, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            T("Animation", 18, FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            names.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, n ->
                        val idx = r * 3 + i
                        val sel = anim == idx
                        val bd by animateColorAsState(if (sel) C.a2 else C.line, label = "an")
                        Box(
                            Modifier.weight(1f).height(48.dp).bounceClick { anim = idx }.clip(RoundedCornerShape(16.dp))
                                .background(if (sel) C.a2.copy(alpha = 0.14f) else C.card).border(1.dp, bd, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) { T(n, 14, FontWeight.Medium, if (sel) C.a2 else C.text) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(if (saved) "Saved" else "Save") {
                Store.putInt(ctx, "et_size", size.toInt())
                Store.putInt(ctx, "et_color", color)
                Store.putInt(ctx, "et_anim", anim)
                CoreService.sync(ctx)
                saved = true
                scope.launch { delay(700); onBack() }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------ SETTINGS

fun restartApp(c: Context) {
    c.cacheDir.deleteRecursively()
    c.externalCacheDir?.deleteRecursively()
    Store.clearApiKeys(c)
    Store.putBool(c, "jarvis_on", false)
    Store.putBool(c, "et_on", false)
    try { c.stopService(Intent(c, CoreService::class.java)) } catch (e: Exception) { }
    val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (launch != null) {
        val pi = PendingIntent.getActivity(c, 7, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 600, pi)
    }
    exitProcess(0)
}

@Composable
fun SettingsScreen(ctx: Context, onBack: () -> Unit, onApi: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    LaunchedEffect(restarting) { if (restarting) { delay(1800); restartApp(ctx) } }

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Spacer(Modifier.width(14.dp))
                T("Settings", 26, FontWeight.Light)
            }
            Spacer(Modifier.height(24.dp))
            Reveal(80) {
                KCard(onClick = { confirm = true }) {
                    T("Restart Kitagawh", 19, FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    T("Clears the cache and fixes bugs by fully restarting the app. Saved API keys are cleared too.", 13, color = C.dim)
                }
            }
            Spacer(Modifier.height(12.dp))
            Reveal(180) {
                KCard(onClick = onApi) {
                    T("API", 19, FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    T("Add or change the keys that power Jarvis.", 13, color = C.dim)
                }
            }
            Spacer(Modifier.height(12.dp))
            Reveal(280) {
                KCard(onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:csharan2122011@gmail.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { }
                }) {
                    T("Contact admin", 19, FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    T("csharan2122011@gmail.com", 13, color = C.a2)
                }
            }
        }
        AnimatedVisibility(restarting, enter = fadeIn(tween(400))) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Logo(150.dp)
                    Spacer(Modifier.height(18.dp))
                    T("Restarting", 18, FontWeight.Light, C.dim, 2f)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = C.card2,
            title = { T("Restart Kitagawh?", 20, FontWeight.SemiBold) },
            text = { T("This clears the cache and your saved API keys, then restarts the app. You will need to add the keys again.", 14, color = C.dim) },
            confirmButton = { TextButton({ confirm = false; restarting = true }) { T("Restart", 15, FontWeight.SemiBold, C.bad) } },
            dismissButton = { TextButton({ confirm = false }) { T("Cancel", 15, color = C.dim) } }
        )
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).bounceClick(onClick = onClick).clip(CircleShape).border(1.5.dp, C.a2, CircleShape),
        contentAlignment = Alignment.Center
    ) { T("i", 15, FontWeight.SemiBold, C.a2) }
}

private fun mask(k: String) = if (k.isBlank()) "" else "\u2022".repeat(14) + k.takeLast(4)

@Composable
private fun ApiRow(ctx: Context, spec: ApiSpec) {
    var saved by remember { mutableStateOf(Store.apiKey(ctx, spec.id)) }
    var text by remember { mutableStateOf(saved) }
    var editing by remember { mutableStateOf(saved.isBlank()) }
    var info by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoButton { info = true }
            Spacer(Modifier.width(12.dp))
            T(spec.name, 22, FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        KField("${spec.name} API key", if (editing) text else mask(saved), { text = it }, enabled = editing, hint = spec.hint)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                PrimaryButton("Save", enabled = editing && text.isNotBlank()) {
                    Store.putApiKey(ctx, spec.id, text)
                    saved = text.trim(); text = saved; editing = false; note = "Saved"
                }
            }
            Box(Modifier.weight(1f)) {
                GhostButton("Edit") { editing = true; text = saved; note = "" }
            }
        }
        AnimatedVisibility(note.isNotEmpty()) {
            Column { Spacer(Modifier.height(10.dp)); T(note, 13, color = C.ok) }
        }
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            containerColor = C.card2,
            title = { T("Get your ${spec.name} key", 20, FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spec.guide.forEachIndexed { i, s -> T("${i + 1}. $s", 14, color = C.dim) }
                }
            },
            confirmButton = { TextButton({ info = false }) { T("Got it", 15, FontWeight.SemiBold, C.a2) } }
        )
    }
}

@Composable
fun ApiScreen(ctx: Context, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Spacer(Modifier.width(14.dp))
                T("API", 26, FontWeight.Light)
            }
            Spacer(Modifier.height(10.dp))
            T("Keys stay on this phone and are only sent to the service they belong to.", 14, color = C.dim)
            Spacer(Modifier.height(22.dp))
            Api.list.forEach { spec ->
                Reveal(100) { ApiRow(ctx, spec) }
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
