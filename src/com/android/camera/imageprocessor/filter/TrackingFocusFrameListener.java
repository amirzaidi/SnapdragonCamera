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

import android.graphics.Point;
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
import com.android.camera.ui.TrackingFocusRenderer;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;

public class TrackingFocusFrameListener implements ImageFilter {

    int mWidth;
    int mHeight;
    int mStrideY;
    int mStrideVU;
    private CaptureModule mModule;
    private static boolean DEBUG = false;
    private static String TAG = "TrackingFocusFrameListener";
    private static boolean mIsSupported = false;
    private Rect imageRect;
    public static final long PENDING_REGISTRATION = -1;
    public static final int MAX_NUM_TRACKED_OBJECTS = 3;
    private long mTrackedId = PENDING_REGISTRATION;
    private boolean mIsInitialzed = false;
    private TrackingFocusRenderer mTrackingFocusRender;
    byte[] yvuBytes = null;
    private int[] mInputCords = null;
    private boolean mIsFirstTime = true;

    public enum OperationMode {
        DEFAULT,
        PERFORMANCE,
        CPU_OFFLOAD,
        LOW_POWER
    }

    public enum Precision {
        HIGH,
        LOW
    }

    public TrackingFocusFrameListener(CaptureModule module) {
        mModule = module;
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        return null;
    }

    @Override
    public String getStringName() {
        return "TrackingFocusFrameListener";
    }

    @Override
    public int getNumRequiredImage() {
        return 1;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        mWidth = width;
        mHeight = height;
        mStrideY = strideY;
        mStrideVU = strideVU;
        if(!mIsInitialzed) {
            if (nInit(OperationMode.PERFORMANCE.ordinal(), Precision.HIGH.ordinal(), mWidth, mHeight, mStrideY) < 0) {
                Log.e(TAG, "Initialization failed.");
            }
            imageRect = new Rect(0, 0, width, height);
            mTrackingFocusRender = mModule.getTrackingForcusRenderer();
            yvuBytes = new byte[mStrideY*mHeight*3/2];
            mIsInitialzed = true;
        }
    }

    @Override
    public void deinit() {
        if (mIsInitialzed) {
            nRelease();
            mIsInitialzed = false;
        }
    }

    @Override
    public void addImage(ByteBuffer bY, ByteBuffer bVU, int imageNum, Object isPreview) {
        bY.get(yvuBytes, 0, bY.remaining());
        bVU.get(yvuBytes, mStrideY * mHeight, bVU.remaining());
        int[] cords = mTrackingFocusRender.getInputCords(mWidth, mHeight);
        if(cords != null) {
            if(mTrackedId != PENDING_REGISTRATION) {
                unregisterObject(mTrackedId);
                mTrackedId = PENDING_REGISTRATION;
            }
            mIsFirstTime = true;
            mInputCords = cords;
        }
        if(mInputCords != null) {
            if (mTrackedId == PENDING_REGISTRATION) {
                try {
                    mTrackedId = registerObject(yvuBytes, new Point(mInputCords[0], mInputCords[1]), mIsFirstTime);
                    mIsFirstTime = false;
                }catch(IllegalArgumentException e) {
                    mTrackedId = PENDING_REGISTRATION;
                    Log.e(TAG, e.toString());
                }
            }
            if(mTrackedId != PENDING_REGISTRATION) {
                mTrackingFocusRender.putRegisteredCords(trackObjects(yvuBytes), mWidth, mHeight);
            }
        }
    }

    public static class Result {
        public final int id;
        public final int confidence;
        public Rect pos;

        private Result(int id, int confidence, int left, int top, int right, int bottom) {
            this.id = id;
            this.confidence = confidence;
            this.pos = new Rect(left, top, right, bottom);
        }

        public static Result Copy(Result old) {
            Result result = new Result(old.id, old.confidence, old.pos.left, old.pos.top, old.pos.right, old.pos.bottom);
            return result;
        }
    }

    public int getMinRoiDimension() {
        if (!mIsInitialzed) {
            throw new IllegalArgumentException("already released");
        }

        return nGetMinRoiDimension();
    }

    public int getMaxRoiDimension() {
        if (!mIsInitialzed) {
            throw new IllegalArgumentException("already released");
        }

        return nGetMaxRoiDimension();
    }

    public long registerObject(byte[] imageDataNV21, Rect rect)
    {
        if (imageDataNV21 == null || imageDataNV21.length < getMinFrameSize()) {
            throw new IllegalArgumentException("imageDataNV21 null or too small to encode frame");
        } else if (rect == null || rect.isEmpty() || !imageRect.contains(rect)) {
            throw new IllegalArgumentException("rect must be non-empty and be entirely inside " +
                    "the frame");
        } else if (!mIsInitialzed) {
            throw new IllegalArgumentException("already released");
        }
        long id = nRegisterObjectByRect(imageDataNV21, rect.left, rect.top, rect.right, rect.bottom);
        if(id == 0) {
            id = PENDING_REGISTRATION;
        }
        mTrackedId = id;
        return mTrackedId;
    }

    public long registerObject(byte[] imageDataNV21, Point point, boolean firstTime)
    {
        if (imageDataNV21 == null || imageDataNV21.length < getMinFrameSize()) {
            throw new IllegalArgumentException("imageDataNV21 null or too small to encode frame"
                    + imageDataNV21.length+ " "+getMinFrameSize());
        } else if (point == null || !imageRect.contains(point.x, point.y)) {
            throw new IllegalArgumentException("point is outside the image frame: "+imageRect.toString());
        } else if (!mIsInitialzed) {
            throw new IllegalArgumentException("already released");
        }
        long id = nRegisterObjectByPoint(imageDataNV21, point.x, point.y, firstTime);
        if(id == 0) {
            id = PENDING_REGISTRATION;
        }
        mTrackedId = id;
        return mTrackedId;
    }

    public void unregisterObject(long id)
    {
        if (id == PENDING_REGISTRATION) {
            Log.e(TAG, "There's a pending object");
        } else if (!mIsInitialzed) {
            Log.e(TAG, "already released");
        }
        nUnregisterObject(id);
    }

    public Result trackObjects(byte[] imageDataNV21)
    {
        if (imageDataNV21 == null || imageDataNV21.length < getMinFrameSize()) {
            Log.e(TAG, "imageDataNV21 null or too small to encode frame "
                    + imageDataNV21.length+ " "+getMinFrameSize());
        } else if (!mIsInitialzed) {
            Log.e(TAG, "It's released");
        }

        int[] nResults = nTrackObjects(imageDataNV21);
        return new Result(nResults[0], nResults[1], nResults[2], nResults[3], nResults[4], nResults[5]);
    }

    private int getMinFrameSize() {
        return ((mStrideY * imageRect.bottom * 3) / 2);
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
        return true;
    }

    @Override
    public boolean isManualMode() {
        return false;
    }

    @Override
    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback, Handler handler) {

    }

    private native int nInit(int operationMode, int precision, int width, int height, int stride);
    private native void nRelease();
    private native int nGetMinRoiDimension();
    private native int nGetMaxRoiDimension();
    private native long nRegisterObjectByRect(byte[] imageDataNV21, int left, int top, int right, int bottom);
    private native long nRegisterObjectByPoint(byte[] imageDataNV21, int x, int y, boolean firstTime);
    private native void nUnregisterObject(long id);
    private native int[] nTrackObjects(byte[] imageDataNV21);

    static {
        try {
            System.loadLibrary("jni_trackingfocus");
            mIsSupported = true;
        }catch(UnsatisfiedLinkError e) {
            Log.d(TAG, e.toString());
            mIsSupported = false;
        }
    }
}
