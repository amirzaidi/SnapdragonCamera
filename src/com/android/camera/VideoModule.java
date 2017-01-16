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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.VideoEncoderCap;

import com.android.camera.CameraManager.CameraPictureCallback;
import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.app.OrientationManager;
import com.android.camera.exif.ExifInterface;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.AccessibilityUtils;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;
import com.android.camera.PhotoModule;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class VideoModule implements CameraModule,
    VideoController,
    CameraPreference.OnPreferenceChangedListener,
    ShutterButton.OnShutterButtonListener,
    LocationManager.Listener,
    MediaRecorder.OnErrorListener,
    MediaRecorder.OnInfoListener {

    private static final String TAG = "CAM_VideoModule";

    private static final int CHECK_DISPLAY_ROTATION = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;
    private static final int SHOW_TAP_TO_SNAPSHOT_TOAST = 7;
    private static final int SWITCH_CAMERA = 8;
    private static final int SWITCH_CAMERA_START_ANIMATION = 9;
    private static final int HANDLE_FLASH_TORCH_DELAY = 10;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final int SDCARD_SIZE_LIMIT = 4000 * 1024 * 1024;

    private static final long SHUTTER_BUTTON_TIMEOUT = 0L; // 0ms

    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // module fields
    private CameraActivity mActivity;
    private boolean mPaused;
    private int mCameraId;
    private Parameters mParameters;

    private boolean mIsInReviewMode;
    private boolean mSnapshotInProgress = false;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private ComboPreferences mPreferences;
    private PreferenceGroup mPreferenceGroup;
    private boolean mSaveToSDCard = false;

    // Preference must be read before starting preview. We check this before starting
    // preview.
    private boolean mPreferenceRead;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;

    private MediaRecorder mMediaRecorder;

    private boolean mSwitchingCamera;
    private boolean mMediaRecorderRecording = false;
    private boolean mMediaRecorderPausing = false;
    private long mRecordingStartTime;
    private long mRecordingTotalTime;
    private boolean mRecordingTimeCountsDown = false;
    private long mOnResumeTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private boolean mCurrentVideoUriFromMediaSaved;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    // Time Lapse parameters.
    private boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;

    boolean mPreviewing = false; // True if preview is started.
    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;
    private int mCameraDisplayOrientation;

    private int mDesiredPreviewWidth;
    private int mDesiredPreviewHeight;
    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;
    private OrientationManager mOrientationManager;
    private int mPendingSwitchCameraId;
    private final Handler mHandler = new MainHandler();
    private VideoUI mUI;
    private CameraProxy mCameraDevice;
    private static final String KEY_PREVIEW_FORMAT = "preview-format";
    private static final String FORMAT_NV12_VENUS = "nv12-venus";
    private static final String FORMAT_NV21 = "yuv420sp";

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private int mZoomValue;  // The current zoom value.

    private boolean mStartRecPending = false;
    private boolean mStopRecPending = false;
    private boolean mStartPrevPending = false;
    private boolean mStopPrevPending = false;
    private String mPrevSavedVideoCDS = null;
    private String mTempVideoCDS = null;
    private boolean mIsVideoTNREnabled;
    private boolean mIsVideoCDSUpdated = false;
    private boolean mOverrideCDS = false;

    //settings, which if enabled, need to turn off low power mode
    private boolean mIsFlipEnabled = false;
    private boolean mIsDISEnabled = false;

    // The preview window is on focus
    private boolean mPreviewFocused = false;

    private boolean mIsMute = false;
    private boolean mWasMute = false;

    private boolean mFaceDetectionEnabled = false;
    private boolean mFaceDetectionStarted = false;

    private static final boolean PERSIST_4K_NO_LIMIT =
            android.os.SystemProperties.getBoolean("persist.camcorder.4k.nolimit", false);

    private static final int PERSIST_EIS_MAX_FPS =
            android.os.SystemProperties.getInt("persist.camcorder.eis.maxfps", 30);

    private final MediaSaveService.OnMediaSavedListener mOnVideoSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mCurrentVideoUri = uri;
                        mCurrentVideoUriFromMediaSaved = true;
                        onVideoSaved();
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    private final MediaSaveService.OnMediaSavedListener mOnPhotoSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    public void setMute(boolean enable, boolean isValue)
    {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        am.setMicrophoneMute(enable);
        if(isValue) {
            mIsMute = enable;
        }
    }

    public boolean isAudioMute()
    {
        return mIsMute;
    }

    protected class CameraOpenThread extends Thread {
        @Override
        public void run() {
            openCamera();
        }
    }

    private void openCamera() {
        if (mCameraDevice == null) {
            mCameraDevice = CameraUtil.openCamera(
                    mActivity, mCameraId, mHandler,
                    mActivity.getCameraOpenErrorCallback());
        }
        if (mCameraDevice == null) {
            // Error.
            return;
        }
        mParameters = mCameraDevice.getParameters();
        mPreviewFocused = arePreviewControlsVisible();
    }

    //QCOM data Members Starts here
    static class DefaultHashMap<K, V> extends HashMap<K, V> {
        private V mDefaultValue;

        public void putDefault(V defaultValue) {
            mDefaultValue = defaultValue;
        }

        @Override
        public V get(Object key) {
            V value = super.get(key);
            return (value == null) ? mDefaultValue : value;
        }
        public K getKey(V toCheck) {
            Iterator<K> it = this.keySet().iterator();
            V val;
            K key;
            while(it.hasNext()) {
                key = it.next();
                val = this.get(key);
                if (val.equals(toCheck)) {
                    return key;
                }
            }
        return null;
        }
    }


    private static final DefaultHashMap<String, Integer>
            OUTPUT_FORMAT_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            VIDEO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            AUDIO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            VIDEOQUALITY_BITRATE_TABLE = new DefaultHashMap<String, Integer>();

    static {
        OUTPUT_FORMAT_TABLE.put("3gp", MediaRecorder.OutputFormat.THREE_GPP);
        OUTPUT_FORMAT_TABLE.put("mp4", MediaRecorder.OutputFormat.MPEG_4);
        OUTPUT_FORMAT_TABLE.putDefault(MediaRecorder.OutputFormat.DEFAULT);

        VIDEO_ENCODER_TABLE.put("h263", MediaRecorder.VideoEncoder.H263);
        VIDEO_ENCODER_TABLE.put("h264", MediaRecorder.VideoEncoder.H264);
        int h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                       "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT);
        if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
            h265 = ApiHelper.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                       "H265", null, MediaRecorder.VideoEncoder.DEFAULT);
        }
        VIDEO_ENCODER_TABLE.put("h265", h265);
        VIDEO_ENCODER_TABLE.put("m4v", MediaRecorder.VideoEncoder.MPEG_4_SP);
        VIDEO_ENCODER_TABLE.putDefault(MediaRecorder.VideoEncoder.DEFAULT);

        AUDIO_ENCODER_TABLE.put("amrnb", MediaRecorder.AudioEncoder.AMR_NB);
        // Enabled once support is added in MediaRecorder.
        // AUDIO_ENCODER_TABLE.put("qcelp", MediaRecorder.AudioEncoder.QCELP);
        // AUDIO_ENCODER_TABLE.put("evrc", MediaRecorder.AudioEncoder.EVRC);
        AUDIO_ENCODER_TABLE.put("amrwb", MediaRecorder.AudioEncoder.AMR_WB);
        AUDIO_ENCODER_TABLE.put("aac", MediaRecorder.AudioEncoder.AAC);
        AUDIO_ENCODER_TABLE.putDefault(MediaRecorder.AudioEncoder.DEFAULT);

    }

    private int mVideoEncoder;
    private int mAudioEncoder;
    private boolean mRestartPreview = false;
    private int videoWidth;
    private int videoHeight;
    boolean mUnsupportedResolution = false;
    private boolean mUnsupportedHFRVideoSize = false;
    private boolean mUnsupportedHSRVideoSize = false;
    private boolean mUnsupportedHFRVideoCodec = false;
    private String mDefaultAntibanding = null;
    boolean mUnsupportedProfile = false;

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case ENABLE_SHUTTER_BUTTON:
                    mUI.enableShutter(true);
                    break;

                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Restart the preview if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if ((CameraUtil.getDisplayRotation(mActivity) != mDisplayRotation)
                            && !mMediaRecorderRecording && !mSwitchingCamera) {
                        startPreview();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case SHOW_TAP_TO_SNAPSHOT_TOAST: {
                    showTapToSnapshotToast();
                    break;
                }

                case SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case SWITCH_CAMERA_START_ANIMATION: {
                    //TODO:
                    //((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();

                    // Enable all camera controls.
                    mSwitchingCamera = false;
                    break;
                }

                case HANDLE_FLASH_TORCH_DELAY: {
                    forceFlashOff(!mPreviewFocused);
                    break;
                }

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT) ||
                    action.equals(Intent.ACTION_SCREEN_OFF)) {
                stopVideoRecording();
                RotateTextToast.makeText(mActivity,
                        mActivity.getResources().getString(R.string.video_recording_stopped),
                                Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));

        return dateFormat.format(date);
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

    private void initializeSurfaceView() {
        if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {  // API level < 16
            mUI.initializeSurfaceView();
        }
    }

    public void reinit() {
        mPreferences = ComboPreferences.get(mActivity);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mActivity);
        }

        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), mActivity);
        mCameraId = getPreferredCameraId(mPreferences);
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
    }

    @Override
    public void init(CameraActivity activity, View root) {
        mActivity = activity;
        mUI = new VideoUI(activity, this, root);
        mPreferences = ComboPreferences.get(mActivity);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mActivity);
        }

        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), activity);
        mCameraId = getPreferredCameraId(mPreferences);

        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mOrientationManager = new OrientationManager(mActivity);

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        CameraOpenThread cameraOpenThread = new CameraOpenThread();
        cameraOpenThread.start();

        mContentResolver = mActivity.getContentResolver();

        Storage.setSaveSDCard(
            mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));
        mSaveToSDCard = Storage.isSaveSDCard();
        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsVideoCaptureIntent = isVideoCaptureIntent();
        initializeSurfaceView();

        // Make sure camera device is opened.
        try {
            cameraOpenThread.join();
            if (mCameraDevice == null) {
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        readVideoPreferences();
        mUI.setPrefChangedListener(this);

        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = new LocationManager(mActivity, this);

        mUI.setOrientationIndicator(0, false);
        setDisplayOrientation();

        mUI.showTimeLapseUI(mCaptureTimeLapse);
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();

        initializeVideoControl();
        mPendingSwitchCameraId = -1;
    }

    @Override
    public void waitingLocationPermissionResult(boolean result) {
        mLocationManager.waitingLocationPermissionResult(result);
    }

    @Override
    public void enableRecordingLocation(boolean enable) {
        String value = (enable ? RecordLocationPreference.VALUE_ON
                        : RecordLocationPreference.VALUE_OFF);
        if (mPreferences != null) {
            mPreferences.edit()
                .putString(CameraSettings.KEY_RECORD_LOCATION, value)
                .apply();
        }
        mLocationManager.recordLocation(enable);
     }

    @Override
    public void setPreferenceForTest(String key, String value) {
        mUI.setPreference(key, value);
        onSharedPreferenceChanged();
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mMediaRecorderPausing) return;
        takeASnapshot();
    }

    private void takeASnapshot() {
        // Only take snapshots if video snapshot is supported by device
        if (CameraUtil.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            if (!mMediaRecorderRecording || mPaused || mSnapshotInProgress) {
                return;
            }
            MediaSaveService s = mActivity.getMediaSaveService();
            if (s == null || s.isQueueFull()) {
                return;
            }

            // Set rotation and gps data.
            int rotation = CameraUtil.getJpegRotation(mCameraId, mOrientation);
            mParameters.setRotation(rotation);
            Location loc = mLocationManager.getCurrentLocation();
            CameraUtil.setGpsParameters(mParameters, loc);
            mCameraDevice.setParameters(mParameters);

            Log.v(TAG, "Video snapshot start");
            mCameraDevice.takePicture(mHandler,
                    null, null, null, new JpegPictureCallback(loc));
            showVideoSnapshotUI(true);
            mSnapshotInProgress = true;
            UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                    UsageStatistics.ACTION_CAPTURE_DONE, "VideoSnapshot");
        }
    }

    @Override
    public void onStop() {}

    @Override
    public void onDestroy() {}

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(mActivity, mParameters,
                mCameraId, CameraHolder.instance().getCameraInfo());
        // Remove the video quality preference setting when the quality is given in the intent.
        mPreferenceGroup = filterPreferenceScreenByIntent(
                settings.getPreferenceGroup(R.xml.video_preferences));

        int numOfCams = Camera.getNumberOfCameras();

        //TODO: If numOfCams > 2 then corresponding entries needs to be added to the media_profiles.xml

        Log.e(TAG,"loadCameraPreferences() updating camera_id pref");

        IconListPreference switchIconPref =
                (IconListPreference)mPreferenceGroup.findPreference(
                CameraSettings.KEY_CAMERA_ID);

        //if numOfCams < 2 then switchIconPref will be null as there is no switch icon in this case
        if (switchIconPref == null)
            return;

        int[] iconIds = new int[numOfCams];
        String[] entries = new String[numOfCams];
        String[] labels = new String[numOfCams];
        int[] largeIconIds = new int[numOfCams];

        for(int i=0;i<numOfCams;i++) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[i];
            if(info.facing == CameraInfo.CAMERA_FACING_BACK) {
                iconIds[i] = R.drawable.ic_switch_back;
                entries[i] = mActivity.getResources().getString(R.string.pref_camera_id_entry_back);
                labels[i] = mActivity.getResources().getString(R.string.pref_camera_id_label_back);
                largeIconIds[i] = R.drawable.ic_switch_back;
            } else {
                iconIds[i] = R.drawable.ic_switch_front;
                entries[i] = mActivity.getResources().getString(R.string.pref_camera_id_entry_front);
                labels[i] = mActivity.getResources().getString(R.string.pref_camera_id_label_front);
                largeIconIds[i] = R.drawable.ic_switch_front;
            }
        }

        switchIconPref.setIconIds(iconIds);
        switchIconPref.setEntries(entries);
        switchIconPref.setLabels(labels);
        switchIconPref.setLargeIconIds(largeIconIds);

    }

    private void initializeVideoControl() {
        loadCameraPreferences();
        mUI.initializePopup(mPreferenceGroup);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int newOrientation = CameraUtil.roundOrientation(orientation, mOrientation);

        if (mOrientation != newOrientation) {
            mOrientation = newOrientation;
            Log.v(TAG, "onOrientationChanged, update parameters");
            if ((mCameraDevice != null) && (mParameters != null)
                    && (true == mPreviewing) && !mMediaRecorderRecording){
                setFlipValue();
                updatePowerMode();
                mCameraDevice.setParameters(mParameters);
            }
            mUI.tryToCloseSubList();
            mUI.setOrientation(newOrientation, true);
        }

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(SHOW_TAP_TO_SNAPSHOT_TOAST)) {
            mHandler.removeMessages(SHOW_TAP_TO_SNAPSHOT_TOAST);
            showTapToSnapshotToast();
        }
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity
                    .startActivityForResult(intent, CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    @Override
    @OnClickAttr
    public void onReviewPlayClicked(View v) {
        startPlayVideoActivity();
    }

    @Override
    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        mIsInReviewMode = false;
        doReturnToCaller(true);
    }

    @Override
    @OnClickAttr
    public void onReviewCancelClicked(View v) {
        // TODO: It should be better to not even insert the URI at all before we
        // confirm done in review, which means we need to handle temporary video
        // files in a quite different way than we currently had.
        // Make sure we don't delete the Uri sent from the video capture intent.
        if (mCurrentVideoUriFromMediaSaved) {
            mContentResolver.delete(mCurrentVideoUri, null, null);
        }
        mIsInReviewMode = false;
        doReturnToCaller(false);
    }

    @Override
    public boolean isInReviewMode() {
        return mIsInReviewMode;
    }

    private void onStopVideoRecording() {
        boolean recordFail = stopVideoRecording();
        if (mIsVideoCaptureIntent) {
            if (mQuickCapture) {
                doReturnToCaller(!recordFail);
            } else if (!recordFail) {
                showCaptureResult();
            }
        } else if (!recordFail){
            // Start capture animation.
            if (!mPaused && ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                // The capture animation is disabled on ICS because we use SurfaceView
                // for preview during recording. When the recording is done, we switch
                // back to use SurfaceTexture for preview and we need to stop then start
                // the preview. This will cause the preview flicker since the preview
                // will not be continuous for a short period of time.

                mUI.animateFlash();
                mUI.animateCapture();
            }
        }
        mUI.showUIafterRecording();
    }

    public void onVideoSaved() {
        if (mIsVideoCaptureIntent) {
            showCaptureResult();
        }
    }

    public void onProtectiveCurtainClick(View v) {
        // Consume clicks
    }

    public boolean isPreviewReady() {
        if ((mStartPrevPending == true || mStopPrevPending == true))
            return false;
        else
            return true;
    }

    public boolean isRecorderReady() {
        if ((mStartRecPending == true || mStopRecPending == true))
            return false;
        else
            return true;
    }

    @Override
    public void onShutterButtonClick() {
        if (mPaused || mUI.collapseCameraControls() ||
                mSwitchingCamera) return;

        boolean stop = mMediaRecorderRecording;

        if (isPreviewReady() == false)
            return;

        if (isRecorderReady() == false)
            return;

        mUI.enableShutter(false);

        if (stop) {
            onStopVideoRecording();
        } else {
            if (!startVideoRecording()) {
                // Show ui when start recording failed.
                mUI.showUIafterRecording();
            }
        }

        // Keep the shutter button disabled when in video capture intent
        // mode and recording is stopped. It'll be re-enabled when
        // re-take button is clicked.
        if (!(mIsVideoCaptureIntent && stop)) {
            mHandler.sendEmptyMessageDelayed(
                    ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        mUI.setShutterPressed(pressed);
    }

    @Override
    public void onShutterButtonLongClick() {}

    private void qcomReadVideoPreferences() {
        String videoEncoder = mPreferences.getString(
               CameraSettings.KEY_VIDEO_ENCODER,
               mActivity.getString(R.string.pref_camera_videoencoder_default));
        mVideoEncoder = VIDEO_ENCODER_TABLE.get(videoEncoder);

        Log.v(TAG, "Video Encoder selected = " +mVideoEncoder);

        String audioEncoder = mPreferences.getString(
               CameraSettings.KEY_AUDIO_ENCODER,
               mActivity.getString(R.string.pref_camera_audioencoder_default));
        mAudioEncoder = AUDIO_ENCODER_TABLE.get(audioEncoder);

        Log.v(TAG, "Audio Encoder selected = " +mAudioEncoder);

        String minutesStr = mPreferences.getString(
              CameraSettings.KEY_VIDEO_DURATION,
              mActivity.getString(R.string.pref_camera_video_duration_default));
        int minutes = -1;
        try {
            minutes = Integer.parseInt(minutesStr);
        } catch(NumberFormatException npe) {
            // use default value continue
            minutes = Integer.parseInt(mActivity.getString(
                         R.string.pref_camera_video_duration_default));
        }
        if (minutes == -1) {
            // User wants lowest, set 30s */
            mMaxVideoDurationInMs = 30000;
        } else {
            // 1 minute = 60000ms
            mMaxVideoDurationInMs = 60000 * minutes;
        }

        if(mParameters.isPowerModeSupported()) {
            String powermode = mPreferences.getString(
                    CameraSettings.KEY_POWER_MODE,
                    mActivity.getString(R.string.pref_camera_powermode_default));
            Log.v(TAG, "read videopreferences power mode =" +powermode);
            String old_mode = mParameters.getPowerMode();
            if(!old_mode.equals(powermode) && mPreviewing)
                mRestartPreview = true;

            mParameters.setPowerMode(powermode);
        }

        // Set wavelet denoise mode
        if (mParameters.getSupportedDenoiseModes() != null) {
            String denoise = mPreferences.getString(CameraSettings.KEY_DENOISE,
                    mActivity.getString(R.string.pref_camera_denoise_default));
            mParameters.setDenoise(denoise);
        }
   }

    private void readVideoPreferences() {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        String videoQuality = mPreferences.getString(CameraSettings.KEY_VIDEO_QUALITY,
                        null);
        if (videoQuality == null) {
            mParameters = mCameraDevice.getParameters();
            String defaultQuality = mActivity.getResources().getString(
                    R.string.pref_video_quality_default);
            if (!defaultQuality.equals("") &&
                    CameraUtil.isSupported(defaultQuality,
                            CameraSettings.getSupportedVideoQualities(
                                                    mCameraId, mParameters))){
                videoQuality = defaultQuality;
            } else {
                // check for highest quality supported
                videoQuality = CameraSettings.getSupportedHighestVideoQuality(
                        mCameraId, mParameters);
            }
            mPreferences.edit().putString(CameraSettings.KEY_VIDEO_QUALITY, videoQuality).apply();
        }
        int quality = CameraSettings.VIDEO_QUALITY_TABLE.get(videoQuality);

        // Set video quality.
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                quality = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                quality = CamcorderProfile.QUALITY_LOW;
            }
        }

        // Read time lapse recording interval.
        String frameIntervalStr = mPreferences.getString(
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                mActivity.getString(R.string.pref_video_time_lapse_frame_interval_default));
        mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);
        mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);

        int hfrRate = 0;
        String highFrameRate = mPreferences.getString(
            CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
            mActivity. getString(R.string.pref_camera_hfr_default));
        if (("hfr".equals(highFrameRate.substring(0,3))) ||
                ("hsr".equals(highFrameRate.substring(0,3)))) {
            String rate = highFrameRate.substring(3);
            Log.i(TAG,"HFR :"  + highFrameRate + " : rate = " + rate);
            try {
                hfrRate = Integer.parseInt(rate);
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Invalid hfr rate " + rate);
            }
        }

        int mappedQuality = quality;
        if (mCaptureTimeLapse) {
            mappedQuality = CameraSettings.getTimeLapseQualityFor(quality);
        } else if (hfrRate > 0) {
            mappedQuality = CameraSettings.getHighSpeedQualityFor(quality);
            Log.i(TAG,"NOTE: HighSpeed quality (" + mappedQuality + ") for (" + quality + ")");
        }

        if (CamcorderProfile.hasProfile(mCameraId, mappedQuality)) {
            quality = mappedQuality;
        } else {
            Log.e(TAG,"NOTE: Quality " + mappedQuality + " is not supported ! Will use " + quality);
        }
        mProfile = CamcorderProfile.get(mCameraId, quality);
        getDesiredPreviewSize();
        qcomReadVideoPreferences();

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
                    intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        }
        mPreferenceRead = true;
    }

    public boolean is4KEnabled() {
       if (mProfile.quality == CamcorderProfile.QUALITY_2160P ||
           mProfile.quality == CamcorderProfile.QUALITY_TIME_LAPSE_2160P ||
           mProfile.quality == CamcorderProfile.QUALITY_4KDCI ) {
           return true;
       } else {
           return false;
       }
    }

    private boolean is1080pEnabled() {
       if (mProfile.quality == CamcorderProfile.QUALITY_1080P) {
           return true;
       } else {
           return false;
       }
    }

    private boolean is720pEnabled() {
       if (mProfile.quality == CamcorderProfile.QUALITY_720P) {
           return true;
       } else {
           return false;
       }
    }

    private boolean isSessionSupportedByEncoder(int w, int h, int fps) {
        int expectedMBsPerSec = w * h * fps;

        List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (VideoEncoderCap videoEncoder: videoEncoders) {
            if (videoEncoder.mCodec == mVideoEncoder) {
                int maxMBsPerSec = (videoEncoder.mMaxFrameWidth * videoEncoder.mMaxFrameHeight
                        * videoEncoder.mMaxFrameRate);
                if (expectedMBsPerSec > maxMBsPerSec) {
                    Log.e(TAG,"Selected codec " + mVideoEncoder
                            + " does not support width(" + w
                            + ") X height ("+ h
                            + "@ " + fps +" fps");
                    Log.e(TAG, "Max capabilities: " +
                            "MaxFrameWidth = " + videoEncoder.mMaxFrameWidth + " , " +
                            "MaxFrameHeight = " + videoEncoder.mMaxFrameHeight + " , " +
                            "MaxFrameRate = " + videoEncoder.mMaxFrameRate);
                    return false;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isHFREnabled(int videoWidth, int videoHeight) {
        if ((null == mPreferences) || (null == mParameters)) {
            return false;
        }

        String HighFrameRate = mPreferences.getString(
                CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
                mActivity. getString(R.string.pref_camera_hfr_default));

        if(!("off".equals(HighFrameRate))) {
            Size size = null;
            try {
                if (isSupported(HighFrameRate.substring(3), mParameters.getSupportedVideoHighFrameRateModes())) {
                    int index = mParameters.getSupportedVideoHighFrameRateModes().indexOf(
                            HighFrameRate.substring(3));
                    size = mParameters.getSupportedHfrSizes().get(index);
                } else {
                    return false;
                }
            } catch (NullPointerException e) {
                return false;
            } catch (IndexOutOfBoundsException e) {
                return false;
            }

            if (size != null) {
                if (videoWidth > size.width || videoHeight > size.height) {
                    return false;
                }
            } else {
                return false;
            }

            int hfrFps = Integer.parseInt(HighFrameRate.substring(3));
            return isSessionSupportedByEncoder(videoWidth, videoHeight, hfrFps);
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void getDesiredPreviewSize() {
        if (mCameraDevice == null) {
            return;
        }
        mParameters = mCameraDevice.getParameters();
        if (mParameters.getSupportedVideoSizes() == null ||
                isHFREnabled(mProfile.videoFrameWidth, mProfile.videoFrameHeight)) {
            mDesiredPreviewWidth = mProfile.videoFrameWidth;
            mDesiredPreviewHeight = mProfile.videoFrameHeight;
        } else { // Driver supports separates outputs for preview and video.
            List<Size> sizes = mParameters.getSupportedPreviewSizes();
            Size preferred = mParameters.getPreferredPreviewSizeForVideo();
            int product = preferred.width * preferred.height;
            Iterator<Size> it = sizes.iterator();
            // Remove the preview sizes that are not preferred.
            while (it.hasNext()) {
                Size size = it.next();
                if (size.width * size.height > product) {
                    it.remove();
                }
            }
            Size optimalSize = CameraUtil.getOptimalPreviewSize(mActivity, sizes,
                    (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
            mDesiredPreviewWidth = optimalSize.width;
            mDesiredPreviewHeight = optimalSize.height;
        }
        mUI.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        Log.v(TAG, "mDesiredPreviewWidth=" + mDesiredPreviewWidth +
                ". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    void setPreviewFrameLayoutCameraOrientation(){
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];

        //if camera mount angle is 0 or 180, we want to resize preview
        if (info.orientation % 180 == 0)
            mUI.cameraOrientationPreviewResize(true);
        else
            mUI.cameraOrientationPreviewResize(false);
    }

    @Override
    public void resizeForPreviewAspectRatio() {
        setPreviewFrameLayoutCameraOrientation();
        mUI.setAspectRatio(
                (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
    }

    @Override
    public void onSwitchSavePath() {
        mUI.setPreference(CameraSettings.KEY_CAMERA_SAVEPATH, "1");
        RotateTextToast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void installIntentFilter() {
        if(mReceiver != null)
            return;
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        mActivity.registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        mUI.enableShutter(false);
        mZoomValue = 0;
        mUI.showSurfaceView();
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        mWasMute = am.isMicrophoneMute();
        if(mWasMute != mIsMute) {
            setMute(mIsMute, false);
        }
        initializeVideoControl();
        showVideoSnapshotUI(false);
        installIntentFilter();

        if (!mPreviewing) {
            openCamera();
            if (mCameraDevice == null) {
                return;
            }
            readVideoPreferences();
            resizeForPreviewAspectRatio();
            startPreview();
        } else {
            // preview already started
            mUI.enableShutter(true);
        }

        mUI.applySurfaceChange(VideoUI.SURFACE_STATUS.SURFACE_VIEW);

        mUI.initDisplayChangeListener();
        // Initializing it here after the preview is started.
        mUI.initializeZoom(mParameters);
        mUI.setPreviewGesturesVideoUI();
        mUI.setSwitcherIndex();
        keepScreenOnAwhile();

        mOrientationManager.resume();
        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                CameraSettings.KEY_RECORD_LOCATION);
        mLocationManager.recordLocation(recordLocation);

        if (mPreviewing) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
        }

        UsageStatistics.onContentViewChanged(
                UsageStatistics.COMPONENT_CAMERA, "VideoModule");
        mHandler.post(new Runnable(){
            @Override
            public void run(){
                mActivity.updateStorageSpaceAndHint();
            }
        });
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mCameraDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId);
        mUI.setDisplayOrientation(mCameraDisplayOrientation);
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    @Override
    public void updateCameraOrientation() {
        if (mMediaRecorderRecording) return;
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
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

    private void startPreview() {
        Log.v(TAG, "startPreview");
        mStartPrevPending = true;

        SurfaceHolder sh = null;
        Log.v(TAG, "startPreview: SurfaceHolder (MDP path)");
        sh = mUI.getSurfaceHolder();

        if (!mPreferenceRead || mPaused == true || mCameraDevice == null) {
            mStartPrevPending = false;
            return;
        }
        mErrorCallback.setActivity(mActivity);
        mCameraDevice.setErrorCallback(mErrorCallback);
        if (mPreviewing == true) {
            stopPreview();
        }

        setDisplayOrientation();
        mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        setCameraParameters(true);

        try {
            mCameraDevice.setPreviewDisplay(sh);
            mCameraDevice.setOneShotPreviewCallback(mHandler,
                new CameraManager.CameraPreviewDataCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, CameraProxy camera) {
                        mUI.hidePreviewCover();
                    }
                });
            mCameraDevice.startPreview();
            mPreviewing = true;
            onPreviewStarted();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
        mStartPrevPending = false;
    }

    private void onPreviewStarted() {
        mUI.enableShutter(true);
        startFaceDetection();
    }

    @Override
    public void stopPreview() {
        mStopPrevPending = true;

        if (!mPreviewing) {
            mStopPrevPending = false;
            return;
        }
        mCameraDevice.stopPreview();
        mPreviewing = false;
        mStopPrevPending = false;
        mUI.enableShutter(false);
        stopFaceDetection();
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        mCameraDevice.setZoomChangeListener(null);
        mCameraDevice.setErrorCallback(null);
        mCameraDevice.setFaceDetectionCallback(null, null);
        if (mActivity.isForceReleaseCamera()) {
            CameraHolder.instance().strongRelease();
        } else {
            CameraHolder.instance().release();
        }
        mCameraDevice = null;
        mPreviewing = false;
        mSnapshotInProgress = false;
        mPreviewFocused = false;
        mFaceDetectionStarted = false;
    }

    private void releasePreviewResources() {
        if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
            mUI.hideSurfaceView();
        }
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;

        mUI.showPreviewCover();
        mUI.hideSurfaceView();
        if (mMediaRecorderRecording) {
            // Camera will be released in onStopVideoRecording.
            onStopVideoRecording();
        } else {
            closeCamera();
            releaseMediaRecorder();
        }

        closeVideoFileDescriptor();


        releasePreviewResources();

        if (mReceiver != null) {
            mActivity.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

        if (mLocationManager != null) mLocationManager.recordLocation(false);
        mOrientationManager.pause();

        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mHandler.removeMessages(SWITCH_CAMERA);
        mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
        mHandler.removeMessages(HANDLE_FLASH_TORCH_DELAY);
        mPendingSwitchCameraId = -1;
        mSwitchingCamera = false;
        mPreferenceRead = false;

        mUI.collapseCameraControls();
        mUI.removeDisplayChangeListener();

        if(mWasMute != mIsMute) {
            setMute(mWasMute, false);
        }
        mUI.applySurfaceChange(VideoUI.SURFACE_STATUS.HIDE);
    }

    @Override
    public void onPauseAfterSuper() {
    }

    @Override
    public void onUserInteraction() {
        if (!mMediaRecorderRecording && !mActivity.isFinishing()) {
            keepScreenOnAwhile();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mPaused) return true;
        if (mMediaRecorderRecording) {
            onStopVideoRecording();
            return true;
        } else if (mUI.hideSwitcherPopup()) {
            return true;
        } else {
            return mUI.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mPaused) {
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    mUI.clickShutter();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    mUI.clickShutter();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                if (mMediaRecorderRecording) return true;
                break;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                mUI.pressShutter(false);
                return true;
        }
        return false;
    }

    @Override
    public boolean isVideoCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = Activity.RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
            resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        mActivity.setResultEx(resultCode, resultIntent);
        mActivity.finish();
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    private void setupMediaRecorderPreviewDisplay() {
        // Nothing to do here if using SurfaceTexture.
        if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
            // We stop the preview here before unlocking the device because we
            // need to change the SurfaceTexture to SurfaceView for preview.
            stopPreview();
            mCameraDevice.setPreviewDisplay(mUI.getSurfaceHolder());
            // The orientation for SurfaceTexture is different from that for
            // SurfaceView. For SurfaceTexture we don't need to consider the
            // display rotation. Just consider the sensor's orientation and we
            // will set the orientation correctly when showing the texture.
            // Gallery will handle the orientation for the preview. For
            // SurfaceView we will have to take everything into account so the
            // display rotation is considered.
            mCameraDevice.setDisplayOrientation(
                    CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId));
            mCameraDevice.startPreview();
            mPreviewing = true;
            mMediaRecorder.setPreviewDisplay(mUI.getSurfaceHolder().getSurface());
        }
    }
    private int getHighSpeedVideoEncoderBitRate(CamcorderProfile profile, int targetRate) {
        int bitRate;
        String key = profile.videoFrameWidth+"x"+profile.videoFrameHeight+":"+targetRate;
        if (CameraSettings.VIDEO_ENCODER_BITRATE.containsKey(key)) {
            bitRate = CameraSettings.VIDEO_ENCODER_BITRATE.get(key);
        } else {
            Log.i(TAG, "No pre-defined bitrate for "+key);
            bitRate = profile.videoBitRate * (targetRate / profile.videoFrameRate);
        }
        return bitRate;
    }

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
            // Set the SurfaceView to visible so the surface gets created.
            // surfaceCreated() is called immediately when the visibility is
            // changed to visible. Thus, mSurfaceViewReady should become true
            // right after calling setVisibility().
            mUI.showSurfaceView();
        }

        Intent intent = mActivity.getIntent();
        Bundle myExtras = intent.getExtras();

        videoWidth = mProfile.videoFrameWidth;
        videoHeight = mProfile.videoFrameHeight;
        mUnsupportedResolution = false;

        //check if codec supports the resolution, otherwise throw toast
        List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (VideoEncoderCap videoEncoder: videoEncoders) {
            if (videoEncoder.mCodec == mVideoEncoder) {
                if (videoWidth > videoEncoder.mMaxFrameWidth ||
                        videoWidth < videoEncoder.mMinFrameWidth ||
                        videoHeight > videoEncoder.mMaxFrameHeight ||
                        videoHeight < videoEncoder.mMinFrameHeight) {
                    Log.e(TAG, "Selected codec " + mVideoEncoder +
                            " does not support "+ videoWidth + "x" + videoHeight
                            + " resolution");
                    Log.e(TAG, "Codec capabilities: " +
                            "mMinFrameWidth = " + videoEncoder.mMinFrameWidth + " , " +
                            "mMinFrameHeight = " + videoEncoder.mMinFrameHeight + " , " +
                            "mMaxFrameWidth = " + videoEncoder.mMaxFrameWidth + " , " +
                            "mMaxFrameHeight = " + videoEncoder.mMaxFrameHeight);
                    mUnsupportedResolution = true;
                    RotateTextToast.makeText(mActivity, R.string.error_app_unsupported,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            }
        }

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        mCurrentVideoUriFromMediaSaved = false;
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();

        // Unlock the camera object before passing it to media recorder.
        mCameraDevice.unlock();
        mMediaRecorder.setCamera(mCameraDevice.getCamera());

        String hfr = mParameters.getVideoHighFrameRate();
        String hsr =  mParameters.get(CameraSettings.KEY_VIDEO_HSR);
        Log.i(TAG,"NOTE: hfr = " + hfr + " : hsr = " + hsr);

        int captureRate = 0;
        boolean isHFR = (hfr != null && !hfr.equals("off"));
        boolean isHSR = (hsr != null && !hsr.equals("off"));

        try {
            captureRate = isHFR ? Integer.parseInt(hfr) :
                    isHSR ? Integer.parseInt(hsr) : 0;
        } catch (NumberFormatException nfe) {
            Log.e(TAG, "Invalid hfr(" + hfr + ") or hsr(" + hsr + ")");
        }

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mProfile.videoCodec = mVideoEncoder;
        mProfile.audioCodec = mAudioEncoder;
        mProfile.duration = mMaxVideoDurationInMs;

        if ((mProfile.audioCodec == MediaRecorder.AudioEncoder.AMR_NB) &&
            !mCaptureTimeLapse && !isHFR) {
            mProfile.fileFormat = MediaRecorder.OutputFormat.THREE_GPP;
        }
        // Set params individually for HFR case, as we do not want to encode audio
        if ((isHFR || isHSR) && captureRate > 0) {
            if (isHSR) {
                Log.i(TAG, "Enabling audio for HSR");
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            }
            mMediaRecorder.setOutputFormat(mProfile.fileFormat);
            mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
            mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
            mMediaRecorder.setVideoEncoder(mProfile.videoCodec);
            if (isHSR) {
                Log.i(TAG, "Configuring audio for HSR");
                mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
                mMediaRecorder.setAudioChannels(mProfile.audioChannels);
                mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
                mMediaRecorder.setAudioEncoder(mProfile.audioCodec);
            }
        } else {
            if (!mCaptureTimeLapse) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            }

            mMediaRecorder.setProfile(mProfile);
        }

        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
            setCaptureRate(mMediaRecorder, fps);
        } else if (captureRate > 0) {
            Log.i(TAG, "Setting capture-rate = " + captureRate);
            mMediaRecorder.setCaptureRate(captureRate);

            // for HSR, encoder's target-framerate = capture-rate
            // for HFR, encoder's taget-framerate = 30fps (from profile)
            int targetFrameRate = isHSR ? captureRate :
                    isHFR ? 30 : mProfile.videoFrameRate;

            Log.i(TAG, "Setting target fps = " + targetFrameRate);
            mMediaRecorder.setVideoFrameRate(targetFrameRate);

            // Profiles advertizes bitrate corresponding to published framerate.
            // In case framerate is different, scale the bitrate
            int scaledBitrate = getHighSpeedVideoEncoderBitRate(mProfile, targetFrameRate);
            Log.i(TAG, "Scaled Video bitrate : " + scaledBitrate);
            mMediaRecorder.setVideoEncodingBitRate(scaledBitrate);
        }

        setRecordLocation();

        // Set output file.
        // Try Uri in the intent first. If it doesn't exist, use our own
        // instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpaceBytes() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        if (Storage.isSaveSDCard() && maxFileSize > SDCARD_SIZE_LIMIT) {
            maxFileSize = SDCARD_SIZE_LIMIT;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        // Note that mOrientation here is the device orientation, which is the opposite of
        // what activity.getWindowManager().getDefaultDisplay().getRotation() would return,
        // which is the orientation the graphics need to rotate in order to render correctly.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
        mMediaRecorder.setOrientationHint(rotation);
        setupMediaRecorderPreviewDisplay();

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    private static void setCaptureRate(MediaRecorder recorder, double fps) {
        recorder.setCaptureRate(fps);
    }

    private void setRecordLocation() {
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
                    (float) loc.getLongitude());
        }
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        mVideoFilename = null;
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);
        String path = null;
        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            path = SDCard.instance().getDirectory() + '/' + filename;
        } else {
            path = Storage.DIRECTORY + '/' + filename;
        }
        mCurrentVideoValues = new ContentValues(9);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, path);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
                Integer.toString(mProfile.videoFrameWidth) + "x" +
                Integer.toString(mProfile.videoFrameHeight));
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues.put(Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues.put(Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoFilename = path;
        Log.v(TAG, "New video filename: " + mVideoFilename);
    }

    private void saveVideo() {
        if (mVideoFileDescriptor == null) {
            //use the recording stop timestamp to generate the video's file name.
            String videoSourcePath = mVideoFilename;
            generateVideoFilename(mProfile.fileFormat);
            mCurrentVideoFilename = mVideoFilename;
            File sourceFile = new File(videoSourcePath);
            sourceFile.renameTo(new File(mCurrentVideoFilename));
            File origFile = new File(mCurrentVideoFilename);
            if (!origFile.exists() || origFile.length() <= 0) {
                Log.e(TAG, "Invalid file");
                mCurrentVideoValues = null;
                return;
            }

            long duration = 0L;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(mCurrentVideoFilename);
                duration = Long.valueOf(retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "cannot access the file");
            }
            retriever.release();

            mActivity.getMediaSaveService().addVideo(mCurrentVideoFilename,
                    duration, mCurrentVideoValues,
                    mOnVideoSavedListener, mContentResolver);
        }
        mCurrentVideoValues = null;
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private PreferenceGroup filterPreferenceScreenByIntent(
            PreferenceGroup screen) {
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }

        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }
        return screen;
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        stopVideoRecording();
        mUI.showUIafterRecording();
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            mActivity.updateStorageSpaceAndHint();
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording();
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording();

            // Show the toast.
            RotateTextToast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void requestAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);

        // Send request to obtain audio focus. This will stop other
        // music stream.
        int result = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                                 AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus request failed");
        }
    }

    private void releaseAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);

        int result = am.abandonAudioFocus(null);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus release failed");
        }
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private boolean startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        mStartRecPending = true;
        mUI.cancelAnimations();
        mUI.setSwipingEnabled(false);
        mUI.hideUIwhileRecording();
        // When recording request is sent before starting preview, onPreviewFrame()
        // callback doesn't happen so removing preview cover here, instead.
        if (mUI.isPreviewCoverVisible()) {
            mUI.hidePreviewCover();
        }
        mActivity.updateStorageSpaceAndHint();
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.v(TAG, "Storage issue, ignore the start request");
            mStartRecPending = false;
            return false;
        }

        if( mUnsupportedHFRVideoSize == true) {
            Log.e(TAG, "Unsupported HFR and video size combinations");
            RotateTextToast.makeText(mActivity,R.string.error_app_unsupported_hfr,
                    Toast.LENGTH_SHORT).show();
            mStartRecPending = false;
            return false;
        }

        if (mUnsupportedHSRVideoSize == true) {
            Log.e(TAG, "Unsupported HSR and video size combinations");
            RotateTextToast.makeText(mActivity,R.string.error_app_unsupported_hsr,
                    Toast.LENGTH_SHORT).show();
            mStartRecPending = false;
            return false;
        }

        if( mUnsupportedHFRVideoCodec == true) {
            Log.e(TAG, "Unsupported HFR and video codec combinations");
            RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_hfr_codec,
                    Toast.LENGTH_SHORT).show();
            mStartRecPending = false;
            return false;
        }
        if (mUnsupportedProfile == true) {
            Log.e(TAG, "Unsupported video profile");
            RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_profile,
                    Toast.LENGTH_SHORT).show();
            mStartRecPending = false;
            return false;
        }
        //??
        //if (!mCameraDevice.waitDone()) return;
        mCurrentVideoUri = null;

        initializeRecorder();
        if (mUnsupportedResolution == true) {
              Log.v(TAG, "Unsupported Resolution according to target");
              mStartRecPending = false;
              return false;
        }
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder");
            mStartRecPending = false;
            return false;
        }

        requestAudioFocus();

        try {
            mMediaRecorder.start(); // Recording is now started
        } catch (RuntimeException e) {
            Toast.makeText(mActivity,"Could not start media recorder.\n Can't start video recording.", Toast.LENGTH_LONG).show();
            releaseMediaRecorder();
            releaseAudioFocus();
            // If start fails, frameworks will not lock the camera for us.
            mCameraDevice.lock();
            mStartRecPending = false;
            return false;
        }

        // Make sure the video recording has started before announcing
        // this in accessibility.
        AccessibilityUtils.makeAnnouncement(mUI.getShutterButton(),
                mActivity.getString(R.string.video_recording_started));

        // The parameters might have been altered by MediaRecorder already.
        // We need to force mCameraDevice to refresh before getting it.
        mCameraDevice.refreshParameters();
        // The parameters may have been changed by MediaRecorder upon starting
        // recording. We need to alter the parameters if we support camcorder
        // zoom. To reduce latency when setting the parameters during zoom, we
        // update mParameters here once.
        mParameters = mCameraDevice.getParameters();

        mUI.enableCameraControls(false);

        mMediaRecorderRecording = true;
        mMediaRecorderPausing = false;
        mUI.resetPauseButton();
        mRecordingTotalTime = 0L;
        mRecordingStartTime = SystemClock.uptimeMillis();
        mUI.showRecordingUI(true);

        updateRecordingTime();
        keepScreenOn();
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                UsageStatistics.ACTION_CAPTURE_START, "Video");
        mStartRecPending = false;
        return true;
    }

    private Bitmap getVideoThumbnail() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mVideoFileDescriptor.getFileDescriptor(),
                    mDesiredPreviewWidth);
        } else if (mCurrentVideoUri != null) {
            try {
                mVideoFileDescriptor = mContentResolver.openFileDescriptor(mCurrentVideoUri, "r");
                bitmap = Thumbnail.createVideoThumbnailBitmap(
                        mVideoFileDescriptor.getFileDescriptor(), mDesiredPreviewWidth);
            } catch (java.io.FileNotFoundException ex) {
                // invalid uri
                Log.e(TAG, ex.toString());
            }
        }

        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            boolean mirror = (info[mCameraId].facing == CameraInfo.CAMERA_FACING_FRONT);
            bitmap = CameraUtil.rotateAndMirror(bitmap, 0, mirror);
        }
        return bitmap;
    }

    private void showCaptureResult() {
        mIsInReviewMode = true;
        Bitmap bitmap = getVideoThumbnail();
        if (bitmap != null) {
            mUI.showReviewImage(bitmap);
        }
        mUI.showReviewControls();
        mUI.enableCameraControls(false);
        mUI.showTimeLapseUI(false);
    }

    private void pauseVideoRecording() {
        Log.v(TAG, "pauseVideoRecording");
        mMediaRecorderPausing = true;
        mRecordingTotalTime += SystemClock.uptimeMillis() - mRecordingStartTime;
        mMediaRecorder.pause();
    }

    private void resumeVideoRecording() {
        Log.v(TAG, "resumeVideoRecording");
        mMediaRecorderPausing = false;
        mRecordingStartTime = SystemClock.uptimeMillis();
        updateRecordingTime();
        if (!ApiHelper.HAS_RESUME_SUPPORTED){
            mMediaRecorder.start();
        } else {
            try {
                Method resumeRec = Class.forName("android.media.MediaRecorder").getMethod("resume");
                resumeRec.invoke(mMediaRecorder);
            } catch (Exception e) {
                Log.v(TAG, "resume method not implemented");
            }
        }
    }

    private boolean stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        mStopRecPending = true;
        mUI.setSwipingEnabled(true);
        if (!isVideoCaptureIntent()) {
            mUI.showSwitcher();
        }

        boolean fail = false;
        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                mMediaRecorder.setOnErrorListener(null);
                mMediaRecorder.setOnInfoListener(null);
                mMediaRecorder.stop();
                shouldAddToMediaStoreNow = true;
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "stopVideoRecording: Setting current video filename: "
                        + mCurrentVideoFilename);
                AccessibilityUtils.makeAnnouncement(mUI.getShutterButton(),
                        mActivity.getString(R.string.video_recording_stopped));
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
                fail = true;
            }
            mMediaRecorderRecording = false;

            //If recording stops while snapshot is in progress, we might not get jpeg callback
            //because cameraservice will disable picture related messages. Hence reset the
            //flag here so that we can take liveshots in the next recording session.
            mSnapshotInProgress = false;
            showVideoSnapshotUI(false);

            // If the activity is paused, this means activity is interrupted
            // during recording. Release the camera as soon as possible because
            // face unlock or other applications may need to use the camera.
            if (mPaused) {
                closeCamera();
            }

            mUI.showRecordingUI(false);
            if (!mIsVideoCaptureIntent) {
                mUI.enableCameraControls(true);
            }
            // The orientation was fixed during video recording. Now make it
            // reflect the device orientation as video recording is stopped.
            mUI.setOrientationIndicator(0, true);
            keepScreenOnAwhile();
            if (shouldAddToMediaStoreNow && !fail) {
                if (mVideoFileDescriptor == null) {
                    saveVideo();
                } else if (mIsVideoCaptureIntent) {
                    // if no file save is needed, we can show the post capture UI now
                    showCaptureResult();
                }
            }
        }
        // release media recorder
        releaseMediaRecorder();
        releaseAudioFocus();
        if (!mPaused) {
            mCameraDevice.lock();
            if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                stopPreview();
                mUI.hideSurfaceView();
                // Switch back to use SurfaceTexture for preview.
                startPreview();
            }
        }
        // Update the parameters here because the parameters might have been altered
        // by MediaRecorder.
        if (!mPaused) mParameters = mCameraDevice.getParameters();
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                fail ? UsageStatistics.ACTION_CAPTURE_FAIL :
                        UsageStatistics.ACTION_CAPTURE_DONE, "Video",
                mMediaRecorderPausing ? mRecordingTotalTime :
                        SystemClock.uptimeMillis() - mRecordingStartTime + mRecordingTotalTime);
        mStopRecPending = false;
        return fail;
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

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        StringBuilder timeStringBuilder = new StringBuilder();

        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);

            timeStringBuilder.append(':');
        }

        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');

        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);

        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }

        return timeStringBuilder.toString();
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        if (mMediaRecorderPausing) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime + mRecordingTotalTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            text = millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }

        mUI.setRecordingTime(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mUI.setRecordingTimeTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
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

        if ((preview_flip_value != 0) || (video_flip_value != 0) || (picture_flip_value != 0)) {
            mIsFlipEnabled = true;
        } else {
            mIsFlipEnabled = false;
        }
    }

     private void qcomSetCameraParameters(){
        // add QCOM Parameters here
        // Set color effect parameter.
        Log.i(TAG,"NOTE: qcomSetCameraParameters " + videoWidth + " x " + videoHeight);
        String colorEffect = mPreferences.getString(
            CameraSettings.KEY_COLOR_EFFECT,
            mActivity.getString(R.string.pref_camera_coloreffect_default));
        Log.v(TAG, "Color effect value =" + colorEffect);
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        String disMode = mPreferences.getString(
                CameraSettings.KEY_DIS,
                mActivity.getString(R.string.pref_camera_dis_default));
        Log.v(TAG, "DIS value =" + disMode);
        mIsDISEnabled = disMode.equals("enable");

        if (is4KEnabled() && !PERSIST_4K_NO_LIMIT) {
            if (isSupported(mActivity.getString(R.string.pref_camera_dis_value_disable),
                    CameraSettings.getSupportedDISModes(mParameters))) {
                mParameters.set(CameraSettings.KEY_QC_DIS_MODE,
                        mActivity.getString(R.string.pref_camera_dis_value_disable));
                mUI.overrideSettings(CameraSettings.KEY_DIS,
                        mActivity.getString(R.string.pref_camera_dis_value_disable));
                mIsDISEnabled = false;
            } else {
                Log.e(TAG, "Not supported IS mode = " +
                        mActivity.getString(R.string.pref_camera_dis_value_disable));
            }
        } else {
            if (isSupported(disMode,
                    CameraSettings.getSupportedDISModes(mParameters))) {
                mParameters.set(CameraSettings.KEY_QC_DIS_MODE, disMode);
            } else {
                Log.e(TAG, "Not supported IS mode = " + disMode);
            }
        }

        if (mDefaultAntibanding == null) {
            mDefaultAntibanding = mParameters.getAntibanding();
            Log.d(TAG, "default antibanding value = " + mDefaultAntibanding);
        }

        if (disMode.equals("enable")) {
            Log.d(TAG, "dis is enabled, set antibanding to auto.");
            if (isSupported(Parameters.ANTIBANDING_AUTO, mParameters.getSupportedAntibanding())) {
                mParameters.setAntibanding(Parameters.ANTIBANDING_AUTO);
            }
        } else {
            if (isSupported(mDefaultAntibanding, mParameters.getSupportedAntibanding())) {
                mParameters.setAntibanding(mDefaultAntibanding);
            }
        }
        Log.d(TAG, "antiBanding value = " + mParameters.getAntibanding());

        mUnsupportedHFRVideoSize = false;
        mUnsupportedHFRVideoCodec = false;
        mUnsupportedHSRVideoSize = false;
        // To set preview format as YV12 , run command
        // "adb shell setprop "debug.camera.yv12" true"
        String yv12formatset = SystemProperties.get("debug.camera.yv12");
        if(yv12formatset.equals("true")) {
            Log.v(TAG, "preview format set to YV12");
            mParameters.setPreviewFormat (ImageFormat.YV12);
        }

        mParameters.set(KEY_PREVIEW_FORMAT, FORMAT_NV21);
        Log.v(TAG, "preview format set to NV21");

        // Set High Frame Rate.
        String highFrameRate = mPreferences.getString(
            CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE,
            mActivity. getString(R.string.pref_camera_hfr_default));
        boolean isHFR = "hfr".equals(highFrameRate.substring(0,3));
        boolean isHSR = "hsr".equals(highFrameRate.substring(0,3));

        if (isHFR || isHSR) {
            String hfrRate = highFrameRate.substring(3);
            if (isHFR) {
                mUnsupportedHFRVideoSize = true;
            } else {
                mUnsupportedHSRVideoSize = true;
            }
            String hfrsize = videoWidth+"x"+videoHeight;
            Log.v(TAG, "current set resolution is : "+hfrsize+ " : Rate is : " + hfrRate );
            try {
                Size size = null;
                if (isSupported(hfrRate, mParameters.getSupportedVideoHighFrameRateModes())) {
                    int index = mParameters.getSupportedVideoHighFrameRateModes().indexOf(
                            hfrRate);
                    size = mParameters.getSupportedHfrSizes().get(index);
                }
                if (size != null) {
                    if (videoWidth <= size.width && videoHeight <= size.height) {
                        if (isHFR) {
                            mUnsupportedHFRVideoSize = false;
                        } else {
                            mUnsupportedHSRVideoSize = false;
                        }
                        Log.v(TAG,"Current hfr resolution is supported");
                    }
                }
            } catch (NullPointerException e){
                Log.e(TAG, "supported hfr sizes is null");
            }

            int hfrFps = Integer.parseInt(hfrRate);
            if (!isSessionSupportedByEncoder(videoWidth, videoHeight, hfrFps)) {
                if (isHFR) {
                            mUnsupportedHFRVideoSize = true;
                        } else {
                            mUnsupportedHSRVideoSize = true;
                        }
                    }

            if (isHFR) {
                mParameters.set(CameraSettings.KEY_VIDEO_HSR, "off");
                if (mUnsupportedHFRVideoSize) {
                    mParameters.setVideoHighFrameRate("off");
                    Log.v(TAG,"Unsupported hfr resolution");
                } else {
                    mParameters.setVideoHighFrameRate(hfrRate);
                }
            } else {
                mParameters.setVideoHighFrameRate("off");
                if (mUnsupportedHSRVideoSize) {
                    Log.v(TAG,"Unsupported hsr resolution");
                    mParameters.set(CameraSettings.KEY_VIDEO_HSR, "off");
                } else {
                    mParameters.set(CameraSettings.KEY_VIDEO_HSR, hfrRate);
                }
            }
        } else {
            mParameters.setVideoHighFrameRate("off");
            mParameters.set(CameraSettings.KEY_VIDEO_HSR, "off");
        }
        setFlipValue();

        // Set video CDS
        String video_cds = mPreferences.getString(
                CameraSettings.KEY_VIDEO_CDS_MODE,
                mActivity.getString(R.string.pref_camera_video_cds_default));

        if ((mPrevSavedVideoCDS == null) && (video_cds != null)) {
            mPrevSavedVideoCDS = video_cds;
        }

        if (mOverrideCDS) {
            video_cds = mPrevSavedVideoCDS;
            mOverrideCDS = false;
        }

        if (CameraUtil.isSupported(video_cds,
                CameraSettings.getSupportedVideoCDSModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_VIDEO_CDS_MODE, video_cds);
        }

        // Set video TNR
        String video_tnr = mPreferences.getString(
                CameraSettings.KEY_VIDEO_TNR_MODE,
                mActivity.getString(R.string.pref_camera_video_tnr_default));
        if (CameraUtil.isSupported(video_tnr,
                CameraSettings.getSupportedVideoTNRModes(mParameters))) {
            if (!video_tnr.equals(mActivity.getString(R.string.
                    pref_camera_video_tnr_value_off))) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_video_cds_value_off));
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_video_cds_value_off));
                if (!mIsVideoCDSUpdated) {
                    if (video_cds != null) {
                        mPrevSavedVideoCDS = mTempVideoCDS;
                    }
                    mIsVideoTNREnabled = true;
                    mIsVideoCDSUpdated = true;
                }
            } else if (mIsVideoTNREnabled) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_CDS_MODE, mPrevSavedVideoCDS);
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_CDS_MODE, mPrevSavedVideoCDS);
                mIsVideoTNREnabled = false;
                mIsVideoCDSUpdated = false;
                mOverrideCDS = true;
            } else {
                mTempVideoCDS = video_cds;
            }
            mParameters.set(CameraSettings.KEY_QC_VIDEO_TNR_MODE, video_tnr);
            mUI.overrideSettings(CameraSettings.KEY_VIDEO_TNR_MODE, video_tnr);
        }

        String noiseReductionMode = mPreferences.getString(
                CameraSettings.KEY_NOISE_REDUCTION,
                mActivity.getString(R.string.pref_camera_noise_reduction_default));
        Log.v(TAG, "Noise ReductionMode =" + noiseReductionMode);

        if (isSupported(noiseReductionMode,
                CameraSettings.getSupportedNoiseReductionModes(mParameters))) {
            /* Disable CDS */
            if (noiseReductionMode.equals(
                    mActivity.getString(R.string.pref_camera_noise_reduction_value_high_quality)) &&
                    video_cds.equals(mActivity.getString(R.string.
                            pref_camera_video_cds_value_on))) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_video_cds_value_off));
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_CDS_MODE,
                        mActivity.getString(R.string.pref_camera_video_cds_value_off));
                Toast.makeText(mActivity, R.string.disable_CDS_during_HighQualityNoiseReduction,
                        Toast.LENGTH_LONG).show();
            }

            /* Disable TNR */
            if (noiseReductionMode.equals(
                    mActivity.getString(R.string.pref_camera_noise_reduction_value_high_quality)) &&
                    video_tnr.equals(mActivity.getString(R.string.
                            pref_camera_video_tnr_value_on))) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_TNR_MODE,
                        mActivity.getString(R.string.pref_camera_video_tnr_value_off));
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_TNR_MODE,
                        mActivity.getString(R.string.pref_camera_video_tnr_value_off));
                Toast.makeText(mActivity, R.string.disable_TNR_during_HighQualityNoiseReduction,
                        Toast.LENGTH_LONG).show();
            }

            /* Set Noise Reduction mode */
            mParameters.set(CameraSettings.KEY_QC_NOISE_REDUCTION_MODE, noiseReductionMode);
        }

        String seeMoreMode = mPreferences.getString(
                CameraSettings.KEY_SEE_MORE,
                mActivity.getString(R.string.pref_camera_see_more_default));
        Log.v(TAG, "See More value =" + seeMoreMode);

        if (isSupported(seeMoreMode,
                CameraSettings.getSupportedSeeMoreModes(mParameters))) {
            /* Disable CDS */
            if (seeMoreMode.equals(
                    mActivity.getString(R.string.pref_camera_see_more_value_on)) &&
                    video_cds.equals(mActivity.getString(R.string.
                    pref_camera_video_cds_value_on))) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_CDS_MODE,
                    mActivity.getString(R.string.pref_camera_video_cds_value_off));
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_CDS_MODE,
                    mActivity.getString(R.string.pref_camera_video_cds_value_off));
                Toast.makeText(mActivity, R.string.disable_CDS_during_SeeMore,
                    Toast.LENGTH_LONG).show();
            }

            /* Disable TNR */
            if (seeMoreMode.equals(
                    mActivity.getString(R.string.pref_camera_see_more_value_on)) &&
                    video_tnr.equals(mActivity.getString(R.string.
                    pref_camera_video_tnr_value_on))) {
                mParameters.set(CameraSettings.KEY_QC_VIDEO_TNR_MODE,
                    mActivity.getString(R.string.pref_camera_video_tnr_value_off));
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_TNR_MODE,
                    mActivity.getString(R.string.pref_camera_video_tnr_value_off));
                Toast.makeText(mActivity, R.string.disable_TNR_during_SeeMore,
                    Toast.LENGTH_LONG).show();
            }

            /* Disable NR */
            if (seeMoreMode.equals(
                    mActivity.getString(R.string.pref_camera_see_more_value_on)) &&
                    !noiseReductionMode.equals(mActivity.getString(R.string.
                            pref_camera_noise_reduction_value_off))) {
                mParameters.set(CameraSettings.KEY_QC_NOISE_REDUCTION_MODE,
                        mActivity.getString(R.string.pref_camera_noise_reduction_value_off));
                mUI.overrideSettings(CameraSettings.KEY_NOISE_REDUCTION,
                        mActivity.getString(R.string.pref_camera_noise_reduction_value_off));
                Toast.makeText(mActivity, R.string.disable_NR_during_SeeMore,
                        Toast.LENGTH_LONG).show();
            }
            /* Set SeeMore mode */
            mParameters.set(CameraSettings.KEY_QC_SEE_MORE_MODE, seeMoreMode);
        }

        // Set Video HDR.
        String videoHDR = mPreferences.getString(
                CameraSettings.KEY_VIDEO_HDR,
                mActivity.getString(R.string.pref_camera_video_hdr_default));
        Log.v(TAG, "Video HDR Setting =" + videoHDR);
        if (isSupported(videoHDR, mParameters.getSupportedVideoHDRModes())) {
             mParameters.setVideoHDRMode(videoHDR);
        } else
             mParameters.setVideoHDRMode("off");

        //HFR/HSR recording not supported with DIS,TimeLapse,HDR option
        String hfr = mParameters.getVideoHighFrameRate();
        String hsr = mParameters.get(CameraSettings.KEY_VIDEO_HSR);
        String hdr = mParameters.getVideoHDRMode();
         if ( !"off".equals(highFrameRate) ) {
             // Read time lapse recording interval.
             String frameIntervalStr = mPreferences.getString(
                    CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                    mActivity.getString(R.string.pref_video_time_lapse_frame_interval_default));
             int timeLapseInterval = Integer.parseInt(frameIntervalStr);
             int rate = 0;
             if ( isDigit(highFrameRate.substring(3)) ) {
                 rate = Integer.parseInt(highFrameRate.substring(3));
             }
             Log.v(TAG, "rate = "+rate);
             if ( (timeLapseInterval != 0) ||
                  (disMode.equals("enable") && (rate > PERSIST_EIS_MAX_FPS)) ||
                  ((hdr != null) && (!hdr.equals("off"))) ) {
                Log.v(TAG,"HDR/DIS/Time Lapse ON for HFR/HSR selection, turning HFR/HSR off");
                mParameters.setVideoHighFrameRate("off");
                mParameters.set(CameraSettings.KEY_VIDEO_HSR, "off");
                mUI.overrideSettings(CameraSettings.KEY_VIDEO_HIGH_FRAME_RATE, "off");
             }
        }

        //getSupportedPictureSizes will always send a sorted a list in descending order
        Size biggestSize = mParameters.getSupportedPictureSizes().get(0);

        if (biggestSize.width <= videoWidth || biggestSize.height <= videoHeight) {
            if (disMode.equals("enable")) {
                Log.v(TAG,"DIS is not supported for this video quality");
                RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_dis,
                               Toast.LENGTH_LONG).show();
                mParameters.set(CameraSettings.KEY_QC_DIS_MODE, "disable");
                mUI.overrideSettings(CameraSettings.KEY_DIS,"disable");
                mIsDISEnabled = false;
            }
        }
        //setting video rotation
        String videoRotation = mPreferences.getString(
            CameraSettings.KEY_VIDEO_ROTATION,
            mActivity.getString(R.string.pref_camera_video_rotation_default));
        if (isSupported(videoRotation, mParameters.getSupportedVideoRotationValues())) {
            mParameters.setVideoRotation(videoRotation);
        }

        //set power mode settings
        updatePowerMode();

        // Set face detetction parameter.
        String faceDetection = mPreferences.getString(
            CameraSettings.KEY_FACE_DETECTION,
            mActivity.getString(R.string.pref_camera_facedetection_default));

        if (CameraUtil.isSupported(faceDetection, mParameters.getSupportedFaceDetectionModes())) {
            Log.d(TAG, "setFaceDetectionMode "+faceDetection);
            mParameters.setFaceDetectionMode(faceDetection);
            if(faceDetection.equals("on") && mFaceDetectionEnabled == false) {
                mFaceDetectionEnabled = true;
                startFaceDetection();
            } else if(faceDetection.equals("off") && mFaceDetectionEnabled == true) {
                stopFaceDetection();
                mFaceDetectionEnabled = false;
            }
        }
    }

    private boolean isDigit(String input) {
        String ruler = "[1-9][0-9]*";
        Pattern pattern = Pattern.compile(ruler);
        return pattern.matcher(input).matches();
    }

    @SuppressWarnings("deprecation")
    private void setCameraParameters(boolean isFlashDelay) {
        Log.d(TAG,"Preview dimension in App->"+mDesiredPreviewWidth+"X"+mDesiredPreviewHeight);
        mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        mParameters.set("video-size", mProfile.videoFrameWidth+"x"+mProfile.videoFrameHeight);
        int[] fpsRange = CameraUtil.getMaxPreviewFpsRange(mParameters);
        if (fpsRange.length > 0) {
            mParameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        } else {
            mParameters.setPreviewFrameRate(mProfile.videoFrameRate);
        }

        if (isFlashDelay) {
            mHandler.sendEmptyMessageDelayed(HANDLE_FLASH_TORCH_DELAY, 800);
        } else {
            forceFlashOffIfSupported(!mPreviewFocused);
        }
        videoWidth = mProfile.videoFrameWidth;
        videoHeight = mProfile.videoFrameHeight;

        Log.i(TAG,"NOTE: SetCameraParameters " + videoWidth + " x " + videoHeight);
        String recordSize = videoWidth + "x" + videoHeight;
        Log.e(TAG,"Video dimension in App->"+recordSize);
        mParameters.set("video-size", recordSize);
        // Set white balance parameter.
        String whiteBalance = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                mActivity.getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }

        // Set zoom.
        if (mParameters.isZoomSupported()) {
            Parameters p = mCameraDevice.getParameters();
            mZoomValue = p.getZoom();
            mParameters.setZoom(mZoomValue);
        }

        // Set continuous autofocus.
        List<String> supportedFocus = mParameters.getSupportedFocusModes();
        if (isSupported(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, supportedFocus)) {
            mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        mParameters.set(CameraUtil.RECORDING_HINT, CameraUtil.TRUE);

        // Enable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "true");
        }

        // Set picture size.
        // The logic here is different from the logic in still-mode camera.
        // There we determine the preview size based on the picture size, but
        // here we determine the picture size based on the preview size.
        String videoSnapshotSize = mPreferences.getString(
                CameraSettings.KEY_VIDEO_SNAPSHOT_SIZE,
                mActivity.getString(R.string.pref_camera_videosnapsize_default));
        Size optimalSize;
        if(videoSnapshotSize.equals("auto")) {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            optimalSize = CameraUtil.getOptimalVideoSnapshotPictureSize(supported,
                    (double) mDesiredPreviewWidth / mDesiredPreviewHeight);
            Size original = mParameters.getPictureSize();
            if (!original.equals(optimalSize)) {
                mParameters.setPictureSize(optimalSize.width, optimalSize.height);
            }
        } else {
            CameraSettings.setCameraPictureSize(
                videoSnapshotSize,
                mParameters.getSupportedPictureSizes(),
                mParameters);
            optimalSize = mParameters.getPictureSize();
        }

        Log.v(TAG, "Video snapshot size is " + optimalSize.width + "x" +
                optimalSize.height);

        // Set jpegthumbnail size
        // Set a jpegthumbnail size that is closest to the Picture height and has
        // the right aspect ratio.
        Size size = mParameters.getPictureSize();
        List<Size> sizes = mParameters.getSupportedJpegThumbnailSizes();
        optimalSize = CameraUtil.getOptimalJpegThumbnailSize(sizes,
                (double) size.width / size.height);
        Size original = mParameters.getJpegThumbnailSize();
        if (!original.equals(optimalSize)) {
            mParameters.setJpegThumbnailSize(optimalSize.width, optimalSize.height);
        }
        Log.v(TAG, "Thumbnail size is " + optimalSize.width + "x" + optimalSize.height);

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);
        //Call Qcom related Camera Parameters
        qcomSetCameraParameters();

        boolean flag = false;
        if (mPreviewing) {
            stopPreview();
            flag = true;
        }
        mCameraDevice.setParameters(mParameters);
        if (flag) {
            startPreview();
        }
        // Keep preview size up to date.
        mParameters = mCameraDevice.getParameters();

        // Update UI based on the new parameters.
        mUI.updateOnScreenIndicators(mParameters, mPreferences);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Do nothing.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();
        resizeForPreviewAspectRatio();
    }

    @Override
    public void onOverriddenPreferencesClicked() {
    }

    @Override
    // TODO: Delete this after old camera code is removed
    public void onRestorePreferencesClicked() {
    }

    @Override
    public void onSharedPreferenceChanged(ListPreference pref) {
        if (pref != null && CameraSettings.KEY_VIDEO_QUALITY.equals(pref.getKey())
            && !PERSIST_4K_NO_LIMIT) {
            String videoQuality = pref.getValue();
            if (CameraSettings.VIDEO_QUALITY_TABLE.containsKey(videoQuality)) {
                int quality = CameraSettings.VIDEO_QUALITY_TABLE.get(videoQuality);
                if ((quality == CamcorderProfile.QUALITY_2160P
                        || quality == CamcorderProfile.QUALITY_4KDCI)
                        && mPreferences != null) {
                    String disDisable = mActivity.getString(R.string.pref_camera_dis_value_disable);
                    if (!disDisable.equals(
                            mPreferences.getString(CameraSettings.KEY_DIS, disDisable))) {
                        RotateTextToast.makeText(mActivity, R.string.video_quality_4k_disable_IS,
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        onSharedPreferenceChanged();
    }

    @Override
    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPaused) {
            return;
        }
        synchronized (mPreferences) {
            // If mCameraDevice is not ready then we can set the parameter in
            // startPreview().
            if (mCameraDevice == null) return;

            boolean recordLocation = RecordLocationPreference.get(mPreferences,
                    CameraSettings.KEY_RECORD_LOCATION);
            mLocationManager.recordLocation(recordLocation);

            readVideoPreferences();
            mUI.showTimeLapseUI(mCaptureTimeLapse);
            // We need to restart the preview if preview size is changed.
            Size size = mParameters.getPreviewSize();
            if (size.width != mDesiredPreviewWidth
                    || size.height != mDesiredPreviewHeight || mRestartPreview) {

                stopPreview();
                resizeForPreviewAspectRatio();
                startPreview(); // Parameters will be set in startPreview().
            } else {
                setCameraParameters(false);
            }
            mRestartPreview = false;
            mUI.updateOnScreenIndicators(mParameters, mPreferences);
            Storage.setSaveSDCard(
                mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));
            mActivity.updateStorageSpaceAndHint();
        }
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    private void switchCamera() {
        if (mPaused)  {
            return;
        }

        Log.d(TAG, "Start to switch camera.");
        mUI.applySurfaceChange(VideoUI.SURFACE_STATUS.HIDE);
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        setCameraId(mCameraId);

        closeCamera();
        mUI.collapseCameraControls();
        // Restart the camera and initialize the UI. From onCreate.
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        openCamera();
        readVideoPreferences();
        mUI.applySurfaceChange(VideoUI.SURFACE_STATUS.SURFACE_VIEW);
        startPreview();
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();
        initializeVideoControl();

        // From onResume
        mZoomValue = 0;
        mUI.initializeZoom(mParameters);
        mUI.setOrientationIndicator(0, false);

        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
        mUI.updateOnScreenIndicators(mParameters, mPreferences);

        //Display timelapse msg depending upon selection in front/back camera.
        mUI.showTimeLapseUI(mCaptureTimeLapse);
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

    private void initializeVideoSnapshot() {
        if (mParameters == null) return;
        if (CameraUtil.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            // Show the tap to focus toast if this is the first start.
            if (mPreferences.getBoolean(
                        CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, true)) {
                // Delay the toast for one second to wait for orientation.
                mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_SNAPSHOT_TOAST, 1000);
            }
        }
    }

    void showVideoSnapshotUI(boolean enabled) {
        if (mParameters == null) return;
        if (CameraUtil.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            if (enabled) {
                mUI.animateFlash();
                mUI.animateCapture();
            } else {
                mUI.showPreviewBorder(enabled);
            }
            mUI.enableShutter(!enabled);
        }
    }

    private void forceFlashOffIfSupported(boolean forceOff) {
        String flashMode;
        if (!forceOff) {
            flashMode = mPreferences.getString(
                    CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_video_flashmode_default));
        } else {
            flashMode = Parameters.FLASH_MODE_OFF;
        }
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
                flashMode = mActivity.getString(
                        R.string.pref_camera_flashmode_no_flash);
            }
        }
    }

    /**
     * Used to update the flash mode. Video mode can turn on the flash as torch
     * mode, which we would like to turn on and off when we switching in and
     * out to the preview.
     *
     * @param forceOff whether we want to force the flash off.
     */
    private void forceFlashOff(boolean forceOff) {
        if (!mPreviewing || mParameters.getFlashMode() == null) {
            return;
        }
        forceFlashOffIfSupported(forceOff);
        mCameraDevice.setParameters(mParameters);
        mUI.updateOnScreenIndicators(mParameters, mPreferences);
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mUI.onPreviewFocusChanged(previewFocused);
        mHandler.sendEmptyMessageDelayed(HANDLE_FLASH_TORCH_DELAY, 800);
        mPreviewFocused = previewFocused;
    }

    @Override
    public boolean arePreviewControlsVisible() {
        return mUI.arePreviewControlsVisible();
    }

    private final class JpegPictureCallback implements CameraPictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(byte [] jpegData, CameraProxy camera) {
            Log.v(TAG, "onPictureTaken");
            if(!mSnapshotInProgress || mPaused || mCameraDevice == null) return;
            mSnapshotInProgress = false;
            showVideoSnapshotUI(false);
            storeImage(jpegData, mLocation);
        }
    }

    private void storeImage(final byte[] data, Location loc) {
        long dateTaken = System.currentTimeMillis();
        String title = CameraUtil.createJpegName(dateTaken);
        ExifInterface exif = Exif.getExif(data);
        int orientation = Exif.getOrientation(exif);
        Size s = mParameters.getPictureSize();
        mActivity.getMediaSaveService().addImage(
                data, title, dateTaken, loc, s.width, s.height, orientation,
                exif, mOnPhotoSavedListener, mContentResolver,
                PhotoModule.PIXEL_FORMAT_JPEG);
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    private void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    private void showTapToSnapshotToast() {
        new RotateTextToast(mActivity, R.string.video_snapshot_hint, 0).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return true;
    }

    // required by OnPreferenceChangedListener
    @Override
    public void onCameraPickerClicked(int cameraId) {
        if (mPaused || mPendingSwitchCameraId != -1) return;

        mPendingSwitchCameraId = cameraId;
        Log.d(TAG, "Start to copy texture.");
        // We need to keep a preview frame for the animation before
        // releasing the camera. This will trigger onPreviewTextureCopied.
        // TODO: ((CameraScreenNail) mActivity.mCameraScreenNail).copyTexture();
        // Disable all camera controls.
        mSwitchingCamera = true;
        switchCamera();

    }

    @Override
    public void onShowSwitcherPopup() {
        mUI.onShowSwitcherPopup();
    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        // do nothing.
    }

    @Override
    public void onPreviewUIReady() {
        if (mPaused || mCameraDevice == null) {
            return;
        }
        Log.v(TAG, "onPreviewUIReady");
        if (!mPreviewing) {
            startPreview();
        } else {
            synchronized (mCameraDevice) {
                SurfaceHolder sh = mUI.getSurfaceHolder();
                if (sh == null) {
                    Log.w(TAG, "holder for preview is not ready.");
                    return;
                }
                mCameraDevice.setPreviewDisplay(sh);
            }
        }
    }

    @Override
    public void onPreviewUIDestroyed() {
        stopPreview();
    }

     @Override
    public void onButtonPause() {
        pauseVideoRecording();
    }

    @Override
    public void onButtonContinue() {
        resumeVideoRecording();
    }

    private void updatePowerMode() {
        String lpmSupported = mParameters.get("low-power-mode-supported");
        if ((lpmSupported != null) && "true".equals(lpmSupported)) {
            if (!mIsDISEnabled && !mIsFlipEnabled) {
                mParameters.set("low-power-mode", "enable");
            } else {
                mParameters.set("low-power-mode", "disable");
            }
        }
    }


    public void startFaceDetection() {
        if (mCameraDevice == null) return;

        if (mFaceDetectionEnabled == false
               || mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mUI.onStartFaceDetection(mCameraDisplayOrientation,
                    (info.facing == CameraInfo.CAMERA_FACING_FRONT));
            mCameraDevice.setFaceDetectionCallback(mHandler, mUI);
            Log.d(TAG, "start face detection Video "+mParameters.getMaxNumDetectedFaces());
            mCameraDevice.startFaceDetection();
        }
    }

    public void stopFaceDetection() {
        Log.d(TAG, "stop face detection");
        if (mFaceDetectionEnabled == false || !mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionCallback(null, null);
            mUI.pauseFaceDetection();
            mCameraDevice.stopFaceDetection();
            mUI.onStopFaceDetection();
        }
    }

    @Override
    public void onErrorListener(int error) {
        enableRecordingLocation(false);
    }

}

