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
import androidx.room.Room
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
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
    
    var detailedAiResponse by mutableStateOf<String?>(null)
        private set

    var isAiLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
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

    private val db = Room.databaseBuilder(
        application,
        WeatherDatabase::class.java, "weather-db"
    ).build()
    private val weatherDao = db.weatherDao()
    private val gson = Gson()

    private val LAST_CITY_KEY = stringPreferencesKey("last_city")

    init {
        loadLastCityAndCache()
    }

    private fun loadLastCityAndCache() {
        viewModelScope.launch {
            // 1. Try to load from Cache first for instant UI
            val cache = weatherDao.getCache()
            if (cache != null) {
                try {
                    val weather = gson.fromJson(cache.weatherJson, WeatherResponse::class.java)
                    val forecast = gson.fromJson(cache.forecastJson, ForecastResponse::class.java)
                    weatherUiState = WeatherUiState.Success(weather, forecast)
                    getAiAdvice(weather)
                } catch (e: Exception) {
                    // Cache corrupted
                }
            }

            // 2. Then load from DataStore and refresh from Network
            val lastCity = getApplication<Application>().dataStore.data
                .map { preferences -> preferences[LAST_CITY_KEY] }
                .first()
            
            if (lastCity != null) {
                fetchWeather(lastCity, isRefreshingUpdate = cache != null)
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

    private suspend fun updateCache(weather: WeatherResponse, forecast: ForecastResponse) {
        val cache = WeatherCacheEntity(
            weatherJson = gson.toJson(weather),
            forecastJson = gson.toJson(forecast),
            lastUpdated = System.currentTimeMillis()
        )
        weatherDao.insertCache(cache)
    }

    fun toggleUnits() {
        isCelsius = !isCelsius
        refresh()
    }

    fun refresh() {
        val state = weatherUiState
        if (state is WeatherUiState.Success) {
            fetchWeather(state.weather.name, isRefreshingUpdate = true)
        }
    }

    fun fetchWeather(city: String, isRefreshingUpdate: Boolean = false) {
        viewModelScope.launch {
            if (isRefreshingUpdate) isRefreshing = true else weatherUiState = WeatherUiState.Loading
            try {
                coroutineScope {
                    val units = if (isCelsius) "metric" else "imperial"
                    val weatherDeferred = async { apiService.getWeather(city.trim(), units = units, apiKey = apiKey) }
                    val forecastDeferred = async { apiService.getForecast(city.trim(), units = units, apiKey = apiKey) }
                    
                    val weather = weatherDeferred.await()
                    val forecast = forecastDeferred.await()
                    
                    weatherUiState = WeatherUiState.Success(weather, forecast)
                    saveLastCity(weather.name)
                    updateCache(weather, forecast)
                    getAiAdvice(weather)
                }
            } catch (e: Exception) {
                if (!isRefreshingUpdate) weatherUiState = WeatherUiState.Error
            } finally {
                isRefreshing = false
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
                    updateCache(weather, forecast)
                    getAiAdvice(weather)
                }
            } catch (e: Exception) {
                weatherUiState = WeatherUiState.Error
            }
        }
    }

    private fun getAiAdvice(weather: WeatherResponse) {
        viewModelScope.launch {
            isAiLoading = true
            try {
                val unitStr = if (isCelsius) "°C" else "°F"
                val prompt = "Provide a 1-sentence friendly weather tip for ${weather.name} where it is currently ${weather.main.temp}$unitStr and ${weather.weather.firstOrNull()?.description}."
                val response = generativeModel.generateContent(prompt)
                aiResponse = response.text
            } catch (e: Exception) {
                aiResponse = "Check the forecast and stay prepared!"
            } finally {
                isAiLoading = false
            }
        }
    }

    fun getDetailedAiInsight() {
        val state = weatherUiState
        if (state !is WeatherUiState.Success) return
        
        viewModelScope.launch {
            detailedAiResponse = null
            try {
                val weather = state.weather
                val unitStr = if (isCelsius) "°C" else "°F"
                val prompt = """
                    As a professional meteorologist, provide a detailed 3-paragraph breakdown for ${weather.name}.
                    Current: ${weather.main.temp}$unitStr, Humidity: ${weather.main.humidity}%, Wind: ${weather.wind.speed}.
                    Paragraph 1: Atmospheric conditions analysis.
                    Paragraph 2: Health/Activity impact (allergies, exercise, etc).
                    Paragraph 3: Fun fact about this type of weather.
                """.trimIndent()
                val response = generativeModel.generateContent(prompt)
                detailedAiResponse = response.text
            } catch (e: Exception) {
                detailedAiResponse = "Detailed analysis is currently unavailable."
            }
        }
    }
}
