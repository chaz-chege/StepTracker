package com.chazc.steptracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp

/**
 * Replays synthetic accelerometer signals through [StepDetector] at the production rate (50 Hz).
 * The signals are generated, not recorded from a phone, so these tests protect the logic from
 * regressions; they do not prove accuracy on real hardware. Always do the 100-step walk test too.
 */
class StepDetectorTest {

    private class Sample(val tNs: Long, val x: Float, val y: Float, val z: Float)
    private class Scenario(val trueSteps: Int, val samples: List<Sample>)

    private fun pulse(tau: Double, amp: Double) =
        amp * (0.6 * exp(-(tau / 0.11) * (tau / 0.11)) + 0.4 * exp(-(tau / 0.04) * (tau / 0.04)))

    private fun render(
        stepTimes: List<Double>, amps: List<Double>, durationS: Double, fs: Double, noise: Double, rnd: Random,
        extra: (Double) -> Double = { 0.0 }
    ): List<Sample> {
        val out = ArrayList<Sample>()
        var t = 0.0
        var first = 0
        while (t < durationS) {
            var a = 0.0
            while (first < stepTimes.size && stepTimes[first] < t - 0.4) first++
            var k = first
            while (k < stepTimes.size && stepTimes[k] < t + 0.4) { a += pulse(t - stepTimes[k], amps[k]); k++ }
            a += extra(t) + rnd.nextGaussian() * noise
            val mag = 9.81 + a
            out.add(Sample((t * 1e9).toLong() + 5_000_000_000L, (mag * 0.25).toFloat(), (mag * 0.15).toFloat(), (mag * 0.957).toFloat()))
            t += (1.0 / fs) * (1.0 + 0.08 * rnd.nextGaussian()).coerceIn(0.6, 1.5)
        }
        return out
    }

    /** segments: (steps, step frequency Hz, amplitude m/s2); pauses between segments in seconds. */
    private fun walk(
        segs: List<Triple<Int, Double, Double>>, pauses: List<Double> = emptyList(),
        cv: Double = 0.05, noise: Double = 0.2, seed: Long
    ): Scenario {
        val rnd = Random(seed)
        val times = ArrayList<Double>()
        val amps = ArrayList<Double>()
        var t = 1.0
        var total = 0
        for ((i, seg) in segs.withIndex()) {
            repeat(seg.first) {
                times.add(t)
                amps.add((seg.third * (1 + 0.2 * rnd.nextGaussian())).coerceAtLeast(seg.third * 0.4))
                t += (1.0 / seg.second) * (1 + cv * rnd.nextGaussian()).coerceIn(0.6, 1.5)
                total++
            }
            if (i < pauses.size) t += pauses[i]
        }
        return Scenario(total, render(times, amps, t + 2.0, 50.0, noise, rnd))
    }

    private fun still(seconds: Double, noise: Double, seed: Long): Scenario {
        val rnd = Random(seed)
        return Scenario(0, render(emptyList(), emptyList(), seconds, 50.0, noise, rnd))
    }

    private fun vehicle(seconds: Double, vib: Double, bumpAmp: Double, meanGapS: Double, seed: Long): Scenario {
        val rnd = Random(seed)
        val bumps = ArrayList<Double>()
        val amps = ArrayList<Double>()
        var t = 2.0
        while (t < seconds) {
            bumps.add(t)
            amps.add(bumpAmp * (0.5 + rnd.nextDouble()))
            t += -meanGapS * Math.log(1.0 - rnd.nextDouble())
        }
        return Scenario(0, render(bumps, amps, seconds, 50.0, vib, rnd) { tt -> 0.4 * vib * Math.sin(2 * Math.PI * 14 * tt) })
    }

    private fun count(s: Scenario): Int {
        var n = 0
        val detector = StepDetector { c, _ -> n += c }
        for (p in s.samples) detector.onSample(p.x, p.y, p.z, p.tNs)
        return n
    }

    private fun assertWithin(s: Scenario, tolerancePercent: Double) {
        val got = count(s)
        val err = 100.0 * abs(got - s.trueSteps) / s.trueSteps
        assertTrue("expected ${s.trueSteps} steps, got $got (${"%.1f".format(err)}% off)", err <= tolerancePercent)
    }

    @Test fun normalWalk() = assertWithin(walk(listOf(Triple(120, 1.8, 2.5)), seed = 1), 5.0)
    @Test fun strongPocketWalk() = assertWithin(walk(listOf(Triple(120, 1.9, 4.0)), noise = 0.25, seed = 2), 5.0)
    @Test fun gentleWalk() = assertWithin(walk(listOf(Triple(100, 1.4, 1.3)), cv = 0.07, noise = 0.25, seed = 3), 8.0)
    @Test fun jog() = assertWithin(walk(listOf(Triple(150, 2.8, 5.0)), cv = 0.04, noise = 0.3, seed = 5), 8.0)
    @Test fun irregularGait() = assertWithin(walk(listOf(Triple(120, 1.8, 2.5)), cv = 0.12, noise = 0.25, seed = 6), 8.0)

    @Test fun walkStopWalk() = assertWithin(
        walk(listOf(Triple(30, 1.8, 2.5), Triple(30, 1.8, 2.5), Triple(30, 1.8, 2.5)), pauses = listOf(3.0, 1.5), seed = 7), 8.0
    )

    @Test fun shortWalkIsNotLost() {
        val s = walk(listOf(Triple(12, 1.8, 2.5)), seed = 8)
        val got = count(s)
        assertTrue("expected about 12, got $got", got in 10..13)
    }

    @Test fun phoneOnTableCountsNothing() = assertEquals(0, count(still(300.0, 0.05, 9)))
    @Test fun noisyPocketWhileSittingCountsNothing() = assertEquals(0, count(still(300.0, 0.35, 10)))

    @Test fun roughRoadVehicleCountsAlmostNothing() {
        val got = count(vehicle(300.0, 0.4, 3.0, 0.9, 11))
        assertTrue("vehicle produced $got false steps in 5 minutes", got < 20)
    }

    @Test fun singleBumpIsNeverCounted() {
        var n = 0
        val d = StepDetector { c, _ -> n += c }
        // 2 s of stillness, one sharp bump, 2 s of stillness
        var t = 5_000_000_000L
        repeat(100) { d.onSample(0f, 0f, 9.81f, t); t += 20_000_000L }
        repeat(5) { d.onSample(0f, 0f, 14.0f, t); t += 20_000_000L }
        repeat(100) { d.onSample(0f, 0f, 9.81f, t); t += 20_000_000L }
        assertEquals(0, n)
    }

    @Test fun sensorPauseResetsInsteadOfInventingSteps() {
        var n = 0
        val d = StepDetector { c, _ -> n += c }
        d.onSample(0f, 0f, 9.81f, 5_000_000_000L)
        // a 10 s gap (CPU was asleep) must not be treated as one long sample
        d.onSample(0f, 0f, 9.81f, 15_000_000_000L)
        assertEquals(0, n)
        assertTrue(!d.isWalking)
    }
}
