package com.chazc.steptracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED // service is killed on every reinstall/update
        if (!shouldStart) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("BootReceiver", "Activity recognition not granted, not starting service")
            return
        }

        Log.d("BootReceiver", "Starting StepListenerService after $action")
        try {
            ContextCompat.startForegroundService(context, Intent(context, StepListenerService::class.java))
        } catch (e: Exception) {
            Log.w("BootReceiver", "Could not start service: ${e.message}")
        }
    }
}
