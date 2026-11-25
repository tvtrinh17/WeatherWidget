package com.example.weatherwidget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.widget.*

/** Quản lý sự kiện âm lịch: giỗ, kỵ, ăn chay... */
class EventsActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val p = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "Sự kiện âm lịch"; textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Thêm giỗ, kỵ, ngày ăn chay... App sẽ nhắc bạn theo ngày âm lịch."
            setPadding(0, dp(4), 0, dp(12))
        })

        // Giờ nhắc mỗi ngày
        val hourTv = TextView(this).apply { text = "Giờ nhắc mỗi ngày: ${p.notifyHour}:00" }
        val hourSb = SeekBar(this).apply {
            max = 23; progress = p.notifyHour
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pr: Int, user: Boolean) {
                    p.notifyHour = pr; hourTv.text = "Giờ nhắc mỗi ngày: $pr:00"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { Notifier.schedule(this@EventsActivity) }
            })
        }
        root.addView(hourTv); root.addView(hourSb, LinearLayout.LayoutParams(-1, -2))

        fun button(label: String, onClick: () -> Unit) = root.addView(Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        button("＋ Thêm sự kiện") { showEditor(null) }
        button("Thêm nhanh: Ăn chay mùng 1 & rằm (1, 15)") { quickAdd("Ăn chay", listOf(1, 15)) }
        button("Thêm nhanh: Ăn chay 1, 15, 30") { quickAdd("Ăn chay", listOf(1, 15, 30)) }
        button("Gửi thử 1 thông báo") {
            val ok = Notifier.post(this, 999, "🕯️ Thông báo thử", "Nếu thấy dòng này là thông báo đã hoạt động.")
            if (!ok) Toast.makeText(this, "Chưa được cấp quyền thông báo cho app", Toast.LENGTH_LONG).show()
        }

        root.addView(TextView(this).apply {
            text = "Danh sách sự kiện"; textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(20), 0, dp(6))
        })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        Notifier.schedule(this)
        refresh()
    }

    private fun quickAdd(name: String, days: List<Int>) {
        val list = Events.load(this)
        list.add(LunarEvent(System.currentTimeMillis(), name, 0, days, 1))
        afterChange(list)
    }

    private fun afterChange(list: List<LunarEvent>) {
        Events.save(this, list)
        refresh()
        WeatherWidget.updateAll(applicationContext)
        Notifier.schedule(this)
        Thread { Notifier.check(applicationContext) }.start()
    }

    private fun refresh() {
        listBox.removeAllViews()
        val list = Events.load(this)
        if (list.isEmpty()) {
            listBox.addView(TextView(this).apply { text = "Chưa có sự kiện nào." })
            return
        }
        list.forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val info = TextView(this).apply {
                text = "${e.name}\n${e.describe()}"
                setOnClickListener { showEditor(e) }
            }
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Xóa"
                setOnClickListener {
                    AlertDialog.Builder(this@EventsActivity)
                        .setMessage("Xóa \"${e.name}\"?")
                        .setPositiveButton("Xóa") { _, _ ->
                            afterChange(Events.load(this@EventsActivity).filter { it.id != e.id })
                        }
                        .setNegativeButton("Hủy", null).show()
                }
            })
            listBox.addView(row)
        }
    }

    private fun showEditor(existing: LunarEvent?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            hint = "Tên (VD: Giỗ ông nội)"; setText(existing?.name ?: "")
        }
        val days = EditText(this).apply {
            hint = "Ngày âm lịch (VD: 15 hoặc 1,15,30)"
            setText(existing?.days?.joinToString(",") ?: "")
        }
        val month = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EventsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Hằng tháng") + (1..12).map { "Tháng $it ÂL (hằng năm)" }
            )
            setSelection(existing?.month ?: 0)
        }
        val remind = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EventsActivity, android.R.layout.simple_spinner_dropdown_item,
                (0..7).map { if (it == 0) "Chỉ báo đúng ngày" else "Báo thêm trước $it ngày" }
            )
            setSelection(existing?.remindBefore ?: 1)
        }
        box.addView(name); box.addView(days); box.addView(month); box.addView(remind)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Thêm sự kiện" else "Sửa sự kiện")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val n = name.text.toString().trim()
                val d = days.text.toString().split(",", " ", ";")
                    .mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..30 }.distinct().sorted()
                if (n.isEmpty() || d.isEmpty()) {
                    Toast.makeText(this, "Cần nhập tên và ngày hợp lệ (1–30)", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val ev = LunarEvent(
                    existing?.id ?: System.currentTimeMillis(), n,
                    month.selectedItemPosition, d, remind.selectedItemPosition
                )
                val list = Events.load(this)
                val idx = list.indexOfFirst { it.id == ev.id }
                if (idx >= 0) list[idx] = ev else list.add(ev)
                afterChange(list)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
