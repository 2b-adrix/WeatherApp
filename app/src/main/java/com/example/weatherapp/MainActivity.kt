package com.example.weatherapp

import android.os.AsyncTask
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
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity() {

    var CITY: String = "London"
    var api: String = "87fd3238b1408462c0d6d66cd73135f1"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        weathertask().execute()
        findViewById<Button>(R.id.ButtonChangeCity).setOnClickListener {
            CITY = findViewById<EditText>(R.id.editTextCity).text.toString()
            weathertask().execute()
        }
    }


    inner class weathertask(): AsyncTask<String, Void,String>()
    {
        override fun onPreExecute() {
            super.onPreExecute()
            findViewById<RelativeLayout>(R.id.holder).visibility = View.GONE
            findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE

        }


        override fun doInBackground(vararg params: String?): String? {
            var response : String ?
            try {
                response = URL("https://api.openweathermap.org/data/2.5/weather?q=$CITY&units=metric&appid=$api")
                    .readText(Charsets.UTF_8)
            }
            catch (e: Exception)
            {
                response = null
            }
            return response
        }


        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            try {

                val jsonObj = JSONObject(result)
                val main = jsonObj.getJSONObject("main")
                val wind = jsonObj.getJSONObject("wind")
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
                val temp = main.getString("temp") + "°C"
                val humidity = main.getString("humidity")+"%"
                val windSpeed = main.getString("Speed")+"km/hr"
                val maiMain= weather.getString("main")
                val icon = weather.getString("icon")


                findViewById<TextView>(R.id.temperature).text=temp
                findViewById<TextView>(R.id.humidity).text=humidity
                findViewById<TextView>(R.id.wind).text=windSpeed
                findViewById<TextView>(R.id.textCondition).text=maiMain
                findViewById<TextView>(R.id.city).text=CITY


                when(icon)
                {
                    "01d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.sun)
                    "01n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.clear_sky)
                    "02d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.fewclouds)
                    "02n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.few_cloudes)
                    "03d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.scattered_clouds)
                    "03n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.scattered_clouds)
                    "04d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.broken_cloudes)
                    "04n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.broken_cloudes)
                    "09d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.shower_rain)
                    "09n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.shower_rain)
                    "10d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.rain)
                    "10n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.rain)
                    "11d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.thunderstorm)
                    "11n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.thunderstorm)
                    "13d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.snow)
                    "13n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.snow)
                    "50d" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.mist)
                    "50n" -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.mist)
                    else -> findViewById<ImageView>(R.id.imgCondition).setImageResource(R.drawable.sun)
                }
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                findViewById<RelativeLayout>(R.id.holder).visibility = View.VISIBLE
            }

            catch (e: Exception)
            {
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                findViewById<TextView>(R.id.errorText).visibility = View.VISIBLE
                findViewById<RelativeLayout>(R.id.holder).visibility = View.GONE


            }
        }

    }
}