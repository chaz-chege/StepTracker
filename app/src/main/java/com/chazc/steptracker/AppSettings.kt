package com.chazc.steptracker

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "step_tracker_prefs"
    private const val KEY_DAILY_GOAL = "daily_goal"
    private const val KEY_WEIGHT_KG = "weight_kg"
    private const val KEY_HEIGHT_CM = "height_cm"
    private const val KEY_VOICE_CUES = "voice_cues_enabled"
    private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    private const val KEY_LIFETIME_STEPS = "lifetime_steps"
    private const val KEY_TOTAL_BOUNTY = "total_bounty"
    private const val KEY_EARNED_BOUNTY_IDS = "earned_bounty_ids"
    private const val KEY_STREAK_COUNT = "streak_count"
    private const val KEY_LAST_STREAK_DATE = "last_streak_date"
    private const val KEY_HEAVY_WEIGHTS = "heavy_weights_enabled"
    private const val KEY_SELECTED_CHARACTER = "selected_character"
    private const val KEY_HIGH_ACCURACY = "high_accuracy_enabled"
    private const val KEY_KEEP_ALIVE_GUIDE_DONE = "keep_alive_guide_done"
    private const val DEFAULT_GOAL = 10000
    private const val DEFAULT_WEIGHT = 70f
    private const val DEFAULT_HEIGHT = 170f

    fun getDailyGoal(context: Context): Int =
        prefs(context).getInt(KEY_DAILY_GOAL, DEFAULT_GOAL)

    fun setDailyGoal(context: Context, goal: Int) {
        prefs(context).edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }

    fun getWeightKg(context: Context): Float =
        prefs(context).getFloat(KEY_WEIGHT_KG, DEFAULT_WEIGHT)

    fun setWeightKg(context: Context, weight: Float) {
        prefs(context).edit().putFloat(KEY_WEIGHT_KG, weight).apply()
    }

    fun getHeightCm(context: Context): Float =
        prefs(context).getFloat(KEY_HEIGHT_CM, DEFAULT_HEIGHT)

    fun setHeightCm(context: Context, height: Float) {
        prefs(context).edit().putFloat(KEY_HEIGHT_CM, height).apply()
    }

    fun isVoiceCuesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_CUES, true)

    fun setVoiceCuesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_CUES, enabled).apply()
    }

    fun isSoundEffectsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND_EFFECTS, true)

    fun setSoundEffectsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
    }

    fun getLifetimeSteps(context: Context): Int =
        prefs(context).getInt(KEY_LIFETIME_STEPS, 0)

    fun setLifetimeSteps(context: Context, total: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_STEPS, total).apply()
    }

    fun incrementLifetimeSteps(context: Context, amount: Int) {
        val current = getLifetimeSteps(context)
        prefs(context).edit().putInt(KEY_LIFETIME_STEPS, current + amount).apply()
    }

    fun getTotalBounty(context: Context): Long =
        prefs(context).getLong(KEY_TOTAL_BOUNTY, 0L)

    fun addBounty(context: Context, amount: Long) {
        val current = getTotalBounty(context)
        prefs(context).edit().putLong(KEY_TOTAL_BOUNTY, current + amount).apply()
    }

    fun getEarnedBountyIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EARNED_BOUNTY_IDS, emptySet()) ?: emptySet()

    fun addEarnedBountyId(context: Context, id: String) {
        val current = getEarnedBountyIds(context).toMutableSet()
        current.add(id)
        prefs(context).edit().putStringSet(KEY_EARNED_BOUNTY_IDS, current).apply()
    }

    fun getStreakCount(context: Context): Int =
        prefs(context).getInt(KEY_STREAK_COUNT, 0)

    fun setStreakCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_STREAK_COUNT, count).apply()
    }

    fun getLastStreakDate(context: Context): String =
        prefs(context).getString(KEY_LAST_STREAK_DATE, "") ?: ""

    fun setLastStreakDate(context: Context, date: String) {
        prefs(context).edit().putString(KEY_LAST_STREAK_DATE, date).apply()
    }

    fun isHeavyWeightsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HEAVY_WEIGHTS, false)

    fun setHeavyWeightsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HEAVY_WEIGHTS, enabled).apply()
    }

    fun getSelectedCharacter(context: Context): String =
        prefs(context).getString(KEY_SELECTED_CHARACTER, "Roronoa Zoro") ?: "Roronoa Zoro"

    fun setSelectedCharacter(context: Context, name: String) {
        prefs(context).edit().putString(KEY_SELECTED_CHARACTER, name).apply()
    }

    fun isHighAccuracyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGH_ACCURACY, false)

    fun setHighAccuracyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_ACCURACY, enabled).apply()
    }

    /** Estimated stride from height (about 41.5 percent of height), kept inside a sane range. */
    fun getStrideMeters(context: Context): Float =
        (getHeightCm(context) * 0.415f / 100f).coerceIn(0.45f, 1.10f)

    fun isKeepAliveGuideDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_ALIVE_GUIDE_DONE, false)

    fun setKeepAliveGuideDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_ALIVE_GUIDE_DONE, done).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
