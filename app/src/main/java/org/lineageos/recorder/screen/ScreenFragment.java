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
import android.content.Context;
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

public class ScreenFragment extends Fragment {
    public static final int REQUEST_AUDIO_PERMS = 654;
    private static final String TYPE = "video/mp4";

    private Activity mActivity;

    private Switch mAudioSwitch;
    private RelativeLayout mWarningLayout;
    private CardView mStopCard;
    private CardView mLastCard;
    private TextView mLastMessage;

    public ScreenFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater mInflater, ViewGroup mContainer,
                             Bundle mSavedInstance) {
        View mView = mInflater.inflate(R.layout.fragment_screen, mContainer, false);

        mAudioSwitch = (Switch) mView.findViewById(R.id.screen_audio_toggle);
        mWarningLayout = (RelativeLayout) mView.findViewById(R.id.screen_warning_layout);
        mStopCard = (CardView) mView.findViewById(R.id.screen_card_recording);
        mLastCard = (CardView) mView.findViewById(R.id.screen_card_last);
        mLastMessage = (TextView) mView.findViewById(R.id.screen_last_message);
        Button mRequestButton = (Button) mView.findViewById(R.id.screen_warning_button);
        Button mStopButton = (Button) mView.findViewById(R.id.screen_recording_button);
        ImageButton mPlayButton = (ImageButton) mView.findViewById(R.id.screen_last_play);
        ImageButton mShareButton = (ImageButton) mView.findViewById(R.id.screen_last_share);
        ImageButton mDeleteButton = (ImageButton) mView.findViewById(R.id.screen_last_delete);

        mActivity = getActivity();

        mAudioSwitch.setOnCheckedChangeListener((mButton, mStatus) ->
                mAudioSwitch.setText(getString(mStatus ?
                        R.string.screen_audio_message_on : R.string.screen_audio_message_off)));

        mRequestButton.setOnClickListener(mButtonView -> {
            String mPerms[] = {Manifest.permission.RECORD_AUDIO};
            mActivity.requestPermissions(mPerms, REQUEST_AUDIO_PERMS);
        });

        mStopButton.setOnClickListener(mButtonView ->
                ((RecorderActivity) getActivity()).toggleScreenRecorder());
        mPlayButton.setOnClickListener(mButtonView -> startActivityForResult(
                LastRecordHelper.getOpenIntent(getContext(), getFile(), TYPE), 0));
        mShareButton.setOnClickListener(mButtonView ->
                startActivity(LastRecordHelper.getShareIntent(getContext(), getFile(), TYPE)));
        mDeleteButton.setOnClickListener(mButtonView -> {
            AlertDialog mDialog = LastRecordHelper.deleteFile(getContext(), getFile(), false);
            mDialog.setOnDismissListener(mListener -> refresh(mActivity));
            mDialog.show();
        });

        refresh(getContext());
        return mView;
    }

    // Pass context to avoid unexpected NPE when refreshing from RecorderActivity
    public void refresh(Context mContext) {
        if (mActivity == null) {
            return;
        }

        boolean hasAudioPermission = hasPermission();
        mAudioSwitch.setVisibility(hasAudioPermission ? View.VISIBLE : View.GONE);
        mWarningLayout.setVisibility(hasAudioPermission ? View.GONE : View.VISIBLE);
        mStopCard.setVisibility(Utils.isScreenRecording(mContext) ? View.VISIBLE : View.GONE);
        mAudioSwitch.setEnabled(!Utils.isScreenRecording(mContext));
        boolean hasLastRecord = getFile() != null;
        mLastCard.setVisibility(hasLastRecord ? View.VISIBLE : View.GONE);
        if (hasLastRecord) {
            mLastMessage.setText(mActivity.getString(R.string.screen_last_message,
                    LastRecordHelper.getLastItemDate(mActivity, false),
                    LastRecordHelper.getLastItemDuration(mActivity, false) / 1000));
        }
    }

    private boolean hasPermission() {
        int mRes = mActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return mRes == PackageManager.PERMISSION_GRANTED;
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
