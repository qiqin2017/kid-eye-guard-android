package com.kideyeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = GuardPrefs(context)
            if (prefs.autostart && prefs.guardEnabled) {
                val svc = Intent(context, EyeGuardService::class.java)
                ContextCompat.startForegroundService(context, svc)
            }
        }
    }
}
