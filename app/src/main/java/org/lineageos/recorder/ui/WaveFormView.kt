/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.recorder.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors
import org.lineageos.recorder.R
import kotlin.math.pow
import kotlin.math.sin

class WaveFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val maxAudioValue: Float
    private val numOfWaves: Int
    private val density: Float
    private val idleAmplitude: Float
    private val frequency: Float
    private val phaseShift: Float

    @ColorInt
    private val wavesColor: Int
    private val paint: Paint
    private val path = Path()
    private val ampLock = Any()
    private var amplitude: Float
    private var phase = 0f

    init {
        val ta = context.resources.obtainAttributes(
            attrs,
            R.styleable.WaveFormView
        )

        maxAudioValue = ta.getInt(
            R.styleable.WaveFormView_maxAudioValue,
            DEFAULT_MAX_AUDIO_VALUE
        ).toFloat()

        numOfWaves = ta.getInt(
            R.styleable.WaveFormView_numOfWaves,
            DEFAULT_NUMBER_OF_WAVES
        )

        density = ta.getFloat(
            R.styleable.WaveFormView_density,
            DEFAULT_DENSITY
        )

        amplitude = ta.getFloat(
            R.styleable.WaveFormView_defaultAmplitude,
            DEFAULT_AMPLITUDE
        )

        idleAmplitude = amplitude

        frequency = ta.getFloat(
            R.styleable.WaveFormView_defaultFrequency,
            DEFAULT_FREQUENCY
        )

        phaseShift = ta.getFloat(
            R.styleable.WaveFormView_defaultPhaseShift,
            DEFAULT_PHASE_SHIFT
        )

        wavesColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorAccent
        )

        paint = Paint()
        paint.color = wavesColor
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = 1f
        paint.isAntiAlias = true

        ta.recycle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        phase = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val height = height.toFloat()
        val width = width.toFloat()
        val halfHeight = height / 2f
        val halfWidth = width / 2f
        val maxAmplitude = halfHeight - 4f
        val amplitude = synchronized(ampLock) { amplitude }

        for (i in 0 until numOfWaves) {
            val progress = 1f - i / numOfWaves.toFloat()
            val normalizedAmplitude = (1.5f * progress - 0.5f) * amplitude
            path.reset()
            var x = 0f
            while (x < width + density) {
                val scale =
                    -(1 / halfWidth * (x - halfWidth)).toDouble().pow(2.0).toFloat() + 1f
                val y = halfHeight + scale *
                        maxAmplitude *
                        normalizedAmplitude * sin(2 * Math.PI * (x / width) * frequency + phase)
                    .toFloat()
                if (x == 0f) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                x += density
            }
            canvas.drawPath(path, paint)
        }

        phase += phaseShift

        invalidate()
    }

    fun setAmplitude(amplitude: Int) {
        synchronized(ampLock) {
            this.amplitude =
                (amplitude / maxAudioValue).coerceAtMost(idleAmplitude)
        }
    }

    companion object {
        private const val DEFAULT_NUMBER_OF_WAVES = 5
        private const val DEFAULT_MAX_AUDIO_VALUE = 1500
        private const val DEFAULT_FREQUENCY = 1.5f
        private const val DEFAULT_AMPLITUDE = 1f
        private const val DEFAULT_PHASE_SHIFT = -0.2f
        private const val DEFAULT_DENSITY = 5f
    }
}
