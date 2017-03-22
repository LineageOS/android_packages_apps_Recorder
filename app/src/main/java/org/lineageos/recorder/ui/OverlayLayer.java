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

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import org.lineageos.recorder.R;

public class OverlayLayer extends View {

    private final FrameLayout mLayout;
    private final WindowManager mManager;
    private final WindowManager.LayoutParams mParams;
    private final ImageButton mButton;

    public OverlayLayer(Context context) {
        super(context);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayout = new FrameLayout(context);
        mManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.START;
        mManager.addView(mLayout, mParams);
        inflater.inflate(R.layout.window_screen_recorder_overlay, mLayout);

        mButton = (ImageButton) mLayout.findViewById(R.id.overlay_button);
        ImageButton mDrag = (ImageButton) mLayout.findViewById(R.id.overlay_drag);
        mDrag.setOnTouchListener(new OnTouchListener() {
            private int origX;
            private int origY;
            private int touchX;
            private int touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        origX = mParams.x;
                        origY = mParams.y;
                        touchX = x;
                        touchY = y;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = origX + x - touchX;
                        mParams.y = origY + y - touchY;
                        mManager.updateViewLayout(mLayout, mParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        break;
                    default:
                        return false;
                }

                return true;
            }
        });
    }

    public void destroy() {
        mLayout.setVisibility(GONE);
        mManager.removeView(mLayout);
    }

    public void setOnActionClickListener(ActionClickListener listener) {
        mButton.setOnClickListener(v -> listener.onClick());
    }

    public interface ActionClickListener {
        void onClick();
    }
}
