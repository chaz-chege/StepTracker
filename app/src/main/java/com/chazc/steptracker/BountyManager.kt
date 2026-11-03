package com.chazc.steptracker

import android.content.Context

enum class BountyType { LIFETIME_STEPS, SESSION_DISTANCE, GOAL_REACHED_COUNT }

data class Bounty(
    val id: String,
    val title: String,
    val reward: Long,
    val requirement: String,
    val type: BountyType,
    val goalValue: Float
)

object BountyManager {
    val bounties = listOf(
        Bounty("b1", "Apprentice Pirate", 30_000_000L, "Reach 5,000 lifetime steps", BountyType.LIFETIME_STEPS, 5000f),
        Bounty("b2", "The Pirate Hunter", 60_000_000L, "Complete a session > 5km", BountyType.SESSION_DISTANCE, 5000f),
        Bounty("b3", "Santoryu Master", 120_000_000L, "Reach 50,000 lifetime steps", BountyType.LIFETIME_STEPS, 50000f),
        Bounty("b4", "King of Hell", 1_111_000_000L, "Reach 500,000 lifetime steps", BountyType.LIFETIME_STEPS, 500000f)
    )

    fun checkBounties(context: Context, type: BountyType, currentValue: Float, onEarned: () -> Unit) {
        val earnedIds = AppSettings.getEarnedBountyIds(context)
        bounties.filter { it.type == type && it.id !in earnedIds }.forEach { bounty ->
            if (currentValue >= bounty.goalValue) {
                AppSettings.addEarnedBountyId(context, bounty.id)
                AppSettings.addBounty(context, bounty.reward)
                onEarned()
            }
        }
    }
}
