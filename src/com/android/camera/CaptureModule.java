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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
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
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;

import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;

import org.codeaurora.snapcam.R;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CaptureModule implements CameraModule, PhotoController {
    public static final int DUAL_MODE = 0;
    public static final int BAYER_MODE = 1;
    public static final int MONO_MODE = 2;
    private static final int OPEN_CAMERA = 0;
    private static final int MAX_NUM_CAM = 3;
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;
    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;
    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    //Todo: Read ids from the device dynamically
    private static final int BAYER_ID = 0;
    private static final int MONO_ID = 1;
    private static final String TAG = "SnapCam_CaptureModule";
    private static int MODE = DUAL_MODE;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private boolean mInitialized = false;
    private long mCaptureStartTime;
    private boolean mSurfaceReady = false;
    private boolean[] mCameraOpened = new boolean[MAX_NUM_CAM];
    private CameraDevice[] mCameraDevice = new CameraDevice[MAX_NUM_CAM];
    private String[] mCameraId = new String[MAX_NUM_CAM];
    private CaptureUI mUI;
    private CameraActivity mActivity;
    private PreferenceGroup mPreferenceGroup;
    private ComboPreferences mPreferences;
    private CaptureRequest.Builder[] mCaptureBuilder = new CaptureRequest.Builder[MAX_NUM_CAM];
    private final CameraPreference.OnPreferenceChangedListener prefListener = new
            CameraPreference.OnPreferenceChangedListener() {
                @Override
                public void onSharedPreferenceChanged(ListPreference pref) {
                    applyPreference(0, pref);
                    mUI.overrideSettings(pref.getKey(), null);
                }

                @Override
                public void onSharedPreferenceChanged() {
                }

                @Override
                public void onRestorePreferencesClicked() {
                }

                @Override
                public void onOverriddenPreferencesClicked() {
                }

                @Override
                public void onCameraPickerClicked(int cameraId) {
                }
            };
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession[] mCaptureSession = new CameraCaptureSession[MAX_NUM_CAM];
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;
    private HandlerThread mImageAvailableThread;
    private HandlerThread mCallbackThread;
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mCameraHandler;
    private Handler mImageAvailableHandler;
    private Handler mCallbackHandler;
    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader[] mImageReader = new ImageReader[MAX_NUM_CAM];
    private NamedImages mNamedImages;
    private ContentResolver mContentResolver;
    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "image available");
            mCaptureStartTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(mCaptureStartTime);
            NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;

            Image mImage = reader.acquireNextImage();
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            //Todo: dont create new buffer and use the one from ImageReader
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            mActivity.getMediaSaveService().addImage(
                    bytes, title, date, null, reader.getWidth(), reader.getHeight(),
                    0, null, mOnMediaSavedListener, mContentResolver, "jpeg");
            mImage.close();
        }

    };
    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder[] mPreviewRequestBuilder = new CaptureRequest.Builder[MAX_NUM_CAM];
    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest[] mPreviewRequest = new CaptureRequest[MAX_NUM_CAM];
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int[] mState = new int[MAX_NUM_CAM];
    /**
     * A {@link Semaphore} make sure the camera open callback happens first before closing the
     * camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            int id = (int) result.getRequest().getTag();
            switch (mState[id]) {
                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (afState == null) {
                        captureStillPicture(id);
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices

                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState[id] = STATE_PICTURE_TAKEN;
                            captureStillPicture(id);
                        } else {
                            runPrecaptureSequence(id);
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState[id] = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState[id] = STATE_PICTURE_TAKEN;
                        captureStillPicture(id);
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
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
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onOpened " + id);
            mCameraOpenCloseLock.release();
            if (MODE == DUAL_MODE && id == BAYER_ID) {
                Message msg = Message.obtain();
                msg.what = OPEN_CAMERA;
                msg.arg1 = MONO_ID;
                mCameraHandler.sendMessage(msg);
            }
            if (!mInitialized) {
                mInitialized = true;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.onCameraOpened(mPreferenceGroup, prefListener);
                    }
                });
            }

            mCameraDevice[id] = cameraDevice;
            mCameraOpened[id] = true;
            createSession(id);
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onDisconnected " + id);
            cameraDevice.close();
            mCameraDevice = null;
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onError " + id + error);
            cameraDevice.close();
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
            if (null != mActivity) {
                mActivity.finish();
            }
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onClosed " + id);
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
        }

    };

    public static boolean setMode(String value) {
        int mode = DUAL_MODE;
        switch (value) {
            case "dual":
                mode = DUAL_MODE;
                break;
            case "bayer":
                mode = BAYER_MODE;
                break;
            case "mono":
                mode = MONO_MODE;
                break;
        }
        if (MODE == mode) return false;
        MODE = mode;
        return true;
    }

    private void createSession(final int id) {
        if (!mCameraOpened[id] || !mSurfaceReady) return;
        List<Surface> list = new LinkedList<Surface>();
        mUI.hidePreviewCover();
        try {
            Surface surface;
            if (id == BAYER_ID || (id == MONO_ID && MODE == MONO_MODE)) {
                SurfaceHolder sh = mUI.getSurfaceHolder();
                if (sh == null) {
                    return;
                }
                surface = sh.getSurface();
            } else {
                SurfaceHolder sh = mUI.getSurfaceHolder2();
                if (sh == null) {
                    return;
                }
                surface = sh.getSurface();
            }
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder[id] = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            mPreviewRequestBuilder[id].addTarget(surface);
            // Auto focus should be continuous for camera preview.
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Flash is automatically enabled when necessary.
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mPreviewRequestBuilder[id].setTag(id);

            // This is the CaptureRequest.Builder that we use to take a picture.
            mCaptureBuilder[id] =
                    mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureBuilder[id].addTarget(mImageReader[id].getSurface());

            // Use the same AE and AF modes as the preview.
            mCaptureBuilder[id].set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureBuilder[id].set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            list.add(surface);
            list.add(mImageReader[id].getSurface());
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice[id].createCaptureSession(list,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice[id]) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession[id] = cameraCaptureSession;
                            try {
                                // Finally, we start displaying the camera preview.
                                mPreviewRequest[id] = mPreviewRequestBuilder[id].build();
                                mCaptureSession[id].setRepeatingRequest(mPreviewRequest[id],
                                        mCaptureCallback, mCameraHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
        }
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        Log.d(TAG, "init");
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mCameraOpened[i] = false;
        }
        mSurfaceReady = false;

        mActivity = activity;
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }

        mPreferences = new ComboPreferences(mActivity);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), activity);

        mPreferences.setLocalId(mActivity, BAYER_ID);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        PreferenceInflater inflater = new PreferenceInflater(mActivity);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(R.xml.camera_preferences);
        mPreferenceGroup = group;

        ListPreference pref = group.findPreference(CameraSettings.KEY_DUAL_CAMERA);
        setMode(pref.getValue());

        mContentResolver = mActivity.getContentResolver();
        mUI = new CaptureUI(activity, this, parent);
        mUI.initializeControlByIntent();
        mNamedImages = new NamedImages();
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.d(TAG, "takePicture");
        switch (MODE) {
            case DUAL_MODE:
                lockFocus(BAYER_ID);
                lockFocus(MONO_ID);
                break;
            case BAYER_MODE:
                lockFocus(BAYER_ID);
                break;
            case MONO_MODE:
                lockFocus(MONO_ID);
                break;
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus(int id) {
        Log.d(TAG, "lockFocus " + id);
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState[id] = STATE_WAITING_LOCK;
            mCaptureSession[id].capture(mPreviewRequestBuilder[id].build(), mCaptureCallback,
                    mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture(final int id) {
        Log.d(TAG, "captureStillPicture " + id);
        try {
            if (null == mActivity || null == mCameraDevice) {
                return;
            }

            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            mCaptureBuilder[id].set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            List<CaptureRequest> burstList = new ArrayList<CaptureRequest>();

            for (int i = 0; i < 1; i++) {
                burstList.add(mCaptureBuilder[id].build());
            }

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    Log.d(TAG, "captureStillPicture onCaptureCompleted");
                }

                @Override
                public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                        sequenceId, long frameNumber) {
                    Log.d(TAG, "captureStillPicture onCaptureSequenceCompleted");
                    unlockFocus(id);
                }

            };
            mCaptureSession[id].captureBurst(burstList, CaptureCallback, mCallbackHandler);
        } catch (CameraAccessException e) {
            Log.d(TAG, "Capture still picture has failed");
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence(int id) {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState[id] = STATE_WAITING_PRECAPTURE;
            mCaptureSession[id].capture(mPreviewRequestBuilder[id].build(), mCaptureCallback,
                    mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs() {
        Activity activity = mActivity;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();

            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                mImageReader[i] = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, 3);
                mImageReader[i].setOnImageAvailableListener(
                        mOnImageAvailableListener, mImageAvailableHandler);
                mCameraId[i] = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus(int id) {
        Log.d(TAG, "unlockFocus " + id);
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession[id].capture(mPreviewRequestBuilder[id].build(), mCaptureCallback,
                    mCameraHandler);
            // After this, the camera will go back to the normal state of preview.
            mState[id] = STATE_PREVIEW;
            mCaptureSession[id].setRepeatingRequest(mPreviewRequest[id], mCaptureCallback,
                    mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        Log.d(TAG, "closeCamera");
        try {
            mCameraOpenCloseLock.acquire();
            for (int i = 0; i < MAX_NUM_CAM; i++) {
                if (null != mCaptureSession[i]) {
                    mCaptureSession[i].close();
                    mCaptureSession[i] = null;
                }
                if (null != mImageReader[i]) {
                    mImageReader[i].close();
                    mImageReader[i] = null;
                }
            }
            for (int i = 0; i < MAX_NUM_CAM; i++) {
                if (null != mCameraDevice[i]) {
                    mCameraDevice[i].close();
                    mCameraDevice[i] = null;
                    mCameraOpened[i] = false;
                }
            }
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mImageAvailableThread = new HandlerThread("CameraImageAvailable");
        mImageAvailableThread.start();
        mCallbackThread = new HandlerThread("CameraCallback");
        mCallbackThread.start();

        mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
        mImageAvailableHandler = new Handler(mImageAvailableThread.getLooper());
        mCallbackHandler = new Handler(mCallbackThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        mImageAvailableThread.quitSafely();
        mCallbackThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mImageAvailableThread.join();
            mImageAvailableThread = null;
            mImageAvailableHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mCallbackThread.join();
            mCallbackThread = null;
            mCallbackHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int id) {
        Log.d(TAG, "openCamera " + id);
        CameraManager manager;
        try {
            manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            mCameraId[id] = manager.getCameraIdList()[id];
            if (!mCameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Time out waiting to lock camera opening.");
                throw new RuntimeException("Time out waiting to lock camera opening");
            }
            manager.openCamera(mCameraId[id], mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {

    }

    @Override
    public void onPauseBeforeSuper() {

    }

    @Override
    public void onPauseAfterSuper() {
        Log.d(TAG, "onPause");
        mUI.showPreviewCover();
        stopBackgroundThread();
        closeCamera();
        mUI.onPause();
    }

    @Override
    public void onResumeBeforeSuper() {

    }

    @Override
    public void onResumeAfterSuper() {
        Log.d(TAG, "onResume " + MODE);
        setUpCameraOutputs();
        startBackgroundThread();
        Message msg = Message.obtain();
        msg.what = OPEN_CAMERA;
        switch (MODE) {
            case DUAL_MODE:
            case BAYER_MODE:
                msg.arg1 = BAYER_ID;
                mCameraHandler.sendMessage(msg);
                break;
            case MONO_MODE:
                msg.what = OPEN_CAMERA;
                msg.arg1 = MONO_ID;
                mCameraHandler.sendMessage(msg);
                break;
        }
        mUI.hidePreviewCover();
        mUI.enableShutter(true);
        mUI.initializeFirstTime();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {

    }

    @Override
    public void onStop() {

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
    public void onPreviewUIReady() {
        Log.d(TAG, "onPreviewUIReady");
        mSurfaceReady = true;
        switch (MODE) {
            case DUAL_MODE:
                createSession(BAYER_ID);
                createSession(MONO_ID);
                break;
            case BAYER_MODE:
                createSession(BAYER_ID);
                break;
            case MONO_MODE:
                createSession(MONO_ID);
                break;
        }
    }

    @Override
    public void onPreviewUIDestroyed() {

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
    public void onShutterButtonFocus(boolean pressed) {

    }

    @Override
    public void onShutterButtonClick() {
        takePicture();
    }

    @Override
    public void onShutterButtonLongClick() {

    }

    private void applyPreference(int cameraId, ListPreference pref) {
        String key = pref.getKey();
        String value = pref.getValue();
        if (key.equals(CameraSettings.KEY_FLASH_MODE)) {
            if (value.equals("on")) {
                mCaptureBuilder[cameraId].set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            } else if (value.equals("auto")) {
                mCaptureBuilder[cameraId].set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            } else if (value.equals("off")) {
                mCaptureBuilder[cameraId].set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
            }
        } else if (key.equals(CameraSettings.KEY_MONO_PREVIEW)) {
            if (value.equals("on")) {
            } else if (value.equals("off")) {
            }
        }
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

    private class MyCameraHandler extends Handler {

        public MyCameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OPEN_CAMERA: {
                    int id = msg.arg1;
                    openCamera(id);
                    break;
                }
            }
        }
    }

}
