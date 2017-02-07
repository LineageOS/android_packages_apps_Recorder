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

    public static AlertDialog deleteFile(Context context, final String path, boolean isSound) {
        return new AlertDialog.Builder(context)
                .setTitle(R.string.delete_title)
                .setMessage(context.getString(R.string.delete_message, path))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    File record = new File(path);
                    if (record.exists()) {
                        record.delete();
                    }
                    setLastItem(context, null, 0, isSound);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    public static Intent getShareIntent(Context context, String filePath, String mimeType) {
        File file = new File(filePath);
        Uri uri = FileProvider.getUriForFile(context, "org.lineageos.recorder.fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    public static Intent getOpenIntent(Context context, String filePath, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context,
                "org.lineageos.recorder.fileprovider", new File(filePath));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    public static void setLastItem(Context context, String path, long duration,
                                   boolean isSound) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, path)
                .putLong(isSound ? KEY_LAST_SOUND_TIME : KEY_LAST_SCREEN_TIME, duration)
                .apply();
    }

    public static String getLastItemPath(Context context, boolean isSound) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, null);
    }

    public static long getLastItemDuration(Context context, boolean isSound) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getLong(isSound ? KEY_LAST_SOUND_TIME : KEY_LAST_SCREEN_TIME, -1);
    }

    public static String getLastItemDate(Context context, boolean isSound) {
        String path = getLastItemPath(context, isSound);
        String[] pathParts = path.split("/");
        String[] date = pathParts[pathParts.length - 1]
                .replace(isSound ? ".wav" : ".mp4", "")
                .replace(isSound ? "SoundRecord" : "ScreenRecord", "")
                .split("-");
        return context.getString(R.string.date_format, date[1], date[2], date[3],
                date[4], date[5]);
    }
}
