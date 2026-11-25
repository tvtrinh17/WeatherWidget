package com.example.weatherwidget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lấy thời tiết từ Open-Meteo (miễn phí, không cần API key). */
object Weather {
    fun fetchJson(lat: Double, lon: Double): String {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=Asia%2FBangkok&forecast_days=5"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    fun icon(code: Int) = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "☁️"
    }

    fun desc(code: Int) = when (code) {
        0 -> "Trời quang"
        1, 2 -> "Có mây rải rác"
        3 -> "Nhiều mây"
        45, 48 -> "Có sương mù"
        in 51..57 -> "Mưa phùn"
        in 61..67, in 80..82 -> "Có mưa"
        in 71..77, 85, 86 -> "Có tuyết"
        in 95..99 -> "Có dông"
        else -> "Nhiều mây"
    }

    class Day(val label: String, val code: Int, val min: Int, val max: Int)
    class Data(val temp: Int, val code: Int, val hi: Int, val lo: Int, val days: List<Day>)

    fun parse(json: String): Data {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val codes = d.getJSONArray("weather_code")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val days = (0 until codes.length()).map { i ->
            val dow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, i) }
                .get(java.util.Calendar.DAY_OF_WEEK) // CN=1 ... T7=7
            val label = when {
                i == 0 -> "H.NAY"
                dow == 1 -> "CN"
                else -> "THỨ $dow"
            }
            Day(label, codes.getInt(i), Math.round(mn.getDouble(i)).toInt(), Math.round(mx.getDouble(i)).toInt())
        }
        return Data(
            Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"),
            days[0].max, days[0].min, days
        )
    }
}
