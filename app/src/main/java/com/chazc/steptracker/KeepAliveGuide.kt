package com.chazc.steptracker

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Helpers for keeping the tracking service alive on Xiaomi, Redmi and POCO phones (MIUI / HyperOS).
 * These skins kill background apps far more aggressively than stock Android, and no code inside
 * the app can switch that off. The user has to grant it in system screens, so we open those
 * screens directly.
 *
 * The MIUI screens are private, undocumented activities that differ between MIUI versions, so every
 * jump is wrapped and falls back to the standard app-details page.
 */
object KeepAlive {
    private const val TAG = "KeepAlive"

    fun isXiaomiFamily(): Boolean {
        val maker = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return maker.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (!launch(context, request)) launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** MIUI "Autostart" list. Without it MIUI will not restart the service after killing it. */
    fun openAutostart(context: Context) {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!launch(context, miui)) openAppDetails(context)
    }

    /** MIUI per-app battery saver page, where "No restrictions" must be chosen. */
    fun openBatterySaver(context: Context) {
        val miui = Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
        if (!launch(context, miui)) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (!launch(context, details)) {
            Toast.makeText(context, "Open Settings > Apps > StepTracker", Toast.LENGTH_LONG).show()
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Screen not available: ${intent.action ?: intent.component}")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Screen not permitted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not open screen: ${e.message}")
            false
        }
}

@Composable
fun KeepAliveDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep step tracking alive") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Your phone's battery manager can stop StepTracker when the screen is off, " +
                        "and the app cannot prevent that by itself. Do these three things once:"
                )
                Text("1. Autostart: turn StepTracker ON.")
                OutlinedButton(onClick = { KeepAlive.openAutostart(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Autostart")
                }
                Text("2. Battery saver: choose No restrictions.")
                OutlinedButton(onClick = { KeepAlive.openBatterySaver(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open battery settings")
                }
                Text("3. Allow unrestricted battery use.")
                OutlinedButton(
                    onClick = { KeepAlive.requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow unlimited background")
                }
                Text(
                    "Also lock the app: open Recent apps, pull the StepTracker card down or hold it, " +
                        "and tap the lock icon. Never swipe it away."
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { KeepAlive.openAppDetails(context) }) { Text("App info") } }
    )
}
