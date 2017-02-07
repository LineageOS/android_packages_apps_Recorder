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

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

public class ScreenFragment extends Fragment implements View.OnClickListener {
    public static final int REQUEST_AUDIO_PERMS = 654;
    private static final String TYPE = "video/mp4";

    private Activity mActivity;

    private Switch mAudioSwitch;
    private RelativeLayout mWarningLayout;
    private CardView mStopCard;
    private CardView mLastCard;
    private TextView mLastMessage;
    private Button mRequestButton;
    private Button mStopButton;
    private ImageButton mPlayButton;
    private ImageButton mShareButton;
    private ImageButton mDeleteButton;

    public ScreenFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_screen, container, false);

        mAudioSwitch = (Switch) view.findViewById(R.id.screen_audio_toggle);
        mWarningLayout = (RelativeLayout) view.findViewById(R.id.screen_warning_layout);
        mStopCard = (CardView) view.findViewById(R.id.screen_card_recording);
        mLastCard = (CardView) view.findViewById(R.id.screen_card_last);
        mLastMessage = (TextView) view.findViewById(R.id.screen_last_message);
        mRequestButton = (Button) view.findViewById(R.id.screen_warning_button);
        mStopButton = (Button) view.findViewById(R.id.screen_recording_button);
        mPlayButton = (ImageButton) view.findViewById(R.id.screen_last_play);
        mShareButton = (ImageButton) view.findViewById(R.id.screen_last_share);
        mDeleteButton = (ImageButton) view.findViewById(R.id.screen_last_delete);

        mActivity = getActivity();

        mAudioSwitch.setOnCheckedChangeListener((button, checked) ->
                mAudioSwitch.setText(getString(checked ?
                        R.string.screen_audio_message_on : R.string.screen_audio_message_off)));

        mRequestButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mShareButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);

        refresh();

        return view;
    }

    @Override
    public void onClick(View v) {
        if (v == mRequestButton) {
            final String[] perms = {Manifest.permission.RECORD_AUDIO};
            mActivity.requestPermissions(perms, REQUEST_AUDIO_PERMS);
        } else if (v == mStopButton) {
            ((RecorderActivity) mActivity).toggleScreenRecorder();
        } else if (v == mPlayButton) {
            startActivityForResult(LastRecordHelper.getOpenIntent(mActivity, getFile(), TYPE), 0);
        } else if (v == mShareButton) {
            startActivity(LastRecordHelper.getShareIntent(mActivity, getFile(), TYPE));
        } else if (v == mDeleteButton) {
            AlertDialog dialog = LastRecordHelper.deleteFile(mActivity, getFile(), false);
            dialog.setOnDismissListener(d -> refresh());
            dialog.show();
        }
    }

    public void refresh() {
        if (mActivity == null) {
            return;
        }

        boolean hasAudioPermission = hasPermission();
        mAudioSwitch.setVisibility(hasAudioPermission ? View.VISIBLE : View.GONE);
        mWarningLayout.setVisibility(hasAudioPermission ? View.GONE : View.VISIBLE);
        mStopCard.setVisibility(Utils.isScreenRecording(mActivity) ? View.VISIBLE : View.GONE);
        mAudioSwitch.setEnabled(!Utils.isScreenRecording(mActivity));
        boolean hasLastRecord = getFile() != null;
        mLastCard.setVisibility(hasLastRecord ? View.VISIBLE : View.GONE);
        if (hasLastRecord) {
            mLastMessage.setText(mActivity.getString(R.string.screen_last_message,
                    LastRecordHelper.getLastItemDate(mActivity, false),
                    LastRecordHelper.getLastItemDuration(mActivity, false) / 1000));
        }
    }

    private boolean hasPermission() {
        int result = mActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean withAudio() {
        return mAudioSwitch.isChecked();
    }

    private String getFile() {
        if (mActivity == null) {
            return null;
        }

        return LastRecordHelper.getLastItemPath(mActivity, false);
    }
}
