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

import android.hardware.camera2.CameraMetadata;
import android.media.MediaRecorder;

import java.util.HashMap;
import java.util.Map;

public class SettingTranslation {
    public static final int NOT_FOUND = -1;
    private static final TwoWayMap VIDEO_ENCODER_TABLE = new TwoWayMap();
    private static final TwoWayMap AUDIO_ENCODER_TABLE = new TwoWayMap();
    private static final TwoWayMap NOISE_REDUCTION_TABLE = new TwoWayMap();

    static {
        VIDEO_ENCODER_TABLE.put("default", MediaRecorder.VideoEncoder.DEFAULT);
        VIDEO_ENCODER_TABLE.put("h263", MediaRecorder.VideoEncoder.H263);
        VIDEO_ENCODER_TABLE.put("h264", MediaRecorder.VideoEncoder.H264);
        int h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT);
        if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
            h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                    "H265", null, MediaRecorder.VideoEncoder.DEFAULT);
        }
        VIDEO_ENCODER_TABLE.put("h265", h265);
        VIDEO_ENCODER_TABLE.put("mpeg-4-sp", MediaRecorder.VideoEncoder.MPEG_4_SP);
        VIDEO_ENCODER_TABLE.put("vp8", MediaRecorder.VideoEncoder.VP8);

        AUDIO_ENCODER_TABLE.put("aac", MediaRecorder.AudioEncoder.AAC);
        AUDIO_ENCODER_TABLE.put("aac-eld", MediaRecorder.AudioEncoder.AAC_ELD);
        AUDIO_ENCODER_TABLE.put("amr-nb", MediaRecorder.AudioEncoder.AMR_NB);
        AUDIO_ENCODER_TABLE.put("amr-wb", MediaRecorder.AudioEncoder.AMR_WB);
        AUDIO_ENCODER_TABLE.put("default", MediaRecorder.AudioEncoder.DEFAULT);
        AUDIO_ENCODER_TABLE.put("he-aac", MediaRecorder.AudioEncoder.HE_AAC);
        AUDIO_ENCODER_TABLE.put("vorbis", MediaRecorder.AudioEncoder.VORBIS);

        NOISE_REDUCTION_TABLE.put("off", CameraMetadata.NOISE_REDUCTION_MODE_OFF);
        NOISE_REDUCTION_TABLE.put("fast", CameraMetadata.NOISE_REDUCTION_MODE_FAST);
        NOISE_REDUCTION_TABLE.put("high-quality", CameraMetadata
                .NOISE_REDUCTION_MODE_HIGH_QUALITY);
        NOISE_REDUCTION_TABLE.put("minimal", CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL);
        NOISE_REDUCTION_TABLE.put("zero-shutter-lag", CameraMetadata
                .NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
    }

    public static int getVideoEncoder(String key) {
        return VIDEO_ENCODER_TABLE.get(key);
    }

    public static String getVideoEncoder(int key) {
        return VIDEO_ENCODER_TABLE.get(key);
    }

    public static int getAudioEncoder(String key) {
        return AUDIO_ENCODER_TABLE.get(key);
    }

    public static String getAudioEncoder(int key) {
        return AUDIO_ENCODER_TABLE.get(key);
    }

    public static int getNoiseReduction(String key) {
        return NOISE_REDUCTION_TABLE.get(key);
    }

    public static String getNoiseReduction(int key) {
        return NOISE_REDUCTION_TABLE.get(key);
    }

    private static class TwoWayMap {
        private Map<String, Integer> strToInt = new HashMap<>();
        private Map<Integer, String> intToStr = new HashMap<>();

        public void put(String key, int value) {
            strToInt.put(key, value);
            intToStr.put(value, key);
        }

        public int get(String key) {
            Integer res =  strToInt.get(key);
            if (res != null) return res;
            else return NOT_FOUND;
        }

        public String get(int key) {
            return intToStr.get(key);
        }
    }
}
