/*
 * SPDX-FileCopyrightText: 2020-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import java.io.IOException

class LocationHelper(private val context: Context) {
    private val preferences = PreferencesManager(context)

    // System services
    private val locationManager: LocationManager? by lazy {
        context.getSystemService(LocationManager::class.java)
    }

    val currentLocationName: String?
        get() = lastGoodLocation
            ?.let { lastGoodLocation: Location ->
                val geocoder = Geocoder(context)
                try {
                    geocoder.getFromLocation(
                        lastGoodLocation.latitude,
                        lastGoodLocation.longitude, 1
                    )
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to obtain address from last known location", e)
                    emptyList<Address>()
                }
            }?.firstOrNull()
            ?.let { address: Address ->
                address.featureName?.takeIf {
                    // Street numbers may be returned here, ignore them
                    it.length > 3
                } ?: address.locality ?: address.countryName
            }

    private val lastGoodLocation: Location?
        get() {
            if (!preferences.tagWithLocation) {
                return null
            }

            val lastGoodLocation = try {
                locationManager?.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                ) ?: locationManager?.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER
                ) ?: locationManager?.getLastKnownLocation(
                    LocationManager.PASSIVE_PROVIDER
                )
            } catch (e: SecurityException) {
                null
            }

            return lastGoodLocation?.takeIf { it.accuracy != 0f }
        }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
