package com.kitagawh.app

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import rikka.shizuku.Shizuku

enum class Perm(val title: String, val desc: String, val optional: Boolean = false) {
    MIC("Microphone", "Lets Jarvis hear your voice commands."),
    SPEAKER("Speaker", "Lets Jarvis talk back through the phone speaker."),
    OVERLAY("Display over other apps", "Needed for the floating Easy Touch icon and the Jarvis overlay."),
    ACCESS(
        "Accessibility",
        "Lets Jarvis see the screen and tap, swipe, type and scroll for you. If the switch is greyed out on Android 13 or newer: open App info, tap the three dots menu, choose Allow restricted settings, then try again."
    ),
    PIP("Picture in picture", "Lets Kitagawh keep a small window on screen while you use other apps."),
    SIM("Phone number check", "Used at sign in to confirm the number belongs to this phone."),
    NOTIF("Notifications", "Shows that Kitagawh is running in the background."),
    SETTINGS("Modify system settings", "Used by the brightness and volume commands."),
    BATTERY("Run in background", "Keeps Jarvis and Easy Touch alive after the app is closed."),
    MASTER(
        "Master access (Shizuku, optional)",
        "Install and start the Shizuku app, then tap here and allow it. Kitagawh then grants itself every permission and switches Accessibility back on by itself every 10 seconds if it is ever turned off.",
        true
    )
}

object Perms {
    private const val WSS = "android.permission.WRITE_SECURE_SETTINGS"

    fun granted(c: Context, p: Perm): Boolean = try {
        when (p) {
            Perm.MIC -> c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            Perm.SPEAKER -> c.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED
            Perm.OVERLAY -> Settings.canDrawOverlays(c)
            Perm.ACCESS -> accessOn(c)
            Perm.PIP -> pipAllowed(c)
            Perm.SIM -> c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                c.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            Perm.NOTIF -> Build.VERSION.SDK_INT < 33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            Perm.SETTINGS -> Settings.System.canWrite(c)
            Perm.BATTERY -> (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
            Perm.MASTER -> masterOn(c)
        }
    } catch (e: Throwable) {
        false
    }

    fun allRequiredGranted(c: Context) = Perm.values().filter { !it.optional }.all { granted(c, it) }

    fun accessOn(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(c, KitaAccessibilityService::class.java)
        return s.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    private fun pipAllowed(c: Context): Boolean = try {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow("android:picture_in_picture", Process.myUid(), c.packageName)
        else
            @Suppress("DEPRECATION") ops.checkOpNoThrow("android:picture_in_picture", Process.myUid(), c.packageName)
        mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
    } catch (e: Throwable) {
        true
    }

    fun shizukuAlive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun masterOn(c: Context): Boolean {
        if (c.checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED) return true
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    private fun details(a: Activity) {
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
    }

    private fun go(a: Activity, i: Intent) {
        try {
            a.startActivity(i)
        } catch (e: Exception) {
            try { details(a) } catch (e2: Exception) { }
        }
    }

    private fun askRuntime(a: Activity, key: String, perms: Array<String>) {
        if (Store.bool(a, "asked_$key")) {
            try { details(a) } catch (e: Exception) { }
            Toast.makeText(a, "Open Permissions and allow it", Toast.LENGTH_SHORT).show()
        } else {
            Store.putBool(a, "asked_$key", true)
            a.requestPermissions(perms, 11)
        }
    }

    fun open(a: Activity, p: Perm) {
        val pkg = Uri.parse("package:${a.packageName}")
        if (granted(a, p) && p != Perm.ACCESS) return
        when (p) {
            Perm.MIC -> askRuntime(a, "mic", arrayOf(Manifest.permission.RECORD_AUDIO))
            Perm.SPEAKER -> Toast.makeText(a, "Speaker is granted automatically", Toast.LENGTH_SHORT).show()
            Perm.OVERLAY -> go(a, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            Perm.ACCESS -> {
                if (Perms.accessOn(a)) return
                Toast.makeText(a, "Find Kitagawh! and switch it on", Toast.LENGTH_LONG).show()
                go(a, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Perm.PIP -> go(a, Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", pkg))
            Perm.SIM -> askRuntime(a, "sim", arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS))
            Perm.NOTIF -> {
                if (Build.VERSION.SDK_INT >= 33) askRuntime(a, "notif", arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else go(a, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
            }
            Perm.SETTINGS -> go(a, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg))
            Perm.BATTERY -> go(a, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            Perm.MASTER -> {
                if (shizukuAlive()) {
                    try { Shizuku.requestPermission(1001) } catch (e: Throwable) { }
                } else {
                    val launch = a.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (launch != null) {
                        Toast.makeText(a, "Start Shizuku, then come back", Toast.LENGTH_LONG).show()
                        go(a, launch)
                    } else {
                        Toast.makeText(a, "Install the Shizuku app first", Toast.LENGTH_LONG).show()
                        go(a, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    }
                }
            }
        }
    }
}

/** Runs privileged commands through Shizuku and keeps Accessibility switched on. */
object Master {
    private const val WSS = "android.permission.WRITE_SECURE_SETTINGS"

    fun sh(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Throwable) {
        ""
    }

    fun applyAll(c: Context) {
        val p = c.packageName
        val cmds = listOf(
            "pm grant $p android.permission.WRITE_SECURE_SETTINGS",
            "pm grant $p android.permission.RECORD_AUDIO",
            "pm grant $p android.permission.READ_PHONE_STATE",
            "pm grant $p android.permission.READ_PHONE_NUMBERS",
            "pm grant $p android.permission.POST_NOTIFICATIONS",
            "appops set $p SYSTEM_ALERT_WINDOW allow",
            "appops set $p WRITE_SETTINGS allow",
            "appops set $p PICTURE_IN_PICTURE allow",
            "dumpsys deviceidle whitelist +$p"
        )
        Thread {
            cmds.forEach { sh(it) }
            keepAccessibility(c)
        }.start()
    }

    private fun merged(c: Context): String {
        val cn = ComponentName(c, KitaAccessibilityService::class.java).flattenToString()
        val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return if (cur.split(':').any { it.equals(cn, true) }) cur else if (cur.isBlank()) cn else "$cur:$cn"
    }

    /**
     * Switches the Accessibility service back on by itself.
     * Works when WRITE_SECURE_SETTINGS is granted (one adb command) or Shizuku is running.
     * Returns false when Android gives us no way to do it.
     */
    fun keepAccessibility(c: Context): Boolean {
        val list = merged(c)
        if (c.checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED) {
            return try {
                Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                true
            } catch (e: Exception) {
                false
            }
        }
        if (Perms.shizukuAlive() && Perms.masterOn(c)) {
            Thread {
                sh("pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS")
                sh("settings put secure enabled_accessibility_services $list")
                sh("settings put secure accessibility_enabled 1")
            }.start()
            return true
        }
        return false
    }
}

object Optimizer {
    @Volatile private var ran = false
    fun run(c: Context) {
        if (ran) return
        ran = true
        if (Perms.masterOn(c)) Master.applyAll(c)
        CoreService.sync(c)
    }
}
