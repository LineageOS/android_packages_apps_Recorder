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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import org.lineageos.recorder.R;
import org.lineageos.recorder.service.IAudioVisualizer;

public class WaveFormView extends View implements IAudioVisualizer {
    private static final int DEFAULT_NUMBER_OF_WAVES = 5;

    private static final int DEFAULT_MAX_AUDIO_VALUE = 1500;

    private static final float DEFAULT_FREQUENCY = 1.5f;
    private static final float DEFAULT_AMPLITUDE = 1f;
    private static final float DEFAULT_PHASE_SHIFT = -0.2f;
    private static final float DEFAULT_DENSITY = 5f;

    private final float mMaxAudioValue;
    private final int mNumOfWaves;
    private final float mDensity;

    private final float mIdleAmplitude;
    private final float mFrequency;
    private final float mPhaseShift;

    private final Paint mPaint;
    private final Path mPath = new Path();

    private final Object mAmpLock = new Object();
    private float mAmplitude;
    private float mPhase;

    public WaveFormView(Context context) {
        this(context, null);
    }

    public WaveFormView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray ta = context.getResources().obtainAttributes(attrs,
                R.styleable.WaveFormView);

        mMaxAudioValue = (float) ta.getInt(R.styleable.WaveFormView_maxAudioValue,
                DEFAULT_MAX_AUDIO_VALUE);

        mNumOfWaves = ta.getInt(R.styleable.WaveFormView_numOfWaves,
                DEFAULT_NUMBER_OF_WAVES);
        mDensity = ta.getFloat(R.styleable.WaveFormView_density,
                DEFAULT_DENSITY);

        mAmplitude = ta.getFloat(R.styleable.WaveFormView_defaultAmplitude,
                DEFAULT_AMPLITUDE);
        mIdleAmplitude = mAmplitude;
        mFrequency = ta.getFloat(R.styleable.WaveFormView_defaultFrequency,
                DEFAULT_FREQUENCY);
        mPhaseShift = ta.getFloat(R.styleable.WaveFormView_defaultPhaseShift,
                DEFAULT_PHASE_SHIFT);

        mPaint = new Paint();
        mPaint.setColor(ta.getColor(R.styleable.WaveFormView_wavesColor, Color.WHITE));
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setStrokeWidth(1f);
        mPaint.setAntiAlias(true);

        ta.recycle();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPhase = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight();
        float width = getWidth();
        float halfHeight = height / 2f;
        float halfWidth = width / 2f;
        float maxAmplitude = halfHeight - 4f;

        float amplitude;
        synchronized (mAmpLock) {
            amplitude = mAmplitude;
        }

        for (int i = 0; i < mNumOfWaves; i++) {
            float progress = 1f - (i / (float) mNumOfWaves);
            float normalizedAmplitude = (1.5f * progress - 0.5f) * amplitude;

            mPath.reset();
            for (float x = 0f; x < width + mDensity; x += mDensity) {
                float scale = (float) -Math.pow(1 / halfWidth * (x - halfWidth), 2) + 1f;
                float y = halfHeight + (scale *
                        maxAmplitude *
                        normalizedAmplitude *
                        (float) Math.sin(2 * Math.PI * (x / width) * mFrequency + mPhase));
                if (x == 0f) {
                    mPath.moveTo(x, y);
                } else {
                    mPath.lineTo(x, y);
                }
            }
            canvas.drawPath(mPath, mPaint);
        }


        mPhase += mPhaseShift;
        invalidate();
    }

    @Override
    public void setAmplitude(int amplitude) {
        synchronized (mAmpLock) {
            mAmplitude = Math.min(amplitude / mMaxAudioValue, mIdleAmplitude);
        }
    }
}
