package com.example.weatherwidget

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Âm lịch Việt Nam (thuật toán Hồ Ngọc Đức, múi giờ UTC+7). */
object Lunar {
    private const val TZ = 7.0

    data class Date(val day: Int, val month: Int, val year: Int, val leap: Boolean)

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    fun canChi(year: Int) = "${CAN[(year + 6) % 10]} ${CHI[(year + 8) % 12]}"

    private fun jd(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        return dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85; val t2 = t * t; val t3 = t2 * t; val dr = PI / 180
        var j = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        j += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mp = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c += -0.4068 * sin(mp * dr) + 0.0161 * sin(dr * 2 * mp) - 0.0004 * sin(dr * 3 * mp)
        c += 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mp)) - 0.0074 * sin(dr * (m - mp)) +
            0.0004 * sin(dr * (2 * f + m))
        c += -0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mp)) +
            0.0010 * sin(dr * (2 * f - mp)) + 0.0005 * sin(dr * (2 * mp + m))
        val dt = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return j + c - dt
    }

    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525; val t2 = t * t; val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    private fun sunLong(day: Int) = floor(sunLongitude(day - 0.5 - TZ / 24) / PI * 6).toInt()
    private fun newMoonDay(k: Int) = floor(newMoon(k) + 0.5 + TZ / 24).toInt()

    private fun month11(yy: Int): Int {
        val off = jd(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = newMoonDay(k)
        if (sunLong(nm) >= 9) nm = newMoonDay(k - 1)
        return nm
    }

    private fun leapOffset(a11: Int): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var i = 1
        var arc = sunLong(newMoonDay(k + i))
        var last: Int
        do {
            last = arc; i++
            arc = sunLong(newMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    fun convert(dd: Int, mm: Int, yy: Int): Date {
        val dn = jd(dd, mm, yy)
        val k = floor((dn - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dn) monthStart = newMoonDay(k)
        var a11 = month11(yy)
        var b11 = a11
        var ly: Int
        if (a11 >= monthStart) { ly = yy; a11 = month11(yy - 1) } else { ly = yy + 1; b11 = month11(yy + 1) }
        val ld = dn - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lm = diff + 11
        var leap = false
        if (b11 - a11 > 365) {
            val lo = leapOffset(a11)
            if (diff >= lo) { lm = diff + 10; if (diff == lo) leap = true }
        }
        if (lm > 12) lm -= 12
        if (lm >= 11 && diff < 4) ly -= 1
        return Date(ld, lm, ly, leap)
    }
}
