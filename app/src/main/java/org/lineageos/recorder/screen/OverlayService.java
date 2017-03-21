package org.lineageos.recorder.screen;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.ui.OverlayLayer;
import org.lineageos.recorder.utils.Utils;

public class OverlayService extends Service {
    public static final String EXTRA_HAS_AUDIO = "extra_audio";
    private final static int FG_ID = 123;
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

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.screen_overlay_notif_title))
                .setContentText(getString(R.string.screen_overlay_notif_message))
                .setSmallIcon(R.drawable.ic_action_screen_record)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, RecorderActivity.class), 0))
                .build();

        startForeground(FG_ID, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mLayer != null) {
            mLayer.destroy();
        }

        new Handler().postDelayed(() -> {
            mLayer = null;
            stopForeground(true);
            super.onDestroy();
        }, 500);
    }
}
