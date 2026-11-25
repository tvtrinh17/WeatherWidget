package com.example.weatherwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*

/** Màn hình tùy chỉnh: kính, bo góc, cỡ đồng hồ, màu chữ, thành phố. */
class ConfigActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Prefs(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        fun title(t: String) = root.addView(TextView(this).apply {
            text = t; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(18), 0, dp(6))
        })

        fun radio(opts: List<String>, sel: Int, onSel: (Int) -> Unit) {
            val g = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            opts.forEachIndexed { i, s -> g.addView(RadioButton(this).apply { text = s; id = 1000 + i }) }
            g.check(1000 + sel)
            g.setOnCheckedChangeListener { _, id -> onSel(id - 1000) }
            root.addView(g)
        }

        fun slider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
            val tv = TextView(this).apply { text = "$label: $value" }
            val sb = SeekBar(this).apply {
                this.max = max - min; progress = value - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, pr: Int, user: Boolean) {
                        tv.text = "$label: ${pr + min}"; onChange(pr + min)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            root.addView(tv); root.addView(sb, ViewGroup.LayoutParams(-1, -2))
        }

        root.addView(Button(this).apply {
            text = "🕯️ Sự kiện âm lịch (giỗ, kỵ, ăn chay…)"; isAllCaps = false
            setOnClickListener { startActivity(Intent(context, EventsActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, -2))

        title("1. Kính lòng & khung viền")
        radio(listOf("Trắng", "Khói tối", "Xanh băng", "Kem ấm"), p.tone) { p.tone = it }
        slider("Độ đục nền (%)", 0, 100, p.opacity) { p.opacity = it }
        slider("Bo góc (dp)", 0, 48, p.radius) { p.radius = it }
        slider("Viền kính (%)", 0, 100, p.border) { p.border = it }

        title("2. Đồng hồ & màu sắc")
        slider("Cỡ chữ đồng hồ (sp)", 30, 72, p.clockSize) { p.clockSize = it }
        radio(listOf("Đen than", "Trắng sáng", "Xanh Navy"), p.textColor) { p.textColor = it }

        title("3. Vị trí thời tiết")
        radio(Prefs.CITIES.map { it.first }, p.city) { p.city = it }

        root.addView(Button(this).apply {
            text = "Lưu & cập nhật widget"
            setOnClickListener {
                WeatherWidget.updateAll(applicationContext)
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                }
                Toast.makeText(context, "Đã lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
    }
}
