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

    ResizeAnimation(View mView, int mInitialRadius, int mDeltaRadius) {
        this.mView = mView;
        this.mInitialRadius = mInitialRadius;
        this.mDeltaRadius = mDeltaRadius;
    }

    @Override
    protected void applyTransformation(float mTime, Transformation mTransformation) {
        int mNewRadius = (int) (mInitialRadius + mDeltaRadius * mTime);
        mView.getLayoutParams().height = mNewRadius;
        mView.getLayoutParams().width = mNewRadius;
        mView.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}
