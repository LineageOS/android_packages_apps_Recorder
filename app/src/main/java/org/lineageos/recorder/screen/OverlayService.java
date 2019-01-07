/*
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.recorder.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.ui.OverlayLayer;
import org.lineageos.recorder.utils.Utils;

public class OverlayService extends Service {

    private static final String SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL =
            "screencast_overlay_notification_channel";

    public static final String EXTRA_HAS_AUDIO = "extra_audio";
    private final static int FG_ID = 123;

    /* Horrible hack to determine whether the service is running:
     * the ActivityManager.getRunningServices() method has been nuked on api 26+
     * so we're unable to properly determine if this service is currently running
     */
    public static boolean isRunning = false;

    private OverlayLayer mLayer;

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        boolean hasAudio = intent != null && intent.getBooleanExtra(EXTRA_HAS_AUDIO, false);

        mLayer = new OverlayLayer(this);
        mLayer.setOnActionClickListener(() -> {
            Intent fabIntent = new Intent(ScreencastService.ACTION_START_SCREENCAST);
            fabIntent.putExtra(ScreencastService.EXTRA_WITHAUDIO, hasAudio);
            startService(fabIntent.setClass(this, ScreencastService.class));
            Utils.setStatus(getApplication(), Utils.UiStatus.SCREEN);
            onDestroy();
        });

        Notification notification = new NotificationCompat.Builder(
                this, SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(R.string.screen_overlay_notif_title))
                .setContentText(getString(R.string.screen_overlay_notif_message))
                .setSmallIcon(R.drawable.ic_action_screen_record)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, RecorderActivity.class), 0))
                .build();

        startForeground(FG_ID, notification);
        isRunning = true;
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                notificationManager == null || notificationManager
                .getNotificationChannel(SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL) != null) {
            return;
        }

        CharSequence name = getString(R.string.screen_overlay_channel_title);
        String description = getString(R.string.screen_overlay_channel_desc);
        NotificationChannel notificationChannel =
                new NotificationChannel(SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onDestroy() {
        if (mLayer != null) {
            mLayer.destroy();
            mLayer = null;
        }

        stopForeground(true);
        isRunning = false;
        super.onDestroy();
    }
}
