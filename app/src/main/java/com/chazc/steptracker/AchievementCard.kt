package com.chazc.steptracker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.HaramakiGreen
import com.chazc.steptracker.ui.theme.KatanaGold
import com.chazc.steptracker.ui.theme.OnigiriBlack
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AchievementCard(
    steps: Int,
    distanceKm: Float,
    calories: Int,
    goal: Int,
    paceMinPerKm: Float = 0f,
    modifier: Modifier = Modifier
) {
    val dateStr = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }
    val goalHit = steps >= goal

    Column(
        modifier = modifier
            .background(OnigiriBlack)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TRAINING LOG",
            color = KatanaGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Text(text = dateStr, color = BandageWhite.copy(alpha = 0.6f), fontSize = 12.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "$steps",
            color = BandageWhite,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black
        )
        Text(text = "STEPS", color = KatanaGold, fontSize = 12.sp, letterSpacing = 2.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MiniStat(label = "DISTANCE", value = "%.2f km".format(distanceKm))
            MiniStat(label = "CALORIES", value = "$calories kcal")
            MiniStat(label = "PACE", value = formatPaceDisplay(paceMinPerKm))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (goalHit) HaramakiGreen else OnigiriBlack)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (goalHit) "GOAL ACHIEVED" else "$steps / $goal STEPS",
                color = if (goalHit) BandageWhite else KatanaGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = BandageWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = KatanaGold.copy(alpha = 0.8f), fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

fun shareBitmap(context: Context, bitmap: Bitmap) {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(imagesDir, "achievement_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share your training log"))
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "achievement_${System.currentTimeMillis()}.png"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StepTracker")
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        uri != null
    } catch (e: Exception) {
        false
    }
}
