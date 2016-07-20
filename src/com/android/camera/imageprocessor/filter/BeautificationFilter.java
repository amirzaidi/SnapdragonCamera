/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.camera.imageprocessor.filter;

import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.Face;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import com.android.camera.CaptureModule;
import com.android.camera.ui.FilmstripBottomControls;

import java.nio.ByteBuffer;
import java.util.List;

public class BeautificationFilter implements ImageFilter {

    int mWidth;
    int mHeight;
    int mStrideY;
    int mStrideVU;
    private CaptureModule mModule;
    private static boolean DEBUG = false;
    private static String TAG = "BeautificationFilter";
    private static boolean mIsSupported = false;

    public BeautificationFilter(CaptureModule module) {
        mModule = module;
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        return null;
    }

    @Override
    public String getStringName() {
        return "BeautificationFilter";
    }

    @Override
    public int getNumRequiredImage() {
        return 0;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        mWidth = width;
        mHeight = height;
        mStrideY = strideY;
        mStrideVU = strideVU;
    }

    @Override
    public void deinit() {

    }

    @Override
    public void addImage(ByteBuffer bY, ByteBuffer bVU, int imageNum, Object isPreview) {
        Rect back = mModule.getCameraRegion();
        Face[] faces;
        if(((Boolean)isPreview).booleanValue()) {
            faces = mModule.getPreviewFaces();
        } else {
            faces = mModule.getStickyFaces();
        }
        float widthRatio = (float)mWidth/back.width();
        float heightRatio = (float)mHeight/back.height();
        if(faces == null || faces.length == 0)
            return;
        Rect rect = faces[0].getBounds();
        int value = nativeBeautificationProcess(bY, bVU, mWidth, mHeight, mStrideY,
                (int)(rect.left*widthRatio), (int)(rect.top*heightRatio),
                (int)(rect.right*widthRatio), (int)(rect.bottom*heightRatio));
        if(DEBUG && value < 0) {
            if(value == -1) {
                Log.d(TAG, "library initialization is failed.");
            } else if(value == -2) {
                Log.d(TAG, "No face is recognized");
            }
        }
    }

    @Override
    public ResultImage processImage() {
        return null;
    }

    @Override
    public boolean isSupported() {
        return mIsSupported;
    }

    public static boolean isSupportedStatic() {
        return mIsSupported;
    }

    @Override
    public boolean isFrameListener() {
        return false;
    }

    @Override
    public boolean isManualMode() {
        return false;
    }

    @Override
    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback, Handler handler) {

    }

    private native int nativeBeautificationProcess(ByteBuffer yB, ByteBuffer vuB,
                        int width, int height, int stride, int fleft, int ftop, int fright, int fbottom);

    static {
        try {
            System.loadLibrary("jni_makeup");
            mIsSupported = true;
        }catch(UnsatisfiedLinkError e) {
            mIsSupported = false;
        }
    }
}
