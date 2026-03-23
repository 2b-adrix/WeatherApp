package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = viewModel()
) {
    val context = LocalContext.current
    var cityInput by remember { mutableStateOf("") }
    val state = viewModel.weatherUiState
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getCurrentLocation(context, fusedLocationClient) { lat, lon ->
                viewModel.fetchWeatherByLocation(lat, lon)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state is WeatherUiState.Loading) {
            val fineLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (fineLocationPermission == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation(context, fusedLocationClient) { lat, lon ->
                    viewModel.fetchWeatherByLocation(lat, lon)
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val backgroundBrush = remember(state) {
        val colors = if (state is WeatherUiState.Success) {
            getBackgroundColors(state.weather.weather.firstOrNull()?.icon ?: "")
        } else {
            listOf(Color(0xFF1A237E), Color(0xFF3949AB))
        }
        Brush.verticalGradient(colors)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        when (state) {
            is WeatherUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is WeatherUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        top = 16.dp, 
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp
                    )
                ) {
                    item {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        SearchBar(
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            onSearch = {
                                if (cityInput.isNotBlank()) {
                                    viewModel.fetchWeather(cityInput)
                                    cityInput = ""
                                }
                            },
                            onMyLocationClick = {
                                getCurrentLocation(context, fusedLocationClient) { lat, lon ->
                                    viewModel.fetchWeatherByLocation(lat, lon)
                                }
                            },
                            isCelsius = viewModel.isCelsius,
                            onToggleUnits = { viewModel.toggleUnits() }
                        )
                    }

                    item {
                        AiAssistanceCard(
                            response = viewModel.aiResponse,
                            isLoading = viewModel.isAiLoading
                        )
                    }

                    item {
                        MainWeatherInfo(state.weather, viewModel.isCelsius)
                    }

                    item {
                        SectionTitle(title = "Hourly Forecast")
                        HourlyForecastList(state.forecast, viewModel.isCelsius)
                    }

                    item {
                        SectionTitle(title = "Weather Details")
                        WeatherDetailsGrid(state.weather, viewModel.isCelsius)
                    }
                    
                    item {
                        SectionTitle(title = "5-Day Forecast")
                        DailyForecastList(state.forecast, viewModel.isCelsius)
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Last updated: ${formatTime(System.currentTimeMillis() / 1000)}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            is WeatherUiState.Error -> {
                ErrorView { 
                    getCurrentLocation(context, fusedLocationClient) { lat, lon ->
                        viewModel.fetchWeatherByLocation(lat, lon)
                    }
                }
            }
        }
    }
}

@Composable
fun AiAssistanceCard(response: String?, isLoading: Boolean) {
    AnimatedVisibility(
        visible = response != null || isLoading,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    Text(
                        text = response ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun getCurrentLocation(
    context: Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocationReceived: (Double, Double) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReceived(location.latitude, location.longitude)
                }
            }
    }
}

@Composable
fun SearchBar(
    value: String, 
    onValueChange: (String) -> Unit, 
    onSearch: () -> Unit,
    onMyLocationClick: () -> Unit,
    isCelsius: Boolean,
    onToggleUnits: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Search city...", color = Color.White.copy(alpha = 0.6f)) },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onMyLocationClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "My Location", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .clickable { onToggleUnits() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCelsius) "°C" else "°F",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MainWeatherInfo(weather: WeatherResponse, isCelsius: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weather.name,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Image(
            painter = painterResource(id = getWeatherIcon(weather.weather.firstOrNull()?.icon ?: "")),
            contentDescription = null,
            modifier = Modifier.size(120.dp).padding(vertical = 16.dp)
        )

        Text(
            text = "${weather.main.temp.toInt()}°",
            fontSize = 100.sp,
            fontWeight = FontWeight.ExtraLight,
            color = Color.White
        )
        Text(
            text = weather.weather.firstOrNull()?.description?.uppercase() ?: "",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
            letterSpacing = 2.sp
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "H:${weather.main.tempMax.toInt()}°", color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "L:${weather.main.tempMin.toInt()}°", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}

@Composable
fun HourlyForecastList(forecast: ForecastResponse, isCelsius: Boolean) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(forecast.list.take(12)) { item ->
            HourlyItem(item)
        }
    }
}

@Composable
fun HourlyItem(item: ForecastItem) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.width(70.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(item.dt),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Image(
                painter = painterResource(id = getWeatherIcon(item.weather.firstOrNull()?.icon ?: "")),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "${item.main.temp.toInt()}°",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun DailyForecastList(forecast: ForecastResponse, isCelsius: Boolean) {
    val dailyItems = forecast.list.filterIndexed { index, _ -> index % 8 == 0 }
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            dailyItems.forEach { item ->
                DailyItem(item)
                if (item != dailyItems.last()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
fun DailyItem(item: ForecastItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatDate(item.dt),
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(id = getWeatherIcon(item.weather.firstOrNull()?.icon ?: "")),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End
        ) {
            Text(text = "${item.main.tempMax.toInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "${item.main.tempMin.toInt()}°", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun WeatherDetailsGrid(weather: WeatherResponse, isCelsius: Boolean) {
    val windUnit = if (isCelsius) "km/h" else "mph"
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailCard(Modifier.weight(1f), "HUMIDITY", "${weather.main.humidity}%", R.drawable.humidity)
            DetailCard(Modifier.weight(1f), "WIND", "${weather.wind.speed} $windUnit", R.drawable.wind)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailCard(Modifier.weight(1f), "FEELS LIKE", "${weather.main.feelsLike.toInt()}°", R.drawable.suncloud)
            DetailCard(Modifier.weight(1f), "PRESSURE", "${weather.main.pressure} hPa", R.drawable.storm)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailCard(Modifier.weight(1f), "SUNRISE", formatTime(weather.sys.sunrise), R.drawable.sun)
            DetailCard(Modifier.weight(1f), "SUNSET", formatTime(weather.sys.sunset), R.drawable.moon)
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier, label: String, value: String, iconRes: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        modifier = modifier.height(110.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            }
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
fun ErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Oops! Something went wrong.", color = Color.White, fontSize = 20.sp)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp * 1000))
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    calendar.time = date
    
    return if (calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
        "Today"
    } else {
        SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    }
}

fun getBackgroundColors(icon: String): List<Color> {
    return when (icon.dropLast(1)) {
        "01" -> listOf(Color(0xFF29B6F6), Color(0xFF0288D1)) // Clear
        "02", "03", "04" -> listOf(Color(0xFF607D8B), Color(0xFF455A64)) // Clouds
        "09", "10", "11" -> listOf(Color(0xFF37474F), Color(0xFF263238)) // Rain
        "13" -> listOf(Color(0xFF81D4FA), Color(0xFF4FC3F7)) // Snow
        "50" -> listOf(Color(0xFF78909C), Color(0xFF546E7A)) // Mist
        else -> listOf(Color(0xFF1A237E), Color(0xFF3949AB))
    }
}

fun getWeatherIcon(icon: String): Int {
    return when (icon) {
        "01d" -> R.drawable.sun
        "01n" -> R.drawable.moon
        "02d" -> R.drawable.fewclouds
        "02n" -> R.drawable.few_cloudes
        "03d", "03n" -> R.drawable.scattered_clouds
        "04d", "04n" -> R.drawable.broken_cloudes
        "09d", "09n" -> R.drawable.shower_rain
        "10d", "10n" -> R.drawable.rain
        "11d", "11n" -> R.drawable.thunderstorm
        "13d", "13n" -> R.drawable.snow
        "50d", "50n" -> R.drawable.mist
        else -> R.drawable.suncloud
    }
}
