package com.kideyeguard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LockOverlayManager(
    private val context: Context,
    private val prefs: GuardPrefs,
    private val onUnlocked: (viaMath: Boolean) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var timer: CountDownTimer? = null
    private var isShowing = false

    fun show(reasonText: String) {
        if (isShowing) return
        if (!Settings.canDrawOverlays(context)) return

        val restSecs = prefs.restSeconds.coerceAtLeast(10)
        isShowing = true

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F50F172A"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg("#1E293B", 18, "#334155")
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }

        val badge = TextView(context).apply {
            text = "护眼休息时间"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg("#064E3B", 99)
            setPadding(dp(14), dp(5), dp(14), dp(5))
        }
        card.addView(badge)

        val titleView = TextView(context).apply {
            text = "让眼睛歇一会吧"
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(6))
        }
        card.addView(titleView)

        val reasonView = TextView(context).apply {
            text = reasonText
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        card.addView(reasonView)

        val countdownView = TextView(context).apply {
            text = formatMmSs(restSecs)
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 48f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(22))
        }
        card.addView(countdownView)

        val panelBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(14))
        }
        card.addView(panelBox)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        if (prefs.allowMathUnlock) {
            val mathBtn = makeButton("做口算提前解锁", "#2563EB") {
                showMathPanel(panelBox)
            }
            btnRow.addView(mathBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) })
        }

        val pinBtn = makeButton("家长密码解锁", "#475569") {
            showPinPanel(panelBox)
        }
        btnRow.addView(pinBtn)
        card.addView(btnRow)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            })
        }
        container.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        rootView = container
        windowManager.addView(container, params)

        timer = object : CountDownTimer(restSecs * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = ((millisUntilFinished + 999) / 1000).toInt()
                countdownView.text = formatMmSs(sec)
            }

            override fun onFinish() {
                dismiss(viaMath = false)
            }
        }.start()
    }

    private fun showMathPanel(panelBox: LinearLayout) {
        panelBox.removeAllViews()
        panelBox.visibility = View.VISIBLE
        val problems = MathChallenge.generate(prefs.mathDifficulty, prefs.mathCount)
        val inputs = mutableListOf<EditText>()

        val hint = TextView(context).apply {
            text = "答对以下 ${problems.size} 道题即可提前解锁："
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            setPadding(0, 0, 0, dp(10))
        }
        panelBox.addView(hint)

        problems.forEach { p ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val qLabel = TextView(context).apply {
                text = p.prompt
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dp(12), 0)
            }
            val ansInput = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "答案"
                textSize = 16f
                background = roundedBg("#0F172A", 8, "#475569")
                setPadding(dp(14), dp(8), dp(14), dp(8))
                minWidth = dp(90)
                gravity = Gravity.CENTER
            }
            inputs.add(ansInput)
            row.addView(qLabel)
            row.addView(ansInput)
            panelBox.addView(row)
        }

        val errLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 13f
            setPadding(0, dp(6), 0, dp(6))
        }
        panelBox.addView(errLabel)

        val submit = makeButton("提交答案", "#10B981") {
            val allCorrect = problems.indices.all { i ->
                inputs[i].text.toString().trim().toIntOrNull() == problems[i].answer
            }
            if (allCorrect) {
                dismiss(viaMath = true)
            } else {
                errLabel.text = "有题目答错了，再仔细算一算哦"
            }
        }
        panelBox.addView(submit)
    }

    private fun showPinPanel(panelBox: LinearLayout) {
        panelBox.removeAllViews()
        panelBox.visibility = View.VISIBLE

        val hint = TextView(context).apply {
            text = "请输入家长密码以立即结束休息："
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            setPadding(0, 0, 0, dp(10))
        }
        panelBox.addView(hint)

        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "默认 1234"
            textSize = 16f
            background = roundedBg("#0F172A", 8, "#475569")
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minWidth = dp(160)
            gravity = Gravity.CENTER
        }
        panelBox.addView(pinInput)

        val errLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 13f
            setPadding(0, dp(6), 0, dp(6))
        }
        panelBox.addView(errLabel)

        val submit = makeButton("验证密码解锁", "#10B981") {
            if (prefs.verifyPin(pinInput.text.toString())) {
                dismiss(viaMath = false)
            } else {
                errLabel.text = "家长密码不正确"
            }
        }
        panelBox.addView(submit)
    }

    fun dismiss(viaMath: Boolean) {
        if (!isShowing) return
        isShowing = false
        timer?.cancel()
        timer = null
        rootView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        rootView = null
        onUnlocked(viaMath)
    }

    private fun makeButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg(colorHex, 10)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBg(fillHex: String, radiusDp: Int, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun formatMmSs(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }
}
