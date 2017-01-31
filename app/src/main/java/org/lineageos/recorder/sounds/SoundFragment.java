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
package org.lineageos.recorder.sounds;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.utils.LastRecordHelper;
import org.lineageos.recorder.utils.Utils;

public class SoundFragment extends Fragment {
    private static final String TYPE = "audio/wav";

    private Activity mActivity;
    private TextView mCardTitle;
    private SoundVisualizer mVisualizer;
    private Button mStopButton;
    private CardView mLastCard;
    private TextView mLastMessage;

    public SoundFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater mInflater, ViewGroup mContainer,
                             Bundle mSavedInstance) {
        View mView = mInflater.inflate(R.layout.fragment_sound, mContainer, false);

        mActivity = getActivity();

        mCardTitle = (TextView) mView.findViewById(R.id.sound_recording_title);
        mVisualizer = (SoundVisualizer) mView.findViewById(R.id.sound_visualizer);
        mStopButton = (Button) mView.findViewById(R.id.sound_recording_button);
        mLastCard = (CardView) mView.findViewById(R.id.sound_card_last);
        mLastMessage = (TextView) mView.findViewById(R.id.sound_last_message);
        ImageButton mPlayButton = (ImageButton) mView.findViewById(R.id.sound_last_play);
        ImageButton mShareButton = (ImageButton) mView.findViewById(R.id.sound_last_share);
        ImageButton mDeleteButton = (ImageButton) mView.findViewById(R.id.sound_last_delete);

        mStopButton.setOnClickListener(mButtonView ->
                ((RecorderActivity) mActivity).toggleSoundRecorder());
        mPlayButton.setOnClickListener(mButtonView -> startActivityForResult(
                LastRecordHelper.getOpenIntent(getContext(), getFile(), TYPE), 0));
        mShareButton.setOnClickListener(mButtonView ->
                startActivity(LastRecordHelper.getShareIntent(getContext(), getFile(), TYPE)));
        mDeleteButton.setOnClickListener(mButtonView -> {
            AlertDialog mDialog = LastRecordHelper.deleteFile(getContext(), getFile(), true);
            mDialog.setOnDismissListener(mListener -> refresh(mActivity));
            mDialog.show();
        });

        refresh(getContext());
        return mView;
    }

    public SoundVisualizer getVisualizer() {
        return mVisualizer;
    }

    // Pass context to avoid unexpected NPE when refreshing from RecorderActivity
    public void refresh(Context mContext) {
        if (mActivity == null) {
            return;
        }

        if (Utils.isRecording(mContext)) {
            mCardTitle.setText(mContext.getString(Utils.isSoundRecording(mContext) ?
                    R.string.sound_recording_title_working : R.string.sound_recording_title_busy));
        } else {
            mCardTitle.setText(mContext.getString(R.string.sound_recording_title_ready));
        }
        mVisualizer.onAudioLevelUpdated(0);
        mStopButton.setVisibility(Utils.isSoundRecording(mContext) ? View.VISIBLE : View.GONE);
        boolean hasLastRecord = getFile() != null;
        mLastCard.setVisibility(hasLastRecord ? View.VISIBLE : View.GONE);
        if (hasLastRecord) {
            mLastMessage.setText(mActivity.getString(R.string.sound_last_message,
                    LastRecordHelper.getLastItemDate(mActivity, true),
                    LastRecordHelper.getLastItemDuration(mActivity, true) / 100));
        }
    }

    private String getFile() {
        if (mActivity == null) {
            return null;
        }

        return LastRecordHelper.getLastItemPath(mActivity, true);
    }


}
