/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.camera.util;

import android.graphics.Point;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

public class PersistUtil {

    public static final int CAMERA2_DEBUG_DUMP_IMAGE = 1;
    public static final int CAMERA2_DEBUG_DUMP_LOG = 2;
    public static final int CAMERA2_DEBUG_DUMP_ALL = 100;

    private static final int CAMERA_SENSOR_HORIZONTAL_ALIGNED = 0;
    private static final int CAMERA_SENSOR_VERTICAL_ALIGNED = 1;

    private static final int PERSIST_MEMORY_LIMIT =
            SystemProperties.getInt("persist.camera.perf.memlimit", 60);
    private static final boolean PERSIST_SKIP_MEMORY_CHECK =
            SystemProperties.getBoolean("persist.camera.perf.skip_memck", false);
    private static final int PERSIST_LONGSHOT_SHOT_LIMIT =
            SystemProperties.getInt("persist.camera.longshot.shotnum", 50);
    private static final String PERSIST_CAMERA_PREVIEW_SIZE =
            SystemProperties.get("persist.camera.preview.size", "");
    private static final boolean PERSIST_CAMERA_CAMERA2 =
            SystemProperties.getBoolean("persist.camera.camera2", false);
    private static final boolean PERSIST_CAMERA_ZSL =
            SystemProperties.getBoolean("persist.camera.zsl.disabled", false);
    private static final int PERSIST_CAMERA_CANCEL_TOUCHFOCUS_DELAY =
            SystemProperties.getInt("persist.camera.focus_delay", 5000);
    private static final int PERSIST_CAMERA_DEBUG =
            SystemProperties.getInt("persist.camera.debug", 0);
    private static final String PERSIST_CAMERA_STILLMORE_BRCOLR =
            SystemProperties.get("persist.camera.stm_brcolor", "0.5");
    private static final String PERSIST_CAMERA_STILLMORE_BRINTENSITY =
            SystemProperties.get("persist.camera.stm_brintensity", "0.6");
    private static final String PERSIST_CAMERA_STILLMORE_SMOOTHINGINTENSITY =
            SystemProperties.get("persist.camera.stm_smooth", "0");
    private static final int PERSIST_CAMERA_STILLMORE_NUM_REQUIRED_IMAGE =
            SystemProperties.getInt("persist.camera.stm_img_nums", 5);
    private static final String PERSIST_CAMERA_CS_BRINTENSITY_KEY =
            SystemProperties.get("persist.camera.sensor.brinten", "0.0");
    private static final String PERSIST_CAMERA_CS_SMOOTH_KEY =
            SystemProperties.get("persist.camera.sensor.smooth", "0.5");
    private static final int PERSIST_CAMERA_SENSOR_ALIGN_KEY =
            SystemProperties.getInt("persist.camera.sensor.align",
                    CAMERA_SENSOR_HORIZONTAL_ALIGNED);

    public static int getMemoryLimit() {
        return PERSIST_MEMORY_LIMIT;
    }

    public static boolean getSkipMemoryCheck() {
        return PERSIST_SKIP_MEMORY_CHECK;
    }

    public static int getLongshotShotLimit() {
        return PERSIST_LONGSHOT_SHOT_LIMIT;
    }

    public static Point getCameraPreviewSize() {
        Point result = null;
        if (PERSIST_CAMERA_PREVIEW_SIZE != null) {
            String[] sourceStrArray = PERSIST_CAMERA_PREVIEW_SIZE.split("x");
            if (sourceStrArray != null && sourceStrArray.length >= 2) {
                result = new Point();
                result.x = Integer.parseInt(sourceStrArray[0]);
                result.y = Integer.parseInt(sourceStrArray[1]);
            }
        }
        return result;
    }

    public static boolean getCamera2Mode() {
        return PERSIST_CAMERA_CAMERA2;
    }

    public static boolean getCameraZSLDisabled() {
        return PERSIST_CAMERA_ZSL;
    }

    public static int getCamera2Debug() {
        return PERSIST_CAMERA_DEBUG;
    }

    public static float getStillmoreBrColor(){
        float brColor = Float.parseFloat(PERSIST_CAMERA_STILLMORE_BRCOLR);
        return brColor = (brColor < 0 || brColor > 1) ? 0.5f : brColor;
    }

    public static float getStillmoreBrIntensity(){
        float brIntensity = Float.parseFloat(PERSIST_CAMERA_STILLMORE_BRINTENSITY);
        return brIntensity = (brIntensity < 0 || brIntensity > 1) ? 0.6f : brIntensity;
    }

    public static float getStillmoreSmoothingIntensity(){
        float smoothingIntensity = Float.parseFloat(PERSIST_CAMERA_STILLMORE_SMOOTHINGINTENSITY);
        return smoothingIntensity = (smoothingIntensity < 0 || smoothingIntensity > 1) ?
                0f : smoothingIntensity;
    }

    public static int getStillmoreNumRequiredImages() {
        return (PERSIST_CAMERA_STILLMORE_NUM_REQUIRED_IMAGE < 3 ||
                PERSIST_CAMERA_STILLMORE_NUM_REQUIRED_IMAGE > 5) ?
                5 : PERSIST_CAMERA_STILLMORE_NUM_REQUIRED_IMAGE;
    }

    public static int getCancelTouchFocusDelay() {
        return PERSIST_CAMERA_CANCEL_TOUCHFOCUS_DELAY;
    }

    public static float getDualCameraBrIntensity() {
        return Float.parseFloat(PERSIST_CAMERA_CS_BRINTENSITY_KEY);
    }

    public static float getDualCameraSmoothingIntensity() {
        return Float.parseFloat(PERSIST_CAMERA_CS_SMOOTH_KEY);
    }

    public static boolean getDualCameraSensorAlign() {
        return PERSIST_CAMERA_SENSOR_ALIGN_KEY == CAMERA_SENSOR_VERTICAL_ALIGNED;
    }
}
