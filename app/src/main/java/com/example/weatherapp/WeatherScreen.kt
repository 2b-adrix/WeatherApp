package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
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
    val focusManager = LocalFocusManager.current
    
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
            listOf(Color(0xFF0F172A), Color(0xFF1E293B))
        }
        Brush.verticalGradient(colors)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            },
            label = "StateTransition"
        ) { targetState ->
            when (targetState) {
                is WeatherUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Cyan)
                    }
                }
                is WeatherUiState.Success -> {
                    WeatherContent(
                        weather = targetState.weather,
                        forecast = targetState.forecast,
                        cityInput = cityInput,
                        onCityInputChange = { cityInput = it },
                        viewModel = viewModel,
                        focusManager = focusManager,
                        context = context,
                        fusedLocationClient = fusedLocationClient
                    )
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
}

@Composable
fun WeatherContent(
    weather: WeatherResponse,
    forecast: ForecastResponse,
    cityInput: String,
    onCityInputChange: (String) -> Unit,
    viewModel: WeatherViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager,
    context: Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = 16.dp, 
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        )
    ) {
        item {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            ModernSearchBar(
                value = cityInput,
                onValueChange = onCityInputChange,
                onSearch = {
                    if (cityInput.isNotBlank()) {
                        viewModel.fetchWeather(cityInput)
                        onCityInputChange("")
                        focusManager.clearFocus()
                    }
                },
                onMyLocationClick = {
                    getCurrentLocation(context, fusedLocationClient) { lat, lon ->
                        viewModel.fetchWeatherByLocation(lat, lon)
                        focusManager.clearFocus()
                    }
                },
                isCelsius = viewModel.isCelsius,
                onToggleUnits = { viewModel.toggleUnits() }
            )
        }

        item {
            HeroSection(weather)
        }

        item {
            AiInsightCard(
                response = viewModel.aiResponse,
                isLoading = viewModel.isAiLoading
            )
        }

        item {
            BentoGrid(weather, viewModel.isCelsius)
        }

        item {
            ForecastSection("Hourly Forecast") {
                HourlyForecastList(forecast)
            }
        }
        
        item {
            ForecastSection("5-Day Forecast") {
                DailyForecastCard(forecast)
            }
        }

        item {
            Text(
                text = "System Time: ${formatTime(System.currentTimeMillis() / 1000)}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
fun ModernSearchBar(
    value: String, 
    onValueChange: (String) -> Unit, 
    onSearch: () -> Unit,
    onMyLocationClick: () -> Unit,
    isCelsius: Boolean,
    onToggleUnits: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animatedPadding by animateDpAsState(if (isFocused) 4.dp else 12.dp, label = "Padding")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = if (isFocused) 0.4f else 0.15f), RoundedCornerShape(28.dp))
            .padding(horizontal = animatedPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Search city (e.g. Mumbai, London)...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
            modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        IconButton(onClick = onMyLocationClick) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = Color.White.copy(alpha = 0.2f))
        
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleUnits() }
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = if (isCelsius) "°C" else "°F",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.animateContentSize()
            )
        }
    }
}

@Composable
fun HeroSection(weather: WeatherResponse) {
    val infiniteTransition = rememberInfiniteTransition(label = "FloatingIcon")
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Offset"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weather.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = (-1).sp
        )
        
        // Show Local Time
        Text(
            text = formatLocalTime(weather.dt, weather.timezone),
            fontSize = 16.sp,
            color = Color.Cyan.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${weather.main.temp.toInt()}°",
                fontSize = 86.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Image(
                painter = painterResource(id = getWeatherIcon(weather.weather.firstOrNull()?.icon ?: "")),
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer { translationY = floatingOffset }
            )
        }
        Text(
            text = weather.weather.firstOrNull()?.description ?: "",
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = "H:${weather.main.tempMax.toInt()}°  L:${weather.main.tempMin.toInt()}°",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun AiInsightCard(response: String?, isLoading: Boolean) {
    AnimatedVisibility(
        visible = response != null || isLoading,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF6366F1).copy(alpha = 0.3f), Color(0xFFA855F7).copy(alpha = 0.3f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infiniteTransition = rememberInfiniteTransition(label = "Sparkle")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                        label = "Alpha"
                    )
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Cyan.copy(alpha = alpha), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI INSIGHT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Cyan, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = Color.Cyan, trackColor = Color.White.copy(alpha = 0.1f))
                } else {
                    Text(
                        text = response ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun BentoGrid(weather: WeatherResponse, isCelsius: Boolean) {
    val windUnit = if (isCelsius) "km/h" else "mph"
    
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(modifier = Modifier.height(160.dp)) {
            BentoItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "WIND",
                value = "${weather.wind.speed}",
                unit = windUnit,
                icon = Icons.Default.Air,
                index = 0
            )
            Spacer(modifier = Modifier.width(12.dp))
            BentoItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "HUMIDITY",
                value = "${weather.main.humidity}",
                unit = "%",
                icon = Icons.Default.WaterDrop,
                index = 1
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.height(120.dp)) {
            BentoItem(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                label = "FEELS LIKE",
                value = "${weather.main.feelsLike.toInt()}°",
                unit = "",
                icon = Icons.Default.Thermostat,
                index = 2
            )
            Spacer(modifier = Modifier.width(12.dp))
            BentoItem(
                modifier = Modifier.weight(0.8f).fillMaxHeight(),
                label = "PRESSURE",
                value = "${weather.main.pressure}",
                unit = "hPa",
                icon = Icons.Default.Compress,
                small = true,
                index = 3
            )
        }
    }
}

@Composable
fun BentoItem(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    small: Boolean = false,
    index: Int
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 100L)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it / 2 } + fadeIn(tween(600)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f), letterSpacing = 0.5.sp)
                }
                Column {
                    Text(
                        text = value,
                        fontSize = if (small) 24.sp else 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (unit.isNotEmpty()) {
                        Text(unit, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
fun ForecastSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun HourlyForecastList(forecast: ForecastResponse) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        itemsIndexed(forecast.list.take(16)) { index, item ->
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(initialScale = 0.8f) + fadeIn()
            ) {
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(formatLocalTime(item.dt, forecast.city.timezone), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = getWeatherIcon(item.weather.firstOrNull()?.icon ?: "")),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("${item.main.temp.toInt()}°", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DailyForecastCard(forecast: ForecastResponse) {
    val dailyItems = forecast.list.filterIndexed { index, _ -> index % 8 == 0 }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        dailyItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (index == 0) "Today" else formatDate(item.dt),
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.width(80.dp)
                )
                Image(
                    painter = painterResource(id = getWeatherIcon(item.weather.firstOrNull()?.icon ?: "")),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Row(modifier = Modifier.width(100.dp), horizontalArrangement = Arrangement.End) {
                    Text("${item.main.tempMax.toInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("${item.main.tempMin.toInt()}°", color = Color.White.copy(alpha = 0.4f))
                }
            }
            if (index < dailyItems.size - 1) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
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
fun ErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Connection issues", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Button(
            onClick = onRetry, 
            modifier = Modifier.padding(top = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Text("Try again", color = Color.White)
        }
    }
}

fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp * 1000))
}

fun formatLocalTime(timestamp: Long, timezoneOffset: Int): String {
    val date = Date((timestamp + timezoneOffset) * 1000)
    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(date)
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    return SimpleDateFormat("EEE", Locale.getDefault()).format(date)
}

fun getBackgroundColors(icon: String): List<Color> {
    return when (icon.dropLast(1)) {
        "01" -> listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))
        "02", "03", "04" -> listOf(Color(0xFF64748B), Color(0xFF334155))
        "09", "10", "11" -> listOf(Color(0xFF334155), Color(0xFF0F172A))
        "13" -> listOf(Color(0xFF7DD3FC), Color(0xFF0EA5E9))
        "50" -> listOf(Color(0xFF94A3B8), Color(0xFF475569))
        else -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
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
