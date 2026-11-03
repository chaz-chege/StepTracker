package com.chazc.steptracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chazc.steptracker.ui.theme.BandageWhite
import kotlinx.coroutines.launch
import com.chazc.steptracker.ui.theme.CharacterRegistry
import com.chazc.steptracker.ui.theme.ThemeConfig
import com.chazc.steptracker.ui.theme.HaramakiGreen
import com.chazc.steptracker.ui.theme.KatanaGold
import com.chazc.steptracker.ui.theme.LocalCharacterTheme
import com.chazc.steptracker.ui.theme.OnigiriBlack
import com.chazc.steptracker.ui.theme.StepTrackerTheme
import kotlin.math.cos
import kotlin.math.sin

enum class Screen { HUD, SETTINGS, HISTORY, DOJO, BOUNTY, GRAND_LINE }

class MainActivity : ComponentActivity() {

    // Compose reads this, so the setup guide appears only once the core permission is in place.
    private var corePermissionGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            corePermissionGranted = hasCorePermission()
            // Software counting (accelerometer) works without physical-activity permission.
            // Start the service regardless so a denied hardware permission cannot disable tracking.
            startStepService()
            if (corePermissionGranted) {
                sendSessionAction(StepListenerService.ACTION_RECONFIGURE_SENSORS)
            }
            if (!corePermissionGranted) {
                Toast.makeText(
                    this,
                    "Using software step counting. Physical activity permission is optional and enables hardware sensors.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /** ACTIVITY_RECOGNITION is a runtime permission from Android 10. Without it the service cannot count. */
    private fun hasCorePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize character theme
        val savedCharacter = AppSettings.getSelectedCharacter(this)
        StepTrackerState.updateCharacter(CharacterRegistry.getByName(savedCharacter))

        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        StepTrackerState.updateSensorStatus(
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
            sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        )

        setContent {
            StepTrackerTheme {
                var currentScreen by remember { mutableStateOf(Screen.HUD) }
                var showKeepAliveGuide by remember {
                    mutableStateOf(KeepAlive.isXiaomiFamily() && !AppSettings.isKeepAliveGuideDone(this))
                }
                if (showKeepAliveGuide && corePermissionGranted) {
                    KeepAliveDialog(onDismiss = {
                        AppSettings.setKeepAliveGuideDone(this, true)
                        showKeepAliveGuide = false
                    })
                }

                when (currentScreen) {
                    Screen.SETTINGS -> SettingsScreen(onBack = { currentScreen = Screen.HUD })
                    Screen.HISTORY -> HistoryScreen(onBack = { currentScreen = Screen.HUD })
                    Screen.DOJO -> DojoScreen(onBack = { currentScreen = Screen.HUD })
                    Screen.BOUNTY -> BountyScreen(onBack = { currentScreen = Screen.HUD })
                    Screen.GRAND_LINE -> GrandLineScreen(onBack = { currentScreen = Screen.HUD })
                    Screen.HUD -> TrainingScreen(
                        onStart = { sendSessionAction(StepListenerService.ACTION_START_SESSION) },
                        onStop = { sendSessionAction(StepListenerService.ACTION_STOP_SESSION) },
                        onGoalChange = { newGoal ->
                            AppSettings.setDailyGoal(this, newGoal)
                            StepTrackerState.updateDailyGoal(newGoal)
                            // Trigger a widget refresh to show the new goal
                            sendSessionAction(StepListenerService.ACTION_REFRESH_WIDGET)
                        },
                        onOpenSettings = { currentScreen = Screen.SETTINGS },
                        onOpenHistory = { currentScreen = Screen.HISTORY },
                        onOpenDojo = { currentScreen = Screen.DOJO },
                        onOpenBounty = { currentScreen = Screen.BOUNTY },
                        onOpenGrandLine = { currentScreen = Screen.GRAND_LINE }
                    )
                }
            }
        }

        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)

        corePermissionGranted = hasCorePermission()
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startStepService()
        }
    }

    override fun onStart() {
        super.onStart()
        // Nudge the service every time the app comes to the front. It is idempotent, and it revives
        // tracking if the system killed the service while the app was in the background.
        corePermissionGranted = hasCorePermission()
        startStepService()
    }

    private fun startStepService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, StepListenerService::class.java))
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Could not start step service: ${e.message}")
        }
    }

    private fun sendSessionAction(action: String) {
        val intent = Intent(this, StepListenerService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun TrainingScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGoalChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDojo: () -> Unit,
    onOpenBounty: () -> Unit,
    onOpenGrandLine: () -> Unit
) {
    val sessionActive by StepTrackerState.isSessionActive.collectAsStateWithLifecycle()
    val steps by StepTrackerState.steps.collectAsStateWithLifecycle()
    val distanceKm by StepTrackerState.distanceKm.collectAsStateWithLifecycle()
    val dailyDistanceKm by StepTrackerState.dailyDistanceKm.collectAsStateWithLifecycle()
    val paceMinPerKm by StepTrackerState.paceMinPerKm.collectAsStateWithLifecycle()
    val calories by StepTrackerState.calories.collectAsStateWithLifecycle()
    val activeMinutes by StepTrackerState.activeMinutes.collectAsStateWithLifecycle()
    val dailyGoal by StepTrackerState.dailyGoal.collectAsStateWithLifecycle()
    val streakCount by StepTrackerState.streakCount.collectAsStateWithLifecycle()
    val heavyWeights by StepTrackerState.heavyWeightsEnabled.collectAsStateWithLifecycle()
    
    val theme = LocalCharacterTheme.current
    val dailyQuote = remember(theme) { QuotesProvider.getQuoteOfDay(theme) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-screen character background image
            if (theme.backgroundRes != null) {
                Image(
                    painter = painterResource(id = theme.backgroundRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f
                )
            }

            // Subtle character background sheen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (theme.backgroundRes != null) Color.Black.copy(alpha = 0.6f) else theme.primary.copy(alpha = 0.08f),
                                Color.Transparent,
                                if (theme.backgroundRes != null) Color.Black.copy(alpha = 0.4f) else theme.primary.copy(alpha = 0.05f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\"$dailyQuote\"",
                    color = BandageWhite.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = "- ${theme.moniker}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                SantoryuGauge(
                    steps = steps,
                    goal = dailyGoal,
                    streak = streakCount,
                    theme = theme,
                    onGoalClick = {
                        val nextGoal = when (dailyGoal) {
                            5000 -> 10000
                            10000 -> 15000
                            else -> 5000
                        }
                        onGoalChange(nextGoal)
                    }
                )

                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (streakCount > 0) {
                        Text(
                            text = "🔥 $streakCount DAY STREAK", 
                            color = KatanaGold, 
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    if (heavyWeights) {
                        Text(
                            text = "🏋️ WEIGHTS ON", 
                            color = HaramakiGreen, 
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onOpenSettings) {
                        Text(text = "SETTINGS", color = KatanaGold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onOpenHistory) {
                        Text(text = "HISTORY", color = KatanaGold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onOpenDojo) {
                        Text(text = "DOJO", color = KatanaGold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onOpenBounty) {
                        Text(text = "WANTED", color = KatanaGold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onOpenGrandLine) {
                        Text(text = "MAP", color = KatanaGold, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StatCard(label = "TOTAL DISTANCE", value = "%.2f km".format(dailyDistanceKm), iconRes = R.drawable.ic_distance)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            StatCard(label = "PACE", value = formatPaceDisplay(paceMinPerKm), iconRes = R.drawable.ic_pace)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StatCard(label = theme.stats.caloriesLabel, value = "$calories kcal", iconRes = R.drawable.ic_calories)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            StatCard(label = theme.stats.activeLabel, value = "$activeMinutes min", iconRes = R.drawable.ic_heartbeat)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (sessionActive) {
                    Text(
                        text = "SESSION: %.2f km".format(distanceKm),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        if (sessionActive) onStop() else onStart()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionActive) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        contentColor = BandageWhite
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        text = if (sessionActive) "END TRAINING" else "BEGIN TRAINING",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            shareBitmap(context, bitmap)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = KatanaGold
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(text = "SHARE TRAINING LOG", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val saved = saveBitmapToGallery(context, bitmap)
                            val message = if (saved) "Saved to Pictures/StepTracker" else "Save failed"
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = KatanaGold
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(text = "SAVE TO GALLERY", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Overlay: alpha(0f) keeps it fully drawn (so capture works) without
            // taking up space in the visible Column above, since it's a sibling
            // in this Box rather than nested inside the Column's layout flow.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .alpha(0f)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                AchievementCard(
                    steps = steps,
                    distanceKm = distanceKm,
                    calories = calories,
                    goal = dailyGoal,
                    paceMinPerKm = paceMinPerKm,
                    modifier = Modifier.width(360.dp)
                )
            }
        }
    }
}

fun formatPaceDisplay(paceMinPerKm: Float): String {
    if (paceMinPerKm <= 0f) return "-- /km"
    val minutes = paceMinPerKm.toInt()
    val seconds = ((paceMinPerKm - minutes) * 60).toInt()
    return "%d:%02d /km".format(minutes, seconds)
}

@Composable
fun SantoryuGauge(steps: Int, goal: Int, streak: Int, theme: ThemeConfig, onGoalClick: () -> Unit) {
    val targetProgress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 700),
        label = "gaugeProgress"
    )

    val isArmament = streak >= 3
    val isConqueror = streak >= 7

    val ringColor = when {
        isConqueror -> Color(0xFF8B0000) // Dark Red
        isArmament -> Color(0xFF1A1A1A) // Black
        else -> theme.secondary
    }

    val glowColor = when {
        isConqueror -> Color.Red.copy(alpha = 0.3f)
        isArmament -> Color.Gray.copy(alpha = 0.2f)
        else -> theme.secondary.copy(alpha = 0.18f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clickable { onGoalClick() }
    ) {
        // Soft glow behind the ring
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

            drawArc(
                color = if (isArmament) Color.Black.copy(alpha = 0.6f) else theme.primary.copy(alpha = 0.25f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(diameter, diameter), topLeft = topLeft
            )
            drawArc(
                color = ringColor,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(diameter, diameter), topLeft = topLeft
            )

            // Pattern Details based on character soul
            val radius = diameter / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            when (theme.gaugePattern) {
                com.chazc.steptracker.ui.theme.GaugePattern.CROSSES -> {
                    // Law's Crosses
                    for (i in 0 until 4) {
                        val angle = (-90f + i * 90f)
                        val angleRad = Math.toRadians(angle.toDouble())
                        val pos = Offset(
                            x = center.x + (radius * cos(angleRad)).toFloat(),
                            y = center.y + (radius * sin(angleRad)).toFloat()
                        )
                        drawLine(color = theme.primary, start = pos - Offset(8f, 0f), end = pos + Offset(8f, 0f), strokeWidth = 2.dp.toPx())
                        drawLine(color = theme.primary, start = pos - Offset(0f, 8f), end = pos + Offset(0f, 8f), strokeWidth = 2.dp.toPx())
                    }
                }
                com.chazc.steptracker.ui.theme.GaugePattern.FLAMES -> {
                    // Sanji's Flames (flickering details)
                    for (i in 0 until 8) {
                        val angle = (System.currentTimeMillis() / 150 + i * 45) % 360
                        val angleRad = Math.toRadians(angle.toDouble())
                        val start = Offset(center.x + (radius * 0.85f * cos(angleRad)).toFloat(), center.y + (radius * 0.85f * sin(angleRad)).toFloat())
                        val end = Offset(center.x + (radius * 1.1f * cos(angleRad)).toFloat(), center.y + (radius * 1.1f * sin(angleRad)).toFloat())
                        drawLine(color = theme.tertiary.copy(alpha = 0.6f), start = start, end = end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                com.chazc.steptracker.ui.theme.GaugePattern.SWIRLS -> {
                    // Nami's Swirls
                    for (i in 0 until 3) {
                        val angle = (-90f + i * 120f)
                        val angleRad = Math.toRadians(angle.toDouble())
                        drawCircle(color = theme.secondary.copy(alpha = 0.4f), radius = 10.dp.toPx(), center = Offset(center.x + (radius * cos(angleRad)).toFloat(), center.y + (radius * sin(angleRad)).toFloat()), style = Stroke(2.dp.toPx()))
                    }
                }
                else -> {
                    // Default Notches
                    val notches = theme.notchCount
                    for (i in 0 until notches) {
                        val angleRad = Math.toRadians((-90f + i * (360f / notches)).toDouble())
                        val notch = Offset(
                            x = center.x + (radius * cos(angleRad)).toFloat(),
                            y = center.y + (radius * sin(angleRad)).toFloat()
                        )
                        drawCircle(color = if (isArmament) Color.DarkGray else OnigiriBlack, radius = strokeWidth / 2 + 2.dp.toPx(), center = notch)
                        drawCircle(color = if (isConqueror) Color.Red else BandageWhite, radius = strokeWidth / 4, center = notch)
                    }
                }
            }
            
            // Conqueror's Haki Lightning (Red streaks)
            if (isConqueror) {
                for (i in 0 until 5) {
                    val angle = (System.currentTimeMillis() / 100 + i * 72) % 360
                    val angleRad = Math.toRadians(angle.toDouble())
                    val start = Offset(
                        x = center.x + (radius * 0.9f * cos(angleRad)).toFloat(),
                        y = center.y + (radius * 0.9f * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (radius * 1.2f * cos(angleRad + 0.1)).toFloat(),
                        y = center.y + (radius * 1.2f * sin(angleRad + 0.1)).toFloat()
                    )
                    drawLine(color = Color.Red, start = start, end = end, strokeWidth = 2.dp.toPx())
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = theme.accentSymbol, 
                style = MaterialTheme.typography.headlineLarge, 
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "$steps", 
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), 
                color = if (isArmament) Color.White else BandageWhite
            )
            Text(
                text = theme.stats.stepsLabel, 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), 
                color = if (isArmament) Color.Gray else theme.secondary
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, iconRes: Int) {
    val theme = LocalCharacterTheme.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.1f)),
        modifier = Modifier.widthIn(min = 130.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(theme.secondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value, 
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), 
                color = BandageWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label, 
                style = MaterialTheme.typography.labelSmall, 
                color = theme.secondary.copy(alpha = 0.8f)
            )
        }
    }
}
