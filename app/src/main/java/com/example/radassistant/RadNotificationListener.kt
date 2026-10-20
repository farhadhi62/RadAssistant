package com.example.radassistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/** Keeps only package identifiers/count, deliberately never stores notification text. */
class RadNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sbn?.let { packages[it.packageName] = true } }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* summary is intentionally an in-memory, non-content hint */ }
    companion object {
        private val packages = ConcurrentHashMap<String, Boolean>()
        fun summary(): String = if (packages.isEmpty()) "اعلان ثبت‌شده‌ای در این نشست در دسترس نیست. متن اعلان‌ها خوانده یا ذخیره نمی‌شوند." else "اعلان از ${packages.size} برنامه دریافت شده است: ${packages.keys.joinToString()} . متن اعلان‌ها خوانده یا ذخیره نمی‌شوند."
    }
}
