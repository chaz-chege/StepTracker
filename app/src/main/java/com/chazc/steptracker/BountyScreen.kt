package com.chazc.steptracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.OnigiriBlack

@Composable
fun BountyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val totalBounty = AppSettings.getTotalBounty(context)
    val earnedIds = AppSettings.getEarnedBountyIds(context)

    Surface(modifier = Modifier.fillMaxSize(), color = OnigiriBlack) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "WANTED LIST",
                    color = BandageWhite,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
                TextButton(onClick = onBack) {
                    Text(text = "BACK", color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total Bounty HUD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161A17),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TOTAL REWARD", 
                        color = MaterialTheme.colorScheme.secondary, 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "฿ ${"%,d".format(totalBounty)}",
                        color = BandageWhite,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(BountyManager.bounties) { bounty ->
                    WantedPoster(bounty, bounty.id in earnedIds)
                }
            }
        }
    }
}

@Composable
fun WantedPoster(bounty: Bounty, isEarned: Boolean) {
    val accentColor = MaterialTheme.colorScheme.secondary
    
    Surface(
        modifier = Modifier.aspectRatio(0.7f),
        color = if (isEarned) accentColor.copy(alpha = 0.1f) else Color(0xFF161A17),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (isEarned) accentColor else Color.DarkGray)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "WANTED",
                color = if (isEarned) accentColor else Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEarned) "☠️" else "❓",
                    fontSize = 40.sp,
                    color = if (isEarned) Color.White else Color.DarkGray
                )
                if (isEarned) {
                   Text(
                       text = "COLLECTED",
                       modifier = Modifier.border(2.dp, Color.Red, RoundedCornerShape(2.dp)).padding(4.dp),
                       color = Color.Red,
                       fontSize = 10.sp,
                       fontWeight = FontWeight.Bold
                   )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = bounty.title.uppercase(),
                    color = if (isEarned) BandageWhite else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "฿ ${"%,d".format(bounty.reward)}",
                    color = if (isEarned) accentColor else Color.DarkGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
