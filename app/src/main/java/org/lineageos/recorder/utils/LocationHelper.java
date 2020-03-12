/*
 * Copyright (C) 2020-2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.recorder.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

public final class LocationHelper {
    private static final String TAG = "LocationHelper";

    @NonNull
    private final Context context;

    @Nullable
    private final LocationManager locationManager;

    public LocationHelper(@NonNull Context context) {
        this.context = context;

        locationManager = context.getSystemService(LocationManager.class);
    }

    @Nullable
    public String getCurrentLocationName() {
        final Location lastGoodLocation = getLastGoodLocation();
        if (lastGoodLocation == null) {
            return null;
        }

        final Geocoder geocoder = new Geocoder(context);
        try {
            final List<Address> addressList = geocoder.getFromLocation(
                    lastGoodLocation.getLatitude(), lastGoodLocation.getLongitude(), 1);
            if (addressList.isEmpty()) {
                return null;
            }

            final Address address = addressList.get(0);
            final String featureName = address.getFeatureName();
            // Street numbers may be returned here, ignore them
            if (featureName != null && featureName.length() > 3) {
                return featureName;
            }
            final String locality = address.getLocality();
            if (locality != null) {
                return locality;
            }
            return address.getCountryName();
        } catch (IOException e) {
            Log.e(TAG, "Failed to obtain address from last known location", e);
        }
        return null;
    }

    @Nullable
    private Location getLastGoodLocation() {
        if (locationManager == null) {
            return null;
        }
        Location lastGoodLocation;
        try {
            lastGoodLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastGoodLocation == null) {
                // Try network provider
                lastGoodLocation = locationManager.getLastKnownLocation(
                        LocationManager.NETWORK_PROVIDER);
            }
            if (lastGoodLocation == null) {
                // Fallback from passive provider
                lastGoodLocation = locationManager.getLastKnownLocation(
                        LocationManager.PASSIVE_PROVIDER);
            }
        } catch (SecurityException e) {
            return null;
        }

        if (lastGoodLocation == null || lastGoodLocation.getAccuracy() == 0f) {
            return null;
        }
        return lastGoodLocation;
    }
}
