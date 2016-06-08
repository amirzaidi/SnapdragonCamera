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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CameraProfile;
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
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.filter.ClearSightImageProcessor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CaptureModule implements CameraModule, PhotoController,
        MediaSaveService.Listener, ClearSightImageProcessor.Callback,
        SettingsManager.Listener {
    public static final int DUAL_MODE = 0;
    public static final int BAYER_MODE = 1;
    public static final int MONO_MODE = 2;
    //Todo: Read ids from the device dynamically
    public static final int BAYER_ID = 0;
    public static final int MONO_ID = 1;
    public static final int FRONT_ID = 1;
    private static final int BACK_MODE = 0;
    private static final int FRONT_MODE = 1;
    private static final int CANCEL_TOUCH_FOCUS_DELAY = 3000;
    private static final int OPEN_CAMERA = 0;
    private static final int CANCEL_TOUCH_FOCUS = 1;
    private static final int MAX_NUM_CAM = 3;
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)};
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
    /**
     * Camera state: Waiting for the touch-to-focus to converge.
     */
    private static final int STATE_WAITING_TOUCH_FOCUS = 5;
    private static final String TAG = "SnapCam_CaptureModule";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    MeteringRectangle[][] mAFRegions = new MeteringRectangle[MAX_NUM_CAM][];
    CaptureRequest.Key<Byte> BayerMonoLinkEnableKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.enable",
                    Byte.class);
    CaptureRequest.Key<Byte> BayerMonoLinkMainKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.is_main",
                    Byte.class);
    CaptureRequest.Key<Integer> BayerMonoLinkSessionIdKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data" +
                    ".related_camera_id", Integer.class);

    private boolean[] mTakingPicture = new boolean[MAX_NUM_CAM];
    private int mControlAFMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    private int mLastResultAFState = -1;
    private Rect[] mCropRegion = new Rect[MAX_NUM_CAM];
    private boolean mAutoFocusSupported;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private int mJpegQuality;
    private boolean mFirstTimeInitialized;
    private boolean mInitialized = false;
    private boolean mIsLinked = false;
    private long mCaptureStartTime;
    private boolean mPaused = true;
    private boolean mSurfaceReady = false;
    private boolean[] mCameraOpened = new boolean[MAX_NUM_CAM];
    private CameraDevice[] mCameraDevice = new CameraDevice[MAX_NUM_CAM];
    private String[] mCameraId = new String[MAX_NUM_CAM];
    private CaptureUI mUI;
    private CameraActivity mActivity;
    private List<Integer> mCameraIdList;
    private float mZoomValue = 1f;
    private FocusStateListener mFocusStateListener;
    private LocationManager mLocationManager;
    private SettingsManager mSettingsManager;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession[] mCaptureSession = new CameraCaptureSession[MAX_NUM_CAM];
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;
    private HandlerThread mImageAvailableThread;
    private HandlerThread mCaptureCallbackThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mCameraHandler;
    private Handler mImageAvailableHandler;
    private Handler mCaptureCallbackHandler;

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

    static abstract class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        int mCamId;

        ImageAvailableListener(int cameraId) {
            mCamId = cameraId;
        }
    }

    static abstract class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
        int mCamId;

        CameraCaptureCallback(int cameraId) {
            mCamId = cameraId;
        }
    }

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder[] mPreviewRequestBuilder = new CaptureRequest.Builder[MAX_NUM_CAM];
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
                    Log.d(TAG, "STATE_WAITING_LOCK id: " + id + " afState:" + afState + " aeState:" + aeState);
                    // AF_PASSIVE is added for continous auto focus mode
                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState ||
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        if (aeState == null || (aeState == CaptureResult
                                .CONTROL_AE_STATE_CONVERGED) && isFlashOff(id)) {
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
                    Log.d(TAG, "STATE_WAITING_PRECAPTURE id: " + id + " aeState:" + aeState);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        mState[id] = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d(TAG, "STATE_WAITING_NON_PRECAPTURE id: " + id + " aeState:" + aeState);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState[id] = STATE_PICTURE_TAKEN;
                        captureStillPicture(id);
                    }
                    break;
                }
                case STATE_WAITING_TOUCH_FOCUS:
                    break;
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            int id = (int) partialResult.getRequest().getTag();
            if (id == getMainCameraId()) updateFocusStateChange(partialResult);
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            int id = (int) result.getRequest().getTag();
            if (id == getMainCameraId()) updateFocusStateChange(result);
            process(result);
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onOpened " + id);
            mCameraOpenCloseLock.release();
            if (mPaused) {
                return;
            }
            if (isBackMode() && getMode() == DUAL_MODE && id == BAYER_ID) {
                Message msg = mCameraHandler.obtainMessage(OPEN_CAMERA, MONO_ID);
                mCameraHandler.sendMessage(msg);
            }
            if (!mInitialized) {
                mInitialized = true;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.onCameraOpened(mCameraIdList);
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

    private boolean isBackMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
        if (value == null) return true;
        if (Integer.parseInt(value) == BAYER_ID) return true;
        return false;
    }

    private int getMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_DUAL_CAMERA);
        return Integer.parseInt(value);
    }

    private boolean isClearSightOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_CLEARSIGHT);
        if (value == null) return false;
        return isBackMode() && getMode() == DUAL_MODE && value.equals("on");
    }

    public static int getQualityNumber(String jpegQuality) {
        try {
            int qualityPercentile = Integer.parseInt(jpegQuality);
            if (qualityPercentile >= 0 && qualityPercentile <= 100)
                return qualityPercentile;
            else
                return 85;
        } catch (NumberFormatException nfe) {
            //chosen quality is not a number, continue
        }
        int value = 0;
        switch (jpegQuality) {
            case "superfine":
                value = CameraProfile.QUALITY_HIGH;
                break;
            case "fine":
                value = CameraProfile.QUALITY_MEDIUM;
                break;
            case "normal":
                value = CameraProfile.QUALITY_LOW;
                break;
            default:
                return 85;
        }
        return CameraProfile.getJpegEncodingQualityParameter(value);
    }

    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        //Todo: test record location. Jack to provide instructions
        // Initialize location service.
        boolean recordLocation = getRecordLocation();
        mLocationManager.recordLocation(recordLocation);

        mUI.initializeFirstTime();
        MediaSaveService s = mActivity.getMediaSaveService();
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (s != null) {
            s.setListener(this);
        }

        mNamedImages = new NamedImages();
        mFirstTimeInitialized = true;
    }

    private void initializeSecondTime() {
        // Start location update if needed.
        boolean recordLocation = getRecordLocation();
        mLocationManager.recordLocation(recordLocation);
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(this);
        }
        mNamedImages = new NamedImages();
    }

    private void createSession(final int id) {
        if (mPaused || !mCameraOpened[id] || !mSurfaceReady) return;
        Log.d(TAG, "createSession " + id);
        List<Surface> list = new LinkedList<Surface>();
        mUI.hidePreviewCover();
        try {
            Surface surface;
            if (isBackMode()) {
                if (id == BAYER_ID || (id == MONO_ID && getMode() == MONO_MODE)) {
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
            } else {
                SurfaceHolder sh = mUI.getSurfaceHolder();
                if (sh == null) {
                    return;
                }
                surface = sh.getSurface();
            }
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder[id] = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            mPreviewRequestBuilder[id].setTag(id);
            mPreviewRequestBuilder[id].addTarget(surface);

            CameraCaptureSession.StateCallback captureSessionCallback =
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (mPaused || null == mCameraDevice[id]) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession[id] = cameraCaptureSession;
                            initializePreviewConfiguration(id);
                            try {
                                if (isBackMode() && getMode() == DUAL_MODE) {
                                    linkBayerMono(id);
                                    mIsLinked = true;
                                }
                                // Finally, we start displaying the camera preview.
                                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                                        .build(), mCaptureCallback, mCameraHandler);

                                if (isClearSightOn()) {
                                    ClearSightImageProcessor.getInstance().onCaptureSessionConfigured(id == BAYER_ID, cameraCaptureSession);
                                }
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "cameracapturesession - onConfigureFailed");
                        }

                        @Override
                        public void onClosed(CameraCaptureSession session) {
                            Log.d(TAG, "cameracapturesession - onClosed");
                        }
                    };

            list.add(surface);

            if(isClearSightOn()) {
                ClearSightImageProcessor.getInstance().createCaptureSession(
                        id==BAYER_ID, mCameraDevice[id], list, captureSessionCallback);
            } else {
                list.add(mImageReader[id].getSurface());
                // Here, we create a CameraCaptureSession for camera preview.
                mCameraDevice[id].createCaptureSession(list, captureSessionCallback, null);
            }
        } catch (CameraAccessException e) {
        }
    }

    private void setAFModeToPreview(int id, int afMode) {
        Log.d(TAG, "setAFModeToPreview " + afMode);
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_MODE, afMode);
        applyAFRegions(mPreviewRequestBuilder[id], id);
        try {
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void reinit() {
        String pictureSize = mSettingsManager.getValue(SettingsManager
                .KEY_PICTURE_SIZE);

        Size size = parsePictureSize(pictureSize);
        mUI.setPreviewSize(size.getHeight(), size.getWidth());
        mSettingsManager.reinit(getMainCameraId());
    }

    public boolean getRecordLocation() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_RECORD_LOCATION);
        if (value == null) value = RecordLocationPreference.VALUE_NONE;
        return RecordLocationPreference.VALUE_ON.equals(value);
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mSettingsManager.init();
        Log.d(TAG, "init");
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mCameraOpened[i] = false;
            mTakingPicture[i] = false;
        }
        mSurfaceReady = false;

        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }

        mContentResolver = mActivity.getContentResolver();
        mUI = new CaptureUI(activity, this, parent);
        mUI.initializeControlByIntent();
        String pictureSize = mSettingsManager.getValue(SettingsManager.KEY_PICTURE_SIZE);
        Size size = parsePictureSize(pictureSize);
        mUI.setPreviewSize(size.getHeight(), size.getWidth());
        mFocusStateListener = new FocusStateListener(mUI);
        mLocationManager = new LocationManager(mActivity, mUI);
        Storage.setSaveSDCard(mSettingsManager.getValue(SettingsManager
                .KEY_CAMERA_SAVEPATH).equals("1"));
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.d(TAG, "takePicture");
        if (isBackMode()) {
            switch (getMode()) {
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
        } else {
            lockFocus(FRONT_ID);
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus(int id) {
        Log.d(TAG, "lockFocus " + id);
        mTakingPicture[id] = true;
        if (mState[id] == STATE_WAITING_TOUCH_FOCUS) {
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, id);
            mState[id] = STATE_WAITING_LOCK;
            return;
        }

        try {
            CaptureRequest.Builder builder = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            builder.setTag(id);
            builder.addTarget(getPreviewSurface(id));

            applySettingsForLockFocus(builder, id);

            mState[id] = STATE_WAITING_LOCK;
            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void autoFocusTrigger(int id) {
        Log.d(TAG, "autoFocusTrigger " + id);
        try {
            CaptureRequest.Builder builder = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            builder.setTag(id);
            builder.addTarget(getPreviewSurface(id));

            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
            applySettingsForAutoFocus(builder, id);

            mState[id] = STATE_WAITING_TOUCH_FOCUS;
            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            setAFModeToPreview(id, mControlAFMode);
            Message message = mCameraHandler.obtainMessage(CANCEL_TOUCH_FOCUS, id);
            mCameraHandler.sendMessageDelayed(message, CANCEL_TOUCH_FOCUS_DELAY);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void linkBayerMono(int id) {
        Log.d(TAG, "linkBayerMono " + id);
        if (id == BAYER_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkMainKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkSessionIdKey, MONO_ID);
        } else if (id == MONO_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkMainKey, (byte) 0);
            mPreviewRequestBuilder[id].set(BayerMonoLinkSessionIdKey, BAYER_ID);
        }
    }

    public void unLinkBayerMono(int id) {
        Log.d(TAG, "unlinkBayerMono " + id);
        if (id == BAYER_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 0);
        } else if (id == MONO_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 0);
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture(final int id) {
        Log.d(TAG, "captureStillPicture " + id);
        try {
            if (null == mActivity || null == mCameraDevice[id]) {
                return;
            }

            final boolean csEnabled = isClearSightOn();
            CaptureRequest.Builder captureBuilder;

            if(csEnabled) {
                captureBuilder = ClearSightImageProcessor.getInstance().createCaptureRequest(mCameraDevice[id]);
            } else {
                // No Clearsight
                captureBuilder = mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }

            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.addTarget(getPreviewSurface(id));
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            applySettingsForCapture(captureBuilder, id);

            if(csEnabled) {
                ClearSightImageProcessor.getInstance().capture(
                        id==BAYER_ID, mCaptureSession[id], captureBuilder, mCaptureCallbackHandler);
            } else {
                captureBuilder.addTarget(mImageReader[id].getSurface());
                mCaptureSession[id].stopRepeating();

                mCaptureSession[id].capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {

                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session,
                                                   CaptureRequest request,
                                                   TotalCaptureResult result) {
                        Log.d(TAG, "captureStillPicture onCaptureCompleted: " + id);
                    }

                    @Override
                    public void onCaptureFailed(CameraCaptureSession session,
                                                CaptureRequest request,
                                                CaptureFailure result) {
                        Log.d(TAG, "captureStillPicture onCaptureFailed: " + id);
                    }

                    @Override
                    public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                            sequenceId, long frameNumber) {
                        Log.d(TAG, "captureStillPicture onCaptureSequenceCompleted: " + id);
                        unlockFocus(id);
                    }
                }, mCaptureCallbackHandler);
            }
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
        Log.d(TAG, "runPrecaptureSequence");
        try {
            CaptureRequest.Builder builder = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            builder.setTag(id);
            builder.addTarget(getPreviewSurface(id));

            applySettingsForPrecapture(builder, id);

            mState[id] = STATE_WAITING_PRECAPTURE;
            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
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
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                mCameraIdList.add(i);
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                mCameraId[i] = cameraId;

                if(isClearSightOn()) {
                    Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CompareSizesByArea());
                    ClearSightImageProcessor.getInstance().init(
                            largest.getWidth(), largest.getHeight(), mActivity, mOnMediaSavedListener);
                    ClearSightImageProcessor.getInstance().setCallback(this);
                } else {
                    // No Clearsight
                    // For still image captures, we use the largest available size.
                    Size largest = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new CompareSizesByArea());

                    String pictureSize = mSettingsManager.getValue(SettingsManager
                            .KEY_PICTURE_SIZE);

                    Size size = parsePictureSize(pictureSize);

                    mImageReader[i] = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                            ImageFormat.JPEG, 3);
                    mImageReader[i].setOnImageAvailableListener(new ImageAvailableListener(i) {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            Log.d(TAG, "image available for cam: " + mCamId);
                            Image image = reader.acquireNextImage();
                            mCaptureStartTime = System.currentTimeMillis();
                            mNamedImages.nameNewImage(mCaptureStartTime);
                            NamedEntity name = mNamedImages.getNextNameEntity();
                            String title = (name == null) ? null : name.title;
                            long date = (name == null) ? -1 : name.date;

                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);

                            mActivity.getMediaSaveService().addImage(bytes, title, date,
                                    null, image.getWidth(), image.getHeight(), 0, null,
                                    mOnMediaSavedListener, mContentResolver, "jpeg");
                            image.close();
                        }
                    }, mImageAvailableHandler);
                }
            }
            mAutoFocusSupported = mSettingsManager.isAutoFocusSupported(mCameraIdList);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus(int id) {
        Log.d(TAG, "unlockFocus " + id);
        try {
            CaptureRequest.Builder builder = mCameraDevice[id].createCaptureRequest(CameraDevice
                    .TEMPLATE_PREVIEW);
            builder.setTag(id);
            builder.addTarget(getPreviewSurface(id));

            applySettingsForUnlockFocus(builder, id);

            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            mState[id] = STATE_PREVIEW;
            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
            setAFModeToPreview(id, mControlAFMode);
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                    mCaptureCallback, mCameraHandler);
            mTakingPicture[id] = false;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size parsePictureSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
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
                    if (mIsLinked) {
                        unLinkBayerMono(i);
                        try {
                            mCaptureSession[i].capture(mPreviewRequestBuilder[i].build(), null,
                                    mCameraHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    mCaptureSession[i].close();
                    mCaptureSession[i] = null;
                }

                if (null != mImageReader[i]) {
                    mImageReader[i].close();
                    mImageReader[i] = null;
                }

                if (null != mCameraDevice[i]) {
                    mCameraDevice[i].close();
                    mCameraDevice[i] = null;
                    mCameraOpened[i] = false;
                }
            }
            /* no need to set this in the callback and handle asynchronously. This is the same
               reason as why we release the semaphore here, not in camera close callback function
               as we don't have to protect the case where camera open() gets called during camera
               close(). The low level framework/HAL handles the synchronization for open()
               happens after close() */
            mIsLinked = false;
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void applySettingsForLockFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForCapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        applyFlash(builder, id);
        applyJpegQuality(builder);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForPrecapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        applyFlash(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForUnlockFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForAutoFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applyCommonSettings(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
        applyWhiteBalance(builder);
        applyExposure(builder);
        applyIso(builder);
        applyColorEffect(builder);
        applySceneMode(builder);
        applyZoom(builder, id);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mImageAvailableThread = new HandlerThread("CameraImageAvailable");
        mImageAvailableThread.start();
        mCaptureCallbackThread = new HandlerThread("CameraCaptureCallback");
        mCaptureCallbackThread.start();

        mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
        mImageAvailableHandler = new Handler(mImageAvailableThread.getLooper());
        mCaptureCallbackHandler = new Handler(mCaptureCallbackThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        mImageAvailableThread.quitSafely();
        mCaptureCallbackThread.quitSafely();

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
            mCaptureCallbackThread.join();
            mCaptureCallbackThread = null;
            mCaptureCallbackHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int id) {
        if (mPaused) {
            return;
        }
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
        mUI.onPreviewFocusChanged(previewFocused);
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;
    }

    @Override
    public void onPauseAfterSuper() {
        Log.d(TAG, "onPause");
        mUI.showPreviewCover();
        if (mLocationManager != null) mLocationManager.recordLocation(false);
        if(isClearSightOn()) {
            ClearSightImageProcessor.getInstance().close();
        }
        closeCamera();
        stopBackgroundThread();
        mUI.onPause();
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        Log.d(TAG, "onResume " + getMode());
        mUI.setSwitcherIndex();
        mCameraIdList = new ArrayList<>();
        setUpCameraOutputs();
        startBackgroundThread();
        Message msg = Message.obtain();
        msg.what = OPEN_CAMERA;
        if (isBackMode()) {
            switch (getMode()) {
                case DUAL_MODE:
                case BAYER_MODE:
                    msg.obj = BAYER_ID;
                    mCameraHandler.sendMessage(msg);
                    break;
                case MONO_MODE:
                    msg.what = OPEN_CAMERA;
                    msg.obj = MONO_ID;
                    mCameraHandler.sendMessage(msg);
                    break;
            }
        } else {
            msg.obj = FRONT_ID;
            mCameraHandler.sendMessage(msg);
        }
        if (!mFirstTimeInitialized) {
            initializeFirstTime();
        } else {
            initializeSecondTime();
        }
        mUI.hidePreviewCover();
        mUI.enableShutter(true);
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
        return mUI.onBackPressed();
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
        mZoomValue = requestedZoom;

        if (isBackMode()) {
            switch (getMode()) {
                case DUAL_MODE:
                    applyZoomAndUpdate(BAYER_ID);
                    applyZoomAndUpdate(MONO_ID);
                    break;
                case BAYER_MODE:
                    applyZoomAndUpdate(BAYER_ID);
                    break;
                case MONO_MODE:
                    applyZoomAndUpdate(MONO_ID);
                    break;
            }
        } else {
            applyZoomAndUpdate(FRONT_ID);
        }
    }

    @Override
    public boolean isImageCaptureIntent() {
        return false;
    }

    @Override
    public boolean isCameraIdle() {
        return true;
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
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized || !mAutoFocusSupported ||
                !isTouchToFocusAllowed()) {
            return;
        }
        Log.d(TAG, "onSingleTapUp " + x + " " + y);
        mUI.setFocusPosition(x, y);
        mUI.onFocusStarted();
        if (isBackMode()) {
            switch (getMode()) {
                case DUAL_MODE:
                    triggerFocusAtPoint(x, y, BAYER_ID);
                    triggerFocusAtPoint(x, y, MONO_ID);
                    break;
                case BAYER_MODE:
                    triggerFocusAtPoint(x, y, BAYER_ID);
                    break;
                case MONO_MODE:
                    triggerFocusAtPoint(x, y, MONO_ID);
                    break;
            }
        } else {
            triggerFocusAtPoint(x, y, FRONT_ID);
        }
    }

    private int getMainCameraId() {
        if (isBackMode()) {
            switch (getMode()) {
                case DUAL_MODE:
                case BAYER_MODE:
                    return BAYER_ID;
                case MONO_MODE:
                    return MONO_ID;
            }
            return 0;
        } else {
            return FRONT_ID;
        }
    }

    private boolean isTouchToFocusAllowed() {
        for (int i = 0; i < mTakingPicture.length; i++) {
            if (mTakingPicture[i]) return false;
        }
        return true;
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
        if (mPaused) {
            return;
        }
        Log.d(TAG, "onPreviewUIReady");
        mSurfaceReady = true;
        if (isBackMode()) {
            switch (getMode()) {
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
        } else {
            createSession(FRONT_ID);
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
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int oldOrientation = mOrientation;
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (oldOrientation != mOrientation) {
            mUI.onOrientationChanged();
            mUI.setOrientation(mOrientation, true);
        }
    }

    @Override
    public void onShowSwitcherPopup() {

    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        if (mFirstTimeInitialized) {
            s.setListener(this);
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }
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
        mSettingsManager.setValue(SettingsManager.KEY_CAMERA_SAVEPATH, "1");
        RotateTextToast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
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

    private boolean isFlashOff(int id) {
        if (!mSettingsManager.isFlashSupported(id)) return true;
        return mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE).equals("off");
    }

    private void initializePreviewConfiguration(int id) {
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_IDLE);
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest
                .CONTROL_AE_MODE_ON);
        mPreviewRequestBuilder[id].set(CaptureRequest.FLASH_MODE, CaptureRequest
                .FLASH_MODE_OFF);
        applyCommonSettings(mPreviewRequestBuilder[id], id);
    }

    public Rect cropRegionForZoom(int id) {
        Log.d(TAG, "cropRegionForZoom " + id);
        Rect activeRegion = mSettingsManager.getSensorActiveArraySize(id);
        Rect cropRegion = new Rect();

        int xCenter = activeRegion.width() / 2;
        int yCenter = activeRegion.height() / 2;
        int xDelta = (int) (activeRegion.width() / (2 * mZoomValue));
        int yDelta = (int) (activeRegion.height() / (2 * mZoomValue));
        cropRegion.set(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
        mCropRegion[id] = cropRegion;
        return mCropRegion[id];
    }

    private void applyZoom(CaptureRequest.Builder request, int id) {
        request.set(CaptureRequest.SCALER_CROP_REGION, cropRegionForZoom(id));
    }

    private boolean applyPreferenceToPreview(int cameraId, String key, String value) {
        boolean updatePreview = false;
        switch (key) {
            case SettingsManager.KEY_WHITE_BALANCE:
                updatePreview = true;
                applyWhiteBalance(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_COLOR_EFFECT:
                updatePreview = true;
                applyColorEffect(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_SCENE_MODE:
                updatePreview = true;
                applySceneMode(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_EXPOSURE:
                updatePreview = true;
                applyExposure(mPreviewRequestBuilder[cameraId]);
                break;
        }
        return updatePreview;
    }

    private void applyZoomAndUpdate(int id) {
        applyZoom(mPreviewRequestBuilder[id], id);
        try {
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void applyJpegQuality(CaptureRequest.Builder request) {
        request.set(CaptureRequest.JPEG_QUALITY, (byte) mJpegQuality);
    }

    private void applyAFRegions(CaptureRequest.Builder request, int id) {
        if (mControlAFMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions[id]);
        } else {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
        }
    }

    private void applySceneMode(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        if (mode != CaptureRequest.CONTROL_SCENE_MODE_DISABLED) {
            request.set(CaptureRequest.CONTROL_SCENE_MODE, mode);
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        } else {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        }
    }

    private void applyExposure(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_EXPOSURE);
        if (value == null) return;
        int intValue = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, intValue);
    }

    private void applyIso(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_ISO);
        if (value == null) return;
        if (value.equals("auto")) return;
        int intValue = Integer.parseInt(value);
        request.set(CaptureRequest.SENSOR_SENSITIVITY, intValue);
    }

    private void applyColorEffect(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_EFFECT_MODE, mode);
    }

    private void applyWhiteBalance(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_WHITE_BALANCE);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_AWB_MODE, mode);
    }

    private void applyFlash(CaptureRequest.Builder request, String value) {
        int mode = Integer.parseInt(value);
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        request.set(CaptureRequest.CONTROL_AE_MODE, mode);
        switch (mode) {
            case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                request.set(CaptureRequest.FLASH_MODE, CaptureRequest
                        .FLASH_MODE_SINGLE);
                break;
            case CaptureRequest.CONTROL_AE_MODE_ON:
                request.set(CaptureRequest.FLASH_MODE, CaptureRequest
                        .FLASH_MODE_OFF);
                break;
            case CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH:
                if (redeye.equals("disable")) {
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest
                            .CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                }
                break;
        }
    }

    private void applyFlash(CaptureRequest.Builder request, int id) {
        if (mSettingsManager.isFlashSupported(id)) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE);
            applyFlash(request, value);
        } else {
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private Surface getPreviewSurface(int id) {
        if (isBackMode()) {
            if (getMode() == DUAL_MODE && id == MONO_ID) {
                return mUI.getSurfaceHolder2().getSurface();
            } else {
                return mUI.getSurfaceHolder().getSurface();
            }
        } else {
            return mUI.getSurfaceHolder().getSurface();
        }
    }

    @Override
    public void onQueueStatus(final boolean full) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.enableShutter(!full);
            }
        });
    }

    public void triggerFocusAtPoint(float x, float y, int id) {
        Log.d(TAG, "triggerFocusAtPoint " + x + " " + y + " " + id);
        Point p;
        if (id == getMainCameraId()) {
            p = mUI.getSurfaceViewSize();
        } else {
            p = mUI.getSurfaceView2Size();
        }
        int width = p.x;
        int height = p.y;
        x = x / width;
        y = y / height;
        mAFRegions[id] = afRectangle(x, y, mCropRegion[id]);
        mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, id);
        autoFocusTrigger(id);
    }

    private MeteringRectangle[] afRectangle(float x, float y, Rect cropRegion) {
        int side = Math.max(cropRegion.width(), cropRegion.height()) / 8;
        int xCenter = (int) (cropRegion.left + x * cropRegion.width());
        int yCenter = (int) (cropRegion.top + y * cropRegion.height());
        Rect meteringRegion = new Rect(xCenter - side / 2, yCenter - side / 2, xCenter +
                side / 2, yCenter + side / 2);

        meteringRegion.left = CameraUtil.clamp(meteringRegion.left, cropRegion.left, cropRegion
                .right);
        meteringRegion.top = CameraUtil.clamp(meteringRegion.top, cropRegion.top, cropRegion
                .bottom);
        meteringRegion.right = CameraUtil.clamp(meteringRegion.right, cropRegion.left, cropRegion
                .right);
        meteringRegion.bottom = CameraUtil.clamp(meteringRegion.bottom, cropRegion.top,
                cropRegion.bottom);
        MeteringRectangle[] meteringRectangle = new MeteringRectangle[1];
        meteringRectangle[0] = new MeteringRectangle(meteringRegion, 1);
        return meteringRectangle;
    }

    private void updateFocusStateChange(CaptureResult result) {
        final int resultAFState = result.get(CaptureResult.CONTROL_AF_STATE);

        // Report state change when AF state has changed.
        if (resultAFState != mLastResultAFState && mFocusStateListener != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFocusStateListener.onFocusStatusUpdate(resultAFState);
                }
            });
        }
        mLastResultAFState = resultAFState;
    }

    @Override
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        if (mPaused) return;
        boolean updatePreviewBayer = false;
        boolean updatePreviewMono = false;
        boolean updatePreviewFront = false;
        for (SettingsManager.SettingState settingState : settings) {

            String key = settingState.key;
            SettingsManager.Values values = settingState.values;
            String value;
            if (values.overriddenValue != null) {
                value = values.overriddenValue;
            } else {
                value = values.value;
            }
            if (SettingsManager.KEY_CAMERA_SAVEPATH.equals(key)) {
                Storage.setSaveSDCard(value.equals("1"));
                mActivity.updateStorageSpaceAndHint();
                continue;
            }
            if (SettingsManager.KEY_JPEG_QUALITY.equals(key)) {
                mJpegQuality = getQualityNumber(value);
                continue;
            }

            if (isBackMode()) {
                switch (getMode()) {
                    case BAYER_MODE:
                        updatePreviewBayer |= applyPreferenceToPreview(BAYER_ID, key, value);
                        break;
                    case MONO_MODE:
                        updatePreviewMono |= applyPreferenceToPreview(MONO_ID, key, value);
                        break;
                    case DUAL_MODE:
                        updatePreviewBayer |= applyPreferenceToPreview(BAYER_ID, key, value);
                        updatePreviewMono |= applyPreferenceToPreview(MONO_ID, key, value);
                        break;
                }
            } else {
                updatePreviewFront |= applyPreferenceToPreview(FRONT_ID, key, value);
            }
        }
        if (updatePreviewBayer) {
            try {
                mCaptureSession[BAYER_ID].setRepeatingRequest(mPreviewRequestBuilder[BAYER_ID]
                        .build(), mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewMono) {
            try {
                mCaptureSession[MONO_ID].setRepeatingRequest(mPreviewRequestBuilder[MONO_ID]
                        .build(), mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewFront) {
            try {
                mCaptureSession[FRONT_ID].setRepeatingRequest(mPreviewRequestBuilder[FRONT_ID]
                        .build(), mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
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
            int id = (int) msg.obj;
            switch (msg.what) {
                case OPEN_CAMERA:
                    openCamera(id);
                    break;
                case CANCEL_TOUCH_FOCUS:
                    mState[id] = STATE_PREVIEW;
                    mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                    setAFModeToPreview(id, mControlAFMode);
                    break;
            }
        }
    }

    @Override
    public void onClearSightSuccess() {
        Log.d(TAG, "onClearSightSuccess");
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RotateTextToast.makeText(mActivity, R.string.clearsight_capture_success,
                        Toast.LENGTH_SHORT).show();
            }
        });

        unlockFocus(BAYER_ID);
        unlockFocus(MONO_ID);
    }

    @Override
    public void onClearSightFailure() {
        Log.d(TAG, "onClearSightFailure");
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RotateTextToast.makeText(mActivity, R.string.clearsight_capture_fail,
                        Toast.LENGTH_SHORT).show();
            }
        });

        unlockFocus(BAYER_ID);
        unlockFocus(MONO_ID);
    }
}
