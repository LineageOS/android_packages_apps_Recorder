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

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * Custom ImageButton implementation to avoid
 * performClick() warnings
 */
@SuppressLint("AppCompatCustomView")
class DragView extends ImageButton {

    public DragView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return false;
    }
}
