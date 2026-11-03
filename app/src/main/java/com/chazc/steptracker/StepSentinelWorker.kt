package com.chazc.steptracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs every 15 minutes (the WorkManager minimum). If the tracking service is not alive in this
 * process it tries to bring it back. Uses a process-local flag instead of the deprecated
 * ActivityManager.getRunningServices, and never lets a refused start crash the worker.
 */
class StepSentinelWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (StepListenerService.isRunning) {
            Log.d("StepSentinel", "Service is alive")
            return Result.success()
        }

        Log.d("StepSentinel", "Service is NOT running, attempting restart")
        try {
            ContextCompat.startForegroundService(
                applicationContext,
                android.content.Intent(applicationContext, StepListenerService::class.java)
            )
        } catch (e: Exception) {
            // Android 12+ can refuse a foreground start from the background. Next app open or boot fixes it.
            Log.w("StepSentinel", "Restart refused: ${e.message}")
        }
        return Result.success()
    }
}
