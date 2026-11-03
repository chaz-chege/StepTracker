package com.chazc.steptracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DeviceCompatibility {
    fun openBackgroundSettings(context: Context) {
        val packageName = context.packageName
        val manufacturer = Build.MANUFACTURER.lowercase()

        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
        ) {
            val intents = listOf(
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerDetailsActivity"
                ).apply { putExtra("package_name", packageName) },
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            if (intents.first().resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intents.first())
                    return
                } catch (_: Exception) {
                }
            }
        }

        val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            context.startActivity(batteryIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}
