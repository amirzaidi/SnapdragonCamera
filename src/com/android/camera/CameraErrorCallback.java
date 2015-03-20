/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera;

import android.util.Log;
import android.widget.Toast;
import com.android.camera.ui.RotateTextToast;
import org.codeaurora.snapcam.R;

public class CameraErrorCallback
        implements android.hardware.Camera.ErrorCallback {
    private static final String TAG = "CameraErrorCallback";
    public CameraActivity mActivity = null;
    //custom error code for thermal shutdown. This should be in sync
    //with HAL.
    private static final int THERMAL_SHUTDOWN = 50;

    public void setActivity(CameraActivity activity) {
        mActivity = activity;
    }

    @Override
    public void onError(int error, android.hardware.Camera camera) {
        Log.e(TAG, "Got camera error callback. error=" + error);
        // We are not sure about the current state of the app (in preview or
        // snapshot or recording). Closing the app is better than creating a
        // new Camera object.
        if (mActivity != null) {
            final int resId;
            switch (error) {
                case android.hardware.Camera.CAMERA_ERROR_SERVER_DIED:
                    resId = R.string.camera_server_died;
                    break;
                case THERMAL_SHUTDOWN:
                    resId = R.string.camera_thermal_shutdown;
                    break;
                case android.hardware.Camera.CAMERA_ERROR_UNKNOWN:
                default:
                    resId = R.string.camera_unknown_error;
                    break;
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                     RotateTextToast.makeText(mActivity, resId, Toast.LENGTH_LONG).show();
                     mActivity.finish();
                }
            });
        } else {
            throw new RuntimeException("Unknown error");
        }
    }
}
