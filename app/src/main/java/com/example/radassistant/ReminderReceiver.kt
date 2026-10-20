package com.example.radassistant

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("rad_reminders", "یادآورهای راد", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(context, "rad_reminders")
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("یادآور راد").setContentText(intent.getStringExtra("text") ?: "یادآوری").setAutoCancel(true).build())
    }
}
