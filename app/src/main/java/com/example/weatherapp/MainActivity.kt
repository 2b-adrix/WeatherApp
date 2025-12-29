package com.example.weatherapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope // Import for lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity() {


    private var city: String = "London"
    private val apiKey: String = "503b07c5c207d667e1f7eee73739ed1c"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fetchWeatherData()

        findViewById<Button>(R.id.ButtonChangeCity).setOnClickListener {
            val newCity = findViewById<EditText>(R.id.editTextCity).text.toString()
            if (newCity.isNotBlank()) {
                city = newCity
                fetchWeatherData()
            } else {
                findViewById<TextView>(R.id.errorText).text = "Please enter a city name"
                findViewById<TextView>(R.id.errorText).visibility = View.VISIBLE
                findViewById<RelativeLayout>(R.id.holder).visibility = View.GONE
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
            }
        }
    }

    private fun fetchWeatherData() {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<RelativeLayout>(R.id.holder).visibility = View.GONE
        findViewById<TextView>(R.id.errorText).visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = fetchWeatherFromApi(city, apiKey)
                updateUI(result)
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private suspend fun fetchWeatherFromApi(cityName: String, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            var response: String?
            try {
                val cleanApiKey = apiKey.trim()
                response = URL("https://api.openweathermap.org/data/2.5/weather?q=$cityName&units=metric&appid=$cleanApiKey")
                    .readText(Charsets.UTF_8)
            } catch (e: Exception) {
                response = null

            }
            response
        }
    }

    private fun updateUI(result: String?) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        if (result != null) {
            try {
                val jsonObj = JSONObject(result)
                val main = jsonObj.getJSONObject("main")
                val windSpeed = if (jsonObj.has("wind")) {
                    jsonObj.getJSONObject("wind").getString("speed") + " km/hr"
                } else {
                    "N/A"
                }
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
                val temp = main.getString("temp") + "°C"
                val humidity = main.getString("humidity") + "%"
                val maiMain = weather.getString("main")
                val icon = weather.getString("icon")


                findViewById<TextView>(R.id.temperature).text = temp
                findViewById<TextView>(R.id.humidity).text = humidity
                findViewById<TextView>(R.id.wind).text = windSpeed
                findViewById<TextView>(R.id.textCondition).text = maiMain
                findViewById<TextView>(R.id.city).text = city

                updateWeatherIcon(icon)

                findViewById<RelativeLayout>(R.id.holder).visibility = View.VISIBLE
                findViewById<TextView>(R.id.errorText).visibility = View.GONE

            } catch (e: Exception) {
                showError(e, "Error parsing weather data")
            }
        } else {
            showError(null, "Could not fetch weather data. Check your connection or city name.")
        }
    }

    private fun updateWeatherIcon(icon: String) {
        val imageView = findViewById<ImageView>(R.id.imgCondition)
        when (icon) {
            "01d" -> imageView.setImageResource(R.drawable.sun)
            "01n" -> imageView.setImageResource(R.drawable.clear_sky)
            "02d" -> imageView.setImageResource(R.drawable.fewclouds)
            "02n" -> imageView.setImageResource(R.drawable.few_cloudes)
            "03d", "03n" -> imageView.setImageResource(R.drawable.scattered_clouds)
            "04d", "04n" -> imageView.setImageResource(R.drawable.broken_cloudes)
            "09d", "09n" -> imageView.setImageResource(R.drawable.shower_rain)
            "10d", "10n" -> imageView.setImageResource(R.drawable.rain)
            "11d", "11n" -> imageView.setImageResource(R.drawable.thunderstorm)
            "13d", "13n" -> imageView.setImageResource(R.drawable.snow)
            "50d", "50n" -> imageView.setImageResource(R.drawable.mist)
            else -> imageView.setImageResource(R.drawable.sun)
        }
    }

    private fun showError(e: Exception?, customMessage: String? = null) {

        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        findViewById<RelativeLayout>(R.id.holder).visibility = View.GONE
        val errorTextView = findViewById<TextView>(R.id.errorText)
        errorTextView.text = customMessage ?: "An error occurred: ${e?.message ?: "Unknown error"}"
        errorTextView.visibility = View.VISIBLE
    }
}
