package com.chazc.steptracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.OnigiriBlack

data class Sword(val name: String, val milestone: Int, val description: String)

val swordCollection = listOf(
    Sword("Wado Ichimonji", 0, "The sword of Zoro's childhood promise."),
    Sword("Sandai Kitetsu", 25000, "The cursed blade that chose its master."),
    Sword("Shusui", 100000, "The heavy black blade, treasure of Wano."),
    Sword("Enma", 250000, "The legendary blade that drains Haki.")
)

@Composable
fun DojoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifetimeSteps = AppSettings.getLifetimeSteps(context)

    Surface(modifier = Modifier.fillMaxSize(), color = OnigiriBlack) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SANTORYU DOJO",
                    color = BandageWhite,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
                TextButton(onClick = onBack) {
                    Text(text = "BACK", color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lifetime Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161A17),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LIFETIME TRAINING", 
                        color = MaterialTheme.colorScheme.secondary, 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "$lifetimeSteps", 
                        color = BandageWhite, 
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = "TOTAL STEPS", 
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), 
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "KATANA COLLECTION", color = BandageWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(swordCollection) { sword ->
                    SwordCard(sword, lifetimeSteps)
                }
            }
        }
    }
}

@Composable
fun SwordCard(sword: Sword, lifetimeSteps: Int) {
    val isUnlocked = lifetimeSteps >= sword.milestone
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isUnlocked) primaryColor.copy(alpha = 0.3f) else Color(0xFF161A17),
        shape = RoundedCornerShape(8.dp),
        border = if (isUnlocked) BorderStroke(1.dp, primaryColor) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for Sword Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isUnlocked) secondaryColor else Color.DarkGray, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isUnlocked) "⚔️" else "🔒", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = sword.name.uppercase(),
                    color = if (isUnlocked) BandageWhite else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isUnlocked) sword.description else "Unlocks at ${sword.milestone} steps",
                    color = if (isUnlocked) secondaryColor.copy(alpha = 0.8f) else Color.DarkGray,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
