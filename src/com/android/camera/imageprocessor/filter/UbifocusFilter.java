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

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.Handler;
import android.util.Log;
import android.util.Range;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.imageprocessor.PostProcessor;
import com.android.camera.util.CameraUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class UbifocusFilter implements ImageFilter {
    public static final int NUM_REQUIRED_IMAGE = 5;
    private int mWidth;
    private int mHeight;
    private int mStrideY;
    private int mStrideVU;
    private static String TAG = "UbifocusFilter";
    private static final boolean DEBUG = false;
    private static final int FOCUS_ADJUST_TIME_OUT = 400;
    private static final int META_BYTES_SIZE = 25;
    private int temp;
    private static boolean mIsSupported = true;
    private ByteBuffer mOutBuf;
    private CaptureModule mModule;
    private CameraActivity mActivity;
    private int mOrientation = 0;
    private float mMinFocusDistance = -1f;
    private Object mClosingLock = new Object();
    private PostProcessor mPostProcessor;
    final String[] NAMES = {"00.jpg", "01.jpg", "02.jpg", "03.jpg",
            "04.jpg", "DepthMapImage.y", "AllFocusImage.jpg"};

    private int mSavedCount = 0;

    private static void Log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public UbifocusFilter(CaptureModule module, CameraActivity activity, PostProcessor processor) {
        mModule = module;
        mActivity = activity;
        mPostProcessor = processor;
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        return null;
    }

    @Override
    public String getStringName() {
        return "UbifocusFilter";
    }

    @Override
    public int getNumRequiredImage() {
        return NUM_REQUIRED_IMAGE;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        Log("init");
        mWidth = width/2*2;
        mHeight = height/2*2;
        mStrideY = strideY/2*2;
        mStrideVU = strideVU/2*2;
        mOutBuf = ByteBuffer.allocate(mStrideY * mHeight * 3 / 2);
        Log("width: "+mWidth+" height: "+mHeight+" strideY: "+mStrideY+" strideVU: "+mStrideVU);
        nativeInit(mWidth, mHeight, mStrideY, mStrideVU, NUM_REQUIRED_IMAGE);
    }

    @Override
    public void deinit() {
        Log("deinit");
        synchronized (mClosingLock) {
            mOutBuf = null;
            nativeDeinit();
        }
    }

    @Override
    public void addImage(final ByteBuffer bY, final ByteBuffer bVU, final int imageNum, Object param) {
        Log("addImage");
        if(imageNum == 0) {
            mModule.setRefocusLastTaken(false);
            mOrientation = CameraUtil.getJpegRotation(mModule.getMainCameraId(), mModule.getDisplayOrientation());
            mSavedCount = 0;
        }
        int yActualSize = bY.remaining();
        int vuActualSize = bVU.remaining();
        if(nativeAddImage(bY, bVU, yActualSize, vuActualSize, imageNum) < 0) {
            Log.e(TAG, "Fail to add image");
        }
        new Thread() {
            public void run() {
                synchronized (mClosingLock) {
                    if(mOutBuf == null) {
                        return;
                    }
                    saveToPrivateFile(imageNum, nv21ToJpeg(bY, bVU, new Rect(0, 0, mWidth, mHeight), mOrientation, imageNum));
                    mSavedCount++;
                }
            }
        }.start();
    }

    @Override
    public ResultImage processImage() {
        Log("processImage ");
        int[] roi = new int[4];
        int[] depthMapSize = new int[2];
        int status = nativeProcessImage(mOutBuf.array(), roi, depthMapSize);
        if(status < 0) { //In failure case, library will return the first image as it is.
            Log.w(TAG, "Fail to process the "+getStringName());
        } else {
            byte[] depthMapBuf = new byte[depthMapSize[0] * depthMapSize[1] + META_BYTES_SIZE];
            nativeGetDepthMap(depthMapBuf, depthMapSize[0], depthMapSize[1]);
            saveToPrivateFile(NAMES.length - 2, depthMapBuf);
            saveToPrivateFile(NAMES.length - 1, nv21ToJpeg(mOutBuf, null, new Rect(roi[0], roi[1], roi[0] + roi[2], roi[1] + roi[3]), mOrientation, 0));
            mModule.setRefocusLastTaken(true);
        }
        while(mSavedCount < NUM_REQUIRED_IMAGE) {
            try {
                Thread.sleep(1);
            } catch (Exception e) {
            }
        }
        ResultImage result = new ResultImage(mOutBuf, new Rect(roi[0], roi[1], roi[0]+roi[2], roi[1] + roi[3]), mWidth, mHeight, mStrideY);
        Log("processImage done");
        return result;
    }

    @Override
    public boolean isSupported() {
        return mIsSupported;
    }

    @Override
    public boolean isFrameListener() {
        return false;
    }

    @Override
    public boolean isManualMode() {
        return true;
    }

    @Override
    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback, Handler handler) throws CameraAccessException {
        if (mMinFocusDistance == -1f) {
            mMinFocusDistance = mModule.getMainCameraCharacteristics().get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        }
        float step = mMinFocusDistance / NUM_REQUIRED_IMAGE;
        for(int i=0; i < NUM_REQUIRED_IMAGE; i++) {
            float value = (i * step);
            mModule.setAFModeToPreview(mModule.getMainCameraId(), CaptureRequest.CONTROL_AF_MODE_OFF);
            mModule.setFocusDistanceToPreview(mModule.getMainCameraId(), value);
            Log("Request:  " + value);
            float focusDistance;
            try {
                int count = FOCUS_ADJUST_TIME_OUT;
                do {
                    Thread.sleep(5);
                    count -= 5;
                    if(count <= 0) {
                        break;
                    }
                    focusDistance = mModule.getPreviewCaptureResult().get(CaptureResult.LENS_FOCUS_DISTANCE);
                    Log("Taken focus value :"+focusDistance);
                } while(Math.abs(focusDistance - value) >= 1f);
            } catch (InterruptedException e) {
            } catch (NullPointerException e) {
            }
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, value);
            captureSession.capture(builder.build(), callback, handler);
        }
    }

    public static boolean isSupportedStatic() {
        return mIsSupported;
    }

    private byte[] nv21ToJpeg(ByteBuffer bY, ByteBuffer bVU, Rect roi, int orientation, int imageIndex) {
        ByteBuffer buf =  ByteBuffer.allocate(mStrideY*mHeight*3/2);
        buf.put(bY);
        bY.rewind();
        if(bVU != null) {
            buf.put(bVU);
            bVU.rewind();
        }
        BitmapOutputStream bos = new BitmapOutputStream(1024);
        YuvImage im = new YuvImage(buf.array(), ImageFormat.NV21,
                mWidth, mHeight, new int[]{mStrideY, mStrideVU});
        im.compressToJpeg(roi, mPostProcessor.getJpegQualityValue(), bos);
        byte[] bytes = bos.getArray();
        bytes = PostProcessor.addExifTags(bytes, orientation, mPostProcessor.waitForMetaData(imageIndex));
        return bytes;
    }

    private class BitmapOutputStream extends ByteArrayOutputStream {
        public BitmapOutputStream(int size) {
            super(size);
        }

        public byte[] getArray() {
            return buf;
        }
    }

    private void saveToPrivateFile(final int index, final byte[] bytes) {
        String filesPath = mActivity.getFilesDir()+"/Ubifocus";
        File file = new File(filesPath);
        if(!file.exists()) {
            file.mkdir();
        }
        file = new File(filesPath+"/"+NAMES[index]);
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes, 0, bytes.length);
            out.close();
        } catch (Exception e) {
        }
    }

    private native int nativeInit(int width, int height, int yStride, int vuStride, int numImages);
    private native int nativeDeinit();
    private native int nativeAddImage(ByteBuffer yB, ByteBuffer vuB, int ySize, int vuSize, int imageNum);
    private native int nativeGetDepthMap(byte[] depthMapBuf, int depthMapWidth, int depthMapHeight);
    private native int nativeProcessImage(byte[] buffer, int[] roi, int[] depthMapSize);

    static {
        try {
            System.loadLibrary("jni_ubifocus");
            mIsSupported = true;
        }catch(UnsatisfiedLinkError e) {
            mIsSupported = false;
        }
    }
}
