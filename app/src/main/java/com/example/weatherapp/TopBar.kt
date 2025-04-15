package com.example.weatherapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(30.dp)
            .padding(horizontal = 24.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.kinh_lup),
            contentDescription = null,
            modifier = Modifier.size(27.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.refresh),
            contentDescription = "Refresh",
            modifier = Modifier
                .size(27.dp)
                .clickable { onRefresh() }
        )
        Image(
            painter = painterResource(id = R.drawable.setting),
            contentDescription = null,
            modifier = Modifier.size(27.dp)
        )
    }
}