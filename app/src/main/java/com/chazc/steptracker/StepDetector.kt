package com.chazc.steptracker

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Accelerometer step detector for phones without a hardware step counter.
 *
 * Pure Kotlin on purpose: no Android imports, so it runs in JVM unit tests.
 *
 * Differences from the old inline detector in StepListenerService:
 *  - Uses SensorEvent.timestamp (nanoseconds, sensor clock), not wall-clock time. Events that
 *    are delivered late (CPU was asleep, FIFO batching) still land at the right moment.
 *  - Filters are time-based, so they behave the same at 25 Hz or 50 Hz.
 *  - Detects local maxima instead of a one-shot threshold cross, which survives low sample rates.
 *  - Adaptive threshold that follows how hard the phone is being shaken (pocket vs hand vs bag).
 *  - Needs [Config.confirmSteps] regular peaks before it starts counting, then credits them
 *    retroactively. This rejects single bumps and fidgeting and loses no steps at the start of a walk.
 *  - While walking it tracks the cadence: a skipped peak is recovered (counted as two steps),
 *    and irregular extra peaks are ignored, so vehicle vibration cannot ride on a confirmed walk.
 *
 * Not thread-safe. Call from one thread only (the service's sensor thread).
 */
class StepDetector(
    private val cfg: Config = Config(),
    private val onSteps: (count: Int, timestampNs: Long) -> Unit
) {
    data class Config(
        val minStepIntervalNs: Long = 250_000_000L,     // faster than 4 steps/s is not walking or jogging
        val maxChainIntervalNs: Long = 1_000_000_000L,  // while confirming a new walk
        val maxWalkingGapNs: Long = 2_000_000_000L,     // once walking, tolerate a 2 s pause
        val maxSampleGapNs: Long = 500_000_000L,        // longer: sensor was paused, re-init filters
        val confirmSteps: Int = 5,
        val minThreshold: Float = 0.25f,                // m/s^2 floor so sensor noise never counts
        val maxThreshold: Float = 6.0f,
        val thresholdFraction: Float = 0.40f,           // of the recent peak envelope
        val gravityTauS: Float = 1.5f,
        val smoothTauS: Float = 0.05f,
        val envelopeTauS: Float = 4.0f,
        val chainRatioMin: Float = 0.75f,               // consecutive intervals must be similar
        val chainRatioMax: Float = 1.33f,
        val maxIrregular: Int = 3                       // irregular peaks tolerated before leaving walking
    )

    private var lastSampleNs = 0L
    private var gravity = 9.81f
    private var smoothed = 0f
    private var prev1 = 0f
    private var prev2 = 0f
    private var prev1Ns = 0L
    private var envelope = cfg.minThreshold * 4f

    private var lastPeakNs = 0L
    private var lastIntervalNs = 0L
    private var avgIntervalNs = 0f
    private var chainCount = 0
    private var irregular = 0
    private var walking = false

    /** True while the detector is confident the user is walking. */
    val isWalking: Boolean get() = walking

    fun reset() {
        lastSampleNs = 0L
        endWalk()
    }

    private fun endWalk() {
        walking = false
        chainCount = 0
        lastPeakNs = 0L
        lastIntervalNs = 0L
        avgIntervalNs = 0f
        irregular = 0
    }

    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long) {
        val magnitude = sqrt(x * x + y * y + z * z)

        if (lastSampleNs == 0L || timestampNs <= lastSampleNs || timestampNs - lastSampleNs > cfg.maxSampleGapNs) {
            // First sample, clock went backwards, or sensor was paused: restart filters, keep nothing stale.
            gravity = magnitude
            smoothed = 0f
            prev1 = 0f
            prev2 = 0f
            prev1Ns = timestampNs
            lastSampleNs = timestampNs
            endWalk()
            return
        }

        val dt = (timestampNs - lastSampleNs) / 1_000_000_000f
        lastSampleNs = timestampNs

        gravity += (dt / (cfg.gravityTauS + dt)) * (magnitude - gravity)
        val dynamic = magnitude - gravity
        smoothed += (dt / (cfg.smoothTauS + dt)) * (dynamic - smoothed)

        envelope -= envelope * (dt / (cfg.envelopeTauS + dt))
        envelope = max(envelope, cfg.minThreshold * 4f)

        // prev1 is a local maximum if it is above its left neighbour and not below its right one.
        val isPeak = prev1 > prev2 && prev1 >= smoothed
        if (isPeak && prev1 > 0f) {
            if (prev1 > envelope) envelope = prev1
            val threshold = min(cfg.maxThreshold, max(cfg.minThreshold, cfg.thresholdFraction * envelope))
            if (prev1 > threshold) registerPeak(prev1Ns)
        }

        prev2 = prev1
        prev1 = smoothed
        prev1Ns = timestampNs
    }

    private fun registerPeak(peakNs: Long) {
        if (lastPeakNs == 0L) {
            lastPeakNs = peakNs
            chainCount = 1
            return
        }

        val interval = peakNs - lastPeakNs
        if (interval < cfg.minStepIntervalNs) return // second bump of the same footfall

        if (walking) registerWhileWalking(peakNs, interval) else registerWhileConfirming(peakNs, interval)
    }

    private fun registerWhileConfirming(peakNs: Long, interval: Long) {
        val regular = lastIntervalNs == 0L ||
            (interval.toFloat() / lastIntervalNs.toFloat()) in cfg.chainRatioMin..cfg.chainRatioMax

        if (interval <= cfg.maxChainIntervalNs && regular) {
            chainCount++
            lastIntervalNs = interval
            lastPeakNs = peakNs
            if (chainCount >= cfg.confirmSteps) {
                walking = true
                avgIntervalNs = interval.toFloat()
                irregular = 0
                onSteps(chainCount, peakNs)
            }
        } else {
            // Not a regular rhythm: this peak becomes the start of a new candidate chain.
            chainCount = 1
            lastIntervalNs = 0L
            lastPeakNs = peakNs
        }
    }

    private fun registerWhileWalking(peakNs: Long, interval: Long) {
        if (interval > cfg.maxWalkingGapNs) {
            endWalk()
            lastPeakNs = peakNs
            chainCount = 1
            return
        }

        val ratio = interval / avgIntervalNs
        when {
            ratio in 0.65f..1.55f -> {
                avgIntervalNs += 0.25f * (interval - avgIntervalNs)
                lastPeakNs = peakNs
                irregular = 0
                onSteps(1, peakNs)
            }
            ratio > 1.55f && ratio <= 2.5f -> {
                // A footfall fell under the threshold. Count what the rhythm says happened.
                val n = 2
                avgIntervalNs += 0.25f * (interval / n - avgIntervalNs)
                lastPeakNs = peakNs
                irregular = 0
                onSteps(n, peakNs)
            }
            ratio > 2.5f -> {
                endWalk()
                lastPeakNs = peakNs
                chainCount = 1
            }
            else -> {
                // Extra bump that does not fit the cadence: ignore it, but too many means this is not walking.
                irregular++
                if (irregular >= cfg.maxIrregular) {
                    endWalk()
                    lastPeakNs = peakNs
                    chainCount = 1
                }
            }
        }
    }
}
