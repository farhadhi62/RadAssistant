package com.example.radassistant

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.*
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log

/** Best-effort phrase detector. Android speech services may stream audio to a provider. */
class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var restarts = 0
    private val channel = "rad_wake"
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "شنود راد", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("راد فعال است").setContentText("تشخیص آزمایشی «هی راد»؛ برای توقف، برنامهٔ راد را باز کنید.").setOngoing(true).build()
        startForeground(41, notification)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else stopSelf()
    }
    private fun startListening() {
        if (stopped || !SpeechRecognizer.isRecognitionAvailable(this)) { if (!stopped) handler.postDelayed({ startListening() }, 5000); return }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { scheduleRestart() }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.joinToString(" ").orEmpty()
                        if (text.contains("هی راد") || text.contains("هی‌راد") || text.contains("hey rad", true)) {
                            val n = getSystemService(NotificationManager::class.java)
                            n.notify(41, Notification.Builder(this@WakeWordService, channel).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("راد صدایتان را شنید").setContentText("برای فرمان، برنامهٔ راد را باز کنید.").setAutoCancel(true).build())
                        }
                        scheduleRestart()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                sr.startListening(intent)
            }
        } catch (e: Exception) { Log.w("RadWake", "Recognizer restart failed", e); scheduleRestart() }
    }
    private fun scheduleRestart() {
        if (stopped) return
        restarts++
        val delay = if (restarts > 10) 8000L else 1200L
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startListening() }, delay)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { stopped = true; handler.removeCallbacksAndMessages(null); recognizer?.cancel(); recognizer?.destroy(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
