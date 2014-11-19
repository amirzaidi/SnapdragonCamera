/*
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
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraManager.CameraAFCallback;
import com.android.camera.CameraManager.CameraAFMoveCallback;
import com.android.camera.CameraManager.CameraPictureCallback;
import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.CameraManager.CameraShutterCallback;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;

import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;

import com.android.internal.util.MemInfoReader;
import android.app.ActivityManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Vector;
import java.util.HashMap;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemProperties;
import java.util.Collections;
import java.util.Formatter;

public class PhotoModule
        implements CameraModule,
        PhotoController,
        FocusOverlayManager.Listener,
        CameraPreference.OnPreferenceChangedListener,
        ShutterButton.OnShutterButtonListener,
        MediaSaveService.Listener,
        OnCountDownFinishedListener,
        SensorEventListener {

    private static final String TAG = "CAM_PhotoModule";

   //QCom data members
    public static boolean mBrightnessVisible = false;
    private static final int MAX_SHARPNESS_LEVEL = 6;
    private boolean mRestartPreview = false;
    private int mSnapshotMode;
    private int mBurstSnapNum = 1;
    private int mReceivedSnapNum = 0;
    public boolean mFaceDetectionEnabled = false;
    private DrawAutoHDR mDrawAutoHDR;
   /*Histogram variables*/
    private GraphView mGraphView;
    private static final int STATS_DATA = 257;
    public static int statsdata[] = new int[STATS_DATA];
    public boolean mHiston = false;
    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_CROP = 1000;

    private static final int SETUP_PREVIEW = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int CLEAR_SCREEN_DELAY = 3;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    private static final int SHOW_TAP_TO_FOCUS_TOAST = 5;
    private static final int SWITCH_CAMERA = 6;
    private static final int SWITCH_CAMERA_START_ANIMATION = 7;
    private static final int CAMERA_OPEN_DONE = 8;
    private static final int OPEN_CAMERA_FAIL = 9;
    private static final int CAMERA_DISABLED = 10;
    private static final int SET_SKIN_TONE_FACTOR = 11;
    private static final int SET_PHOTO_UI_PARAMS = 12;
    private static final int SWITCH_TO_GCAM_MODULE = 13;
    private static final int CONFIGURE_SKIN_TONE_FACTOR = 14;
    private static final int ON_PREVIEW_STARTED = 15;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // This is the delay before we execute onResume tasks when coming
    // from the lock screen, to allow time for onPause to execute.
    private static final int ON_RESUME_TASKS_DELAY_MSEC = 20;

    private static final String DEBUG_IMAGE_PREFIX = "DEBUG_";

    // copied from Camera hierarchy
    private CameraActivity mActivity;
    private CameraProxy mCameraDevice;
    private int mCameraId;
    private Parameters mParameters;
    private boolean mPaused;
    private View mRootView;

    private PhotoUI mUI;

    public boolean mAutoHdrEnable;
    // The activity is going to switch to the specified camera id. This is
    // needed because texture copy is done in GL thread. -1 means camera is not
    // switching.
    protected int mPendingSwitchCameraId = -1;
    private boolean mOpenCameraFail;
    private boolean mCameraDisabled;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private int mZoomValue;  // The current zoom value.

    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mContinuousFocusSupported;
    private boolean mTouchAfAecFlag;
    private boolean mLongshotSave = false;
    private boolean mRefocus = false;

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private ComboPreferences mPreferences;
    private String mPrevSavedCDS;
    private boolean isTNREnabled;

    private static final String sTempCropFilename = "crop-temp";

    private ContentProviderClient mMediaProviderClient;
    private boolean mFaceDetectionStarted = false;

    private static final String PERSIST_LONG_ENABLE = "persist.camera.longshot.enable";
    private static final String PERSIST_LONG_SAVE = "persist.camera.longshot.save";
    private static final String PERSIST_PREVIEW_RESTART = "persist.camera.feature.restart";

    private static final int MINIMUM_BRIGHTNESS = 0;
    private static final int MAXIMUM_BRIGHTNESS = 6;
    private static final int DEFAULT_BRIGHTNESS = 3;
    private int mbrightness = 3;
    private int mbrightness_step = 1;
    private ProgressBar brightnessProgressBar;
    // Constant from android.hardware.Camera.Parameters
    private static final String KEY_PICTURE_FORMAT = "picture-format";
    private static final String KEY_QC_RAW_PICUTRE_SIZE = "raw-size";
    public static final String PIXEL_FORMAT_JPEG = "jpeg";

    private static final int MIN_SCE_FACTOR = -10;
    private static final int MAX_SCE_FACTOR = +10;
    private int SCE_FACTOR_STEP = 10;
    private int mskinToneValue = 0;
    private boolean mSkinToneSeekBar= false;
    private boolean mSeekBarInitialized = false;
    private SeekBar skinToneSeekBar;
    private TextView LeftValue;
    private TextView RightValue;
    private TextView Title;

    private boolean mPreviewRestartSupport = false;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private Uri mDebugUri;

    // Used for check memory status for longshot mode
    // Currently, this cancel threshold selection is based on test experiments,
    // we can change it based on memory status or other requirements.
    private static final int LONGSHOT_CANCEL_THRESHOLD = 40;
    private MemInfoReader mMemInfoReader = new MemInfoReader();
    private ActivityManager mAm;
    private long SECONDARY_SERVER_MEM;
    private long mMB = 1024 * 1024;
    private boolean mLongshotActive = false;

    // We use a queue to generated names of the images to be used later
    // when the image is ready to be saved.
    private NamedImages mNamedImages;

    private Runnable mDoSnapRunnable = new Runnable() {
        @Override
        public void run() {
            onShutterButtonClick();
        }
    };

    private class OpenCameraThread extends Thread {
        @Override
        public void run() {
            openCamera();
            startPreview();
        }
    }

    private OpenCameraThread mOpenCameraThread = null;
    /**
     * An unpublished intent flag requesting to return as soon as capturing
     * is completed.
     *
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mCameraDisplayOrientation;
    // The value for UI components like indicators.
    private int mDisplayOrientation;
    // The value for android.hardware.Camera.Parameters.setRotation.
    private int mJpegRotation;
    // Indicates whether we are using front camera
    private boolean mMirror;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;

    private int mCameraState = INIT;
    private boolean mSnapshotOnIdle = false;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new AutoFocusMoveCallback()
                    : null;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
    private final StatsCallback mStatsCallback = new StatsCallback();
    private final MetaDataCallback mMetaDataCallback = new MetaDataCallback();
    private long mFocusStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private byte[] mJpegImageData;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;
    public long mCaptureStartTime;

    // This handles everything about focus.
    private FocusOverlayManager mFocusManager;

    private String mSceneMode;
    private String mCurrTouchAfAec = Parameters.TOUCH_AF_AEC_ON;

    private final Handler mHandler = new MainHandler();
    private MessageQueue.IdleHandler mIdleHandler = null;

    private PreferenceGroup mPreferenceGroup;

    private boolean mQuickCapture;
    private SensorManager mSensorManager;
    private float[] mGData = new float[3];
    private float[] mMData = new float[3];
    private float[] mR = new float[16];
    private int mHeading = -1;

    // True if all the parameters needed to start preview is ready.
    private boolean mCameraPreviewParamsReady = false;

    private int mManual3AEnabled = 0;
    private static final int MANUAL_FOCUS = 1;
    private static final int MANUAL_WB = 2;
    private static final int MANUAL_EXPOSURE = 4;

    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    private void checkDisplayRotation() {
        // Set the display orientation if display rotation has changed.
        // Sometimes this happens when the device is held upside
        // down and camera app is opened. Rotation animation will
        // take some time and the rotation value we have got may be
        // wrong. Framework does not have a callback for this now.
        if (CameraUtil.getDisplayRotation(mActivity) != mDisplayRotation) {
            setDisplayOrientation();
        }
        if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkDisplayRotation();
                }
            }, 100);
        }
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        public MainHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SETUP_PREVIEW: {
                    setupPreview();
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }

                case SHOW_TAP_TO_FOCUS_TOAST: {
                    showTapToFocusToast();
                    break;
                }

                case SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case SWITCH_CAMERA_START_ANIMATION: {
                    // TODO: Need to revisit
                    // ((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();
                    break;
                }

                case CAMERA_OPEN_DONE: {
                    onCameraOpened();
                    break;
                }

                case OPEN_CAMERA_FAIL: {
                    mOpenCameraFail = true;
                    CameraUtil.showErrorAndFinish(mActivity,
                            R.string.cannot_connect_camera);
                    break;
                }

                case CAMERA_DISABLED: {
                    mCameraDisabled = true;
                    CameraUtil.showErrorAndFinish(mActivity,
                            R.string.camera_disabled);
                    break;
                }
               case SET_SKIN_TONE_FACTOR: {
                    Log.v(TAG, "set tone bar: mSceneMode = " + mSceneMode);
                    setSkinToneFactor();
                    mSeekBarInitialized = true;
                    // skin tone ie enabled only for party and portrait BSM
                    // when color effects are not enabled
                    String colorEffect = mPreferences.getString(
                        CameraSettings.KEY_COLOR_EFFECT,
                        mActivity.getString(R.string.pref_camera_coloreffect_default));
                    if((Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
                        Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode))&&
                        (Parameters.EFFECT_NONE.equals(colorEffect))) {
                        ;
                    }
                    else{
                        Log.v(TAG, "Skin tone bar: disable");
                        disableSkinToneSeekBar();
                    }
                    break;
               }
               case SET_PHOTO_UI_PARAMS: {
                    setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
                    mUI.updateOnScreenIndicators(mParameters, mPreferenceGroup,
                        mPreferences);
                    break;
               }

                case SWITCH_TO_GCAM_MODULE: {
                    mActivity.onModuleSelected(ModuleSwitcher.GCAM_MODULE_INDEX);
                }

                case CONFIGURE_SKIN_TONE_FACTOR: {
                     if ((mCameraDevice != null) && isCameraIdle()) {
                         synchronized (mCameraDevice) {
                             mParameters = mCameraDevice.getParameters();
                             mParameters.set("skinToneEnhancement", String.valueOf(msg.arg1));
                             mCameraDevice.setParameters(mParameters);
                         }
                    }
                    break;
                }
                case ON_PREVIEW_STARTED: {
                    onPreviewStarted();
                    break;
                }
            }
        }
    }


    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;
        mPreferences = new ComboPreferences(mActivity);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = getPreferredCameraId(mPreferences);

        mContentResolver = mActivity.getContentResolver();
        mAm = (ActivityManager)(mActivity.getSystemService(Context.ACTIVITY_SERVICE));

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsImageCaptureIntent = isImageCaptureIntent();

        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        if (mOpenCameraThread == null && !mActivity.mIsModuleSwitchInProgress) {
            mOpenCameraThread = new OpenCameraThread();
            mOpenCameraThread.start();
        }
        mUI = new PhotoUI(activity, this, parent);
        initializeControlByIntent();
        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = new LocationManager(mActivity, mUI);
        mSensorManager = (SensorManager)(mActivity.getSystemService(Context.SENSOR_SERVICE));

        brightnessProgressBar = (ProgressBar)mRootView.findViewById(R.id.progress);
        if (brightnessProgressBar instanceof SeekBar) {
            SeekBar seeker = (SeekBar) brightnessProgressBar;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        brightnessProgressBar.setMax(MAXIMUM_BRIGHTNESS);
        mbrightness = mPreferences.getInt(
                 CameraSettings.KEY_BRIGHTNESS,
                 DEFAULT_BRIGHTNESS);
        brightnessProgressBar.setProgress(mbrightness);
        skinToneSeekBar = (SeekBar) mRootView.findViewById(R.id.skintoneseek);
        skinToneSeekBar.setOnSeekBarChangeListener(mskinToneSeekListener);
        skinToneSeekBar.setVisibility(View.INVISIBLE);
        Title = (TextView)mRootView.findViewById(R.id.skintonetitle);
        RightValue = (TextView)mRootView.findViewById(R.id.skintoneright);
        LeftValue = (TextView)mRootView.findViewById(R.id.skintoneleft);
        Storage.setSaveSDCard(
            mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));

        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        mAm.getMemoryInfo(memInfo);
        SECONDARY_SERVER_MEM = memInfo.secondaryServerThreshold;

    }

    private void initializeControlByIntent() {
        mUI.initializeControlByIntent();
        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        }
    }

    private void onPreviewStarted() {
        setCameraState(IDLE);
        mFocusManager.onPreviewStarted();
        startFaceDetection();
        locationFirstRun();
        mUI.enableShutter(true);
    }

    // Prompt the user to pick to record location for the very first run of
    // camera only
    private void locationFirstRun() {
        if (RecordLocationPreference.isSet(mPreferences)) {
            return;
        }
        if (mActivity.isSecureCamera()) return;
        // Check if the back camera exists
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId == -1) {
            // If there is no back camera, do not show the prompt.
            return;
        }
        mUI.showLocationDialog();
    }

    @Override
    public void enableRecordingLocation(boolean enable) {
        setLocationPreference(enable ? RecordLocationPreference.VALUE_ON
                : RecordLocationPreference.VALUE_OFF);
    }

    @Override
    public void onPreviewUIReady() {
        if (mPaused || mCameraDevice == null) {
            return;
        }
        Log.v(TAG, "onPreviewUIReady");
        if (mCameraState == PREVIEW_STOPPED || mCameraState == INIT) {
            startPreview();
        } else {
            SurfaceTexture st = mUI.getSurfaceTexture();
            if (st == null) {
                Log.w(TAG, "startPreview: surfaceTexture is not ready.");
                return;
            }
            mCameraDevice.setPreviewTexture(st);
        }
    }

    @Override
    public void onPreviewUIDestroyed() {
        if (mCameraDevice == null) {
            return;
        }
        Log.v(TAG, "onPreviewUIDestroyed");
        mCameraDevice.setPreviewTexture(null);
        stopPreview();
    }

    private void setLocationPreference(String value) {
        mPreferences.edit()
                .putString(CameraSettings.KEY_RECORD_LOCATION, value)
                .apply();
        // TODO: Fix this to use the actual onSharedPreferencesChanged listener
        // instead of invoking manually
        onSharedPreferenceChanged();
    }

    private void onCameraOpened() {
        if (mPaused) {
            return;
        }
        Log.v(TAG, "onCameraOpened");
        openCameraCommon();
        resizeForPreviewAspectRatio();
        updateFocusManager(mUI);
    }

    private void switchCamera() {
        if (mPaused) return;

        Log.v(TAG, "Start to switch camera. id=" + mPendingSwitchCameraId);
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        setCameraId(mCameraId);

        // from onPause
        closeCamera();
        mUI.collapseCameraControls();
        mUI.clearFaces();
        disableSkinToneSeekBar();
        if (mFocusManager != null) mFocusManager.removeMessages();

        // Restart the camera and initialize the UI. From onCreate.
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        mCameraDevice = CameraUtil.openCamera(
                mActivity, mCameraId, mHandler,
                mActivity.getCameraOpenErrorCallback());

        if (mCameraDevice == null) {
            Log.e(TAG, "Failed to open camera:" + mCameraId + ", aborting.");
            return;
        }
        mParameters = mCameraDevice.getParameters();
        mInitialParams = mParameters;
        initializeCapabilities();
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.setMirror(mMirror);
        mFocusManager.setParameters(mInitialParams);
        setupPreview();

        // reset zoom value index
        mZoomValue = 0;
        resizeForPreviewAspectRatio();
        openCameraCommon();

        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    // either open a new camera or switch cameras
    private void openCameraCommon() {
        loadCameraPreferences();

        mUI.onCameraOpened(mPreferenceGroup, mPreferences, mParameters, this);
        if (mIsImageCaptureIntent) {
            mUI.overrideSettings(CameraSettings.KEY_CAMERA_HDR_PLUS,
                    mActivity.getString(R.string.setting_off_value));
        }
        updateCameraSettings();
        showTapToFocusToastIfNeeded();
        resetManual3ASettings();
    }

    @Override
    public void onScreenSizeChanged(int width, int height) {
        if (mFocusManager != null) mFocusManager.setPreviewSize(width, height);
    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {
        if (mFocusManager != null) mFocusManager.setPreviewRect(previewRect);
    }

    private void resetExposureCompensation() {
        String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_EXPOSURE, "0");
            editor.apply();
        }
    }

    private void resetManual3ASettings() {
        String manualExposureDefault = mActivity.getString(
                R.string.pref_camera_manual_exp_default);
        String manualExposureMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_EXPOSURE, manualExposureDefault);
        if (!manualExposureMode.equals(manualExposureDefault)) {
            mUI.setPreference(
                    CameraSettings.KEY_MANUAL_EXPOSURE, manualExposureDefault);
            UpdateManualExposureSettings();
        }
        String manualFocusDefault = mActivity.getString(
                R.string.pref_camera_manual_focus_default);
        String manualFocusMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_FOCUS, manualFocusDefault);
        if (!manualFocusMode.equals(manualFocusDefault)) {
            mUI.setPreference(
                    CameraSettings.KEY_MANUAL_FOCUS, manualFocusDefault);
            UpdateManualFocusSettings();
        }
        String manualWBDefault = mActivity.getString(
                R.string.pref_camera_manual_wb_default);
        String manualWBMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_WB, manualWBDefault);
        if (!manualWBMode.equals(manualWBDefault)) {
            mUI.setPreference(
                    CameraSettings.KEY_MANUAL_WB, manualWBDefault);
            UpdateManualWBSettings();
        }
        mManual3AEnabled = 0;
    }

    void setPreviewFrameLayoutCameraOrientation(){
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        //if camera mount angle is 0 or 180, we want to resize preview
        if (info.orientation % 180 == 0){
            mUI.cameraOrientationPreviewResize(true);
        } else{
            mUI.cameraOrientationPreviewResize(false);
        }
    }

    @Override
    public void resizeForPreviewAspectRatio() {
        if ( mCameraDevice == null || mParameters == null) {
            Log.e(TAG, "Camera not yet initialized");
            return;
        }
        setPreviewFrameLayoutCameraOrientation();
        Size size = mParameters.getPreviewSize();
        Log.e(TAG,"Width = "+ size.width+ "Height = "+size.height);
        mUI.setAspectRatio((float) size.width / size.height);
    }

    @Override
    public void onSwitchSavePath() {
        mUI.setPreference(CameraSettings.KEY_CAMERA_SAVEPATH, "1");
        Toast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = mContentResolver
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);

        keepMediaProviderInstance();

        mUI.initializeFirstTime();
        MediaSaveService s = mActivity.getMediaSaveService();
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (s != null) {
            s.setListener(this);
        }

        mNamedImages = new NamedImages();
        mGraphView = (GraphView)mRootView.findViewById(R.id.graph_view);
        mDrawAutoHDR = (DrawAutoHDR )mRootView.findViewById(R.id.autohdr_view);
        if (mGraphView == null || mDrawAutoHDR == null){
            Log.e(TAG, "mGraphView or mDrawAutoHDR is null");
        } else{
            mGraphView.setPhotoModuleObject(this);
            mDrawAutoHDR.setPhotoModuleObject(this);
        }

        mFirstTimeInitialized = true;
        Log.d(TAG, "addIdleHandler in first time initialization");
        addIdleHandler();

    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start location update if needed.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(this);
        }
        mNamedImages = new NamedImages();
        if (!mIsImageCaptureIntent) {
            mUI.showSwitcher();
        }
        mUI.initializeSecondTime(mParameters);
        keepMediaProviderInstance();
    }

    private void showTapToFocusToastIfNeeded() {
        // Show the tap to focus toast if this is the first start.
        if (mFocusAreaSupported &&
                mPreferences.getBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, true)) {
            // Delay the toast for one second to wait for orientation.
            mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_FOCUS_TOAST, 1000);
        }
    }

    private void addIdleHandler() {
        if (mIdleHandler == null) {
            mIdleHandler = new MessageQueue.IdleHandler() {
                @Override
                public boolean queueIdle() {
                    Storage.ensureOSXCompatible();
                    return false;
                }
            };

            MessageQueue queue = Looper.myQueue();
            queue.addIdleHandler(mIdleHandler);
        }
    }

    private void removeIdleHandler() {
        if (mIdleHandler != null) {
            MessageQueue queue = Looper.myQueue();
            queue.removeIdleHandler(mIdleHandler);
            mIdleHandler = null;
        }
    }

    @Override
    public void startFaceDetection() {
        if (mCameraDevice == null) return;

        if (mFaceDetectionEnabled == false
               || mFaceDetectionStarted || mCameraState != IDLE) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mUI.onStartFaceDetection(mDisplayOrientation,
                    (info.facing == CameraInfo.CAMERA_FACING_FRONT));
            mCameraDevice.setFaceDetectionCallback(mHandler, mUI);
            mCameraDevice.startFaceDetection();
        }
    }

    @Override
    public void stopFaceDetection() {
        if (mFaceDetectionEnabled == false || !mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionCallback(null, null);
            mUI.pauseFaceDetection();
            mCameraDevice.stopFaceDetection();
            mUI.onStopFaceDetection();
        }
    }

    private boolean isLongshotNeedCancel() {

        long totalMemory = Runtime.getRuntime().totalMemory() / mMB;
        long maxMemory = Runtime.getRuntime().maxMemory() / mMB;
        long remainMemory = maxMemory - totalMemory;

        mMemInfoReader.readMemInfo();
        long availMem = mMemInfoReader.getFreeSize() + mMemInfoReader.getCachedSize()
            - SECONDARY_SERVER_MEM;
        availMem = availMem/ mMB;

        if(availMem <= 0 ||
            remainMemory <= LONGSHOT_CANCEL_THRESHOLD) {
            Log.d(TAG, "memory used up, need cancel longshot.");
            mLongshotActive = false;
            Toast.makeText(mActivity,R.string.msg_cancel_longshot_for_limited_memory,
                Toast.LENGTH_SHORT).show();
            return true;
        }

        return false;
    }

    private final class LongshotShutterCallback
            implements CameraShutterCallback {

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.e(TAG, "[KPI Perf] PROFILE_SHUTTER_LAG mShutterLag = " + mShutterLag + "ms");
            synchronized(mCameraDevice) {

                if (mCameraState != LONGSHOT ||
                    !mLongshotActive) {
                    return;
                }

                if(isLongshotNeedCancel()) {
                    return;
                }

                mUI.doShutterAnimation();

                if (mLongshotSave) {
                    mCameraDevice.takePicture(mHandler,
                            new LongshotShutterCallback(),
                            mRawPictureCallback, mPostViewPictureCallback,
                            new LongshotPictureCallback(null));
                } else {
                    mCameraDevice.takePicture(mHandler,new LongshotShutterCallback(),
                            mRawPictureCallback, mPostViewPictureCallback,
                            new JpegPictureCallback(null));
                }
            }
        }
    }

    private final class ShutterCallback
            implements CameraShutterCallback {

        private boolean mNeedsAnimation;

        public ShutterCallback(boolean needsAnimation) {
            mNeedsAnimation = needsAnimation;
        }

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.e(TAG, "[KPI Perf] PROFILE_SHUTTER_LAG mShutterLag = " + mShutterLag + "ms");
            if (mNeedsAnimation) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        animateAfterShutter();
                    }
                });
            }
        }
    }
    private final class StatsCallback
           implements android.hardware.Camera.CameraDataCallback {
            @Override
        public void onCameraData(int [] data, android.hardware.Camera camera) {
            //if(!mPreviewing || !mHiston || !mFirstTimeInitialized){
            if(!mHiston || !mFirstTimeInitialized){
                return;
            }
            /*The first element in the array stores max hist value . Stats data begin from second value*/
            synchronized(statsdata) {
                System.arraycopy(data,0,statsdata,0,STATS_DATA);
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if(mGraphView != null)
                        mGraphView.PreviewChanged();
                }
           });
        }
    }

    private final class MetaDataCallback
           implements android.hardware.Camera.CameraMetaDataCallback{
        @Override
        public void onCameraMetaData (byte[] data, android.hardware.Camera camera) {
            int metadata[] = new int[3];
            if (data.length <= 12) {
                for (int i =0;i<3;i++) {
                    metadata[i] = byteToInt( (byte []) data, i*4);
                }
                if (metadata[2] == 1) {
                    mAutoHdrEnable = true;
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (mDrawAutoHDR != null)
                                mDrawAutoHDR.AutoHDR();
                        }
                    });
                }
                else {
                    mAutoHdrEnable = false;
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (mDrawAutoHDR != null)
                                mDrawAutoHDR.AutoHDR();
                        }
                    });
                }
            }
        }

        private int byteToInt (byte[] b, int offset) {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                value += (b[(3-i) + offset] & 0x000000FF) << shift;
            }
            return value;
        }
    }

    private final class PostViewPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte [] data, CameraProxy camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte [] rawData, CameraProxy camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class LongshotPictureCallback implements CameraPictureCallback {
        Location mLocation;

        public LongshotPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(final byte [] jpegData, CameraProxy camera) {
            if (mPaused) {
                return;
            }

            mFocusManager.updateFocusUI(); // Ensure focus indicator is hidden.

            String jpegFilePath = new String(jpegData);
            mNamedImages.nameNewImage(mCaptureStartTime);
            NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;

            if (title == null) {
                Log.e(TAG, "Unbalanced name/data pair");
                return;
            }


            if  (date == -1 ) {
                Log.e(TAG, "Invalid filename date");
                return;
            }

            String dstPath = Storage.DIRECTORY;
            File sdCard = android.os.Environment.getExternalStorageDirectory();
            File dstFile = new File(dstPath);
            if (dstFile == null) {
                Log.e(TAG, "Destination file path invalid");
                return;
            }

            File srcFile = new File(jpegFilePath);
            if (srcFile == null) {
                Log.e(TAG, "Source file path invalid");
                return;
            }

            if ( srcFile.renameTo(dstFile) ) {
                Size s = mParameters.getPictureSize();
                String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                mActivity.getMediaSaveService().addImage(
                       null, title, date, mLocation, s.width, s.height,
                       0, null, mOnMediaSavedListener, mContentResolver, pictureFormat);
            } else {
                Log.e(TAG, "Failed to move jpeg file");
            }
        }
    }

    private final class JpegPictureCallback
            implements CameraPictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(final byte [] jpegData, CameraProxy camera) {
            mUI.enableShutter(true);
            if (mPaused) {
                return;
            }
            if (mIsImageCaptureIntent) {
                stopPreview();
            } else if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
                mUI.showSwitcher();
                mUI.setSwipingEnabled(true);
            }

            mReceivedSnapNum = mReceivedSnapNum + 1;
            mJpegPictureCallbackTime = System.currentTimeMillis();
            if(mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
                Log.v(TAG, "JpegPictureCallback : in zslmode");
                mParameters = mCameraDevice.getParameters();
                mBurstSnapNum = mParameters.getInt("num-snaps-per-shutter");
            }
            Log.v(TAG, "JpegPictureCallback: Received = " + mReceivedSnapNum +
                      "Burst count = " + mBurstSnapNum);
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            mFocusManager.updateFocusUI(); // Ensure focus indicator is hidden.

            boolean needRestartPreview = !mIsImageCaptureIntent
                    && !mPreviewRestartSupport
                    && (mCameraState != LONGSHOT)
                    && (mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL)
                    && (mReceivedSnapNum == mBurstSnapNum);
            if (needRestartPreview) {
                setupPreview();
                if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(
                    mFocusManager.getFocusMode())) {
                    mCameraDevice.cancelAutoFocus();
                }
            } else if ((mReceivedSnapNum == mBurstSnapNum)
                        && (mCameraState != LONGSHOT)){
                mFocusManager.resetTouchFocus();
                if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(
                        mFocusManager.getFocusMode())) {
                    mCameraDevice.cancelAutoFocus();
                }
                mUI.resumeFaceDetection();
                if (!mIsImageCaptureIntent) {
                    setCameraState(IDLE);
                }
                startFaceDetection();
            }
            if ((mRefocus) && (mReceivedSnapNum == 6)) {
                Size s = mParameters.getPictureSize();
                mNamedImages.nameNewImage(mCaptureStartTime, mRefocus);
                NamedEntity name = mNamedImages.getNextNameEntity();
                String title = (name == null) ? null : name.title;
                long date = (name == null) ? -1 : name.date;
                if (title == null) {
                    Log.e(TAG, "Unbalanced name/data pair");
                    return;
                }
                if (date == -1) {
                    Log.e(TAG, "Invalid filename date");
                    return;
                }
                mActivity.getMediaSaveService().addImage(
                        jpegData, title, date, mLocation, s.width, s.height,
                        0, null, mOnMediaSavedListener, mContentResolver, ".jpeg");

            } else {
                ExifInterface exif = Exif.getExif(jpegData);
                int orientation = Exif.getOrientation(exif);
                if (!mIsImageCaptureIntent) {
                    // Burst snapshot. Generate new image name.
                    if (mReceivedSnapNum > 1) {
                        mNamedImages.nameNewImage(mCaptureStartTime, mRefocus);
                    }
                    // Calculate the width and the height of the jpeg.
                    Size s = mParameters.getPictureSize();
                    int width, height;
                    if ((mJpegRotation + orientation) % 180 == 0) {
                        width = s.width;
                        height = s.height;
                    } else {
                        width = s.height;
                        height = s.width;
                    }
                    String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                    if (pictureFormat != null && !pictureFormat.equalsIgnoreCase(PIXEL_FORMAT_JPEG)) {
                        // overwrite width and height if raw picture
                        String pair = mParameters.get(KEY_QC_RAW_PICUTRE_SIZE);
                        if (pair != null) {
                            int pos = pair.indexOf('x');
                            if (pos != -1) {
                                width = Integer.parseInt(pair.substring(0, pos));
                                height = Integer.parseInt(pair.substring(pos + 1));
                            }
                        }
                    }
                    NamedEntity name = mNamedImages.getNextNameEntity();
                    String title = (name == null) ? null : name.title;
                    long date = (name == null) ? -1 : name.date;
                    // Handle debug mode outputs
                    if (mDebugUri != null) {
                        // If using a debug uri, save jpeg there.
                        saveToDebugUri(jpegData);
                        // Adjust the title of the debug image shown in mediastore.
                        if (title != null) {
                            title = DEBUG_IMAGE_PREFIX + title;
                        }
                     }
                     if (title == null) {
                         Log.e(TAG, "Unbalanced name/data pair");
                     } else {
                        if (date == -1) {
                            date = mCaptureStartTime;
                        }
                        if (mHeading >= 0) {
                            // heading direction has been updated by the sensor.
                            ExifTag directionRefTag = exif.buildTag(
                              ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                              ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
                            ExifTag directionTag = exif.buildTag(
                              ExifInterface.TAG_GPS_IMG_DIRECTION,
                              new Rational(mHeading, 1));
                            exif.setTag(directionRefTag);
                            exif.setTag(directionTag);
                        }
                        String mPictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                            mActivity.getMediaSaveService().addImage(
                                    jpegData, title, date, mLocation, width, height,
                                    orientation, exif, mOnMediaSavedListener,
                                    mContentResolver, mPictureFormat);
                        }
                        // Animate capture with real jpeg data instead of a preview frame.
                        if (mCameraState != LONGSHOT) {
                            Size pic_size = mParameters.getPictureSize();
                            if ((pic_size.width <= 352) && (pic_size.height<= 288)) {
                                mUI.setDownFactor(2); //Downsample by 2 for CIF & below
                            } else {
                                mUI.setDownFactor(4);
                            }
                            mUI.animateCapture(jpegData, orientation, mMirror);
                        }
                    } else {
                        mJpegImageData = jpegData;
                        if (!mQuickCapture) {
                            mUI.showCapturedImageForReview(jpegData, orientation, mMirror);
                        } else {
                            onCaptureDone();
                        }
                    }
                    // Check this in advance of each shot so we don't add to shutter
                    // latency. It's true that someone else could write to the SD card in
                    // the mean time and fill it, but that could have happened between the
                    // shutter press and saving the JPEG too.
                    mActivity.updateStorageSpaceAndHint();
                    long now = System.currentTimeMillis();
                    mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                    Log.v(TAG, "mJpegCallbackFinishTime = "
                            + mJpegCallbackFinishTime + "ms");

                    if (mReceivedSnapNum == mBurstSnapNum) {
                        mJpegPictureCallbackTime = 0;
                    }

                    if (mHiston && (mSnapshotMode ==CameraInfo.CAMERA_SUPPORT_MODE_ZSL)) {
                        mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (mGraphView != null) {
                                mGraphView.setVisibility(View.VISIBLE);
                                mGraphView.PreviewChanged();
                            }
                        }
                    });
                }
                if (mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL &&
                        mCameraState != LONGSHOT &&
                        mReceivedSnapNum == mBurstSnapNum &&
                        !mIsImageCaptureIntent) {
                    cancelAutoFocus();
                }
            }
        }
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        // no support
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromtouch) {
        }
        public void onStopTrackingTouch(SeekBar bar) {
        }
    };

    private OnSeekBarChangeListener mskinToneSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        // no support
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromtouch) {
            int value = (progress + MIN_SCE_FACTOR) * SCE_FACTOR_STEP;
            if(progress > (MAX_SCE_FACTOR - MIN_SCE_FACTOR)/2){
                RightValue.setText(String.valueOf(value));
                LeftValue.setText("");
            } else if (progress < (MAX_SCE_FACTOR - MIN_SCE_FACTOR)/2){
                LeftValue.setText(String.valueOf(value));
                RightValue.setText("");
            } else {
                LeftValue.setText("");
                RightValue.setText("");
            }
            if (value != mskinToneValue && mCameraDevice != null) {
                mskinToneValue = value;
                Message msg = mHandler.obtainMessage(CONFIGURE_SKIN_TONE_FACTOR, mskinToneValue, 0);
                mHandler.sendMessage(msg);
            }
        }

        public void onStopTrackingTouch(SeekBar bar) {
            Log.v(TAG, "Set onStopTrackingTouch mskinToneValue = " + mskinToneValue);
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR,
                             Integer.toString(mskinToneValue));
            editor.apply();
        }
    };
    private final class AutoFocusCallback implements CameraAFCallback {
        @Override
        public void onAutoFocus(
                boolean focused, CameraProxy camera) {
            if (mPaused) return;

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            //don't reset the camera state while capture is in progress
            //otherwise, it might result in another takepicture
            switch (mCameraState) {
                case PhotoController.LONGSHOT:
                case SNAPSHOT_IN_PROGRESS:
                    break;
                default:
                    setCameraState(IDLE);
                    break;
            }
            mFocusManager.onAutoFocus(focused, mUI.isShutterPressed());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private final class AutoFocusMoveCallback
            implements CameraAFMoveCallback {
        @Override
        public void onAutoFocusMoving(
                boolean moving, CameraProxy camera) {
            mFocusManager.onAutoFocusMoving(moving);
        }
    }

    /**
     * This class is just a thread-safe queue for name,date holder objects.
     */
    public static class NamedImages {
        private Vector<NamedEntity> mQueue;

        public NamedImages() {
            mQueue = new Vector<NamedEntity>();
        }

        public void nameNewImage(long date) {
            NamedEntity r = new NamedEntity();
            r.title = CameraUtil.createJpegName(date);
            r.date = date;
            mQueue.add(r);
        }

        public void nameNewImage(long date, boolean refocus) {
            NamedEntity r = new NamedEntity();
            r.title = CameraUtil.createJpegName(date, refocus);
            r.date = date;
            mQueue.add(r);
        }

        public NamedEntity getNextNameEntity() {
            synchronized(mQueue) {
                if (!mQueue.isEmpty()) {
                    return mQueue.remove(0);
                }
            }
            return null;
        }

        public static class NamedEntity {
            public String title;
            public long date;
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case PhotoController.PREVIEW_STOPPED:
            case PhotoController.SNAPSHOT_IN_PROGRESS:
            case PhotoController.LONGSHOT:
            case PhotoController.SWITCHING_CAMERA:
                mUI.enableGestures(false);
                break;
            case PhotoController.IDLE:
                mUI.enableGestures(true);
                break;
        }
    }

    private void animateAfterShutter() {
        // Only animate when in full screen capture mode
        // i.e. If monkey/a user swipes to the gallery during picture taking,
        // don't show animation
        if (!mIsImageCaptureIntent) {
            mUI.animateFlash();
        }
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot or the image save request
        // is full then ignore.
        if (mCameraDevice == null || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mActivity.getMediaSaveService() == null
                || mActivity.getMediaSaveService().isQueueFull()) {
            return false;
        }
        mCaptureStartTime = System.currentTimeMillis();
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        final boolean animateBefore = (mSceneMode == CameraUtil.SCENE_MODE_HDR);
        if(mHiston) {
            if (mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
                mHiston = false;
                mCameraDevice.setHistogramMode(null);
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if(mGraphView != null)
                        mGraphView.setVisibility(View.INVISIBLE);
                }
            });
        }

        if (animateBefore) {
            animateAfterShutter();
        }

        // Set rotation and gps data.
        int orientation;
        // We need to be consistent with the framework orientation (i.e. the
        // orientation of the UI.) when the auto-rotate screen setting is on.
        if (mActivity.isAutoRotateScreen()) {
            orientation = (360 - mDisplayRotation) % 360;
        } else {
            orientation = mOrientation;
        }
        mJpegRotation = CameraUtil.getJpegRotation(mCameraId, orientation);
        mParameters.setRotation(mJpegRotation);
        String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
        Location loc = null;
        if (pictureFormat != null &&
              PIXEL_FORMAT_JPEG.equalsIgnoreCase(pictureFormat)) {
            loc = mLocationManager.getCurrentLocation();
        }
        CameraUtil.setGpsParameters(mParameters, loc);

        if (mRefocus) {
            mParameters.set(CameraSettings.KEY_QC_LEGACY_BURST,
                    CameraSettings.KEY_QC_RE_FOCUS_COUNT);
        } else {
            mParameters.remove(CameraSettings.KEY_QC_LEGACY_BURST);
        }

        mCameraDevice.setParameters(mParameters);
        mParameters = mCameraDevice.getParameters();

        mBurstSnapNum = mParameters.getInt("num-snaps-per-shutter");
        mReceivedSnapNum = 0;
        mPreviewRestartSupport = SystemProperties.getBoolean(
                PERSIST_PREVIEW_RESTART, false);
        mPreviewRestartSupport &= CameraSettings.isInternalPreviewSupported(
                mParameters);
        mPreviewRestartSupport &= (mBurstSnapNum == 1);
        mPreviewRestartSupport &= PIXEL_FORMAT_JPEG.equalsIgnoreCase(
                pictureFormat);

        // We don't want user to press the button again while taking a
        // multi-second HDR photo. For longshot, no need to disable.
        if (mCameraState != LONGSHOT) {
            mUI.enableShutter(false);
        }

        if (mCameraState == LONGSHOT) {
            if(mLongshotSave) {
                mCameraDevice.takePicture(mHandler,
                        new LongshotShutterCallback(),
                        mRawPictureCallback, mPostViewPictureCallback,
                        new LongshotPictureCallback(loc));
            } else {
                mCameraDevice.takePicture(mHandler,
                        new LongshotShutterCallback(),
                        mRawPictureCallback, mPostViewPictureCallback,
                        new JpegPictureCallback(loc));
            }
        } else {
            mCameraDevice.takePicture(mHandler,
                    new ShutterCallback(!animateBefore),
                    mRawPictureCallback, mPostViewPictureCallback,
                    new JpegPictureCallback(loc));
            setCameraState(SNAPSHOT_IN_PROGRESS);
        }

        mNamedImages.nameNewImage(mCaptureStartTime, mRefocus);

        if (mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
            mFaceDetectionStarted = false;
        }
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                UsageStatistics.ACTION_CAPTURE_DONE, "Photo", 0,
                UsageStatistics.hashFileName(mNamedImages.mQueue.lastElement().title + ".jpg"));
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    private int getPreferredCameraId(ComboPreferences preferences) {
        int intentCameraId = CameraUtil.getCameraFacingIntentExtras(mActivity);
        if (intentCameraId != -1) {
            // Testing purpose. Launch a specific camera through the intent
            // extras.
            return intentCameraId;
        } else {
            return CameraSettings.readPreferredCameraId(preferences);
        }
    }

    private void updateCommonManual3ASettings() {
        String touchAfAec = mParameters.TOUCH_AF_AEC_OFF;
        mSceneMode = Parameters.SCENE_MODE_AUTO;
        String flashMode = Parameters.FLASH_MODE_OFF;
        String redeyeReduction = mActivity.getString(R.string.
                pref_camera_redeyereduction_entry_disable);
        String aeBracketing = mActivity.getString(R.string.
                pref_camera_ae_bracket_hdr_entry_off);
        String colorEffect = mActivity.getString(R.string.
                pref_camera_coloreffect_default);
        String exposureCompensation = CameraSettings.EXPOSURE_DEFAULT_VALUE;

        if (mManual3AEnabled > 0) {
            overrideCameraSettings(flashMode, null, null,
                                   exposureCompensation, touchAfAec,
                                   mParameters.getAutoExposure(),
                                   Integer.toString(mParameters.getSaturation()),
                                   Integer.toString(mParameters.getContrast()),
                                   Integer.toString(mParameters.getSharpness()),
                                   colorEffect,
                                   mSceneMode, redeyeReduction, aeBracketing);
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT,
                        mActivity.getString(R.string.setting_off_value));
        } else {
            //enable all
            touchAfAec = mActivity.getString(
                    R.string.pref_camera_touchafaec_default);
            overrideCameraSettings(null, null, null,
                                   null, touchAfAec, null,
                                   null, null, null, null,
                                   null, null, null);
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT, null);
        }

        String isoMode = mParameters.getISOValue();
        final String isoManual = CameraSettings.KEY_MANUAL_ISO;
        if (isoMode.equals(isoManual)) {
            final String isoPref = mPreferences.getString(
                    CameraSettings.KEY_ISO,
                    mActivity.getString(R.string.pref_camera_iso_default));
            mUI.overrideSettings(CameraSettings.KEY_ISO, isoPref);
        }
        if ((mManual3AEnabled & MANUAL_WB) != 0) {
            String whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    mActivity.getString(R.string.pref_camera_whitebalance_default));
            mUI.overrideSettings(CameraSettings.KEY_WHITE_BALANCE, whiteBalance);
        }
        if ((mManual3AEnabled & MANUAL_FOCUS) != 0) {
            mUI.overrideSettings(CameraSettings.KEY_FOCUS_MODE,
                    mFocusManager.getFocusMode());
        }
    }

    private void updateCameraSettings() {
        String sceneMode = null;
        String flashMode = null;
        String redeyeReduction = null;
        String aeBracketing = null;
        String focusMode = null;
        String colorEffect = null;
        String exposureCompensation = null;
        String touchAfAec = null;
        boolean disableLongShot = false;

        String ubiFocusOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_ubifocus_on);
        String continuousShotOn =
                mActivity.getString(R.string.setting_on_value);
        String reFocusOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_refocus_on);
        String chromaFlashOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_chromaflash_on);
        String optiZoomOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_optizoom_on);
        String fssrOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_FSSR_on);
        String truPortraitOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_trueportrait_on);
        String multiTouchFocusOn = mActivity.getString(R.string.
            pref_camera_advanced_feature_value_multi_touch_focus_on);
        String optiZoom =
            mParameters.get(CameraSettings.KEY_QC_OPTI_ZOOM);
        String chromaFlash =
            mParameters.get(CameraSettings.KEY_QC_CHROMA_FLASH);
        String ubiFocus =
            mParameters.get(CameraSettings.KEY_QC_AF_BRACKETING);
        String fssr =
            mParameters.get(CameraSettings.KEY_QC_FSSR);
        String truePortrait =
            mParameters.get(CameraSettings.KEY_QC_TP);
        String multiTouchFocus =
            mParameters.get(CameraSettings.KEY_QC_MULTI_TOUCH_FOCUS);
        String continuousShot =
                mParameters.get("long-shot");

        if (mManual3AEnabled > 0) {
            disableLongShot = true;
        }

        if ((continuousShot != null) && continuousShot.equals(continuousShotOn)) {
            String pictureFormat = mActivity.getString(R.string.
                    pref_camera_picture_format_value_jpeg);
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, pictureFormat);
        } else {
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, null);
        }
        String reFocus =
            mParameters.get(CameraSettings.KEY_QC_RE_FOCUS);

        if (mFocusManager.isZslEnabled()) {
            String pictureFormat = mActivity.getString(R.string.
                    pref_camera_picture_format_value_jpeg);
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, pictureFormat);
        } else {
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, null);
        }
        if ((ubiFocus != null && ubiFocus.equals(ubiFocusOn)) ||
                (multiTouchFocus != null && multiTouchFocus.equals(multiTouchFocusOn)) ||
                (reFocus != null && reFocus.equals(reFocusOn)) ||
                (chromaFlash != null && chromaFlash.equals(chromaFlashOn)) ||
                (optiZoom != null && optiZoom.equals(optiZoomOn)) ||
                (fssr != null && fssr.equals(fssrOn)) ||
                (truePortrait != null && truePortrait.equals(truPortraitOn))) {
            mSceneMode = sceneMode = Parameters.SCENE_MODE_AUTO;
            flashMode = Parameters.FLASH_MODE_OFF;
            focusMode = Parameters.FOCUS_MODE_INFINITY;
            redeyeReduction = mActivity.getString(R.string.
                pref_camera_redeyereduction_entry_disable);
            aeBracketing = mActivity.getString(R.string.
                pref_camera_ae_bracket_hdr_entry_off);
            colorEffect = mActivity.getString(R.string.
                pref_camera_coloreffect_default);
            exposureCompensation = CameraSettings.EXPOSURE_DEFAULT_VALUE;

            overrideCameraSettings(flashMode, null, focusMode,
                                   exposureCompensation, touchAfAec, null,
                                   null, null, null, colorEffect,
                                   sceneMode, redeyeReduction, aeBracketing);
            disableLongShot = true;
            Toast.makeText(mActivity, R.string.advanced_capture_disable_continuous_shot,
                    Toast.LENGTH_LONG).show();
        }

        // If scene mode is set, for flash mode, white balance and focus mode
        // read settings from preferences so we retain user preferences.
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            flashMode = mParameters.getFlashMode();
            String whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            focusMode = mFocusManager.getFocusMode();
            colorEffect = mParameters.getColorEffect();
            exposureCompensation =
                Integer.toString(mParameters.getExposureCompensation());
            touchAfAec = mCurrTouchAfAec;

            overrideCameraSettings(flashMode, whiteBalance, focusMode,
                    exposureCompensation, touchAfAec,
                    mParameters.getAutoExposure(),
                    Integer.toString(mParameters.getSaturation()),
                    Integer.toString(mParameters.getContrast()),
                    Integer.toString(mParameters.getSharpness()),
                    colorEffect,
                    sceneMode, redeyeReduction, aeBracketing);
            if (CameraUtil.SCENE_MODE_HDR.equals(mSceneMode)) {
                disableLongShot = true;
            }
        } else if (mFocusManager.isZslEnabled()) {
            focusMode = mParameters.getFocusMode();
            overrideCameraSettings(flashMode, null, focusMode,
                                   exposureCompensation, touchAfAec, null,
                                   null, null, null, colorEffect,
                                   sceneMode, redeyeReduction, aeBracketing);
        } else {
            if (mManual3AEnabled > 0) {
                updateCommonManual3ASettings();
            } else {
                overrideCameraSettings(flashMode, null, focusMode,
                                       exposureCompensation, touchAfAec, null,
                                       null, null, null, colorEffect,
                                       sceneMode, redeyeReduction, aeBracketing);
            }
        }
        /* Disable focus if aebracket is ON */
        String aeBracket = mParameters.get(CameraSettings.KEY_QC_AE_BRACKETING);
        if (!aeBracket.equalsIgnoreCase("off")) {
            String fMode = Parameters.FLASH_MODE_OFF;
            mUI.overrideSettings(CameraSettings.KEY_FLASH_MODE, fMode);
            mParameters.setFlashMode(fMode);
        }
        if (disableLongShot) {
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT,
                    mActivity.getString(R.string.setting_off_value));
        } else {
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT, null);
        }

        boolean disableQcomMiscSetting =
                SystemProperties.getBoolean("camera.qcom.misc.disable", false);
        if (disableQcomMiscSetting) {
            mUI.overrideSettings(CameraSettings.KEY_ZSL, Parameters.ZSL_OFF);
            mUI.overrideSettings(CameraSettings.KEY_FACE_DETECTION, Parameters.FACE_DETECTION_OFF);
            mUI.overrideSettings(CameraSettings.KEY_TOUCH_AF_AEC, Parameters.TOUCH_AF_AEC_OFF);
            mUI.overrideSettings(CameraSettings.KEY_FOCUS_MODE, Parameters.FOCUS_MODE_AUTO);
            mUI.overrideSettings(CameraSettings.KEY_FLASH_MODE, Parameters.FLASH_MODE_OFF);
            mUI.overrideSettings(CameraSettings.KEY_DENOISE, Parameters.DENOISE_OFF);
        }
    }

    private void overrideCameraSettings(final String flashMode,
            final String whiteBalance, final String focusMode,
            final String exposureMode, final String touchMode,
            final String autoExposure, final String saturation,
            final String contrast, final String sharpness,
            final String coloreffect, final String sceneMode,
            final String redeyeReduction, final String aeBracketing) {
        mUI.overrideSettings(
                CameraSettings.KEY_FLASH_MODE, flashMode,
                CameraSettings.KEY_WHITE_BALANCE, whiteBalance,
                CameraSettings.KEY_FOCUS_MODE, focusMode,
                CameraSettings.KEY_EXPOSURE, exposureMode,
                CameraSettings.KEY_TOUCH_AF_AEC, touchMode,
                CameraSettings.KEY_AUTOEXPOSURE, autoExposure,
                CameraSettings.KEY_SATURATION, saturation,
                CameraSettings.KEY_CONTRAST, contrast,
                CameraSettings.KEY_SHARPNESS, sharpness,
                CameraSettings.KEY_COLOR_EFFECT, coloreffect,
                CameraSettings.KEY_SCENE_MODE, sceneMode,
                CameraSettings.KEY_REDEYE_REDUCTION, redeyeReduction,
                CameraSettings.KEY_AE_BRACKET_HDR, aeBracketing);
    }

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(mActivity, mInitialParams,
                mCameraId, CameraHolder.instance().getCameraInfo());
        mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);

        int numOfCams = Camera.getNumberOfCameras();
        int backCamId = CameraHolder.instance().getBackCameraId();
        int frontCamId = CameraHolder.instance().getFrontCameraId();
        // We need to swap the list preference contents if back camera and front camera
        // IDs are not 0 and 1 respectively
        if ((numOfCams == 2) && ((backCamId != CameraInfo.CAMERA_FACING_BACK)
                || (frontCamId != CameraInfo.CAMERA_FACING_FRONT))) {
            Log.e(TAG,"loadCameraPreferences() updating camera_id pref");

            IconListPreference switchIconPref =
                    (IconListPreference)mPreferenceGroup.findPreference(
                    CameraSettings.KEY_CAMERA_ID);

            int[] iconIds = {R.drawable.ic_switch_front, R.drawable.ic_switch_back};
            switchIconPref.setIconIds(iconIds);

            String[] entries = {mActivity.getResources().getString(
                    R.string.pref_camera_id_entry_front), mActivity.getResources().
                    getString(R.string.pref_camera_id_entry_back)};
            switchIconPref.setEntries(entries);

            String[] labels = {mActivity.getResources().getString(
                    R.string.pref_camera_id_label_front), mActivity.getResources().
                    getString(R.string.pref_camera_id_label_back)};
            switchIconPref.setLabels(labels);

            int[] largeIconIds = {R.drawable.ic_switch_front, R.drawable.ic_switch_back};
            switchIconPref.setLargeIconIds(largeIconIds);
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int oldOrientation = mOrientation;
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (oldOrientation != mOrientation &&
            oldOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            Log.v(TAG, "onOrientationChanged, update parameters");
            if (mParameters != null && mCameraDevice != null) {
                setFlipValue();
                mCameraDevice.setParameters(mParameters);
            }
        }

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(SHOW_TAP_TO_FOCUS_TOAST)) {
            mHandler.removeMessages(SHOW_TAP_TO_FOCUS_TOAST);
            showTapToFocusToast();
        }

        // need to re-initialize mGraphView to show histogram on rotate
        mGraphView = (GraphView)mRootView.findViewById(R.id.graph_view);
        if(mGraphView != null){
            mGraphView.setAlpha(0.75f);
            mGraphView.setPhotoModuleObject(this);
            mGraphView.PreviewChanged();
        }
    }

    @Override
    public void onStop() {
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    @Override
    public void onCaptureCancelled() {
        mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
        mActivity.finish();
    }

    @Override
    public void onCaptureRetake() {
        if (mPaused)
            return;
        mUI.hidePostCaptureAlert();
        setupPreview();
    }

    @Override
    public void onCaptureDone() {
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    CameraUtil.closeSilently(outputStream);
                }
            } else {
                ExifInterface exif = Exif.getExif(data);
                int orientation = Exif.getOrientation(exif);
                Bitmap bitmap = CameraUtil.makeBitmap(data, 50 * 1024);
                bitmap = CameraUtil.rotate(bitmap, orientation);
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } catch (IOException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } finally {
                CameraUtil.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CameraUtil.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CameraUtil.KEY_SHOW_WHEN_LOCKED, true);
            }

            // TODO: Share this constant.
            final String CROP_ACTION = "com.android.camera.action.CROP";
            Intent cropIntent = new Intent(CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mPaused || mUI.collapseCameraControls()
                || (mCameraState == SNAPSHOT_IN_PROGRESS)
                || (mCameraState == PREVIEW_STOPPED)) return;

        synchronized(mCameraDevice) {
           if (mCameraState == LONGSHOT) {
               mLongshotActive = false;
               mCameraDevice.setLongshot(false);
               if (!mFocusManager.isZslEnabled()) {
                   setupPreview();
               } else {
                   setCameraState(IDLE);
                   mFocusManager.resetTouchFocus();
                   if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(
                           mFocusManager.getFocusMode())) {
                       mCameraDevice.cancelAutoFocus();
                   }
                   mUI.resumeFaceDetection();
               }
           }
        }

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) return;

        if (pressed) {
            mFocusManager.onShutterDown();
        } else {
            // for countdown mode, we need to postpone the shutter release
            // i.e. lock the focus during countdown.
            if (!mUI.isCountingDown()) {
                mFocusManager.onShutterUp();
            }
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mPaused || mUI.collapseCameraControls()
                || (mCameraState == SWITCHING_CAMERA)
                || (mCameraState == PREVIEW_STOPPED)) return;

        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }
        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
            mUI.hideSwitcher();
            mUI.setSwipingEnabled(false);
        }

         //Need to disable focus for ZSL mode
        if(mSnapshotMode == CameraInfo.CAMERA_SUPPORT_MODE_ZSL) {
            mFocusManager.setZslEnable(true);
        } else {
            mFocusManager.setZslEnable(false);
        }

        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if ((mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS)
                && !mIsImageCaptureIntent) {
            mSnapshotOnIdle = true;
            return;
        }

        String timer = mPreferences.getString(
                CameraSettings.KEY_TIMER,
                mActivity.getString(R.string.pref_camera_timer_default));
        boolean playSound = mPreferences.getString(CameraSettings.KEY_TIMER_SOUND_EFFECTS,
                mActivity.getString(R.string.pref_camera_timer_sound_default))
                .equals(mActivity.getString(R.string.setting_on_value));

        int seconds = Integer.parseInt(timer);
        // When shutter button is pressed, check whether the previous countdown is
        // finished. If not, cancel the previous countdown and start a new one.
        if (mUI.isCountingDown()) {
            mUI.cancelCountDown();
        }
        if (seconds > 0) {
            String zsl = mPreferences.getString(CameraSettings.KEY_ZSL,
                    mActivity.getString(R.string.pref_camera_zsl_default));
            mUI.overrideSettings(CameraSettings.KEY_ZSL, zsl);
            mUI.startCountDown(seconds, playSound);
        } else {
            mSnapshotOnIdle = false;
            mFocusManager.doSnap();
        }
    }

    @Override
    public void onShutterButtonLongClick() {
        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }

        if ((null != mCameraDevice) && ((mCameraState == IDLE) || (mCameraState == FOCUSING))) {
            //Add on/off Menu for longshot
            String longshot_enable = mPreferences.getString(
                CameraSettings.KEY_LONGSHOT,
                mActivity.getString(R.string.pref_camera_longshot_default));

            Log.d(TAG, "longshot_enable = " + longshot_enable);
            if (longshot_enable.equals("on")) {
                boolean enable = SystemProperties.getBoolean(PERSIST_LONG_SAVE, false);
                mLongshotSave = enable;

                //check whether current memory is enough for longshot.
                if(isLongshotNeedCancel()) {
                    return;
                }
                mLongshotActive = true;
                mCameraDevice.setLongshot(true);
                setCameraState(PhotoController.LONGSHOT);
                mFocusManager.doSnap();
            }
        }
    }

    @Override
    public void installIntentFilter() {
        // Do nothing.
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return mFirstTimeInitialized;
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    private void openCamera() {
        // We need to check whether the activity is paused before long
        // operations to ensure that onPause() can be done ASAP.
        if (mPaused) {
            return;
        }
        Log.v(TAG, "Open camera device.");
        mCameraDevice = CameraUtil.openCamera(
                mActivity, mCameraId, mHandler,
                mActivity.getCameraOpenErrorCallback());
        if (mCameraDevice == null) {
            Log.e(TAG, "Failed to open camera:" + mCameraId);
            mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
            return;
        }
        mParameters = mCameraDevice.getParameters();
        mCameraPreviewParamsReady = true;
        mInitialParams = mParameters;
        if (mFocusManager == null) initializeFocusManager();
        initializeCapabilities();
        mHandler.sendEmptyMessageDelayed(CAMERA_OPEN_DONE,100);
        return;
    }

    @Override
    public void onResumeAfterSuper() {
        // Add delay on resume from lock screen only, in order to to speed up
        // the onResume --> onPause --> onResume cycle from lock screen.
        // Don't do always because letting go of thread can cause delay.
        String action = mActivity.getIntent().getAction();
        if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
            Log.v(TAG, "On resume, from lock screen.");
            // Note: onPauseAfterSuper() will delete this runnable, so we will
            // at most have 1 copy queued up.
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    onResumeTasks();
                }
            }, ON_RESUME_TASKS_DELAY_MSEC);
        } else {
            Log.v(TAG, "On resume.");
            onResumeTasks();
        }
        mHandler.post(new Runnable(){
            @Override
            public void run(){
                mActivity.updateStorageSpaceAndHint();
            }
        });
    }

    private void onResumeTasks() {
        Log.v(TAG, "Executing onResumeTasks.");
        if (mOpenCameraFail || mCameraDisabled) return;

        if (mOpenCameraThread == null) {
            mOpenCameraThread = new OpenCameraThread();
            mOpenCameraThread.start();
        }

        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;
        resetExposureCompensation();

        if (mSkinToneSeekBar != true)
        {
            Log.v(TAG, "Send tone bar: mSkinToneSeekBar = " + mSkinToneSeekBar);
            mHandler.sendEmptyMessage(SET_SKIN_TONE_FACTOR);
        }
        // If first time initialization is not finished, put it in the
        // message queue.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
        mUI.initDisplayChangeListener();
        keepScreenOnAwhile();
        mUI.updateOnScreenIndicators(mParameters, mPreferenceGroup,
                        mPreferences);

        UsageStatistics.onContentViewChanged(
                UsageStatistics.COMPONENT_CAMERA, "PhotoModule");

        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mOnResumeTime = SystemClock.uptimeMillis();
        checkDisplayRotation();
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;
        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.unregisterListener(this, gsensor);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.unregisterListener(this, msensor);
        }

        Log.d(TAG, "remove idle handleer in onPause");
        removeIdleHandler();
    }

    @Override
    public void onPauseAfterSuper() {
        Log.v(TAG, "On pause.");
        mUI.showPreviewCover();

        try {
            if (mOpenCameraThread != null) {
                mOpenCameraThread.join();
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        mOpenCameraThread = null;
        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus();
        }
        resetManual3ASettings();
        // If the camera has not been opened asynchronously yet,
        // and startPreview hasn't been called, then this is a no-op.
        // (e.g. onResume -> onPause -> onResume).
        stopPreview();

        mNamedImages = null;

        if (mLocationManager != null) mLocationManager.recordLocation(false);

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages and runnables in the queue.
        mHandler.removeCallbacksAndMessages(null);

        closeCamera();

        resetScreenOn();
        mUI.onPause();

        mPendingSwitchCameraId = -1;
        if (mFocusManager != null) mFocusManager.removeMessages();
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(null);
        }
        mUI.removeDisplayChangeListener();
    }

    /**
     * The focus manager is the first UI related element to get initialized,
     * and it requires the RenderOverlay, so initialize it here
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
            String[] defaultFocusModes = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            mFocusManager = new FocusOverlayManager(mPreferences, defaultFocusModes,
                    mInitialParams, this, mMirror,
                    mActivity.getMainLooper(), mUI);
        }
    }

    private void updateFocusManager(PhotoUI mUI) {
        // Idea here is to let focus manager create in camera open thread
        // (in initializeFocusManager) even if photoUI is null by that time so
        // as to not block start preview process. Once UI creation is done,
        // we will update focus manager with proper UI.
        if (mFocusManager != null && mUI != null) {
            mFocusManager.setPhotoUI(mUI);

            View root = mUI.getRootView();
            // These depend on camera parameters.
            int width = root.getWidth();
            int height = root.getHeight();
            mFocusManager.setPreviewSize(width, height);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();
        resizeForPreviewAspectRatio();
    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CROP: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                mActivity.setResultEx(resultCode, intent);
                mActivity.finish();

                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    protected CameraManager.CameraProxy getCamera() {
        return mCameraDevice;
    }

    private boolean canTakePicture() {
        return isCameraIdle() && (mActivity.getStorageSpaceBytes() > Storage.LOW_STORAGE_THRESHOLD_BYTES);
    }

    @Override
    public void autoFocus() {
        mFocusStartTime = System.currentTimeMillis();
        mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
        setCameraState(FOCUSING);
    }

    @Override
    public void cancelAutoFocus() {
        if (null != mCameraDevice ) {
            mCameraDevice.cancelAutoFocus();
            setCameraState(IDLE);
            setCameraParameters(UPDATE_PARAM_PREFERENCE);
        }
    }

    // Preview area is touched. Handle touch focus.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }
        //If Touch AF/AEC is disabled in UI, return
        if(this.mTouchAfAecFlag == false) {
            return;
        }
        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) return;
        mFocusManager.onSingleTapUp(x, y);
    }

    @Override
    public boolean onBackPressed() {
        return mUI.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_FOCUS:
                if (/*TODO: mActivity.isInCameraApp() &&*/ mFirstTimeInitialized) {
                    if (event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if ( (mCameraState != PREVIEW_STOPPED) && (mFocusManager != null) &&
                  (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING) &&
                  (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING_SNAP_ON_FINISH) ) {
                if (mbrightness > MINIMUM_BRIGHTNESS) {
                    mbrightness-=mbrightness_step;
                    synchronized (mCameraDevice) {
                        /* Set the "luma-adaptation" parameter */
                        mParameters = mCameraDevice.getParameters();
                        mParameters.set("luma-adaptation", String.valueOf(mbrightness));
                        mCameraDevice.setParameters(mParameters);
                    }
                }
                brightnessProgressBar.setProgress(mbrightness);
                Editor editor = mPreferences.edit();
                editor.putInt(CameraSettings.KEY_BRIGHTNESS, mbrightness);
                editor.apply();
                brightnessProgressBar.setVisibility(View.VISIBLE);
                mBrightnessVisible = true;
            }
            break;
           case KeyEvent.KEYCODE_DPAD_RIGHT:
            if ( (mCameraState != PREVIEW_STOPPED) && (mFocusManager != null) &&
                  (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING) &&
                  (mFocusManager.getCurrentFocusState() != mFocusManager.STATE_FOCUSING_SNAP_ON_FINISH) ) {
                if (mbrightness < MAXIMUM_BRIGHTNESS) {
                    mbrightness+=mbrightness_step;
                    synchronized (mCameraDevice) {
                        /* Set the "luma-adaptation" parameter */
                        mParameters = mCameraDevice.getParameters();
                        mParameters.set("luma-adaptation", String.valueOf(mbrightness));
                        mCameraDevice.setParameters(mParameters);
                    }
                }
                brightnessProgressBar.setProgress(mbrightness);
                Editor editor = mPreferences.edit();
                editor.putInt(CameraSettings.KEY_BRIGHTNESS, mbrightness);
                editor.apply();
                brightnessProgressBar.setVisibility(View.VISIBLE);
                mBrightnessVisible = true;
            }
            break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, onShutterButtonFocus()
                    // will be called again but it is fine.
                    onShutterButtonFocus(true);
                    mUI.pressShutterButton();
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (/*mActivity.isInCameraApp() && */ mFirstTimeInitialized) {
                    onShutterButtonClick();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return false;
    }

    private void closeCamera() {
        Log.v(TAG, "Close camera device.");
        if (mCameraDevice != null) {
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.setErrorCallback(null);

            if (mActivity.isSecureCamera() && !CameraActivity.isFirstStartAfterScreenOn()) {
                // Blocks until camera is actually released.
                CameraHolder.instance().strongRelease();
            } else {
                CameraHolder.instance().release();
            }

            mFaceDetectionStarted = false;
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            if (mFocusManager != null) {
                mFocusManager.onCameraReleased();
            }
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDisplayOrientation = mDisplayOrientation;
        // This will be called again in checkDisplayRotation(), so there
        // should not be any problem even if mUI is null.
        if (mUI != null) {
            mUI.setDisplayOrientation(mDisplayOrientation);
        }
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    /** Only called by UI thread. */
    private void setupPreview() {
        mFocusManager.resetTouchFocus();
        startPreview();
    }

    /** This can run on a background thread, so don't do UI updates here. Post any
             view updates to MainHandler or do it on onPreviewStarted() .  */
    private void startPreview() {
        if (mPaused || mCameraDevice == null) {
            return;
        }

        Log.v(TAG, "startPreview");

        SurfaceTexture st = null;
        if (mUI != null) {
            st = mUI.getSurfaceTexture();
        }
        // Surfacetexture could be null here, but its still valid and safe to set null
        // surface before startpreview. This will help in basic preview setup and
        // surface creation in parallel. Once valid surface is ready in onPreviewUIReady()
        // we set the surface to camera to actually start preview.
        mCameraDevice.setPreviewTexture(st);

        if (!mCameraPreviewParamsReady) {
            Log.w(TAG, "startPreview: parameters for preview is not ready.");
            return;
        }
        mCameraDevice.setErrorCallback(mErrorCallback);
        // ICS camera frameworks has a bug. Face detection state is not cleared 1589
        // after taking a picture. Stop the preview to work around it. The bug
        // was fixed in JB.
        if (mCameraState != PREVIEW_STOPPED && mCameraState != INIT) {
            stopPreview();
        }

        setCameraParameters(UPDATE_PARAM_ALL);

        mCameraDevice.startPreview();
        mHandler.sendEmptyMessage(ON_PREVIEW_STARTED);

        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus to
            // resume it because it may have been paused by autoFocus call.
            if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode())) {
                mCameraDevice.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        } else {
            mHandler.post(mDoSnapRunnable);
        }
    }

    @Override
    public void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) mFocusManager.onPreviewStopped();
    }

    @SuppressWarnings("deprecation")
    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        int[] fpsRange = CameraUtil.getPhotoPreviewFpsRange(mParameters);
        if (fpsRange != null && fpsRange.length > 0) {
            mParameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        }

        mParameters.set(CameraUtil.RECORDING_HINT, CameraUtil.FALSE);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            Parameters p = mCameraDevice.getParameters();
            mZoomValue = p.getZoom();
            mParameters.setZoom(mZoomValue);
        }
    }
    private boolean needRestart() {
        mRestartPreview = false;
        String zsl = mPreferences.getString(CameraSettings.KEY_ZSL,
                                  mActivity.getString(R.string.pref_camera_zsl_default));
        if(zsl.equals("on") && mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_ZSL
           && mCameraState != PREVIEW_STOPPED) {
            //Switch on ZSL Camera mode
            Log.v(TAG, "Switching to ZSL Camera Mode. Restart Preview");
            mRestartPreview = true;
            return mRestartPreview;
        }
        if(zsl.equals("off") && mSnapshotMode != CameraInfo.CAMERA_SUPPORT_MODE_NONZSL
                 && mCameraState != PREVIEW_STOPPED) {
            //Switch on Normal Camera mode
            Log.v(TAG, "Switching to Normal Camera Mode. Restart Preview");
            mRestartPreview = true;
            return mRestartPreview;
        }
        return mRestartPreview;
    }

    private void qcomUpdateAdvancedFeatures(String ubiFocus,
                                            String chromaFlash,
                                            String reFocus,
                                            String optiZoom,
                                            String fssr,
                                            String truePortrait,
                                            String multiTouchFocus) {
        if (CameraUtil.isSupported(ubiFocus,
              CameraSettings.getSupportedAFBracketingModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_AF_BRACKETING, ubiFocus);
        }
        if (CameraUtil.isSupported(chromaFlash,
              CameraSettings.getSupportedChromaFlashModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_CHROMA_FLASH, chromaFlash);
        }
        if (CameraUtil.isSupported(optiZoom,
              CameraSettings.getSupportedOptiZoomModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_OPTI_ZOOM, optiZoom);
        }
        if (CameraUtil.isSupported(reFocus,
              CameraSettings.getSupportedRefocusModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_RE_FOCUS, reFocus);
        }
        if (CameraUtil.isSupported(fssr,
              CameraSettings.getSupportedFSSRModes(mParameters))) {
             mParameters.set(CameraSettings.KEY_QC_FSSR, fssr);
        }
        if (CameraUtil.isSupported(truePortrait,
              CameraSettings.getSupportedTruePortraitModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_TP, truePortrait);
        }
        if(CameraUtil.isSupported(multiTouchFocus,
              CameraSettings.getSupportedMultiTouchFocusModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_MULTI_TOUCH_FOCUS, multiTouchFocus);
        }
    }

    /** This can run on a background thread, so don't do UI updates here.*/
    private void qcomUpdateCameraParametersPreference() {
        //qcom Related Parameter update
        //Set Brightness.
        mParameters.set("luma-adaptation", String.valueOf(mbrightness));

        String longshot_enable = mPreferences.getString(
                CameraSettings.KEY_LONGSHOT,
                mActivity.getString(R.string.pref_camera_longshot_default));
        mParameters.set("long-shot", longshot_enable);

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode) ||
            CameraUtil.SCENE_MODE_HDR.equals(mSceneMode)) {
            // Set Touch AF/AEC parameter.
            String touchAfAec = mPreferences.getString(
                 CameraSettings.KEY_TOUCH_AF_AEC,
                 mActivity.getString(R.string.pref_camera_touchafaec_default));
            if (CameraUtil.isSupported(touchAfAec, mParameters.getSupportedTouchAfAec())) {
                mCurrTouchAfAec = touchAfAec;
                mParameters.setTouchAfAec(touchAfAec);
            }
        } else {
            mParameters.setTouchAfAec(mParameters.TOUCH_AF_AEC_OFF);
            mFocusManager.resetTouchFocus();
        }
        try {
            if(mParameters.getTouchAfAec().equals(mParameters.TOUCH_AF_AEC_ON))
                this.mTouchAfAecFlag = true;
            else
                this.mTouchAfAecFlag = false;
        } catch(Exception e){
            Log.e(TAG, "Handled NULL pointer Exception");
        }

        // Set Picture Format
        // Picture Formats specified in UI should be consistent with
        // PIXEL_FORMAT_JPEG and PIXEL_FORMAT_RAW constants
        String pictureFormat = mPreferences.getString(
                CameraSettings.KEY_PICTURE_FORMAT,
                mActivity.getString(R.string.pref_camera_picture_format_default));

        //Change picture format to JPEG if camera is start from other APK by intent.
        if (mIsImageCaptureIntent && !pictureFormat.equals(PIXEL_FORMAT_JPEG)) {
            pictureFormat = PIXEL_FORMAT_JPEG;
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_PICTURE_FORMAT,
                mActivity.getString(R.string.pref_camera_picture_format_value_jpeg));
            editor.apply();
        }
        Log.v(TAG, "Picture format value =" + pictureFormat);
        mParameters.set(KEY_PICTURE_FORMAT, pictureFormat);

        // Set JPEG quality.
        String jpegQuality = mPreferences.getString(
                CameraSettings.KEY_JPEG_QUALITY,
                mActivity.getString(R.string.pref_camera_jpegquality_default));
        //mUnsupportedJpegQuality = false;
        Size pic_size = mParameters.getPictureSize();
        if (pic_size == null) {
            Log.e(TAG, "error getPictureSize: size is null");
        }
        else{
            if("100".equals(jpegQuality) && (pic_size.width >= 3200)){
                //mUnsupportedJpegQuality = true;
            }else {
                mParameters.setJpegQuality(JpegEncodingQualityMappings.getQualityNumber(jpegQuality));
            }
        }

        // Set Selectable Zone Af parameter.
        String selectableZoneAf = mPreferences.getString(
            CameraSettings.KEY_SELECTABLE_ZONE_AF,
            mActivity.getString(R.string.pref_camera_selectablezoneaf_default));
        List<String> str = mParameters.getSupportedSelectableZoneAf();
        if (CameraUtil.isSupported(selectableZoneAf, mParameters.getSupportedSelectableZoneAf())) {
            mParameters.setSelectableZoneAf(selectableZoneAf);
        }

        // Set wavelet denoise mode
        if (mParameters.getSupportedDenoiseModes() != null) {
            String Denoise = mPreferences.getString( CameraSettings.KEY_DENOISE,
                             mActivity.getString(R.string.pref_camera_denoise_default));
            mParameters.setDenoise(Denoise);
        }
        // Set Redeye Reduction
        String redeyeReduction = mPreferences.getString(
                CameraSettings.KEY_REDEYE_REDUCTION,
                mActivity.getString(R.string.pref_camera_redeyereduction_default));
        if (CameraUtil.isSupported(redeyeReduction,
            mParameters.getSupportedRedeyeReductionModes())) {
            mParameters.setRedeyeReductionMode(redeyeReduction);
        }
        // Set ISO parameter
        if ((mManual3AEnabled & MANUAL_EXPOSURE) == 0) {
            String iso = mPreferences.getString(
                    CameraSettings.KEY_ISO,
                    mActivity.getString(R.string.pref_camera_iso_default));
            if (CameraUtil.isSupported(iso,
                mParameters.getSupportedIsoValues())) {
                mParameters.setISOValue(iso);
            }
        }
        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                mActivity.getString(R.string.pref_camera_coloreffect_default));
        Log.v(TAG, "Color effect value =" + colorEffect);
        if (CameraUtil.isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }
        //Set Saturation
        String saturationStr = mPreferences.getString(
                CameraSettings.KEY_SATURATION,
                mActivity.getString(R.string.pref_camera_saturation_default));
        int saturation = Integer.parseInt(saturationStr);
        Log.v(TAG, "Saturation value =" + saturation);
        if((0 <= saturation) && (saturation <= mParameters.getMaxSaturation())){
            mParameters.setSaturation(saturation);
        }
        // Set contrast parameter.
        String contrastStr = mPreferences.getString(
                CameraSettings.KEY_CONTRAST,
                mActivity.getString(R.string.pref_camera_contrast_default));
        int contrast = Integer.parseInt(contrastStr);
        Log.v(TAG, "Contrast value =" +contrast);
        if((0 <= contrast) && (contrast <= mParameters.getMaxContrast())){
            mParameters.setContrast(contrast);
        }
        // Set sharpness parameter
        String sharpnessStr = mPreferences.getString(
                CameraSettings.KEY_SHARPNESS,
                mActivity.getString(R.string.pref_camera_sharpness_default));
        int sharpness = Integer.parseInt(sharpnessStr) *
                (mParameters.getMaxSharpness()/MAX_SHARPNESS_LEVEL);
        Log.v(TAG, "Sharpness value =" + sharpness);
        if((0 <= sharpness) && (sharpness <= mParameters.getMaxSharpness())){
            mParameters.setSharpness(sharpness);
        }
        // Set Face Recognition
        String faceRC = mPreferences.getString(
                CameraSettings.KEY_FACE_RECOGNITION,
                mActivity.getString(R.string.pref_camera_facerc_default));
        Log.v(TAG, "Face Recognition value = " + faceRC);
        if (CameraUtil.isSupported(faceRC,
                CameraSettings.getSupportedFaceRecognitionModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_FACE_RECOGNITION, faceRC);
        }
        // Set AE Bracketing
        String aeBracketing = mPreferences.getString(
                CameraSettings.KEY_AE_BRACKET_HDR,
                mActivity.getString(R.string.pref_camera_ae_bracket_hdr_default));
        Log.v(TAG, "AE Bracketing value =" + aeBracketing);
        if (CameraUtil.isSupported(aeBracketing,
                CameraSettings.getSupportedAEBracketingModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_AE_BRACKETING, aeBracketing);
        }

        // Set CDS
        String cds = mPreferences.getString(
                CameraSettings.KEY_CDS_MODE,
                mActivity.getString(R.string.pref_camera_cds_default));
        if ((mPrevSavedCDS == null) && (cds != null)) {
            mPrevSavedCDS = cds;
        }
        if (CameraUtil.isSupported(cds,
                CameraSettings.getSupportedCDSModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_CDS_MODE, cds);
        }

        // Set TNR
        String tnr = mPreferences.getString(
                CameraSettings.KEY_TNR_MODE,
                mActivity.getString(R.string.pref_camera_tnr_default));
        if (CameraUtil.isSupported(tnr,
                CameraSettings.getSupportedTNRModes(mParameters))) {
            if (!tnr.equals(mActivity.getString(R.string.
                    pref_camera_tnr_value_off))) {
                mParameters.set(CameraSettings.KEY_QC_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_cds_value_off));
                mUI.overrideSettings(CameraSettings.KEY_QC_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_cds_value_off));
                if (cds != null) {
                    mPrevSavedCDS = cds;
                }
                isTNREnabled = true;
            } else if (isTNREnabled) {
                mParameters.set(CameraSettings.KEY_QC_CDS_MODE, mPrevSavedCDS);
                mUI.overrideSettings(CameraSettings.KEY_QC_CDS_MODE, mPrevSavedCDS);
                isTNREnabled = false;
            }
            mParameters.set(CameraSettings.KEY_QC_TNR_MODE, tnr);
        }

        // Set hdr mode
        String hdrMode = mPreferences.getString(
                CameraSettings.KEY_HDR_MODE,
                mActivity.getString(R.string.pref_camera_hdr_mode_default));
        Log.v(TAG, "HDR Mode value =" + hdrMode);
        if (CameraUtil.isSupported(hdrMode,
                CameraSettings.getSupportedHDRModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_SNAPCAM_HDR_MODE, hdrMode);
        }

        // Set hdr need 1x
        String hdrNeed1x = mPreferences.getString(
                CameraSettings.KEY_HDR_NEED_1X,
                mActivity.getString(R.string.pref_camera_hdr_need_1x_default));
        Log.v(TAG, "HDR need 1x value =" + hdrNeed1x);
        if (CameraUtil.isSupported(hdrNeed1x,
                CameraSettings.getSupportedHDRNeed1x(mParameters))) {
            mParameters.set(CameraSettings.KEY_SNAPCAM_HDR_NEED_1X, hdrNeed1x);
        }

        // Set Advanced features.
        String advancedFeature = mPreferences.getString(
                CameraSettings.KEY_ADVANCED_FEATURES,
                mActivity.getString(R.string.pref_camera_advanced_feature_default));
        Log.e(TAG, " advancedFeature value =" + advancedFeature);

        mRefocus = false;
        if(advancedFeature != null) {
             String ubiFocusOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_ubifocus_off);
             String chromaFlashOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_chromaflash_off);
             String optiZoomOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_optizoom_off);
             String reFocusOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_refocus_off);
             String fssrOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_FSSR_off);
             String truePortraitOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_trueportrait_off);
             String multiTouchFocusOff = mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_multi_touch_focus_off);

             if (advancedFeature.equals(mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_ubifocus_on))) {
                 qcomUpdateAdvancedFeatures(advancedFeature,
                                           chromaFlashOff,
                                           reFocusOff,
                                           optiZoomOff,
                                           fssrOff,
                                           truePortraitOff,
                                           multiTouchFocusOff);
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_chromaflash_on))) {
                 qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           advancedFeature,
                                           reFocusOff,
                                           optiZoomOff,
                                           fssrOff,
                                           truePortraitOff,
                                           multiTouchFocusOff);
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                 pref_camera_advanced_feature_value_refocus_on))) {
                 qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           advancedFeature,
                                           optiZoomOff,
                                           fssrOff,
                                           truePortraitOff,
                                           multiTouchFocusOff);
                 mRefocus = true;
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                pref_camera_advanced_feature_value_optizoom_on))) {
                qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           reFocusOff,
                                           advancedFeature,
                                           fssrOff,
                                           truePortraitOff,
                                           multiTouchFocusOff);
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                pref_camera_advanced_feature_value_FSSR_on))) {
                 qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           reFocusOff,
                                           optiZoomOff,
                                           advancedFeature,
                                           truePortraitOff,
                                           multiTouchFocusOff);
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                pref_camera_advanced_feature_value_trueportrait_on))) {
                qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           reFocusOff,
                                           optiZoomOff,
                                           fssrOff,
                                           advancedFeature,
                                           multiTouchFocusOff);
            } else if (advancedFeature.equals(mActivity.getString(R.string.
                pref_camera_advanced_feature_value_multi_touch_focus_on))) {
                qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           reFocusOff,
                                           optiZoomOff,
                                           fssrOff,
                                           truePortraitOff,
                                           advancedFeature);
            } else {
                qcomUpdateAdvancedFeatures(ubiFocusOff,
                                           chromaFlashOff,
                                           reFocusOff,
                                           optiZoomOff,
                                           fssrOff,
                                           truePortraitOff,
                                           multiTouchFocusOff);
            }
        }
        // Set auto exposure parameter.
        String autoExposure = mPreferences.getString(
                CameraSettings.KEY_AUTOEXPOSURE,
                mActivity.getString(R.string.pref_camera_autoexposure_default));
        Log.v(TAG, "autoExposure value =" + autoExposure);
        if (CameraUtil.isSupported(autoExposure, mParameters.getSupportedAutoexposure())) {
            mParameters.setAutoExposure(autoExposure);
        }

        // Set anti banding parameter.
        String antiBanding = mPreferences.getString(
                 CameraSettings.KEY_ANTIBANDING,
                 mActivity.getString(R.string.pref_camera_antibanding_default));
        Log.v(TAG, "antiBanding value =" + antiBanding);
        if (CameraUtil.isSupported(antiBanding, mParameters.getSupportedAntibanding())) {
            mParameters.setAntibanding(antiBanding);
        }

        String zsl = mPreferences.getString(CameraSettings.KEY_ZSL,
                                  mActivity.getString(R.string.pref_camera_zsl_default));
        String auto_hdr = mPreferences.getString(CameraSettings.KEY_AUTO_HDR,
                                       mActivity.getString(R.string.pref_camera_hdr_default));
        if (CameraUtil.isAutoHDRSupported(mParameters)) {
            mParameters.setAutoHDRMode(auto_hdr);
            if (auto_hdr.equals("enable")) {
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        if (mDrawAutoHDR != null) {
                            mDrawAutoHDR.setVisibility(View.VISIBLE);
                        }
                    }
                });
                mParameters.setSceneMode("asd");
                mCameraDevice.setMetadataCb(mMetaDataCallback);
            }
            else {
                mAutoHdrEnable = false;
                mActivity.runOnUiThread( new Runnable() {
                    public void run () {
                        if (mDrawAutoHDR != null) {
                            mDrawAutoHDR.setVisibility (View.INVISIBLE);
                        }
                    }
                });
            }
        }
        mParameters.setZSLMode(zsl);
        if(zsl.equals("on")) {
            //Switch on ZSL Camera mode
            mSnapshotMode = CameraInfo.CAMERA_SUPPORT_MODE_ZSL;
            mParameters.setCameraMode(1);
            mFocusManager.setZslEnable(true);

            //Raw picture format is not supported under ZSL mode
            mParameters.set(KEY_PICTURE_FORMAT, PIXEL_FORMAT_JPEG);

            //Try to set CAF for ZSL
            if(CameraUtil.isSupported(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                    mParameters.getSupportedFocusModes()) && !mFocusManager.isTouch()) {
                mFocusManager.overrideFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (mFocusManager.isTouch()) {
                mFocusManager.overrideFocusMode(null);
                mParameters.setFocusMode(mFocusManager.getFocusMode());
            } else {
                // If not supported use the current mode
                mFocusManager.overrideFocusMode(mFocusManager.getFocusMode());
            }

            if(!pictureFormat.equals(PIXEL_FORMAT_JPEG)) {
                     mActivity.runOnUiThread(new Runnable() {
                     public void run() {
                Toast.makeText(mActivity, R.string.error_app_unsupported_raw,
                    Toast.LENGTH_SHORT).show();
                         }
                    });
            }
        } else if(zsl.equals("off")) {
            mSnapshotMode = CameraInfo.CAMERA_SUPPORT_MODE_NONZSL;
            mParameters.setCameraMode(0);
            mFocusManager.setZslEnable(false);
            if ((mManual3AEnabled & MANUAL_FOCUS) == 0) {
                mFocusManager.overrideFocusMode(null);
                mParameters.setFocusMode(mFocusManager.getFocusMode());
            }
        }
        // Set face detetction parameter.
        String faceDetection = mPreferences.getString(
            CameraSettings.KEY_FACE_DETECTION,
            mActivity.getString(R.string.pref_camera_facedetection_default));

        if (CameraUtil.isSupported(faceDetection, mParameters.getSupportedFaceDetectionModes())) {
            mParameters.setFaceDetectionMode(faceDetection);
            if(faceDetection.equals("on") && mFaceDetectionEnabled == false) {
                mFaceDetectionEnabled = true;
                startFaceDetection();
            }
            if(faceDetection.equals("off") && mFaceDetectionEnabled == true) {
                stopFaceDetection();
                mFaceDetectionEnabled = false;
            }
        }
        // skin tone ie enabled only for auto,party and portrait BSM
        // when color effects are not enabled
        if((Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
            Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode)) &&
            (Parameters.EFFECT_NONE.equals(colorEffect))) {
             //Set Skin Tone Correction factor
             Log.v(TAG, "set tone bar: mSceneMode = " + mSceneMode);
             if(mSeekBarInitialized == true)
                 mHandler.sendEmptyMessage(SET_SKIN_TONE_FACTOR);
        }

        //Set Histogram
        String histogram = mPreferences.getString(
                CameraSettings.KEY_HISTOGRAM,
                mActivity.getString(R.string.pref_camera_histogram_default));
        if (CameraUtil.isSupported(histogram,
            mParameters.getSupportedHistogramModes()) && mCameraDevice != null) {
            // Call for histogram
            if(histogram.equals("enable")) {
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        if(mGraphView != null) {
                            mGraphView.setVisibility(View.VISIBLE);
                            mGraphView.PreviewChanged();
                        }
                    }
                });
                mCameraDevice.setHistogramMode(mStatsCallback);
                mHiston = true;
            } else {
                mHiston = false;
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                         if (mGraphView != null)
                             mGraphView.setVisibility(View.INVISIBLE);
                         }
                    });
                mCameraDevice.setHistogramMode(null);
            }
        }

        setFlipValue();

        /* Disable focus if aebracket is ON */
        String aeBracket = mParameters.get(CameraSettings.KEY_QC_AE_BRACKETING);
        if (!aeBracket.equalsIgnoreCase("off")) {
            String fMode = Parameters.FLASH_MODE_OFF;
            mParameters.setFlashMode(fMode);
        }
    }

    private void setFlipValue() {
        // Read Flip mode from adb command
        //value: 0(default) - FLIP_MODE_OFF
        //value: 1 - FLIP_MODE_H
        //value: 2 - FLIP_MODE_V
        //value: 3 - FLIP_MODE_VH
        int preview_flip_value = SystemProperties.getInt("debug.camera.preview.flip", 0);
        int video_flip_value = SystemProperties.getInt("debug.camera.video.flip", 0);
        int picture_flip_value = SystemProperties.getInt("debug.camera.picture.flip", 0);
        int rotation = CameraUtil.getJpegRotation(mCameraId, mOrientation);
        mParameters.setRotation(rotation);
        if (rotation == 90 || rotation == 270) {
            // in case of 90 or 270 degree, V/H flip should reverse
            if (preview_flip_value == 1) {
                preview_flip_value = 2;
            } else if (preview_flip_value == 2) {
                preview_flip_value = 1;
            }
            if (video_flip_value == 1) {
                video_flip_value = 2;
            } else if (video_flip_value == 2) {
                video_flip_value = 1;
            }
            if (picture_flip_value == 1) {
                picture_flip_value = 2;
            } else if (picture_flip_value == 2) {
                picture_flip_value = 1;
            }
        }
        String preview_flip = CameraUtil.getFilpModeString(preview_flip_value);
        String video_flip = CameraUtil.getFilpModeString(video_flip_value);
        String picture_flip = CameraUtil.getFilpModeString(picture_flip_value);
        if(CameraUtil.isSupported(preview_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_PREVIEW_FLIP, preview_flip);
        }
        if(CameraUtil.isSupported(video_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_VIDEO_FLIP, video_flip);
        }
        if(CameraUtil.isSupported(picture_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_SNAPSHOT_PICTURE_FLIP, picture_flip);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }

    /** This can run on a background thread, so don't do UI updates here.*/
    private boolean updateCameraParametersPreference() {
        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();

        // initialize focus mode
        if ((mManual3AEnabled & MANUAL_FOCUS) == 0) {
            mFocusManager.overrideFocusMode(null);
            mParameters.setFocusMode(mFocusManager.getFocusMode());
        }

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(mActivity, mParameters);
        } else {
            Size old_size = mParameters.getPictureSize();
            Log.v(TAG, "old picture_size = " + old_size.width + " x " + old_size.height);
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
            Size size = mParameters.getPictureSize();
            Log.v(TAG, "new picture_size = " + size.width + " x " + size.height);
            if (old_size != null && size != null) {
                if(!size.equals(old_size) && mCameraState != PREVIEW_STOPPED) {
                    Log.v(TAG, "Picture Size changed. Restart Preview");
                    mRestartPreview = true;
                }
            }
        }
        Size size = mParameters.getPictureSize();

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = CameraUtil.getOptimalPreviewSize(mActivity, sizes,
                (double) size.width / size.height);

        //Read Preview Resolution from adb command
        //value: 0(default) - Default value as per snapshot aspect ratio
        //value: 1 - 640x480
        //value: 2 - 720x480
        //value: 3 - 1280x720
        //value: 4 - 1920x1080
        int preview_resolution = SystemProperties.getInt("persist.camera.preview.size", 0);
        switch (preview_resolution) {
            case 1: {
                optimalSize.width = 640;
                optimalSize.height = 480;
                Log.v(TAG, "Preview resolution hardcoded to 640x480");
                break;
            }
            case 2: {
                optimalSize.width = 720;
                optimalSize.height = 480;
                Log.v(TAG, "Preview resolution hardcoded to 720x480");
                break;
            }
            case 3: {
                optimalSize.width = 1280;
                optimalSize.height = 720;
                Log.v(TAG, "Preview resolution hardcoded to 1280x720");
                break;
            }
            case 4: {
                optimalSize.width = 1920;
                optimalSize.height = 1080;
                Log.v(TAG, "Preview resolution hardcoded to 1920x1080");
                break;
            }
            default: {
                Log.v(TAG, "Preview resolution as per Snapshot aspect ratio");
                break;
            }
        }

        Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get latest values
            if (mHandler.getLooper() == Looper.myLooper()) {
                // On UI thread only, not when camera starts up
                setupPreview();
            } else {
                mCameraDevice.setParameters(mParameters);
            }
            mParameters = mCameraDevice.getParameters();
            Log.v(TAG, "Preview Size changed. Restart Preview");
            mRestartPreview = true;
        }

        Log.v(TAG, "Preview size is " + optimalSize.width + "x" + optimalSize.height);
        size = mParameters.getPictureSize();

        // Set jpegthumbnail size
        // Set a jpegthumbnail size that is closest to the Picture height and has
        // the right aspect ratio.
        List<Size> supported = mParameters.getSupportedJpegThumbnailSizes();
        optimalSize = CameraUtil.getOptimalJpegThumbnailSize(supported,
                (double) size.width / size.height);
        original = mParameters.getJpegThumbnailSize();
        if (!original.equals(optimalSize)) {
            mParameters.setJpegThumbnailSize(optimalSize.width, optimalSize.height);
        }

        Log.v(TAG, "Thumbnail size is " + optimalSize.width + "x" + optimalSize.height);

        // Since changing scene mode may change supported values, set scene mode
        // first. HDR is a scene mode. To promote it in UI, it is stored in a
        // separate preference.
        String onValue = mActivity.getString(R.string.setting_on_value);
        String hdr = mPreferences.getString(CameraSettings.KEY_CAMERA_HDR,
                mActivity.getString(R.string.pref_camera_hdr_default));
        String hdrPlus = mPreferences.getString(CameraSettings.KEY_CAMERA_HDR_PLUS,
                mActivity.getString(R.string.pref_camera_hdr_plus_default));
        boolean hdrOn = onValue.equals(hdr);
        boolean hdrPlusOn = onValue.equals(hdrPlus);

        boolean doGcamModeSwitch = false;
        if (hdrPlusOn && GcamHelper.hasGcamCapture()) {
            // Kick off mode switch to gcam.
            doGcamModeSwitch = true;
        } else {
            if (hdrOn) {
                mSceneMode = CameraUtil.SCENE_MODE_HDR;
                if (!(Parameters.SCENE_MODE_AUTO).equals(mParameters.getSceneMode())
                    && !(Parameters.SCENE_MODE_HDR).equals(mParameters.getSceneMode())) {
                    mParameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
                    mCameraDevice.setParameters(mParameters);
                    mParameters = mCameraDevice.getParameters();
                }
            } else {
                mSceneMode = mPreferences.getString(
                        CameraSettings.KEY_SCENE_MODE,
                        mActivity.getString(R.string.pref_camera_scenemode_default));
            }
        }
        if (CameraUtil.isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set exposure compensation
        int value = CameraSettings.readExposure(mPreferences);
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (value >= min && value <= max) {
            mParameters.setExposureCompensation(value);
        } else {
            Log.w(TAG, "invalid exposure range: " + value);
        }

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (CameraUtil.isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = mActivity.getString(
                            R.string.pref_camera_flashmode_no_flash);
                }
            }

            // Set white balance parameter.
            if ((mManual3AEnabled & MANUAL_WB) == 0) {
                String whiteBalance = mPreferences.getString(
                        CameraSettings.KEY_WHITE_BALANCE,
                        mActivity.getString(R.string.pref_camera_whitebalance_default));
                if (CameraUtil.isSupported(whiteBalance,
                        mParameters.getSupportedWhiteBalance())) {
                    mParameters.setWhiteBalance(whiteBalance);
                } else {
                    whiteBalance = mParameters.getWhiteBalance();
                    if (whiteBalance == null) {
                        whiteBalance = Parameters.WHITE_BALANCE_AUTO;
                    }
                }
            }

            // Set focus mode.
            if ((mManual3AEnabled & MANUAL_FOCUS) == 0) {
                mFocusManager.overrideFocusMode(null);
                mParameters.setFocusMode(mFocusManager.getFocusMode());
            }
        } else {
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
            if (hdrOn)
                mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            else {
                mParameters.setFlashMode(Parameters.FLASH_MODE_AUTO);
            }
            if (CameraUtil.isSupported(Parameters.WHITE_BALANCE_AUTO,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
            }
        }

        if (mContinuousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }
        //QCom related parameters updated here.
        qcomUpdateCameraParametersPreference();
        return doGcamModeSwitch;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mParameters.getFocusMode().equals(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if (mCameraDevice == null) {
            return;
        }
        synchronized (mCameraDevice) {
            boolean doModeSwitch = false;

            if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
                updateCameraParametersInitialize();
            }

            if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
                updateCameraParametersZoom();
            }

            if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
                doModeSwitch = updateCameraParametersPreference();
            }

            mCameraDevice.setParameters(mParameters);

            // Switch to gcam module if HDR+ was selected
            if (doModeSwitch && !mIsImageCaptureIntent) {
                mHandler.sendEmptyMessage(SWITCH_TO_GCAM_MODULE);
            }
        }
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
             if(mRestartPreview && mCameraState != PREVIEW_STOPPED) {
                Log.v(TAG, "Restarting Preview...");
                stopPreview();
                resizeForPreviewAspectRatio();
                startPreview();
                setCameraState(IDLE);
            }
            mRestartPreview = false;
            updateCameraSettings();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    @Override
    public boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                (mCameraState == PREVIEW_STOPPED) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted()
                        && (mCameraState != SWITCHING_CAMERA));
    }

    @Override
    public boolean isImageCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void UpdateManualFocusSettings() {
        //dismiss all popups first, because we need to show edit dialog
        mUI.collapseCameraControls();
        final AlertDialog.Builder alert = new AlertDialog.Builder(mActivity);
        LinearLayout linear = new LinearLayout(mActivity);
        linear.setOrientation(1);
        alert.setTitle("Manual Focus Settings");
        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int id)
            {
                dialog.cancel();
            }
        });
        final TextView focusPositionText = new TextView(mActivity);
        String scaleMode = mActivity.getString(
                R.string.pref_camera_manual_focus_value_scale_mode);
        String diopterMode = mActivity.getString(
                R.string.pref_camera_manual_focus_value_diopter_mode);
        String manualFocusMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_FOCUS,
                mActivity.getString(R.string.pref_camera_manual_focus_default));

        Log.v(TAG, "manualFocusMode selected = " + manualFocusMode);
        if (manualFocusMode.equals(scaleMode)) {
            final SeekBar focusbar = new SeekBar(mActivity);
            final int minFocusPos = mParameters.getInt(CameraSettings.KEY_MIN_FOCUS_SCALE);
            final int maxFocusPos = mParameters.getInt(CameraSettings.KEY_MAX_FOCUS_SCALE);
            //update mparameters to fetch latest focus position
            mParameters = mCameraDevice.getParameters();
            final int CurFocusPos = mParameters.getInt(CameraSettings.KEY_MANUAL_FOCUS_SCALE);
            focusbar.setProgress(CurFocusPos);
            focusPositionText.setText("Current focus position is " + CurFocusPos);

            alert.setMessage("Enter focus position in the range of " + minFocusPos
                    + " to " + maxFocusPos);

            focusbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                    focusPositionText.setText("Current focus position is " + progress);
                }
            });
            linear.addView(focusbar);
            linear.addView(focusPositionText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    int focusPos = focusbar.getProgress();
                    Log.v(TAG, "Setting focus position : " + focusPos);
                    mManual3AEnabled |= MANUAL_FOCUS;
                    mParameters.setFocusMode(Parameters.FOCUS_MODE_MANUAL_POSITION);
                    mParameters.set(CameraSettings.KEY_MANUAL_FOCUS_TYPE, 2); // 2 for scale mode
                    mParameters.set(CameraSettings.KEY_MANUAL_FOCUS_POSITION, focusPos);
                    updateCommonManual3ASettings();
                    onSharedPreferenceChanged();
                }
            });
            alert.show();
        } else if (manualFocusMode.equals(diopterMode)) {
            String minFocusStr = mParameters.get(CameraSettings.KEY_MIN_FOCUS_DIOPTER);
            String maxFocusStr = mParameters.get(CameraSettings.KEY_MAX_FOCUS_DIOPTER);
            final double minFocusPos = Double.parseDouble(minFocusStr);
            final double maxFocusPos = Double.parseDouble(maxFocusStr);
            final EditText input = new EditText(mActivity);
            int floatType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
            input.setInputType(floatType);
            alert.setMessage("Enter focus position in the range of " + minFocusPos
                    + " to " + maxFocusPos);
            //update mparameters to fetch latest focus position
            mParameters = mCameraDevice.getParameters();
            final String CurFocusPos = mParameters.get(CameraSettings.KEY_MANUAL_FOCUS_DIOPTER);
            focusPositionText.setText("Current focus position is " + CurFocusPos);
            linear.addView(input);
            linear.addView(focusPositionText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    double focuspos = 0;
                    String focusStr = input.getText().toString();
                    if (focusStr.length() > 0) {
                        focuspos = Double.parseDouble(focusStr);
                    } else {
                        Toast.makeText(mActivity, "Invalid focus position",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (focuspos >= minFocusPos && focuspos <= maxFocusPos) {
                        Log.v(TAG, "Setting focus position : " + focusStr);
                        mManual3AEnabled |= MANUAL_FOCUS;
                        mParameters.setFocusMode(Parameters.FOCUS_MODE_MANUAL_POSITION);
                        //focus type 3 is diopter mode
                        mParameters.set(CameraSettings.KEY_MANUAL_FOCUS_TYPE, 3);
                        mParameters.set(CameraSettings.KEY_MANUAL_FOCUS_POSITION, focusStr);
                        updateCommonManual3ASettings();
                        onSharedPreferenceChanged();
                    } else {
                        Toast.makeText(mActivity, "Invalid focus position",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else {
            mManual3AEnabled &= ~MANUAL_FOCUS;
            mParameters.setFocusMode(mFocusManager.getFocusMode());
            mUI.overrideSettings(CameraSettings.KEY_FOCUS_MODE, null);
            updateCommonManual3ASettings();
            onSharedPreferenceChanged();
        }
    }

    private void UpdateManualWBSettings() {
        //dismiss all popups first, because we need to show edit dialog
        mUI.collapseCameraControls();
        final AlertDialog.Builder alert = new AlertDialog.Builder(mActivity);
        LinearLayout linear = new LinearLayout(mActivity);
        linear.setOrientation(1);
        alert.setTitle("Manual White Balance Settings");
        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int id)
            {
                dialog.cancel();
            }
        });

        String cctMode = mActivity.getString(
                R.string.pref_camera_manual_wb_value_color_temperature);
        String rgbGainMode = mActivity.getString(
                R.string.pref_camera_manual_wb_value_rbgb_gains);
        String manualWBMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_WB,
                mActivity.getString(R.string.pref_camera_manual_wb_default));
        final String wbPref = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                mActivity.getString(R.string.pref_camera_whitebalance_default));
        Log.v(TAG, "manualWBMode selected = " + manualWBMode);
        if (manualWBMode.equals(cctMode)) {
            final TextView CCTtext = new TextView(mActivity);
            final EditText CCTinput = new EditText(mActivity);
            CCTinput.setInputType(InputType.TYPE_CLASS_NUMBER);
            final int minCCT = mParameters.getInt(CameraSettings.KEY_MIN_WB_CCT);
            final int maxCCT = mParameters.getInt(CameraSettings.KEY_MAX_WB_CCT);

            //refresh camera parameters to get latest CCT value
            mParameters = mCameraDevice.getParameters();
            String currentCCT = mParameters.get(CameraSettings.KEY_MANUAL_WB_CCT);
            if (currentCCT != null) {
                CCTtext.setText("Current CCT is " + currentCCT);
            }
            alert.setMessage("Enter CCT value in the range of " + minCCT+ " to " + maxCCT);
            linear.addView(CCTinput);
            linear.addView(CCTtext);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    int newCCT = -1;
                    String cct = CCTinput.getText().toString();
                    if (cct.length() > 0) {
                        newCCT = Integer.parseInt(cct);
                    }
                    if (newCCT <= maxCCT && newCCT >= minCCT) {
                        mManual3AEnabled |= MANUAL_WB;
                        Log.v(TAG, "Setting CCT value : " + newCCT);
                        mParameters.setWhiteBalance(CameraSettings.KEY_MANUAL_WHITE_BALANCE);
                        //0 corresponds to manual CCT mode
                        mParameters.set(CameraSettings.KEY_MANUAL_WB_TYPE, 0);
                        mParameters.set(CameraSettings.KEY_MANUAL_WB_VALUE, newCCT);
                        updateCommonManual3ASettings();
                        onSharedPreferenceChanged();
                    } else {
                        Toast.makeText(mActivity, "Invalid CCT", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualWBMode.equals(rgbGainMode)) {
            final TextView RGBtext = new TextView(mActivity);
            final EditText Rinput = new EditText(mActivity);
            Rinput.setHint("Enter R gain here");
            final EditText Ginput = new EditText(mActivity);
            Ginput.setHint("Enter G gain here");
            final EditText Binput = new EditText(mActivity);
            Binput.setHint("Enter B gain here");

            int floatType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
            Rinput.setInputType(floatType);
            Ginput.setInputType(floatType);
            Binput.setInputType(floatType);

            String minGainStr = mParameters.get(CameraSettings.KEY_MIN_WB_GAIN);
            final double minGain = Double.parseDouble(minGainStr);
            String maxGainStr = mParameters.get(CameraSettings.KEY_MAX_WB_GAIN);
            final double maxGain = Double.parseDouble(maxGainStr);

            //refresh camera parameters to get latest WB gains
            mParameters = mCameraDevice.getParameters();
            String currentGains = mParameters.get(CameraSettings.KEY_MANUAL_WB_GAINS);
            if (currentGains != null) {
                RGBtext.setText("Current RGB gains are " + currentGains);
            }

            alert.setMessage("Enter RGB gains in the range of " + minGain
                + " to " + maxGain);
            linear.addView(Rinput);
            linear.addView(Ginput);
            linear.addView(Binput);
            linear.addView(RGBtext);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    String Rgain = Rinput.getText().toString();
                    String Ggain = Ginput.getText().toString();
                    String Bgain = Binput.getText().toString();
                    if (Rgain.length() > 0 && Ggain.length() > 0 && Bgain.length() > 0) {
                        double Rgainf = Double.parseDouble(Rgain);
                        double Ggainf = Double.parseDouble(Ggain);
                        double Bgainf = Double.parseDouble(Bgain);
                        String RGBGain = Rgain + "," + Ggain + "," + Bgain;
                        if (Rgainf <= maxGain && Rgainf >= minGain &&
                            Ggainf <= maxGain && Ggainf >= minGain &&
                            Bgainf <= maxGain && Bgainf >= minGain) {
                            Log.v(TAG, "Setting RGB gains : " + RGBGain);
                            mManual3AEnabled |= MANUAL_WB;
                            mParameters.setWhiteBalance(CameraSettings.KEY_MANUAL_WHITE_BALANCE);
                            // 1 corresponds to manual WB gain mode
                            mParameters.set(CameraSettings.KEY_MANUAL_WB_TYPE, 1);
                            mParameters.set(CameraSettings.KEY_MANUAL_WB_VALUE, RGBGain);
                            updateCommonManual3ASettings();
                            onSharedPreferenceChanged();
                        } else {
                            Toast.makeText(mActivity, "Invalid RGB gains",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(mActivity, "Invalid RGB gains",
                                    Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else {
            //reset white balance
            mManual3AEnabled &= ~MANUAL_WB;
            mUI.overrideSettings(CameraSettings.KEY_WHITE_BALANCE, null);
            updateCommonManual3ASettings();
            onSharedPreferenceChanged();
        }
    }

    private void UpdateManualExposureSettings() {
        //dismiss all popups first, because we need to show edit dialog
        mUI.collapseCameraControls();
        final AlertDialog.Builder alert = new AlertDialog.Builder(mActivity);
        LinearLayout linear = new LinearLayout(mActivity);
        linear.setOrientation(1);
        final TextView ISOtext = new TextView(mActivity);
        final EditText ISOinput = new EditText(mActivity);
        final TextView ExpTimeText = new TextView(mActivity);
        final EditText ExpTimeInput = new EditText(mActivity);
        ISOinput.setInputType(InputType.TYPE_CLASS_NUMBER);
        int floatType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
        ExpTimeInput.setInputType(floatType);
        alert.setTitle("Manual Exposure Settings");
        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int id)
            {
                dialog.cancel();
            }
        });

        mParameters = mCameraDevice.getParameters();
        final int minISO = mParameters.getInt(CameraSettings.KEY_MIN_ISO);
        final int maxISO = mParameters.getInt(CameraSettings.KEY_MAX_ISO);
        String isoMode = mParameters.getISOValue();
        final String isoManual = CameraSettings.KEY_MANUAL_ISO;
        String currentISO = mParameters.get(CameraSettings.KEY_CURRENT_ISO);
        if (currentISO != null) {
            ISOtext.setText("Current ISO is " + currentISO);
        }

        final String minExpTime = mParameters.get(CameraSettings.KEY_MIN_EXPOSURE_TIME);
        final String maxExpTime = mParameters.get(CameraSettings.KEY_MAX_EXPOSURE_TIME);
        String currentExpTime = mParameters.get(CameraSettings.KEY_CURRENT_EXPOSURE_TIME);
        if (currentExpTime != null) {
            ExpTimeText.setText("Current exposure time is " + currentExpTime);
        }

        String isoPriority = mActivity.getString(
                R.string.pref_camera_manual_exp_value_ISO_priority);
        String expTimePriority = mActivity.getString(
                R.string.pref_camera_manual_exp_value_exptime_priority);
        String userSetting = mActivity.getString(
                R.string.pref_camera_manual_exp_value_user_setting);
        String manualExposureMode = mPreferences.getString(
                CameraSettings.KEY_MANUAL_EXPOSURE,
                mActivity.getString(R.string.pref_camera_manual_exp_default));
        Log.v(TAG, "manual Exposure Mode selected = " + manualExposureMode);
        if (manualExposureMode.equals(isoPriority)) {
            alert.setMessage("Enter ISO in the range of " + minISO + " to " + maxISO);
            linear.addView(ISOinput);
            linear.addView(ISOtext);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    int newISO = -1;
                    String iso = ISOinput.getText().toString();
                    Log.v(TAG, "string iso length " + iso.length());
                    if (iso.length() > 0) {
                        newISO = Integer.parseInt(iso);
                    }
                    if (newISO <= maxISO && newISO >= minISO) {
                        Log.v(TAG, "Setting ISO : " + newISO);
                        mManual3AEnabled |= MANUAL_EXPOSURE;
                        mParameters.setISOValue(isoManual);
                        mParameters.set(CameraSettings.KEY_CONTINUOUS_ISO, newISO);
                        mParameters.set(CameraSettings.KEY_EXPOSURE_TIME, "0");
                        updateCommonManual3ASettings();
                        onSharedPreferenceChanged();
                    } else {
                        Toast.makeText(mActivity, "Invalid ISO", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualExposureMode.equals(expTimePriority)) {
            alert.setMessage("Enter exposure time in the range of " + minExpTime
                + "ms to " + maxExpTime + "ms");
            linear.addView(ExpTimeInput);
            linear.addView(ExpTimeText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    double newExpTime = -1;
                    String expTime = ExpTimeInput.getText().toString();
                    if (expTime.length() > 0) {
                        newExpTime = Double.parseDouble(expTime);
                    }
                    if (newExpTime <= Double.parseDouble(maxExpTime) &&
                        newExpTime >= Double.parseDouble(minExpTime)) {
                        Log.v(TAG, "Setting Exposure time : " + newExpTime);
                        mManual3AEnabled |= MANUAL_EXPOSURE;
                        mParameters.set(CameraSettings.KEY_EXPOSURE_TIME, expTime);
                        mParameters.setISOValue(Parameters.ISO_AUTO);
                        mUI.setPreference(CameraSettings.KEY_ISO, Parameters.ISO_AUTO);
                        mUI.overrideSettings(CameraSettings.KEY_ISO, null);
                        updateCommonManual3ASettings();
                        onSharedPreferenceChanged();
                    } else {
                        Toast.makeText(mActivity, "Invalid exposure time",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualExposureMode.equals(userSetting)) {
            alert.setMessage("Full manual mode - Enter both ISO and Exposure Time");
            final TextView ISORangeText = new TextView(mActivity);
            final TextView ExpTimeRangeText = new TextView(mActivity);
            ISORangeText.setText("Enter ISO in the range of " + minISO + " to " + maxISO);
            ExpTimeRangeText.setText("Enter exposure time in the range of " + minExpTime
                    + "ms to " + maxExpTime + "ms");
            linear.addView(ISORangeText);
            linear.addView(ISOinput);
            linear.addView(ISOtext);
            linear.addView(ExpTimeRangeText);
            linear.addView(ExpTimeInput);
            linear.addView(ExpTimeText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog,int id)
                {
                    int newISO = -1;
                    String iso = ISOinput.getText().toString();
                    Log.v(TAG, "string iso length " + iso.length());
                    if (iso.length() > 0) {
                        newISO = Integer.parseInt(iso);
                    }
                    double newExpTime = -1;
                    String expTime = ExpTimeInput.getText().toString();
                    if (expTime.length() > 0) {
                        newExpTime = Double.parseDouble(expTime);
                    }
                    if (newISO <= maxISO && newISO >= minISO &&
                        newExpTime <= Double.parseDouble(maxExpTime) &&
                        newExpTime >= Double.parseDouble(minExpTime)) {
                        mManual3AEnabled |= MANUAL_EXPOSURE;
                        Log.v(TAG, "Setting ISO : " + newISO);
                        mParameters.setISOValue(isoManual);
                        mParameters.set(CameraSettings.KEY_CONTINUOUS_ISO, newISO);
                        Log.v(TAG, "Setting Exposure time : " + newExpTime);
                        mParameters.set(CameraSettings.KEY_EXPOSURE_TIME, expTime);
                        updateCommonManual3ASettings();
                        onSharedPreferenceChanged();
                    } else {
                        Toast.makeText(mActivity, "Invalid input", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else {
            mManual3AEnabled &= ~MANUAL_EXPOSURE;
            //auto exposure mode - reset both exposure time and ISO
            mParameters.set(CameraSettings.KEY_EXPOSURE_TIME, "0");
            mUI.overrideSettings(CameraSettings.KEY_ISO, null);
            updateCommonManual3ASettings();
            onSharedPreferenceChanged();
        }
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    @Override
    public void onSharedPreferenceChanged(ListPreference pref) {
        // ignore the events after "onPause()"
        if (mPaused) return;

        //filter off unsupported settings
        final String settingOff = mActivity.getString(R.string.setting_off_value);
        if (!CameraSettings.isZSLHDRSupported(mParameters)) {
            if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, settingOff)) {
                mUI.setPreference(CameraSettings.KEY_ZSL,settingOff);
            } else if (notSame(pref,CameraSettings.KEY_ZSL,settingOff)) {
                mUI.setPreference(CameraSettings.KEY_CAMERA_HDR, settingOff);
            }
        }

        if(CameraSettings.KEY_MANUAL_EXPOSURE.equals(pref.getKey())) {
            UpdateManualExposureSettings();
            return;
        }
        if (CameraSettings.KEY_MANUAL_WB.equals(pref.getKey())) {
            UpdateManualWBSettings();
            return;
        }
        if (CameraSettings.KEY_MANUAL_FOCUS.equals(pref.getKey())) {
            UpdateManualFocusSettings();
            return;
        }

        //call generic onSharedPreferenceChanged
        onSharedPreferenceChanged();
    }

    @Override
    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPaused) return;

        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);
        if(needRestart()){
            Log.v(TAG, "Restarting Preview... Camera Mode Changhed");
            stopPreview();
            startPreview();
            setCameraState(IDLE);
            mRestartPreview = false;
        }
        /* Check if the PhotoUI Menu is initialized or not. This
         * should be initialized during onCameraOpen() which should
         * have been called by now. But for some reason that is not
         * executed till now, then schedule these functionality for
         * later by posting a message to the handler */
        if (mUI.mMenuInitialized) {
            setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
            mUI.updateOnScreenIndicators(mParameters, mPreferenceGroup,
                mPreferences);
        } else {
            mHandler.sendEmptyMessage(SET_PHOTO_UI_PARAMS);
        }
        resizeForPreviewAspectRatio();
        if (mSeekBarInitialized == true){
            Log.v(TAG, "onSharedPreferenceChanged Skin tone bar: change");
            // skin tone is enabled only for party and portrait BSM
            // when color effects are not enabled
            String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                mActivity.getString(R.string.pref_camera_coloreffect_default));
            if((Parameters.SCENE_MODE_PARTY.equals(mSceneMode) ||
                Parameters.SCENE_MODE_PORTRAIT.equals(mSceneMode)) &&
                (Parameters.EFFECT_NONE.equals(colorEffect))) {
                Log.v(TAG, "Party/Portrait + No effect, SkinToneBar enabled");
            } else {
                disableSkinToneSeekBar();
            }
        }
        Storage.setSaveSDCard(
            mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));
    }

    @Override
    public void onCameraPickerClicked(int cameraId) {
        if (mPaused || mPendingSwitchCameraId != -1) return;

        mPendingSwitchCameraId = cameraId;

        Log.v(TAG, "Start to switch camera. cameraId=" + cameraId);
        // We need to keep a preview frame for the animation before
        // releasing the camera. This will trigger onPreviewTextureCopied.
        //TODO: Need to animate the camera switch
        switchCamera();
    }

    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    public void onPreviewTextureCopied() {
        mHandler.sendEmptyMessage(SWITCH_CAMERA);
    }

    @Override
    public void onCaptureTextureCopied() {
    }

    @Override
    public void onUserInteraction() {
        if (!mActivity.isFinishing()) keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    @Override
    public void onOverriddenPreferencesClicked() {
        if (mPaused) return;
        mUI.showPreferencesToast();
    }

    private void showTapToFocusToast() {
        // TODO: Use a toast?
        new RotateTextToast(mActivity, R.string.tap_to_focus, 0).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void initializeCapabilities() {
        mFocusAreaSupported = CameraUtil.isFocusAreaSupported(mInitialParams);
        mMeteringAreaSupported = CameraUtil.isMeteringAreaSupported(mInitialParams);
        mAeLockSupported = CameraUtil.isAutoExposureLockSupported(mInitialParams);
        mAwbLockSupported = CameraUtil.isAutoWhiteBalanceLockSupported(mInitialParams);
        mContinuousFocusSupported = mInitialParams.getSupportedFocusModes().contains(
                CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    @Override
    public void onCountDownFinished() {
        mSnapshotOnIdle = false;
        mFocusManager.doSnap();
        mFocusManager.onShutterUp();
        mUI.overrideSettings(CameraSettings.KEY_ZSL, null);
    }

    @Override
    public void onShowSwitcherPopup() {
        mUI.onShowSwitcherPopup();
    }

    @Override
    public int onZoomChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPaused) return index;
        mZoomValue = index;
        if (mParameters == null || mCameraDevice == null) return index;
        // Set zoom parameters asynchronously
        mParameters.setZoom(mZoomValue);
        mCameraDevice.setParameters(mParameters);
        Parameters p = mCameraDevice.getParameters();
        if (p != null) return p.getZoom();
        return index;
    }

    @Override
    public int getCameraState() {
        return mCameraState;
    }

    @Override
    public void onQueueStatus(boolean full) {
        mUI.enableShutter(!full);
    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (mFirstTimeInitialized) {
            s.setListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            // we should not be here.
            return;
        }
        for (int i = 0; i < 3 ; i++) {
            data[i] = event.values[i];
        }
        float[] orientation = new float[3];
        SensorManager.getRotationMatrix(mR, null, mGData, mMData);
        SensorManager.getOrientation(mR, orientation);
        mHeading = (int) (orientation[0] * 180f / Math.PI) % 360;
        if (mHeading < 0) {
            mHeading += 360;
        }
    }
    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mUI.onPreviewFocusChanged(previewFocused);
    }
    // TODO: Delete this function after old camera code is removed
    @Override
    public void onRestorePreferencesClicked() {}
    private void setSkinToneFactor() {
        if(mCameraDevice == null || mParameters == null || skinToneSeekBar == null)
            return;

        String skinToneEnhancementPref = "enable";
        if(CameraUtil.isSupported(skinToneEnhancementPref,
               mParameters.getSupportedSkinToneEnhancementModes())) {
            if(skinToneEnhancementPref.equals("enable")) {
                int skinToneValue =0;
                int progress;
                //get the value for the first time!
                if (mskinToneValue ==0) {
                    String factor = mPreferences.getString(
                         CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR, "0");
                    skinToneValue = Integer.parseInt(factor);
                }

                Log.v(TAG, "Skin tone bar: enable = " + mskinToneValue);
                enableSkinToneSeekBar();
                //As a wrokaround set progress again to show the actually progress on screen.
                if (skinToneValue != 0) {
                    progress = (skinToneValue/SCE_FACTOR_STEP)-MIN_SCE_FACTOR;
                    skinToneSeekBar.setProgress(progress);
                }
            } else {
                Log.v(TAG, "Skin tone bar: disable");
                disableSkinToneSeekBar();
            }
        } else {
            Log.v(TAG, "Skin tone bar: Not supported");
            skinToneSeekBar.setVisibility(View.INVISIBLE);
        }
    }

    private void enableSkinToneSeekBar() {
        int progress;
        if(brightnessProgressBar != null)
            brightnessProgressBar.setVisibility(View.INVISIBLE);
        skinToneSeekBar.setMax(MAX_SCE_FACTOR-MIN_SCE_FACTOR);
        skinToneSeekBar.setVisibility(View.VISIBLE);
        skinToneSeekBar.requestFocus();
        if (mskinToneValue != 0) {
            progress = (mskinToneValue/SCE_FACTOR_STEP)-MIN_SCE_FACTOR;
            mskinToneSeekListener.onProgressChanged(skinToneSeekBar, progress, false);
        } else {
            progress = (MAX_SCE_FACTOR-MIN_SCE_FACTOR)/2;
            RightValue.setText("");
            LeftValue.setText("");
        }
        skinToneSeekBar.setProgress(progress);
        mActivity.findViewById(R.id.linear).bringToFront();
        mActivity.findViewById(R.id.progress).setVisibility(View.GONE);
        skinToneSeekBar.bringToFront();
        Title.setText("Skin Tone Enhancement");
        Title.setVisibility(View.VISIBLE);
        RightValue.setVisibility(View.VISIBLE);
        LeftValue.setVisibility(View.VISIBLE);
        mSkinToneSeekBar = true;
    }

    private void disableSkinToneSeekBar() {
        skinToneSeekBar.setVisibility(View.INVISIBLE);
        Title.setVisibility(View.INVISIBLE);
        RightValue.setVisibility(View.INVISIBLE);
        LeftValue.setVisibility(View.INVISIBLE);
        mskinToneValue = 0;
        mSkinToneSeekBar = false;
        Editor editor = mPreferences.edit();
        editor.putString(CameraSettings.KEY_SKIN_TONE_ENHANCEMENT_FACTOR,
            Integer.toString(mskinToneValue - MIN_SCE_FACTOR));
        editor.apply();
        if ((brightnessProgressBar != null) && mBrightnessVisible)
             brightnessProgressBar.setVisibility(View.VISIBLE);
}

/*
 * Provide a mapping for Jpeg encoding quality levels
 * from String representation to numeric representation.
 */
    @Override
    public boolean arePreviewControlsVisible() {
        return mUI.arePreviewControlsVisible();
    }

    // For debugging only.
    public void setDebugUri(Uri uri) {
        mDebugUri = uri;
    }

    // For debugging only.
    private void saveToDebugUri(byte[] data) {
        if (mDebugUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = mContentResolver.openOutputStream(mDebugUri);
                outputStream.write(data);
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception while writing debug jpeg file", e);
            } finally {
                CameraUtil.closeSilently(outputStream);
            }
        }
    }
}

/* Below is no longer needed, except to get rid of compile error
 * TODO: Remove these
 */
class JpegEncodingQualityMappings {
    private static final String TAG = "JpegEncodingQualityMappings";
    private static final int DEFAULT_QUALITY = 85;
    private static HashMap<String, Integer> mHashMap =
            new HashMap<String, Integer>();

    static {
        mHashMap.put("normal",    CameraProfile.QUALITY_LOW);
        mHashMap.put("fine",      CameraProfile.QUALITY_MEDIUM);
        mHashMap.put("superfine", CameraProfile.QUALITY_HIGH);
    }

    // Retrieve and return the Jpeg encoding quality number
    // for the given quality level.
    public static int getQualityNumber(String jpegQuality) {
        try{
            int qualityPercentile = Integer.parseInt(jpegQuality);
            if(qualityPercentile >= 0 && qualityPercentile <=100)
                return qualityPercentile;
            else
                return DEFAULT_QUALITY;
        } catch(NumberFormatException nfe){
            //chosen quality is not a number, continue
        }
        Integer quality = mHashMap.get(jpegQuality);
        if (quality == null) {
            Log.w(TAG, "Unknown Jpeg quality: " + jpegQuality);
            return DEFAULT_QUALITY;
        }
        return CameraProfile.getJpegEncodingQualityParameter(quality.intValue());
    }
}

class GraphView extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mScale = (float)3;
    private float   mWidth;
    private float   mHeight;
    private PhotoModule mPhotoModule;
    private CameraManager.CameraProxy mGraphCameraDevice;
    private float scaled;
    private static final int STATS_SIZE = 256;
    private static final String TAG = "GraphView";


    public GraphView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mWidth = w;
        mHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        Log.v(TAG, "in Camera.java ondraw");
        if(mPhotoModule == null || !mPhotoModule.mHiston ) {
            Log.e(TAG, "returning as histogram is off ");
            return;
        }

        if (mBitmap != null) {
            final Paint paint = mPaint;
            final Canvas cavas = mCanvas;
            final float border = 5;
            float graphheight = mHeight - (2 * border);
            float graphwidth = mWidth - (2 * border);
            float left,top,right,bottom;
            float bargap = 0.0f;
            float barwidth = graphwidth/STATS_SIZE;

            cavas.drawColor(0xFFAAAAAA);
            paint.setColor(Color.BLACK);

            for (int k = 0; k <= (graphheight /32) ; k++) {
                float y = (float)(32 * k)+ border;
                cavas.drawLine(border, y, graphwidth + border , y, paint);
            }
            for (int j = 0; j <= (graphwidth /32); j++) {
                float x = (float)(32 * j)+ border;
                cavas.drawLine(x, border, x, graphheight + border, paint);
            }
            synchronized(PhotoModule.statsdata) {
                 //Assumption: The first element contains
                //            the maximum value.
                int maxValue = Integer.MIN_VALUE;
                if ( 0 == PhotoModule.statsdata[0] ) {
                    for ( int i = 1 ; i <= STATS_SIZE ; i++ ) {
                         if ( maxValue < PhotoModule.statsdata[i] ) {
                             maxValue = PhotoModule.statsdata[i];
                         }
                    }
                } else {
                    maxValue = PhotoModule.statsdata[0];
                }
                mScale = ( float ) maxValue;
                for(int i=1 ; i<=STATS_SIZE ; i++)  {
                    scaled = (PhotoModule.statsdata[i]/mScale)*STATS_SIZE;
                    if(scaled >= (float)STATS_SIZE)
                        scaled = (float)STATS_SIZE;
                    left = (bargap * (i+1)) + (barwidth * i) + border;
                    top = graphheight + border;
                    right = left + barwidth;
                    bottom = top - scaled;
                    cavas.drawRect(left, top, right, bottom, mPaintRect);
                }
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
        if (mPhotoModule.mHiston && mPhotoModule!= null) {
            mGraphCameraDevice = mPhotoModule.getCamera();
            if (mGraphCameraDevice != null){
                mGraphCameraDevice.sendHistogramData();
            }
        }
    }
    public void PreviewChanged() {
        invalidate();
    }
    public void setPhotoModuleObject(PhotoModule photoModule) {
        mPhotoModule = photoModule;
    }
}

class DrawAutoHDR extends View{

    private static final String TAG = "AutoHdrView";
    private PhotoModule mPhotoModule;

    public DrawAutoHDR (Context context, AttributeSet attrs) {
        super(context,attrs);
    }

    @Override
    protected void onDraw (Canvas canvas) {
        if (mPhotoModule == null)
            return;
        if (mPhotoModule.mAutoHdrEnable) {
            Paint AutoHDRPaint = new Paint();
            AutoHDRPaint.setColor(Color.WHITE);
            AutoHDRPaint.setAlpha (0);
            canvas.drawPaint(AutoHDRPaint);
            AutoHDRPaint.setStyle(Paint.Style.STROKE);
            AutoHDRPaint.setColor(Color.MAGENTA);
            AutoHDRPaint.setStrokeWidth(1);
            AutoHDRPaint.setTextSize(16);
            AutoHDRPaint.setAlpha (255);
            canvas.drawText("HDR On",200,100,AutoHDRPaint);
        }
        else {
            super.onDraw(canvas);
            return;
        }
    }

    public void AutoHDR () {
        invalidate();
    }

    public void setPhotoModuleObject (PhotoModule photoModule) {
        mPhotoModule = photoModule;
    }

}
