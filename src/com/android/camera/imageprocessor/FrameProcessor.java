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

package com.android.camera.imageprocessor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
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

import com.android.camera.CaptureModule;
import com.android.camera.PhotoModule;
import com.android.camera.imageprocessor.filter.BeautificationFilter;
import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.util.CameraUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class FrameProcessor {

    private ImageReader mInputImageReader;
    private Allocation mInputAllocation;
    private Allocation mProcessAllocation;
    private Allocation mOutputAllocation;

    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;
    private HandlerThread mOutingThread;
    private Handler mOutingHandler;

    public ProcessingTask mTask;
    private RenderScript mRs;
    private Activity mActivity;
    ScriptC_YuvToRgb mRsYuvToRGB;
    ScriptC_rotator mRsRotator;
    private Size mSize;
    private Object mAllocationLock = new Object();
    private boolean mIsAllocationEverUsed;
    private ArrayList<ImageFilter> mPreviewFilters;
    private ArrayList<ImageFilter> mFinalFilters;
    private Surface mSurfaceAsItIs;
    private boolean mIsActive = false;
    public static final int FILTER_NONE = 0;
    public static final int FILTER_MAKEUP = 1;
    private CaptureModule mModule;

    public FrameProcessor(Activity activity, CaptureModule module) {
        mActivity = activity;
        mModule = module;
        mPreviewFilters = new ArrayList<ImageFilter>();
        mFinalFilters = new ArrayList<ImageFilter>();
    }

    public void init(Size previewDim) {
        mSize = previewDim;
        synchronized (mAllocationLock) {
            mRs = RenderScript.create(mActivity);
            mRsYuvToRGB = new ScriptC_YuvToRgb(mRs);
            mRsRotator = new ScriptC_rotator(mRs);
            mInputImageReader = ImageReader.newInstance(mSize.getWidth(), mSize.getHeight(), ImageFormat.YUV_420_888, 8);

            Type.Builder rgbTypeBuilder = new Type.Builder(mRs, Element.RGBA_8888(mRs));
            rgbTypeBuilder.setX(mSize.getHeight());
            rgbTypeBuilder.setY(mSize.getWidth());
            mOutputAllocation = Allocation.createTyped(mRs, rgbTypeBuilder.create(),
                    Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);

            if (mProcessingThread == null) {
                mProcessingThread = new HandlerThread("FrameProcessor");
                mProcessingThread.start();
                mProcessingHandler = new Handler(mProcessingThread.getLooper());
            }

            if (mOutingThread == null) {
                mOutingThread = new HandlerThread("FrameOutingThread");
                mOutingThread.start();
                mOutingHandler = new Handler(mOutingThread.getLooper());
            }

            mTask = new ProcessingTask();
            mInputImageReader.setOnImageAvailableListener(mTask, mProcessingHandler);
            mIsAllocationEverUsed = false;
        }
    }

    private void createAllocation(int width, int height, int stridePad) {
        Type.Builder yuvTypeBuilder = new Type.Builder(mRs, Element.YUV(mRs));
        yuvTypeBuilder.setX(width);
        yuvTypeBuilder.setY(height);
        yuvTypeBuilder.setYuvFormat(ImageFormat.NV21);
        mInputAllocation = Allocation.createTyped(mRs, yuvTypeBuilder.create(), Allocation.USAGE_SCRIPT);
        Type.Builder nv21TypeBuilder = new Type.Builder(mRs, Element.U8(mRs));
        nv21TypeBuilder.setX(width * height * 3 / 2);
        mProcessAllocation = Allocation.createTyped(mRs, nv21TypeBuilder.create(), Allocation.USAGE_SCRIPT);
        mRsRotator.set_gIn(mInputAllocation);
        mRsRotator.set_gOut(mProcessAllocation);
        mRsRotator.set_width(width);
        mRsRotator.set_height(height);
        mRsRotator.set_pad(stridePad);
        mRsRotator.set_gFlip(!mModule.isBackCamera());
        mRsYuvToRGB.set_gIn(mProcessAllocation);
        mRsYuvToRGB.set_width(height);
        mRsYuvToRGB.set_height(width);
    }

    public ArrayList<ImageFilter> getFrameFilters() {
        return mFinalFilters;
    }

    private void cleanFilterSet() {
        if(mPreviewFilters != null) {
            for (ImageFilter filter : mPreviewFilters) {
                filter.deinit();
            }
        }
        if(mFinalFilters != null) {
            for (ImageFilter filter : mFinalFilters) {
                filter.deinit();
            }
        }
        mPreviewFilters = new ArrayList<ImageFilter>();
        mFinalFilters = new ArrayList<ImageFilter>();
    }

    public void onOpen(ArrayList<Integer> filterIds) {
        mIsActive = true;
        synchronized (mAllocationLock) {
            cleanFilterSet();
            if (filterIds != null) {
                for (Integer i : filterIds) {
                    addFilter(i.intValue());
                }
            }
        }
    }

    private void addFilter(int filterId) {
        if(filterId == FILTER_MAKEUP) {
            ImageFilter filter = new BeautificationFilter(mModule);
            if(filter.isSupported()) {
                mPreviewFilters.add(filter);
                mFinalFilters.add(filter);
            }
        }
    }

    public void onClose() {
        mIsActive = false;
        synchronized (mAllocationLock) {
            if (mIsAllocationEverUsed) {
                if (mInputAllocation != null) {
                    mInputAllocation.destroy();
                }
                if (mOutputAllocation != null) {
                    mOutputAllocation.destroy();
                }
                if (mProcessAllocation != null) {
                    mProcessAllocation.destroy();
                }
            }
            if (mRs != null) {
                mRs.destroy();
            }
            mRs = null;
            mProcessAllocation = null;
            mOutputAllocation = null;
            mInputAllocation = null;
        }
        if (mProcessingThread != null) {
            mProcessingThread.quitSafely();
            try {
                mProcessingThread.join();
                mProcessingThread = null;
                mProcessingHandler = null;
            } catch (InterruptedException e) {
            }
        }
        if (mOutingThread != null) {
            mOutingThread.quitSafely();
            try {
                mOutingThread.join();
                mOutingThread = null;
                mOutingHandler = null;
            } catch (InterruptedException e) {
            }
        }
        for(ImageFilter filter : mPreviewFilters) {
            filter.deinit();
        }
        for(ImageFilter filter : mFinalFilters) {
            filter.deinit();
        }
    }

    public Surface getInputSurface() {
        if(mPreviewFilters.size() == 0) {
            return mSurfaceAsItIs;
        }
        synchronized (mAllocationLock) {
            if (mInputImageReader == null)
                return null;
            return mInputImageReader.getSurface();
        }
    }

    public boolean isFrameFilterEnabled() {
        if(mPreviewFilters.size() == 0) {
            return false;
        }
        return true;
    }

    public void setOutputSurface(Surface surface) {
        if(mPreviewFilters.size() == 0) {
            mSurfaceAsItIs = surface;
        } else {
            mOutputAllocation.setSurface(surface);
        }
    }

    class ProcessingTask implements Runnable, ImageReader.OnImageAvailableListener {
        byte[] yvuBytes = null;
        int ySize;
        int stride;
        int height;
        int width;

        public ProcessingTask() {
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            synchronized (mAllocationLock) {
                if(mOutputAllocation == null)
                    return;
                try {
                    Image image = reader.acquireLatestImage();
                    if(image == null)
                        return;
                    if(!mIsActive) {
                        image.close();
                        return;
                    }
                    mIsAllocationEverUsed = true;
                    ByteBuffer bY = image.getPlanes()[0].getBuffer();
                    ByteBuffer bVU = image.getPlanes()[2].getBuffer();
                    if(yvuBytes == null) {
                        stride = image.getPlanes()[0].getRowStride();
                        width = mSize.getWidth();
                        height = mSize.getHeight();
                        ySize = stride * mSize.getHeight();
                        yvuBytes = new byte[ySize*3/2];
                    }
                    //Start processing yvu buf
                    for (ImageFilter filter : mPreviewFilters) {
                        filter.init(mSize.getWidth(), mSize.getHeight(), stride, stride);
                        filter.addImage(bY, bVU, 0, new Boolean(true));
                    }
                    //End processing yvu buf
                    bY.get(yvuBytes, 0, bY.remaining());
                    bVU.get(yvuBytes, ySize, bVU.remaining());
                    image.close();
                    mOutingHandler.post(this);
                } catch (IllegalStateException e) {
                }
            }
        }

        @Override
        public void run() {
            synchronized (mAllocationLock) {
                if(!mIsActive) {
                    return;
                }
                if(mInputAllocation == null) {
                    createAllocation(stride, height, stride-width);
                }
                mInputAllocation.copyFrom(yvuBytes);
                mRsRotator.forEach_rotate90andMerge(mInputAllocation);
                mRsYuvToRGB.forEach_nv21ToRgb(mOutputAllocation);
                mOutputAllocation.ioSend();
            }
        }
    }

    private native int nativeRotateNV21(ByteBuffer inBuf, int imageWidth, int imageHeight, int degree, ByteBuffer outBuf);

    private native int nativeNV21toRgb(ByteBuffer yvuBuf, ByteBuffer rgbBuf, int width, int height);

    static {
        System.loadLibrary("jni_imageutil");
    }
}

