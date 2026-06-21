package com.pixeleye.plantdoctor.data.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.pixeleye.plantdoctor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("data/2.5/forecast")
    suspend fun get5DayForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): retrofit2.Response<OpenWeatherForecastResponse>
}

data class OpenWeatherForecastResponse(
    @SerializedName("list") val list: List<ForecastEntry>
)

data class ForecastEntry(
    @SerializedName("dt") val dt: Long,
    @SerializedName("main") val main: MainForecastData,
    @SerializedName("weather") val weather: List<WeatherDescription>,
    @SerializedName("dt_txt") val dtTxt: String
)

data class MainForecastData(
    @SerializedName("temp") val temp: Double,
    @SerializedName("humidity") val humidity: Int
)

data class WeatherDescription(
    @SerializedName("main") val main: String,
    @SerializedName("description") val description: String
)

object WeatherRepository {
    private const val TAG = "WeatherRepository"
    private val exhaustedKeys = mutableSetOf<String>()
    
    // In-memory cache for the 5-day forecast
    @Volatile
    var cachedForecastText: String? = null
        private set

    private val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    /**
     * Fetches the 5-day forecast for the given coordinates, rotations through keys,
     * formats the result into a clean text summary, and caches it in memory.
     */
    suspend fun fetchAndCacheForecast(lat: Double, lon: Double): String? {
        return withContext(Dispatchers.IO) {
            val keysString = BuildConfig.OPENWEATHER_API_KEYS
            if (keysString.isBlank()) {
                Log.w(TAG, "No OpenWeather API keys configured in BuildConfig.")
                return@withContext null
            }

            val allKeys = keysString.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (allKeys.isEmpty()) {
                Log.w(TAG, "OpenWeather API keys list is empty.")
                return@withContext null
            }

            // Shuffle keys to distribute API request load
            val candidateKeys = allKeys.filter { it !in exhaustedKeys }
            val keysToTry = if (candidateKeys.isEmpty()) {
                Log.i(TAG, "All keys were marked as exhausted. Resetting exhausted cache.")
                exhaustedKeys.clear()
                allKeys.shuffled()
            } else {
                candidateKeys.shuffled()
            }

            var resultText: String? = null

            for (key in keysToTry) {
                try {
                    Log.d(TAG, "Attempting OpenWeather fetch with key: ${key.take(6)}...")
                    val response = api.get5DayForecast(lat, lon, key)
                    
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            // Extract noon (12:00:00) forecasts to give 1 clean summary per day for 5 days
                            val dailyForecasts = body.list
                                .filter { it.dtTxt.contains("12:00:00") }
                                .mapIndexed { index, entry ->
                                    val date = entry.dtTxt.substringBefore(" ")
                                    "Day ${index + 1} ($date): ${entry.weather.firstOrNull()?.description ?: "clear sky"}, Temp: ${entry.main.temp}°C, Humidity: ${entry.main.humidity}%"
                                }
                            
                            val formattedText = if (dailyForecasts.isNotEmpty()) {
                                dailyForecasts.joinToString("\n")
                            } else {
                                // Fallback if no 12:00:00 points are found
                                body.list.take(5).mapIndexed { index, entry ->
                                    "Day ${index + 1}: ${entry.weather.firstOrNull()?.description ?: "clear sky"}, Temp: ${entry.main.temp}°C, Humidity: ${entry.main.humidity}%"
                                }.joinToString("\n")
                            }

                            Log.d(TAG, "Weather forecast fetched successfully: \n$formattedText")
                            resultText = formattedText
                            cachedForecastText = formattedText
                            break // Success! Exit key loop
                        }
                    } else {
                        val code = response.code()
                        Log.w(TAG, "OpenWeather call failed with code: $code")
                        if (code == 429 || code == 401) {
                            Log.w(TAG, "Key $key is exhausted or invalid. Adding to exhausted list.")
                            exhaustedKeys.add(key)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception fetching weather with key: ${key.take(6)}", e)
                }
            }

            resultText
        }
    }

    /**
     * Clears the in-memory cache.
     */
    fun clearCache() {
        cachedForecastText = null
    }
}
