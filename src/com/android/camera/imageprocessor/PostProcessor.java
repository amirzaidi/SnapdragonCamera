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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.Exif;
import com.android.camera.MediaSaveService;
import com.android.camera.PhotoModule;
import com.android.camera.SettingsManager;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.Rational;
import com.android.camera.imageprocessor.filter.BestpictureFilter;
import com.android.camera.imageprocessor.filter.ChromaflashFilter;
import com.android.camera.imageprocessor.filter.OptizoomFilter;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.StillmoreFilter;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.ui.RotateTextToast;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.util.CameraUtil;
import android.util.Size;

public class PostProcessor{

    private CaptureModule mController;

    private static final String TAG = "PostProcessor";
    public static final int FILTER_NONE = 0;
    public static final int FILTER_OPTIZOOM = 1;
    public static final int FILTER_SHARPSHOOTER = 2;
    public static final int FILTER_UBIFOCUS = 3;
    public static final int FILTER_STILLMORE = 4;
    public static final int FILTER_BESTPICTURE = 5;
    public static final int FILTER_CHROMAFLASH = 6;
    public static final int FILTER_MAX = 7;

    //BestPicture requires 10 which is the biggest among filters
    public static final int MAX_REQUIRED_IMAGE_NUM = 11;
    private int mCurrentNumImage = 0;
    private ImageFilter mFilter;
    private int mFilterIndex;
    private HandlerThread mHandlerThread;
    private ProcessorHandler mHandler;
    private CameraActivity mActivity;
    private int mWidth;
    private int mHeight;
    private int mStride;
    private Object lock = new Object();
    private ImageFilter.ResultImage mDefaultResultImage;  //This is used only no filter is chosen.
    private Image[] mImages;
    private PhotoModule.NamedImages mNamedImages;
    private WatchdogThread mWatchdog;
    private int mOrientation = 0;
    private ImageWriter mImageWriter;

    //This is for the debug feature.
    private static boolean DEBUG_FILTER = false;
    private static boolean DEBUG_ZSL = true;
    private ImageFilter.ResultImage mDebugResultImage;
    private ZSLQueue mZSLQueue;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;
    private ImageReader mZSLReprocessImageReader;
    private boolean mUseZSL = true;
    private Handler mZSLHandler;
    private HandlerThread mZSLHandlerThread;
    private ImageHandlerTask mImageHandlerTask;
    private LinkedList<TotalCaptureResult> mTotalCaptureResultList = new LinkedList<TotalCaptureResult>();

    public boolean isZSLEnabled() {
        return mUseZSL;
    }

    public void onStartCapturing() {
        mTotalCaptureResultList.clear();
    }

    public ImageReader getZSLReprocessImageReader() {
        return mZSLReprocessImageReader;
    }

    public ImageHandlerTask getImageHandler() {
        return mImageHandlerTask;
    }

    private class ImageWrapper {
        Image mImage;
        boolean mIsTaken;

        public ImageWrapper(Image image) {
            mImage = image;
            mIsTaken = false;
        }

        public boolean isTaken() {
            return mIsTaken;
        }

        public Image getImage() {
            mIsTaken = true;
            return mImage;
        }
    }

    class ImageHandlerTask implements Runnable, ImageReader.OnImageAvailableListener {
        private ImageWrapper mImageWrapper = null;
        Semaphore mMutureLock = new Semaphore(1);

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                if(mUseZSL) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }
                    if (!mMutureLock.tryAcquire()) {
                        image.close();
                        return;
                    }
                    if (mImageWrapper == null || mImageWrapper.isTaken()) {
                        mImageWrapper = new ImageWrapper(image);
                        mMutureLock.release();
                    } else {
                        image.close();
                        mMutureLock.release();
                        return;
                    }
                    if (mZSLHandler != null) {
                        mZSLHandler.post(this);
                    }
                } else { //Non ZSL case
                    Image image = reader.acquireNextImage();
                    if(image != null) {
                        onImageToProcess(image);
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Max images has been already acquired. ");
            }
        }

        @Override
        public void run() {   //Only ZSL case
           Image image = mImageWrapper.getImage();
            try {
                mMutureLock.acquire();
                if (mUseZSL) {
                    if (mZSLQueue != null) {
                        mZSLQueue.add(image);
                    }
                }
                mMutureLock.release();
            } catch (InterruptedException e) {
            }
        }
    }

    public void onMetaAvailable(TotalCaptureResult metadata) {
        if(mUseZSL && mZSLQueue != null) {
            mZSLQueue.add(metadata);
        }
    }

    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            if(mTotalCaptureResultList.size() <= PostProcessor.MAX_REQUIRED_IMAGE_NUM) {
                mTotalCaptureResultList.add(result);
            }
            onMetaAvailable(result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session,
                                    CaptureRequest request,
                                    CaptureFailure result) {
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                sequenceId, long frameNumber) {
            mController.unlockFocus(mController.getMainCameraId());
        }
    };

    public CameraCaptureSession.CaptureCallback getCaptureCallback() {
        return mCaptureCallback;
    }

    public void onSessionConfigured(CameraDevice cameraDevice, CameraCaptureSession captureSession) {
        mCameraDevice = cameraDevice;
        mCaptureSession = captureSession;
        if(mUseZSL) {
            mImageWriter = ImageWriter.newInstance(captureSession.getInputSurface(), 2);
        }
    }

    public void onImageReaderReady(ImageReader imageReader, Size maxSize, Size pictureSize) {
        mImageReader = imageReader;
        if(mUseZSL) {
            mZSLReprocessImageReader = ImageReader.newInstance(pictureSize.getWidth(), pictureSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mZSLReprocessImageReader.setOnImageAvailableListener(processedImageAvailableListener, mHandler);
        }
    }

    public boolean takeZSLPicture() {
        ZSLQueue.ImageItem imageItem = mZSLQueue.tryToGetMatchingItem();
        if(mController.getPreviewCaptureResult() == null ||
                mController.getPreviewCaptureResult().get(CaptureResult.CONTROL_AE_STATE) == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED) {
            if(DEBUG_ZSL) Log.d(TAG, "Flash required image");
            imageItem = null;
        }
        if (mController.isSelfieFlash()) {
            imageItem = null;
        }
        if (imageItem != null) {
            if(DEBUG_ZSL) Log.d(TAG,"Got the item from the queue");
            reprocessImage(imageItem.getImage(), imageItem.getMetadata());
            return true;
        } else {
            if(DEBUG_ZSL) Log.d(TAG, "No good item in queue, register the request for the future");
            mZSLQueue.addPictureRequest();
            return false;
        }
    }

    public void onMatchingZSLPictureAvailable(ZSLQueue.ImageItem imageItem) {
        reprocessImage(imageItem.getImage(), imageItem.getMetadata());
    }

    private void reprocessImage(Image image, TotalCaptureResult metadata) {
        if(mCameraDevice == null || mCaptureSession == null || mImageReader == null) {
            Log.e(TAG, "Reprocess request is called even before taking picture");
            new Throwable().printStackTrace();
            return;
        }
        Log.d(TAG, "reprocess Image request");
        CaptureRequest.Builder builder = null;
        try {
            builder = mCameraDevice.createReprocessCaptureRequest(metadata);
            builder.addTarget(mZSLReprocessImageReader.getSurface());
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            try {
                mImageWriter.queueInputImage(image);
            } catch(IllegalStateException e) {
                Log.e(TAG, "Queueing more than it can have");
            }
            mCaptureSession.capture(builder.build(), mCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
        }
    }

    private void onImageToProcess(Image image) {
        addImage(image);
        if (isReadyToProcess()) {
            long captureStartTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(captureStartTime);
            PhotoModule.NamedImages.NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;
            processImage(title, date, mController.getMediaSavedListener(), mActivity.getContentResolver());
        }
    }

    enum STATUS {
        DEINIT,
        INIT,
        BUSY
    }
    private STATUS mStatus = STATUS.DEINIT;

    public PostProcessor(CameraActivity activity, CaptureModule module) {
        mController = module;
        mActivity = activity;
        mNamedImages = new PhotoModule.NamedImages();
    }

    public boolean isItBusy() {
        if(mStatus == STATUS.BUSY)
            return true;
        return false;
    }

    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        if(mFilter == null) {
            List<CaptureRequest> list = new ArrayList<CaptureRequest>();
            list.add(builder.build());
            return list;
        } else {
            return mFilter.setRequiredImages(builder);
        }
    }

    public boolean isManualMode() {
        return mFilter.isManualMode();
    }

    public void manualCapture(final CaptureRequest.Builder builder, final CameraCaptureSession captureSession,
                              final Handler handler) throws CameraAccessException{
        mHandler.post(new Runnable() {
            public void run() {
                try {
                    mFilter.manualCapture(builder, captureSession, mCaptureCallback, handler);
                } catch(IllegalStateException e) {
                    Log.w(TAG, "Session is closed while taking manual pictures ");
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public boolean isFilterOn() {
        if (mFilter != null) {
            return true;
        }
        return false;
    }

    public boolean isSelfieMirrorOn() {
        if (SettingsManager.getInstance() != null &&
                SettingsManager.getInstance().getValue(SettingsManager.KEY_SELFIEMIRROR) != null &&
                SettingsManager.getInstance().getValue(SettingsManager.KEY_SELFIEMIRROR).equalsIgnoreCase("on")) {
            return true;
        }
        return false;
    }

    public void onOpen(int postFilterId, boolean isLongShotOn, boolean isFlashModeOn, boolean isTrackingFocusOn) {
        mImageHandlerTask = new ImageHandlerTask();

        if(setFilter(postFilterId) || isLongShotOn || isFlashModeOn || isTrackingFocusOn) {
            mUseZSL = false;
        } else {
            mUseZSL = true;
        }
        Log.d(TAG,"ZSL is "+mUseZSL);
        startBackgroundThread();
        if(mUseZSL) {
            mZSLQueue = new ZSLQueue(mController);
        }
    }

    public int getFilterIndex() {
        return mFilterIndex;
    }

    public void onClose() {
        synchronized (lock) {
            if(mHandler != null) {
                mHandler.setInActive();
            }
            stopBackgroundThread();
        }
        setFilter(FILTER_NONE);
        if(mZSLQueue != null) {
            mZSLQueue.onClose();
            mZSLQueue = null;
        }
        if (mImageWriter != null) {
            mImageWriter.close();
            mImageWriter = null;
        }
        if(mZSLReprocessImageReader != null) {
            mZSLReprocessImageReader.close();
            mZSLReprocessImageReader = null;
        }
        mCameraDevice = null;
        mCaptureSession = null;
        mImageReader = null;
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread("PostProcessorThread");
        mHandlerThread.start();
        mHandler = new ProcessorHandler(mHandlerThread.getLooper());

        mZSLHandlerThread = new HandlerThread("ZSLHandlerThread");
        mZSLHandlerThread.start();
        mZSLHandler = new ProcessorHandler(mZSLHandlerThread.getLooper());

        mWatchdog = new WatchdogThread();
        mWatchdog.start();
    }

    class WatchdogThread extends Thread {
        private boolean isAlive = true;
        private boolean isMonitor = false;
        private int counter = 0;
        public void run() {
            while(isAlive) {
                try {
                    Thread.sleep(200);
                }catch(InterruptedException e) {
                }
                if(isMonitor) {
                    counter++;
                    if(counter >= 100) { //This is 20 seconds.
                        bark();
                        break;
                    }
                }
            }
        }

        public void startMonitor() {
            isMonitor = true;
        }

        public void stopMonitor() {
            isMonitor = false;
            counter = 0;
        }

        public void kill() {
            isAlive = false;
        }
        private void bark() {
            Log.e(TAG, "It takes too long to get the images and process the filter!");
            int index = getFilterIndex();
            setFilter(FILTER_NONE);
            setFilter(index);
        }
    }

    class ProcessorHandler extends Handler {
        boolean isRunning;

        public ProcessorHandler(Looper looper) {
            super(looper);
            isRunning = true;
        }

        public void setInActive() {
            isRunning = false;
        }
    }

    private void stopBackgroundThread() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
            } catch (InterruptedException e) {
            }
            mHandlerThread = null;
            mHandler = null;
        }
        if (mZSLHandlerThread != null) {
            mZSLHandlerThread.quitSafely();
            try {
                mZSLHandlerThread.join();
            } catch (InterruptedException e) {
            }
            mZSLHandlerThread = null;
            mZSLHandler = null;
        }
        if(mWatchdog != null) {
            mWatchdog.kill();
            mWatchdog = null;
        }
        clear();
    }

    public boolean setFilter(int index) {
        if(index < 0 || index >= FILTER_MAX) {
            Log.e(TAG, "Invalid scene filter ID");
            return false;
        }
        synchronized (lock) {
            if (mFilter != null) {
                mFilter.deinit();
            }
            mStatus = STATUS.DEINIT;
            switch (index) {
                case FILTER_NONE:
                    mFilter = null;
                    break;
                case FILTER_OPTIZOOM:
                    mFilter = new OptizoomFilter(mController);
                    break;
                case FILTER_SHARPSHOOTER:
                    mFilter = new SharpshooterFilter(mController);
                    break;
                case FILTER_UBIFOCUS:
                    mFilter = new UbifocusFilter(mController, mActivity, this);
                    break;
                case FILTER_STILLMORE:
                    mFilter = new StillmoreFilter(mController);
                    break;
                case FILTER_BESTPICTURE:
                    mFilter = new BestpictureFilter(mController, mActivity, this);
                    break;
                case FILTER_CHROMAFLASH:
                    mFilter = new ChromaflashFilter(mController);
                    break;
            }
        }

        if(mFilter != null && !mFilter.isSupported()) {
            final String filterName = mFilter.getStringName();
            mFilter = null;
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    RotateTextToast.makeText(mActivity, filterName+" is not supported. ", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if(mFilter == null) {
            mFilterIndex = FILTER_NONE;
            return false;
        }
        mFilterIndex = index;
        mImages = new Image[mFilter.getNumRequiredImage()];
        return true;
    }

    private boolean isReadyToProcess() {
        synchronized (lock) {
            if (mFilter == null) {
                return true;
            }
            if (mCurrentNumImage >= mFilter.getNumRequiredImage()) {
                return true;
            }
        }
        return false;
    }

    private void addImage(final Image image) {
        if(mHandler == null || !mHandler.isRunning) {
            return;
        }
        final ProcessorHandler handler = mHandler;
        if (mStatus == STATUS.DEINIT) {
            mWidth = image.getWidth();
            mHeight = image.getHeight();
            mStride = image.getPlanes()[0].getRowStride();
            mStatus = STATUS.INIT;
            mHandler.post(new Runnable() {
                    public void run() {
                        synchronized (lock) {
                            if(!handler.isRunning) {
                                return;
                            }
                            if(mFilter == null) {
                                //Nothing here we have to do if filter is not chosen.
                            } else {
                                mFilter.init(mWidth, mHeight, mStride, mStride);
                            }
                        }
                    }
                });
        }
        if(mCurrentNumImage == 0) {
            mStatus = STATUS.BUSY;
            if(mWatchdog != null) {
                mWatchdog.startMonitor();
            }
            mOrientation = CameraUtil.getJpegRotation(mController.getMainCameraId(), mController.getDisplayOrientation());
        }
        if(mFilter != null && mCurrentNumImage >= mFilter.getNumRequiredImage()) {
            return;
        }
        final int numImage = mCurrentNumImage;
        mCurrentNumImage++;
        if(mHandler == null) {
            return;
        }
        mHandler.post(new Runnable() {
                public void run() {
                    synchronized (lock) {
                        if(!handler.isRunning || mStatus != STATUS.BUSY) {
                            return;
                        }
                        ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
                        ByteBuffer vuBuf = image.getPlanes()[2].getBuffer();
                        if(mFilter != null && DEBUG_FILTER && numImage == 0) {
                            mDebugResultImage = new ImageFilter.ResultImage(ByteBuffer.allocateDirect(mStride * mHeight*3/2),
                                    new Rect(0, 0, mWidth, mHeight), mWidth, mHeight, mStride);
                            yBuf.get(mDebugResultImage.outBuffer.array(), 0, yBuf.remaining());
                            vuBuf.get(mDebugResultImage.outBuffer.array(), mStride * mHeight, vuBuf.remaining());
                            yBuf.rewind();
                            vuBuf.rewind();
                        }
                        if(mFilter == null) {
                            mDefaultResultImage = new ImageFilter.ResultImage(ByteBuffer.allocateDirect(mStride * mHeight*3/2),
                                                                    new Rect(0, 0, mWidth, mHeight), mWidth, mHeight, mStride);
                            yBuf.get(mDefaultResultImage.outBuffer.array(), 0, yBuf.remaining());
                            vuBuf.get(mDefaultResultImage.outBuffer.array(), mStride*mHeight, vuBuf.remaining());
                            image.close();
                        } else {
                            mFilter.addImage(image.getPlanes()[0].getBuffer(),
                                    image.getPlanes()[2].getBuffer(), numImage, null);
                            mImages[numImage] = image;
                        }
                    }
                }
            });
    }

    public static byte[] addExifTags(byte[] jpeg, int orientationInDegree, TotalCaptureResult result) {
        ExifInterface exif = new ExifInterface();
        exif.addMakeAndModelTag();
        exif.addOrientationTag(orientationInDegree);
        exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, System.currentTimeMillis(),
                TimeZone.getDefault());
        if(result != null) {
            if(result.get(CaptureResult.FLASH_MODE) != null) {
                exif.addFlashTag(result.get(CaptureResult.FLASH_MODE) != CaptureResult.FLASH_MODE_OFF);
            }
            if(result.get(CaptureResult.LENS_FOCAL_LENGTH) != null) {
                exif.addFocalLength(new Rational((int)(result.get(CaptureResult.LENS_FOCAL_LENGTH)*100), 100));
            }
            if(result.get(CaptureResult.CONTROL_AWB_MODE) != null) {
                exif.addWhiteBalanceMode(result.get(CaptureResult.CONTROL_AWB_MODE));
            }
            if(result.get(CaptureResult.LENS_APERTURE) != null) {
                exif.addAperture(new Rational((int)(result.get(CaptureResult.LENS_APERTURE)*100), 100));
            }
            if(result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null) {
                exif.addExposureTime(new Rational(result.get(CaptureResult.SENSOR_EXPOSURE_TIME)/1000000, 1000));
            }
            if(result.get(CaptureResult.SENSOR_SENSITIVITY) != null) {
                exif.addISO(result.get(CaptureResult.SENSOR_SENSITIVITY));
            }
        }
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        try {
            exif.writeExif(jpeg, jpegOut);
        } catch (IOException e) {
            Log.e(TAG, "Could not write EXIF", e);
        }
        return jpegOut.toByteArray();
    }

    private void clear() {
        mCurrentNumImage = 0;
    }

    private void processImage(final String title, final long date,
                             final MediaSaveService.OnMediaSavedListener mediaSavedListener,
                             final ContentResolver contentResolver) {
        if(mHandler == null || !mHandler.isRunning || mStatus != STATUS.BUSY) {
            return;
        }
        final ProcessorHandler handler = mHandler;
        mHandler.post(new Runnable() {
            public void run() {
                byte[] bytes;
                ImageFilter.ResultImage resultImage = null;
                synchronized (lock) {
                    if (!handler.isRunning) {
                        return;
                    }
                    if (mFilter == null) { //In case no post filter is chosen
                        resultImage = mDefaultResultImage;
                    } else {
                        resultImage = mFilter.processImage();
                        for (int i = 0; i < mImages.length; i++) {
                            if(mImages[i] != null) {
                                mImages[i].close();
                                mImages[i] = null;
                            }
                        }
                    }
                    if(resultImage != null) {
                        //Start processing FrameProcessor filter as well
                        for (ImageFilter filter : mController.getFrameFilters()) {
                            filter.init(resultImage.width, resultImage.height, resultImage.stride, resultImage.stride);
                            filter.addImage(resultImage.outBuffer, null, 0, new Boolean(false));
                        }

                        if(isSelfieMirrorOn() && !mController.isBackCamera()) {
                            nativeFlipNV21(resultImage.outBuffer.array(), resultImage.stride, resultImage.height, resultImage.stride - resultImage.width, true);
                        }
                    }
                    //End processing FrameProessor filter
                    clear();
                    mStatus = STATUS.INIT;
                    if(mWatchdog != null) {
                        mWatchdog.stopMonitor();
                    }
                    if(resultImage == null ||
                            (resultImage.outRoi.left + resultImage.outRoi.width() > resultImage.width) ||
                            (resultImage.outRoi.top + resultImage.outRoi.height() > resultImage.height)
                            ) {
                        Log.d(TAG, "Result image is not valid.");
                    } else {
                        if(mFilter != null && DEBUG_FILTER) {
                            bytes = nv21ToJpeg(mDebugResultImage, mOrientation, null);
                            mActivity.getMediaSaveService().addImage(
                                    bytes, title + "_beforeApplyingFilter", date, null, mDebugResultImage.outRoi.width(), mDebugResultImage.outRoi.height(),
                                    mOrientation, null, mediaSavedListener, contentResolver, "jpeg");
                        }
                        bytes = nv21ToJpeg(resultImage, mOrientation, waitForMetaData(0));
                        mActivity.getMediaSaveService().addImage(
                                    bytes, title, date, null, resultImage.outRoi.width(), resultImage.outRoi.height(),
                                    mOrientation, null, mediaSavedListener, contentResolver, "jpeg");
                            mController.updateThumbnailJpegData(bytes);
                    }
                }
            }
        });
    }

    public TotalCaptureResult waitForMetaData(int index) {
        int timeout = 10; //100ms
    	while(timeout > 0) {
            if (mTotalCaptureResultList.size() > index) {
                    return mTotalCaptureResultList.get(index);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
            timeout--;
        }
        if(mTotalCaptureResultList.size() == 0) {
            return null;
        }
        return mTotalCaptureResultList.get(0);
    }

    private ImageFilter.ResultImage resizeImage(ImageFilter.ResultImage oldImage, Size newSize) {
        ImageFilter.ResultImage newImage = new ImageFilter.ResultImage(
                ByteBuffer.allocateDirect(newSize.getWidth() * newSize.getHeight() * 3/2),
                new Rect(0, 0,
                        newSize.getWidth(), newSize.getHeight()),
                newSize.getWidth(), newSize.getHeight(), newSize.getWidth());
        int ratio = nativeResizeImage(oldImage.outBuffer.array(), newImage.outBuffer.array(),
                oldImage.width, oldImage.height, oldImage.stride, newSize.getWidth(), newSize.getHeight());
        newImage.outRoi = new Rect(oldImage.outRoi.left/ratio, oldImage.outRoi.top/ratio,
                                       oldImage.outRoi.right/ratio, oldImage.outRoi.bottom/ratio);
        if(newImage.width < newImage.outRoi.width()) {
            newImage.outRoi.right = newImage.width;
        }
        if(newImage.height < newImage.outRoi.height()) {
            newImage.outRoi.bottom = newImage.height;
        }
        Log.d(TAG, "Image is resized by SW with the ratio: "+ratio+" oldRoi: "+oldImage.outRoi.toString());
        return newImage;
    }

    ImageReader.OnImageAvailableListener processedImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "ZSL image Reprocess is done");
            Image image = reader.acquireNextImage();
            if(image != null) {
                onImageToProcess(image);
            }
        }
    };

    private byte[] nv21ToJpeg(ImageFilter.ResultImage resultImage, int orientation, TotalCaptureResult result) {
        BitmapOutputStream bos = new BitmapOutputStream(1024);
        YuvImage im = new YuvImage(resultImage.outBuffer.array(), ImageFormat.NV21,
                                    resultImage.width, resultImage.height, new int[]{resultImage.stride, resultImage.stride});
        if(isSelfieMirrorOn() && !mController.isBackCamera()) {
            int t = resultImage.height - (resultImage.outRoi.top + resultImage.outRoi.height());
            resultImage.outRoi = new Rect(resultImage.outRoi.left, t, resultImage.outRoi.right , resultImage.outRoi.height() + t);
        }
        im.compressToJpeg(resultImage.outRoi, getJpegQualityValue(), bos);
        byte[] bytes = bos.getArray();
        bytes = addExifTags(bytes, orientation, result);
        return bytes;
    }

    public int getJpegQualityValue() {
        int quality = 55;
        if(SettingsManager.getInstance() != null && SettingsManager.getInstance().getValue(SettingsManager.KEY_JPEG_QUALITY) != null) {
            String value = SettingsManager.getInstance().getValue(SettingsManager.KEY_JPEG_QUALITY);
            int jpegQuality = mController.getQualityNumber(value);
        }
        return quality;
    }

    private class BitmapOutputStream extends ByteArrayOutputStream {
        public BitmapOutputStream(int size) {
            super(size);
        }

        public byte[] getArray() {
            return buf;
        }
    }

    private native int nativeNV21Split(byte[] srcYVU, ByteBuffer yBuf, ByteBuffer vuBuf, int width, int height, int srcStride, int dstStride);
    private native int nativeResizeImage(byte[] oldBuf, byte[] newBuf, int oldWidth, int oldHeight, int oldStride, int newWidth, int newHeight);
    private native int nativeFlipNV21(byte[] buf, int stride, int height, int gap, boolean isVertical);
    static {
        System.loadLibrary("jni_imageutil");
    }
}
