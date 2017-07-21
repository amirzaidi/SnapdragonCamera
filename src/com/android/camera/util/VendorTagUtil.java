/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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

import android.hardware.camera2.CaptureRequest;
import android.util.Log;

public class VendorTagUtil {
    private static final String TAG = "VendorTagUtil";

    private static CaptureRequest.Key<Integer> CdsModeKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.CDS.cds_mode",
                    Integer.class);
    private static CaptureRequest.Key<Byte> JpegCropEnableKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.enable",
                    Byte.class);
    private static CaptureRequest.Key<int[]> JpegCropRectKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.rect",
                    int[].class);
    private static CaptureRequest.Key<int[]> JpegRoiRectKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.roi",
                    int[].class);
    private static CaptureRequest.Key<Integer> SELECT_PRIORITY =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.iso_exp_priority.select_priority",
                    Integer.class);
    private static CaptureRequest.Key<Long> ISO_EXP =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.iso_exp_priority.use_iso_exp_priority",
                    Long.class);


    private static boolean isSupported(CaptureRequest.Builder builder,
                                       CaptureRequest.Key<?> key) {
        boolean supported = true;
        try {
            builder.get(key);
        }catch(IllegalArgumentException exception){
            supported = false;
            Log.d(TAG, "vendor tag " + key.getName() + " is not supported");
        }
        if ( supported ) {
            Log.d(TAG, "vendor tag " + key.getName() + " is supported");
        }
        return supported;
    }

    // value=0:OFF
    // value=1:ON
    // value=2:AUTO
    public static void setCdsMode(CaptureRequest.Builder builder, Integer value) {
        if ( isCdsModeSupported(builder) ) {
            builder.set(CdsModeKey, value);
        }
    }

    private static boolean isCdsModeSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, CdsModeKey);
    }

    public static void setJpegCropEnable(CaptureRequest.Builder builder, Byte value) {
        if ( isJpegCropEnableSupported(builder) ) {
            builder.set(JpegCropEnableKey, value);
        }
    }

    private static boolean isJpegCropEnableSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegCropEnableKey);
    }

    public static void setJpegCropRect(CaptureRequest.Builder builder, int[] value) {
        if ( isJpegCropRectSupported(builder) ) {
            builder.set(JpegCropRectKey, value);
        }
    }

    private static boolean isJpegCropRectSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegCropRectKey);
    }

    public static void setJpegRoiRect(CaptureRequest.Builder builder, int[] value) {
        if ( isJpegRoiRectSupported(builder) ) {
            builder.set(JpegRoiRectKey, value);
        }
    }

    private static boolean isJpegRoiRectSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegRoiRectKey);
    }

    public static void setIsoExpPrioritySelectPriority(CaptureRequest.Builder builder,
                                                       Integer value) {
        if ( isIsoExpPrioritySelectPrioritySupported(builder) ) {
            builder.set(SELECT_PRIORITY, value);
        }
    }
    private static boolean isIsoExpPrioritySelectPrioritySupported(CaptureRequest.Builder builder) {
        return isSupported(builder, SELECT_PRIORITY);
    }

    public static void setIsoExpPriority(CaptureRequest.Builder builder,Long value) {
        if ( isIsoExpPrioritySupported(builder) ) {
            builder.set(ISO_EXP, value);
        }
    }
    private static boolean isIsoExpPrioritySupported(CaptureRequest.Builder builder) {
        return isSupported(builder, ISO_EXP);
    }

}
