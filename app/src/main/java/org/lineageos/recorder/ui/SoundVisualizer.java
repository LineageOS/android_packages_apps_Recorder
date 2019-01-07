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
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.lineageos.recorder.R;
import org.lineageos.recorder.sounds.OnAudioLevelUpdatedListener;
import org.lineageos.recorder.utils.Utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class SoundVisualizer extends LinearLayout implements OnAudioLevelUpdatedListener {
    private final Context mContext;
    private final List<View> mWaveList = new ArrayList<>();
    private final int mBubbleRadius;
    private final AccelerateDecelerateInterpolator mInterpolator =
            new AccelerateDecelerateInterpolator();

    public SoundVisualizer(Context context) {
        this(context, null);
    }

    public SoundVisualizer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SoundVisualizer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;
        mBubbleRadius = Utils.convertDp2Px(context, 24);

        // Setup layout
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Draw bubbles
        for (int counter = 0; counter < 5; counter++) {
            // Create container layout
            RelativeLayout container = new RelativeLayout(context);
            RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            containerParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            container.setLayoutParams(containerParams);

            // Inflate params to set weight = 1
            LinearLayout.LayoutParams inflateParams =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);

            // Create bubble
            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(mBubbleRadius, mBubbleRadius));

            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(ContextCompat.getColor(context, R.color.icon));
            drawable.setCornerRadius(mBubbleRadius);

            view.setBackground(drawable);
            view.setForegroundGravity(Gravity.CENTER);

            // Register items
            mWaveList.add(view);
            container.addView(view, containerParams);
            addView(container, inflateParams);
        }
    }

    @Override
    public void onAudioLevelUpdated(int value) {
        SecureRandom random = new SecureRandom();
        for (int counter = 0; counter < 5 && counter < mWaveList.size(); counter++) {
            final int finalCount = counter;
            ((Activity) mContext).runOnUiThread(() -> {
                View bubble = mWaveList.get(finalCount);

                int originalRadius = bubble.getHeight();
                int size = value / (random.nextInt(5) + 1) * 2 + 10;
                int newRadius = Math.max(size, mBubbleRadius);

                ResizeAnimation anim = new ResizeAnimation(bubble, originalRadius,
                        newRadius - originalRadius);
                anim.setInterpolator(mInterpolator);
                anim.setDuration(150);

                bubble.startAnimation(anim);
            });
        }
    }
}
