package com.kideyeguard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class GuardKeepAliveJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = GuardPrefs(this)
        if (prefs.guardEnabled) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, EyeGuardService::class.java))
            } catch (_: Exception) {
            }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 2001

        fun schedule(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, GuardKeepAliveJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }
    }
}
