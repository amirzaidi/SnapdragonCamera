/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.data.LocalData;
import com.android.camera.exif.ExifInterface;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class PanoCaptureModule implements CameraModule, PhotoController {
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    private static final String TAG = "SnapCam_PanoCaptureModule";

    private static final int BAYER_CAMERA_ID = 0;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private int mState = STATE_PREVIEW;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private boolean mSurfaceReady = false;
    private boolean mCameraOpened = false;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private PanoCaptureUI mUI;
    private CameraActivity mActivity;

    private CameraCaptureSession mCaptureSession;

    private HandlerThread mCameraThread;

    private Handler mCameraHandler;

    private ContentResolver mContentResolver;
    private Size mOutputSize;
    private PanoCaptureFrameProcessor mFrameProcessor;
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private LocationManager mLocationManager;
    private Object mSessionLock = new Object();
    public static final float TARGET_RATIO = 4f/3f;
    private static final int STATE_WAITING_LOCK = 1;
    private Semaphore mFocusLockSemaphore = new Semaphore(1);
    private boolean mIsLockFocusAttempted = false;
    private int mCameraSensorOrientation;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d(TAG, "STATE_WAITING_LOCK afState:" + afState + " aeState:" + aeState);
                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        changePanoStatus(true, false);
                        mState = STATE_PREVIEW;
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

    };
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUI.onCameraOpened();
                }


            });
            mCameraDevice = cameraDevice;
            mCameraOpened = true;
            createSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            int id = Integer.parseInt(cameraDevice.getId());
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != mActivity) {
                mActivity.finish();
            }
        }

    };

    private void closeSession() {
        synchronized (mSessionLock) {
            if (mFrameProcessor != null) {
                mFrameProcessor.clear();
                mFrameProcessor = null;
            }
        }
    }

    private void createSession() {
        if (!mCameraOpened || !mSurfaceReady) return;
        synchronized (mSessionLock) {
            List<Surface> list = new LinkedList<Surface>();
            try {
                Surface surface = null;
                SurfaceHolder sh = mUI.getSurfaceHolder();
                if (sh != null) {
                    surface = sh.getSurface();
                }
                if (surface == null)
                    return;

                if(mFrameProcessor == null) {
                    mFrameProcessor = new PanoCaptureFrameProcessor(mOutputSize, mActivity, mUI, this);
                }

                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice
                           .TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(mFrameProcessor.getInputSurface());
                mPreviewRequestBuilder.addTarget(surface);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mPreviewRequest = mPreviewRequestBuilder.build();
                list.add(surface);
                list.add(mFrameProcessor.getInputSurface());
                mCameraDevice.createCaptureSession(list,
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                if (null == mCameraDevice) {
                                    Log.e(TAG, "The camera is already closed.");
                                    return;
                                }
                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                                try {
                                    mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                            mCaptureCallback, mCameraHandler);
                                } catch (CameraAccessException e) {
                                    Log.e(TAG, "createCaptureSession: " + e.toString());
                                }
                            }

                            @Override
                            public void onConfigureFailed(
                                    CameraCaptureSession cameraCaptureSession) {
                                Log.e(TAG, "Capture session configuration is failed");
                            }
                        }, null
                );
            } catch (CameraAccessException e) {
                Log.e(TAG, "createSession: " + e.toString());
                mActivity.finish();
            } catch (SecurityException e) {
                Log.e(TAG, "createSession: " + e.toString());
                mActivity.finish();
            }
        }
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mCameraOpened = false;
        mSurfaceReady = false;
        mActivity = activity;
        SettingsManager settingsManager = SettingsManager.getInstance();
        settingsManager.init();
        mUI = new PanoCaptureUI(activity, this, parent);
        mContentResolver = mActivity.getContentResolver();
        mLocationManager = new LocationManager(mActivity, null);

    }

    public void changePanoStatus(boolean newStatus, boolean isCancelling) {
        mUI.onPanoStatusChange(newStatus);
        if(mFrameProcessor != null) {
            mFrameProcessor.changePanoStatus(newStatus, isCancelling);
        }
    }

    public boolean isPanoActive() {
        if(mFrameProcessor != null) {
            return mFrameProcessor.isPanoActive();
        }
        return false;
    }

    private void setUpCameraOutputs() {
        Activity activity = mActivity;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            String cameraId = cameraIdList[BAYER_CAMERA_ID];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }
            mCameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Display display = mActivity.getWindowManager().getDefaultDisplay();
            Point ds = new Point();
            display.getSize(ds);
            mOutputSize = getOutputSize(TARGET_RATIO, map.getOutputSizes(ImageFormat.YUV_420_888), ds.x, ds.y);
            mCameraId = cameraId;
        } catch (CameraAccessException e) {
            Log.e(TAG, "setUpCameraOutputs: " + e.toString());
        }
    }

    public int getCameraSensorOrientation() {
        return mCameraSensorOrientation;
    }

    private Size getOutputSize(float ratio, Size[] prevSizes, int screenW, int
            screenH) {
        Size optimal = prevSizes[0];
        for (Size prevSize: prevSizes) {
            float prevRatio = (float) prevSize.getWidth() / prevSize.getHeight();
            if (Math.abs(prevRatio - ratio) < 0.01) {
                if (prevSize.getWidth() <= screenH && prevSize.getHeight() <= screenW) {
                    return prevSize;
                } else {
                    optimal = prevSize;
                }
            }
        }
        return optimal;
    }

    public Size getPictureOutputSize() {
        return mOutputSize;
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        boolean wasPreviousCameraOpenFailed = false;
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Time out waiting to lock camera closing.");
                wasPreviousCameraOpenFailed = true;
            }
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
                mCameraOpened = false;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            if(wasPreviousCameraOpenFailed) {
                mActivity.finish();
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            //Ignore this
        }
    }

    private void openCamera() {
        CameraManager manager;
        try {
            manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            mCameraId = manager.getCameraIdList()[BAYER_CAMERA_ID];
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (SecurityException e) {
            Log.e(TAG, "openCamera: " + e.toString());
            Toast.makeText(mActivity, "Can't open camera, please restart it", Toast.LENGTH_LONG).show();
            mActivity.finish();
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: " + e.toString());
            Toast.makeText(mActivity, "Can't open camera, please restart it", Toast.LENGTH_LONG).show();
            mActivity.finish();
        } catch (InterruptedException e) {
            Log.e(TAG, "openCamera: " + e.toString());
        }
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {

    }

    @Override
    public void onPauseBeforeSuper() {
        mUI.applySurfaceChange(0, false);
    }

    @Override
    public void onPauseAfterSuper() {
        stopBackgroundThread();
        closeCamera();
        mUI.onPause();
    }

    @Override
    public void onResumeBeforeSuper() {

    }

    @Override
    public void onResumeAfterSuper() {
        mUI.onResume();
        openCamera();
        setUpCameraOutputs();
        mUI.applySurfaceChange(2, false);
        mUI.setLayout(mOutputSize);
        startBackgroundThread();
        mUI.enableShutter(true);
        mUI.initializeShutterButton();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {

    }

    public Uri savePanorama(byte[] jpegData, int width, int height, int orientation) {
        long timeTaken = System.currentTimeMillis();

        if (jpegData != null) {
            String filename = PanoUtil.createName(
                    mActivity.getResources().getString(R.string.pano_file_name_format), timeTaken);
            String filepath = Storage.generateFilepath(filename,
                    PhotoModule.PIXEL_FORMAT_JPEG);

            Location loc = mLocationManager.getCurrentLocation();
            ExifInterface exif = new ExifInterface();
            try {
                exif.readExif(jpegData);
                exif.addGpsDateTimeStampTag(timeTaken);
                exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, timeTaken,
                        TimeZone.getDefault());
                exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,orientation));
                writeLocation(loc, exif);
                exif.writeExif(jpegData, filepath);
            } catch (IOException e) {
                Log.e(TAG, "Cannot set exif for " + filepath, e);
                Storage.writeFile(filepath, jpegData);
            }
            int jpegLength = (int) (new File(filepath).length());
            return Storage.addImage(mContentResolver, filename, timeTaken, loc, orientation,
                    jpegLength, filepath, width, height, LocalData.MIME_TYPE_JPEG);
        }
        return null;
    }

    private static void writeLocation(Location location, ExifInterface exif) {
        if (location == null) {
            return;
        }
        exif.addGpsTags(location.getLatitude(), location.getLongitude());
        exif.setTag(exif.buildTag(ExifInterface.TAG_GPS_PROCESSING_METHOD, location.getProvider()));
    }

    @Override
    public void installIntentFilter() {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public int onZoomChanged(int requestedZoom) {
        return 0;
    }

    @Override
    public void onZoomChanged(float requestedZoom) {

    }

    @Override
    public boolean isImageCaptureIntent() {
        return false;
    }

    @Override
    public boolean isCameraIdle() {
        return false;
    }

    @Override
    public void onCaptureDone() {

    }

    @Override
    public void onCaptureCancelled() {

    }

    @Override
    public void onCaptureRetake() {

    }

    @Override
    public void cancelAutoFocus() {

    }

    @Override
    public void stopPreview() {

    }

    @Override
    public int getCameraState() {
        return 0;
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {

    }

    @Override
    public void onCountDownFinished() {

    }

    @Override
    public void onScreenSizeChanged(int width, int height) {

    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {

    }

    @Override
    public void updateCameraOrientation() {

    }

    @Override
    public void enableRecordingLocation(boolean enable) {

    }

    @Override
    public void setPreferenceForTest(String key, String value) {
    }

    @Override
    public void onPreviewUIReady() {
        mSurfaceReady = true;
        createSession();
    }

    @Override
    public void onPreviewUIDestroyed() {
        closeSession();
    }

    @Override
    public void onPreviewTextureCopied() {

    }

    @Override
    public void onCaptureTextureCopied() {

    }

    @Override
    public void onUserInteraction() {

    }

    @Override
    public boolean updateStorageHintOnResume() {
        return false;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int newOrientation = CameraUtil.roundOrientation(orientation, mOrientation);

        if (mOrientation != newOrientation) {
            mOrientation = newOrientation;
            mUI.setOrientation(newOrientation, true);
        }
    }

    @Override
    public void onShowSwitcherPopup() {

    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {

    }

    @Override
    public boolean arePreviewControlsVisible() {
        return false;
    }

    @Override
    public void resizeForPreviewAspectRatio() {

    }

    @Override
    public void onSwitchSavePath() {

    }

    @Override
    public void waitingLocationPermissionResult(boolean waiting) {

    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {

    }

    @Override
    public void onShutterButtonClick() {
        if(!mFocusLockSemaphore.tryAcquire())
            return;
        mFocusLockSemaphore.release();
        if (mState == STATE_WAITING_LOCK) {
            return;
        } else {
            if (isPanoActive()) {
                changePanoStatus(false, false);
            } else {
                lockFocus();
            }
        }
    }

    private void lockFocus() {
        Log.d(TAG, "lockFocus");
        mIsLockFocusAttempted = true;
        try {
            mFocusLockSemaphore.acquire();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                    mCaptureCallback, mCameraHandler);
            mState = STATE_WAITING_LOCK;
            mFocusLockSemaphore.release();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
        }
    }

    public void unlockFocus() {
        if(!mIsLockFocusAttempted) {
            return;
        }
        Log.d(TAG, "unlockFocus ");
        try {
            mFocusLockSemaphore.acquire();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                    mCaptureCallback, mCameraHandler);
            mState = STATE_PREVIEW;
            mFocusLockSemaphore.release();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
        }
        mIsLockFocusAttempted = false;
    }

    @Override
    public void onShutterButtonLongClick() {
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
