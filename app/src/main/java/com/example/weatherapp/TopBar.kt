package com.example.weatherapp

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalTime

@Composable
fun TopBar(
    context: Context,
    onMenuClick: () -> Unit,
    isNightTime: Boolean = false,
    currentCityName: String = "",
    scrollOffset: Float = 0f
) {
    val localContext = LocalContext.current
    val textColor = if (isNightTime) Color.White else Color(0xFF1E4385)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(30.dp)
            .padding(horizontal = 24.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = currentCityName,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    try {
                        Log.d("TopBar", "Settings button clicked")
                        val intent = Intent(localContext, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        localContext.startActivity(intent)
                        Log.d("TopBar", "SettingsActivity started")
                    } catch (e: Exception) {
                        Log.e("TopBar", "Error in onClick: ${e.message}", e)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.setting),
                contentDescription = "Settings",
                modifier = Modifier.size(27.dp)
            )
        }
    }
}

