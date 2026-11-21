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

/** The Jarvis overlay: a rotating sphere with radial and bottom spectrum, status text, and an X button at the top. */
class JarvisOverlay(private val ctx: Context, private val onX: () -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val panel = Panel(ctx)
    private val xBtn = XButton(ctx)
    private var added = false

    private class HudView(ctx: Context) : View(ctx) {
        var level = 0f
        var mode = 0 // 0 listening, 1 working, 2 error, 3 text
        private val d = ctx.resources.displayMetrics.density
        private var t = 0f
        private var rot = 0f
        private var shift = 0f
        private var sLevel = 0f
        private val n = 340
        private val pts = FloatArray(n * 3)
        private val bars = 72
        private val amp = FloatArray(bars)
        private val amp2 = FloatArray(44)
        private val pPoint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val pDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * d, 9f * d), 0f)
        }
        private val oval = RectF()

        init {
            val g = 2.399963f
            for (i in 0 until n) {
                val y = 1f - 2f * (i + 0.5f) / n
                val r = kotlin.math.sqrt(1f - y * y)
                pts[i * 3] = cos(g * i) * r; pts[i * 3 + 1] = y; pts[i * 3 + 2] = sin(g * i) * r
            }
        }

        private fun withA(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
        private fun mix(a: Int, b: Int, f: Float): Int {
            val k = f.coerceIn(0f, 1f)
            val r = ((a shr 16 and 255) * (1 - k) + (b shr 16 and 255) * k).toInt()
            val g = ((a shr 8 and 255) * (1 - k) + (b shr 8 and 255) * k).toInt()
            val bl = ((a and 255) * (1 - k) + (b and 255) * k).toInt()
            return (255 shl 24) or (r shl 16) or (g shl 8) or bl
        }

        override fun onDraw(c: Canvas) {
            t += 0.05f
            val w = width.toFloat(); val h = height.toFloat()
            sLevel += (level - sLevel) * 0.25f
            shift += ((if (mode == 3) 1f else 0f) - shift) * 0.08f
            val c1 = if (mode == 2) 0xFFFF5C7A.toInt() else 0xFF7C8CFF.toInt()
            val c2 = if (mode == 2) 0xFFFFA06C.toInt() else 0xFF4FD8FF.toInt()

            // bottom glow
            pGlow.shader = RadialGradient(w / 2f, h, w * 0.95f * (0.9f + 0.1f * sin(t * 0.8f)),
                intArrayOf(withA(c1, 0xCC), withA(c1, 0x44), 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, pGlow)

            val cx = w / 2f
            val cy = h * (0.36f - 0.18f * shift)
            val rad = min(w * 0.24f, h * 0.17f) * (1f - 0.35f * shift)
            rot += when (mode) { 0 -> 0.012f + sLevel * 0.05f; 1 -> 0.045f; 2 -> 0.004f; else -> 0.01f }

            // halo
            pGlow.shader = RadialGradient(cx, cy, rad * 2.2f,
                intArrayOf(withA(c1, (90 * (0.6f + 0.4f * sLevel)).toInt()), withA(c2, 0x20), 0),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rad * 2.2f, pGlow)

            // radial spectrum
            val r0 = rad * 1.32f
            for (i in 0 until bars) {
                val target = when (mode) {
                    0 -> sLevel * (0.3f + 0.7f * abs(sin(t * 2.1f + i * 0.45f)) * abs(cos(t * 1.3f + i * 0.21f))) + 0.04f
                    1 -> (0.5f + 0.5f * sin(t * 2.6f - i * 0.25f)) * 0.45f + 0.05f
                    else -> 0.05f
                }
                amp[i] += (target - amp[i]) * 0.35f
                val a = i * 2f * PI.toFloat() / bars + rot * 0.3f
                val len = rad * (0.08f + 0.55f * amp[i])
                pLine.color = withA(mix(c1, c2, i.toFloat() / bars), 215)
                pLine.strokeWidth = 2.8f * d
                c.drawLine(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * (r0 + len), cy + sin(a) * (r0 + len), pLine)
            }

            // dashed rings
            c.save(); c.rotate(t * 9f, cx, cy)
            pDash.color = withA(c2, 120); pDash.strokeWidth = 1.6f * d
            c.drawCircle(cx, cy, rad * 1.88f, pDash)
            c.restore()
            c.save(); c.rotate(-t * 14f, cx, cy)
            pRing.color = withA(c1, 190); pRing.strokeWidth = 2.2f * d; pRing.strokeCap = Paint.Cap.ROUND
            oval.set(cx - rad * 1.98f, cy - rad * 1.98f, cx + rad * 1.98f, cy + rad * 1.98f)
            c.drawArc(oval, 0f, 46f, false, pRing); c.drawArc(oval, 120f, 28f, false, pRing); c.drawArc(oval, 240f, 62f, false, pRing)
            c.restore()

            // orbit ovals
            pRing.color = withA(c2, 80); pRing.strokeWidth = 1.2f * d
            for (k in 0..1) {
                c.save(); c.rotate((if (k == 0) 1 else -1) * (rot * 40f) + k * 60f, cx, cy)
                oval.set(cx - rad * 1.16f, cy - rad * 0.3f, cx + rad * 1.16f, cy + rad * 0.3f)
                c.drawOval(oval, pRing)
                c.restore()
            }

            // sphere points
            val cr = cos(rot); val sr = sin(rot)
            val tilt = 0.4f + 0.12f * sin(t * 0.4f); val ct = cos(tilt); val st = sin(tilt)
            val re = rad * (1f + 0.07f * sLevel)
            for (i in 0 until n) {
                val x = pts[i * 3]; val y = pts[i * 3 + 1]; val z = pts[i * 3 + 2]
                val x1 = x * cr + z * sr; val z1 = -x * sr + z * cr
                val y2 = y * ct - z1 * st; val z2 = y * st + z1 * ct
                val depth = (z2 + 1f) / 2f
                pPoint.color = withA(mix(c1, c2, depth), (50 + 205 * depth).toInt())
                c.drawCircle(cx + x1 * re, cy + y2 * re, (0.7f + 2.4f * depth) * d, pPoint)
            }

            // bottom spectrum
            if (shift < 0.6f) {
                val m = amp2.size
                val by = h * 0.72f
                val bw = w * 0.62f / m
                val sx = w / 2f - w * 0.31f
                for (i in 0 until m) {
                    val env = 1f - abs(i - m / 2f) / (m / 2f) * 0.7f
                    val target = when (mode) {
                        0 -> sLevel * (0.35f + 0.65f * abs(sin(t * 2.4f + i * 0.5f))) * env + 0.03f
                        1 -> (0.5f + 0.5f * sin(t * 3f - i * 0.35f)) * 0.5f * env + 0.03f
                        else -> 0.03f
                    }
                    amp2[i] += (target - amp2[i]) * 0.35f
                    val hh = 4f * d + amp2[i] * 56f * d
                    pLine.color = withA(mix(c1, c2, i.toFloat() / m), ((1f - shift * 1.6f) * 230).toInt())
                    pLine.strokeWidth = bw * 0.55f
                    val x = sx + i * bw + bw / 2f
                    c.drawLine(x, by - hh / 2f, x, by + hh / 2f, pLine)
                }
            }
            postInvalidateOnAnimation()
        }
    }

    private class Panel(ctx: Context) : FrameLayout(ctx) {
        val hud = HudView(ctx)
        val label = TextView(ctx)
        val title = TextView(ctx)
        val sub = TextView(ctx)
        init {
            val d = ctx.resources.displayMetrics.density
            addView(hud, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            label.setTextColor(0xFF4FD8FF.toInt()); label.textSize = 11f; label.gravity = Gravity.CENTER
            label.typeface = Typeface.MONOSPACE; label.letterSpacing = 0.35f
            title.setTextColor(Color.WHITE); title.textSize = 24f; title.gravity = Gravity.CENTER
            title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); title.letterSpacing = 0.04f
            sub.setTextColor(0xFFB9BEDF.toInt()); sub.textSize = 13f; sub.gravity = Gravity.CENTER
            sub.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding((28 * d).toInt(), 0, (28 * d).toInt(), (52 * d).toInt())
            }
            col.addView(label); col.addView(title); col.addView(sub)
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
            WindowManager.LayoutParams.MATCH_PARENT, (m.heightPixels * 0.66f).toInt(),
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
        panel.alpha = 0f; panel.translationY = 140 * dp
        xBtn.alpha = 0f
        try {
            wm.addView(panel, pp); wm.addView(xBtn, xp); added = true
        } catch (e: Exception) { return }
        panel.animate().alpha(1f).translationY(0f).setDuration(480).start()
        xBtn.animate().alpha(1f).setDuration(480).start()
        setTitle("")
        setSub("")
        setLabel("listening")
    }

    private fun setLabel(t: String) { main.post { panel.label.text = t } }
    fun setTitle(t: String) { main.post { panel.title.textSize = 24f; panel.title.maxLines = 3; panel.title.text = t } }
    fun setSub(t: String) { main.post { panel.sub.text = t } }
    fun listening() { main.post { panel.hud.mode = 0; panel.label.text = "listening" } }
    fun thinking() { main.post { panel.hud.mode = 1; panel.label.text = "working" } }
    fun level(rms: Float) { panel.hud.level = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
    fun error(t: String) {
        main.post { panel.hud.mode = 2; panel.label.text = "error"; panel.title.textSize = 20f; panel.title.text = "Something went wrong"; panel.sub.text = t }
    }
    fun showText(t: String) {
        main.post { panel.hud.mode = 3; panel.label.text = "screen search"; panel.title.textSize = 16f; panel.title.maxLines = 9; panel.title.text = t; panel.sub.text = "" }
    }

    fun close() {
        if (!added) return
        added = false
        panel.animate().alpha(0f).translationY(110 * dp).setDuration(280).start()
        xBtn.animate().alpha(0f).setDuration(200).start()
        main.postDelayed({
            try { wm.removeView(panel) } catch (e: Exception) { }
            try { wm.removeView(xBtn) } catch (e: Exception) { }
        }, 320)
    }
}
