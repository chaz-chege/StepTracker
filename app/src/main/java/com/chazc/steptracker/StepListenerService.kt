package com.chazc.steptracker

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Foreground service that counts steps and (during a session) measures distance.
 *
 * Threading model: every piece of mutable tracking state is touched ONLY on [handler], the
 * dedicated sensor thread. Sensor events, GPS fixes, the 30 s ticker and every start command
 * are all delivered or posted there, so there are no data races between them. The only code
 * that runs elsewhere is onCreate (before anything is posted) and the screen-on receiver,
 * which just posts back to [handler].
 *
 * Counting source, chosen once in onCreate:
 *  1. TYPE_STEP_COUNTER  (hardware, survives this process dying; missed steps are recovered)
 *  2. TYPE_STEP_DETECTOR (hardware, one event per step)
 *  3. Accelerometer + [StepDetector] (software; needs the CPU awake, so a wake lock is held)
 * Only the chosen source is registered, so a phone that has both a detector and an
 * accelerometer can no longer double count.
 */
class StepListenerService : Service(), SensorEventListener {

    private enum class CountMode { HARDWARE_COUNTER, HARDWARE_DETECTOR, ACCELEROMETER }

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var mode = CountMode.ACCELEROMETER

    private lateinit var sensorThread: HandlerThread
    private lateinit var handler: Handler
    private var initialized = false
    private var sensorsRegistered = false
    private var screenReceiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val accelDetector = StepDetector { count, _ -> onAccelerometerSteps(count) }

    // --- Daily counters (persisted) ---
    private var stepCount = 0
    private var lastHardwareTotal = -1f
    private var lastBootCount = -1
    private var lifetimeSteps = 0
    private var dailyDistanceMeters = 0f
    private var lastRecordedDate = ""
    private var streakCount = 0
    private var goalSoundPlayedToday = false

    // Cached "today" so we do not allocate a date formatter on every step.
    private var dayEndMs = 0L
    private var todayString = ""

    // --- Step timing (SystemClock.elapsedRealtime, immune to wall-clock changes) ---
    private var lastConfirmedStepMs = 0L

    // --- Session state ---
    private var sessionActive = false
    private var isAutoSession = false
    private var isSilentSession = false
    private var sessionDistanceMeters = 0f
    private var sessionStartElapsedMs = 0L
    private var sessionStartWallMs = 0L
    private var firstMovementMs = 0L
    private var sessionStartSteps = 0
    private var verificationStartSteps = 0
    private var lastAnnouncedKm = 0
    private var stepsInWindow = 0
    private var windowStartMs = 0L

    // --- GPS state ---
    private var gpsAnchor: Location? = null
    private var lastGpsCountedMs = 0L
    private var lastGpsSpeedMps = 0f
    private var lastGpsSpeedAtMs = 0L

    // --- Persistence / UI throttles ---
    private var stepsSinceLastPersist = 0
    private var lastPersistMs = 0L
    private var lastWidgetUpdateMs = 0L
    private var lastNotificationUpdateMs = 0L
    private var uiDirty = false
    private var channelCreated = false

    // --- Sound / speech ---
    private var soundPool: SoundPool? = null
    private var soundSwordStart = 0
    private var soundSwordEnd = 0
    private var soundGoalReached = 0
    private var soundSwordForge = 0
    private var soundBountyEarned = 0
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    companion object {
        const val TAG = "StepTracker"
        const val CHANNEL_ID = "step_tracker_channel"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "step_tracker_prefs"

        const val ACTION_START_SESSION = "com.chazc.steptracker.START_SESSION"
        const val ACTION_STOP_SESSION = "com.chazc.steptracker.STOP_SESSION"
        const val ACTION_REFRESH_WIDGET = "com.chazc.steptracker.REFRESH_WIDGET"
        const val ACTION_MIDNIGHT_RESET = "com.chazc.steptracker.MIDNIGHT_RESET"
        const val ACTION_TOGGLE_WAKELOCK = "com.chazc.steptracker.TOGGLE_WAKELOCK"
        const val ACTION_RECONFIGURE_SENSORS = "com.chazc.steptracker.RECONFIGURE_SENSORS"

        /** True while an instance of the service is alive in this process. Used by the sentinel worker. */
        @Volatile var isRunning = false
            private set

        // Sensor configuration
        private const val ACCEL_SAMPLING_US = 20_000          // 50 Hz
        private const val HARDWARE_BATCH_LATENCY_US = 1_000_000
        private const val MAX_PLAUSIBLE_BATCH_STEPS = 30_000  // a bigger jump than this is a sensor glitch

        // Housekeeping
        private const val TICK_MS = 30_000L
        private const val WAKELOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L // re-acquired by the ticker if it ever lapses
        private const val UI_THROTTLE_MS = 5_000L
        private const val NOTIFICATION_THROTTLE_SCREEN_OFF_MS = 60_000L
        private const val PERSIST_EVERY_STEPS = 10
        private const val PERSIST_EVERY_MS = 5_000L

        // Auto tracking
        const val AUTO_START_THRESHOLD = 25          // steps inside the window to start a silent session
        const val AUTO_START_WINDOW_MS = 30_000L
        const val AUTO_STOP_WINDOW_MS = 420_000L     // 7 min without steps ends an auto session
        const val SILENT_STOP_WINDOW_MS = 90_000L
        const val MANUAL_IDLE_TIMEOUT_MS = 60 * 60 * 1000L // forgotten manual session: stop after 1 h without steps
        private const val PROMOTE_STEPS = 75
        private const val PROMOTE_DISTANCE_METERS = 50f
        private const val MAX_LIVE_BATCH_STEPS = 25      // bigger batches are recovered steps, not a walk starting

        // GPS
        private const val GPS_INTERVAL_MS = 1_000L
        const val MIN_ACCURACY_METERS = 25f
        private const val MIN_SEGMENT_METERS = 2f        // smaller moves are GPS jitter, anchor is kept
        private const val MAX_SEGMENT_METERS = 100f
        private const val GRACE_MIN_SPEED_MPS = 1.0f
        const val MAX_WALKING_SPEED_MPS = 6.0f           // about 21.6 km/h
        const val LOCATION_FIX_MAX_AGE_MS = 15_000L
        const val MAX_MS_SINCE_STEP_FOR_DISTANCE = 90_000L
        const val MANUAL_START_GRACE_PERIOD_MS = 60_000L
        private const val GPS_FRESH_MS = 10_000L

        // Calories: about 0.75 kcal per kg per km walked. Stride comes from height.
        private const val KCAL_PER_KG_PER_KM = 0.75f
        private const val HEAVY_WEIGHTS_MULTIPLIER = 1.5f

        private val FORGE_MILESTONES = intArrayOf(25_000, 100_000, 250_000)
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        // A service started with startForegroundService must call startForeground within seconds,
        // so do it before any other work. If it is refused (missing permission on Android 14+),
        // stop cleanly instead of crashing.
        if (!startInForeground()) {
            Log.e(TAG, "Could not enter foreground, stopping service")
            stopSelf()
            return
        }

        isRunning = true
        sensorThread = HandlerThread("StepSensorThread").apply { start() }
        handler = Handler(sensorThread.looper)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        mode = when {
            hasActivityRecognition() && stepCounterSensor != null -> CountMode.HARDWARE_COUNTER
            hasActivityRecognition() && stepDetectorSensor != null -> CountMode.HARDWARE_DETECTOR
            else -> CountMode.ACCELEROMETER
        }
        StepTrackerState.updateSensorStatus(stepCounterSensor != null, stepDetectorSensor != null)
        Log.d(TAG, "Counting mode: $mode")

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        stepCount = prefs.getInt("current_steps", 0)
        lastHardwareTotal = prefs.getFloat("last_hardware_total", -1f)
        lastBootCount = prefs.getInt("last_boot_count", -1)
        dailyDistanceMeters = prefs.getFloat("daily_distance", 0f)
        lastRecordedDate = prefs.getString("last_date", null) ?: today()
        goalSoundPlayedToday = prefs.getBoolean("goal_sound_played_$lastRecordedDate", false)
        streakCount = AppSettings.getStreakCount(this)
        lifetimeSteps = AppSettings.getLifetimeSteps(this)

        checkMidnightReset()

        StepTrackerState.updateSteps(stepCount)
        StepTrackerState.updateDailyDistance(dailyDistanceMeters)
        StepTrackerState.updateMetrics(caloriesForSteps(stepCount), stepCount / 100)
        StepTrackerState.updatePace(0f)
        StepTrackerState.updateStreak(streakCount)
        StepTrackerState.updateHeavyWeights(AppSettings.isHeavyWeightsEnabled(this))
        StepTrackerState.updateDailyGoal(AppSettings.getDailyGoal(this))

        initSoundPool()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }

        registerScreenReceiver()
        scheduleMidnightAlarm()
        scheduleSentinelWorker()

        initialized = true
        handler.post {
            registerSensors()
            syncWakeLock()
        }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!initialized) {
            stopSelf()
            return START_NOT_STICKY
        }
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action")
        handler.post { handleCommand(action) }
        return START_STICKY
    }

    private fun handleCommand(action: String?) {
        try {
            if (action == ACTION_RECONFIGURE_SENSORS) reconfigureSensors()
            registerSensors() // idempotent, also recovers if registration failed earlier
            when (action) {
                ACTION_START_SESSION -> startSession(auto = false, silent = false)
                ACTION_STOP_SESSION -> stopSession()
                ACTION_REFRESH_WIDGET -> {
                    reloadSettings()
                    updateWidget(force = true)
                    updateNotification(force = true)
                }
                ACTION_MIDNIGHT_RESET -> {
                    checkMidnightReset()
                    scheduleMidnightAlarm()
                }
                ACTION_TOGGLE_WAKELOCK -> syncWakeLock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleCommand failed for $action", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: app swiped away")
        if (initialized) handler.post { persistCurrentTotals() }

        // Best effort restart. Many Android skins (MIUI included) kill the whole process on swipe
        // and cancel its alarms, in which case this cannot run. The reliable fix is the
        // "Autostart" and "No restrictions" settings, which the in-app guide walks through.
        try {
            val restartIntent = Intent(applicationContext, StepListenerService::class.java).apply {
                setPackage(packageName)
            }
            val pendingIntent = servicePendingIntent(1, restartIntent, oneShot = true)
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2_000L, pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule restart: ${e.message}")
        }
    }

    override fun onDestroy() {
        isRunning = false
        if (!initialized) {
            super.onDestroy()
            return
        }
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        handler.removeCallbacksAndMessages(null)
        // Final tidy-up runs on the sensor thread so it cannot race with a task that is mid-way.
        handler.post {
            try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
            persistCurrentTotals()
            if (wakeLock?.isHeld == true) wakeLock?.release()
            try { soundPool?.release() } catch (_: Exception) {}
            soundPool = null
            tts?.stop()
            tts?.shutdown()
            sensorThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------------------------------------

    private fun startInForeground(): Boolean {
        val notification = buildNotification("Listening for steps...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = foregroundServiceType()
                if (type != 0) startForeground(NOTIFICATION_ID, notification, type)
                else startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground refused: ${e.message}")
            false
        }
    }

    /** Only declare a type whose runtime permission is granted, otherwise Android 14+ throws. */
    private fun foregroundServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasActivityRecognition()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        if (hasFineLocation()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return type
    }

    private fun hasActivityRecognition(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Step Tracker Service", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        channelCreated = true
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Step Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun notificationText(): String =
        if (sessionActive && !isSilentSession) {
            "Training: $stepCount steps | ${formatDistance(sessionDistanceMeters)}"
        } else {
            "Steps: $stepCount | ${formatDistance(dailyDistanceMeters)}"
        }

    private fun updateNotification(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val interval = if (isScreenOn()) UI_THROTTLE_MS else NOTIFICATION_THROTTLE_SCREEN_OFF_MS
        if (!force && now - lastNotificationUpdateMs < interval) {
            uiDirty = true
            return
        }
        lastNotificationUpdateMs = now
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(notificationText()))
        } catch (e: Exception) {
            Log.w(TAG, "notify failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sensors
    // ------------------------------------------------------------------------------------------

    private fun reconfigureSensors() {
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        mode = when {
            hasActivityRecognition() && stepCounterSensor != null -> CountMode.HARDWARE_COUNTER
            hasActivityRecognition() && stepDetectorSensor != null -> CountMode.HARDWARE_DETECTOR
            else -> CountMode.ACCELEROMETER
        }
        Log.d(TAG, "Reconfigured counting mode: $mode")
        syncWakeLock()
    }

    private fun registerSensors() {
        if (sensorsRegistered) return

        var ok = false
        try {
            when (mode) {
                CountMode.HARDWARE_COUNTER -> ok = sensorManager.registerListener(
                    this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL, HARDWARE_BATCH_LATENCY_US, handler
                )
                CountMode.HARDWARE_DETECTOR -> ok = sensorManager.registerListener(
                    this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL, 0, handler
                )
                CountMode.ACCELEROMETER -> Unit
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Hardware sensor permission revoked", e)
            ok = false
        }

        if (!ok && mode != CountMode.ACCELEROMETER) {
            // Typically ACTIVITY_RECOGNITION not granted, which makes the hardware sensors silent.
            Log.w(TAG, "Hardware step sensor registration failed, falling back to accelerometer")
            mode = CountMode.ACCELEROMETER
        }
        if (mode == CountMode.ACCELEROMETER) {
            val sensor = accelerometer
            ok = sensor != null && sensorManager.registerListener(this, sensor, ACCEL_SAMPLING_US, handler)
            if (ok) accelDetector.reset()
        }
        sensorsRegistered = ok
        if (!ok) Log.e(TAG, "No usable step sensor could be registered")
        syncWakeLock()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        try {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> if (mode == CountMode.HARDWARE_COUNTER) handleHardwareCounter(event.values[0])
                Sensor.TYPE_STEP_DETECTOR -> if (mode == CountMode.HARDWARE_DETECTOR) addSteps(1)
                Sensor.TYPE_ACCELEROMETER -> if (mode == CountMode.ACCELEROMETER) {
                    accelDetector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
            }
        } catch (e: Exception) {
            // One bad event must never take the counting service down.
            Log.e(TAG, "onSensorChanged failed", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun currentBootCount(): Int =
        try {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }

    private fun handleHardwareCounter(currentTotal: Float) {
        val boot = currentBootCount()

        if (lastHardwareTotal < 0f) {
            // First ever reading: this is only a baseline, nothing to credit.
            lastHardwareTotal = currentTotal
            lastBootCount = boot
            persistCurrentTotals()
            return
        }

        // The hardware counter restarts from zero at every boot. Comparing boot counts catches the
        // case the old code missed: after a reboot the new total can already be larger than the old one.
        val rebooted = boot != -1 && lastBootCount != -1 && boot != lastBootCount
        val diff = if (rebooted || currentTotal < lastHardwareTotal) currentTotal else currentTotal - lastHardwareTotal

        lastHardwareTotal = currentTotal
        if (boot != -1) lastBootCount = boot

        when {
            diff > MAX_PLAUSIBLE_BATCH_STEPS -> {
                Log.w(TAG, "Ignoring implausible counter jump of ${diff.toInt()} steps")
                persistCurrentTotals()
            }
            diff >= 1f -> {
                if (diff > MAX_LIVE_BATCH_STEPS) Log.d(TAG, "Recovered ${diff.toInt()} steps from hardware")
                addSteps(diff.toInt())
            }
        }
    }

    private fun onAccelerometerSteps(count: Int) {
        // Rough vehicle filter: a fresh GPS fix faster than walking pace means these are bumps.
        val now = SystemClock.elapsedRealtime()
        if (lastGpsSpeedMps > MAX_WALKING_SPEED_MPS && now - lastGpsSpeedAtMs < LOCATION_FIX_MAX_AGE_MS) return
        addSteps(count)
    }

    // ------------------------------------------------------------------------------------------
    // Counting core
    // ------------------------------------------------------------------------------------------

    private fun addSteps(n: Int) {
        if (n <= 0) return
        checkMidnightReset()
        val nowMs = SystemClock.elapsedRealtime()

        stepCount += n
        lastConfirmedStepMs = nowMs
        accumulateStepDistance(n, nowMs)

        val lifetimeBefore = lifetimeSteps
        lifetimeSteps += n

        if (!sessionActive && n <= MAX_LIVE_BATCH_STEPS) {
            if (nowMs - windowStartMs > AUTO_START_WINDOW_MS) {
                windowStartMs = nowMs
                stepsInWindow = n
            } else {
                stepsInWindow += n
                if (stepsInWindow >= AUTO_START_THRESHOLD) {
                    Log.d(TAG, "Auto-start threshold met ($stepsInWindow steps). Silent verification begins.")
                    startSession(auto = true, silent = true)
                    stepsInWindow = 0
                }
            }
        }
        if (isSilentSession && stepCount - verificationStartSteps >= PROMOTE_STEPS) promoteSilentSession()

        StepTrackerState.updateSteps(stepCount)
        StepTrackerState.updateMetrics(caloriesForSteps(stepCount), stepCount / 100)

        BountyManager.checkBounties(this, BountyType.LIFETIME_STEPS, lifetimeSteps.toFloat()) {
            playSound(soundBountyEarned)
            speak("Bounty collected!")
        }
        for (milestone in FORGE_MILESTONES) {
            if (lifetimeBefore < milestone && lifetimeSteps >= milestone) {
                playSound(soundSwordForge)
                speak("A new blade has been forged!")
            }
        }
        if (stepCount >= AppSettings.getDailyGoal(this) && !goalSoundPlayedToday) {
            playSound(soundGoalReached)
            goalSoundPlayedToday = true
        }

        updateWidget()
        updateNotification()
        gatedPersist(n)
    }

    /**
     * Distance from stride length. While GPS is measuring the same stretch we stay out of its way
     * so nothing is counted twice; indoors, or with no fix, steps take over (dead reckoning).
     */
    private fun accumulateStepDistance(n: Int, nowMs: Long) {
        val gpsIsMeasuring = sessionActive && nowMs - lastGpsCountedMs < GPS_FRESH_MS
        if (gpsIsMeasuring) return

        val meters = n * AppSettings.getStrideMeters(this)
        dailyDistanceMeters += meters
        StepTrackerState.updateDailyDistance(dailyDistanceMeters)
        if (sessionActive) {
            if (firstMovementMs == 0L) firstMovementMs = nowMs
            sessionDistanceMeters += meters
            StepTrackerState.updateDistance(sessionDistanceMeters)
            updatePace(nowMs)
        }
    }

    private fun kcalPerStep(): Float {
        val multiplier = if (AppSettings.isHeavyWeightsEnabled(this)) HEAVY_WEIGHTS_MULTIPLIER else 1f
        return KCAL_PER_KG_PER_KM * AppSettings.getWeightKg(this) * (AppSettings.getStrideMeters(this) / 1000f) * multiplier
    }

    private fun caloriesForSteps(steps: Int): Int = (steps * kcalPerStep()).toInt()

    private fun reloadSettings() {
        StepTrackerState.updateDailyGoal(AppSettings.getDailyGoal(this))
        StepTrackerState.updateHeavyWeights(AppSettings.isHeavyWeightsEnabled(this))
        StepTrackerState.updateMetrics(caloriesForSteps(stepCount), stepCount / 100)
        syncWakeLock()
    }

    // ------------------------------------------------------------------------------------------
    // Day rollover
    // ------------------------------------------------------------------------------------------

    private fun today(): String {
        val now = System.currentTimeMillis()
        if (todayString.isEmpty() || now >= dayEndMs) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }
            dayEndMs = cal.timeInMillis
            todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        }
        return todayString
    }

    private fun checkMidnightReset() {
        val today = today()
        if (today == lastRecordedDate) return
        Log.d(TAG, "Day rollover: $lastRecordedDate -> $today")

        // Keep yesterday's final numbers; the old code threw them away.
        HistoryManager.saveDailyTotals(this, lastRecordedDate, stepCount, dailyDistanceMeters, caloriesForSteps(stepCount))

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            .let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it.time) }
        val goal = AppSettings.getDailyGoal(this)
        if (stepCount >= goal) {
            streakCount = if (lastRecordedDate == yesterday) streakCount + 1 else 1
        } else if (lastRecordedDate != yesterday) {
            streakCount = 0
        }
        AppSettings.setStreakCount(this, streakCount)
        StepTrackerState.updateStreak(streakCount)

        stepCount = 0
        dailyDistanceMeters = 0f
        lastRecordedDate = today
        goalSoundPlayedToday = false
        StepTrackerState.updateSteps(0)
        StepTrackerState.updateDailyDistance(0f)
        StepTrackerState.updateMetrics(0, 0)
        persistCurrentTotals()
    }

    private fun scheduleMidnightAlarm() {
        try {
            val intent = Intent(this, StepListenerService::class.java).apply { action = ACTION_MIDNIGHT_RESET }
            val pendingIntent = servicePendingIntent(0, intent, oneShot = false)
            val cal = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            // Inexact on purpose: the exact-alarm permission is not needed for a few seconds of slack,
            // and every step event also checks the date, so this is only a safety net for idle days.
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule midnight alarm: ${e.message}")
        }
    }

    private fun servicePendingIntent(requestCode: Int, intent: Intent, oneShot: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (oneShot) PendingIntent.FLAG_ONE_SHOT else PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sessions and GPS
    // ------------------------------------------------------------------------------------------

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Delivered on the sensor thread because that looper is passed to requestLocationUpdates.
            try {
                handleLocation(location)
            } catch (e: Exception) {
                Log.e(TAG, "handleLocation failed", e)
            }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun handleLocation(location: Location) {
        if (!sessionActive) return
        val nowMs = SystemClock.elapsedRealtime()

        if (location.hasSpeed()) {
            lastGpsSpeedMps = location.speed
            lastGpsSpeedAtMs = nowMs
        }
        if (!location.hasAccuracy() || location.accuracy > MIN_ACCURACY_METERS) return

        val anchor = gpsAnchor
        if (anchor == null) {
            gpsAnchor = location
            return
        }

        val segment = anchor.distanceTo(location)
        if (segment < MIN_SEGMENT_METERS) return // jitter, keep the anchor so real movement still adds up

        val dtSeconds = (location.elapsedRealtimeNanos - anchor.elapsedRealtimeNanos) / 1_000_000_000f
        gpsAnchor = location
        if (dtSeconds <= 0f) return

        val segmentSpeed = segment / dtSeconds
        val walkingSpeed = segmentSpeed < MAX_WALKING_SPEED_MPS &&
            (!location.hasSpeed() || location.speed < MAX_WALKING_SPEED_MPS)
        if (!walkingSpeed || segment > MAX_SEGMENT_METERS) return // vehicle or a GPS jump

        val hasRecentSteps = lastConfirmedStepMs > 0 && nowMs - lastConfirmedStepMs < MAX_MS_SINCE_STEP_FOR_DISTANCE
        val inGrace = !isAutoSession && nowMs - sessionStartElapsedMs < MANUAL_START_GRACE_PERIOD_MS &&
            segmentSpeed > GRACE_MIN_SPEED_MPS
        if (!hasRecentSteps && !inGrace) return

        if (firstMovementMs == 0L) firstMovementMs = nowMs
        sessionDistanceMeters += segment
        dailyDistanceMeters += segment
        lastGpsCountedMs = nowMs
        StepTrackerState.updateDistance(sessionDistanceMeters)
        StepTrackerState.updateDailyDistance(dailyDistanceMeters)

        if (isSilentSession && (dailyDistanceMeters >= PROMOTE_DISTANCE_METERS ||
                stepCount - verificationStartSteps >= PROMOTE_STEPS)) {
            promoteSilentSession()
        }

        updatePace(nowMs)
        val currentKm = (sessionDistanceMeters / 1000f).toInt()
        if (currentKm > lastAnnouncedKm) {
            lastAnnouncedKm = currentKm
            speak("$currentKm kilometer${if (currentKm > 1) "s" else ""}. Pace ${formatPace(StepTrackerState.paceMinPerKm.value)}")
        }

        updateWidget()
        updateNotification()
        gatedPersist(0)
    }

    private fun updatePace(nowMs: Long) {
        val distanceKm = sessionDistanceMeters / 1000f
        val elapsedMin = if (firstMovementMs > 0) (nowMs - firstMovementMs) / 60_000f else 0f
        StepTrackerState.updatePace(if (distanceKm > 0.05f) elapsedMin / distanceKm else 0f)
    }

    private fun startSession(auto: Boolean, silent: Boolean) {
        checkMidnightReset()
        if (sessionActive) {
            if (!auto) {
                isAutoSession = false
                if (isSilentSession) promoteSilentSession()
            }
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        sessionActive = true
        isSilentSession = silent
        isAutoSession = auto
        sessionStartElapsedMs = nowMs
        sessionStartWallMs = System.currentTimeMillis()
        sessionStartSteps = stepCount
        verificationStartSteps = stepCount
        firstMovementMs = 0L
        lastAnnouncedKm = 0
        sessionDistanceMeters = 0f
        gpsAnchor = null
        lastGpsCountedMs = 0L

        StepTrackerState.updatePace(0f)
        StepTrackerState.updateDistance(0f)
        if (!silent) {
            StepTrackerState.updateSessionStatus(true)
            playSound(soundSwordStart)
            speak("Training started")
        }

        syncWakeLock()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (!hasFineLocation()) {
            Log.w(TAG, "No location permission, session will use step-based distance")
            return
        }
        try {
            // GPS only. Network fixes jump by tens of metres and, mixed with GPS fixes, used to add
            // phantom distance. Without GPS (indoors) the step-based estimate takes over.
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, locationListener, handler.looper
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission revoked", e)
        }
    }

    private fun stopLocationUpdates() {
        try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
        gpsAnchor = null
    }

    private fun promoteSilentSession() {
        if (!isSilentSession) return
        Log.d(TAG, "Promoting silent session to active training")
        isSilentSession = false
        StepTrackerState.updateSessionStatus(true)
        playSound(soundSwordStart)
        speak("Training started")
    }

    private fun stopSession() {
        if (!sessionActive) return

        val wasSilent = isSilentSession
        val nowMs = SystemClock.elapsedRealtime()

        // Take every number we need BEFORE resetting anything. The old code zeroed the distance first,
        // so every saved session had 0 km and the 5 km bounty could never be earned.
        val finalDistance = sessionDistanceMeters
        val sessionSteps = (stepCount - sessionStartSteps).coerceAtLeast(0)
        val elapsedMin = (nowMs - sessionStartElapsedMs) / 60_000f
        val distanceKm = finalDistance / 1000f
        val avgPace = if (distanceKm > 0.05f) elapsedMin / distanceKm else 0f
        val record = SessionRecord(
            startTimeMs = sessionStartWallMs,
            endTimeMs = System.currentTimeMillis(),
            steps = sessionSteps,
            distanceMeters = finalDistance,
            calories = caloriesForSteps(sessionSteps),
            avgPaceMinPerKm = avgPace
        )

        sessionActive = false
        isSilentSession = false
        isAutoSession = false
        sessionDistanceMeters = 0f
        firstMovementMs = 0L
        stopLocationUpdates()
        StepTrackerState.updateSessionStatus(false)
        StepTrackerState.updatePace(0f)
        StepTrackerState.updateDistance(0f)
        syncWakeLock()

        if (wasSilent) {
            Log.d(TAG, "Discarded silent session (never met promotion criteria)")
            return
        }

        playSound(soundSwordEnd)
        speak("Training complete. ${formatDistance(finalDistance)}. Average pace ${formatPace(avgPace)}")
        HistoryManager.saveSession(this, record)
        BountyManager.checkBounties(this, BountyType.SESSION_DISTANCE, finalDistance) {
            playSound(soundBountyEarned)
            speak("Bounty collected!")
        }
        updateWidget(force = true)
        updateNotification(force = true)
        persistCurrentTotals()
    }

    private fun checkSessionTimeout(nowMs: Long) {
        if (!sessionActive) return
        val lastActivity = maxOf(lastConfirmedStepMs, sessionStartElapsedMs)
        val idle = nowMs - lastActivity
        val timeout = when {
            isSilentSession -> SILENT_STOP_WINDOW_MS
            isAutoSession -> AUTO_STOP_WINDOW_MS
            else -> MANUAL_IDLE_TIMEOUT_MS
        }
        if (idle > timeout) {
            Log.d(TAG, "Auto-stopping session after ${idle / 1000}s without steps")
            stopSession()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Ticker
    // ------------------------------------------------------------------------------------------

    private val ticker = object : Runnable {
        override fun run() {
            try {
                val nowMs = SystemClock.elapsedRealtime()
                checkMidnightReset()
                syncWakeLock()
                checkSessionTimeout(nowMs)
                if (stepsSinceLastPersist > 0) persistCurrentTotals()
                if (uiDirty && isScreenOn()) {
                    updateWidget(force = true)
                    updateNotification(force = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ticker failed", e)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Wake lock
    // ------------------------------------------------------------------------------------------

    /**
     * Without a hardware step sensor the accelerometer only delivers while the CPU is awake, so the
     * software counter needs a partial wake lock. The old code only held one when the user flipped
     * "Keep CPU Awake" (default off), and even then for just 10 minutes, so screen-off walking was
     * silently missed. Phones with a hardware counter do not need it.
     */
    private fun shouldHoldWakeLock(): Boolean =
        mode == CountMode.ACCELEROMETER || sessionActive || AppSettings.isHighAccuracyEnabled(this)

    private fun syncWakeLock() {
        try {
            if (shouldHoldWakeLock()) {
                val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StepTracker:Counting")
                    .apply { setReferenceCounted(false) }
                    .also { wakeLock = it }
                if (!lock.isHeld) lock.acquire(WAKELOCK_TIMEOUT_MS)
            } else if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock sync failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------------------------------
    // Screen state, widget, persistence
    // ------------------------------------------------------------------------------------------

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Screen just turned on: bring the widget and notification up to date right away.
            if (initialized) handler.post {
                updateWidget(force = true)
                updateNotification(force = true)
            }
        }
    }

    private fun registerScreenReceiver() {
        try {
            ContextCompat.registerReceiver(
                this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Screen receiver not registered: ${e.message}")
        }
    }

    private fun isScreenOn(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun updateWidget(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && (now - lastWidgetUpdateMs < UI_THROTTLE_MS || !isScreenOn())) {
            uiDirty = true
            return
        }
        lastWidgetUpdateMs = now
        uiDirty = false
        WidgetRenderer.update(this)
    }

    private fun gatedPersist(addedSteps: Int) {
        stepsSinceLastPersist += addedSteps
        val now = SystemClock.elapsedRealtime()
        if (stepsSinceLastPersist >= PERSIST_EVERY_STEPS || now - lastPersistMs > PERSIST_EVERY_MS) {
            persistCurrentTotals()
        }
    }

    private fun persistCurrentTotals() {
        // commit = true: this runs on the background sensor thread, and a synchronous write means a
        // process kill right after cannot lose the last few seconds (apply() writes asynchronously).
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putInt("current_steps", stepCount)
            putFloat("last_hardware_total", lastHardwareTotal)
            putInt("last_boot_count", lastBootCount)
            putFloat("daily_distance", dailyDistanceMeters)
            putString("last_date", lastRecordedDate)
            putBoolean("goal_sound_played_$lastRecordedDate", goalSoundPlayedToday)
        }
        AppSettings.setLifetimeSteps(this, lifetimeSteps)
        HistoryManager.saveDailyTotals(this, lastRecordedDate, stepCount, dailyDistanceMeters, caloriesForSteps(stepCount))
        stepsSinceLastPersist = 0
        lastPersistMs = SystemClock.elapsedRealtime()
    }

    // ------------------------------------------------------------------------------------------
    // Sentinel worker, sound, speech, formatting
    // ------------------------------------------------------------------------------------------

    private fun scheduleSentinelWorker() {
        try {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            val request = PeriodicWorkRequestBuilder<StepSentinelWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "StepSentinel", ExistingPeriodicWorkPolicy.KEEP, request
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule sentinel: ${e.message}")
        }
    }

    private fun initSoundPool() {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
            soundPool = pool

            fun load(name: String): Int {
                val id = resources.getIdentifier(name, "raw", packageName)
                return if (id != 0) pool.load(this, id, 1) else 0
            }
            soundSwordStart = load("sword_start")
            soundSwordEnd = load("sword_end")
            soundGoalReached = load("goal_reached")
            soundSwordForge = load("sword_forge")
            soundBountyEarned = load("bounty_earned")
        } catch (e: Exception) {
            Log.w(TAG, "SoundPool init failed: ${e.message}")
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0 && AppSettings.isSoundEffectsEnabled(this)) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private fun speak(text: String) {
        if (!AppSettings.isVoiceCuesEnabled(this) || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun formatDistance(meters: Float): String = "%.2f km".format(Locale.US, meters / 1000f)

    private fun formatPace(paceMinPerKm: Float): String {
        if (paceMinPerKm <= 0f) return "--:-- /km"
        val minutes = paceMinPerKm.toInt()
        val seconds = ((paceMinPerKm - minutes) * 60).toInt()
        return "%d:%02d /km".format(Locale.US, minutes, seconds)
    }
}
