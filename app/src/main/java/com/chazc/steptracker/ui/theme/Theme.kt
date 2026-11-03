package com.chazc.steptracker.ui.theme

import android.app.Activity
import android.util.Log
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.chazc.steptracker.StepTrackerState

val LocalCharacterTheme = staticCompositionLocalOf { CharacterRegistry.Zoro }

@Composable
fun StepTrackerTheme(content: @Composable () -> Unit) {
    val themeConfig by StepTrackerState.selectedCharacter.collectAsState()
    
    Log.d("StepTrackerTheme", "Re-composing theme for character: ${themeConfig.characterName}")

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = OnigiriBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(
        LocalCharacterTheme provides themeConfig,
        LocalTextStyle provides themeConfig.typography.bodyLarge
    ) {
        MaterialTheme(
            colorScheme = themeConfig.colorScheme,
            typography = themeConfig.typography,
            content = content
        )
    }
}
