package org.lineageos.recorder.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;

import org.lineageos.recorder.R;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

public class LastRecordHelper {
    private static final String TAG = LastRecordHelper.class.getSimpleName();
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SOUND = "sound_last_path";
    private static final String KEY_LAST_SCREEN = "screen_last_path";
    private static final String KEY_LAST_SOUND_TIME = "sound_last_duration";
    private static final String KEY_LAST_SCREEN_TIME = "screen_last_duration";

    private LastRecordHelper() {
    }

    public static AlertDialog deleteFile(Context sContext, String sPath, boolean isSound) {
        return new AlertDialog.Builder(sContext)
                .setTitle(R.string.delete_title)
                .setMessage(sContext.getString(R.string.delete_message, sPath))
                .setPositiveButton(R.string.delete, (mDialog, mView) -> {
                    File mRecord = new File(sPath);
                    if (mRecord.exists()) {
                        mRecord.delete();
                    }
                    setLastItem(sContext, null, 0, isSound);
                })
                .setNegativeButton(android.R.string.cancel, (mDialog, mView) -> {})
                .create();
    }

    //
    public static Intent getShareIntent(Context sContext, String sFile, String sType) {
        Uri sUri = FileProvider.getUriForFile(sContext,
                "org.lineageos.recorder.fileprovider", new File(sFile));
        Intent sIntent = new Intent(Intent.ACTION_SEND);
        sIntent.setType(sType);
        sIntent.putExtra(Intent.EXTRA_STREAM, sUri);
        sIntent.putExtra(Intent.EXTRA_SUBJECT, new File(sFile).getName());
        Intent sChooser = Intent.createChooser(sIntent, null);
        sChooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return sChooser;
    }

    public static Intent getOpenIntent(Context sContext, String sFile, String sType) {
        Uri sUri = FileProvider.getUriForFile(sContext,
                "org.lineageos.recorder.fileprovider", new File(sFile));
        Intent sIntent = new Intent(Intent.ACTION_VIEW);
        sIntent.setDataAndType(sUri, sType);
        sIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return sIntent;
    }

    public static void setLastItem(Context sContext, String sPath, long sDuration,
                                   boolean isSound) {
        SharedPreferences sPrefs = sContext.getSharedPreferences(PREFS, 0);
        sPrefs.edit()
                .putString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, sPath)
                .putLong(isSound ? KEY_LAST_SOUND_TIME : KEY_LAST_SCREEN_TIME, sDuration)
                .apply();
    }

    public static String getLastItemPath(Context sContext, boolean isSound) {
        SharedPreferences sPrefs = sContext.getSharedPreferences(PREFS, 0);
        return sPrefs.getString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, null);
    }

    public static long getLastItemDuration(Context sContext, boolean isSound) {
        SharedPreferences sPrefs = sContext.getSharedPreferences(PREFS, 0);
        return sPrefs.getLong(isSound ? KEY_LAST_SOUND_TIME : KEY_LAST_SCREEN_TIME, -1);
    }

    public static String getLastItemDate(Context sContext, boolean isSound) {
        String sPath = getLastItemPath(sContext, isSound);
        String[] sPathArray = sPath.split("/");
        String[] sDate = sPathArray[sPathArray.length - 1]
                .replace(isSound ? ".wav" : ".mp4", "")
                .replace(isSound ? "SoundRecord" : "ScreenRecord", "")
                .split("-");
        return sContext.getString(R.string.date_format, sDate[1], sDate[2], sDate[3],
                sDate[4], sDate[5]);
    }
}
