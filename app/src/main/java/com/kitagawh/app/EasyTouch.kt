package com.kitagawh.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

class EasyTouch(private val ctx: Context, private val onFeature: (String) -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = ctx.resources.displayMetrics.density
    private var icon: FloatingIconView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menu: FrameLayout? = null

    private var offX = 0f; private var offY = 0f; private var startX = 0f; private var startY = 0f
    private var moved = false

    fun show() {
        hide()
        if (!Settings.canDrawOverlays(ctx)) return
        val size = (Store.etSize(ctx) * dp).toInt()
        val v = FloatingIconView(ctx, size, Store.etColor(ctx), Store.etAnim(ctx))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = Store.int(ctx, "et_x", 0)
        p.y = Store.int(ctx, "et_y", (300 * dp).toInt())
        v.setOnTouchListener(::onTouch)
        try { wm.addView(v, p) } catch (e: Exception) { return }
        icon = v; params = p
        v.scaleX = 0f; v.scaleY = 0f
        v.animate().scaleX(1f).scaleY(1f).setDuration(380).start()
    }

    fun hide() {
        closeMenu(false)
        icon?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        icon = null; params = null
    }

    private fun onTouch(v: View, e: MotionEvent): Boolean {
        val p = params ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.rawX; startY = e.rawY
                offX = p.x - e.rawX; offY = p.y - e.rawY
                moved = false
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - startX) > 8 * dp || abs(e.rawY - startY) > 8 * dp) moved = true
                if (moved) {
                    p.x = (e.rawX + offX).toInt(); p.y = (e.rawY + offY).toInt()
                    try { wm.updateViewLayout(v, p) } catch (ex: Exception) { }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                if (!moved && e.actionMasked == MotionEvent.ACTION_UP) openMenu() else snap(v, p)
            }
        }
        return true
    }

    private fun snap(v: View, p: WindowManager.LayoutParams) {
        val w = ctx.resources.displayMetrics.widthPixels
        val target = if (p.x + v.width / 2 < w / 2) 0 else w - v.width
        ValueAnimator.ofInt(p.x, target).apply {
            duration = 260
            addUpdateListener {
                p.x = it.animatedValue as Int
                try { wm.updateViewLayout(v, p) } catch (ex: Exception) { }
            }
            start()
        }
        Store.putInt(ctx, "et_x", target)
        Store.putInt(ctx, "et_y", p.y)
    }

    fun openMenu() {
        if (menu != null) return
        val dm = ctx.resources.displayMetrics
        val root = FrameLayout(ctx)
        root.setBackgroundColor(0xB3000000.toInt())
        val m = ShapeMenuView(ctx)
        m.accent = Store.etColor(ctx)
        m.slots = Store.slots(ctx)
        m.activeIds = if (Features.torchOn) setOf("torch") else emptySet()
        m.onSlot = { i ->
            if (i < 0) closeMenu(true)
            else Store.slots(ctx)[i]?.let { id -> closeMenu(true); onFeature(id) }
        }
        val size = (min(dm.widthPixels, dm.heightPixels) * 0.88f).toInt()
        root.addView(m, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        root.setOnClickListener { closeMenu(true) }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(root, p) } catch (e: Exception) { return }
        menu = root
        root.alpha = 0f
        m.scaleX = 0.6f; m.scaleY = 0.6f; m.rotation = -12f
        root.animate().alpha(1f).setDuration(220).start()
        m.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(360)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
    }

    fun closeMenu(animate: Boolean) {
        val m = menu ?: return
        menu = null
        if (!animate) { try { wm.removeView(m) } catch (e: Exception) { }; return }
        m.animate().alpha(0f).setDuration(160).withEndAction { try { wm.removeView(m) } catch (e: Exception) { } }.start()
    }
}
