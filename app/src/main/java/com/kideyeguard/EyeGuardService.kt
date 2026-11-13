package com.kideyeguard

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.time.LocalTime

class EyeGuardService : Service() {

    private lateinit var prefs: GuardPrefs
    private lateinit var overlayManager: LockOverlayManager
    private val handler = Handler(Looper.getMainLooper())
    private var sessionSeconds = 0
    private var isLockedNow = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = GuardPrefs(this)
        overlayManager = LockOverlayManager(this, prefs) { viaMath ->
            isLockedNow = false
            sessionSeconds = 0
            prefs.recordRestCompleted(viaMath)
            broadcastState()
        }
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("守护运行中"))
        GuardKeepAliveJobService.schedule(this)
        handler.postDelayed(tickRunnable, 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIEW_LOCK -> triggerLock("家长主动预览 · 体验护眼锁屏与口算/密码解锁")
            ACTION_RESET_SESSION -> {
                sessionSeconds = 0
                broadcastState()
            }
            ACTION_REFRESH -> broadcastState()
        }
        return START_STICKY
    }

    private fun onTick() {
        if (!prefs.guardEnabled || isLockedNow) {
            broadcastState()
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            return
        }

        sessionSeconds += 1
        prefs.addUsedSeconds(1)

        if (prefs.curfewEnabled && GuardPrefs.isInCurfew(LocalTime.now(), prefs.curfewStart, prefs.curfewEnd)) {
            triggerLock("已进入夜间护眼宵禁时段（${prefs.curfewStart} ~ ${prefs.curfewEnd}），请放下设备休息")
            return
        }

        if (prefs.dailyLimitEnabled && prefs.todayUsedSeconds >= prefs.dailyLimitMinutes * 60) {
            triggerLock("今日累计用屏已达 ${prefs.dailyLimitMinutes} 分钟上限，明天再继续哦")
            return
        }

        val workLimit = prefs.workSeconds
        val remaining = workLimit - sessionSeconds
        if (remaining == 10) {
            Toast.makeText(this, "小树苗提醒：还有 10 秒就要休息眼睛啦", Toast.LENGTH_SHORT).show()
        }
        if (sessionSeconds >= workLimit) {
            triggerLock("连续用屏已满 ${formatDuration(workLimit)}，远眺放松一下眼睛吧")
            return
        }

        broadcastState()
    }

    private fun triggerLock(reason: String) {
        if (isLockedNow) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先在主界面授予「悬浮窗权限」才能弹出护眼锁屏", Toast.LENGTH_LONG).show()
            return
        }

        if (!prefs.isActivated()) {
            if (prefs.trialLocksUsed >= prefs.maxFreeTrialLocks) {
                prefs.guardEnabled = false
                Toast.makeText(this, "免费体验次数已用完，请家长输入激活码永久解锁", Toast.LENGTH_LONG).show()
                broadcastState()
                return
            }
            prefs.trialLocksUsed += 1
        }

        isLockedNow = true
        overlayManager.show(reason)
        broadcastState()
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_SESSION_SECONDS, sessionSeconds)
            putExtra(EXTRA_IS_LOCKED, isLockedNow)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "小树苗护眼守护",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小树苗护眼卫士")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_sprout)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (prefs.guardEnabled) {
            val restartIntent = Intent(applicationContext, EyeGuardService::class.java)
            val pi = PendingIntent.getService(
                applicationContext,
                1002,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1500L, pi)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        overlayManager.dismiss(viaMath = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatDuration(secs: Int): String {
        return if (secs < 60) "${secs}秒" else "${secs / 60}分钟"
    }

    companion object {
        const val CHANNEL_ID = "kid_eye_guard_channel"
        const val NOTIF_ID = 1001
        const val ACTION_PREVIEW_LOCK = "com.kideyeguard.PREVIEW_LOCK"
        const val ACTION_RESET_SESSION = "com.kideyeguard.RESET_SESSION"
        const val ACTION_REFRESH = "com.kideyeguard.REFRESH"
        const val ACTION_STATE_UPDATE = "com.kideyeguard.STATE_UPDATE"
        const val EXTRA_SESSION_SECONDS = "session_seconds"
        const val EXTRA_IS_LOCKED = "is_locked"
    }
}
