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

public class SoundFragment extends Fragment implements View.OnClickListener {
    private static final String TYPE = "audio/wav";

    private Activity mActivity;
    private TextView mCardTitle;
    private SoundVisualizer mVisualizer;
    private Button mStopButton;
    private ImageButton mPlayButton;
    private ImageButton mShareButton;
    private ImageButton mDeleteButton;
    private CardView mLastCard;
    private TextView mLastMessage;

    public SoundFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sound, container, false);

        mActivity = getActivity();

        mCardTitle = (TextView) view.findViewById(R.id.sound_recording_title);
        mVisualizer = (SoundVisualizer) view.findViewById(R.id.sound_visualizer);
        mStopButton = (Button) view.findViewById(R.id.sound_recording_button);
        mLastCard = (CardView) view.findViewById(R.id.sound_card_last);
        mLastMessage = (TextView) view.findViewById(R.id.sound_last_message);
        mPlayButton = (ImageButton) view.findViewById(R.id.sound_last_play);
        mShareButton = (ImageButton) view.findViewById(R.id.sound_last_share);
        mDeleteButton = (ImageButton) view.findViewById(R.id.sound_last_delete);

        mStopButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mShareButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);

        refresh();
        return view;
    }

    @Override
    public void onClick(View v) {
        if (v == mStopButton) {
            ((RecorderActivity) getActivity()).toggleSoundRecorder();
        } else if (v == mPlayButton) {
            startActivityForResult(
                    LastRecordHelper.getOpenIntent(getContext(), getFile(), TYPE), 0);
        } else if (v == mShareButton) {
            startActivity(LastRecordHelper.getShareIntent(getContext(), getFile(), TYPE));
        } else if (v == mDeleteButton) {
            AlertDialog dialog = LastRecordHelper.deleteFile(getContext(), getFile(), true);
            dialog.setOnDismissListener(d -> refresh());
            dialog.show();
        }
    }

    public SoundVisualizer getVisualizer() {
        return mVisualizer;
    }

    // Pass context to avoid unexpected NPE when refreshing from RecorderActivity
    public void refresh() {
        final Context context = getActivity();
        if (context == null) {
            return;
        }

        boolean recording = Utils.isRecording(context);
        boolean recordingSound = Utils.isSoundRecording(context);
        mCardTitle.setText(context.getString(
                recordingSound ? R.string.sound_recording_title_working :
                        recording ? R.string.sound_recording_title_busy :
                                R.string.sound_recording_title_ready));

        mVisualizer.onAudioLevelUpdated(0);
        mStopButton.setVisibility(recordingSound ? View.VISIBLE : View.GONE);
        boolean hasLastRecord = getFile() != null;
        mLastCard.setVisibility(hasLastRecord ? View.VISIBLE : View.GONE);
        if (hasLastRecord) {
            mLastMessage.setText(mActivity.getString(R.string.sound_last_message,
                    LastRecordHelper.getLastItemDate(context, true),
                    LastRecordHelper.getLastItemDuration(context, true)));
        }
    }

    private String getFile() {
        final Context context = getActivity();
        if (context == null) {
            return null;
        }

        return LastRecordHelper.getLastItemPath(context, true);
    }
}
