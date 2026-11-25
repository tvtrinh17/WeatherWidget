package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.concurrent.thread

class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread {
            try { refresh(ctx, mgr, ids) } finally { pending.finish() }
        }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
            thread { refresh(ctx, mgr, ids) }
        }

        private fun refresh(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val p = Prefs(ctx)
            val (cityName, lat, lon) = Prefs.CITIES[p.city.coerceIn(0, Prefs.CITIES.lastIndex)]

            // Thời tiết: ưu tiên mạng, lỗi thì dùng bản lưu gần nhất
            val json = try {
                Weather.fetchJson(lat, lon).also { p.cache = it }
            } catch (e: Exception) { p.cache }
            val w = json?.let { runCatching { Weather.parse(it) }.getOrNull() }

            val text = Prefs.TEXTS[p.textColor.coerceIn(0, 2)]
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val now = Calendar.getInstance()

            // Nền kính
            v.setImageViewBitmap(R.id.bg, background(p))

            // Đồng hồ + ngày
            v.setTextViewTextSize(R.id.clock, android.util.TypedValue.COMPLEX_UNIT_SP, p.clockSize.toFloat())
            val hour = now.get(Calendar.HOUR_OF_DAY)
            v.setTextViewText(R.id.greeting, when {
                hour < 11 -> "Chào buổi sáng"; hour < 14 -> "Chào buổi trưa"
                hour < 18 -> "Chào buổi chiều"; else -> "Chào buổi tối"
            })
            val dow = now.get(Calendar.DAY_OF_WEEK)
            val dowLabel = if (dow == 1) "Chủ Nhật" else "Thứ ${dow}"
            val dd = now.get(Calendar.DAY_OF_MONTH); val mm = now.get(Calendar.MONTH) + 1
            v.setTextViewText(R.id.date, "$dowLabel, $dd Thg $mm")
            val l = Lunar.convert(dd, mm, now.get(Calendar.YEAR))
            v.setTextViewText(R.id.lunar, "${l.day}/${l.month}${if (l.leap) " nhuận" else ""} ÂL • ${Lunar.canChi(l.year)}")

            // Thời tiết hiện tại
            v.setTextViewText(R.id.loc, "📍 $cityName" + (w?.let { " • ↑${it.hi}° ↓${it.lo}°" } ?: ""))
            v.setTextViewText(R.id.temp, w?.let { "${it.temp}°" } ?: "--°")
            v.setTextViewText(R.id.icon, w?.let { Weather.icon(it.code) } ?: "")
            v.setTextViewText(R.id.desc, w?.let { Weather.desc(it.code) } ?: "Chưa có dữ liệu")

            // Pin
            val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bi?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } ?: 0
            val t = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            v.setTextViewText(R.id.battery, "🔋 $level% • ${"%.1f".format(t)}°C")

            // Thanh tuần (T2..CN) — hôm nay tô đỏ
            v.removeAllViews(R.id.week)
            val mon = (now.clone() as Calendar).apply {
                add(Calendar.DATE, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
            }
            val names = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
            for (i in 0..6) {
                val d = (mon.clone() as Calendar).apply { add(Calendar.DATE, i) }
                val isToday = d.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    d.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                val cell = RemoteViews(ctx.packageName, if (isToday) R.layout.cell_day_today else R.layout.cell_day)
                val lun = Lunar.convert(d.get(Calendar.DAY_OF_MONTH), d.get(Calendar.MONTH) + 1, d.get(Calendar.YEAR))
                cell.setTextViewText(R.id.c1, names[i])
                cell.setTextViewText(R.id.c2, d.get(Calendar.DAY_OF_MONTH).toString())
                cell.setTextViewText(R.id.c3, lun.day.toString())
                if (!isToday) {
                    val c = if (i == 6) 0xFFE53935.toInt() else text
                    cell.setTextColor(R.id.c1, c); cell.setTextColor(R.id.c2, text); cell.setTextColor(R.id.c3, (text and 0x00FFFFFF) or 0x99000000.toInt())
                }
                v.addView(R.id.week, cell)
            }

            // Dự báo 5 ngày
            v.removeAllViews(R.id.forecast)
            w?.days?.drop(0)?.take(5)?.forEach { day ->
                val cell = RemoteViews(ctx.packageName, R.layout.cell_day)
                cell.setTextViewText(R.id.c1, day.label)
                cell.setTextViewText(R.id.c2, Weather.icon(day.code))
                cell.setTextViewText(R.id.c3, "${day.min}°/${day.max}°")
                cell.setTextColor(R.id.c1, text); cell.setTextColor(R.id.c3, text)
                v.addView(R.id.forecast, cell)
            }

            // Sự kiện âm lịch gần nhất (trong 30 ngày tới)
            val next = Events.scan(ctx, 30).firstOrNull()
            if (next != null) {
                v.setViewVisibility(R.id.event, android.view.View.VISIBLE)
                v.setTextViewText(R.id.event, "🕯️ ${next.event.name} • " +
                    if (next.offset == 0) "hôm nay" else "còn ${next.offset} ngày")
                v.setTextColor(R.id.event, text)
            } else {
                v.setViewVisibility(R.id.event, android.view.View.GONE)
            }

            // Màu chữ chung
            listOf(R.id.greeting, R.id.clock, R.id.date, R.id.lunar, R.id.temp, R.id.desc, R.id.loc, R.id.battery)
                .forEach { v.setTextColor(it, text) }

            ids.forEach { mgr.updateAppWidget(it, v) }
        }

        private fun background(p: Prefs): Bitmap {
            val w = 720; val h = 520
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = p.radius * 2.1f
            val base = Prefs.TONES[p.tone.coerceIn(0, 3)]
            val a = (p.opacity.coerceIn(0, 100) * 255 / 100)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (base and 0x00FFFFFF) or (a shl 24) }
            val rect = RectF(4f, 4f, w - 4f, h - 4f)
            c.drawRoundRect(rect, r, r, fill)
            if (p.border > 0) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 3f
                    color = Color.argb(p.border * 255 / 100, 255, 255, 255)
                }
                c.drawRoundRect(rect, r, r, stroke)
            }
            return bmp
        }
    }
}
