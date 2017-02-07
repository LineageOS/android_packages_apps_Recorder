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

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Based on Stackoverflow 18742274
 * Resize a view with animation
 */
class ResizeAnimation extends Animation {

    private final int mInitialRadius;
    private final int mDeltaRadius;
    private final View mView;

    ResizeAnimation(View view, int initialRadius, int deltaRadius) {
        mView = view;
        mInitialRadius = initialRadius;
        mDeltaRadius = deltaRadius;
    }

    @Override
    protected void applyTransformation(float time, Transformation transformation) {
        int newRadius = (int) (mInitialRadius + mDeltaRadius * time);
        mView.getLayoutParams().height = newRadius;
        mView.getLayoutParams().width = newRadius;
        mView.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}
