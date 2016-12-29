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
package org.lineageos.recorder.utils;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import org.lineageos.recorder.RecorderActivity;

public class PhoneStateChangeListener extends PhoneStateListener {
    private int mOldCallState = TelephonyManager.CALL_STATE_IDLE;

    private Context mContext;

    public PhoneStateChangeListener(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public void onCallStateChanged(int mState, String mIncomingNumber) {
        switch (mState) {
            case TelephonyManager.CALL_STATE_IDLE:
                if (mOldCallState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    mOldCallState = mState;
                }
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (mOldCallState == TelephonyManager.CALL_STATE_IDLE) {
                    mOldCallState = mState;

                    // Stop sound recorder
                    ((RecorderActivity) mContext).toggleSoundRecorder();
                }
                break;
        }
    }
}
