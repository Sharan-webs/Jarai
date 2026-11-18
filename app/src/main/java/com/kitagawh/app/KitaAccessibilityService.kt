package com.kitagawh.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream

class KitaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: KitaAccessibilityService? = null
    }

    private var nodes: List<AccessibilityNodeInfo> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        CoreService.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Master.keepAccessibility(this)
        return super.onUnbind(intent)
    }

    // ---- reading the screen ----
    fun dumpScreen(): String {
        val root = rootInActiveWindow ?: return "(screen not readable right now)"
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()

        fun walk(n: AccessibilityNodeInfo) {
            if (list.size > 90) return
            val t = n.text?.toString()?.take(60)
            val d = n.contentDescription?.toString()?.take(60)
            val interesting = !t.isNullOrBlank() || !d.isNullOrBlank() || n.isClickable || n.isEditable || n.isCheckable
            if (n.isVisibleToUser && interesting) {
                sb.append(list.size).append(": ").append(t ?: "")
                if (!d.isNullOrBlank()) sb.append(" [").append(d).append("]")
                if (n.isClickable) sb.append(" clickable")
                if (n.isEditable) sb.append(" input")
                if (n.isCheckable) sb.append(if (n.isChecked) " CHECKED" else " unchecked")
                if (n.isScrollable) sb.append(" scrollable")
                sb.append('\n')
                list.add(n)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        nodes = list
        return "app: ${root.packageName}\n$sb"
    }

    // ---- acting on the screen ----
    private fun clickableParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && !cur.isClickable && hops < 6) { cur = cur.parent; hops++ }
        return cur
    }

    fun clickIndex(i: Int): String {
        val node = nodes.getOrNull(i) ?: return "no element with index $i"
        val p = clickableParent(node)
        if (p != null && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "clicked $i"
        val r = Rect()
        node.getBoundsInScreen(r)
        tap(r.centerX().toFloat(), r.centerY().toFloat())
        return "tapped $i"
    }

    fun clickText(text: String): String {
        val root = rootInActiveWindow ?: return "screen not readable"
        val hit = root.findAccessibilityNodeInfosByText(text).firstOrNull() ?: return "no element with text $text"
        val p = clickableParent(hit)
        if (p != null && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "clicked $text"
        val r = Rect()
        hit.getBoundsInScreen(r)
        tap(r.centerX().toFloat(), r.centerY().toFloat())
        return "tapped $text"
    }

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "screen not readable"
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: nodes.firstOrNull { it.isEditable }
            ?: return "no text field found"
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) "typed" else "could not type"
    }

    fun submit(): String {
        val root = rootInActiveWindow ?: return "screen not readable"
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return "no focused field"
        return if (Build.VERSION.SDK_INT >= 30 &&
            f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        ) "submitted" else "could not submit"
    }

    fun scroll(dir: String): String {
        val root = rootInActiveWindow ?: return "screen not readable"
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo) {
            if (found != null) return
            if (n.isScrollable) { found = n; return }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        val down = dir != "up"
        val f = found
        if (f != null && f.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            return "scrolled $dir"
        }
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        if (down) swipe(x, m.heightPixels * 0.7f, x, m.heightPixels * 0.3f) else swipe(x, m.heightPixels * 0.3f, x, m.heightPixels * 0.7f)
        return "swiped $dir"
    }

    fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y); lineTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 350)).build(), null, null)
    }

    // ---- screenshot for Screen search (Android 11+) ----
    fun screenshotBase64(cb: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hb = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        val soft = hw!!.copy(Bitmap.Config.ARGB_8888, false)
                        hb.close()
                        val scale = 800f / soft.width
                        val small = Bitmap.createScaledBitmap(soft, 800, (soft.height * scale).toInt(), true)
                        val out = ByteArrayOutputStream()
                        small.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        cb(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    } catch (e: Exception) {
                        cb(null)
                    }
                }

                override fun onFailure(errorCode: Int) { cb(null) }
            })
        } catch (e: Exception) {
            cb(null)
        }
    }
}
