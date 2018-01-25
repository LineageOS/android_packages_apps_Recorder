package org.lineageos.recorder.screen;

import android.os.Binder;


public class ScreenBinder extends Binder {
    private final ScreencastService mService;

    ScreenBinder(ScreencastService service) {
        super();
        mService = service;
    }

    public ScreencastService getService() {
        return mService;
    }


}