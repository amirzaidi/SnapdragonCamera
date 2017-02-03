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

package org.codeaurora.snapcam.filter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codeaurora.snapcam.filter.ClearSightNativeEngine.CamSystemCalibrationData;
import org.codeaurora.snapcam.filter.ClearSightNativeEngine.ClearsightImage;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import android.util.SparseLongArray;
import android.util.Size;
import android.view.Surface;

import com.android.camera.CaptureModule;
import com.android.camera.Exif;
import com.android.camera.exif.ExifInterface;
import com.android.camera.MediaSaveService;
import com.android.camera.MediaSaveService.OnMediaSavedListener;
import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.Storage;
import com.android.camera.util.CameraUtil;

public class ClearSightImageProcessor {
    private static final String TAG = "ClearSightImageProcessor";
    private static final String PERSIST_TIMESTAMP_LIMIT_KEY = "persist.camera.cs.threshold";
    private static final String PERSIST_BURST_COUNT_KEY = "persist.camera.cs.burstcount";
    private static final String PERSIST_DUMP_FRAMES_KEY = "persist.camera.cs.dumpframes";
    private static final String PERSIST_DUMP_YUV_KEY = "persist.camera.cs.dumpyuv";
    private static final String PERSIST_CS_TIMEOUT_KEY = "persist.camera.cs.timeout";

    private static final long DEFAULT_TIMESTAMP_THRESHOLD_MS = 10;
    private static final int DEFAULT_IMAGES_TO_BURST = 4;
    private static final int DEFAULT_CS_TIMEOUT_MS = 300;

    private static final long MIN_MONO_AREA = 1900000;  // ~1.9 MP
    private static final Size[] MONO_SIZES = {
        new Size(1600, 1200),   // 4:3
        new Size(1920, 1080),   // 16:9
        new Size(1400, 1400)    // 1:1
    };

    private static final int MSG_START_CAPTURE = 0;
    private static final int MSG_NEW_IMG = 1;
    private static final int MSG_NEW_CAPTURE_RESULT = 2;
    private static final int MSG_NEW_CAPTURE_FAIL = 3;
    private static final int MSG_NEW_REPROC_RESULT = 4;
    private static final int MSG_NEW_REPROC_FAIL = 5;
    private static final int MSG_END_CAPTURE = 6;

    private static final int CAM_TYPE_BAYER = 0;
    private static final int CAM_TYPE_MONO = 1;
    private static final int NUM_CAM = 2;

    private static CameraCharacteristics.Key<byte[]> OTP_CALIB_BLOB =
            new CameraCharacteristics.Key<>(
                    "org.codeaurora.qcamera3.dualcam_calib_meta_data.dualcam_calib_meta_data_blob",
                    byte[].class);

    private NamedImages mNamedImages;
    private ImageReader[] mImageReader = new ImageReader[NUM_CAM];
    private ImageReader[] mEncodeImageReader = new ImageReader[NUM_CAM];
    private ImageWriter[] mImageWriter = new ImageWriter[NUM_CAM];
    private float mFinalPictureRatio;
    private Size mFinalPictureSize;
    private Size mFinalMonoSize;

    private ImageProcessHandler mImageProcessHandler;
    private ClearsightRegisterHandler mClearsightRegisterHandler;
    private ClearsightProcessHandler mClearsightProcessHandler;
    private ImageEncodeHandler mImageEncodeHandler;
    private HandlerThread mImageProcessThread;
    private HandlerThread mClearsightRegisterThread;
    private HandlerThread mClearsightProcessThread;
    private HandlerThread mImageEncodeThread;
    private Callback mCallback;

    private CameraCaptureSession[] mCaptureSessions = new CameraCaptureSession[NUM_CAM];
    private MediaSaveService mMediaSaveService;
    private OnMediaSavedListener mMediaSavedListener;

    private long mTimestampThresholdNs;
    private int mNumBurstCount;
    private int mNumFrameCount;
    private int mCsTimeout;
    private boolean mDumpImages;
    private boolean mDumpYUV;
    private boolean mIsClosing;
    private int mFinishReprocessNum;

    private static ClearSightImageProcessor mInstance;

    private ClearSightImageProcessor() {
        mNamedImages = new NamedImages();
        long threshMs = SystemProperties.getLong(PERSIST_TIMESTAMP_LIMIT_KEY, DEFAULT_TIMESTAMP_THRESHOLD_MS);
        mTimestampThresholdNs = threshMs * 1000000;
        Log.d(TAG, "mTimestampThresholdNs: " + mTimestampThresholdNs);

        mNumBurstCount = SystemProperties.getInt(PERSIST_BURST_COUNT_KEY, DEFAULT_IMAGES_TO_BURST);
        Log.d(TAG, "mNumBurstCount: " + mNumBurstCount);

        mNumFrameCount = mNumBurstCount - 1;
        Log.d(TAG, "mNumFrameCount: " + mNumFrameCount);

        mDumpImages = SystemProperties.getBoolean(PERSIST_DUMP_FRAMES_KEY, false);
        Log.d(TAG, "mDumpImages: " + mDumpImages);

        mDumpYUV = SystemProperties.getBoolean(PERSIST_DUMP_YUV_KEY, false);
        Log.d(TAG, "mDumpYUV: " + mDumpYUV);

        mCsTimeout = SystemProperties.getInt(PERSIST_CS_TIMEOUT_KEY, DEFAULT_CS_TIMEOUT_MS);
        Log.d(TAG, "mCsTimeout: " + mCsTimeout);
    }

    public static void createInstance() {
        if(mInstance == null) {
            mInstance = new ClearSightImageProcessor();
            ClearSightNativeEngine.createInstance();
        }
    }

    public static ClearSightImageProcessor getInstance() {
        if(mInstance == null) {
            createInstance();
        }
        return mInstance;
    }

    public void init(StreamConfigurationMap map, int width, int height,
            Context context, OnMediaSavedListener mediaListener) {
        Log.d(TAG, "init() start");
        mIsClosing = false;
        mImageProcessThread = new HandlerThread("CameraImageProcess");
        mImageProcessThread.start();
        mClearsightRegisterThread = new HandlerThread("ClearsightRegister");
        mClearsightRegisterThread.start();
        mClearsightProcessThread = new HandlerThread("ClearsightProcess");
        mClearsightProcessThread.start();
        mImageEncodeThread = new HandlerThread("CameraImageEncode");
        mImageEncodeThread.start();

        mImageProcessHandler = new ImageProcessHandler(mImageProcessThread.getLooper());
        mClearsightRegisterHandler = new ClearsightRegisterHandler(mClearsightRegisterThread.getLooper());
        mClearsightProcessHandler = new ClearsightProcessHandler(mClearsightProcessThread.getLooper());
        mImageEncodeHandler = new ImageEncodeHandler(mImageEncodeThread.getLooper());

        mFinalPictureSize = new Size(width, height);
        mFinalPictureRatio = (float)width / (float)height;
        mFinalMonoSize = getFinalMonoSize();
        Size maxSize = findMaxOutputSize(map);
        int maxWidth = maxSize.getWidth();
        int maxHeight = maxSize.getHeight();
        mImageReader[CAM_TYPE_BAYER] = createImageReader(CAM_TYPE_BAYER, maxWidth, maxHeight);
        mImageReader[CAM_TYPE_MONO] = createImageReader(CAM_TYPE_MONO, maxWidth, maxHeight);
        mEncodeImageReader[CAM_TYPE_BAYER] = createEncodeImageReader(CAM_TYPE_BAYER, maxWidth, maxHeight);
        mEncodeImageReader[CAM_TYPE_MONO] = createEncodeImageReader(CAM_TYPE_MONO, maxWidth, maxHeight);

        mMediaSavedListener = mediaListener;
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cc = cm.getCameraCharacteristics("0");
            byte[] blob = cc.get(OTP_CALIB_BLOB);
            ClearSightNativeEngine.getInstance().init(mNumFrameCount*2,
                    maxWidth, maxHeight, CamSystemCalibrationData.createFromBytes(blob));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "init() done");
    }

    public void close() {
        Log.d(TAG, "close() start");
        mIsClosing = true;
        // use quit instead of quitSafely
        // because we don't want to process any more queued events.
        // just clean up and exit.
        if(mImageProcessThread != null) {
            mImageProcessThread.quit();

            try {
                mImageProcessThread.join();
                mImageProcessThread = null;
                mImageProcessHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(mClearsightRegisterThread != null) {
            mClearsightRegisterThread.quit();

            try {
                mClearsightRegisterThread.join();
                mClearsightRegisterThread = null;
                mClearsightRegisterHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(mClearsightProcessThread != null) {
            mClearsightProcessThread.quit();

            try {
                mClearsightProcessThread.join();
                mClearsightProcessThread = null;
                mClearsightProcessHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(mImageEncodeThread != null) {
            mImageEncodeThread.quit();

            try {
                mImageEncodeThread.join();
                mImageEncodeThread = null;
                mImageEncodeHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for(int i=0; i<mImageReader.length; i++) {
            if (null != mImageReader[i]) {
                mImageReader[i].close();
                mImageReader[i] = null;
            }
            if (null != mEncodeImageReader[i]) {
                mEncodeImageReader[i].close();
                mEncodeImageReader[i] = null;
            }
            if (null != mImageWriter[i]) {
                mImageWriter[i].close();
                mImageWriter[i] = null;
            }
        }

        mCaptureSessions[CAM_TYPE_MONO] = null;
        mCaptureSessions[CAM_TYPE_BAYER] = null;
        mMediaSaveService = null;
        mMediaSavedListener = null;
        ClearSightNativeEngine.getInstance().close();
        Log.d(TAG, "close() done");
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setMediaSaveService(MediaSaveService service) {
        mMediaSaveService = service;
    }

    public void createCaptureSession(boolean bayer, CameraDevice device, List<Surface> surfaces,
            CameraCaptureSession.StateCallback captureSessionCallback) throws CameraAccessException {

        Log.d(TAG, "createCaptureSession: " + bayer);

        int cam = bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO;
        surfaces.add(mImageReader[cam].getSurface());
        surfaces.add(mEncodeImageReader[cam].getSurface());
        // Here, we create a CameraCaptureSession for camera preview.
        device.createReprocessableCaptureSession(
                new InputConfiguration(mImageReader[cam].getWidth(),
                        mImageReader[cam].getHeight(), mImageReader[cam].getImageFormat()),
                        surfaces, captureSessionCallback, null);
    }

    public void onCaptureSessionConfigured(boolean bayer, CameraCaptureSession session) {
        Log.d(TAG, "onCaptureSessionConfigured: " + bayer);

        mCaptureSessions[bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO] = session;
        mImageWriter[bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO] =
                ImageWriter.newInstance(session.getInputSurface(), mNumBurstCount);
    }

    public CaptureRequest.Builder createCaptureRequest(CameraDevice device) throws CameraAccessException {
        Log.d(TAG, "createCaptureRequest");

        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        return builder;
    }

    public void capture(boolean bayer, CameraCaptureSession session, CaptureRequest.Builder requestBuilder,
            Handler captureCallbackHandler) throws CameraAccessException {
        Log.d(TAG, "capture: " + bayer);

        final int cam = bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO;

        CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session,
                    CaptureRequest request,
                    TotalCaptureResult result) {
                Log.d(TAG, "capture - onCaptureCompleted: " + cam);
                if(isClosing())
                    Log.d(TAG, "capture - onCaptureCompleted - closing");
                else
                    mImageProcessHandler.obtainMessage(MSG_NEW_CAPTURE_RESULT,
                            cam, 0, result).sendToTarget();
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session,
                    CaptureRequest request,
                    CaptureFailure result) {
                Log.d(TAG, "capture - onCaptureFailed: " + cam);
                if(isClosing())
                    Log.d(TAG, "capture - onCaptureFailed - closing");
                else
                    mImageProcessHandler.obtainMessage(MSG_NEW_CAPTURE_FAIL,
                            cam, 0, result).sendToTarget();
            }

            @Override
            public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                    sequenceId, long frameNumber) {
                Log.d(TAG, "capture - onCaptureSequenceCompleted: " + cam);
            }
        };

        List<CaptureRequest> burstList = new ArrayList<CaptureRequest>();
        requestBuilder.addTarget(mImageReader[cam].getSurface());
        for (int i = 0; i < mNumBurstCount; i++) {
            requestBuilder.setTag(new Object());
            CaptureRequest request = requestBuilder.build();
            burstList.add(request);
        }

        mImageProcessHandler.obtainMessage(MSG_START_CAPTURE, cam, burstList.size(), 0).sendToTarget();
        session.captureBurst(burstList, captureCallback, captureCallbackHandler);
    }

    private boolean isClosing() {
        return mIsClosing;
    }

    private ImageReader createImageReader(final int cam, int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height,
                ImageFormat.YUV_420_888, mNumBurstCount + mNumFrameCount);
        reader.setOnImageAvailableListener(new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "onImageAvailable for cam: " + cam);
                if(isClosing())
                    Log.d(TAG, "onImageAvailable - closing");
                else
                    mImageProcessHandler.obtainMessage(
                            MSG_NEW_IMG, cam, 0, reader.acquireNextImage()).sendToTarget();
            }
        }, null);

        return reader;
    }

    private ImageReader createEncodeImageReader(final int cam, int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height,
                ImageFormat.JPEG, mNumFrameCount);
        reader.setOnImageAvailableListener(new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "jpeg image available for cam: " + cam);
                mImageEncodeHandler.obtainMessage(
                        MSG_NEW_IMG, cam, 0, reader.acquireNextImage()).sendToTarget();
            }
        }, null);

        return reader;
    }

    public interface Callback {
        public void onReleaseShutterLock();
        public void onClearSightSuccess(byte[] thumnailBytes);
        public void onClearSightFailure(byte[] thumnailBytes);
    }

    private static class ReprocessableImage {
        final Image mImage;
        final TotalCaptureResult mCaptureResult;

        ReprocessableImage(Image image, TotalCaptureResult result) {
            mImage = image;
            mCaptureResult = result;
        }
    }

    private class ImageProcessHandler extends Handler {
        private ArrayDeque<ReprocessableImage> mBayerFrames = new ArrayDeque<ReprocessableImage>(
                mNumBurstCount);
        private ArrayDeque<ReprocessableImage> mMonoFrames = new ArrayDeque<ReprocessableImage>(
                mNumBurstCount);
        private ArrayDeque<TotalCaptureResult> mBayerCaptureResults = new ArrayDeque<TotalCaptureResult>(
                mNumBurstCount);
        private ArrayDeque<TotalCaptureResult> mMonoCaptureResults = new ArrayDeque<TotalCaptureResult>(
                mNumBurstCount);
        private ArrayDeque<Image> mBayerImages = new ArrayDeque<Image>(
                mNumBurstCount);
        private ArrayDeque<Image> mMonoImages = new ArrayDeque<Image>(
                mNumBurstCount);

        private SparseLongArray[] mReprocessingFrames = new SparseLongArray[NUM_CAM];
        private ArrayList<CaptureRequest> mReprocessingRequests = new ArrayList<CaptureRequest>();
        private int mReprocessingPairCount;
        private int mReprocessedBayerCount;
        private int mReprocessedMonoCount;
        private NamedEntity mNamedEntity;
        private int[] mNumImagesToProcess = new int[NUM_CAM];
        private boolean mCaptureDone;
        private boolean mHasFailures;

        ImageProcessHandler(Looper looper) {
            super(looper);
            mReprocessingFrames[CAM_TYPE_BAYER] = new SparseLongArray();
            mReprocessingFrames[CAM_TYPE_MONO] = new SparseLongArray();
        }

        @Override
        public void handleMessage(Message msg) {
            if(isClosing()) return;

            switch (msg.what) {
            case MSG_START_CAPTURE:
                mCaptureDone = false;
                mFinishReprocessNum = 0;
                mHasFailures = false;
                mReprocessingPairCount = 0;
                mReprocessedBayerCount = 0;
                mReprocessedMonoCount = 0;
                mNumImagesToProcess[msg.arg1] = msg.arg2;
                mNamedImages.nameNewImage(System.currentTimeMillis());
                mNamedEntity = mNamedImages.getNextNameEntity();
                mClearsightRegisterHandler.obtainMessage(MSG_START_CAPTURE,
                        0, 0, mNamedEntity).sendToTarget();
                break;
            case MSG_END_CAPTURE:
                // TIMED OUT WAITING FOR FRAME
                handleTimeout();
                break;
            case MSG_NEW_IMG:
                processImg(msg);
                break;
            case MSG_NEW_CAPTURE_RESULT:
                processNewCaptureEvent(msg);
                break;
            case MSG_NEW_REPROC_RESULT:
                processNewReprocessResult(msg);
                break;
            case MSG_NEW_CAPTURE_FAIL:
                processNewCaptureEvent(msg);
                break;
            case MSG_NEW_REPROC_FAIL:
                processNewReprocessFailure(msg);
                break;
            }
        }

        private void handleTimeout() {
            Log.d(TAG, "handleTimeout");
            releaseBayerFrames();
            releaseMonoFrames();
            mReprocessingFrames[CAM_TYPE_BAYER].clear();
            mReprocessingFrames[CAM_TYPE_MONO].clear();
            mReprocessingRequests.clear();

            removeMessages(MSG_NEW_CAPTURE_RESULT);
            removeMessages(MSG_NEW_CAPTURE_FAIL);
            removeMessages(MSG_NEW_REPROC_RESULT);
            removeMessages(MSG_NEW_REPROC_FAIL);
            removeMessages(MSG_END_CAPTURE);

            // set capture done so that any loose frames coming in will be closed
            mCaptureDone = true;
            mClearsightRegisterHandler.obtainMessage(MSG_END_CAPTURE, 0, 1).sendToTarget();
        }

        private void kickTimeout() {
            removeMessages(MSG_END_CAPTURE);
            sendEmptyMessageDelayed(MSG_END_CAPTURE, mCsTimeout);
        }

        private void processImg(Message msg) {
            int camId = msg.arg1;
            Log.d(TAG, "processImg: " + camId);
            Image image = (Image) msg.obj;
            if(mReprocessingFrames[camId].size() > 0
                    && mReprocessingFrames[camId].indexOfValue(image.getTimestamp()) >= 0) {
                // reproc frame
                processNewReprocessImage(msg);
            } else {
                // new capture frame
                processNewCaptureEvent(msg);
            }
        }

        private void processNewCaptureEvent(Message msg) {
            kickTimeout();

             // Toss extra frames
            if(mCaptureDone) {
                Log.d(TAG, "processNewCaptureEvent - captureDone - we already have required frame pairs " + msg.arg1);
                if(msg.what == MSG_NEW_IMG) {
                    Image image = (Image) msg.obj;
                    Log.d(TAG, "processNewCaptureEvent - captureDone - tossed frame ts: " + image.getTimestamp());
                    image.close();
                }
                return;
            }

            ArrayDeque<Image> imageQueue;
            ArrayDeque<TotalCaptureResult> resultQueue;
            ArrayDeque<ReprocessableImage> frameQueue;
            // push image onto queue
            if (msg.arg1 == CAM_TYPE_BAYER) {
                imageQueue = mBayerImages;
                resultQueue = mBayerCaptureResults;
                frameQueue = mBayerFrames;
            } else {
                imageQueue = mMonoImages;
                resultQueue = mMonoCaptureResults;
                frameQueue = mMonoFrames;
            }

            if(msg.what == MSG_NEW_IMG) {
                Log.d(TAG, "processNewCaptureEvent - newImg: " + msg.arg1);
                Image image = (Image) msg.obj;
                imageQueue.add(image);
            } else if(msg.what == MSG_NEW_CAPTURE_FAIL) {
                Log.d(TAG, "processNewCaptureEvent - new failed result: " + msg.arg1);
                mNumImagesToProcess[msg.arg1]--;
            } else {
                Log.d(TAG, "processNewCaptureEvent - newResult: " + msg.arg1);
                TotalCaptureResult result = (TotalCaptureResult) msg.obj;
                resultQueue.add(result);
            }

            Log.d(TAG, "processNewCaptureEvent - cam: " + msg.arg1 + " num imgs: "
                    + imageQueue.size() + " num results: " + resultQueue.size());

            if (!imageQueue.isEmpty() && !resultQueue.isEmpty()) {
                Image headImage = imageQueue.poll();
                TotalCaptureResult headResult = resultQueue.poll();
                frameQueue.add(new ReprocessableImage(headImage, headResult));
                mNumImagesToProcess[msg.arg1]--;
                checkForValidFramePairAndReprocess();
            }


            Log.d(TAG, "processNewCaptureEvent - " +
                    "imagestoprocess[bayer] " + mNumImagesToProcess[CAM_TYPE_BAYER] +
                    " imagestoprocess[mono]: " + mNumImagesToProcess[CAM_TYPE_MONO] +
                    " mReprocessingPairCount: " + mReprocessingPairCount +
                    " mNumFrameCount: " + mNumFrameCount +
                    " mFinishReprocessNum: " + mFinishReprocessNum);

            if ((mNumImagesToProcess[CAM_TYPE_BAYER] == 0
                    && mNumImagesToProcess[CAM_TYPE_MONO] == 0)
                    && mReprocessingPairCount != mNumFrameCount) {
                while (!mBayerFrames.isEmpty() && !mMonoFrames.isEmpty()
                        && mReprocessingPairCount != mNumFrameCount) {
                    checkForValidFramePairAndReprocess();
                }
            }

            if (mReprocessingPairCount == mNumFrameCount ||
                    (mNumImagesToProcess[CAM_TYPE_BAYER] == 0
                    && mNumImagesToProcess[CAM_TYPE_MONO] == 0)) {
                processFinalPair();
                if (mReprocessingPairCount != 0 &&
                        mFinishReprocessNum == mReprocessingPairCount * 2) {
                    checkReprocessDone();
                }
            }
        }

        private void checkForValidFramePairAndReprocess() {
            // if we have images from both
            // as we just added an image onto one of the queues
            // this condition is only true when both are not empty
            Log.d(TAG,
                    "checkForValidFramePair - num bayer frames: "
                            + mBayerFrames.size() + " num mono frames: "
                            + mMonoFrames.size());

            if (!mBayerFrames.isEmpty() && !mMonoFrames.isEmpty()) {
                // peek oldest pair of images
                ReprocessableImage bayer = mBayerFrames.peek();
                ReprocessableImage mono = mMonoFrames.peek();

                long bayerTsSOF = bayer.mCaptureResult.get(CaptureResult.SENSOR_TIMESTAMP);
                long bayerTsEOF = bayerTsSOF + bayer.mCaptureResult.get(
                        CaptureResult.SENSOR_EXPOSURE_TIME);
                long monoTsSOF = mono.mCaptureResult.get(CaptureResult.SENSOR_TIMESTAMP);
                long monoTsEOF = monoTsSOF + mono.mCaptureResult.get(
                        CaptureResult.SENSOR_EXPOSURE_TIME);


                Log.d(TAG,
                        "checkForValidFramePair - bayer ts SOF: "
                                + bayerTsSOF + ", EOF: " + bayerTsEOF
                                + ", mono ts SOF: " + monoTsSOF + ", EOF: " + monoTsEOF);
                Log.d(TAG,
                        "checkForValidFramePair - difference SOF: "
                                + Math.abs(bayerTsSOF - monoTsSOF)
                                + ", EOF: " + Math.abs(bayerTsEOF - monoTsEOF));
                // if timestamps are within threshold, keep frames
                if ((Math.abs(bayerTsSOF - monoTsSOF) > mTimestampThresholdNs) &&
                        (Math.abs(bayerTsEOF - monoTsEOF) > mTimestampThresholdNs)) {
                    if(bayerTsSOF > monoTsSOF) {
                        Log.d(TAG, "checkForValidFramePair - toss mono");
                        // no match, toss
                        mono = mMonoFrames.poll();
                        mono.mImage.close();
                    } else {
                        Log.d(TAG, "checkForValidFramePair - toss bayer");
                        // no match, toss
                        bayer = mBayerFrames.poll();
                        bayer.mImage.close();
                    }
                } else {
                    // send for reproc
                    sendReprocessRequest(CAM_TYPE_BAYER, mBayerFrames.poll());
                    sendReprocessRequest(CAM_TYPE_MONO, mMonoFrames.poll());
                    mReprocessingPairCount++;
                }
            }
        }

        private void sendReprocessRequest(final int camId, ReprocessableImage reprocImg) {
            CameraCaptureSession session = mCaptureSessions[camId];
            CameraDevice device = session.getDevice();

            try {
                Log.d(TAG, "sendReprocessRequest - cam: " + camId);
                CaptureRequest.Builder reprocRequest = device
                        .createReprocessCaptureRequest(reprocImg.mCaptureResult);
                reprocRequest.addTarget(mImageReader[camId]
                        .getSurface());
                reprocRequest.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                        CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                reprocRequest.set(CaptureRequest.EDGE_MODE,
                        CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                reprocRequest.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

                Long ts = Long.valueOf(reprocImg.mImage.getTimestamp());
                Integer hash = ts.hashCode();
                reprocRequest.setTag(hash);
                mReprocessingFrames[camId].put(hash, ts);
                Log.d(TAG, "sendReprocessRequest - adding reproc frame - hash: " + hash + ", ts: " + ts);

                mImageWriter[camId].queueInputImage(reprocImg.mImage);

                CaptureRequest request = reprocRequest.build();
                session.capture(request,
                        new CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Log.d(TAG, "reprocess - onCaptureCompleted: "
                                + camId);
                        Integer ts = (Integer)request.getTag();
                        obtainMessage(
                                MSG_NEW_REPROC_RESULT, camId, ts.intValue(), result)
                                .sendToTarget();
                    }

                    @Override
                    public void onCaptureFailed(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.d(TAG, "reprocess - onCaptureFailed: "
                                + camId);
                        Integer ts = (Integer)request.getTag();
                        obtainMessage(
                                MSG_NEW_REPROC_FAIL, camId, ts.intValue(), failure)
                                .sendToTarget();
                    }
                }, null);

                mReprocessingRequests.add(request);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void releaseBayerFrames() {
            for (ReprocessableImage reprocImg : mBayerFrames) {
                reprocImg.mImage.close();
            }

            mBayerFrames.clear();

            for (Image img : mBayerImages) {
                img.close();
            }

            mBayerImages.clear();
            mBayerCaptureResults.clear();
        }

        private void releaseMonoFrames() {
            for (ReprocessableImage reprocImg : mMonoFrames) {
                reprocImg.mImage.close();
            }

            mMonoFrames.clear();

            for (Image img : mMonoImages) {
                img.close();
            }

            mMonoImages.clear();
            mMonoCaptureResults.clear();
        }

        private void processFinalPair() {
            Log.d(TAG, "processFinalPair");
            releaseBayerFrames();
            releaseMonoFrames();

            removeMessages(MSG_NEW_CAPTURE_RESULT);
            removeMessages(MSG_NEW_CAPTURE_FAIL);

            mCaptureDone = true;

            if(mReprocessingPairCount == 0) {
                // No matching pairs = nothing registered, no need to reset engine
                Log.w(TAG, "processFinalPair - no matching pairs found");
                removeMessages(MSG_END_CAPTURE);
                if(mCallback != null) mCallback.onClearSightFailure(null);
            }
        }

        private void processNewReprocessImage(Message msg) {
            boolean isBayer = (msg.arg1 == CAM_TYPE_BAYER);

            Image image = (Image) msg.obj;
            long ts = image.getTimestamp();
            int camId = msg.arg1;
            Log.d(TAG, "processNewReprocessImage - cam: " + camId + ", ts: " + ts);
            int frameCount = isBayer?++mReprocessedBayerCount:++mReprocessedMonoCount;

            if(mDumpImages) {
                saveDebugImageAsJpeg(mMediaSaveService, image, isBayer, mNamedEntity,
                        frameCount, ts/1000000);
            }
            if(mDumpYUV) {
                saveDebugImageAsNV21(image, isBayer, mNamedEntity, frameCount, ts/1000000);
            }

            mClearsightRegisterHandler.obtainMessage(MSG_NEW_IMG,
                    msg.arg1, 0, msg.obj).sendToTarget();

            mReprocessingFrames[camId].removeAt(mReprocessingFrames[camId].indexOfValue(ts));
            checkReprocessDone();
        }

        private void processNewReprocessResult(Message msg) {
            Log.d(TAG, "processNewReprocessResult: " + msg.arg1);
            boolean isBayer = (msg.arg1 == CAM_TYPE_BAYER);
            TotalCaptureResult result = (TotalCaptureResult)msg.obj;
            mReprocessingRequests.remove(result.getRequest());

            if (ClearSightNativeEngine.getInstance()
                    .getReferenceResult(isBayer) == null) {
                // reference not yet set
                Log.d(TAG, "reprocess - setReferenceResult: " + msg.obj);
                ClearSightNativeEngine.getInstance().setReferenceResult(isBayer, result);
            }
            mFinishReprocessNum++;
            checkReprocessDone();
        }

        private void processNewReprocessFailure(Message msg) {
            int camId = msg.arg1;
            Log.d(TAG, "processNewReprocessFailure: " + camId);
            CaptureFailure failure = (CaptureFailure)msg.obj;
            mReprocessingRequests.remove(failure.getRequest());
            mReprocessingFrames[camId].delete(msg.arg2);
            mHasFailures = true;
            mFinishReprocessNum++;
            checkReprocessDone();
        }

        private void checkReprocessDone() {
            Log.d(TAG, "checkReprocessDone capture done: " + mCaptureDone +
                    ", reproc frames[bay]: " + mReprocessingFrames[CAM_TYPE_BAYER].size() +
                    ", reproc frames[mono]: " + mReprocessingFrames[CAM_TYPE_MONO].size() +
                    ", mReprocessingRequests: " + mReprocessingRequests.size());
            // If all burst frames and results have been processed
            if(mCaptureDone && mReprocessingFrames[CAM_TYPE_BAYER].size() == 0
                    && mReprocessingFrames[CAM_TYPE_MONO].size() == 0
                    && mReprocessingRequests.isEmpty()) {
                mClearsightRegisterHandler.obtainMessage(MSG_END_CAPTURE, mHasFailures?1:0, 0).sendToTarget();
                removeMessages(MSG_NEW_REPROC_RESULT);
                removeMessages(MSG_NEW_REPROC_FAIL);
                mCaptureDone = false;
                mHasFailures = false;

                // all burst and reproc frames processed.
                // remove timeout msg.
                removeMessages(MSG_END_CAPTURE);
            } else {
                kickTimeout();
            }
        }
    };

    private class ClearsightRegisterHandler extends Handler {
        private NamedEntity mNamedEntity;

        ClearsightRegisterHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if(isClosing()) return;

            switch (msg.what) {
            case MSG_START_CAPTURE:
                mNamedEntity = (NamedEntity) msg.obj;
                break;
            case MSG_NEW_IMG:
                registerImage(msg);
                break;
            case MSG_END_CAPTURE:
                // Check if timeout
                if(msg.arg2 == 1) {
                    Log.d(TAG, "ClearsightRegisterHandler - handleTimeout");
                    ClearSightNativeEngine.getInstance().reset();
                    if(mCallback != null) mCallback.onClearSightFailure(null);
                } else {
                    mClearsightProcessHandler.obtainMessage(MSG_START_CAPTURE,
                            msg.arg1, 0, mNamedEntity).sendToTarget();
                }
                break;
            }
        }

        private void registerImage(Message msg) {
            boolean isBayer = (msg.arg1 == CAM_TYPE_BAYER);
            Image image = (Image)msg.obj;

            if (!ClearSightNativeEngine.getInstance()
                    .hasReferenceImage(isBayer)) {
                // reference not yet set
                ClearSightNativeEngine.getInstance().setReferenceImage(isBayer, image);
            } else {
                // if ref images set, register this image
                if(ClearSightNativeEngine.getInstance().registerImage(
                        isBayer, image) == false) {
                    Log.w(TAG, "registerImage : terminal error with input image");
                }
            }
        }
    }

    private class ClearsightProcessHandler extends Handler {
        ClearsightProcessHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if(isClosing()) return;

            switch (msg.what) {
            case MSG_START_CAPTURE:
                processClearSight((NamedEntity) msg.obj);
                break;
            }
        }

        private void processClearSight(NamedEntity namedEntity) {
            mImageEncodeHandler.obtainMessage(MSG_START_CAPTURE).sendToTarget();

            short encodeRequest = 0;
            /* In same case, timeout will reset ClearSightNativeEngine object, so fields
               in the object is not initial, need to return and skip process.
            */
            if (ClearSightNativeEngine.getInstance().getReferenceImage(true) == null) {
                return;
            }
            long csTs = ClearSightNativeEngine.getInstance().getReferenceImage(true).getTimestamp();
            CaptureRequest.Builder csRequest = createEncodeReprocRequest(
                    ClearSightNativeEngine.getInstance().getReferenceResult(true), CAM_TYPE_BAYER);
            csRequest.setTag(new Object());

            boolean processInit = ClearSightNativeEngine.getInstance().initProcessImage();
            sendReferenceMonoEncodeRequest();
            sendReferenceBayerEncodeRequest();
            encodeRequest |= ImageEncodeHandler.MASK_BAYER_ENCODE|ImageEncodeHandler.MASK_MONO_ENCODE;
            ClearSightNativeEngine.getInstance().reset();

            if(processInit) {
                if(mCallback != null)
                    mCallback.onReleaseShutterLock();

                Image encodeImage = mImageWriter[CAM_TYPE_BAYER].dequeueInputImage();
                ClearSightNativeEngine.ClearsightImage csImage = new ClearsightImage(encodeImage);
                encodeImage.setTimestamp(csTs);

                if(ClearSightNativeEngine.getInstance().processImage(csImage)) {
                    encodeRequest |= ImageEncodeHandler.MASK_CS_ENCODE;
                    sendReprocessRequest(csRequest, encodeImage, CAM_TYPE_BAYER);
                } else {
                    csImage = null;
                    encodeImage.close();
                }
            }

            mImageEncodeHandler.obtainMessage(MSG_END_CAPTURE,
                    encodeRequest, 0, namedEntity).sendToTarget();
        }

        private void sendReferenceMonoEncodeRequest() {
            // First Mono
            CaptureRequest.Builder monoRequest = createEncodeReprocRequest(
                    ClearSightNativeEngine.getInstance().getReferenceResult(false), CAM_TYPE_MONO);
            sendReprocessRequest(monoRequest,
                    ClearSightNativeEngine.getInstance().getReferenceImage(false),
                    CAM_TYPE_MONO);
        }

        private void sendReferenceBayerEncodeRequest() {
            // bayer
            CaptureRequest.Builder bayerRequest = createEncodeReprocRequest(
                    ClearSightNativeEngine.getInstance().getReferenceResult(true), CAM_TYPE_BAYER);
            sendReprocessRequest(bayerRequest,
                    ClearSightNativeEngine.getInstance().getReferenceImage(true),
                    CAM_TYPE_BAYER);
        }

        private CaptureRequest.Builder createEncodeReprocRequest(TotalCaptureResult captureResult, int camType) {
            CaptureRequest.Builder reprocRequest = null;
            try {
                reprocRequest = mCaptureSessions[camType].getDevice()
                        .createReprocessCaptureRequest(captureResult);
                reprocRequest.addTarget(mEncodeImageReader[camType]
                        .getSurface());
                reprocRequest.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                        CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
                reprocRequest.set(CaptureRequest.EDGE_MODE,
                        CaptureRequest.EDGE_MODE_OFF);
                reprocRequest.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            return reprocRequest;
        }

        private void sendReprocessRequest(CaptureRequest.Builder reprocRequest, Image image, final int camType) {

            try {
                reprocRequest.set(CaptureModule.JpegCropEnableKey, (byte)1);

                Rect cropRect = image.getCropRect();
                if(cropRect == null ||
                        cropRect.isEmpty()) {
                    // if no crop rect set, init to default image width + height
                    cropRect = new Rect(0, 0, image.getWidth(), image.getHeight());
                }

                cropRect = getFinalCropRect(cropRect);
                // has crop rect. apply to jpeg request
                reprocRequest.set(CaptureModule.JpegCropRectKey,
                       new int[] {cropRect.left, cropRect.top, cropRect.width(), cropRect.height()});

                if(camType == CAM_TYPE_MONO) {
                    reprocRequest.set(CaptureModule.JpegRoiRectKey,
                           new int[] {0, 0, mFinalMonoSize.getWidth(), mFinalMonoSize.getHeight()});
                } else {
                    reprocRequest.set(CaptureModule.JpegRoiRectKey,
                            new int[] {0, 0, mFinalPictureSize.getWidth(), mFinalPictureSize.getHeight()});
                }

                mImageWriter[camType].queueInputImage(image);

                mCaptureSessions[camType].capture(reprocRequest.build(),
                        new CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Log.d(TAG, "encode - onCaptureCompleted: " + camType);
                        mImageEncodeHandler.obtainMessage(
                                MSG_NEW_CAPTURE_RESULT, camType, 0, result)
                                .sendToTarget();
                    }

                    @Override
                    public void onCaptureFailed(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.d(TAG, "encode - onCaptureFailed: " + camType);
                        mImageEncodeHandler.obtainMessage(
                                MSG_NEW_CAPTURE_FAIL, camType, 0, failure)
                                .sendToTarget();
                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e1) {
                e1.printStackTrace();
            }
        }
    }

    private class ImageEncodeHandler extends Handler {
        static final short MASK_CS_ENCODE = 0x01;
        static final short MASK_BAYER_ENCODE = 0x02;
        static final short MASK_MONO_ENCODE = 0x04;

        private short mEncodeRequest;
        private short mEncodeResults;
        private boolean mReadyToSave;
        private boolean mHasFailure;
        private Image mMonoImage;
        private Image mBayerImage;
        private Image mClearSightImage;
        private NamedEntity mNamedEntity;

        public ImageEncodeHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if(isClosing()) return;

            switch (msg.what) {
            case MSG_START_CAPTURE:
                Log.d(TAG, "ImageEncodeEvent - START_CAPTURE");
                resetParams();
                break;
            case MSG_END_CAPTURE:
                Log.d(TAG, "ImageEncodeEvent - END_CAPTURE");
                mNamedEntity = (NamedEntity) msg.obj;
                mEncodeRequest = (short)msg.arg1;
                mReadyToSave = true;
                saveMpoImage();
                break;
            case MSG_NEW_IMG:
            case MSG_NEW_CAPTURE_RESULT:
            case MSG_NEW_CAPTURE_FAIL:
                processNewEvent(msg);
                saveMpoImage();
                break;
            }
        }

        private void processNewEvent(Message msg) {
            if(msg.what == MSG_NEW_IMG) {
                Log.d(TAG, "processNewEncodeEvent - newImg: " + msg.arg1);
                if(msg.arg1 == CAM_TYPE_MONO) {
                    mMonoImage = (Image)msg.obj;
                    mEncodeResults |= MASK_MONO_ENCODE;
                } else if(mBayerImage == null){
                    mBayerImage = (Image)msg.obj;
                    mEncodeResults |= MASK_BAYER_ENCODE;
                } else {
                    mClearSightImage = (Image)msg.obj;
                    mEncodeResults |= MASK_CS_ENCODE;
                }
            } else if (msg.what == MSG_NEW_CAPTURE_RESULT) {
                Log.d(TAG, "processNewEncodeEvent - newResult: " + msg.arg1);
            } else {
                Log.d(TAG, "processNewEncodeEvent - newFailure: " + msg.arg1);
                mHasFailure = true;
                if(msg.arg1 == CAM_TYPE_MONO) {
                    mEncodeResults |= MASK_MONO_ENCODE;
                } else {
                    CaptureFailure failure = (CaptureFailure)msg.obj;
                    if(failure.getRequest().getTag() != null)
                        mEncodeResults |= MASK_CS_ENCODE;
                    else
                        mEncodeResults |= MASK_BAYER_ENCODE;
                }
            }
        }

        private void saveMpoImage() {
            if(!mReadyToSave || mEncodeRequest != mEncodeResults) {
                Log.d(TAG, "saveMpoImage - not yet ready to save");
                return;
            }

            Log.d(TAG, "saveMpoImage");
            if(mHasFailure) {
                // don't save anything and fail
                Log.d(TAG, "saveMpoImage has failure - aborting.");
                if(mCallback != null) mCallback.onClearSightFailure(null);
                resetParams();
                return;
            }

            String title = (mNamedEntity == null) ? null : mNamedEntity.title;
            long date = (mNamedEntity == null) ? -1 : mNamedEntity.date;
            int width = mBayerImage.getWidth();
            int height = mBayerImage.getHeight();

            if(mClearSightImage != null) {
                width = mClearSightImage.getWidth();
                height = mClearSightImage.getHeight();
            }

            byte[] clearSightBytes = getJpegData(mClearSightImage);
            byte[] bayerBytes = getJpegData(mBayerImage);
            byte[] monoBytes = getJpegData(mMonoImage);
            ExifInterface exif = Exif.getExif(bayerBytes);
            int orientation = Exif.getOrientation(exif);

            if(clearSightBytes != null) {
                if(mCallback != null) mCallback.onClearSightSuccess(clearSightBytes);
            } else if (bayerBytes != null) {
                if(mCallback != null) mCallback.onClearSightFailure(bayerBytes);
            } else {
                if(mCallback != null) mCallback.onClearSightFailure(null);
            }

            if(monoBytes == null) {
                mMediaSaveService.addImage(
                        clearSightBytes!=null?clearSightBytes:bayerBytes, title, date, null,
                        width, height, orientation, exif,
                        mMediaSavedListener,
                        mMediaSaveService.getContentResolver(), "jpeg");
            } else if (bayerBytes != null) {
                mMediaSaveService.addMpoImage(
                        clearSightBytes,
                        bayerBytes,
                        monoBytes, width, height, title,
                        date, null, orientation, mMediaSavedListener,
                        mMediaSaveService.getContentResolver(), "jpeg");
            }

            resetParams();
        }

        void resetParams() {
            if(mBayerImage != null) {
                mBayerImage.close();
                mBayerImage = null;
            }
            if(mMonoImage != null) {
                mMonoImage.close();
                mMonoImage = null;
            }
            if(mClearSightImage != null) {
                mClearSightImage.close();
                mClearSightImage = null;
            }
            mNamedEntity = null;
            mReadyToSave = false;
            mHasFailure = false;
            mEncodeRequest = 0;
            mEncodeResults = 0;
        }
    }

    public void saveDebugImageAsJpeg(MediaSaveService service, byte[] data,
            int width, int height, boolean isBayer, NamedEntity namedEntity, int count, long ts) {
        String type = isBayer?"b":"m";
        long date = (namedEntity == null) ? -1 : namedEntity.date;
        String title = String.format("%s_%s%02d_%d", namedEntity.title, type, count, ts);

        service.addImage(data, title, date, null,
                width, height, 0, null, null,
                service.getContentResolver(), "jpeg");
    }

    public void saveDebugImageAsJpeg(MediaSaveService service, YuvImage image, boolean isBayer,
            NamedEntity namedEntity, int count, long ts) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()), 100, baos);

        saveDebugImageAsJpeg(service, baos.toByteArray(), image.getWidth(), image.getHeight(),
                isBayer, namedEntity, count, ts);
    }

    public void saveDebugImageAsJpeg(MediaSaveService service, Image image, boolean isBayer,
            NamedEntity namedEntity, int count, long ts) {
        if(image.getFormat() == ImageFormat.YUV_420_888)
            saveDebugImageAsJpeg(service, createYuvImage(image), isBayer, namedEntity, count, ts);
        else if (image.getFormat() == ImageFormat.JPEG) {
            saveDebugImageAsJpeg(service, getJpegData(image), image.getWidth(), image.getHeight(),
                    isBayer, namedEntity, count, ts);
        }
    }

    public void saveDebugImageAsNV21(Image image, boolean isBayer, NamedEntity namedEntity, int count, long ts) {
        if(image.getFormat() != ImageFormat.YUV_420_888) {
            Log.d(TAG, "saveDebugImageAsNV21 - invalid param");
        }

        String type = isBayer?"b":"m";
        String title = String.format("%s_%dx%d_NV21_%s%02d_%d", namedEntity.title,
                image.getWidth(), image.getHeight(), type, count, ts);

        YuvImage yuv = createYuvImage(image);
        String path = Storage.generateFilepath(title, "yuv");
        Storage.writeFile(path, yuv.getYuvData(), null, "yuv");
    }

    public YuvImage createYuvImage(Image image) {
        if (image == null) {
            Log.d(TAG, "createYuvImage - invalid param");
            return null;
        }
        Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer vuBuffer = planes[2].getBuffer();
        int sizeY = yBuffer.capacity();
        int sizeVU = vuBuffer.capacity();
        byte[] data = new byte[sizeY + sizeVU];
        yBuffer.rewind();
        yBuffer.get(data, 0, sizeY);
        vuBuffer.rewind();
        vuBuffer.get(data, sizeY, sizeVU);
        int[] strides = new int[] { planes[0].getRowStride(),
                planes[2].getRowStride() };

        return new YuvImage(data, ImageFormat.NV21, image.getWidth(),
                image.getHeight(), strides);
    }

    public byte[] getJpegData(Image image) {
        if (image == null) {
            Log.d(TAG, "getJpegData - invalid param");
            return null;
        }
        Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int size = buffer.capacity();
        byte[] data = new byte[size];
        buffer.rewind();
        buffer.get(data, 0, size);

        return data;
    }

    private Size findMaxOutputSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        Arrays.sort(sizes, new CameraUtil.CompareSizesByArea());
        return sizes[sizes.length-1];
    }

    private Size getFinalMonoSize() {
        Size finalSize = null;
        long finalPicArea = mFinalPictureSize.getWidth() * mFinalPictureSize.getHeight();

        // if final pic size is less than 2MP, then use same MONO size
        if(finalPicArea > MIN_MONO_AREA) {
            for(Size size:MONO_SIZES) {
                float monoRatio = (float)size.getWidth() / (float)size.getHeight();
                if(monoRatio == mFinalPictureRatio) {
                    finalSize = size;
                    break;
                } else if (Math.abs(monoRatio - mFinalPictureRatio) < 0.1f) {
                    // close enough
                    int monoWidth = size.getWidth();
                    int monoHeight = size.getHeight();
                    if(monoRatio > mFinalPictureRatio) {
                        // keep width, increase height to match final ratio
                        // add .5 to round up if necessary
                        monoHeight = (int)(((float)monoWidth / mFinalPictureRatio) + .5f);
                    } else if(monoRatio < mFinalPictureRatio) {
                        // keep height, increase width to match final ratio
                        // add .5 to round up if necessary
                        monoWidth = (int)(((float)monoHeight * mFinalPictureRatio) + .5f);
                    }
                    finalSize = new Size(monoWidth, monoHeight);
                }
            }
        }

        if(finalSize == null) {
            // set to mFinalPictureSize if matching size not found
            // or if final resolution is less than 2 MP
            finalSize = mFinalPictureSize;
        }

        return finalSize;
    }

    private Rect getFinalCropRect(Rect rect) {
        Rect finalRect = new Rect(rect);
        float rectRatio = (float) rect.width()/(float) rect.height();

        // if ratios are different, adjust crop rect to fit ratio
        // if ratios are same, no need to adjust crop
        Log.d(TAG, "getFinalCropRect - rect: " + rect.toString());
        Log.d(TAG, "getFinalCropRect - ratios: " + rectRatio + ", " + mFinalPictureRatio);
        if(rectRatio > mFinalPictureRatio) {
            // ratio indicates need for horizontal crop
            // add .5 to round up if necessary
            int newWidth = (int)(((float)rect.height() * mFinalPictureRatio) + .5f);
            int newXoffset = (rect.width() - newWidth)/2 + rect.left;
            finalRect.left = newXoffset;
            finalRect.right = newXoffset + newWidth;
        } else if(rectRatio < mFinalPictureRatio) {
            // ratio indicates need for vertical crop
            // add .5 to round up if necessary
            int newHeight = (int)(((float)rect.width() / mFinalPictureRatio) + .5f);
            int newYoffset = (rect.height() - newHeight)/2 + rect.top;
            finalRect.top = newYoffset;
            finalRect.bottom = newYoffset + newHeight;
        }

        if (finalRect.width() % 2 != 0 || finalRect.height() % 2 != 0) {
            finalRect = new Rect(finalRect.left, finalRect.top,
                    finalRect.width() % 2 == 0 ? finalRect.right : finalRect.right + 1,
                    finalRect.height() % 2 == 0 ? finalRect.bottom : finalRect.bottom + 1);
        }
        Log.d(TAG, "getFinalCropRect - final rect: " + finalRect.toString());
        return finalRect;
    }
}
