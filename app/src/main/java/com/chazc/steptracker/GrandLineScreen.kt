package com.chazc.steptracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.OnigiriBlack

data class Island(val name: String, val distanceKm: Int, val description: String)

val grandLineIslands = listOf(
    Island("Loguetown", 0, "The town of the beginning and the end."),
    Island("Reverse Mountain", 50, "The entrance to the Grand Line."),
    Island("Whiskey Peak", 150, "A town that welcomes bounty hunters."),
    Island("Alabasta", 400, "The kingdom of sand and alabaster."),
    Island("Skypiea", 800, "The island in the sky."),
    Island("Water 7", 1200, "The city of water and shipwrights."),
    Island("Enies Lobby", 1500, "The judicial island of the World Government."),
    Island("Thriller Bark", 2000, "The world's largest pirate ship."),
    Island("Sabaody Archipelago", 2500, "The place where the paths cross."),
    Island("Marineford", 3000, "The headquarters of the Marines."),
    Island("Fish-Man Island", 3500, "The paradise 10,000 meters under the sea."),
    Island("Dressrosa", 4500, "The kingdom of love, passion, and toys."),
    Island("Whole Cake Island", 6000, "The territory of Big Mom."),
    Island("Wano Country", 8000, "The land of samurai and dragons.")
)

@Composable
fun GrandLineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifetimeSteps = AppSettings.getLifetimeSteps(context)
    // Rough estimate: 1300 steps per km
    val lifetimeKm = lifetimeSteps / 1300f
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(modifier = Modifier.fillMaxSize(), color = OnigiriBlack) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GRAND LINE JOURNEY",
                    color = BandageWhite,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
                TextButton(onClick = onBack) {
                    Text(text = "BACK", color = secondaryColor)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161A17),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CURRENT POSITION", 
                        color = secondaryColor, 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "%.1f km".format(lifetimeKm), 
                        color = BandageWhite, 
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black)
                    )
                    
                    val currentIsland = grandLineIslands.lastOrNull { lifetimeKm >= it.distanceKm } ?: grandLineIslands[0]
                    Text(
                        text = "AT ${currentIsland.name.uppercase()}", 
                        color = primaryColor, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(grandLineIslands) { island ->
                    IslandCard(island, lifetimeKm, primaryColor, secondaryColor)
                }
            }
        }
    }
}

@Composable
fun IslandCard(island: Island, lifetimeKm: Float, primaryColor: Color, secondaryColor: Color) {
    val reached = lifetimeKm >= island.distanceKm
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline indicator
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (reached) primaryColor else Color.DarkGray, RoundedCornerShape(6.dp))
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(if (reached) primaryColor.copy(alpha = 0.5f) else Color.DarkGray.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Surface(
            modifier = Modifier.weight(1f),
            color = if (reached) primaryColor.copy(alpha = 0.2f) else Color(0xFF161A17),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = island.name.uppercase(),
                        color = if (reached) BandageWhite else Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${island.distanceKm} km",
                        color = if (reached) secondaryColor else Color.DarkGray,
                        fontSize = 11.sp
                    )
                }
                if (reached) {
                    Text(
                        text = island.description,
                        color = BandageWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
