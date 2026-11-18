package com.kitagawh.app

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    companion object {
        val routeRequest = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        routeRequest.value = intent.getStringExtra("route")
        setContent { KApp(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRequest.value = intent.getStringExtra("route")
    }
}

@Composable
fun KApp(act: MainActivity) {
    // stack[0] is the base page, anything after it is a page opened on top
    var stack by remember { mutableStateOf(listOf("splash")) }
    val base = stack.first()
    val overlays = stack.drop(1)

    fun push(r: String) { if (stack.last() != r) stack = stack + r }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun reset(r: String) { stack = listOf(r) }

    fun firstRoute(): String = when {
        !Perms.allRequiredGranted(act) -> "perms"
        !Store.bool(act, "about_done") -> "about"
        !Store.bool(act, "logged_in") -> "auth"
        else -> "home"
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // jump requests from the floating menu (Settings feature)
    val req = MainActivity.routeRequest.value
    LaunchedEffect(req) {
        if (req != null) {
            if (Store.bool(act, "logged_in") && Perms.allRequiredGranted(act)) stack = listOf("home", req)
            MainActivity.routeRequest.value = null
        }
    }

    // if a required permission is lost while using the app, go back to the permission page
    LaunchedEffect(base) {
        if (base == "home") {
            while (true) {
                delay(1500)
                if (!Perms.allRequiredGranted(act)) { reset("perms"); break }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(surface = C.card2, onSurface = C.text, background = C.bg, primary = C.a1)) {
        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            AnimatedContent(
                targetState = base, label = "base",
                transitionSpec = { (fadeIn(tween(450)) + slideInHorizontally(tween(450)) { it / 8 }) togetherWith fadeOut(tween(250)) }
            ) { b ->
                when (b) {
                    "splash" -> SplashScreen { reset(firstRoute()) }
                    "perms" -> PermissionScreen(act) {
                        reset(if (!Store.bool(act, "about_done")) "about" else if (!Store.bool(act, "logged_in")) "auth" else "home")
                    }
                    "about" -> AboutScreen { Store.putBool(act, "about_done", true); reset("auth") }
                    "auth" -> AuthScreen(act) { reset("home") }
                    else -> HomeScreen(
                        act,
                        onSettings = { push("settings") },
                        onJarvis = { push("jarvis") },
                        onEtUi = { push("et_ui") },
                        onEtDesign = { push("et_design") }
                    )
                }
            }
            overlays.forEach { r ->
                androidx.compose.runtime.key(r) {
                    Box(Modifier.fillMaxSize().background(C.bg)) {
                        com.kitagawh.app.PageIn {
                            when (r) {
                                "settings" -> SettingsScreen(act, onBack = { pop() }, onApi = { push("api") })
                                "api" -> ApiScreen(act, onBack = { pop() })
                                "jarvis" -> JarvisScreen(act, onBack = { pop() }, onApi = { push("api") })
                                "et_ui" -> EtUiScreen(act, onBack = { pop() })
                                "et_design" -> EtDesignScreen(act, onBack = { pop() })
                            }
                        }
                    }
                }
            }
        }
    }
}
