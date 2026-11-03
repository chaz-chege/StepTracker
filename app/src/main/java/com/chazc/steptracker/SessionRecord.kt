package com.chazc.steptracker

data class SessionRecord(
    val id: Long = System.currentTimeMillis(),
    val startTimeMs: Long,
    val endTimeMs: Long,
    val steps: Int,
    val distanceMeters: Float,
    val calories: Int,
    val avgPaceMinPerKm: Float = 0f
)
