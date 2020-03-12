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
import java.text.NumberFormat;
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
