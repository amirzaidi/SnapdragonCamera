/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions are
 *     met:
 *         * Redistributions of source code must retain the above copyright
 *           notice, this list of conditions and the following disclaimer.
 *         * Redistributions in binary form must reproduce the above
 *           copyright notice, this list of conditions and the following
 *           disclaimer in the documentation and/or other materials provided
 *           with the distribution.
 *         * Neither the name of The Linux Foundation nor the names of its
 *           contributors may be used to endorse or promote products derived
 *           from this software without specific prior written permission.
 *
 *     THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *     WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *     MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *     ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *     BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *     CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *     SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *     BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *     WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *     OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *     IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

public class FocusStateListener {
    private static final String TAG = "SnapCam_FocusStateListe";
    private CaptureUI mUI;

    public FocusStateListener(CaptureUI ui) {
        mUI = ui;
    }

    public void onFocusStatusUpdate(int focusState) {
        switch (focusState) {
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                Log.d(TAG, "CONTROL_AF_STATE_ACTIVE_SCAN onFocusStarted");
                mUI.onFocusStarted();
                break;
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                Log.d(TAG, "CONTROL_AF_STATE_FOCUSED_LOCKED onFocusSucceeded");
                mUI.onFocusSucceeded(false);
                break;
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                Log.d(TAG, "CONTROL_AF_STATE_NOT_FOCUSED_LOCKED onFocusFailed");
                mUI.onFocusFailed(false);
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                Log.d(TAG, "CONTROL_AF_STATE_PASSIVE_FOCUSED onFocusSucceeded");
                mUI.onFocusSucceeded(true);
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                Log.d(TAG, "CONTROL_AF_STATE_PASSIVE_SCAN onFocusStarted");
                mUI.onFocusStarted();
                break;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                Log.d(TAG, "CONTROL_AF_STATE_PASSIVE_UNFOCUSED onFocusFailed");
                mUI.onFocusFailed(true);
                break;
            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                Log.d(TAG, "CONTROL_AF_STATE_INACTIVE clearFocus");
                mUI.clearFocus();
                break;
        }
    }
}
