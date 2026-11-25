package com.example.weatherwidget

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    var tone: Int get() = sp.getInt("tone", 3); set(v) = sp.edit().putInt("tone", v).apply()          // 0 trắng, 1 khói tối, 2 xanh băng, 3 kem ấm
    var opacity: Int get() = sp.getInt("opacity", 100); set(v) = sp.edit().putInt("opacity", v).apply()
    var radius: Int get() = sp.getInt("radius", 32); set(v) = sp.edit().putInt("radius", v).apply()   // dp
    var border: Int get() = sp.getInt("border", 0); set(v) = sp.edit().putInt("border", v).apply()    // 0..100
    var clockSize: Int get() = sp.getInt("clock", 54); set(v) = sp.edit().putInt("clock", v).apply()  // sp
    var textColor: Int get() = sp.getInt("text", 0); set(v) = sp.edit().putInt("text", v).apply()     // 0 đen than, 1 trắng, 2 xanh navy
    var city: Int get() = sp.getInt("city", 0); set(v) = sp.edit().putInt("city", v).apply()
    var cache: String? get() = sp.getString("cache", null); set(v) = sp.edit().putString("cache", v).apply()
    var events: String? get() = sp.getString("events", null); set(v) = sp.edit().putString("events", v).apply()
    var notifyHour: Int get() = sp.getInt("notify_hour", 7); set(v) = sp.edit().putInt("notify_hour", v).apply()
    var sent: Set<String>
        get() = sp.getStringSet("sent", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("sent", HashSet(v)).apply()

    companion object {
        val TONES = intArrayOf(0xFFFFFFFF.toInt(), 0xFF2B2B2E.toInt(), 0xFFDCEBFA.toInt(), 0xFFF3EBDD.toInt())
        val TEXTS = intArrayOf(0xFF1C1C1E.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A2B5C.toInt())
        val CITIES = listOf(
            Triple("Hà Nội", 21.0285, 105.8542),
            Triple("Huế", 16.4637, 107.5909),
            Triple("Đà Nẵng", 16.0544, 108.2022),
            Triple("TP.HCM", 10.8231, 106.6297),
        )
    }
}
