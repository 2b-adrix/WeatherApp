package com.example.weatherapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

sealed interface WeatherUiState {
    data class Success(
        val weather: WeatherResponse,
        val forecast: ForecastResponse
    ) : WeatherUiState
    object Error : WeatherUiState
    object Loading : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    var weatherUiState: WeatherUiState by mutableStateOf(WeatherUiState.Loading)
        private set

    var aiResponse by mutableStateOf<String?>(null)
        private set

    var isAiLoading by mutableStateOf(false)
        private set

    var isCelsius by mutableStateOf(true)
        private set

    private val apiService = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("https://api.openweathermap.org/data/2.5/")
        .build()
        .create(WeatherApiService::class.java)

    private val apiKey = "503b07c5c207d667e1f7eee73739ed1c"
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyCUIdylXmGPrUikDUctR2bifa9gw5X8w8Y"
    )

    private val LAST_CITY_KEY = stringPreferencesKey("last_city")

    init {
        loadLastCity()
    }

    private fun loadLastCity() {
        viewModelScope.launch {
            val lastCity = getApplication<Application>().dataStore.data
                .map { preferences -> preferences[LAST_CITY_KEY] }
                .first()
            
            if (lastCity != null) {
                fetchWeather(lastCity)
            }
        }
    }

    private fun saveLastCity(city: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[LAST_CITY_KEY] = city
            }
        }
    }

    fun toggleUnits() {
        isCelsius = !isCelsius
        val currentState = weatherUiState
        if (currentState is WeatherUiState.Success) {
            // Re-fetch to get correct units from API or just convert locally
            // Fetching is safer for wind speeds and pressure units too
            fetchWeather(currentState.weather.name)
        }
    }

    fun fetchWeather(city: String) {
        viewModelScope.launch {
            weatherUiState = WeatherUiState.Loading
            try {
                coroutineScope {
                    val units = if (isCelsius) "metric" else "imperial"
                    val weatherDeferred = async { apiService.getWeather(city.trim(), units = units, apiKey = apiKey) }
                    val forecastDeferred = async { apiService.getForecast(city.trim(), units = units, apiKey = apiKey) }
                    
                    val weather = weatherDeferred.await()
                    val forecast = forecastDeferred.await()
                    
                    weatherUiState = WeatherUiState.Success(weather, forecast)
                    saveLastCity(weather.name)
                    getAiAdvice(weather, forecast)
                }
            } catch (e: Exception) {
                weatherUiState = WeatherUiState.Error
            }
        }
    }

    fun fetchWeatherByLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            weatherUiState = WeatherUiState.Loading
            try {
                coroutineScope {
                    val units = if (isCelsius) "metric" else "imperial"
                    val weatherDeferred = async { apiService.getWeatherByCoords(lat, lon, units = units, apiKey = apiKey) }
                    val forecastDeferred = async { apiService.getForecastByCoords(lat, lon, units = units, apiKey = apiKey) }
                    
                    val weather = weatherDeferred.await()
                    val forecast = forecastDeferred.await()
                    
                    weatherUiState = WeatherUiState.Success(weather, forecast)
                    saveLastCity(weather.name)
                    getAiAdvice(weather, forecast)
                }
            } catch (e: Exception) {
                weatherUiState = WeatherUiState.Error
            }
        }
    }

    private fun getAiAdvice(weather: WeatherResponse, forecast: ForecastResponse) {
        viewModelScope.launch {
            isAiLoading = true
            try {
                val unitStr = if (isCelsius) "°C" else "°F"
                val prompt = """
                    You are a helpful weather assistant. 
                    Current weather in ${weather.name}: ${weather.main.temp}$unitStr, ${weather.weather.firstOrNull()?.description}.
                    Humidity: ${weather.main.humidity}%, Wind: ${weather.wind.speed} ${if (isCelsius) "km/h" else "mph"}.
                    Provide a very short (max 2 sentences) and friendly advice for the user today based on this weather.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                aiResponse = response.text
            } catch (e: Exception) {
                aiResponse = "Stay safe and check the forecast!"
            } finally {
                isAiLoading = false
            }
        }
    }
}
