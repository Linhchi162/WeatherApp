package com.example.weatherapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.weatherapp.R

@Composable
fun SearchOverlay(
    onBackClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: WeatherViewModel
) {

    val searchQuery = viewModel.searchQuery
    val suggestions = viewModel.placeSuggestions
    val isSearching = viewModel.isSearching
    val searchError = viewModel.searchError


    val isNightTime = remember {
        val currentHour = java.time.LocalTime.now().hour
        currentHour < 6 || currentHour >= 18
    }
    val backgroundColors = if (isNightTime) {
        listOf(Color(0xFF475985), Color(0xFF5F4064))
    } else {
        listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
    }
    val primaryTextColor = if (isNightTime) Color.White else Color(0xFF5372dc)
    val secondaryTextColor = if (isNightTime) Color.White.copy(alpha = 0.8f) else Color(0xFF5372dc).copy(alpha = 0.8f)
    val iconTint = if (isNightTime) Color.White else Color(0xFF5372dc)
    val borderColor = if (isNightTime) Color.White.copy(alpha = 0.5f) else Color(0xFF5372dc).copy(alpha = 0.5f)

    Dialog(onDismissRequest = {
        viewModel.clearSearch()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.8f)
                .background(
                    brush = Brush.verticalGradient(colors = backgroundColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(15.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    placeholder = {
                        Text(
                            "Tìm kiếm thành phố...",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = secondaryTextColor
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.kinh_lup),
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp),
                            tint = iconTint
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onSearchQueryChanged("")
                            }) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = "Clear",
                                    tint = iconTint
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = borderColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = iconTint,
                        cursorColor = iconTint,
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color(0xFF5372dc)
                    )
                }

                else if (searchError != null) {
                    Text(
                        text = searchError,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                else if (suggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .clickable {

                                        if (suggestion.latitude != null && suggestion.longitude != null) {

                                            val cityName = suggestion.city
                                                ?: suggestion.formattedName.split(",")[0].trim()

                                            val newCity = City(
                                                name = cityName,
                                                latitude = suggestion.latitude,

                                                longitude = suggestion.longitude,
                                                country = suggestion.country

                                            )
                                            viewModel.addCity(newCity)

                                            onDismiss()
                                        } else {

                                            Log.w("SearchOverlay", "Missing coordinates for suggestion: ${suggestion.formattedName}")
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.gps),
                                    contentDescription = "Location",
                                    tint = if (isNightTime) Color.White else Color(0xFF5372dc).copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = suggestion.formattedName,
                                        fontSize = 15.sp,
                                        color = if (isNightTime) Color.White else Color(0xFF5372dc),
                                        fontWeight = FontWeight.Medium
                                    )

                                    /*
                                    if (suggestion.country != null) {
                                        Text(
                                            text = suggestion.country,
                                            fontSize = 12.sp,
                                            color = Color(0xFF5372dc).copy(alpha = 0.8f)
                                        )
                                    }
                                     */
                                }
                            }
                            Divider(color = if (isNightTime) Color.White.copy(alpha = 0.2f) else Color(0xFF5372dc).copy(alpha = 0.2f))
                        }
                    }
                }

                else if (searchQuery.length >= 3) {
                    Text(
                        text = "Gõ để tìm kiếm địa điểm...",
                        color = Color(0xFF5372dc).copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

