package org.lineageos.recorder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import org.lineageos.recorder.R;

public class OnBoardingHelper {
    private static final String ONBOARD_SCREEN_SETTINGS = "onboard_screen_settings";
    private static final String ONBOARD_SCREEN_LAST = "onboard_screen_last";
    private static final String ONBOARD_SOUND_LAST = "onboard_sound_last";
    private static final long RIPPLE_DELAY = 500;
    private static final long RIPPLE_REPEAT = 4;
    private static final long ROTATION_DELAY = 500;
    private static final int ROTATION_OPEN_APP_WAIT = 2;

    public static void onBoardLastItem(Context context, View view, boolean isSound) {
        SharedPreferences prefs = getPrefs(context);
        String key = isSound ? ONBOARD_SOUND_LAST : ONBOARD_SCREEN_LAST;
        if (prefs.getBoolean(key, false)) {
            return;
        }

        prefs.edit().putBoolean(key, true).apply();

        // Animate using ripple effect
        for (int i = 1; i <= RIPPLE_REPEAT; i++) {
            new Handler().postDelayed(pressRipple(view), i * RIPPLE_DELAY);
        }
    }

    public static void onBoardScreenSettings(Context context, View view) {
        SharedPreferences prefs = getPrefs(context);
        int appOpenTimes = prefs.getInt(ONBOARD_SCREEN_SETTINGS, 0);

        // Wait for the user to open the app 3 times before exposing this
        if (appOpenTimes <= ROTATION_OPEN_APP_WAIT) {
            prefs.edit().putInt(ONBOARD_SCREEN_SETTINGS, appOpenTimes + 1).apply();
        }

        if (appOpenTimes == ROTATION_OPEN_APP_WAIT) {
            new Handler().postDelayed(rotationAnimation(context, view), ROTATION_DELAY);
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(Utils.PREFS, 0);
    }

    @NonNull
    private static Runnable pressRipple(View view) {
        return () -> {
            view.setPressed(true);
            view.postOnAnimationDelayed(() -> view.setPressed(false), RIPPLE_DELAY + 100);
        };
    }

    @NonNull
    private static Runnable rotationAnimation(Context context, View view) {
        return () -> {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.rotation);
            view.startAnimation(animation);
        };
    }
}