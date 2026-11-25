package com.example.weatherwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Sự kiện theo ngày âm lịch (giỗ, kỵ, ăn chay...).
 * month = 0  -> lặp HẰNG THÁNG (ví dụ ăn chay 1, 15, 30)
 * month 1..12 -> lặp HẰNG NĂM vào tháng âm đó (ví dụ giỗ)
 * Ngày 30 = ngày cuối tháng: tháng thiếu (29 ngày) sẽ tính vào ngày 29.
 */
data class LunarEvent(
    val id: Long,
    val name: String,
    val month: Int,
    val days: List<Int>,
    val remindBefore: Int // 0 = chỉ báo đúng ngày; 1..7 = báo thêm trước N ngày
) {
    fun matches(l: Lunar.Date, next: Lunar.Date): Boolean {
        if (month != 0 && (l.month != month || l.leap)) return false
        return days.any { it == l.day || (it == 30 && l.day == 29 && next.day == 1) }
    }

    fun describe(): String {
        val m = if (month == 0) "hằng tháng" else "tháng $month ÂL hằng năm"
        val r = if (remindBefore == 0) "báo đúng ngày" else "báo đúng ngày + trước $remindBefore ngày"
        return "Ngày ${days.joinToString(", ")} • $m\n$r"
    }
}

class Hit(val event: LunarEvent, val offset: Int, val solar: Calendar, val lunar: Lunar.Date)

object Events {
    fun load(ctx: Context): MutableList<LunarEvent> {
        val s = Prefs(ctx).events ?: return mutableListOf()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                LunarEvent(
                    o.getLong("id"), o.getString("name"), o.getInt("month"),
                    o.getString("days").split(",").mapNotNull { it.trim().toIntOrNull() },
                    o.getInt("remind")
                )
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun save(ctx: Context, list: List<LunarEvent>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("month", it.month)
                .put("days", it.days.joinToString(",")).put("remind", it.remindBefore))
        }
        Prefs(ctx).events = a.toString()
    }

    fun lunarOf(c: Calendar) = Lunar.convert(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))

    /** Các lần sự kiện xảy ra trong [hôm nay, hôm nay + maxOffset], xếp theo thời gian. */
    fun scan(ctx: Context, maxOffset: Int): List<Hit> {
        val events = load(ctx)
        if (events.isEmpty()) return emptyList()
        val base = Calendar.getInstance()
        val cals = (0..maxOffset + 1).map { (base.clone() as Calendar).apply { add(Calendar.DATE, it) } }
        val lun = cals.map { lunarOf(it) }
        val hits = mutableListOf<Hit>()
        for (o in 0..maxOffset) for (e in events) {
            if (e.matches(lun[o], lun[o + 1])) hits.add(Hit(e, o, cals[o], lun[o]))
        }
        return hits
    }
}
