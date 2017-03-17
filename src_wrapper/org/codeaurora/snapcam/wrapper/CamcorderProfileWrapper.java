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

package org.codeaurora.snapcam.wrapper;

import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.util.Log;

public class CamcorderProfileWrapper extends Wrapper{
    public static final int QUALITY_VGA = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_VGA"), -1);
    public final static int QUALITY_4KDCI = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_4KDCI"), -1);
    public final static int QUALITY_TIME_LAPSE_VGA = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_TIME_LAPSE_VGA"), -1);
    public static final int QUALITY_TIME_LAPSE_4KDCI = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_TIME_LAPSE_4KDCI"), -1);
    public final static int QUALITY_HIGH_SPEED_CIF = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_HIGH_SPEED_CIF"), -1);
        public static final int QUALITY_HIGH_SPEED_VGA = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_HIGH_SPEED_VGA"), -1);
    public final static int QUALITY_HIGH_SPEED_4KDCI = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_HIGH_SPEED_4KDCI"), -1);
    public static final int QUALITY_QHD = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_QHD"), -1);
    public final static int QUALITY_2k = getFieldValue(
            getField(CamcorderProfile.class, "QUALITY_2k"), -1);
}
