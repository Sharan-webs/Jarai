package com.kitagawh.app

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(250); step = 1; delay(1500); step = 2; delay(450); onDone() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Aura(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(step >= 1, enter = fadeIn(tween(800)) + scaleIn(tween(800), initialScale = 0.7f), exit = fadeOut(tween(300))) {
                Logo(170.dp)
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(step >= 1, enter = fadeIn(tween(1300, delayMillis = 500)), exit = fadeOut(tween(300))) {
                T("Kitagawh!", 28, FontWeight.Light, spacing = 7f)
            }
        }
    }
}

@Composable
private fun StatusRow(on: Boolean, optional: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseDot(if (on) C.ok else if (optional) C.dim else C.a1)
        Spacer(Modifier.width(10.dp))
        T(
            if (on) "Enabled" else if (optional) "Optional, tap to enable" else "Tap to enable",
            14, FontWeight.Medium, if (on) C.ok else if (optional) C.dim else C.a1
        )
    }
}

@Composable
fun PermissionScreen(act: Activity, onStart: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    val state = remember(tick) { Perm.values().associateWith { Perms.granted(act, it) } }
    val accOn = state[Perm.ACCESS] == true
    val masterOn = state[Perm.MASTER] == true
    val allOk = Perm.values().filter { !it.optional }.all { state[it] == true }

    var optimizing by remember { mutableStateOf(false) }
    val prog by animateFloatAsState(if (optimizing) 1f else 0f, tween(if (optimizing) 2400 else 0, easing = LinearEasing), label = "prog")
    LaunchedEffect(accOn) { if (accOn) { optimizing = true; Optimizer.run(act); delay(2600); optimizing = false } }
    LaunchedEffect(masterOn) { if (masterOn) Master.applyAll(act) }

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Reveal {
                    Column {
                        Logo(90.dp)
                        Spacer(Modifier.height(16.dp))
                        T("Permissions", 34, FontWeight.Light, spacing = 1f)
                        Spacer(Modifier.height(6.dp))
                        T("Tap each one. It opens the right settings page and updates here live.", 14, color = C.dim)
                    }
                }
            }
            item {
                AnimatedVisibility(optimizing, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card2).padding(16.dp)
                    ) {
                        T("Optimising Kitagawh", 15, FontWeight.Medium, C.a2)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.line)) {
                            Box(Modifier.fillMaxWidth(prog).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.a2))
                        }
                    }
                }
            }
            items(Perm.values().toList()) { p ->
                val on = state[p] == true
                Reveal(delayMs = 120 + p.ordinal * 70) {
                    val border by animateColorAsState(if (on) C.ok.copy(alpha = 0.5f) else C.line, tween(500), label = "pb")
                    KCard(borderColor = border, onClick = { Perms.open(act, p) }) {
                        T(p.title, 18, FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        T(p.desc, 13, color = C.dim)
                        Spacer(Modifier.height(12.dp))
                        StatusRow(on, p.optional)
                    }
                }
            }
            item {
                Reveal(delayMs = 900) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(if (allOk) "Start" else "Enable the required permissions", enabled = allOk, onClick = onStart)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onAccept: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 30.dp, 22.dp, 50.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Reveal {
                    Column {
                        Logo(110.dp)
                        Spacer(Modifier.height(16.dp))
                        T("About Kitagawh", 34, FontWeight.Light, spacing = 1f)
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            AboutText.sections.forEachIndexed { si, (title, lines) ->
                item {
                    Reveal {
                        Column {
                            Spacer(Modifier.height(18.dp))
                            T(title, 20, FontWeight.SemiBold, C.a2)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                items(lines) { line ->
                    Reveal { T(line, 15, color = C.text.copy(alpha = 0.9f)) }
                }
            }
            item {
                Reveal {
                    Column {
                        Spacer(Modifier.height(26.dp))
                        KCard(borderColor = C.a1.copy(alpha = 0.5f)) {
                            T(AboutText.actTitle, 20, FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            AboutText.actClauses.forEach {
                                T(it, 14, color = C.dim)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        PrimaryButton("Accept and continue", onClick = onAccept)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(ctx: Context, onDone: () -> Unit) {
    var create by remember { mutableStateOf(!Store.bool(ctx, "has_account")) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var age by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish() {
        if (create) {
            Store.putStr(ctx, "acc_name", name.trim())
            Store.putStr(ctx, "acc_number", number.trim())
            Store.putInt(ctx, "acc_age", age)
            Store.putBool(ctx, "has_account", true)
        }
        Store.putBool(ctx, "logged_in", true)
        onDone()
    }

    fun submit() {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 7) { msg = true to "Enter your full phone number."; return }
        if (create) {
            if (name.isBlank()) { msg = true to "Enter your name."; return }
            if (age == 0) { msg = true to "Choose your age."; return }
        } else {
            val saved = Store.str(ctx, "acc_number")
            if (saved.isBlank()) { msg = true to "There is no account on this phone yet. Tap Create account first."; return }
            if (Sim.last10(saved) != Sim.last10(number)) {
                msg = true to "That number does not match the account created on this phone."; return
            }
        }
        when (val r = Sim.verify(ctx, number)) {
            SimResult.Match -> { msg = false to "Number verified."; scope.launch { busy = true; delay(500); finish() } }
            is SimResult.Mismatch -> msg = true to "That number is not in this phone. The SIM inserted here is ${r.ends.joinToString(" and ")}. Use the number of the SIM that is in this phone."
            SimResult.NoSim -> msg = true to "No SIM card was found in this phone, so the number cannot be checked."
            SimResult.Unreadable -> {
                msg = false to "Your carrier does not share the SIM number with apps, so I could not verify it. Continuing with the number you entered."
                scope.launch { busy = true; delay(2200); finish() }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Aura(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Spacer(Modifier.height(24.dp))
            Reveal { Logo(100.dp) }
            Spacer(Modifier.height(18.dp))
            Reveal(100) { T(if (create) "Create account" else "Sign in", 34, FontWeight.Light, spacing = 1f) }
            Spacer(Modifier.height(6.dp))
            Reveal(160) { T(if (create) "Three quick details and you are in." else "Enter the number you registered with.", 14, color = C.dim) }
            Spacer(Modifier.height(26.dp))

            Reveal(220) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AnimatedVisibility(create) {
                        KField("Name", name, { name = it }, hint = "Your name")
                    }
                    KField("Phone number", number, { number = it }, keyboard = KeyboardType.Phone, hint = "Number of the SIM in this phone")
                    AnimatedVisibility(create) {
                        Column {
                            T("Age", 13, color = C.dim, spacing = 0.4f)
                            Spacer(Modifier.height(6.dp))
                            Box {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                                        .border(1.dp, C.line, RoundedCornerShape(14.dp))
                                        .clickable { menu = true }.padding(horizontal = 16.dp, vertical = 15.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    T(if (age == 0) "Select your age" else "$age years", 16, color = if (age == 0) C.dim else C.text)
                                    T("v", 14, color = C.dim)
                                }
                                DropdownMenu(menu, { menu = false }, Modifier.height(320.dp)) {
                                    for (a in 10..99) DropdownMenuItem(text = { Text("$a") }, onClick = { age = a; menu = false })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(msg != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                val (err, text) = msg ?: (false to "")
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background((if (err) C.bad else C.ok).copy(alpha = 0.12f))
                        .border(1.dp, (if (err) C.bad else C.ok).copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp)
                ) { T(text, 14, color = if (err) C.bad else C.ok) }
            }
            Spacer(Modifier.height(20.dp))
            Reveal(300) {
                Column {
                    PrimaryButton(if (create) "Create account" else "Sign in", enabled = !busy) { if (!busy) submit() }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().clickable { create = !create; msg = null }.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) { T(if (create) "I already have an account. Sign in" else "New here? Create account", 14, color = C.a2, align = TextAlign.Center) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
