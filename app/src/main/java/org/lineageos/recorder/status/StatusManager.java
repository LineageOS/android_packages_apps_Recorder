/*
 * Copyright (C) 2021 The LineageOS Project
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
package org.lineageos.recorder.status;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import java.util.ArrayList;
import java.util.List;

public final class StatusManager implements LifecycleObserver {
    private static final String PREFS = "preferences";
    private static final String KEY_STATUS = "status";
    public static final String VALUE_STATUS_READY = "nothing";
    private static final String VALUE_STATUS_RECORDING = "recording";
    private static final String VALUE_STATUS_PAUSED = "paused";

    private static volatile StatusManager sInstance;

    private final List<StatusListener> mListeners;
    private final SharedPreferences mPreferences;

    private StatusManager(Context context) {
        mListeners = new ArrayList<>();
        mPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static StatusManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (StatusManager.class) {
                if (sInstance == null) {
                    final Context appContext = context.getApplicationContext();
                    sInstance = new StatusManager(appContext == null ? context : appContext);
                }
            }
        }
        return sInstance;
    }

    public UiStatus getStatus() {
        switch (getStatusStr()) {
            case VALUE_STATUS_RECORDING:
                return UiStatus.RECORDING;
            case VALUE_STATUS_PAUSED:
                return UiStatus.PAUSED;
            default:
                return UiStatus.READY;
        }
    }

    public void setStatus(UiStatus newStatus) {
        final String newStatusStr;
        switch (newStatus) {
            case PAUSED:
                newStatusStr = VALUE_STATUS_PAUSED;
                break;
            case RECORDING:
                newStatusStr = VALUE_STATUS_RECORDING;
                break;
            default:
                newStatusStr = VALUE_STATUS_READY;
                break;
        }
        mPreferences.edit()
                .putString(KEY_STATUS, newStatusStr)
                .apply();
        mListeners.forEach(listener -> listener.onStatusChanged(newStatus));
    }

    public boolean isRecording() {
        return !VALUE_STATUS_READY.equals(getStatusStr());
    }

    public boolean isPaused() {
        return VALUE_STATUS_PAUSED.equals(getStatusStr());
    }

    public <T extends StatusListener & LifecycleOwner> void addListener(T listener) {
        mListeners.add(listener);
        //noinspection unused
        listener.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroy() {
                mListeners.remove(listener);
            }
        });
    }

    private String getStatusStr() {
        return mPreferences.getString(KEY_STATUS, VALUE_STATUS_READY);
    }
}
