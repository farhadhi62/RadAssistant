package com.example.radassistant

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val permissionRequest = 101
    private val contactsRequest = 102
    private var pendingContactName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createReminderChannel()
        buildUi()
        requestStartupPermissions()
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24); layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(this).apply { text = "راد"; textSize = 34f; gravity = Gravity.CENTER; setPadding(0,0,0,10) }
        val subtitle = TextView(this).apply { text = "دستیار صوتی فارسی — برای اجراهای واقعی، سرویس‌های گوشی را باز می‌کند."; textSize = 16f; gravity = Gravity.CENTER; setPadding(0,0,0,18) }
        status = TextView(this).apply { text = "آماده‌ام. فرمان را بنویسید یا صحبت کنید."; textSize = 15f; gravity = Gravity.CENTER; setPadding(8,12,8,12) }
        input = EditText(this).apply { hint = "مثلاً: در اینترنت جستجو کن..."; textSize = 17f; setSingleLine(false); minLines = 2; textDirection = View.TEXT_DIRECTION_RTL; gravity = Gravity.RIGHT }
        val send = Button(this).apply { text = "اجرای فرمان"; setOnClickListener { handleCommand(input.text.toString()) } }
        val mic = Button(this).apply { text = "🎙 صحبت با راد"; setOnClickListener { startOneShotRecognition() } }
        val wake = Button(this).apply { text = "فعال‌کردن شنود «هی راد»"; setOnClickListener { startWakeService() } }
        val stop = Button(this).apply { text = "توقف شنود پس‌زمینه"; setOnClickListener { stopService(Intent(this@MainActivity, WakeWordService::class.java)); say("شنود متوقف شد.") } }
        val notifications = Button(this).apply { text = "تنظیم دسترسی اعلان‌ها"; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
        listOf(title, subtitle, status, input, send, mic, wake, stop, notifications).forEach { layout.addView(it, LinearLayout.LayoutParams(-1, if (it == input) 110 else LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 7 }) }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), permissionRequest)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == contactsRequest) {
            val name = pendingContactName; pendingContactName = null
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED && name != null) findContact(name)
            else say("اجازهٔ مخاطبین داده نشد.")
        }
    }

    private fun startOneShotRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestStartupPermissions(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("تشخیص گفتار روی این دستگاه در دسترس نیست."); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { say("بفرمایید.") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "در حال پردازش گفتار…" }
                override fun onError(error: Int) { status.text = "گفتار دریافت نشد؛ دوباره تلاش کنید. (کد $error)" }
                override fun onResults(results: Bundle?) { val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); input.setText(text); if (text.isNotBlank()) handleCommand(text) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            sr.startListening(speechIntent())
        }
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    private fun handleCommand(raw: String) {
        val command = raw.trim().replace('ي','ی').replace('ك','ک')
        if (command.isBlank()) return
        status.text = "فرمان: $command"
        val lower = command.lowercase(Locale.ROOT)
        when {
            lower.contains("اعلان") || lower.contains("نوتیفیکیشن") -> showNotificationSummary()
            lower.contains("یادآور") || lower.contains("یادآوری") -> askReminder(command)
            lower.contains("آلارم") || lower.contains("زنگ") -> setAlarm(command)
            lower.contains("پیامک") || lower.contains("اس ام اس") || lower.contains("اس‌ام‌اس") -> composeSms(command)
            lower.contains("تماس") || lower.contains("زنگ بزن") -> requestContact(command)
            lower.contains("مسیریابی") || lower.contains("نقشه") || lower.contains("برو به") -> navigate(command)
            lower.contains("موزیک") || lower.contains("آهنگ") || lower.contains("موسیقی") -> playMusic(command)
            lower.contains("جستجو") || lower.contains("بگرد") || lower.contains("چیست") || lower.contains("چیه") || lower.startsWith("چرا") || lower.startsWith("چطور") || lower.startsWith("کیست") -> searchWeb(command)
            else -> { say("برای این درخواست پاسخ محلی ندارم؛ جستجوی وب را باز می‌کنم."); searchWeb(command) }
        }
    }

    private fun askReminder(command: String) {
        val edit = EditText(this).apply { setText(command.replace(Regex("یادآور(ی)?"), "").trim()); hint = "متن یادآور"; textDirection = View.TEXT_DIRECTION_RTL }
        AlertDialog.Builder(this).setTitle("تنظیم یادآور").setMessage("یادآور یک دقیقهٔ دیگر اعلان می‌شود. متن را ویرایش کنید:").setView(edit)
            .setNegativeButton("لغو", null).setPositiveButton("تنظیم") { _, _ ->
                val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, ReminderReceiver::class.java).putExtra("text", edit.text.toString().ifBlank { "یادآوری راد" })
                val pi = PendingIntent.getBroadcast(this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, pi)
                say("یادآور برای یک دقیقهٔ دیگر تنظیم شد.")
            }.show()
    }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("rad_reminders", "یادآورهای راد", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun setAlarm(command: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_MESSAGE, command).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        launchSafely(intent, "برنامهٔ ساعت برای تنظیم زنگ باز می‌شود.")
    }

    private fun requestContact(command: String) {
        val name = command.replace(Regex("(?i)(لطفا|لطفاً|راد|با|به|تماس|زنگ|بزن|بگیر|شماره)"), " ").trim()
        if (name.isBlank()) { say("نام مخاطب را هم بگویید."); return }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { pendingContactName = name; requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), contactsRequest) }
        else findContact(name)
    }

    private fun findContact(name: String) {
        var number: String? = null
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use { c -> if (c.moveToFirst()) number = c.getString(1) }
        val found = number
        if (found == null) { say("مخاطبی با این نام پیدا نشد."); return }
        AlertDialog.Builder(this).setTitle("تأیید تماس").setMessage("تماس با $name، شمارهٔ $found؟ برنامهٔ شماره‌گیر باز می‌شود و تماس خودکار انجام نمی‌شود.")
            .setNegativeButton("لغو", null).setPositiveButton("بازکردن شماره‌گیر") { _, _ -> launchSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$found")), "") }.show()
    }

    private fun composeSms(command: String) {
        val edit = EditText(this).apply { hint = "شمارهٔ گیرنده (اختیاری)"; inputType = 3 }
        AlertDialog.Builder(this).setTitle("نوشتن پیامک").setMessage("پیام به‌صورت پیش‌نویس باز می‌شود و ارسال خودکار ندارد.").setView(edit)
            .setNegativeButton("لغو", null).setPositiveButton("ادامه") { _, _ ->
                val recipient = edit.text.toString().trim()
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).putExtra("sms_body", command)
                launchSafely(intent, "برنامهٔ پیامک باز می‌شود؛ ارسال را خودتان تأیید کنید.")
            }.show()
    }

    private fun showNotificationSummary() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().contains(packageName)
        if (!enabled) { AlertDialog.Builder(this).setTitle("دسترسی اعلان‌ها").setMessage("برای خواندن اعلان‌ها، دسترسی را در تنظیمات فعال کنید. به‌طور پیش‌فرض فقط تعداد و نام برنامه‌ها نمایش داده می‌شود؛ متن حساس اعلان‌ها خوانده یا ذخیره نمی‌شود.").setPositiveButton("تنظیمات") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.setNegativeButton("لغو", null).show(); return }
        val summary = RadNotificationListener.summary()
        say(summary)
        AlertDialog.Builder(this).setTitle("خلاصهٔ اعلان‌ها").setMessage(summary).setPositiveButton("باشه", null).show()
    }

    private fun playMusic(command: String) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        if (intent.resolveActivity(packageManager) != null) launchSafely(intent, "برنامهٔ موسیقی باز می‌شود.")
        else launchSafely(Intent(MediaStoreIntent.ACTION_MEDIA_SEARCH).putExtra("query", command), "درخواست جستجوی موسیقی ارسال شد.")
    }

    private fun navigate(command: String) {
        val place = command.replace(Regex("(?i)(راد|مسیریابی|نقشه|برو به|مسیر|برای)"), " ").trim().ifBlank { "" }
        val uri = if (place.isBlank()) Uri.parse("geo:0,0?q=") else Uri.parse("geo:0,0?q=" + Uri.encode(place))
        launchSafely(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"), "نقشه باز می‌شود.", fallback = Intent(Intent.ACTION_VIEW, uri))
    }

    private fun searchWeb(command: String) {
        val query = command.replace(Regex("(?i)(راد|لطفا|لطفاً|در اینترنت|اینترنت|جستجو کن|جستجو|بگرد)"), " ").trim().ifBlank { command }
        launchSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))), "جستجوی وب باز می‌شود.")
    }

    private fun launchSafely(intent: Intent, message: String, fallback: Intent? = null) {
        try { if (intent.resolveActivity(packageManager) != null) { startActivity(intent); if (message.isNotBlank()) say(message) } else if (fallback != null) startActivity(fallback) else say("برنامهٔ مناسب روی دستگاه پیدا نشد.") }
        catch (_: Exception) { say("بازکردن برنامه ممکن نشد.") }
    }

    private fun startWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestStartupPermissions(); return }
        val intent = Intent(this, WakeWordService::class.java)
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent); say("تلاش برای فعال‌کردن شنود انجام شد. محدودیت‌های سازندهٔ گوشی ممکن است آن را متوقف کند.") }
        catch (_: Exception) { say("شروع سرویس ممکن نشد؛ برنامه را باز نگه دارید و مجوز میکروفون را بررسی کنید.") }
    }

    private fun say(text: String) { status.text = text; tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rad") }
    override fun onInit(statusCode: Int) { if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale("fa", "IR") }
    override fun onDestroy() { recognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }

    private object MediaStoreIntent { const val ACTION_MEDIA_SEARCH = "android.intent.action.MEDIA_SEARCH" }
}
