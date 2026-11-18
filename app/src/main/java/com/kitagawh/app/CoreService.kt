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
        private val WAKE = listOf("jarvis", "jarvish", "jarves", "jarvas", "jervis")

        fun sync(c: Context) = send(c, ACT_SYNC)
        fun call(c: Context) = send(c, ACT_CALL)

        private fun send(c: Context, a: String) {
            try {
                val i = Intent(c, CoreService::class.java).setAction(a)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var overlay: JarvisOverlay? = null
    private var easy: EasyTouch? = null
    private var tts: TextToSpeech? = null
    private var session = 0
    private var observer: ContentObserver? = null
    private val wakeRunnable = Runnable { startWake() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
        watchAccessibility()
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

    private fun watchAccessibility() {
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                if (!Store.bool(this@CoreService, "logged_in")) return
                if (!Perms.accessOn(this@CoreService) && !Master.keepAccessibility(this@CoreService)) notifyAccessOff()
            }
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
            .setAutoCancel(true)
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
    private fun recIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    private fun stopRec() {
        rec?.let { try { it.cancel(); it.destroy() } catch (e: Exception) { } }
        rec = null
    }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun wakeHit(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { r -> WAKE.any { r.lowercase().contains(it) } }

    private fun afterWake(t: String): String {
        val l = t.lowercase()
        for (w in WAKE) {
            val i = l.indexOf(w)
            if (i >= 0) return t.substring(i + w.length).trim(' ', ',', '.', '!', '?')
        }
        return ""
    }

    private fun restartWake(delay: Long) {
        main.removeCallbacks(wakeRunnable)
        main.postDelayed(wakeRunnable, delay)
    }

    private fun startWake() {
        if (!Store.bool(this, "jarvis_on") || overlay != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        stopRec()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : SimpleListener() {
            override fun onResults(results: Bundle?) {
                val hit = wakeHit(results)
                if (hit != null) beginSession(afterWake(hit)) else restartWake(250)
            }

            override fun onError(error: Int) {
                restartWake(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 350)
            }
        })
        try { r.startListening(recIntent()) } catch (e: Exception) { restartWake(1500) }
    }

    // ---------- sessions ----------
    private fun callJarvis() {
        if (!Store.bool(this, "jarvis_on")) { toast("Power on Jarvis first"); return }
        beginSession(null)
    }

    private fun beginSession(cmd: String?) {
        stopRec()
        main.removeCallbacks(wakeRunnable)
        val sid = ++session
        overlay?.close()
        overlay = JarvisOverlay(this) { cancelSession() }.also { it.show() }
        if (!cmd.isNullOrBlank()) {
            runTask(cmd, sid)
        } else {
            overlay?.setTitle("What's up bruh!!")
            overlay?.listening()
            main.postDelayed({ if (sid == session) listenCommand(sid) }, 450)
        }
    }

    private fun speechError(e: Int) = when (e) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service needs internet."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        else -> "Speech error $e."
    }

    private fun listenCommand(sid: Int) {
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : SimpleListener() {
            override fun onRmsChanged(rmsdB: Float) { overlay?.level(rmsdB) }
            override fun onPartialResults(partialResults: Bundle?) { first(partialResults)?.let { overlay?.setSub(it) } }
            override fun onResults(results: Bundle?) {
                if (sid != session) return
                val t = first(results)
                if (t.isNullOrBlank()) failSession("I did not catch that.") else runTask(t, sid)
            }

            override fun onError(error: Int) { if (sid == session) failSession(speechError(error)) }
        })
        try { r.startListening(recIntent()) } catch (e: Exception) { failSession("Could not start listening.") }
    }

    private fun runTask(cmd: String, sid: Int) {
        stopRec()
        overlay?.setTitle(cmd)
        overlay?.setSub("Working on it")
        overlay?.thinking()
        Thread {
            val err = Agent.run(
                this@CoreService, cmd,
                { s -> main.post { if (sid == session) overlay?.setSub(s) } },
                { s -> main.post { if (sid == session) speak(s) } },
                { sid != session }
            )
            main.post {
                if (sid == session) {
                    if (err == null) {
                        overlay?.setSub("Done")
                        main.postDelayed({ if (sid == session) endSession() }, 1100)
                    } else failSession(err)
                }
            }
        }.start()
    }

    private fun failSession(msg: String) {
        val sid = session
        overlay?.error(msg)
        main.postDelayed({ if (sid == session) endSession() }, 3800)
    }

    private fun endSession() {
        session++
        overlay?.close(); overlay = null
        restartWake(700)
    }

    private fun cancelSession() {
        session++
        stopRec()
        tts?.stop()
        overlay?.close(); overlay = null
        restartWake(700)
    }

    // ---------- Screen search ----------
    private fun screenSearch() {
        val acc = KitaAccessibilityService.instance
        if (acc == null) { toast("Turn on Accessibility first"); return }
        stopRec()
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
                            if (text != null) {
                                overlay?.showText(text!!)
                                main.postDelayed({ if (sid == session) endSession() }, 25000)
                            } else failSession(err ?: "Screen search failed.")
                        }
                    }.start()
                }
            }
        }, 600)
    }
}
