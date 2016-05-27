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
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
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
import android.view.Surface;

import com.android.camera.MediaSaveService;
import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;

public class ClearSightImageProcessor {
    private static final String TAG = "ClearSightImageProcessor";
    private static final String PERSIST_TIMESTAMP_LIMIT_KEY = "persist.camera.cs.threshold";
    private static final String PERSIST_BURST_COUNT_KEY = "persist.camera.cs.burstcount";
    private static final String PERSIST_DUMP_FRAMES_KEY = "persist.camera.cs.dumpframes";

    private static final long DEFAULT_TIMESTAMP_THRESHOLD_MS = 10;
    private static final int DEFAULT_IMAGES_TO_BURST = 5;

    private static final int MSG_START_CAPTURE = 0;
    private static final int MSG_NEW_IMG = 1;
    private static final int MSG_NEW_RESULT = 2;

    private static final int CAM_TYPE_BAYER = 0;
    private static final int CAM_TYPE_MONO = 1;
    private static final int NUM_CAM = 2;

    private static CameraCharacteristics.Key<byte[]> OTP_CALIB_BLOB =
            new CameraCharacteristics.Key<>(
                    "org.codeaurora.qcamera3.dualcam_calib_meta_data.dualcam_calib_meta_data_blob",
                    byte[].class);

    private NamedImages mNamedImages;
    private ImageReader[] mImageReader = new ImageReader[NUM_CAM];
    private ImageReader[] mReprocessImageReader = new ImageReader[NUM_CAM];
    private ImageWriter[] mImageWriter = new ImageWriter[NUM_CAM];

    private ImageProcessHandler mImageProcessHandler;
    private ImageReprocessHandler mImageReprocessHandler;
    private HandlerThread mImageProcessThread;
    private HandlerThread mImageReprocessThread;
    private Callback mCallback;

    private long mTimestampThresholdNs;
    private int mNumBurstCount;
    private boolean mDumpImages;

    private static ClearSightImageProcessor mInstance;

    private ClearSightImageProcessor() {
        mNamedImages = new NamedImages();
        long threshMs = SystemProperties.getLong(PERSIST_TIMESTAMP_LIMIT_KEY, DEFAULT_TIMESTAMP_THRESHOLD_MS);
        mTimestampThresholdNs = threshMs * 1000000;
        Log.d(TAG, "mTimestampThresholdNs: " + mTimestampThresholdNs);

        mNumBurstCount = SystemProperties.getInt(PERSIST_BURST_COUNT_KEY, DEFAULT_IMAGES_TO_BURST);
        Log.d(TAG, "mNumBurstCount: " + mNumBurstCount);

        mDumpImages = SystemProperties.getBoolean(PERSIST_DUMP_FRAMES_KEY, false);
        Log.d(TAG, "mDumpImages: " + mDumpImages);
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

    public void init(int width, int height, Context context) {
        mImageProcessThread = new HandlerThread("CameraImageProcess");
        mImageProcessThread.start();
        mImageReprocessThread = new HandlerThread("CameraImageReprocess");
        mImageReprocessThread.start();

        mImageProcessHandler = new ImageProcessHandler(mImageProcessThread.getLooper());
        mImageReprocessHandler = new ImageReprocessHandler(mImageReprocessThread.getLooper());

        mImageReader[CAM_TYPE_BAYER] = createImageReader(CAM_TYPE_BAYER, width, height);
        mImageReader[CAM_TYPE_MONO] = createImageReader(CAM_TYPE_MONO, width, height);
        mReprocessImageReader[CAM_TYPE_BAYER] = createReprocImageReader(CAM_TYPE_BAYER, width, height);
        mReprocessImageReader[CAM_TYPE_MONO] = createReprocImageReader(CAM_TYPE_MONO, width, height);

        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cc = cm.getCameraCharacteristics("0");
            byte[] blob = cc.get(OTP_CALIB_BLOB);
            ClearSightNativeEngine.setOtpCalibData(CamSystemCalibrationData.createFromBytes(blob));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        for(int i=0; i<mImageReader.length; i++) {
            if (null != mImageReader[i]) {
                mImageReader[i].close();
                mImageReader[i] = null;
            }
            if (null != mReprocessImageReader[i]) {
                mReprocessImageReader[i].close();
                mReprocessImageReader[i] = null;
            }
            if (null != mImageWriter[i]) {
                mImageWriter[i].close();
                mImageWriter[i] = null;
            }
        }

        if(mImageProcessThread != null) {
            mImageProcessThread.quitSafely();

            try {
                mImageProcessThread.join();
                mImageProcessThread = null;
                mImageProcessHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(mImageReprocessThread != null) {
            mImageReprocessThread.quitSafely();

            try {
                mImageReprocessThread.join();
                mImageReprocessThread = null;
                mImageReprocessHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void createCaptureSession(boolean bayer, CameraDevice device, List<Surface> surfaces,
            CameraCaptureSession.StateCallback captureSessionCallback) throws CameraAccessException {

        Log.d(TAG, "createCaptureSession: " + bayer);

        int cam = bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO;
        surfaces.add(mImageReader[cam].getSurface());
        surfaces.add(mReprocessImageReader[cam].getSurface());
        // Here, we create a CameraCaptureSession for camera preview.
        device.createReprocessableCaptureSession(
                new InputConfiguration(mImageReader[cam].getWidth(),
                        mImageReader[cam].getHeight(), mImageReader[cam].getImageFormat()),
                        surfaces, captureSessionCallback, null);
    }

    public void onCaptureSessionConfigured(boolean bayer, CameraCaptureSession session) {
        Log.d(TAG, "onCaptureSessionConfigured: " + bayer);

        mImageWriter[bayer?CAM_TYPE_BAYER:CAM_TYPE_MONO] =
                ImageWriter.newInstance(session.getInputSurface(), mNumBurstCount);
    }

    public CaptureRequest.Builder createCaptureRequest(CameraDevice device) throws CameraAccessException {
        Log.d(TAG, "createCaptureRequest");

        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        // Orientation
        // int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        // captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
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
                Log.d(TAG, "captureStillPicture onCaptureCompleted: " + cam);
                mImageProcessHandler.obtainMessage(MSG_NEW_RESULT,
                        cam, 0, result).sendToTarget();
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session,
                    CaptureRequest request,
                    CaptureFailure result) {
                Log.d(TAG, "captureStillPicture onCaptureFailed: " + cam);
                mImageProcessHandler.obtainMessage(MSG_NEW_RESULT,
                        cam, 1, result).sendToTarget();
            }

            @Override
            public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                    sequenceId, long frameNumber) {
                Log.d(TAG, "captureStillPicture onCaptureSequenceCompleted: " + cam);
            }
        };

        List<CaptureRequest> burstList = new ArrayList<CaptureRequest>();
        requestBuilder.addTarget(mImageReader[cam].getSurface());
        for (int i = 0; i < mNumBurstCount; i++) {
            requestBuilder.setTag(new Object());
            CaptureRequest request = requestBuilder.build();
            burstList.add(request);
        }

        mImageProcessHandler.obtainMessage(MSG_START_CAPTURE, cam, burstList.size()).sendToTarget();
        session.captureBurst(burstList, captureCallback, captureCallbackHandler);
    }

    private ImageReader createImageReader(final int cam, int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height,
                ImageFormat.YUV_420_888, mNumBurstCount);
        reader.setOnImageAvailableListener(new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "image available for cam: " + cam);
                mImageProcessHandler.obtainMessage(
                        MSG_NEW_IMG, cam, 0, reader.acquireNextImage()).sendToTarget();
            }
        }, null);

        return reader;
    }

    private ImageReader createReprocImageReader(final int cam, int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height,
                ImageFormat.YUV_420_888, mNumBurstCount);
        reader.setOnImageAvailableListener(new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "reprocessed image available for cam: " + cam);
                mImageReprocessHandler.obtainMessage(
                        MSG_NEW_IMG, cam, 0, reader.acquireNextImage()).sendToTarget();
            }
        }, null);

        return reader;
    }

    public interface Callback {
        public void onClearSightSuccess(ClearsightImage csImage, YuvImage bayer, YuvImage mono);
        public void onClearSightFailure(YuvImage bayer, YuvImage mono);
        public CameraCaptureSession onReprocess(boolean bayer);
        public MediaSaveService getMediaSaveService();
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
        private int[] mNumImagesToProcess = new int[NUM_CAM];

        public ImageProcessHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_START_CAPTURE:
                mNumImagesToProcess[msg.arg1] = msg.arg2;
                break;
            case MSG_NEW_IMG:
            case MSG_NEW_RESULT:
                processNewEvent(msg);
                break;
            }
        }

        private void processNewEvent(Message msg) {
            ArrayDeque<Image> imageQueue;
            ArrayDeque<TotalCaptureResult> resultQueue;
            ArrayDeque<ReprocessableImage> reprocQueue;
            // push image onto queue
            if (msg.arg1 == CAM_TYPE_BAYER) {
                imageQueue = mBayerImages;
                resultQueue = mBayerCaptureResults;
                reprocQueue = mBayerFrames;
            } else {
                imageQueue = mMonoImages;
                resultQueue = mMonoCaptureResults;
                reprocQueue = mMonoFrames;
            }

            if(msg.what == MSG_NEW_IMG) {
                Log.d(TAG, "processNewEvent - newImg: " + msg.arg1);
                Image image = (Image) msg.obj;
                imageQueue.add(image);
            } else if(msg.arg2 == 1) {
                Log.d(TAG, "processNewEvent - new failed result: " + msg.arg1);
                mNumImagesToProcess[msg.arg1]--;
            } else {
                Log.d(TAG, "processNewEvent - newResult: " + msg.arg1);
                TotalCaptureResult result = (TotalCaptureResult) msg.obj;
                resultQueue.add(result);
            }

            Log.d(TAG, "processNewEvent - cam: " + msg.arg1 + " num imgs: "
                    + imageQueue.size() + " num results: " + resultQueue.size());

            if (!imageQueue.isEmpty() && !resultQueue.isEmpty()) {
                Image headImage = imageQueue.poll();
                TotalCaptureResult headResult = resultQueue.poll();
                reprocQueue.add(new ReprocessableImage(headImage, headResult));
                mNumImagesToProcess[msg.arg1]--;
                checkForValidFramePair();
            }

            Log.d(TAG, "processNewEvent - imagestoprocess[bayer] " + mNumImagesToProcess[CAM_TYPE_BAYER] +
                    " imagestoprocess[mono]: " + mNumImagesToProcess[CAM_TYPE_MONO]);

            if (mNumImagesToProcess[CAM_TYPE_BAYER] == 0
                    && mNumImagesToProcess[CAM_TYPE_MONO] == 0) {
                processReprocess();
            }
        }

        private void checkForValidFramePair() {
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

                Log.d(TAG,
                        "checkForValidFramePair - bayer ts: "
                                + bayer.mImage.getTimestamp() + " mono ts: "
                                + mono.mImage.getTimestamp());
                Log.d(TAG,
                        "checkForValidFramePair - difference: "
                                + Math.abs(bayer.mImage.getTimestamp()
                                        - mono.mImage.getTimestamp()));
                // if timestamps are within threshold, keep frames
                if (Math.abs(bayer.mImage.getTimestamp()
                        - mono.mImage.getTimestamp()) > mTimestampThresholdNs) {
                    if(bayer.mImage.getTimestamp() > mono.mImage.getTimestamp()) {
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
                }
            }
        }

        private void releaseBayerFrames() {
            for (ReprocessableImage reprocImg : mBayerFrames) {
                reprocImg.mImage.close();
            }

            mBayerFrames.clear();
        }

        private void releaseMonoFrames() {
            for (ReprocessableImage reprocImg : mMonoFrames) {
                reprocImg.mImage.close();
            }

            mMonoFrames.clear();
        }

        private void processReprocess() {
            if(mCallback != null) {
                if (mMonoFrames.isEmpty()
                        || mBayerFrames.isEmpty()) {
                    Log.d(TAG, "processReprocess - frames are empty");
                    releaseBayerFrames();
                    releaseMonoFrames();
                    mCallback.onClearSightFailure(null, null);
                    return;
                } else {
                    int frameCount = Math.min(mMonoFrames.size(), mBayerFrames.size());
                    sendReprocessRequests(CAM_TYPE_BAYER, frameCount);
                    sendReprocessRequests(CAM_TYPE_MONO, frameCount);
                }
            } else {
                releaseBayerFrames();
                releaseMonoFrames();
            }
        }

        private void sendReprocessRequests(final int camId, int frameCount) {
            CameraCaptureSession session = mCallback.onReprocess(camId == CAM_TYPE_BAYER);
            CameraDevice device = session.getDevice();

            try {
                ArrayDeque<ReprocessableImage> frameQueue;
                if (camId == CAM_TYPE_BAYER) {
                    frameQueue = mBayerFrames;
                } else {
                    frameQueue = mMonoFrames;
                }
                Log.d(TAG, "sendReprocessRequests - start cam: " + camId
                        + " num frames: " + frameQueue.size()
                        + " frameCount: " + frameCount);

                ArrayList<CaptureRequest> reprocRequests = new ArrayList<CaptureRequest>(
                        frameQueue.size());
                while (reprocRequests.size() < frameCount) {
                    ReprocessableImage reprocImg = frameQueue.poll();

                    CaptureRequest.Builder reprocRequest = device
                            .createReprocessCaptureRequest(reprocImg.mCaptureResult);
                    reprocRequest.addTarget(mReprocessImageReader[camId]
                            .getSurface());
                    reprocRequests.add(reprocRequest.build());

                    mImageWriter[camId].queueInputImage(reprocImg.mImage);
                }

                if(!frameQueue.isEmpty()) {
                    // clear remaining frames
                    if (camId == CAM_TYPE_BAYER) {
                        releaseBayerFrames();
                    } else {
                        releaseMonoFrames();
                    }
                }

                mImageReprocessHandler.obtainMessage(MSG_START_CAPTURE, camId,
                        reprocRequests.size()).sendToTarget();
                session.captureBurst(reprocRequests,
                        new CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Log.d(TAG, "reprocess - onCaptureCompleted: "
                                + camId);
                    }

                    @Override
                    public void onCaptureFailed(
                            CameraCaptureSession session,
                            CaptureRequest request,
                            CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.d(TAG, "reprocess - onCaptureFailed: "
                                + camId);
                        mImageReprocessHandler.obtainMessage(
                                MSG_NEW_RESULT, camId, 1)
                                .sendToTarget();
                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    };

    private class ImageReprocessHandler extends Handler {
        private int[] mNumImagesToProcess = new int[NUM_CAM];

        public ImageReprocessHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_START_CAPTURE:
                mNumImagesToProcess[msg.arg1] = msg.arg2;
                break;
            case MSG_NEW_IMG:
            case MSG_NEW_RESULT:
                processNewEvent(msg);
                break;
            }
        }

        private void processNewEvent(Message msg) {
            boolean isBayer = (msg.arg1 == CAM_TYPE_BAYER);

            if(msg.what == MSG_NEW_IMG) {
                Image image = (Image) msg.obj;
                Log.d(TAG, "reprocess - processNewImg");

                if(mDumpImages) {
                    saveDebugImage(mCallback.getMediaSaveService(), image, true);
                }

                if (!ClearSightNativeEngine.getInstance()
                        .hasReferenceImage(isBayer)) {
                    // reference not yet set
                    ClearSightNativeEngine.getInstance().setReferenceImage(isBayer,
                            image);
                } else {
                    // if ref images set, register this image
                    if(ClearSightNativeEngine.getInstance().registerImage(
                            isBayer, image) == false) {
                        Log.w(TAG, "registerImage : terminal error with input image");
                    }
                }
                mNumImagesToProcess[msg.arg1]--;
            } else if (msg.arg2 == 1) {
                // capture failed
                mNumImagesToProcess[msg.arg1]--;
            }

            Log.d(TAG, "reprocess - processNewEvent, cam: " + msg.arg1
                    + " count: " + mNumImagesToProcess[msg.arg1]);

            if (mNumImagesToProcess[CAM_TYPE_BAYER] == 0
                    && mNumImagesToProcess[CAM_TYPE_MONO] == 0) {
                processClearSight();
            }
        }

        private void processClearSight() {
            Log.d(TAG, "reprocess - processClearSight, bayercount: "
                    + mNumImagesToProcess[CAM_TYPE_BAYER] + " mono count: "
                    + mNumImagesToProcess[CAM_TYPE_MONO]);

            if(mCallback != null) {
                ClearSightNativeEngine.ClearsightImage csImage = ClearSightNativeEngine
                        .getInstance().processImage();

                if(csImage != null) {
                    Log.d(TAG, "reprocess - processClearSight, roiRect: "
                            + csImage.getRoiRect().toString());
                    mCallback.onClearSightSuccess(csImage,
                            createYuvImage(ClearSightNativeEngine.getInstance().getReferenceImage(true)),
                            createYuvImage(ClearSightNativeEngine.getInstance().getReferenceImage(false)));
                } else {
                    mCallback.onClearSightFailure(
                            createYuvImage(ClearSightNativeEngine.getInstance().getReferenceImage(true)),
                            createYuvImage(ClearSightNativeEngine.getInstance().getReferenceImage(false)));
                }
            }
            ClearSightNativeEngine.getInstance().reset();
        }
    };

    public void saveDebugImage(MediaSaveService service, byte[] data,
            int width, int height, boolean isReproc) {
        mNamedImages.nameNewImage(System.currentTimeMillis());
        NamedEntity name = mNamedImages.getNextNameEntity();
        String title = (name == null) ? null : name.title;
        long date = (name == null) ? -1 : name.date;

        if (isReproc) {
            title += "_reproc";
        }

        service.addImage(data, title, date, null,
                width, height, 0, null, null,
                service.getContentResolver(), "jpeg");
    }

    public void saveDebugImage(MediaSaveService service, YuvImage image, boolean isReproc) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()), 100, baos);

        saveDebugImage(service, baos.toByteArray(), image.getWidth(), image.getHeight(),
                isReproc);
    }

    public void saveDebugImage(MediaSaveService service, Image image, boolean isReproc) {
        saveDebugImage(service, createYuvImage(image), isReproc);
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
}
