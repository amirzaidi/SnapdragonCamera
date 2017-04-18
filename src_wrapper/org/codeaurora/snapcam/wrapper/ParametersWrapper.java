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

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;

public class ParametersWrapper extends Wrapper{
    private final static String TAG = "ParametersWrapper";
    public final static String FACE_DETECTION_ON = getFieldValue(
            getField(Parameters.class, "FACE_DETECTION_ON"), "off");
    public static final String FACE_DETECTION_OFF = getFieldValue(
            getField(Parameters.class, "FACE_DETECTION_OFF"), "off");
    public static final String ZSL_OFF  = getFieldValue(
            getField(Parameters.class, "ZSL_OFF"), "off");
    public static final String TOUCH_AF_AEC_ON = getFieldValue(
            getField(Parameters.class, "TOUCH_AF_AEC_ON"), "touch-off");
    public static final String TOUCH_AF_AEC_OFF = getFieldValue(
            getField(Parameters.class, "TOUCH_AF_AEC_OFF"), "touch-off");
    public static final String DENOISE_OFF = getFieldValue(
            getField(Parameters.class, "DENOISE_OFF"), "denoise-off");
    public static final String DENOISE_ON = getFieldValue(
            getField(Parameters.class, "DENOISE_ON"), "denoise-off");
    public static final String ISO_AUTO = getFieldValue(
            getField(Parameters.class, "ISO_AUTO"), "auto");
    public static final String FOCUS_MODE_MANUAL_POSITION = getFieldValue(
            getField(Parameters.class, "FOCUS_MODE_MANUAL_POSITION"), "manual");

    private static Method method_isPowerModeSupported = null;
    public static boolean isPowerModeSupported(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no isPowerModeSupported");
            return false;
        }
        boolean supported = false;
        try {
            if (method_isPowerModeSupported == null) {
                method_isPowerModeSupported =
                        Parameters.class.getDeclaredMethod("isPowerModeSupported");
            }
            supported = (boolean)method_isPowerModeSupported.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supported;
    }

    private static Method method_setPowerMode = null;
    public static void setPowerMode(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setPowerMode");
            return;
        }
        try {
            if (method_setPowerMode == null) {
                method_setPowerMode =
                        Parameters.class.getDeclaredMethod("setPowerMode", String.class);
            }
            method_setPowerMode.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getPowerMode = null;
    public static String getPowerMode(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getPowerMode");
            return null;
        }
        String powerMode = null;
        try {
            if (method_getPowerMode == null) {
                method_getPowerMode = Parameters.class.getDeclaredMethod("getPowerMode");
            }
            powerMode = (String) method_getPowerMode.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return powerMode;
    }

    private static Method method_setCameraMode = null;
    public static void setCameraMode(Parameters parameters, int cameraMode) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setCameraMode");
            return;
        }
        try{
            if ( method_setCameraMode == null ){
                method_setCameraMode = Parameters.class.getDeclaredMethod("setCameraMode",
                        int.class);
            }
            method_setCameraMode.invoke(parameters, cameraMode);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedIsoValues = null;
    public static List<String> getSupportedIsoValues(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedIsoValues");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedIsoValues == null) {
                method_getSupportedIsoValues =
                        Parameters.class.getDeclaredMethod("getSupportedIsoValues");
            }
            supportedList = (List<String>) method_getSupportedIsoValues.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getISOValue = null;
    public static String getISOValue(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getISOValue");
            return null;
        }
        String isoValue = null;
        try{
            if ( method_getISOValue == null ){
                method_getISOValue = Parameters.class.getDeclaredMethod("getISOValue");
            }
            isoValue = (String)method_getISOValue.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return isoValue;
    }

    private static Method method_setISOValue = null;
    public static void setISOValue(Parameters parameters, String iso) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setISOValue");
            return;
        }
        try{
            if ( method_setISOValue == null ) {
                method_setISOValue = Parameters.class.getDeclaredMethod("setISOValue",
                        String.class);
            }
            method_setISOValue.invoke(parameters, iso);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedAutoexposure = null;
    public static List<String> getSupportedAutoexposure(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedAutoexposure");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedAutoexposure == null) {
                method_getSupportedAutoexposure =
                        Parameters.class.getDeclaredMethod("getSupportedAutoexposure");
            }
            supportedList = (List<String>)method_getSupportedAutoexposure.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getAutoExposure = null;
    public static String getAutoExposure(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getAutoExposure");
            return null;
        }
        String autoExposure = null;
        try {
            if (method_getAutoExposure == null) {
                method_getAutoExposure = Parameters.class.getDeclaredMethod("getAutoExposure");
            }
            autoExposure = (String)method_getAutoExposure.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return autoExposure;
    }

    private static Method method_setAutoExposure = null;
    public static void setAutoExposure(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setAutoExposure");
            return;
        }
        try{
            if ( method_setAutoExposure == null ){
                method_setAutoExposure = Parameters.class.getDeclaredMethod("setAutoExposure",
                        String.class);
            }
            method_setAutoExposure.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedTouchAfAec = null;
    public static List<String> getSupportedTouchAfAec(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedTouchAfAec");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedTouchAfAec == null) {
                method_getSupportedTouchAfAec =
                        Parameters.class.getDeclaredMethod("getSupportedTouchAfAec");
            }
            supportedList = (List<String>) method_getSupportedTouchAfAec.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getTouchAfAec = null;
    public static String getTouchAfAec(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getTouchAfAec");
            return null;
        }
        String touchAfAec = null;
        try {
            if (method_getTouchAfAec == null) {
                method_getTouchAfAec = Parameters.class.getDeclaredMethod("getTouchAfAec");
            }
            touchAfAec = (String)method_getTouchAfAec.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return touchAfAec;
    }

    private static Method method_setTouchAfAec = null;
    public static void setTouchAfAec(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setTouchAfAec");
            return;
        }
        try {
            if (method_setTouchAfAec == null) {
                method_setTouchAfAec = Parameters.class.getDeclaredMethod("setTouchAfAec",
                        String.class);
            }
            method_setTouchAfAec.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedSelectableZoneAf = null;
    public static List<String> getSupportedSelectableZoneAf(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedSelectableZoneAf");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedSelectableZoneAf == null) {
                method_getSupportedSelectableZoneAf =
                        Parameters.class.getDeclaredMethod("getSupportedSelectableZoneAf");
            }
            supportedList = (List<String>)method_getSupportedSelectableZoneAf.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_setSelectableZoneAf = null;
    public static void setSelectableZoneAf(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setSelectableZoneAf");
            return;
        }
        try{
            if ( method_setSelectableZoneAf == null ) {
                method_setSelectableZoneAf =
                        Parameters.class.getDeclaredMethod("setSelectableZoneAf", String.class);
            }
            method_setSelectableZoneAf.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedRedeyeReductionModes = null;
    public static List<String> getSupportedRedeyeReductionModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedRedeyeReductionModes");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedRedeyeReductionModes == null) {
                method_getSupportedRedeyeReductionModes =
                        Parameters.class.getDeclaredMethod("getSupportedRedeyeReductionModes");
            }
            supportedList = (List<String>)method_getSupportedRedeyeReductionModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_setRedeyeReductionMode = null;
    public static void setRedeyeReductionMode(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setRedeyeReductionMode");
            return;
        }
        try {
            if (method_setRedeyeReductionMode == null) {
                method_setRedeyeReductionMode = Parameters.class.getDeclaredMethod(
                        "setRedeyeReductionMode", String.class);
            }
            method_setRedeyeReductionMode.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedDenoiseModes = null;
    public static List<String> getSupportedDenoiseModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedDenoiseModes");
            return null;
        }
        List<String> supportedList = null;
        try {
            if (method_getSupportedDenoiseModes == null) {
                method_getSupportedDenoiseModes =
                        Parameters.class.getDeclaredMethod("getSupportedDenoiseModes");
            }
            supportedList = (List<String>) method_getSupportedDenoiseModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_setDenoise = null;
    public static void setDenoise(Parameters parameters,String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setDenoise");
            return;
        }
        try{
            if ( method_setDenoise == null ) {
                method_setDenoise = Parameters.class.getDeclaredMethod("setDenoise",
                        String.class);
            }
            method_setDenoise.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedVideoHDRModes = null;
    public static List<String> getSupportedVideoHDRModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedVideoHDRModes");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedVideoHDRModes == null ){
                method_getSupportedVideoHDRModes =
                        Parameters.class.getDeclaredMethod("getSupportedVideoHDRModes");
            }
            supportedList = (List<String>)method_getSupportedVideoHDRModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getVideoHDRMode = null;
    public static String getVideoHDRMode(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getVideoHDRMode");
            return null;
        }
        String hdrMode = null;
        try{
            if ( method_getVideoHDRMode == null ){
                method_getVideoHDRMode = Parameters.class.getDeclaredMethod("getVideoHDRMode");
            }
            hdrMode = (String)method_getVideoHDRMode.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return hdrMode;
    }

    private static Method method_setVideoHDRMode = null;
    public static void setVideoHDRMode(Parameters parameters, String videohdr) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setVideoHDRMode");
            return;
        }
        try{
            if ( method_setVideoHDRMode == null ){
                method_setVideoHDRMode = Parameters.class.getDeclaredMethod("setVideoHDRMode",
                        String.class);
            }
            method_setVideoHDRMode.invoke(parameters, videohdr);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedHistogramModes = null;
    public static List<String> getSupportedHistogramModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedHistogramModes");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedHistogramModes == null ){
                method_getSupportedHistogramModes =
                        Parameters.class.getDeclaredMethod("getSupportedHistogramModes");
            }
            supportedList = (List<String>)method_getSupportedHistogramModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getSupportedHfrSizes = null;
    public static List<Size> getSupportedHfrSizes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedHfrSizes");
            return null;
        }
        List<Size> supportedList = null;
        try{
            if ( method_getSupportedHfrSizes == null ){
                method_getSupportedHfrSizes =
                        Parameters.class.getDeclaredMethod("getSupportedHfrSizes");
            }
            supportedList = (List<Size>)method_getSupportedHfrSizes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getSupportedVideoHighFrameRateModes = null;
    public static List<String> getSupportedVideoHighFrameRateModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedVideoHighFrameRateModes");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedVideoHighFrameRateModes == null ){
                method_getSupportedVideoHighFrameRateModes =
                    Parameters.class.getDeclaredMethod("getSupportedVideoHighFrameRateModes");
            }
            supportedList =
                    (List<String>)method_getSupportedVideoHighFrameRateModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getVideoHighFrameRate = null;
    public static String getVideoHighFrameRate(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getVideoHighFrameRate");
            return null;
        }
        String hfr = null;
        try{
            if ( method_getVideoHighFrameRate == null ){
                method_getVideoHighFrameRate =
                        Parameters.class.getDeclaredMethod("getVideoHighFrameRate");
            }
           hfr = (String)method_getVideoHighFrameRate.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return hfr;
    }

    private static Method method_setVideoHighFrameRate = null;
    public static void setVideoHighFrameRate(Parameters parameters, String hfr) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setVideoHighFrameRate");
            return;
        }
        try{
            if ( method_setVideoHighFrameRate == null ){
                method_setVideoHighFrameRate = Parameters.class.getDeclaredMethod(
                        "setVideoHighFrameRate", String.class);
            }
            method_setVideoHighFrameRate.invoke(parameters, hfr);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedVideoRotationValues = null;
    public static List<String> getSupportedVideoRotationValues(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedVideoRotationValues");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedVideoRotationValues == null ){
                method_getSupportedVideoRotationValues =
                        Parameters.class.getDeclaredMethod("getSupportedVideoRotationValues");
            }
            supportedList = (List<String>)method_getSupportedVideoRotationValues.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_setVideoRotation = null;
    public static void setVideoRotation(Parameters parameters, String value) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setVideoRotation");
            return;
        }
        try{
            if ( method_setVideoRotation == null ) {
                method_setVideoRotation = Parameters.class.getDeclaredMethod(
                        "setVideoRotation", String.class);
            }
            method_setVideoRotation.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_setFaceDetectionMode = null;
    public static void setFaceDetectionMode(Parameters parameters, String value){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setFaceDetectionMode");
            return;
        }
        try{
            if ( method_setFaceDetectionMode == null ){
                method_setFaceDetectionMode = Parameters.class.getDeclaredMethod(
                        "setFaceDetectionMode", String.class);
            }
            method_setFaceDetectionMode.invoke(parameters, value);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSupportedFaceDetectionModes = null;
    public static List<String> getSupportedFaceDetectionModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedFaceDetectionModes");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedFaceDetectionModes == null ) {
                method_getSupportedFaceDetectionModes =
                        Parameters.class.getDeclaredMethod("getSupportedFaceDetectionModes");
            }
            supportedList = (List<String>)method_getSupportedFaceDetectionModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_getSupportedZSLModes = null;
    public static List<String> getSupportedZSLModes(Parameters parameters) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSupportedZSLModes");
            return null;
        }
        List<String> supportedList = null;
        try{
            if ( method_getSupportedZSLModes == null ) {
                method_getSupportedZSLModes =
                        Parameters.class.getDeclaredMethod("getSupportedZSLModes");
            }
            supportedList = (List<String>)method_getSupportedZSLModes.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return supportedList;
    }

    private static Method method_setZSLMode = null;
    public static void setZSLMode(Parameters parameters, String zsl) {
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setZSLMode");
            return;
        }
        try {
            if ( method_setZSLMode == null ) {
                method_setZSLMode = Parameters.class.getDeclaredMethod("setZSLMode",
                        String.class);
            }
            method_setZSLMode.invoke(parameters, zsl);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getSharpness = null;
    public static int getSharpness(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSharpness");
            return -1;
        }
        int sharpness = -1;
        try{
            if ( method_getSharpness == null ) {
                method_getSharpness = Parameters.class.getDeclaredMethod("getSharpness");
            }
            sharpness = (int)method_getSharpness.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return sharpness;
    }

    private static Method method_setSharpness = null;
    public static void setSharpness(Parameters parameters, int sharpness){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setSharpness");
            return;
        }
        try{
            if ( method_setSharpness == null ) {
                method_setSharpness =
                        Parameters.class.getDeclaredMethod("setSharpness", int.class);
            }
            method_setSharpness.invoke(parameters, sharpness);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getMaxSharpness = null;
    public static int getMaxSharpness(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getMaxSharpness");
            return -1;
        }
        int maxSharpness = -1;
        try{
            if ( method_getMaxSharpness == null ) {
                method_getMaxSharpness = Parameters.class.getDeclaredMethod("getMaxSharpness");
            }
            maxSharpness = (int)method_getMaxSharpness.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return maxSharpness;
    }

    private static Method method_getSaturation = null;
    public static int getSaturation(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getSaturation");
            return -1;
        }
        int saturation = -1;
        try{
            if ( method_getSaturation == null ) {
                method_getSaturation = Parameters.class.getDeclaredMethod("getSaturation");
            }
            saturation = (int)method_getSaturation.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return saturation;
    }

    private static Method method_setSaturation = null;
    public static void setSaturation(Parameters parameters, int saturation){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setSaturation");
            return ;
        }
        try{
            if ( method_setSaturation == null ) {
                method_setSaturation =
                        Parameters.class.getDeclaredMethod("setSaturation", int.class);
            }
            method_setSaturation.invoke(parameters, saturation);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getMaxSaturation = null;
    public static int getMaxSaturation(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getMaxSaturation");
            return -1;
        }
        int maxSaturation = -1;
        try{
            if ( method_getMaxSaturation == null ) {
                method_getMaxSaturation = Parameters.class.getDeclaredMethod("getMaxSaturation");
            }
            maxSaturation = (int)method_getMaxSaturation.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return maxSaturation;
    }

    private static Method method_getContrast = null;
    public static int getContrast(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getContrast");
            return -1;
        }
        int contrast = -1;
        try{
            if ( method_getContrast == null ) {
                method_getContrast = Parameters.class.getDeclaredMethod("getContrast");
            }
            contrast = (int)method_getContrast.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return contrast;
    }

    private static Method method_setContrast = null;
    public static void setContrast(Parameters parameters, int contrast){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no setContrast");
            return;
        }
        try{
            if ( method_setContrast == null ) {
                method_setContrast =
                        Parameters.class.getDeclaredMethod("setContrast", int.class);
            }
            method_setContrast.invoke(parameters, contrast);
        }catch(Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_getMaxContrast = null;
    public static int getMaxContrast(Parameters parameters){
        if ( DEBUG ){
            Log.e(TAG, "Debug:" + Parameters.class + " no getMaxContrast");
            return -1;
        }
        int maxContrast = -1;
        try{
            if ( method_getMaxContrast == null ) {
                method_getMaxContrast = Parameters.class.getDeclaredMethod("getMaxContrast");
            }
            maxContrast = (int)method_getMaxContrast.invoke(parameters);
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return maxContrast;
    }
}
