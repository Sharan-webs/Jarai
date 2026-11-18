package com.kitagawh.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.atan2

/** The ten slot octagon menu. Slots 0..7 are the outer ring (clockwise from top), 8 and 9 are the centre cells. */
class ShapeMenuView(ctx: Context) : View(ctx) {
    var slots: List<String?> = List(10) { null }
        set(v) { field = v; invalidate() }
    var accent: Int = 0xFF7C8CFF.toInt()
        set(v) { field = v; invalidate() }
    var activeIds: Set<String> = emptySet()
    var onSlot: ((Int) -> Unit)? = null

    private val dp = ctx.resources.displayMetrics.density
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private var pulse = 0f
    private var pressed = -1
    private var anim: ValueAnimator? = null

    private var cx = 0f; private var cy = 0f; private var rr = 0f; private var ihw = 0f; private var ihh = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    private fun geo() {
        cx = width / 2f; cy = height / 2f
        rr = min(width, height) / 2f - 6 * dp
        ihw = rr * 0.42f; ihh = rr * 0.44f
    }

    private fun innerDist(a: Double): Float {
        val c = abs(cos(a)).toFloat(); val s = abs(sin(a)).toFloat()
        val tx = if (c < 1e-4f) Float.MAX_VALUE else ihw / c
        val ty = if (s < 1e-4f) Float.MAX_VALUE else ihh / s
        return min(tx, ty)
    }

    private fun fillColor(k: Int): Int {
        val id = slots.getOrNull(k)
        return when {
            pressed == k -> (accent and 0x00FFFFFF) or (110 shl 24)
            id != null && activeIds.contains(id) -> (accent and 0x00FFFFFF) or (80 shl 24)
            id != null -> (accent and 0x00FFFFFF) or (34 shl 24)
            else -> 0
        }
    }

    override fun onDraw(c: Canvas) {
        geo()
        val oct = Path()
        for (i in 0..7) {
            val a = Math.toRadians(-112.5 + 45.0 * i)
            val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
            if (i == 0) oct.moveTo(x, y) else oct.lineTo(x, y)
        }
        oct.close()
        val inner = RectF(cx - ihw, cy - ihh, cx + ihw, cy + ihh)
        val innerPath = Path().apply { addRoundRect(inner, rr * 0.12f, rr * 0.12f, Path.Direction.CW) }

        // outer ring fills
        for (k in 0..7) {
            val col = fillColor(k)
            if (col == 0) continue
            val a1 = Math.toRadians(-90.0 + 45.0 * k - 22.5); val a2 = Math.toRadians(-90.0 + 45.0 * k + 22.5)
            val wedge = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + rr * 1.2f * cos(a1).toFloat(), cy + rr * 1.2f * sin(a1).toFloat())
                lineTo(cx + rr * 1.2f * cos(a2).toFloat(), cy + rr * 1.2f * sin(a2).toFloat())
                close()
            }
            c.save()
            c.clipPath(wedge)
            c.clipOutPath(innerPath)
            fillP.color = col
            c.drawPath(oct, fillP)
            c.restore()
        }
        // centre fills
        for (k in 8..9) {
            val col = fillColor(k)
            if (col == 0) continue
            c.save()
            if (k == 8) c.clipRect(inner.left, inner.top, inner.right, cy) else c.clipRect(inner.left, cy, inner.right, inner.bottom)
            fillP.color = col
            c.drawPath(innerPath, fillP)
            c.restore()
        }

        // lines
        strokeP.color = accent
        strokeP.alpha = (185 + 70 * sin(pulse * 2 * PI)).toInt().coerceIn(120, 255)
        strokeP.strokeWidth = 2.6f * dp
        c.drawPath(oct, strokeP)
        c.drawPath(innerPath, strokeP)
        c.drawLine(cx - ihw, cy, cx + ihw, cy, strokeP)
        for (k in 0..7) {
            val a = Math.toRadians(-90.0 + 45.0 * k - 22.5)
            val d = innerDist(a)
            c.drawLine(cx + d * cos(a).toFloat(), cy + d * sin(a).toFloat(), cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat(), strokeP)
        }

        // labels
        textP.textSize = rr * 0.115f
        val off = (textP.ascent() + textP.descent()) / 2f
        for (k in 0..7) {
            val label = Features.byId(slots[k])?.short ?: continue
            val a = Math.toRadians(-90.0 + 45.0 * k)
            val mid = (innerDist(a) + rr * 0.92f) / 2f
            c.drawText(label, cx + mid * cos(a).toFloat(), cy + mid * sin(a).toFloat() - off, textP)
        }
        Features.byId(slots[8])?.let { c.drawText(it.short, cx, cy - ihh / 2f - off, textP) }
        Features.byId(slots[9])?.let { c.drawText(it.short, cx, cy + ihh / 2f - off, textP) }
    }

    private fun hit(x: Float, y: Float): Int {
        geo()
        val dx = x - cx; val dy = y - cy
        if (abs(dx) <= ihw && abs(dy) <= ihh) return if (dy < 0) 8 else 9
        val r = hypot(dx, dy)
        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0
        deg = ((deg % 360) + 360) % 360
        val k = ((deg + 22.5) / 45.0).toInt() % 8
        val diff = ((deg + 22.5) % 45.0) - 22.5
        return if (r * cos(Math.toRadians(diff)) <= rr * cos(Math.toRadians(22.5))) k else -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
            MotionEvent.ACTION_MOVE -> { if (hit(e.x, e.y) != pressed) { pressed = -1; invalidate() } }
            MotionEvent.ACTION_UP -> {
                val k = hit(e.x, e.y)
                val was = pressed
                pressed = -1
                invalidate()
                if (was >= 0 && k == was) onSlot?.invoke(k) else if (k == -1) onSlot?.invoke(-1)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}

/** Floating Easy Touch icon: a black disc with -_- and one of five animated effects. */
class FloatingIconView(ctx: Context, private val sizePx: Int, private val color: Int, private val effect: Int) : View(ctx) {
    private val pad = (sizePx * 0.45f).toInt()
    private var ph = 0f
    private var anim: ValueAnimator? = null
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = 0xEE000000.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx + pad * 2, sizePx + pad * 2)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { ph = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    private fun withAlpha(a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = sizePx / 2f
        val d = resources.displayMetrics.density
        when (effect) {
            0 -> for (i in 0..1) { // pulse
                val t = (ph + i * 0.5f) % 1f
                ring.color = withAlpha(0.6f * (1 - t)); ring.strokeWidth = 2f * d
                c.drawCircle(cx, cy, r + t * pad, ring)
            }
            1 -> { // breathe
                val k = 0.5f + 0.5f * sin(ph * 2 * PI).toFloat()
                glow.shader = RadialGradient(cx, cy, r + pad, intArrayOf(withAlpha(0.55f * k + 0.1f), 0), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r + pad, glow)
            }
            2 -> { // orbit
                ring.color = withAlpha(0.25f); ring.strokeWidth = 1.2f * d
                c.drawCircle(cx, cy, r + pad * 0.45f, ring)
                val a = ph * 2 * PI
                glow.shader = null; glow.color = color
                c.drawCircle(cx + (r + pad * 0.45f) * cos(a).toFloat(), cy + (r + pad * 0.45f) * sin(a).toFloat(), 3.5f * d, glow)
            }
            3 -> for (i in 0..2) { // ripple
                val t = (ph + i / 3f) % 1f
                ring.color = withAlpha(0.5f * (1 - t)); ring.strokeWidth = (1f + 2f * (1 - t)) * d
                c.drawCircle(cx, cy, r + t * pad * 1.1f, ring)
            }
            else -> { // spin
                ring.color = withAlpha(0.9f); ring.strokeWidth = 2.4f * d; ring.strokeCap = Paint.Cap.ROUND
                val rect = RectF(cx - r - pad * 0.3f, cy - r - pad * 0.3f, cx + r + pad * 0.3f, cy + r + pad * 0.3f)
                c.drawArc(rect, ph * 360f, 110f, false, ring)
                c.drawArc(rect, ph * 360f + 180f, 60f, false, ring)
            }
        }
        c.drawCircle(cx, cy, r, body)
        edge.color = color; edge.strokeWidth = 2f * d
        c.drawCircle(cx, cy, r, edge)
        txt.textSize = r * 0.78f
        c.drawText("-_-", cx, cy - (txt.ascent() + txt.descent()) / 2f, txt)
    }
}

/** The Jarvis overlay: glow and animated bars at the bottom, status text, and an X button at the top. */
class JarvisOverlay(private val ctx: Context, private val onX: () -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val panel = Panel(ctx)
    private val xBtn = XButton(ctx)
    private var added = false

    private class GlowView(ctx: Context) : View(ctx) {
        var level = 0f
        var mode = 0 // 0 listening, 1 thinking, 2 error, 3 text
        private var t = 0f
        private val cur = FloatArray(5) { 10f }
        private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val d = ctx.resources.displayMetrics.density
        private val mult = floatArrayOf(0.25f, 0.55f, 1f, 0.55f, 0.25f)

        override fun onDraw(c: Canvas) {
            t += 0.06f
            val w = width.toFloat(); val h = height.toFloat()
            val pulse = 0.88f + 0.12f * sin(t * 0.8f)
            val core = if (mode == 2) 0xDDFF4F6E.toInt() else 0xDD6C7BFF.toInt()
            val mid = if (mode == 2) 0x66B02A45 else 0x666E4BFF
            glowP.shader = RadialGradient(w / 2f, h, w * pulse, intArrayOf(core, mid, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, glowP)
            if (mode != 3) {
                for (i in 0..4) {
                    val wave = 0.6f + 0.4f * sin(t * 3f + i * 1.3f)
                    val target = when (mode) {
                        0 -> 10f * d + level * mult[i] * 70f * d * wave
                        1 -> 10f * d + (0.5f + 0.5f * sin(t * 2.2f - i * 0.8f)) * 46f * d * mult[i]
                        else -> 10f * d
                    }
                    cur[i] += (target - cur[i]) * 0.3f
                    val x = w / 2f + (i - 2) * 24f * d
                    val y = h * 0.5f
                    barP.alpha = if (mode == 2) 150 else 235
                    c.drawRoundRect(x - 5f * d, y - cur[i] / 2f, x + 5f * d, y + cur[i] / 2f, 5f * d, 5f * d, barP)
                }
            }
            postInvalidateOnAnimation()
        }
    }

    private class Panel(ctx: Context) : FrameLayout(ctx) {
        val glow = GlowView(ctx)
        val title = TextView(ctx)
        val sub = TextView(ctx)
        init {
            val d = ctx.resources.displayMetrics.density
            addView(glow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            title.setTextColor(Color.WHITE); title.textSize = 24f; title.gravity = Gravity.CENTER
            title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            sub.setTextColor(0xFFB9BEDF.toInt()); sub.textSize = 14f; sub.gravity = Gravity.CENTER
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding((28 * d).toInt(), 0, (28 * d).toInt(), (84 * d).toInt())
            }
            col.addView(title); col.addView(sub)
            addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
    }

    private class XButton(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
        private val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = 2.4f * d; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val k = 7f * d
            c.drawCircle(cx, cy, width / 2f, bg)
            c.drawLine(cx - k, cy - k, cx + k, cy + k, ln)
            c.drawLine(cx - k, cy + k, cx + k, cy - k, ln)
        }
    }

    fun show() {
        if (added) return
        val m = ctx.resources.displayMetrics
        val flagsBase = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (m.heightPixels * 0.52f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flagsBase or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT
        )
        pp.gravity = Gravity.BOTTOM
        val xp = WindowManager.LayoutParams(
            (46 * dp).toInt(), (46 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flagsBase, PixelFormat.TRANSLUCENT
        )
        xp.gravity = Gravity.TOP or Gravity.START
        xp.x = (18 * dp).toInt(); xp.y = (46 * dp).toInt()
        xBtn.setOnClickListener { onX() }
        panel.alpha = 0f; panel.translationY = 120 * dp
        xBtn.alpha = 0f
        try {
            wm.addView(panel, pp); wm.addView(xBtn, xp); added = true
        } catch (e: Exception) { return }
        panel.animate().alpha(1f).translationY(0f).setDuration(420).start()
        xBtn.animate().alpha(1f).setDuration(420).start()
        setTitle("")
        setSub("")
    }

    fun setTitle(t: String) { main.post { panel.title.textSize = 24f; panel.title.maxLines = 3; panel.title.text = t } }
    fun setSub(t: String) { main.post { panel.sub.text = t } }
    fun listening() { main.post { panel.glow.mode = 0 } }
    fun thinking() { main.post { panel.glow.mode = 1 } }
    fun level(rms: Float) { panel.glow.level = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
    fun error(t: String) { main.post { panel.glow.mode = 2; panel.title.textSize = 20f; panel.title.text = "Something went wrong"; panel.sub.text = t } }
    fun showText(t: String) {
        main.post { panel.glow.mode = 3; panel.title.textSize = 16f; panel.title.maxLines = 9; panel.title.text = t; panel.sub.text = "" }
    }

    fun close() {
        if (!added) return
        added = false
        panel.animate().alpha(0f).translationY(100 * dp).setDuration(260).start()
        xBtn.animate().alpha(0f).setDuration(200).start()
        main.postDelayed({
            try { wm.removeView(panel) } catch (e: Exception) { }
            try { wm.removeView(xBtn) } catch (e: Exception) { }
        }, 300)
    }
}
