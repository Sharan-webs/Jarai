package com.kitagawh.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

open class SimpleListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    override fun onResults(results: Bundle?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

class CoreService : Service() {

    companion object {
        const val ACT_SYNC = "sync"
        const val ACT_CALL = "call"
        private val WAKE = listOf("jarvis", "jarvish", "jarves", "jarvas", "jervis", "charvis", "garvis", "harvis", "jarwis", "jarviz")
        private val STOPS = listOf("stop", "cancel", "abort", "enough", "quit", "never mind", "nevermind", "stop it", "ok stop", "okay stop", "stop stop")

        fun sync(c: Context) = send(c, ACT_SYNC)
        fun call(c: Context) = send(c, ACT_CALL)

        private fun send(c: Context, a: String) {
            try {
                val i = Intent(c, CoreService::class.java).setAction(a)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { }
        }

        private fun lev(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]
                dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }

        private fun isWakeWord(w: String): Boolean {
            val x = w.lowercase().filter { it.isLetter() }
            return WAKE.contains(x) || (x.length in 5..8 && lev(x, "jarvis") <= 1)
        }

        fun hasWake(t: String) = t.trim().split(Regex("\\s+")).any { isWakeWord(it) }

        fun afterWake(t: String): String {
            val words = t.trim().split(Regex("\\s+"))
            val i = words.indexOfFirst { isWakeWord(it) }
            if (i < 0) return t.trim()
            return words.drop(i + 1).joinToString(" ").trim(' ', ',', '.', '!', '?')
        }

        fun isStop(t: String): Boolean {
            val l = t.lowercase().trim(' ', '.', '!', '?', ',')
            if (l.isEmpty()) return false
            return STOPS.contains(l) || l.startsWith("stop ") || l.startsWith("cancel ") || l.contains("never mind") || l.contains("nevermind")
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var overlay: JarvisOverlay? = null
    private var easy: EasyTouch? = null
    private var tts: TextToSpeech? = null
    private var session = 0
    private var working = false
    private var focus: AudioFocusRequest? = null
    private var observer: ContentObserver? = null
    private val wakeRunnable = Runnable { startWake() }
    private val watchdog = object : Runnable {
        override fun run() {
            checkAccessibility()
            main.postDelayed(this, 10_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
        watchAccessibility()
        main.postDelayed(watchdog, 10_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startFg() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACT_CALL -> callJarvis()
        }
        syncState()
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        stopRec()
        focusOff()
        easy?.hide(); easy = null
        overlay?.close(); overlay = null
        tts?.shutdown()
        observer?.let { contentResolver.unregisterContentObserver(it) }
        super.onDestroy()
    }

    // ---------- notification ----------
    private fun statusText(): String {
        val j = Store.bool(this, "jarvis_on"); val e = Store.bool(this, "et_on")
        return when {
            j && e -> "Jarvis and Easy Touch are on"
            j -> "Jarvis is listening for your call"
            e -> "Easy Touch is on"
            else -> "Standing by"
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "core")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Kitagawh is running")
            .setContentText(statusText())
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("core", "Kitagawh running", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("alert", "Kitagawh alerts", NotificationManager.IMPORTANCE_HIGH))
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    // ---------- accessibility guard (instant + every 10 seconds) ----------
    private fun checkAccessibility() {
        if (!Store.bool(this, "logged_in")) return
        if (!Perms.accessOn(this)) {
            if (!Master.keepAccessibility(this)) notifyAccessOff()
        } else {
            try { getSystemService(NotificationManager::class.java).cancel(2) } catch (e: Exception) { }
        }
    }

    private fun watchAccessibility() {
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) { checkAccessibility() }
        }
        observer = obs
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, obs)
    }

    private fun notifyAccessOff() {
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "alert")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Accessibility was turned off")
            .setContentText("Jarvis is blind without it. Tap to switch it back on.")
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    // ---------- state ----------
    private fun syncState() {
        if (Store.bool(this, "jarvis_on")) {
            if (overlay == null && rec == null) startWake()
        } else if (overlay == null) {
            stopRec()
        }
        if (Store.bool(this, "et_on") && Settings.canDrawOverlays(this)) {
            if (easy == null) easy = EasyTouch(this) { runFeature(it) }
            easy?.show()
        } else {
            easy?.hide(); easy = null
        }
        try { getSystemService(NotificationManager::class.java).notify(1, buildNotification()) } catch (e: Exception) { }
    }

    private fun toast(t: String) = main.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }

    private fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "kita") }

    private fun focusOn() {
        try {
            val am = getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                ).build()
            am.requestAudioFocus(req)
            focus = req
        } catch (e: Exception) { }
    }

    private fun focusOff() {
        try { focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } } catch (e: Exception) { }
        focus = null
    }

    // ---------- Easy Touch features ----------
    private fun runFeature(id: String) {
        when (id) {
            "torch" -> try { Actions.torch(this, !Features.torchOn) } catch (e: Exception) { toast("Could not reach the torch") }
            "call" -> callJarvis()
            "screen_search" -> screenSearch()
            "settings" -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("route", "et_ui")
            )
            else -> {
                val a = KitaAccessibilityService.instance
                if (a == null) { toast("Turn on Accessibility first"); return }
                val code = when (id) {
                    "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                    "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                    "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                    "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    "screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                    "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    else -> return
                }
                a.performGlobalAction(code)
            }
        }
    }

    // ---------- speech ----------
    private fun recIntent(long: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (long) 1800L else 1200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (long) 1500L else 1000L)
    }

    private fun stopRec() {
        rec?.let { try { it.cancel(); it.destroy() } catch (e: Exception) { } }
        rec = null
    }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun wakeHit(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { hasWake(it) }

    private fun restartWake(delay: Long) {
        main.removeCallbacks(wakeRunnable)
        main.postDelayed(wakeRunnable, delay)
    }

    private fun openOverlay(): Int {
        main.removeCallbacks(wakeRunnable)
        val sid = ++session
        working = false
        overlay?.close()
        overlay = JarvisOverlay(this) { cancelSession() }.also { it.show() }
        focusOn()
        return sid
    }

    /** Always-on listener. It reacts to the first partial words, so the overlay opens instantly. */
    private fun startWake() {
        if (!Store.bool(this, "jarvis_on") || overlay != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        stopRec()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : SimpleListener() {
            var woke = false

            private fun wakeUp() {
                woke = true
                openOverlay()
                overlay?.setTitle("What's up bruh!!")
                overlay?.listening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!woke && wakeHit(partialResults) != null) wakeUp()
            }

            override fun onRmsChanged(rmsdB: Float) { if (woke) overlay?.level(rmsdB) }

            override fun onResults(results: Bundle?) {
                val hit = wakeHit(results)
                if (hit == null) {
                    if (woke) cancelSession() else restartWake(250)
                    return
                }
                if (!woke) wakeUp()
                val sid = session
                val cmd = afterWake(hit)
                if (cmd.isBlank()) listenCommand(sid) else runTask(cmd, sid)
            }

            override fun onError(error: Int) {
                if (woke) {
                    if (overlay != null) listenCommand(session)
                } else restartWake(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 350)
            }
        })
        try { r.startListening(recIntent(false)) } catch (e: Exception) { restartWake(1500) }
    }

    // ---------- sessions ----------
    private fun callJarvis() {
        if (!Store.bool(this, "jarvis_on")) { toast("Power on Jarvis first"); return }
        stopRec()
        val sid = openOverlay()
        overlay?.setTitle("What's up bruh!!")
        overlay?.listening()
        main.postDelayed({ if (sid == session) listenCommand(sid) }, 450)
    }

    private fun speechError(e: Int) = when (e) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service needs internet."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        else -> "Speech error $e."
    }

    private fun listenCommand(sid: Int) {
        if (sid != session) return
        stopRec()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        var retried = false
        r.setRecognitionListener(object : SimpleListener() {
            override fun onRmsChanged(rmsdB: Float) { overlay?.level(rmsdB) }
            override fun onPartialResults(partialResults: Bundle?) { first(partialResults)?.let { overlay?.setSub(it) } }
            override fun onResults(results: Bundle?) {
                if (sid != session) return
                val t = first(results)?.let { if (hasWake(it)) afterWake(it) else it }
                if (t.isNullOrBlank()) failSession("I did not catch that.") else runTask(t, sid)
            }

            override fun onError(error: Int) {
                if (sid != session) return
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (quiet && !retried) {
                    retried = true
                    main.postDelayed({ if (sid == session) listenCommand(sid) }, 200)
                } else failSession(speechError(error))
            }
        })
        try { r.startListening(recIntent(true)) } catch (e: Exception) { failSession("Could not start listening.") }
    }

    private fun runTask(cmd: String, sid: Int) {
        if (sid != session) return
        working = true
        overlay?.setTitle(cmd)
        overlay?.setSub("Working on it. Say stop or give me something new.")
        overlay?.thinking()
        listenInterrupt(sid)
        Thread {
            val err = Agent.run(
                this@CoreService, cmd,
                { s -> main.post { if (sid == session) overlay?.setSub(s) } },
                { s -> main.post { if (sid == session) speak(s) } },
                { sid != session }
            )
            main.post {
                if (sid == session) {
                    working = false
                    stopRec()
                    if (err == null) {
                        overlay?.setSub("Done")
                        main.postDelayed({ if (sid == session) endSession() }, 1100)
                    } else failSession(err)
                }
            }
        }.start()
    }

    /** Keeps listening on the side while a task runs, so you can say stop or give a new command. */
    private fun listenInterrupt(sid: Int) {
        if (sid != session || !working || overlay == null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        stopRec()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : SimpleListener() {
            override fun onRmsChanged(rmsdB: Float) { if (tts?.isSpeaking != true) overlay?.level(rmsdB) }

            override fun onPartialResults(partialResults: Bundle?) {
                if (sid != session || tts?.isSpeaking == true) return
                val t = first(partialResults) ?: return
                val c = if (hasWake(t)) afterWake(t) else t
                if (isStop(c)) stopTask(sid)
            }

            override fun onResults(results: Bundle?) {
                if (sid != session || !working) return
                if (tts?.isSpeaking == true) { again(sid); return }
                val t = first(results)
                if (t.isNullOrBlank()) { again(sid); return }
                val c = if (hasWake(t)) afterWake(t) else t
                when {
                    isStop(c) -> stopTask(sid)
                    c.length >= 2 -> {
                        val nsid = ++session
                        overlay?.setTitle(c)
                        runTask(c, nsid)
                    }
                    else -> again(sid)
                }
            }

            override fun onError(error: Int) { again(sid) }

            private fun again(s: Int) {
                main.postDelayed({ if (s == session && working) listenInterrupt(s) }, 200)
            }
        })
        try { r.startListening(recIntent(false)) } catch (e: Exception) { }
    }

    private fun stopTask(sid: Int) {
        if (sid != session) return
        session++
        working = false
        stopRec()
        tts?.stop()
        overlay?.setTitle("Stopped")
        overlay?.setSub("Okay, I stopped.")
        overlay?.listening()
        val s2 = session
        main.postDelayed({ if (s2 == session) endSession() }, 1000)
    }

    private fun failSession(msg: String) {
        val sid = session
        working = false
        stopRec()
        overlay?.error(msg)
        main.postDelayed({ if (sid == session) endSession() }, 3800)
    }

    private fun endSession() {
        session++
        working = false
        focusOff()
        overlay?.close(); overlay = null
        restartWake(700)
    }

    private fun cancelSession() {
        session++
        working = false
        stopRec()
        tts?.stop()
        focusOff()
        overlay?.close(); overlay = null
        restartWake(700)
    }

    // ---------- Screen search ----------
    private fun screenSearch() {
        val acc = KitaAccessibilityService.instance
        if (acc == null) { toast("Turn on Accessibility first"); return }
        stopRec()
        working = false
        overlay?.close(); overlay = null
        val sid = ++session
        main.postDelayed({
            acc.screenshotBase64 { b64 ->
                main.post {
                    if (sid != session) return@post
                    overlay = JarvisOverlay(this) { cancelSession() }.also { it.show() }
                    if (b64 == null) {
                        failSession("Screen search needs Android 11 or newer with Accessibility on.")
                        return@post
                    }
                    overlay?.setTitle("Looking at your screen")
                    overlay?.setSub("")
                    overlay?.thinking()
                    Thread {
                        var text: String? = null
                        var err: String? = null
                        try { text = Agent.describeScreen(this@CoreService, b64) } catch (e: Exception) { err = e.message ?: "Could not read the screen." }
                        main.post {
                            if (sid != session) return@post
                            val t = text
                            if (t != null) {
                                overlay?.showText(t)
                                main.postDelayed({ if (sid == session) endSession() }, 25000)
                            } else failSession(err ?: "Screen search failed.")
                        }
                    }.start()
                }
            }
        }, 600)
    }
}
