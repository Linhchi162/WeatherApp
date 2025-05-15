package com.example.weatherapp

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(context: Context, onSearchClick: () -> Unit) {
    val localContext = LocalContext.current

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
        Image(
            painter = painterResource(id = R.drawable.kinh_lup),
            contentDescription = "Search",
            modifier = Modifier
                .size(27.dp)
                .clickable { onSearchClick() }
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    try {
                        Log.d("TopBar", "Settings button clicked")
                        Log.d("TopBar", "Creating Toast")

                        Log.d("TopBar", "Showing Toast")

                        Log.d("TopBar", "Starting SettingsActivity")
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
