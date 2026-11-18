package com.kitagawh.app

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

object Features {
    data class F(val id: String, val name: String, val short: String)

    val all = listOf(
        F("screen_search", "Screen search", "Search"),
        F("torch", "Torch", "Torch"),
        F("call", "Call Jarvis", "Jarvis"),
        F("settings", "Settings", "Settings"),
        F("home", "Home", "Home"),
        F("back", "Back", "Back"),
        F("recents", "Recents", "Recents"),
        F("notifications", "Notifications", "Alerts"),
        F("screenshot", "Screenshot", "Shot"),
        F("lock", "Lock screen", "Lock")
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    @Volatile var torchOn = false
}

object Actions {

    fun exec(c: Context, a: KitaAccessibilityService, action: String, args: JSONObject): String = try {
        when (action) {
            "open_app" -> openApp(c, args.optString("name"))
            "open_settings" -> openSettings(c, args.optString("page"))
            "open_url" -> {
                c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.optString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "opened"
            }
            "click" -> a.clickIndex(args.optInt("index", -1))
            "click_text" -> a.clickText(args.optString("text"))
            "type" -> a.typeText(args.optString("text"))
            "submit" -> a.submit()
            "scroll" -> a.scroll(args.optString("dir", "down"))
            "back" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); "ok" }
            "home" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); "ok" }
            "recents" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); "ok" }
            "notifications" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); "ok" }
            "quick_settings" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); "ok" }
            "lock" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); "ok" }
            "screenshot" -> { a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); "ok" }
            "torch" -> torch(c, args.optBoolean("on", true))
            "volume" -> volume(c, args.optString("dir"), args.optInt("percent", -1))
            "brightness" -> brightness(c, args.optInt("percent", 50))
            "wait" -> { Thread.sleep(args.optLong("ms", 1000).coerceIn(200, 4000)); "waited" }
            else -> "unknown action"
        }
    } catch (e: Exception) {
        "failed: " + (e.message ?: e.javaClass.simpleName)
    }

    fun launcherIntent(c: Context, name: String): Intent? {
        val pm = c.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(q, 0)
        val n = name.lowercase().trim()
        if (n.isBlank()) return null
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: list.firstOrNull { n.contains(it.loadLabel(pm).toString().lowercase()) && it.loadLabel(pm).toString().length > 2 }
            ?: return null
        return pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
    }

    fun openApp(c: Context, name: String): String {
        val i = launcherIntent(c, name) ?: return "no app named $name"
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "opened $name"
    }

    fun openSettings(c: Context, page: String): String {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "hotspot", "tethering" -> "android.settings.TETHER_SETTINGS"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "mobile_data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "opened $page settings"
        } catch (e: Exception) {
            c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "opened main settings"
        }
    }

    fun torch(c: Context, on: Boolean): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "this phone has no flash"
        cm.setTorchMode(id, on)
        Features.torchOn = on
        return if (on) "torch on" else "torch off"
    }

    fun volume(c: Context, dir: String, percent: Int): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        when {
            percent in 0..100 -> am.setStreamVolume(AudioManager.STREAM_MUSIC, percent * max / 100, AudioManager.FLAG_SHOW_UI)
            dir == "up" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            dir == "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            dir == "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            else -> return "no volume change given"
        }
        return "volume changed"
    }

    fun brightness(c: Context, percent: Int): String {
        if (!Settings.System.canWrite(c)) return "allow Modify system settings first"
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, percent.coerceIn(1, 100) * 255 / 100)
        return "brightness set"
    }
}
