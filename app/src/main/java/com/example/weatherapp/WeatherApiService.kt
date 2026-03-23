package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("weather")
    suspend fun getWeather(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    @GET("weather")
    suspend fun getWeatherByCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    @GET("forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): ForecastResponse

    @GET("forecast")
    suspend fun getForecastByCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): ForecastResponse
}
