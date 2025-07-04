package com.bogdan.waterreminder

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WeatherUtil {
    private const val TAG = "WeatherUtil"
    private const val API_KEY = "d80314e2bfdd45d8420e9a2787961b59"
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    /**
     * @param unit "C" pentru Celsius (default), "F" pentru Fahrenheit
     * @param onResult: (Int?) -> Unit - temperatura rotunjită în unitatea aleasă, sau null dacă eroare
     */
    fun getCurrentTemperature(
        context: Context,
        location: Location,
        unit: String = "C",
        onResult: (Int?) -> Unit
    ) {
        Thread {
            try {
                val unitsStr = if (unit == "F") "imperial" else "metric"
                val url =
                    "$BASE_URL?lat=${location.latitude}&lon=${location.longitude}&units=$unitsStr&appid=$API_KEY"
                Log.d(TAG, "Request URL: $url")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "AndroidApp")
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val responseCode = conn.responseCode
                val response: String = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                }
                Log.d(TAG, "HTTP response code: $responseCode")
                if (responseCode == 200) {
                    val json = JSONObject(response)
                    val main = json.getJSONObject("main")
                    val temp = main.getDouble("temp").toFloat()
                    Log.d(TAG, "Temperatura curentă: $temp ${if (unit == "F") "°F" else "°C"}")
                    Handler(Looper.getMainLooper()).post {
                        onResult(temp.toInt())
                    }
                } else {
                    Log.e(TAG, "Eroare API: $response")
                    Handler(Looper.getMainLooper()).post {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eroare request meteo: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    onResult(null)
                }
            }
        }.start()
    }
}