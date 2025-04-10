package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherMainScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherMainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFcbdfff), Color(0xFFfcdbf6))
                )
            )
            .padding(30.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Top bar icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painter = painterResource(id = R.drawable.kinh_lup), contentDescription = null, modifier = Modifier.size(27.dp))
                    Image(painter = painterResource(id = R.drawable.gps), contentDescription = null, modifier = Modifier.size(17.dp))
                    Image(painter = painterResource(id = R.drawable.setting), contentDescription = null, modifier = Modifier.size(27.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ha Noi", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5372dc))
                        Spacer(modifier = Modifier.height(15.dp))
                        Text("19°", fontSize = 70.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5372dc))
                        Text("Mostly Clear", fontSize = 16.sp, color = Color(0xFF5372dc))
                        Spacer(modifier = Modifier.height(9.dp))
                        Text("H: 24°    L: 17°", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                    }
                    Spacer(modifier = Modifier.width(30.dp))
                    Image(painter = painterResource(id = R.drawable.rainingg), contentDescription = null, modifier = Modifier.size(170.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoItem(R.drawable.rain_dropp, "6%", Color(0xFF5372DC))
                    InfoItem(R.drawable.humidity, "90%", Color(0xFFD05CA2))
                    InfoItem(R.drawable.wind_speed, "19km/h", Color(0xFF3F9CBE))
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(10))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Today", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                        Text("Mar, 10", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF5372dc))
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ForecastItem(R.drawable.sunny, "29°C", "15:00", highlight = true)
                        ForecastItem(R.drawable.cloudy_with_sun, "29°C", "15:00")
                        ForecastItem(R.drawable.rainingg, "29°C", "15:00")
                        ForecastItem(R.drawable.cloudy_with_sun, "29°C", "15:00")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoItem(iconId: Int, text: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ForecastItem(iconId: Int, temp: String, time: String, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                color = if (highlight) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(20)
            )
            .padding(6.dp)
            .width(60.dp)
    ) {
        Text(temp, fontSize = 12.sp, color = Color(0xFF5372dc))
        Spacer(modifier = Modifier.height(5.dp))
        Image(painter = painterResource(id = iconId), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(time, fontSize = 10.sp, color = Color(0xFF5372dc))
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    WeatherMainScreen()
}
