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
package org.lineageos.recorder.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.lineageos.recorder.R;
import org.lineageos.recorder.sounds.OnAudioLevelUpdatedListener;
import org.lineageos.recorder.utils.Utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class SoundVisualizer extends LinearLayout implements OnAudioLevelUpdatedListener {
    private static int BUBBLE_RADIUS;

    private Context mContext;
    private List<View> mWaveList = new ArrayList<>();

    public SoundVisualizer(Context mContext) {
        super(mContext);
        this.mContext = mContext;
        setup();
    }

    public SoundVisualizer(Context mContext, AttributeSet mAttrSet) {
        super(mContext, mAttrSet);
        this.mContext = mContext;
        setup();
    }

    public SoundVisualizer(Context mContext, AttributeSet mAttrSet, int mDefStyleAttr) {
        super(mContext, mAttrSet, mDefStyleAttr);
        this.mContext = mContext;

        setup();
    }

    private void setup() {
        BUBBLE_RADIUS = Utils.convertDp2Px(mContext, 24);

        // Setup layout
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Draw bubbles
        for (int mCounter = 0; mCounter < 5; mCounter++) {
            // Create container layout
            RelativeLayout mContainer = new RelativeLayout(mContext);
            RelativeLayout.LayoutParams mContainerParams = new RelativeLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mContainerParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            mContainer.setLayoutParams(mContainerParams);
            // Inflate params to set weight = 1
            LinearLayout.LayoutParams mInflateParams =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);

            // Create bubble
            View mView = new View(mContext);
            mView.setLayoutParams(new ViewGroup.LayoutParams(BUBBLE_RADIUS, BUBBLE_RADIUS));
            GradientDrawable mDrawable = new GradientDrawable();
            mDrawable.setColor(ContextCompat.getColor(mContext, R.color.colorAccentDark));
            mDrawable.setCornerRadius(BUBBLE_RADIUS);
            mView.setBackground(mDrawable);
            mView.setForegroundGravity(Gravity.CENTER);

            // Register items
            mWaveList.add(mView);
            mContainer.addView(mView, mContainerParams);
            this.addView(mContainer, mInflateParams);
        }
    }

    @Override
    public void onAudioLevelUpdated(int mValue) {
        for (int mCounter = 0; mCounter < 5 && mCounter < mWaveList.size(); mCounter++) {
            final int mFinalCount = mCounter;
            ((Activity) mContext).runOnUiThread(() -> {
                View mBubble = mWaveList.get(mFinalCount);

                int mOriginalRadius = mBubble.getHeight();
                int mSize = mValue / (new SecureRandom().nextInt(5) + 1) * 2;
                int mNewRadius = mSize;
                if (mSize < BUBBLE_RADIUS) {
                    mNewRadius = BUBBLE_RADIUS;
                } else if (mSize > this.getHeight()) {
                    mSize = this.getHeight();
                }

                ResizeAnimation mAnim = new ResizeAnimation(mBubble, mOriginalRadius,
                        mNewRadius - mOriginalRadius);
                mAnim.setDuration(100 / 3);

                mBubble.startAnimation(mAnim);
            });
        }
    }
}