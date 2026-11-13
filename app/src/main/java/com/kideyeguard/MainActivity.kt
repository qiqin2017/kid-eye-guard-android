package com.kideyeguard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: GuardPrefs
    private var settingsUnlocked = false
    private val settingWidgets = mutableListOf<View>()

    private lateinit var licenseBadge: TextView
    private lateinit var guardBtn: Button
    private lateinit var overlayStatusLbl: TextView
    private lateinit var batteryStatusLbl: TextView
    private lateinit var nextRestValue: TextView
    private lateinit var todayUsedValue: TextView
    private lateinit var restCountValue: TextView
    private lateinit var mathCountValue: TextView

    private lateinit var lockStatusLbl: TextView
    private lateinit var unlockSettingsBtn: Button

    private lateinit var workGroup: RadioGroup
    private lateinit var restGroup: RadioGroup
    private lateinit var mathCheck: CheckBox
    private lateinit var diffGroup: RadioGroup
    private lateinit var dailyCheck: CheckBox
    private lateinit var dailyMinsEdit: EditText
    private lateinit var curfewCheck: CheckBox
    private lateinit var curfewStartEdit: EditText
    private lateinit var curfewEndEdit: EditText
    private lateinit var autostartCheck: CheckBox

    private var currentSessionSeconds = 0

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == EyeGuardService.ACTION_STATE_UPDATE) {
                currentSessionSeconds = intent.getIntExtra(EyeGuardService.EXTRA_SESSION_SECONDS, 0)
                refreshStatsUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = GuardPrefs(this)
        buildUi()
        ensureNotificationPermission()
        startGuardService()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(EyeGuardService.ACTION_STATE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        refreshPermissionBadges()
        refreshLicenseBadge()
        refreshGuardButton()
        refreshStatsUi()
        sendServiceAction(EyeGuardService.ACTION_REFRESH)
    }

    override fun onPause() {
        super.onPause()
        if (settingsUnlocked) {
            saveToggles()
            setSettingsLocked(true)
        }
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun startGuardService() {
        val intent = Intent(this, EyeGuardService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, EyeGuardService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        // Header Card
        val headerCard = makeCard()
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "小树苗护眼卫士"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        licenseBadge = TextView(this).apply {
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        titleRow.addView(licenseBadge, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(10) })
        headerCard.addView(titleRow)

        headerCard.addView(TextView(this).apply {
            text = "定时护眼提醒 · 强制休息遮罩 · 口算/家长密码解锁"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(14))
        })

        val headerBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        guardBtn = makeActionButton("守护中 · 暂停", "#10B981") { toggleGuard() }
        val previewBtn = makeActionButton("立即体验锁屏", "#2563EB") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启下方的「悬浮窗权限」", Toast.LENGTH_SHORT).show()
                openOverlaySettings()
            } else {
                sendServiceAction(EyeGuardService.ACTION_PREVIEW_LOCK)
            }
        }
        headerBtns.addView(guardBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })
        headerBtns.addView(previewBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerCard.addView(headerBtns)
        root.addView(headerCard, cardLp())

        // Permission Health Card
        val permCard = makeCard()
        permCard.addView(TextView(this).apply {
            text = "安卓防绕过权限体检"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, dp(8))
        })

        val overlayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        overlayStatusLbl = TextView(this).apply { textSize = 13f }
        overlayRow.addView(overlayStatusLbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        overlayRow.addView(makeSmallButton("去授权") { openOverlaySettings() })
        permCard.addView(overlayRow)

        val batteryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        batteryStatusLbl = TextView(this).apply { textSize = 13f }
        batteryRow.addView(batteryStatusLbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        batteryRow.addView(makeSmallButton("去设置") { openBatterySettings() })
        permCard.addView(batteryRow)
        root.addView(permCard, cardLp())

        // 2x2 Stats Grid
        val statsGrid1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nextRestValue = addStatCard(statsGrid1, "距离下次护眼休息", "#2563EB", marginEndDp = 8)
        todayUsedValue = addStatCard(statsGrid1, "今日累计用屏时长", "#0F172A", marginEndDp = 0)
        root.addView(statsGrid1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        val statsGrid2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        restCountValue = addStatCard(statsGrid2, "今日已完成休息", "#059669", marginEndDp = 8)
        mathCountValue = addStatCard(statsGrid2, "口算解锁成功", "#D97706", marginEndDp = 0)
        root.addView(statsGrid2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Settings Card (Locked by default)
        val settingsCard = makeCard()
        val sHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        sHead.addView(TextView(this).apply {
            text = "护眼规则与家长管控"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        unlockSettingsBtn = makeSmallButton("家长解锁设置") { toggleSettingsLock() }
        sHead.addView(unlockSettingsBtn)
        settingsCard.addView(sHead)

        lockStatusLbl = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        settingsCard.addView(lockStatusLbl)

        // Work duration
        settingsCard.addView(makeSectionLabel("单次连续用屏时长："))
        workGroup = buildRadioRow(
            listOf("10秒(测试)" to 10, "15分钟" to 900, "25分钟" to 1500, "45分钟" to 2700),
            prefs.workSeconds
        ) { secs ->
            if (!settingsUnlocked) return@buildRadioRow
            prefs.workSeconds = secs
            sendServiceAction(EyeGuardService.ACTION_RESET_SESSION)
            refreshStatsUi()
        }
        settingsCard.addView(workGroup)

        // Rest duration
        settingsCard.addView(makeSectionLabel("每次强制休息时长："))
        restGroup = buildRadioRow(
            listOf("15秒(测试)" to 15, "3分钟" to 180, "5分钟" to 300, "10分钟" to 600),
            prefs.restSeconds
        ) { secs ->
            if (!settingsUnlocked) return@buildRadioRow
            prefs.restSeconds = secs
        }
        settingsCard.addView(restGroup)

        // Math unlock
        mathCheck = CheckBox(this).apply {
            text = "允许孩子做口算题提前解锁"
            isChecked = prefs.allowMathUnlock
            setOnCheckedChangeListener { _, _ -> if (settingsUnlocked) saveToggles() }
        }
        settingWidgets.add(mathCheck)
        settingsCard.addView(mathCheck)

        diffGroup = buildStringRadioRow(
            listOf("低年级加减" to "easy", "表内乘除" to "medium", "综合混合" to "hard"),
            prefs.mathDifficulty
        ) { _ ->
            if (settingsUnlocked) saveToggles()
        }
        settingsCard.addView(diffGroup)

        // Daily limit
        val dailyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(4))
        }
        dailyCheck = CheckBox(this).apply {
            text = "每日总时长上限(分钟)："
            isChecked = prefs.dailyLimitEnabled
            setOnCheckedChangeListener { _, _ -> if (settingsUnlocked) saveToggles() }
        }
        dailyMinsEdit = EditText(this).apply {
            setText(prefs.dailyLimitMinutes.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg("#F8FAFC", 6, "#CBD5E1")
        }
        settingWidgets.add(dailyCheck)
        settingWidgets.add(dailyMinsEdit)
        dailyRow.addView(dailyCheck)
        dailyRow.addView(dailyMinsEdit, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        settingsCard.addView(dailyRow)

        // Curfew
        val curfewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        curfewCheck = CheckBox(this).apply {
            text = "夜间宵禁："
            isChecked = prefs.curfewEnabled
            setOnCheckedChangeListener { _, _ -> if (settingsUnlocked) saveToggles() }
        }
        curfewStartEdit = EditText(this).apply {
            setText(prefs.curfewStart)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBg("#F8FAFC", 6, "#CBD5E1")
        }
        curfewEndEdit = EditText(this).apply {
            setText(prefs.curfewEnd)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBg("#F8FAFC", 6, "#CBD5E1")
        }
        settingWidgets.add(curfewCheck)
        settingWidgets.add(curfewStartEdit)
        settingWidgets.add(curfewEndEdit)
        curfewRow.addView(curfewCheck)
        curfewRow.addView(curfewStartEdit, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
        curfewRow.addView(TextView(this).apply {
            text = " 至 "
            setTextColor(Color.parseColor("#64748B"))
        })
        curfewRow.addView(curfewEndEdit, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
        settingsCard.addView(curfewRow)

        // Autostart
        autostartCheck = CheckBox(this).apply {
            text = "开机自动启动护眼守护"
            isChecked = prefs.autostart
            setOnCheckedChangeListener { _, isChecked ->
                if (settingsUnlocked) prefs.autostart = isChecked
            }
        }
        settingWidgets.add(autostartCheck)
        settingsCard.addView(autostartCheck)

        // Bottom actions
        val bottomBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        bottomBtns.addView(makeSmallButton("修改家长密码(默认1234)") { showChangePinDialog() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        bottomBtns.addView(makeSmallButton("输入激活码") { showActivateDialog() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        settingsCard.addView(bottomBtns)

        root.addView(settingsCard, cardLp())

        root.addView(TextView(this).apply {
            text = "提示：所有规则默认锁定，防止孩子自己调长用屏时间或缩短休息时间。"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })

        setSettingsLocked(true)
    }

    private fun setSettingsLocked(locked: Boolean) {
        settingsUnlocked = !locked
        settingWidgets.forEach { it.isEnabled = !locked }
        if (locked) {
            lockStatusLbl.text = "当前已锁定 · 修改规则需验证家长密码"
            lockStatusLbl.setTextColor(Color.parseColor("#64748B"))
            unlockSettingsBtn.text = "家长解锁设置"
            unlockSettingsBtn.background = roundedBg("#FEF3C7", 8)
            unlockSettingsBtn.setTextColor(Color.parseColor("#B45309"))
        } else {
            lockStatusLbl.text = "已解锁 · 修改后请点击右侧完成并锁定"
            lockStatusLbl.setTextColor(Color.parseColor("#059669"))
            unlockSettingsBtn.text = "完成并锁定"
            unlockSettingsBtn.background = roundedBg("#ECFDF5", 8)
            unlockSettingsBtn.setTextColor(Color.parseColor("#059669"))
        }
    }

    private fun toggleSettingsLock() {
        if (settingsUnlocked) {
            saveToggles()
            setSettingsLocked(true)
            return
        }
        askPin("家长验证", "请输入家长密码以解锁护眼规则（默认 1234）：") {
            setSettingsLocked(false)
        }
    }

    private fun saveToggles() {
        if (!settingsUnlocked) return
        prefs.allowMathUnlock = mathCheck.isChecked
        val checkedDiffId = diffGroup.checkedRadioButtonId
        if (checkedDiffId != View.NO_ID) {
            val rb = diffGroup.findViewById<RadioButton>(checkedDiffId)
            (rb?.tag as? String)?.let { prefs.mathDifficulty = it }
        }
        prefs.dailyLimitEnabled = dailyCheck.isChecked
        val mins = dailyMinsEdit.text.toString().trim().toIntOrNull()?.coerceAtLeast(10) ?: 120
        prefs.dailyLimitMinutes = mins
        dailyMinsEdit.setText(mins.toString())

        prefs.curfewEnabled = curfewCheck.isChecked
        val start = GuardPrefs.normalizeHhmm(curfewStartEdit.text.toString(), prefs.curfewStart)
        val end = GuardPrefs.normalizeHhmm(curfewEndEdit.text.toString(), prefs.curfewEnd)
        prefs.curfewStart = start
        prefs.curfewEnd = end
        curfewStartEdit.setText(start)
        curfewEndEdit.setText(end)
    }

    private fun toggleGuard() {
        if (prefs.guardEnabled) {
            askPin("暂停守护", "请输入家长密码以暂停护眼守护：") {
                prefs.guardEnabled = false
                refreshGuardButton()
                refreshStatsUi()
            }
        } else {
            prefs.guardEnabled = true
            sendServiceAction(EyeGuardService.ACTION_RESET_SESSION)
            refreshGuardButton()
            refreshStatsUi()
        }
    }

    private fun showChangePinDialog() {
        askPin("身份验证", "请输入当前家长密码（默认 1234）：") {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "输入 4~8 位新密码"
            }
            AlertDialog.Builder(this)
                .setTitle("设置新家长密码")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newPin = input.text.toString().trim()
                    if (newPin.length < 4) {
                        Toast.makeText(this, "密码至少 4 位数字/字符", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.setPin(newPin)
                        Toast.makeText(this, "家长密码已更新", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showActivateDialog() {
        val input = EditText(this).apply {
            hint = "KEG-XXXX-XXXX-CCCC"
        }
        AlertDialog.Builder(this)
            .setTitle("输入授权激活码")
            .setMessage("与电脑版通用，输入激活码即可永久解锁全部守护次数。")
            .setView(input)
            .setPositiveButton("立即激活") { _, _ ->
                val code = input.text.toString().trim().uppercase()
                if (LicenseManager.verifyActivationCode(code)) {
                    prefs.licenseKey = code
                    refreshLicenseBadge()
                    Toast.makeText(this, "激活成功！已解锁永久完整版", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "激活码无效，请核对后重试", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun askPin(title: String, message: String, onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "默认 1234"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("确认") { _, _ ->
                if (prefs.verifyPin(input.text.toString())) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "家长密码不正确", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshPermissionBadges() {
        val overlayOk = Settings.canDrawOverlays(this)
        overlayStatusLbl.text = if (overlayOk) "悬浮窗锁屏权限：已开启" else "悬浮窗锁屏权限：未开启（必开）"
        overlayStatusLbl.setTextColor(Color.parseColor(if (overlayOk) "#059669" else "#DC2626"))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        batteryStatusLbl.text = if (batteryOk) "后台保活（忽略电池优化）：已开启" else "后台保活：建议开启防系统杀后台"
        batteryStatusLbl.setTextColor(Color.parseColor(if (batteryOk) "#059669" else "#D97706"))
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refreshLicenseBadge() {
        if (prefs.isActivated()) {
            licenseBadge.text = "已永久激活"
            licenseBadge.background = roundedBg("#ECFDF5", 6)
            licenseBadge.setTextColor(Color.parseColor("#059669"))
        } else {
            licenseBadge.text = "试用版(剩${prefs.remainingTrialLocks()}次)"
            licenseBadge.background = roundedBg("#FEF3C7", 6)
            licenseBadge.setTextColor(Color.parseColor("#B45309"))
        }
    }

    private fun refreshGuardButton() {
        if (prefs.guardEnabled) {
            guardBtn.text = "守护中 · 暂停"
            guardBtn.background = roundedBg("#10B981", 8)
        } else {
            guardBtn.text = "已暂停 · 开启"
            guardBtn.background = roundedBg("#64748B", 8)
        }
    }

    private fun refreshStatsUi() {
        if (!prefs.guardEnabled) {
            nextRestValue.text = "已暂停"
        } else {
            val rem = (prefs.workSeconds - currentSessionSeconds).coerceAtLeast(0)
            nextRestValue.text = formatCnDuration(rem)
        }
        todayUsedValue.text = formatCnDuration(prefs.todayUsedSeconds)
        restCountValue.text = "${prefs.todayRestCount} 次"
        mathCountValue.text = "${prefs.todayMathUnlockCount} 次"
    }

    private fun formatCnDuration(secs: Int): String {
        val m = secs / 60
        val s = secs % 60
        return if (m > 0) "${m}分${s}秒" else "${s}秒"
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#FFFFFF", 12, "#E2E8F0")
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun addStatCard(parent: LinearLayout, title: String, colorHex: String, marginEndDp: Int): TextView {
        val card = makeCard()
        card.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        })
        val valueTv = TextView(this).apply {
            text = "-"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(colorHex))
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(valueTv)
        parent.addView(card, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(marginEndDp)
        })
        return valueTv
    }

    private fun makeSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, dp(8), 0, dp(2))
        }
    }

    private fun buildRadioRow(
        items: List<Pair<String, Int>>,
        selectedValue: Int,
        onSelected: (Int) -> Unit
    ): RadioGroup {
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        items.forEach { (label, value) ->
            val rb = RadioButton(this).apply {
                text = label
                textSize = 13f
                tag = value
                isChecked = (value == selectedValue)
                setOnClickListener { onSelected(value) }
            }
            settingWidgets.add(rb)
            group.addView(rb)
        }
        return group
    }

    private fun buildStringRadioRow(
        items: List<Pair<String, String>>,
        selectedValue: String,
        onSelected: (String) -> Unit
    ): RadioGroup {
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        items.forEach { (label, value) ->
            val rb = RadioButton(this).apply {
                text = label
                textSize = 13f
                tag = value
                isChecked = (value == selectedValue)
                setOnClickListener { onSelected(value) }
            }
            settingWidgets.add(rb)
            group.addView(rb)
        }
        return group
    }

    private fun makeActionButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundedBg(colorHex, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun makeSmallButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#0F172A"))
            background = roundedBg("#F1F5F9", 8)
            setPadding(dp(12), dp(6), dp(12), dp(6))
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
