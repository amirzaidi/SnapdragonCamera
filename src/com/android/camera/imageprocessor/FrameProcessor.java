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
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import com.android.camera.CaptureModule;
import com.android.camera.SettingsManager;
import com.android.camera.imageprocessor.filter.BeautificationFilter;
import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.imageprocessor.filter.TrackingFocusFrameListener;
import com.android.camera.ui.RotateTextToast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.codeaurora.snapcam.R;

public class FrameProcessor {

    private ImageReader mInputImageReader;
    private Allocation mInputAllocation;
    private Allocation mProcessAllocation;
    private Allocation mOutputAllocation;
    private Allocation mVideoOutputAllocation;

    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;
    private HandlerThread mOutingThread;
    private Handler mOutingHandler;
    private HandlerThread mListeningThread;
    private Handler mListeningHandler;

    private ProcessingTask mTask;
    private ListeningTask mListeningTask;
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
    private Surface mVideoSurfaceAsItIs;
    private boolean mIsActive = false;
    public static final int FILTER_NONE = 0;
    public static final int FILTER_MAKEUP = 1;
    public static final int LISTENER_TRACKING_FOCUS = 2;
    private CaptureModule mModule;
    private boolean mIsVideoOn = false;

    public FrameProcessor(Activity activity, CaptureModule module) {
        mActivity = activity;
        mModule = module;
        mPreviewFilters = new ArrayList<ImageFilter>();
        mFinalFilters = new ArrayList<ImageFilter>();

        mRs = RenderScript.create(mActivity);
        mRsYuvToRGB = new ScriptC_YuvToRgb(mRs);
        mRsRotator = new ScriptC_rotator(mRs);
    }

    private void init(Size previewDim) {
        mIsActive = true;
        mSize = previewDim;
        synchronized (mAllocationLock) {
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

            if (mListeningThread == null) {
                mListeningThread = new HandlerThread("FrameListeningThread");
                mListeningThread.start();
                mListeningHandler = new Handler(mListeningThread.getLooper());
            }

            mListeningTask = new ListeningTask();
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
        int degree = 90;
        if(mModule.getMainCameraCharacteristics() != null) {
            degree = mModule.getMainCameraCharacteristics().
                    get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (mModule.getMainCameraId() == CaptureModule.FRONT_ID) {
                degree = Math.abs(degree - 90);
            }
        }
        mRsRotator.set_degree(degree);
        mRsYuvToRGB.set_gIn(mProcessAllocation);
        mRsYuvToRGB.set_width(height);
        mRsYuvToRGB.set_height(width);
    }

    public ArrayList<ImageFilter> getFrameFilters() {
        return mFinalFilters;
    }

    private void cleanFilterSet() {
        if (mPreviewFilters != null) {
            for (ImageFilter filter : mPreviewFilters) {
                filter.deinit();
            }
        }
        if (mFinalFilters != null) {
            for (ImageFilter filter : mFinalFilters) {
                filter.deinit();
            }
        }
        mPreviewFilters = new ArrayList<ImageFilter>();
        mFinalFilters = new ArrayList<ImageFilter>();
    }

    public void onOpen(ArrayList<Integer> filterIds, final Size size) {
        cleanFilterSet();
        if (filterIds != null) {
            for (Integer i : filterIds) {
                addFilter(i.intValue());
            }
        }
        if(isFrameFilterEnabled() || isFrameListnerEnabled()) {
            init(size);
        }
    }

    private void addFilter(int filterId) {
        ImageFilter filter = null;
        if (filterId == FILTER_MAKEUP) {
            filter = new BeautificationFilter(mModule);
        } else if (filterId == LISTENER_TRACKING_FOCUS) {
            filter = new TrackingFocusFrameListener(mModule);
        }

        if (filter != null && filter.isSupported()) {
            mPreviewFilters.add(filter);
            if (!filter.isFrameListener()) {
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
                if (mVideoOutputAllocation != null) {
                    mVideoOutputAllocation.destroy();
                }
            }
            mProcessAllocation = null;
            mOutputAllocation = null;
            mInputAllocation = null;
            mVideoOutputAllocation = null;
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
        if (mListeningThread != null) {
            mListeningThread.quitSafely();
            try {
                mListeningThread.join();
                mListeningThread = null;
                mListeningHandler = null;
            } catch (InterruptedException e) {
            }
        }
        for (ImageFilter filter : mPreviewFilters) {
            filter.deinit();
        }
        for (ImageFilter filter : mFinalFilters) {
            filter.deinit();
        }
    }

    public void onDestory(){
        if (mRs != null) {
            mRs.destroy();
        }
        mRs = null;
    }

    private Surface getReaderSurface() {
        synchronized (mAllocationLock) {
            if (mInputImageReader == null) {
                return null;
            }
            return mInputImageReader.getSurface();
        }
    }

    public List<Surface> getInputSurfaces() {
        List<Surface> surfaces = new ArrayList<Surface>();
        if (mPreviewFilters.size() == 0 && mFinalFilters.size() == 0) {
            surfaces.add(mSurfaceAsItIs);
            if (mIsVideoOn) {
                surfaces.add(mVideoSurfaceAsItIs);
            }
        } else if (mFinalFilters.size() == 0) {
            surfaces.add(mSurfaceAsItIs);
            if (mIsVideoOn) {
                surfaces.add(mVideoSurfaceAsItIs);
            }
            surfaces.add(getReaderSurface());
        } else {
            surfaces.add(getReaderSurface());
        }
        return surfaces;
    }

    public boolean isFrameFilterEnabled() {
        if (mFinalFilters.size() == 0) {
            return false;
        }
        return true;
    }

    public boolean isFrameListnerEnabled() {
        if (mPreviewFilters.size() == 0) {
            return false;
        }
        return true;
    }

    public void setOutputSurface(Surface surface) {
        mSurfaceAsItIs = surface;
        if (mFinalFilters.size() != 0) {
            mOutputAllocation.setSurface(surface);
        }
    }

    public void setVideoOutputSurface(Surface surface) {
        if (surface == null) {
            synchronized (mAllocationLock) {
                if (mVideoOutputAllocation != null) {
                    mVideoOutputAllocation.destroy();
                }
                mVideoOutputAllocation = null;
            }
            mIsVideoOn = false;
            return;
        }
        mVideoSurfaceAsItIs = surface;
        mIsVideoOn = true;
        if (mFinalFilters.size() != 0) {
            synchronized (mAllocationLock) {
                if (mVideoOutputAllocation == null) {
                    Type.Builder rgbTypeBuilder = new Type.Builder(mRs, Element.RGBA_8888(mRs));
                    rgbTypeBuilder.setX(mSize.getHeight());
                    rgbTypeBuilder.setY(mSize.getWidth());
                    mVideoOutputAllocation = Allocation.createTyped(mRs, rgbTypeBuilder.create(),
                            Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
                }
                mVideoOutputAllocation.setSurface(surface);
            }
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
                if (mOutputAllocation == null) {
                    return;
                }
                try {
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }
                    if (!mIsActive) {
                        image.close();
                        return;
                    }
                    mIsAllocationEverUsed = true;
                    ByteBuffer bY = image.getPlanes()[0].getBuffer();
                    ByteBuffer bVU = image.getPlanes()[2].getBuffer();
                    if (yvuBytes == null || width != mSize.getWidth() || height != mSize.getHeight()) {
                        stride = image.getPlanes()[0].getRowStride();
                        width = mSize.getWidth();
                        height = mSize.getHeight();
                        ySize = stride * mSize.getHeight();
                        yvuBytes = new byte[ySize * 3 / 2];
                    }
                    boolean needToFeedSurface = false;
                    //Start processing yvu buf
                    for (ImageFilter filter : mPreviewFilters) {
                        if (filter.isFrameListener()) {
                            if (mListeningTask.setParam(filter, bY, bVU, mSize.getWidth(), mSize.getHeight(), stride)) {
                                mListeningHandler.post(mListeningTask);
                            }
                        } else {
                            filter.init(mSize.getWidth(), mSize.getHeight(), stride, stride);
                            filter.addImage(bY, bVU, 0, new Boolean(true));
                            needToFeedSurface = true;
                        }
                        bY.rewind();
                        bVU.rewind();
                    }
                    //End processing yvu buf
                    if (needToFeedSurface) {
                        bY.get(yvuBytes, 0, bY.remaining());
                        bVU.get(yvuBytes, ySize, bVU.remaining());
                        mOutingHandler.post(this);
                    }
                    image.close();
                } catch (IllegalStateException e) {
                }
            }
        }

        @Override
        public void run() {
            synchronized (mAllocationLock) {
                if (!mIsActive) {
                    return;
                }
                if (mInputAllocation == null) {
                    createAllocation(stride, height, stride - width);
                }
                mInputAllocation.copyFrom(yvuBytes);
                mRsRotator.forEach_rotate90andMerge(mInputAllocation);
                mRsYuvToRGB.forEach_nv21ToRgb(mOutputAllocation);
                mOutputAllocation.ioSend();
                if (mVideoOutputAllocation != null) {
                    mVideoOutputAllocation.copyFrom(mOutputAllocation);
                    mVideoOutputAllocation.ioSend();
                }
            }
        }
    }

    class ListeningTask implements Runnable {

        ImageFilter mFilter;
        ByteBuffer mBY = null, mBVU = null;
        int mWidth, mHeight, mStride;
        int bYSize, bVUSize;
        Semaphore mMutureLock = new Semaphore(1);

        public boolean setParam(ImageFilter filter, ByteBuffer bY, ByteBuffer bVU, int width, int height, int stride) {
            if (!mIsActive) {
                return false;
            }
            if (!mMutureLock.tryAcquire()) {
                return false;
            }
            mFilter = filter;
            if (mBY == null || bYSize != bY.remaining()) {
                bYSize = bY.remaining();
                mBY = ByteBuffer.allocateDirect(bYSize);
            }
            if (mBVU == null || bVUSize != bVU.remaining()) {
                bVUSize = bVU.remaining();
                mBVU = ByteBuffer.allocateDirect(bVUSize);
            }
            mBY.rewind();
            mBVU.rewind();
            mBY.put(bY);
            mBVU.put(bVU);
            mWidth = width;
            mHeight = height;
            mStride = stride;
            mMutureLock.release();
            return true;
        }

        @Override
        public void run() {
            try {
                if (!mIsActive) {
                    return;
                }
                mMutureLock.acquire();
                mBY.rewind();
                mBVU.rewind();
                mFilter.init(mWidth, mHeight, mStride, mStride);
                mFilter.addImage(mBY, mBVU, 0, new Boolean(true));
                mMutureLock.release();
            } catch (InterruptedException e) {
            }
        }
    }
}

