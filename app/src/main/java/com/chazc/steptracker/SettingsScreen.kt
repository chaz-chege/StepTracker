package com.chazc.steptracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.CharacterRegistry
import com.chazc.steptracker.ui.theme.KatanaGold
import com.chazc.steptracker.ui.theme.ThemeConfig

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var goalText by remember { mutableStateOf(AppSettings.getDailyGoal(context).toString()) }
    var weightText by remember { mutableStateOf(AppSettings.getWeightKg(context).toString()) }
    var heightText by remember { mutableStateOf(AppSettings.getHeightCm(context).toString()) }
    var voiceCuesEnabled by remember { mutableStateOf(AppSettings.isVoiceCuesEnabled(context)) }
    var soundEffectsEnabled by remember { mutableStateOf(AppSettings.isSoundEffectsEnabled(context)) }
    var heavyWeightsEnabled by remember { mutableStateOf(AppSettings.isHeavyWeightsEnabled(context)) }
    var highAccuracyEnabled by remember { mutableStateOf(AppSettings.isHighAccuracyEnabled(context)) }
    var showKeepAliveGuide by remember { mutableStateOf(false) }
    
    val hasCounter by StepTrackerState.hasHardwareCounter.collectAsState()
    val hasDetector by StepTrackerState.hasHardwareDetector.collectAsState()

    val currentCharacterName = remember { AppSettings.getSelectedCharacter(context) }
    var selectedCharacter by remember { mutableStateOf(CharacterRegistry.getByName(currentCharacterName)) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SETTINGS", 
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), 
                color = BandageWhite
            )

            // Sensor Status
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BandageWhite.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, color, label) = when {
                        hasCounter -> Triple("🟢", Color(0xFF4CAF50), "Hardware Counter Found")
                        hasDetector -> Triple("🟡", Color(0xFFFFC107), "Step Detector Found")
                        else -> Triple("🔴", Color(0xFFF44336), "No Hardware Support")
                    }
                    Text(text = icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (hasCounter) "Best accuracy & battery life." else "Using software fallback.",
                            color = BandageWhite.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Character Selection
            Text(
                text = "SWITCH CREW MEMBER", 
                color = MaterialTheme.colorScheme.secondary, 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(CharacterRegistry.list) { char ->
                    CharacterCircle(
                        char = char,
                        isSelected = selectedCharacter.characterName == char.characterName,
                        onClick = { selectedCharacter = char }
                    )
                }
            }

            Text(
                text = "Currently training with ${selectedCharacter.characterName}",
                color = BandageWhite.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it },
                label = { Text("Daily step goal") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight (kg)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Height (cm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Voice cues during runs", color = BandageWhite)
                Switch(
                    checked = voiceCuesEnabled,
                    onCheckedChange = { voiceCuesEnabled = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Sound effects", color = BandageWhite)
                Switch(
                    checked = soundEffectsEnabled,
                    onCheckedChange = { soundEffectsEnabled = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Training with Weights", color = BandageWhite)
                    Text(text = "Zoro style - Increases calorie burn", color = KatanaGold, fontSize = 10.sp)
                }
                Switch(
                    checked = heavyWeightsEnabled,
                    onCheckedChange = { heavyWeightsEnabled = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Keep CPU Awake", color = BandageWhite)
                    Text(text = "Forces tracking while screen is off. Uses more battery.", color = KatanaGold, fontSize = 10.sp)
                }
                Switch(
                    checked = highAccuracyEnabled,
                    onCheckedChange = { highAccuracyEnabled = it }
                )
            }

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {
                        // Fallback to general settings
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "ALLOW UNLIMITED BACKGROUND", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showKeepAliveGuide = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "BACKGROUND SETUP GUIDE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (showKeepAliveGuide) {
                KeepAliveDialog(onDismiss = { showKeepAliveGuide = false })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    goalText.toIntOrNull()?.let { AppSettings.setDailyGoal(context, it) }
                    weightText.toFloatOrNull()?.let { AppSettings.setWeightKg(context, it) }
                    heightText.toFloatOrNull()?.let { AppSettings.setHeightCm(context, it) }
                    AppSettings.setVoiceCuesEnabled(context, voiceCuesEnabled)
                    AppSettings.setSoundEffectsEnabled(context, soundEffectsEnabled)
                    AppSettings.setHeavyWeightsEnabled(context, heavyWeightsEnabled)
                    AppSettings.setHighAccuracyEnabled(context, highAccuracyEnabled)
                    AppSettings.setSelectedCharacter(context, selectedCharacter.characterName)
                    
                    StepTrackerState.updateHeavyWeights(heavyWeightsEnabled)
                    StepTrackerState.updateCharacter(selectedCharacter)

                    // Refresh Widget Immediately
                    val refreshIntent = Intent(context, StepListenerService::class.java).apply {
                        action = StepListenerService.ACTION_REFRESH_WIDGET
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(refreshIntent)
                    } else {
                        context.startService(refreshIntent)
                    }

                    // Notify service to toggle WakeLock
                    val intent = Intent(context, StepListenerService::class.java).apply {
                        action = StepListenerService.ACTION_TOGGLE_WAKELOCK
                    }
                    context.startService(intent)

                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(text = "SAVE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondary)
            }

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = "CANCEL", color = BandageWhite)
            }
        }
    }
}

@Composable
fun CharacterCircle(char: ThemeConfig, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(char.primary)
                .then(
                    if (isSelected) Modifier.background(char.secondary.copy(alpha = 0.5f)) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(char.secondary)
                )
            }
            Text(text = char.accentSymbol, fontSize = 24.sp)
        }
        Text(
            text = char.characterName.split(" ").last(),
            fontSize = 10.sp,
            color = if (isSelected) char.secondary else BandageWhite.copy(alpha = 0.5f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
