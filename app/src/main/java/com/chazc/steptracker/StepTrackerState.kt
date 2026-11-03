package com.chazc.steptracker

import com.chazc.steptracker.ui.theme.CharacterRegistry
import com.chazc.steptracker.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StepTrackerState {
    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _distanceKm = MutableStateFlow(0.0f)
    val distanceKm: StateFlow<Float> = _distanceKm.asStateFlow()

    private val _dailyDistanceKm = MutableStateFlow(0.0f)
    val dailyDistanceKm: StateFlow<Float> = _dailyDistanceKm.asStateFlow()

    private val _calories = MutableStateFlow(0)
    val calories: StateFlow<Int> = _calories.asStateFlow()

    private val _activeMinutes = MutableStateFlow(0)
    val activeMinutes: StateFlow<Int> = _activeMinutes.asStateFlow()

    private val _dailyGoal = MutableStateFlow(10000)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _paceMinPerKm = MutableStateFlow(0f)
    val paceMinPerKm: StateFlow<Float> = _paceMinPerKm.asStateFlow()

    private val _streakCount = MutableStateFlow(0)
    val streakCount: StateFlow<Int> = _streakCount.asStateFlow()

    private val _heavyWeightsEnabled = MutableStateFlow(false)
    val heavyWeightsEnabled: StateFlow<Boolean> = _heavyWeightsEnabled.asStateFlow()

    private val _hasHardwareCounter = MutableStateFlow(false)
    val hasHardwareCounter: StateFlow<Boolean> = _hasHardwareCounter.asStateFlow()

    private val _hasHardwareDetector = MutableStateFlow(false)
    val hasHardwareDetector: StateFlow<Boolean> = _hasHardwareDetector.asStateFlow()

    private val _selectedCharacter = MutableStateFlow(CharacterRegistry.Zoro)
    val selectedCharacter: StateFlow<ThemeConfig> = _selectedCharacter.asStateFlow()

    fun updateSteps(count: Int) {
        _steps.value = count
    }

    fun updateDistance(distanceMeters: Float) {
        _distanceKm.value = distanceMeters / 1000f
    }

    fun updateDailyDistance(distanceMeters: Float) {
        _dailyDistanceKm.value = distanceMeters / 1000f
    }

    fun updateMetrics(calories: Int, activeMinutes: Int) {
        _calories.value = calories
        _activeMinutes.value = activeMinutes
    }

    fun updateDailyGoal(goal: Int) {
        _dailyGoal.value = goal
    }

    fun updatePace(pace: Float) {
        _paceMinPerKm.value = pace
    }

    fun updateSessionStatus(active: Boolean) {
        _isSessionActive.value = active
    }

    fun updateStreak(count: Int) {
        _streakCount.value = count
    }

    fun updateHeavyWeights(enabled: Boolean) {
        _heavyWeightsEnabled.value = enabled
    }

    fun updateSensorStatus(hasCounter: Boolean, hasDetector: Boolean) {
        _hasHardwareCounter.value = hasCounter
        _hasHardwareDetector.value = hasDetector
    }

    fun updateCharacter(theme: ThemeConfig) {
        _selectedCharacter.value = theme
    }
}
