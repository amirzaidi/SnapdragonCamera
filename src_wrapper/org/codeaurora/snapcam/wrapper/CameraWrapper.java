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

import java.io.IOException;
import java.lang.reflect.Method;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.util.Log;
import android.view.SurfaceHolder;

import android.hardware.Camera.CameraMetaDataCallback;
import android.hardware.Camera.CameraDataCallback;

public class CameraWrapper extends Wrapper{

    private static Method method_setMetadataCb = null;
    public static final void setMetadataCb(Camera camera, CameraMetaDataCallback cb){
        if ( DEBUG ){
            Log.e(TAG, "" + Camera.class + " no setMetadataCb");
            return;
        }
        try{
            if ( method_setMetadataCb == null ){
                method_setMetadataCb = Camera.class.getMethod("setMetadataCb",
                        CameraMetaDataCallback.class);
            }
            method_setMetadataCb.invoke(camera, cb);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_setHistogramMode = null;
    public static final void setHistogramMode(Camera camera, Camera.CameraDataCallback cb) {
        if ( DEBUG ){
            Log.e(TAG, "" + Camera.class + " no setHistogramMode");
            return;
        }
        try{
            if ( method_setHistogramMode == null ){
                method_setHistogramMode = Camera.class.getMethod("setHistogramMode",
                        android.hardware.Camera.CameraDataCallback.class);
            }
            method_setHistogramMode.invoke(camera, cb);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_sendHistogramData = null;
    public static final void sendHistogramData(Camera camera){
        if ( DEBUG ){
            Log.e(TAG, "" + Camera.class + " no sendHistogramData");
            return;
        }
        try{
            if ( method_sendHistogramData == null ){
                method_sendHistogramData = Camera.class.getMethod("sendHistogramData");
            }
            method_sendHistogramData.invoke(camera);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

    private static Method method_setLongshot = null;
    public static final void setLongshot(Camera camera, boolean enable){
        if ( DEBUG ){
            Log.e(TAG, "" + Camera.class + " no setLongshot");
            return;
        }
        try {
            if (method_setLongshot == null) {
                method_setLongshot =
                        Camera.class.getDeclaredMethod("setLongshot", boolean.class);
            }
            method_setLongshot.invoke(camera, enable);
        }catch (Exception exception){
            exception.printStackTrace();
        }
    }

}
