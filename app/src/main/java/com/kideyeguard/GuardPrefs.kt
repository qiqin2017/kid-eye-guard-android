package com.kideyeguard

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime

class GuardPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kid_eye_guard_prefs", Context.MODE_PRIVATE)

    var guardEnabled: Boolean
        get() = prefs.getBoolean("guard_enabled", true)
        set(v) = prefs.edit().putBoolean("guard_enabled", v).apply()

    var workSeconds: Int
        get() = prefs.getInt("work_seconds", 25 * 60)
        set(v) = prefs.edit().putInt("work_seconds", v).apply()

    var restSeconds: Int
        get() = prefs.getInt("rest_seconds", 5 * 60)
        set(v) = prefs.edit().putInt("rest_seconds", v).apply()

    var allowMathUnlock: Boolean
        get() = prefs.getBoolean("allow_math_unlock", true)
        set(v) = prefs.edit().putBoolean("allow_math_unlock", v).apply()

    var mathDifficulty: String
        get() = prefs.getString("math_difficulty", "easy") ?: "easy"
        set(v) = prefs.edit().putString("math_difficulty", v).apply()

    var mathCount: Int
        get() = prefs.getInt("math_count", 2)
        set(v) = prefs.edit().putInt("math_count", v).apply()

    var dailyLimitEnabled: Boolean
        get() = prefs.getBoolean("daily_limit_enabled", false)
        set(v) = prefs.edit().putBoolean("daily_limit_enabled", v).apply()

    var dailyLimitMinutes: Int
        get() = prefs.getInt("daily_limit_minutes", 120)
        set(v) = prefs.edit().putInt("daily_limit_minutes", v.coerceAtLeast(10)).apply()

    var curfewEnabled: Boolean
        get() = prefs.getBoolean("curfew_enabled", false)
        set(v) = prefs.edit().putBoolean("curfew_enabled", v).apply()

    var curfewStart: String
        get() = prefs.getString("curfew_start", "21:30") ?: "21:30"
        set(v) = prefs.edit().putString("curfew_start", v).apply()

    var curfewEnd: String
        get() = prefs.getString("curfew_end", "07:00") ?: "07:00"
        set(v) = prefs.edit().putString("curfew_end", v).apply()

    var autostart: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(v) = prefs.edit().putBoolean("autostart", v).apply()

    var licenseKey: String
        get() = prefs.getString("license_key", "") ?: ""
        set(v) = prefs.edit().putString("license_key", v.trim().uppercase()).apply()

    var trialLocksUsed: Int
        get() = prefs.getInt("trial_locks_used", 0)
        set(v) = prefs.edit().putInt("trial_locks_used", v).apply()

    val maxFreeTrialLocks: Int = 5

    fun isActivated(): Boolean = LicenseManager.verifyActivationCode(licenseKey)

    fun remainingTrialLocks(): Int = (maxFreeTrialLocks - trialLocksUsed).coerceAtLeast(0)

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", "") ?: ""
        val savedHash = prefs.getString("pin_hash", "") ?: ""
        if (salt.isEmpty() || savedHash.isEmpty()) {
            return pin.trim() == "1234"
        }
        return hashPin(pin.trim(), salt) == savedHash
    }

    fun setPin(newPin: String) {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        val salt = bytes.joinToString("") { "%02x".format(it) }
        val hash = hashPin(newPin.trim(), salt)
        prefs.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash)
            .apply()
    }

    fun ensureTodayStats() {
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString("stats_date", "")
        if (savedDate != today) {
            prefs.edit()
                .putString("stats_date", today)
                .putInt("today_used_seconds", 0)
                .putInt("today_rest_count", 0)
                .putInt("today_math_unlock_count", 0)
                .apply()
        }
    }

    var todayUsedSeconds: Int
        get() {
            ensureTodayStats()
            return prefs.getInt("today_used_seconds", 0)
        }
        private set(v) = prefs.edit().putInt("today_used_seconds", v).apply()

    var todayRestCount: Int
        get() {
            ensureTodayStats()
            return prefs.getInt("today_rest_count", 0)
        }
        private set(v) = prefs.edit().putInt("today_rest_count", v).apply()

    var todayMathUnlockCount: Int
        get() {
            ensureTodayStats()
            return prefs.getInt("today_math_unlock_count", 0)
        }
        private set(v) = prefs.edit().putInt("today_math_unlock_count", v).apply()

    fun addUsedSeconds(delta: Int = 1) {
        ensureTodayStats()
        todayUsedSeconds = prefs.getInt("today_used_seconds", 0) + delta
    }

    fun recordRestCompleted(viaMath: Boolean) {
        ensureTodayStats()
        todayRestCount = prefs.getInt("today_rest_count", 0) + 1
        if (viaMath) {
            todayMathUnlockCount = prefs.getInt("today_math_unlock_count", 0) + 1
        }
    }

    companion object {
        fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun normalizeHhmm(raw: String, fallback: String): String {
            val parts = raw.trim().split(":")
            if (parts.size == 2) {
                val hh = parts[0].toIntOrNull()
                val mm = parts[1].toIntOrNull()
                if (hh != null && mm != null && hh in 0..23 && mm in 0..59) {
                    return "%02d:%02d".format(hh, mm)
                }
            }
            return fallback
        }

        fun isInCurfew(now: LocalTime, startHhmm: String, endHhmm: String): Boolean {
            val sParts = startHhmm.trim().split(":")
            val eParts = endHhmm.trim().split(":")
            if (sParts.size != 2 || eParts.size != 2) return false
            val sh = sParts[0].toIntOrNull() ?: return false
            val sm = sParts[1].toIntOrNull() ?: return false
            val eh = eParts[0].toIntOrNull() ?: return false
            val em = eParts[1].toIntOrNull() ?: return false
            if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return false

            val nowMins = now.hour * 60 + now.minute
            val startMins = sh * 60 + sm
            val endMins = eh * 60 + em
            if (startMins == endMins) return false
            return if (startMins < endMins) {
                nowMins in startMins until endMins
            } else {
                nowMins >= startMins || nowMins < endMins
            }
        }
    }
}
