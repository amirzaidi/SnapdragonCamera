/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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
package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.android.camera.ui.PanoCaptureProcessView;

import java.io.ByteArrayOutputStream;

public class PanoCaptureFrameProcessor {

    private Allocation mInputAllocation;
    private Allocation mARGBOutputAllocation;

    private Surface mSurface;
    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;

    public ProcessingTask mTask;
    private Size mSize;
    private RenderScript mRs;
    private PanoCaptureUI mUI;
    private Activity mActivity;
    private PanoCaptureModule mController;
    ScriptIntrinsicYuvToRGB mRsYuvToRGB;
    private Bitmap mBitmap;
    private boolean mIsPanoActive = false;
    private Object mPanoSwitchLock = new Object();
    private Object mAllocationLock = new Object();
    private boolean mIsAllocationEverUsed;

    public PanoCaptureFrameProcessor(Size dimensions, Activity activity, PanoCaptureUI ui, PanoCaptureModule controller) {
        mUI = ui;
        mSize = dimensions;
        mActivity = activity;
        mController = controller;
        synchronized (mAllocationLock) {
            mRs = RenderScript.create(mActivity);
            mRsYuvToRGB = ScriptIntrinsicYuvToRGB.create(mRs, Element.RGBA_8888(mRs));

            Type.Builder yuvTypeBuilder = new Type.Builder(mRs, Element.YUV(mRs));
            yuvTypeBuilder.setX(dimensions.getWidth());
            yuvTypeBuilder.setY(dimensions.getHeight());
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            mInputAllocation = Allocation.createTyped(mRs, yuvTypeBuilder.create(),
                    Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            Type.Builder rgbTypeBuilder = new Type.Builder(mRs, Element.RGBA_8888(mRs));
            rgbTypeBuilder.setX(dimensions.getWidth());
            rgbTypeBuilder.setY(dimensions.getHeight());
            mARGBOutputAllocation = Allocation.createTyped(mRs, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT);

            if (mProcessingThread == null) {
                mProcessingThread = new HandlerThread("PanoCapture_FrameProcessor");
                mProcessingThread.start();
                mProcessingHandler = new Handler(mProcessingThread.getLooper());
            }
            mTask = new ProcessingTask();
            mInputAllocation.setOnBufferAvailableListener(mTask);
            mIsAllocationEverUsed = false;
        }
    }

    public void clear() {
        if(mIsPanoActive) {
            changePanoStatus(false, true);
        }
        synchronized (mAllocationLock) {
            mInputAllocation.setOnBufferAvailableListener(null);
            if(mIsAllocationEverUsed) {
                mRs.destroy();
                mInputAllocation.destroy();
                mARGBOutputAllocation.destroy();
            }
            mRs = null;
            mInputAllocation = null;
            mARGBOutputAllocation = null;
        }
        mProcessingThread.quitSafely();
        try {
            mProcessingThread.join();
            mProcessingThread = null;
            mProcessingHandler = null;
        } catch (InterruptedException e) {
        }
    }

    public Surface getInputSurface() {
        synchronized (mAllocationLock) {
            if (mInputAllocation == null)
                return null;
            return mInputAllocation.getSurface();
        }
    }

    public void changePanoStatus(boolean newStatus, boolean isCancelling) {
        if(newStatus == mIsPanoActive) {
            return;
        }
        synchronized (mPanoSwitchLock) {
            if(mUI.isPanoCompleting()) {
                return;
            }
            mIsPanoActive = newStatus;
            if (!mIsPanoActive) {
                mUI.onFrameAvailable(null, isCancelling);
            }
        }
        if(!mIsPanoActive) {
            mController.unlockFocus();
        }
    }

    public boolean isPanoActive() {
        return mIsPanoActive;
    }

    class ProcessingTask implements Runnable, Allocation.OnBufferAvailableListener {
        private int mPendingFrames = 0;
        private int mFrameCounter = 0;

        public ProcessingTask() {
            mBitmap = Bitmap.createBitmap(mSize.getWidth(), mSize.getHeight(), Bitmap.Config.ARGB_8888);
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            if(mProcessingHandler == null)
                return;
            synchronized(this) {
                mPendingFrames++;
                mProcessingHandler.post(this);
            }
        }

        @Override
        public void run() {
            int pendingFrames;
            synchronized(this) {
                pendingFrames = mPendingFrames;
                mPendingFrames = 0;
                mProcessingHandler.removeCallbacks(this);
            }

            synchronized (mAllocationLock) {
                if(mInputAllocation == null || mARGBOutputAllocation == null)
                    return;
                mIsAllocationEverUsed = true;
                for (int i = 0; i < pendingFrames; i++) {
                    mInputAllocation.ioReceive();
                }
                synchronized (mPanoSwitchLock) {
                    if (mIsPanoActive && !mUI.isFrameProcessing()) {
                        mRsYuvToRGB.setInput(mInputAllocation);
                        mRsYuvToRGB.forEach(mARGBOutputAllocation);
                        mARGBOutputAllocation.copyTo(mBitmap);
                        mUI.onFrameAvailable(mBitmap, false);
                    }
                }
            }
        }
    }

}

