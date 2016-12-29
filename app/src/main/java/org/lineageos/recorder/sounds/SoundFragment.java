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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.lineageos.recorder.R;
import org.lineageos.recorder.RecorderActivity;
import org.lineageos.recorder.ui.SoundVisualizer;
import org.lineageos.recorder.utils.Utils;

public class SoundFragment extends Fragment {

    private Activity mActivity;
    private TextView mCardTitle;
    private SoundVisualizer mVisualizer;
    private Button mStopButton;

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
        mStopButton.setOnClickListener(mButtonView ->
                ((RecorderActivity) mActivity).toggleSoundRecorder());

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
            mCardTitle.setText(getString(Utils.isSoundRecording(mContext) ?
                    R.string.sound_recording_title_working : R.string.sound_recording_title_busy));
        } else {
            mCardTitle.setText(getString(R.string.sound_recording_title_ready));
        }
        mVisualizer.onAudioLevelUpdated(0);
        mStopButton.setVisibility(Utils.isSoundRecording(mContext) ? View.VISIBLE : View.GONE);
    }


}
