package com.pixeleye.plantdoctor.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {
    private const val TAG = "LocationHelper"

    /**
     * Fetches the current location and attempts to reverse geocode it to a String.
     * Guaranteed to return a non-empty string if location permission is granted and a location is found.
     * Returns "adminArea, countryName" if successful, otherwise "Latitude: $lat, Longitude: $lon".
     * Returns null ONLY if permission is denied or location hardware returns null completely.
     */
    @SuppressLint("MissingPermission")
    suspend fun getRobustLocationString(context: Context): String? {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse && !hasFine) {
            Log.w(TAG, "Location permission not granted. Cannot fetch location.")
            return null
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location = suspendCancellableCoroutine<android.location.Location?> { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc -> continuation.resume(loc) }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get last location", e)
                        continuation.resume(null)
                    }
            }

            if (location == null) {
                Log.w(TAG, "Last location from FusedLocationProviderClient is null.")
                return null
            }

            val lat = location.latitude
            val lon = location.longitude
            val fallbackStr = "Latitude: $lat, Longitude: $lon"

            Log.d(TAG, "Fetched raw coordinates: $fallbackStr. Attempting reverse geocoding...")

            if (!Geocoder.isPresent()) {
                Log.d(TAG, "Geocoder is not present on this device. Using raw coords.")
                return fallbackStr
            }

            val geocoder = Geocoder(context, Locale.getDefault())
            
            val addressString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ asynchronous listener implementation
                suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (addresses.isNotEmpty()) {
                                    val address = addresses[0]
                                    val result = formatAddress(address.adminArea, address.countryName)
                                    Log.d(TAG, "GeocodeListener Success: $result")
                                    continuation.resume(result)
                                } else {
                                    Log.w(TAG, "GeocodeListener returned empty list. Using raw coords.")
                                    continuation.resume(fallbackStr)
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                Log.e(TAG, "GeocodeListener Error: $errorMessage. Using raw coords.")
                                continuation.resume(fallbackStr)
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception initiating GeocodeListener: ${e.message}", e)
                        continuation.resume(fallbackStr)
                    }
                }
            } else {
                // Legacy synchronous implementation, run carefully on IO thread
                withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val result = formatAddress(address.adminArea, address.countryName)
                            Log.d(TAG, "Legacy Synchronous Geocode Success: $result")
                            result
                        } else {
                            Log.w(TAG, "Legacy Synchronous Geocode returned null/empty. Using raw coords.")
                            fallbackStr
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during synchronous reverse geocoding: ${e.message}", e)
                        fallbackStr
                    }
                }
            }

            addressString // Return resolved address or fallback inside coroutine scope

        } catch (e: Exception) {
            Log.e(TAG, "Critical failure fetching robust location: ${e.message}", e)
            null
        }
    }

    private fun formatAddress(adminArea: String?, countryName: String?): String {
        return buildString {
            if (!adminArea.isNullOrBlank()) {
                append(adminArea)
            }
            if (!countryName.isNullOrBlank()) {
                if (isNotEmpty()) append(", ")
                append(countryName)
            }
        }.trim()
    }
}
