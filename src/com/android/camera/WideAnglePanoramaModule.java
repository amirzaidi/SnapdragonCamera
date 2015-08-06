/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import com.android.camera.PhotoModule;
import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.CameraManager.CameraPreviewDataCallback;
import com.android.camera.app.OrientationManager;
import com.android.camera.data.LocalData;
import com.android.camera.exif.ExifInterface;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

/**
 * Activity to handle panorama capturing.
 */
public class WideAnglePanoramaModule implements
        CameraModule, WideAnglePanoramaController,
        CameraPreviewDataCallback {
    public static final int DEFAULT_CAPTURE_PIXELS = 960 * 720;

    private static final int MSG_LOW_RES_FINAL_MOSAIC_READY = 1;
    private static final int MSG_GENERATE_FINAL_MOSAIC_ERROR = 2;
    private static final int MSG_END_DIALOG_RESET_TO_PREVIEW = 3;
    private static final int MSG_CLEAR_SCREEN_DELAY = 4;
    private static final int MSG_RESET_TO_PREVIEW = 5;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    @SuppressWarnings("unused")
    private static final String TAG = "CAM_WidePanoModule";
    private static final int PREVIEW_STOPPED = 0;
    private static final int PREVIEW_ACTIVE = 1;
    public static final int CAPTURE_STATE_VIEWFINDER = 0;
    public static final int CAPTURE_STATE_MOSAIC = 1;

    private static final boolean DEBUG = false;

    private ContentResolver mContentResolver;
    private WideAnglePanoramaUI mUI;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private boolean mUsingFrontCamera;
    private int mCameraPreviewWidth;
    private int mCameraPreviewHeight;
    private int mCameraState;
    private int mCaptureState;
    private MosaicFrameProcessor mMosaicFrameProcessor;
    private long mTimeTaken;
    private Handler mMainHandler;
    private SurfaceHolder mCameraSurfaceHolder;
    private boolean isCapturing = false;
    // Prefer FOCUS_MODE_INFINITY to FOCUS_MODE_CONTINUOUS_VIDEO because of
    // getting a better image quality by the former.
    private String mTargetFocusMode = Parameters.FOCUS_MODE_INFINITY;

    private PanoOrientationEventListener mOrientationEventListener;
    // The value could be 0, 90, 180, 270 for the 4 different orientations measured in clockwise
    // respectively.
    private int mDeviceOrientation;
    private int mDeviceOrientationAtCapture;
    private int mCameraOrientation;
    private int mOrientationCompensation;
    private boolean mOrientationLocked;

    private SoundClips.Player mSoundPlayer;

    private CameraActivity mActivity;
    private View mRootView;
    private CameraProxy mCameraDevice;
    private boolean mPaused;

    private LocationManager mLocationManager;
    private OrientationManager mOrientationManager;
    private ComboPreferences mPreferences;
    private boolean mMosaicPreviewConfigured;
    private boolean mPreviewFocused = true;
    private boolean mPreviewLayoutChanged = false;

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mCameraDisplayOrientation;
    // The value for UI components like indicators.
    private int mDisplayOrientation;

    private int mCaptureOrientation = MosaicFrameProcessor.DIRECTION_UNKNOW;
    private int mPanoState = STATUS_PREVIEW;
    public static final int STATUS_PREVIEW = 0x0001;
    public static final int STATUS_PREPARE = 0x0002;
    public static final int STATUS_CAPTURING = 0x0003;
    public static final int STATUS_SUCCESS = 0x0004;
    public static final int STATUS_FAILED = 0x0006;

    @Override
    public void onPreviewUIReady() {
        resetToPreviewIfPossible();
    }

    @Override
    public void onPreviewUIDestroyed() {

    }

    private class MosaicJpeg {
        public MosaicJpeg(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.isValid = true;
        }

        public MosaicJpeg() {
            this.data = null;
            this.width = 0;
            this.height = 0;
            this.isValid = false;
        }

        public final byte[] data;
        public final int width;
        public final int height;
        public final boolean isValid;
    }

    private class PanoOrientationEventListener extends OrientationEventListener {
        public PanoOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            int oldOrientation = mDeviceOrientation;
            mDeviceOrientation = CameraUtil.roundOrientation(orientation, mDeviceOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mDeviceOrientation
                    + CameraUtil.getDisplayRotation(mActivity) % 360;
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
            }
            if (oldOrientation != mDeviceOrientation
                    && oldOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                mPreviewLayoutChanged = true;
                if (!mOrientationLocked)
                    mUI.setOrientation(mDeviceOrientation, true);
            }
        }
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;

        mOrientationManager = new OrientationManager(activity);
        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        mUI = new WideAnglePanoramaUI(mActivity, this, (ViewGroup) mRootView);

        mContentResolver = mActivity.getContentResolver();

        mOrientationEventListener = new PanoOrientationEventListener(mActivity);

        mMosaicFrameProcessor = MosaicFrameProcessor.getInstance();

        Resources appRes = mActivity.getResources();

        mPreferences = new ComboPreferences(mActivity);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), activity);
        mLocationManager = new LocationManager(mActivity, null);

        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_END_DIALOG_RESET_TO_PREVIEW:
                        resetToPreviewIfPossible();
                        break;
                    case MSG_CLEAR_SCREEN_DELAY:
                        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.
                                FLAG_KEEP_SCREEN_ON);
                        break;
                    case MSG_RESET_TO_PREVIEW:
                        resetToPreviewIfPossible();
                        break;
                }
            }
        };
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mPreviewFocused = previewFocused;
        mUI.onPreviewFocusChanged(previewFocused);
    }

    @Override
    public boolean arePreviewControlsVisible() {
        return mUI.arePreviewControlsVisible();
    }

    /**
     * Opens camera and sets the parameters.
     *
     * @return Whether the camera was opened successfully.
     */
    private boolean setupCamera() {
        if (!openCamera()) {
            return false;
        }
        Parameters parameters = mCameraDevice.getParameters();
        setupCaptureParams(parameters);
        configureCamera(parameters);
        return true;
    }

    private void releaseCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mCameraDevice.setErrorCallback(null);
            mCameraDevice = null;
            mCameraState = PREVIEW_STOPPED;
        }
    }

    /**
     * Opens the camera device. The back camera has priority over the front
     * one.
     *
     * @return Whether the camera was opened successfully.
     */
    private boolean openCamera() {
        int cameraId = CameraHolder.instance().getBackCameraId();
        // If there is no back camera, use the first camera. Camera id starts
        // from 0. Currently if a camera is not back facing, it is front facing.
        // This is also forward compatible if we have a new facing other than
        // back or front in the future.
        if (cameraId == -1) cameraId = 0;
        mCameraDevice = CameraUtil.openCamera(mActivity, cameraId,
                mMainHandler, mActivity.getCameraOpenErrorCallback());
        if (mCameraDevice == null) {
            return false;
        }
        mCameraOrientation = CameraUtil.getCameraOrientation(cameraId);
        if (cameraId == CameraHolder.instance().getFrontCameraId()) mUsingFrontCamera = true;
        return true;
    }

    private boolean findBestPreviewSize(List<Size> supportedSizes, boolean need4To3,
            boolean needSmaller) {
        int pixelsDiff = DEFAULT_CAPTURE_PIXELS;
        boolean hasFound = false;
        for (Size size : supportedSizes) {
            int h = size.height;
            int w = size.width;
            // we only want 4:3 format.
            int d = DEFAULT_CAPTURE_PIXELS - h * w;
            if (needSmaller && d < 0) { // no bigger preview than 960x720.
                continue;
            }
            if (need4To3 && (h * 4 != w * 3)) {
                continue;
            }
            d = Math.abs(d);
            if (d < pixelsDiff) {
                mCameraPreviewWidth = w;
                mCameraPreviewHeight = h;
                pixelsDiff = d;
                hasFound = true;
            }
        }
        return hasFound;
    }

    private void setupCaptureParams(Parameters parameters) {
        List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
        if (!findBestPreviewSize(supportedSizes, true, true)) {
            Log.w(TAG, "No 4:3 ratio preview size supported.");
            if (!findBestPreviewSize(supportedSizes, false, true)) {
                Log.w(TAG, "Can't find a supported preview size smaller than 960x720.");
                findBestPreviewSize(supportedSizes, false, false);
            }
        }
        Log.d(TAG, "camera preview h = "
                    + mCameraPreviewHeight + " , w = " + mCameraPreviewWidth);
        parameters.setPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);

        List<int[]> frameRates = parameters.getSupportedPreviewFpsRange();
        int last = frameRates.size() - 1;
        int minFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MAX_INDEX];
        parameters.setPreviewFpsRange(minFps, maxFps);
        Log.d(TAG, "preview fps: " + minFps + ", " + maxFps);

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes.indexOf(mTargetFocusMode) >= 0) {
            parameters.setFocusMode(mTargetFocusMode);
        } else {
            // Use the default focus mode and log a message
            Log.w(TAG, "Cannot set the focus mode to " + mTargetFocusMode +
                  " becuase the mode is not supported.");
        }

        parameters.set(CameraUtil.RECORDING_HINT, CameraUtil.FALSE);
    }

    public int getPreviewBufSize() {
        PixelFormat pixelInfo = new PixelFormat();
        PixelFormat.getPixelFormatInfo(mCameraDevice.getParameters().getPreviewFormat(), pixelInfo);
        // TODO: remove this extra 32 byte after the driver bug is fixed.
        return (mCameraPreviewWidth * mCameraPreviewHeight * pixelInfo.bitsPerPixel / 8) + 32;
    }

    private void configureCamera(Parameters parameters) {
        mCameraDevice.setParameters(parameters);
    }

    /**
     * Receives the layout change event from the preview area. So we can
     * initialize the mosaic preview renderer.
     */
    @Override
    public void onPreviewUILayoutChange(int l, int t, int r, int b) {
        Log.d(TAG, "layout change: " + (r - l) + "/" + (b - t));
        boolean capturePending = false;
        if (mCaptureState == CAPTURE_STATE_MOSAIC){
            capturePending = true;
        }
        if (capturePending == true){
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mPaused){
                        mMainHandler.removeMessages(MSG_RESET_TO_PREVIEW);
                        startCapture();
                    }
                }
            });
        }
    }

    public void startCapture() {
        // Reset values so we can do this again.
        mTimeTaken = System.currentTimeMillis();
        mActivity.setSwipingEnabled(false);
        mCaptureState = CAPTURE_STATE_MOSAIC;
        mUI.onStartCapture();
        mCaptureOrientation = MosaicFrameProcessor.DIRECTION_UNKNOW;
        Parameters parameters = mCameraDevice.getParameters();
        parameters.setAutoExposureLock(true);
        parameters.setAutoWhiteBalanceLock(true);
        configureCamera(parameters);
        updateState(STATUS_PREPARE);
        createPanoramaEngine();

        mDeviceOrientationAtCapture = mDeviceOrientation;
        keepScreenOn();
        mOrientationLocked = true;
    }

    private void createPanoramaEngine() {
        if (mMosaicFrameProcessor == null) {
            mMosaicFrameProcessor = MosaicFrameProcessor.getInstance();
        }
        mMosaicFrameProcessor.Init(mActivity, 30, mCameraPreviewWidth, mCameraPreviewHeight,
                mPanoNotifier);
    }

    private void stopCapture(boolean aborted) {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        mUI.onStopCapture();
        Parameters parameters = mCameraDevice.getParameters();
        parameters.setAutoExposureLock(false);
        parameters.setAutoWhiteBalanceLock(false);
        configureCamera(parameters);

        if (mMosaicFrameProcessor != null) {
            mMosaicFrameProcessor.StopProcessing();
        }

        stopCameraPreview();
        mMainHandler.sendMessage(mMainHandler.obtainMessage(
                MSG_END_DIALOG_RESET_TO_PREVIEW));

        keepScreenOnAwhile();
    }

    @Override
    public void onShutterButtonClick() {
        if (mPaused || mCameraSurfaceHolder == null) {
            return;
        }
        // Since this button will stay on the screen when capturing, we need to check the state
        // right now.
        switch (mCaptureState) {
            case CAPTURE_STATE_VIEWFINDER:
                final long storageSpaceBytes = mActivity.getStorageSpaceBytes();
                if(storageSpaceBytes <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
                    Log.w(TAG, "Low storage warning: " + storageSpaceBytes);
                    return;
                }
                mSoundPlayer.play(SoundClips.START_VIDEO_RECORDING);
                startCapture();
                break;
            case CAPTURE_STATE_MOSAIC:
                mSoundPlayer.play(SoundClips.STOP_VIDEO_RECORDING);
                stopCapture(false);
                break;
            default:
                Log.w(TAG, "Unknown capture state: " + mCaptureState);
                break;
        }
    }

    private int getCaptureOrientation() {
        // The panorama image returned from the library is oriented based on the
        // natural orientation of a camera. We need to set an orientation for the image
        // in its EXIF header, so the image can be displayed correctly.
        // The orientation is calculated from compensating the
        // device orientation at capture and the camera orientation respective to
        // the natural orientation of the device.
        int orientation;
        if (mUsingFrontCamera) {
            // mCameraOrientation is negative with respect to the front facing camera.
            // See document of android.hardware.Camera.Parameters.setRotation.
            orientation = (mDeviceOrientationAtCapture - mCameraOrientation - 360) % 360;
        } else {
            orientation = (mDeviceOrientationAtCapture + mCameraOrientation) % 360;
        }
        return orientation;
    }

    /** The orientation of the camera image. The value is the angle that the camera
     *  image needs to be rotated clockwise so it shows correctly on the display
     *  in its natural orientation. It should be 0, 90, 180, or 270.*/
    public int getCameraOrientation() {
        return mCameraOrientation;
    }

    // This function will be called upon the first camera frame is available.
    private void reset() {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        mOrientationLocked = false;
        mUI.setOrientation(mDeviceOrientation, true);
        mUI.reset();
        mActivity.setSwipingEnabled(true);
        // Orientation change will trigger onLayoutChange->configMosaicPreview->
        // resetToPreview. Do not show the capture UI in film strip.
        if (mPreviewFocused) {
            mUI.showPreviewUI();
        }
    }

    private void resetToPreviewIfPossible() {
        reset();
        if (mUI.getSurfaceHolder() == null) {
            return;
        }
        if (!mPaused) {
            startCameraPreview();
        }
    }

    private void showFinalMosaic(Bitmap bitmap) {
        mUI.showFinalMosaic(bitmap, getCaptureOrientation());
    }

    private Uri savePanorama(byte[] jpegData, int width, int height, int orientation) {
        if (jpegData != null) {
            String filename = PanoUtil.createName(
                    mActivity.getResources().getString(R.string.pano_file_name_format), mTimeTaken);
            String filepath = Storage.generateFilepath(filename,
                              PhotoModule.PIXEL_FORMAT_JPEG);

            UsageStatistics.onEvent(UsageStatistics.COMPONENT_PANORAMA,
                    UsageStatistics.ACTION_CAPTURE_DONE, null, 0,
                    UsageStatistics.hashFileName(filename + ".jpg"));

            Location loc = mLocationManager.getCurrentLocation();
            ExifInterface exif = new ExifInterface();
            try {
                exif.readExif(jpegData);
                exif.addGpsDateTimeStampTag(mTimeTaken);
                exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, mTimeTaken,
                        TimeZone.getDefault());
                exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.getOrientationValueForRotation(orientation)));
                writeLocation(loc, exif);
                exif.writeExif(jpegData, filepath);
            } catch (IOException e) {
                Log.e(TAG, "Cannot set exif for " + filepath, e);
                Storage.writeFile(filepath, jpegData);
            }
            int jpegLength = (int) (new File(filepath).length());
            return Storage.addImage(mContentResolver, filename, mTimeTaken, loc, orientation,
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
    public void onPauseBeforeSuper() {
        mPaused = true;
        if (mLocationManager != null) mLocationManager.recordLocation(false);
        mOrientationManager.pause();
    }

    @Override
    public void onPauseAfterSuper() {
        mOrientationEventListener.disable();
        if (mCameraDevice == null) {
            // Camera open failed. Nothing should be done here.
            return;
        }
        // Stop the capturing first.
        if (mCaptureState == CAPTURE_STATE_MOSAIC) {
            stopCapture(true);
            reset();
        }
        mUI.showPreviewCover();
        releaseCamera();
        mCameraSurfaceHolder = null;

        resetScreenOn();
        mUI.removeDisplayChangeListener();
        if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
        System.gc();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mUI.onConfigurationChanged(newConfig, false);
    }

    @Override
    public void onOrientationChanged(int orientation) {
    }

    @Override
    public void resizeForPreviewAspectRatio() {
    }

    @Override
    public void onSwitchSavePath() {
        mPreferences.getGlobal().edit().putString(CameraSettings.KEY_CAMERA_SAVEPATH, "1").apply();
        RotateTextToast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        mOrientationEventListener.enable();

        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        if (!setupCamera()) {
            CameraUtil.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
            Log.e(TAG, "Failed to open camera, aborting");
            return;
        }
        resetToPreviewIfPossible();

        // Set up sound playback for shutter button
        mSoundPlayer = SoundClips.getPlayer(mActivity);

        // Check if another panorama instance is using the mosaic frame processor.
        mUI.dismissAllDialogs();

        mMainHandler.post(new Runnable(){
            @Override
            public void run(){
                mActivity.updateStorageSpaceAndHint();
            }
        });
        keepScreenOnAwhile();

        mOrientationManager.resume();
        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                mContentResolver);
        mLocationManager.recordLocation(recordLocation);
        mUI.initDisplayChangeListener();
        UsageStatistics.onContentViewChanged(
                UsageStatistics.COMPONENT_CAMERA, "PanoramaModule");
        mUI.hidePreviewCover();
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, CameraHolder
                .instance().getBackCameraId());
        mCameraDisplayOrientation = mDisplayOrientation;

        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    private void startCameraPreview() {
        if (mCameraDevice == null) {
            // Camera open failed. Return.
            return;
        }

        if (mUI.getSurfaceHolder() == null) {
            // UI is not ready.
            return;
        }
        mErrorCallback.setActivity(mActivity);
        mCameraDevice.setErrorCallback(mErrorCallback);

        if (mUI.getSurfaceHolder() == null) return;
        mCameraSurfaceHolder = mUI.getSurfaceHolder();

        // If we're previewing already, stop the preview first (this will
        // blank the screen).
        if (mCameraState != PREVIEW_STOPPED) stopCameraPreview();

        mCameraDevice.setPreviewDataCallback(mMainHandler, this);
        mCameraDevice.setPreviewDisplay(mCameraSurfaceHolder);

        mCameraDevice.startPreview();
        setDisplayOrientation();
        mCameraState = PREVIEW_ACTIVE;
    }

    private void stopCameraPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.stopPreview();
        }
        mCameraState = PREVIEW_STOPPED;
    }

    @Override
    public void onUserInteraction() {
        if (mCaptureState != CAPTURE_STATE_MOSAIC) keepScreenOnAwhile();
    }

    @Override
    public boolean onBackPressed() {
        // If panorama is generating low res or high res mosaic, ignore back
        // key. So the activity will not be destroyed.
        if (mUI.hideSwitcherPopup())
            return true;

        return false;
    }

    private void resetScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void cancelHighResStitching() {
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
    public void onPreviewFrame(byte[] data, CameraProxy camera) {
        if (mMosaicFrameProcessor != null && mMosaicFrameProcessor.IsInited()) {
            mMosaicFrameProcessor.Process(data, mCameraPreviewWidth, mCameraPreviewHeight);
        }
    }

    /**
     * This class defines panorama stitching information.
     **/
    static class PanoramaInfo {
        /** The angle for the panorama information. */
        public int angle;
        /** The offset of the next preview frame to be acquired. */
        public Point offset;
        /** The direction of capturing. */
        public int direction;
        /**
         * Indicate whether current frame is properly positioned to be added for
         * stitching: 0 for not properly positioned, 1 for ready to stitch.
         */
        public int selected;

        /**
         * This is the default constructor of the panorama information object.
         */
        public PanoramaInfo() {
            offset = new Point(0, 0);
        }

        public PanoramaInfo(int ang, Point ptOffset, int direct, int sel) {
            angle = ang;
            offset = new Point(ptOffset.x, ptOffset.y);
            direction = direct;
            selected = sel;
        }

        public PanoramaInfo(int ang, int offsetX, int offsetY, int direct, int sel) {
            angle = ang;
            offset = new Point(offsetX, offsetY);
            direction = direct;
            selected = sel;
        }
    }

    public interface INotifier {
        /**
         * Messages are notified to the application through this function.
         *
         * @param key The message key.
         * @param obj The message object with this message key.
         * @return The user defined result of processing message.
         */
        int onNotify(int key, Object obj);
    }

    private final INotifier mPanoNotifier = new INotifier() {
        @Override
        public int onNotify(int key, Object obj) {
            byte[] imageData;
            int len;
            int width;
            int height;
            YuvImage yuvimage;
            ByteArrayOutputStream out;
            switch (key) {
                case MosaicFrameProcessor.MSG_PANORAMA_TIP:
                    if (mCaptureState == CAPTURE_STATE_MOSAIC) {
                        PanoramaInfo panoInfo = (PanoramaInfo) obj;
                        if (null == panoInfo)
                            break;
                        if (!isCapturing) {
                            isCapturing = true;

                            /**
                             * Device Orientation and Capture Orientation Note
                             * that: Pano's left is always device's top; Pano's
                             * right is always device's bottom Pano's top is
                             * always device's right; Pano's bottom is always
                             * device's left
                             */
                            mCaptureOrientation = panoInfo.direction;
                            updateState(STATUS_CAPTURING);
                        }

                        boolean bIsHorizonal = MosaicFrameProcessor.DIRECTION_LEFTTORIGHT == panoInfo.direction
                                || MosaicFrameProcessor.DIRECTION_RIGHTTOLEFT == panoInfo.direction;
                        int iAbsAngle = Math.abs(panoInfo.angle);
                        if ((bIsHorizonal && iAbsAngle >= MosaicFrameProcessor.MAX_HORIZONAL_ANGLE)
                                || (!bIsHorizonal && iAbsAngle >= MosaicFrameProcessor.MAX_VERTICAL_ANGLE)) {
                            Log.v(TAG, TAG + "capture end !");
                            break;
                        }
                    }
                    break;

                case MosaicFrameProcessor.MSG_CAPTURE_FAILED:
                    if (mCaptureState == CAPTURE_STATE_MOSAIC) {
                        mMosaicFrameProcessor.Uninit();
                    }
                    break;

                case MosaicFrameProcessor.MSG_CAPTURE_SUCCESS:
                    imageData = (byte[]) obj;

                    len = imageData.length - 8;
                    width = (imageData[len + 0] << 24) + ((imageData[len + 1] & 0xFF) << 16)
                            + ((imageData[len + 2] & 0xFF) << 8) + (imageData[len + 3] & 0xFF);
                    height = (imageData[len + 4] << 24) + ((imageData[len + 5] & 0xFF) << 16)
                            + ((imageData[len + 6] & 0xFF) << 8) + (imageData[len + 7] & 0xFF);

                    if (width == 0 || height == 0) {
                        onNotify(MosaicFrameProcessor.MSG_CAPTURE_FAILED, null);
                        return 0;
                    }

                    yuvimage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
                    out = new ByteArrayOutputStream();
                    yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                    final MosaicJpeg jpeg = new MosaicJpeg(out.toByteArray(), width, height);
                    try {
                        out.close();
                    } catch (Exception e) {
                        mMosaicFrameProcessor.Uninit();
                        Log.e(TAG, "Exception in storing final mosaic", e);
                        return 0;
                    }

                    mMainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            saveFinalMosaic(jpeg);
                        }
                    }, 200);
                    break;
                case MosaicFrameProcessor.MSG_UPDATE_UI:
                    if (mCaptureState == CAPTURE_STATE_MOSAIC) {
                        if (mCaptureOrientation == 0)
                            break;
                        imageData = (byte[]) obj;
                        len = imageData.length - 8;
                        width = (imageData[len + 0] << 24) + ((imageData[len + 1] & 0xFF) << 16)
                                + ((imageData[len + 2] & 0xFF) << 8) + (imageData[len + 3] & 0xFF);
                        height = (imageData[len + 4] << 24) + ((imageData[len + 5] & 0xFF) << 16)
                                + ((imageData[len + 6] & 0xFF) << 8) + (imageData[len + 7] & 0xFF);

                        yuvimage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
                        out = new ByteArrayOutputStream();
                        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                        byte[] jpeg2 = out.toByteArray();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg2, 0, jpeg2.length);
                        Matrix rotateMatrix = new Matrix();
                        rotateMatrix.setRotate(getCaptureOrientation());
                        bitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                                rotateMatrix, false);
                        try {
                            out.close();
                        } catch (Exception e) {
                        }

                        boolean horiz = true;
                        switch (mDeviceOrientationAtCapture) {
                            case 0:
                            case 180:
                                horiz = (mCaptureOrientation == 4 || mCaptureOrientation == 8);
                                break;
                            case 90:
                            case 270:
                                horiz = (mCaptureOrientation == 1 || mCaptureOrientation == 2);
                                break;

                        }
                        mUI.drawCapturePreview(bitmap, mDeviceOrientationAtCapture, horiz);
                    }
                    break;
                default:
                    Log.v(TAG, "on Notify unknown MSG: " + key);
                    break;
            }
            return 0;
        }
    };

    void saveFinalMosaic(MosaicJpeg jpeg) {
        mMosaicFrameProcessor.Uninit();
        if (mCaptureState == CAPTURE_STATE_MOSAIC)
            stopCapture(false);
        Bitmap bitmap = null;
        bitmap = BitmapFactory.decodeByteArray(jpeg.data, 0, jpeg.data.length);
        int orientation = getCaptureOrientation();
        showFinalMosaic(bitmap);

        final Uri uri = savePanorama(jpeg.data, jpeg.width, jpeg.height, orientation);
        if (uri != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.notifyNewMedia(uri);
                }
            });
        }
        updateState(STATUS_SUCCESS);
    }

    public void updateState(int status) {
        mPanoState = status;
        switch (status) {
            case STATUS_PREVIEW:
            case STATUS_SUCCESS:
            case STATUS_PREPARE:
            case STATUS_FAILED:
                isCapturing = false;
                break;

            case STATUS_CAPTURING:
                isCapturing = true;
                break;
        }
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
    public void onSingleTapUp(View view, int x, int y) {
    }

    @Override
    public void onPreviewTextureCopied() {
    }

    @Override
    public void onCaptureTextureCopied() {
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return false;
    }

    @Override
    public void onShowSwitcherPopup() {
    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        // do nothing.
    }
}
