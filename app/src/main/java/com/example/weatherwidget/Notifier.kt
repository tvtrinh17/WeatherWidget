package com.example.weatherwidget

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

object Notifier {
    private const val CH = "lunar_events"

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, title: String, text: String): Boolean {
        if (!canPost(ctx)) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Sự kiện âm lịch", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, EventsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
        return true
    }

    /** Quét 7 ngày tới, báo các sự kiện đúng ngày hoặc đúng số ngày báo trước. Mỗi mốc chỉ báo 1 lần. */
    fun check(ctx: Context) {
        val p = Prefs(ctx)
        val sent = p.sent.toMutableSet()
        if (sent.size > 300) sent.clear()
        for (h in Events.scan(ctx, 7)) {
            if (h.offset != 0 && h.offset != h.event.remindBefore) continue
            val key = "${h.event.id}:${h.solar.get(Calendar.YEAR)}-${h.solar.get(Calendar.DAY_OF_YEAR)}:${h.offset}"
            if (key in sent) continue
            val solar = "%02d/%02d".format(h.solar.get(Calendar.DAY_OF_MONTH), h.solar.get(Calendar.MONTH) + 1)
            val lunar = "${h.lunar.day}/${h.lunar.month} ÂL"
            val title = if (h.offset == 0) "🕯️ Hôm nay: ${h.event.name}" else "🕯️ Còn ${h.offset} ngày: ${h.event.name}"
            val text = "Ngày $lunar (dương lịch $solar)"
            if (post(ctx, key.hashCode(), title, text)) sent.add(key)
        }
        p.sent = sent
    }

    /** Đặt báo thức cho lần kiểm tra kế tiếp (giờ do người dùng chọn). */
    fun schedule(ctx: Context) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Prefs(ctx).notifyHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DATE, 1)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { Notifier.check(ctx); Notifier.schedule(ctx) } finally { pending.finish() }
        }.start()
        WeatherWidget.updateAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.schedule(ctx)
    }
}
