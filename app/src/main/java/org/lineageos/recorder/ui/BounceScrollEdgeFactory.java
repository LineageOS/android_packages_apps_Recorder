/*
 * Copyright (C) 2021 The LineageOS Project
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

import android.graphics.Canvas;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

public class BounceScrollEdgeFactory extends RecyclerView.EdgeEffectFactory {
    private static final float VELOCITY_MULTIPLIER = 0.3f;

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
        switch (direction) {
            case DIRECTION_TOP:
                return new BounceEdgeEffect(view, +VELOCITY_MULTIPLIER);
            case DIRECTION_BOTTOM:
                return new BounceEdgeEffect(view, -VELOCITY_MULTIPLIER);
            case RecyclerView.EdgeEffectFactory.DIRECTION_LEFT:
            case RecyclerView.EdgeEffectFactory.DIRECTION_RIGHT:
            default:
                return super.createEdgeEffect(view, direction);
        }
    }

    private static class BounceEdgeEffect extends EdgeEffect {

        private SpringAnimation mSpringAnimation = null;
        private final RecyclerView mView;
        private final float mVelocityMultiplier;

        public BounceEdgeEffect(RecyclerView view, float velocityMultiplier) {
            super(view.getContext());
            mView = view;
            mVelocityMultiplier = velocityMultiplier;
        }

        @Override
        public boolean isFinished() {
            return mSpringAnimation == null || !mSpringAnimation.isRunning();
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            super.onPull(deltaDistance, displacement);
            float translationY = mView.getTranslationY() +
                    (mView.getWidth() * deltaDistance * mVelocityMultiplier);
            mView.setTranslationY(translationY);

            if (mSpringAnimation != null) {
                mSpringAnimation.cancel();
            }
        }

        @Override
        public void onRelease() {
            super.onRelease();
            if (mView.getTranslationX() == 0f) {
                return;
            }
            mSpringAnimation = createAnimation();
            mSpringAnimation.start();
        }

        @Override
        public void onAbsorb(int velocity) {
            super.onAbsorb(velocity);
            int translationVelocity = (int) (mVelocityMultiplier * velocity);
            if (mSpringAnimation != null) {
                mSpringAnimation.cancel();
            }
            mSpringAnimation = createAnimation()
                    .setStartVelocity(translationVelocity);
            mSpringAnimation.start();
        }

        private SpringAnimation createAnimation() {
            return new SpringAnimation(mView, SpringAnimation.TRANSLATION_Y)
                    .setSpring(new SpringForce()
                            .setFinalPosition(0f)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                            .setStiffness(SpringForce.STIFFNESS_LOW));
        }
    }
}
