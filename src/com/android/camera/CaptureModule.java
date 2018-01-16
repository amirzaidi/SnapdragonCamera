/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
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
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.VideoEncoderCap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Bundle;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.graphics.Bitmap;

import com.android.camera.exif.ExifInterface;
import com.android.camera.imageprocessor.filter.BlurbusterFilter;
import com.android.camera.imageprocessor.filter.ChromaflashFilter;
import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.imageprocessor.PostProcessor;
import com.android.camera.imageprocessor.FrameProcessor;
import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.StillmoreFilter;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.TrackingFocusRenderer;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.PersistUtil;
import com.android.camera.util.SettingTranslation;
import com.android.camera.util.AccessibilityUtils;
import com.android.camera.util.VendorTagUtil;
import com.android.internal.util.MemInfoReader;

import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.filter.ClearSightImageProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

public class CaptureModule implements CameraModule, PhotoController,
        MediaSaveService.Listener, ClearSightImageProcessor.Callback,
        SettingsManager.Listener, LocationManager.Listener,
        CountDownView.OnCountDownFinishedListener,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
    public static final int DUAL_MODE = 0;
    public static final int BAYER_MODE = 1;
    public static final int MONO_MODE = 2;
    public static final int SWITCH_MODE = 3;
    public static final int BAYER_ID = 0;
    public static int MONO_ID = -1;
    public static int FRONT_ID = -1;
    public static int SWITCH_ID = -1;
    public static final int INTENT_MODE_NORMAL = 0;
    public static final int INTENT_MODE_CAPTURE = 1;
    public static final int INTENT_MODE_VIDEO = 2;
    public static final int INTENT_MODE_CAPTURE_SECURE = 3;
    private static final int BACK_MODE = 0;
    private static final int FRONT_MODE = 1;
    private static final int CANCEL_TOUCH_FOCUS_DELAY = PersistUtil.getCancelTouchFocusDelay();
    private static final int OPEN_CAMERA = 0;
    private static final int CANCEL_TOUCH_FOCUS = 1;
    private static final int MAX_NUM_CAM = 4;
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)};
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;
    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_AF_LOCK = 1;
    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /**
     * Camera state: Waiting for the exposure state to be locked.
     */
    private static final int STATE_WAITING_AE_LOCK = 3;
    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    /**
     * Camera state: Waiting for the touch-to-focus to converge.
     */
    private static final int STATE_WAITING_TOUCH_FOCUS = 5;
    /**
     * Camera state: Focus and exposure has been locked and converged.
     */
    private static final int STATE_AF_AE_LOCKED = 6;
    private static final String TAG = "SnapCam_CaptureModule";

    // Used for check memory status for longshot mode
    // Currently, this cancel threshold selection is based on test experiments,
    // we can change it based on memory status or other requirements.
    private static final int LONGSHOT_CANCEL_THRESHOLD = 40 * 1024 * 1024;

    private static final int NORMAL_SESSION_MAX_FPS = 30;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    public static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);

    MeteringRectangle[][] mAFRegions = new MeteringRectangle[MAX_NUM_CAM][];
    MeteringRectangle[][] mAERegions = new MeteringRectangle[MAX_NUM_CAM][];
    CaptureRequest.Key<Byte> BayerMonoLinkEnableKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.enable",
                    Byte.class);
    CaptureRequest.Key<Byte> BayerMonoLinkMainKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.is_main",
                    Byte.class);
    CaptureRequest.Key<Integer> BayerMonoLinkSessionIdKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data" +
                    ".related_camera_id", Integer.class);
    public static CameraCharacteristics.Key<Byte> MetaDataMonoOnlyKey =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.sensor_meta_data.is_mono_only",
                    Byte.class);
    public static CameraCharacteristics.Key<int[]> InstantAecAvailableModes =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.instant_aec.instant_aec_available_modes", int[].class);
    public static final CaptureRequest.Key<Integer> INSTANT_AEC_MODE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.instant_aec.instant_aec_mode", Integer.class);
    public static final CaptureRequest.Key<Integer> SATURATION=
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.saturation.use_saturation", Integer.class);
    public static final CaptureRequest.Key<Byte> histMode =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.histogram.enable", byte.class);
    public static CameraCharacteristics.Key<Integer> buckets =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.histogram.buckets", Integer.class);
    public static CameraCharacteristics.Key<Integer> maxCount =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.histogram.max_count", Integer.class);
    public static CaptureResult.Key<int[]> histogramStats =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.histogram.stats", int[].class);
    public static CameraCharacteristics.Key<Integer> isHdrScene =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.stats.is_hdr_scene", Integer.class);
    public static CameraCharacteristics.Key<Byte> IS_SUPPORT_QCFA_SENSOR =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.quadra_cfa.is_qcfa_sensor", Byte.class);
    public static CameraCharacteristics.Key<int[]> QCFA_SUPPORT_DIMENSION =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.quadra_cfa.qcfa_dimension", int[].class);
    public static CameraCharacteristics.Key<Byte> bsgcAvailable =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.stats.bsgc_available", Byte.class);
    public static CaptureResult.Key<byte[]> blinkDetected =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.blink_detected", byte[].class);
    public static CaptureResult.Key<byte[]> blinkDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.blink_degree", byte[].class);
    public static CaptureResult.Key<byte[]> smileDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.smile_degree", byte[].class);
    public static CaptureResult.Key<byte[]> smileConfidence =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.smile_confidence", byte[].class);
    public static CaptureResult.Key<byte[]> gazeAngle =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_angle", byte[].class);
    public static CaptureResult.Key<int[]> gazeDirection =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_direction",
                    int[].class);
    public static CaptureResult.Key<byte[]> gazeDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_degree",
                    byte[].class);
    public static final CameraCharacteristics.Key<int[]> hfrSizeList =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.hfr.sizes", int[].class);
    public static final CaptureRequest.Key<Boolean> bokeh_enable = new CaptureRequest.Key<>(
            "org.codeaurora.qcamera3.bokeh.enable", Boolean.class);
    public static final CaptureRequest.Key<Integer> bokeh_blur_level = new CaptureRequest.Key<>(
            "org.codeaurora.qcamera3.bokeh.blurLevel", Integer.class);

    private boolean[] mTakingPicture = new boolean[MAX_NUM_CAM];
    private int mControlAFMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    private int mLastResultAFState = -1;
    private Rect[] mCropRegion = new Rect[MAX_NUM_CAM];
    private boolean mAutoFocusRegionSupported;
    private boolean mAutoExposureRegionSupported;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    /*Histogram variables*/
    private Camera2GraphView mGraphViewR,mGraphViewGR,mGraphViewGB,mGraphViewB;
    /*HDR Test*/
    private boolean mCaptureHDRTestEnable = false;
    boolean mHiston = false;
    private boolean mFirstTimeInitialized;
    private boolean mCamerasOpened = false;
    private boolean mIsLinked = false;
    private long mCaptureStartTime;
    private boolean mPaused = true;
    private boolean mIsSupportedQcfa = false;
    private Semaphore mSurfaceReadyLock = new Semaphore(1);
    private boolean mSurfaceReady = true;
    private boolean[] mCameraOpened = new boolean[MAX_NUM_CAM];
    private CameraDevice[] mCameraDevice = new CameraDevice[MAX_NUM_CAM];
    private String[] mCameraId = new String[MAX_NUM_CAM];
    private View mRootView;
    private CaptureUI mUI;
    private CameraActivity mActivity;
    private List<Integer> mCameraIdList;
    private float mZoomValue = 1f;
    private FocusStateListener mFocusStateListener;
    private LocationManager mLocationManager;
    private SettingsManager mSettingsManager;
    private long SECONDARY_SERVER_MEM;
    private boolean mLongshotActive = false;
    private CameraCharacteristics mMainCameraCharacteristics;
    private int mDisplayRotation;
    private int mDisplayOrientation;
    private boolean mIsRefocus = false;
    private int mChosenImageFormat;
    private Toast mToast;

    private boolean mStartRecPending = false;
    private boolean mStopRecPending = false;

    boolean mUnsupportedResolution = false;

    private static final int SDCARD_SIZE_LIMIT = 4000 * 1024 * 1024;
    private static final String sTempCropFilename = "crop-temp";
    private static final int REQUEST_CROP = 1000;
    private int mIntentMode = INTENT_MODE_NORMAL;
    private String mCropValue;
    private Uri mCurrentVideoUri;
    private ParcelFileDescriptor mVideoFileDescriptor;
    private Uri mSaveUri;
    private boolean mQuickCapture;
    private byte[] mJpegImageData;
    private boolean mSaveRaw = false;

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
    private HandlerThread mMpoSaveThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private PostProcessor mPostProcessor;
    private FrameProcessor mFrameProcessor;
    private CaptureResult mPreviewCaptureResult;
    private Face[] mPreviewFaces = null;
    private Face[] mStickyFaces = null;
    private ExtendedFace[] mExFaces = null;
    private ExtendedFace[] mStickyExFaces = null;
    private Rect mBayerCameraRegion;
    private Handler mCameraHandler;
    private Handler mImageAvailableHandler;
    private Handler mCaptureCallbackHandler;
    private Handler mMpoSaveHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader[] mImageReader = new ImageReader[MAX_NUM_CAM];
    private ImageReader[] mRawImageReader = new ImageReader[MAX_NUM_CAM];
    private NamedImages mNamedImages;
    private ContentResolver mContentResolver;
    private byte[] mLastJpegData;
    private int mJpegFileSizeEstimation;
    private boolean mFirstPreviewLoaded;
    private int[] mPrecaptureRequestHashCode = new int[MAX_NUM_CAM];
    private int[] mLockRequestHashCode = new int[MAX_NUM_CAM];
    private final Handler mHandler = new MainHandler();
    private CameraCaptureSession mCurrentSession;
    private Size mPreviewSize;
    private Size mPictureSize;
    private Size mVideoPreviewSize;
    private Size mVideoSize;
    private Size mVideoSnapshotSize;
    private Size mPictureThumbSize;
    private Size mVideoSnapshotThumbSize;

    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    // The video duration limit. 0 means no limit.
    private int mMaxVideoDurationInMs;
    private boolean mIsMute = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    private boolean mCaptureTimeLapse = false;
    private CamcorderProfile mProfile;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private ContentValues mCurrentVideoValues;
    private String mVideoFilename;
    private boolean mMediaRecorderPausing = false;
    private long mRecordingStartTime;
    private long mRecordingTotalTime;
    private boolean mRecordingTimeCountsDown = false;
    private ImageReader mVideoSnapshotImageReader;
    private Range mHighSpeedFPSRange;
    private boolean mHighSpeedCapture = false;
    private boolean mHighSpeedRecordingMode = false; //HFR
    private int mHighSpeedCaptureRate;
    private boolean mBokehEnabled = false;
    private CaptureRequest.Builder mBokehRequestBuilder;
    private CaptureRequest.Builder mVideoRequestBuilder;

    public static int statsdata[] = new int[1024];

    private static final int SELFIE_FLASH_DURATION = 680;

    private MediaActionSound mSound;
    private Size mSupportedMaxPictureSize;
    private Size mSupportedRawPictureSize;

    private long mIsoExposureTime;
    private int mIsoSensitivity;

    private class SelfieThread extends Thread {
        public void run() {
            try {
                Thread.sleep(SELFIE_FLASH_DURATION);
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        takePicture();
                    }
                });
            } catch(InterruptedException e) {
            }
            selfieThread = null;
        }
    }
    private SelfieThread selfieThread;

    private class MediaSaveNotifyThread extends Thread {
        private Uri uri;

        public MediaSaveNotifyThread(Uri uri) {
            this.uri = uri;
        }

        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public void run() {
            while (mLongshotActive) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (uri != null)
                        mActivity.notifyNewMedia(uri);
                    mActivity.updateStorageSpaceAndHint();
                    if (mLastJpegData != null) mActivity.updateThumbnail(mLastJpegData);
                }
            });
            mediaSaveNotifyThread = null;
        }
    }

    public void updateThumbnailJpegData(byte[] jpegData) {
        mLastJpegData = jpegData;
    }

    private MediaSaveNotifyThread mediaSaveNotifyThread;

    private final MediaSaveService.OnMediaSavedListener mOnVideoSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                        mCurrentVideoUri = uri;
                    }
                }
            };

    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (mLongshotActive) {
                        if (mediaSaveNotifyThread == null) {
                            mediaSaveNotifyThread = new MediaSaveNotifyThread(uri);
                            mediaSaveNotifyThread.start();
                        } else
                            mediaSaveNotifyThread.setUri(uri);
                    } else {
                        if (uri != null) {
                            mActivity.notifyNewMedia(uri);
                        }
                    }
                }
            };

    public MediaSaveService.OnMediaSavedListener getMediaSavedListener() {
        return mOnMediaSavedListener;
    }

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


    public Face[] getPreviewFaces() {
        return mPreviewFaces;
    }

    public Face[] getStickyFaces() {
        return mStickyFaces;
    }

    public CaptureResult getPreviewCaptureResult() {
        return mPreviewCaptureResult;
    }

    public Rect getCameraRegion() {
        return mBayerCameraRegion;
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void processCaptureResult(CaptureResult result) {
            int id = (int) result.getRequest().getTag();

            if (!mFirstPreviewLoaded) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.hidePreviewCover();
                    }
                });
                mFirstPreviewLoaded = true;
            }
            if (id == getMainCameraId()) {
                mPreviewCaptureResult = result;
            }
            updateCaptureStateMachine(id, result);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            int id = (int) partialResult.getRequest().getTag();
            if (id == getMainCameraId()) {
                updateFocusStateChange(partialResult);
                Face[] faces = partialResult.get(CaptureResult.STATISTICS_FACES);
                if (faces != null && isBsgcDetecionOn()) {
                    updateFaceView(faces, getBsgcInfo(partialResult, faces.length));
                } else {
                    updateFaceView(faces, null);
                }
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            int id = (int) result.getRequest().getTag();
            if (id == getMainCameraId()) {
                updateFocusStateChange(result);
                Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                if (faces != null && isBsgcDetecionOn()) {
                    updateFaceView(faces, getBsgcInfo(result, faces.length));
                } else {
                    updateFaceView(faces, null);
                }
            }
            if (SettingsManager.getInstance().isHistogramSupport()) {
                int[] histogramStats = result.get(CaptureModule.histogramStats);
                if (histogramStats != null && mHiston) {
                    /*The first element in the array stores max hist value . Stats data begin
                    from second value*/
                    synchronized (statsdata) {
                        System.arraycopy(histogramStats, 0, statsdata, 0, 1024);
                    }
                    updateGraghView();
                }
            }
            processCaptureResult(result);
            mPostProcessor.onMetaAvailable(result);
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

            mCameraDevice[id] = cameraDevice;
            mCameraOpened[id] = true;

            if (isBackCamera() && getCameraMode() == DUAL_MODE && id == BAYER_ID) {
                Message msg = mCameraHandler.obtainMessage(OPEN_CAMERA, MONO_ID, 0);
                mCameraHandler.sendMessage(msg);
            } else {
                mCamerasOpened = true;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.onCameraOpened(mCameraIdList);
                    }
                });
                createSessions();
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onDisconnected " + id);
            cameraDevice.close();
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.e(TAG, "onError " + id + " " + error);
            if (mCamerasOpened) {
                mCameraDevice[id].close();
                mCameraDevice[id] = null;
            }
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;

            if (null != mActivity) {
                Toast.makeText(mActivity,"open camera error id =" + id,
                        Toast.LENGTH_LONG).show();
                mActivity.finish();
            }
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onClosed " + id);
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;
        }

    };

    private void updateCaptureStateMachine(int id, CaptureResult result) {
        switch (mState[id]) {
            case STATE_PREVIEW: {
                break;
            }
            case STATE_WAITING_AF_LOCK: {
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                Log.d(TAG, "STATE_WAITING_AF_LOCK id: " + id + " afState:" + afState + " aeState:" + aeState);

                // AF_PASSIVE is added for continous auto focus mode
                if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState ||
                        CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState ||
                        (mLockRequestHashCode[id] == result.getRequest().hashCode() &&
                                afState == CaptureResult.CONTROL_AF_STATE_INACTIVE)) {
                    if(id == MONO_ID && getCameraMode() == DUAL_MODE && isBackCamera()) {
                        // in dual mode, mono AE dictated by bayer AE.
                        // if not already locked, wait for lock update from bayer
                        if(aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)
                            checkAfAeStatesAndCapture(id);
                        else
                            mState[id] = STATE_WAITING_AE_LOCK;
                    } else {
                        if ((mLockRequestHashCode[id] == result.getRequest().hashCode()) || (mLockRequestHashCode[id] == 0)) {

                            // CONTROL_AE_STATE can be null on some devices
                            if(aeState == null || (aeState == CaptureResult
                                    .CONTROL_AE_STATE_CONVERGED) && isFlashOff(id)) {
                                lockExposure(id);
                            } else {
                                runPrecaptureSequence(id);
                            }
                        }
                    }
                } else if (mLockRequestHashCode[id] == result.getRequest().hashCode()){
                    Log.i(TAG, "AF lock request result received, but not focused");
                    mLockRequestHashCode[id] = 0;
                }
                break;
            }
            case STATE_WAITING_PRECAPTURE: {
                // CONTROL_AE_STATE can be null on some devices
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                Log.d(TAG, "STATE_WAITING_PRECAPTURE id: " + id + " afState: " + afState + " aeState:" + aeState);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    if ((mPrecaptureRequestHashCode[id] == result.getRequest().hashCode()) || (mPrecaptureRequestHashCode[id] == 0)) {
                        if (mLongshotActive && isFlashOn(id)) {
                            checkAfAeStatesAndCapture(id);
                        } else {
                            lockExposure(id);
                        }
                    }
                } else if (mPrecaptureRequestHashCode[id] == result.getRequest().hashCode()) {
                    Log.i(TAG, "AE trigger request result received, but not converged");
                    mPrecaptureRequestHashCode[id] = 0;
                }
                break;
            }
            case STATE_WAITING_AE_LOCK: {
                // CONTROL_AE_STATE can be null on some devices
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                Log.d(TAG, "STATE_WAITING_AE_LOCK id: " + id + " afState: " + afState + " aeState:" + aeState);
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) {
                    checkAfAeStatesAndCapture(id);
                }
                break;
            }
            case STATE_AF_AE_LOCKED: {
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                Log.d(TAG, "STATE_AF_AE_LOCKED id: " + id + " afState:" + afState + " aeState:" + aeState);
                break;
            }
            case STATE_WAITING_TOUCH_FOCUS:
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                Log.d(TAG, "STATE_WAITING_TOUCH_FOCUS id: " + id + " afState:" + afState + " aeState:" + aeState);
                break;
        }
    }

    private void checkAfAeStatesAndCapture(int id) {
        if(isBackCamera() && getCameraMode() == DUAL_MODE) {
            mState[id] = STATE_AF_AE_LOCKED;
            try {
                // stop repeating request once we have AF/AE lock
                // for mono when mono preview is off.
                if(id == MONO_ID && !canStartMonoPreview()) {
                    mCaptureSession[id].stopRepeating();
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            if(mState[BAYER_ID] == STATE_AF_AE_LOCKED &&
                    mState[MONO_ID] == STATE_AF_AE_LOCKED) {
                mState[BAYER_ID] = STATE_PICTURE_TAKEN;
                mState[MONO_ID] = STATE_PICTURE_TAKEN;
                captureStillPicture(BAYER_ID);
                captureStillPicture(MONO_ID);
            }
        } else {
            mState[id] = STATE_PICTURE_TAKEN;
            captureStillPicture(id);
            captureStillPictureForHDRTest(id);
        }
    }

    private void captureStillPictureForHDRTest(int id) {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (SettingsManager.getInstance().isCamera2HDRSupport()
                && scene != null && scene.equals("18")){
            mCaptureHDRTestEnable = true;
            captureStillPicture(id);
        }
        mCaptureHDRTestEnable = false;
    }

    public void startFaceDetection() {
            mUI.onStartFaceDetection(mDisplayOrientation,
                    mSettingsManager.isFacingFront(getMainCameraId()),
                    mSettingsManager.getSensorActiveArraySize(getMainCameraId()));
    }

    private boolean canStartMonoPreview() {
        return getCameraMode() == MONO_MODE ||
                (getCameraMode() == DUAL_MODE && isMonoPreviewOn());
    }

    private boolean isMonoPreviewOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW);
        if (value == null) return false;
        if (value.equals("on")) return true;
        else return false;
    }

    public boolean isBackCamera() {
        String switchValue = mSettingsManager.getValue(SettingsManager.KEY_SWITCH_CAMERA);
        if (switchValue != null && !switchValue.equals("-1") ) {
            CharSequence[] value = mSettingsManager.getEntryValues(SettingsManager.KEY_SWITCH_CAMERA);
            if (value.toString().contains("front"))
                return false;
            else
                return true;
        }
        String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
        if (value == null) return true;
        if (Integer.parseInt(value) == BAYER_ID) return true;
        return false;
    }

    public int getCameraMode() {
        String switchValue = mSettingsManager.getValue(SettingsManager.KEY_SWITCH_CAMERA);
        if (switchValue != null && !switchValue.equals("-1") ) {
            return SWITCH_MODE;
        }
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value != null && value.equals(SettingsManager.SCENE_MODE_DUAL_STRING)) return DUAL_MODE;
        value = mSettingsManager.getValue(SettingsManager.KEY_MONO_ONLY);
        if (value == null || !value.equals("on")) return BAYER_MODE;
        return MONO_MODE;
    }

    private boolean isClearSightOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_CLEARSIGHT);
        if (value == null) return false;
        return isBackCamera() && getCameraMode() == DUAL_MODE && value.equals("on");
    }

    private boolean isBsgcDetecionOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_BSGC_DETECTION);
        if (value == null) return false;
        return  value.equals("enable");
    }

    private boolean isRawCaptureOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SAVERAW);
        if (value == null) return  false;
        return value.equals("enable");
    }

    private boolean isMpoOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MPO);
        if (value == null) return false;
        return isBackCamera() && getCameraMode() == DUAL_MODE && value.equals("on");
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

    public LocationManager getLocationManager() {
        return mLocationManager;
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
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }

        mNamedImages = new NamedImages();
        mGraphViewR = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_r);
        mGraphViewGR = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gr);
        mGraphViewGB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gb);
        mGraphViewB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_b);
        mGraphViewR.setDataSection(0,256);
        mGraphViewGR.setDataSection(256,512);
        mGraphViewGB.setDataSection(512,768);
        mGraphViewB.setDataSection(768,1024);
        if (mGraphViewR != null){
            mGraphViewR.setCaptureModuleObject(this);
        }
        if (mGraphViewGR != null){
            mGraphViewGR.setCaptureModuleObject(this);
        }
        if (mGraphViewGB != null){
            mGraphViewGB.setCaptureModuleObject(this);
        }
        if (mGraphViewB != null){
            mGraphViewB.setCaptureModuleObject(this);
        }
        mFirstTimeInitialized = true;
    }

    private void initializeSecondTime() {
        // Start location update if needed.
        boolean recordLocation = getRecordLocation();
        mLocationManager.recordLocation(recordLocation);
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(this);
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }
        mNamedImages = new NamedImages();
    }

    public ArrayList<ImageFilter> getFrameFilters() {
        if(mFrameProcessor == null) {
            return new ArrayList<ImageFilter>();
        } else {
            return mFrameProcessor.getFrameFilters();
        }
    }

    private void applyFocusDistance(CaptureRequest.Builder builder, String value) {
        if (value == null) return;
        float valueF = Float.valueOf(value);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, valueF);
    }

    private void createSessions() {
        if (mPaused || !mCamerasOpened ) return;
        if (isBackCamera()) {
            switch (getCameraMode()) {
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
                case SWITCH_MODE:
                    createSession(SWITCH_ID);
                    break;
            }
        } else {
            int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
            createSession(cameraId);
        }
    }

    private CaptureRequest.Builder getRequestBuilder(int id) throws CameraAccessException {
        CaptureRequest.Builder builder;
        if(mPostProcessor.isZSLEnabled() && id == getMainCameraId()) {
            builder = mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        } else {
            builder = mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }
        return builder;
    }

    private void waitForPreviewSurfaceReady() {
        try {
            if (!mSurfaceReady) {
                if (!mSurfaceReadyLock.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
                    Log.d(TAG, "Time out waiting for surface.");
                    throw new RuntimeException("Time out waiting for surface.");
                }
                mSurfaceReadyLock.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updatePreviewSurfaceReadyState(boolean rdy) {
        if (rdy != mSurfaceReady) {
            if (rdy) {
                Log.i(TAG, "Preview Surface is ready!");
                mSurfaceReadyLock.release();
                mSurfaceReady = true;
            } else {
                try {
                    Log.i(TAG, "Preview Surface is not ready!");
                    mSurfaceReady = false;
                    mSurfaceReadyLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void createSession(final int id) {
        if (mPaused || !mCameraOpened[id]) return;
        Log.d(TAG, "createSession " + id);
        List<Surface> list = new LinkedList<Surface>();
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder[id] = getRequestBuilder(id);
            mPreviewRequestBuilder[id].setTag(id);

            CameraCaptureSession.StateCallback captureSessionCallback =
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mPaused || null == mCameraDevice[id]) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession[id] = cameraCaptureSession;
                            if(id == getMainCameraId()) {
                                mCurrentSession = cameraCaptureSession;
                            }
                            initializePreviewConfiguration(id);
                            setDisplayOrientation();
                            updateFaceDetection();
                            try {
                                if (isBackCamera() && getCameraMode() == DUAL_MODE) {
                                    linkBayerMono(id);
                                    mIsLinked = true;
                                }
                                // Finally, we start displaying the camera preview.
                                // for cases where we are in dual mode with mono preview off,
                                // don't set repeating request for mono
                                if(id == MONO_ID && !canStartMonoPreview()
                                        && getCameraMode() == DUAL_MODE) {
                                    mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                                } else {
                                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                                }

                                if (isClearSightOn()) {
                                    ClearSightImageProcessor.getInstance().onCaptureSessionConfigured(id == BAYER_ID, cameraCaptureSession);
                                } else if (mChosenImageFormat == ImageFormat.PRIVATE && id == getMainCameraId()) {
                                    mPostProcessor.onSessionConfigured(mCameraDevice[id], mCaptureSession[id]);
                                }

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            } catch(IllegalStateException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "cameracapturesession - onConfigureFailed "+ id);
                            if (mActivity.isFinishing()) {
                                return;
                            }
                            new AlertDialog.Builder(mActivity)
                                    .setTitle("Camera Initialization Failed")
                                    .setMessage("Closing SnapdragonCamera")
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            closeCamera();
                                            mActivity.finish();
                                        }
                                    })
                                    .setCancelable(false)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                        }

                        @Override
                        public void onClosed(CameraCaptureSession session) {
                            Log.d(TAG, "cameracapturesession - onClosed");
                        }
                    };
            waitForPreviewSurfaceReady();
            Surface surface = getPreviewSurfaceForSession(id);

            if(id == getMainCameraId()) {
                mFrameProcessor.setOutputSurface(surface);
            }

            if(isClearSightOn()) {
                mPreviewRequestBuilder[id].addTarget(surface);
                list.add(surface);
                ClearSightImageProcessor.getInstance().createCaptureSession(
                        id == BAYER_ID, mCameraDevice[id], list, captureSessionCallback);
            } else if (id == getMainCameraId()) {
                if(mFrameProcessor.isFrameFilterEnabled()) {
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            mUI.getSurfaceHolder().setFixedSize(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                        }
                    });
                }
                List<Surface> surfaces = mFrameProcessor.getInputSurfaces();
                for(Surface surs : surfaces) {
                    mPreviewRequestBuilder[id].addTarget(surs);
                    list.add(surs);
                }
                list.add(mImageReader[id].getSurface());
                if (mSaveRaw) {
                    list.add(mRawImageReader[id].getSurface());
                }
                if(mChosenImageFormat == ImageFormat.YUV_420_888 || mChosenImageFormat == ImageFormat.PRIVATE) {
                    if (mPostProcessor.isZSLEnabled()) {
                        mPreviewRequestBuilder[id].addTarget(mImageReader[id].getSurface());
                        list.add(mPostProcessor.getZSLReprocessImageReader().getSurface());
                        if (mSaveRaw) {
                            mPreviewRequestBuilder[id].addTarget(mRawImageReader[id].getSurface());
                        }
                        mCameraDevice[id].createReprocessableCaptureSession(new InputConfiguration(mImageReader[id].getWidth(),
                                mImageReader[id].getHeight(), mImageReader[id].getImageFormat()), list, captureSessionCallback, null);
                    } else {
                        mCameraDevice[id].createCaptureSession(list, captureSessionCallback, null);
                    }
                } else {
                    mCameraDevice[id].createCaptureSession(list, captureSessionCallback, null);
                }
            } else {
                mPreviewRequestBuilder[id].addTarget(surface);
                list.add(surface);
                list.add(mImageReader[id].getSurface());
                // Here, we create a CameraCaptureSession for camera preview.
                mCameraDevice[id].createCaptureSession(list, captureSessionCallback, null);
            }
        } catch (CameraAccessException e) {
        }
    }

    public void setAFModeToPreview(int id, int afMode) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "setAFModeToPreview " + afMode);
        }
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_MODE, afMode);
        applyAFRegions(mPreviewRequestBuilder[id], id);
        applyAERegions(mPreviewRequestBuilder[id], id);
        mPreviewRequestBuilder[id].setTag(id);
        try {
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void setFlashModeToPreview(int id, boolean isFlashOn) {
        if (DEBUG) {
            Log.d(TAG, "setFlashModeToPreview " + isFlashOn);
        }
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        if (isFlashOn) {
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            mPreviewRequestBuilder[id].set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
        } else {
            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder[id].set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
        applyAFRegions(mPreviewRequestBuilder[id], id);
        applyAERegions(mPreviewRequestBuilder[id], id);
        mPreviewRequestBuilder[id].setTag(id);
        try {
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void setFocusDistanceToPreview(int id, float fd) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        mPreviewRequestBuilder[id].set(CaptureRequest.LENS_FOCUS_DISTANCE, fd);
        mPreviewRequestBuilder[id].setTag(id);
        try {
            if (id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void reinit() {
        mSettingsManager.init();
    }

    public boolean isRefocus() {
        return mIsRefocus;
    }

    public boolean getRecordLocation() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_RECORD_LOCATION);
        if (value == null) value = RecordLocationPreference.VALUE_NONE;
        return RecordLocationPreference.VALUE_ON.equals(value);
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mSettingsManager.init();
        mFirstPreviewLoaded = false;
        Log.d(TAG, "init");
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mCameraOpened[i] = false;
            mTakingPicture[i] = false;
        }
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }

        mPostProcessor = new PostProcessor(mActivity, this);
        mFrameProcessor = new FrameProcessor(mActivity, this);

        mContentResolver = mActivity.getContentResolver();
        initModeByIntent();
        mUI = new CaptureUI(activity, this, parent);
        mUI.initializeControlByIntent();

        mFocusStateListener = new FocusStateListener(mUI);
        mLocationManager = new LocationManager(mActivity, this);
    }

    private void initModeByIntent() {
        String action = mActivity.getIntent().getAction();
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)) {
            mIntentMode = INTENT_MODE_CAPTURE;
        } else if (CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mIntentMode = INTENT_MODE_CAPTURE_SECURE;
        } else if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            mIntentMode = INTENT_MODE_VIDEO;
        }
        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    public boolean isQuickCapture() {
        return mQuickCapture;
    }

    public void setJpegImageData(byte[] data) {
        mJpegImageData = data;
    }

    public void showCapturedReview(final byte[] jpegData, final int orientation) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.showCapturedImageForReview(jpegData, orientation);
            }
        });
    }


    public int getCurrentIntentMode() {
        return mIntentMode;
    }

    public void cancelCapture() {
        mActivity.finish();
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.d(TAG, "takePicture");
        mUI.enableShutter(false);
        if (mSettingsManager.isZSLInHALEnabled()) {
            takeZSLPictureInHAL();
        }else {
            if (isBackCamera()) {
                switch (getCameraMode()) {
                    case DUAL_MODE:
                        lockFocus(BAYER_ID);
                        lockFocus(MONO_ID);
                        break;
                    case BAYER_MODE:
                        if(takeZSLPicture(BAYER_ID)) {
                            return;
                        }
                        lockFocus(BAYER_ID);
                        break;
                    case MONO_MODE:
                        lockFocus(MONO_ID);
                        break;
                    case SWITCH_MODE:
                        lockFocus(SWITCH_ID);
                        break;
                }
            } else {
                int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
                if(takeZSLPicture(cameraId)) {
                    return;
                }
                lockFocus(cameraId);
            }
        }
    }

    private boolean takeZSLPicture(int cameraId) {
        if(mPostProcessor.isZSLEnabled() && mPostProcessor.takeZSLPicture()) {
            checkAndPlayShutterSound(getMainCameraId());
            mUI.enableShutter(true);
            return true;
        }
        return false;
    }

    private void takeZSLPictureInHAL() {
        Log.d(TAG, "takeHALZSLPicture");
        int cameraId = BAYER_ID;
        if (isBackCamera()) {
            switch (getCameraMode()) {
                case DUAL_MODE:
                    captureStillPicture(BAYER_ID);
                    captureStillPicture(MONO_ID);
                    return;
                case BAYER_MODE:
                    cameraId = BAYER_ID;
                    break;
                case MONO_MODE:
                    cameraId = MONO_ID;
                    break;
                case SWITCH_MODE:
                    cameraId = SWITCH_ID;
                    break;
            }
        } else {
            cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
        }
        captureStillPicture(cameraId);
    }

    public boolean isLongShotActive() {
        return mLongshotActive;
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus(int id) {
        if (mActivity == null || mCameraDevice[id] == null
                || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            mUI.enableShutter(true);
            warningToast("Camera is not ready yet to take a picture.");
            return;
        }
        Log.d(TAG, "lockFocus " + id);

        try {
            // start repeating request to get AF/AE state updates
            // for mono when mono preview is off.
            if(id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                // for longshot flash, need to re-configure the preview flash mode.
                if (mLongshotActive && isFlashOn(id)) {
                    mCaptureSession[id].stopRepeating();
                    applyFlash(mPreviewRequestBuilder[id], id);
                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mTakingPicture[id] = true;
        if (mState[id] == STATE_WAITING_TOUCH_FOCUS) {
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[id]);
            mState[id] = STATE_WAITING_AF_LOCK;
            mLockRequestHashCode[id] = 0;
            return;
        }

        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);

            applySettingsForLockFocus(builder, id);
            CaptureRequest request = builder.build();
            mLockRequestHashCode[id] = request.hashCode();
            mState[id] = STATE_WAITING_AF_LOCK;
            mCaptureSession[id].capture(request, mCaptureCallback, mCameraHandler);
            if(mHiston) {
                updateGraghViewVisibility(View.INVISIBLE);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void autoFocusTrigger(int id) {
        if (DEBUG) {
            Log.d(TAG, "autoFocusTrigger " + id);
        }
        if (null == mActivity || null == mCameraDevice[id]
                || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            warningToast("Camera is not ready yet to take a picture.");
            return;
        }
        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);

            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
            applySettingsForAutoFocus(builder, id);
            mState[id] = STATE_WAITING_TOUCH_FOCUS;
            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            setAFModeToPreview(id, mControlAFMode);
            Message message = mCameraHandler.obtainMessage(CANCEL_TOUCH_FOCUS, mCameraId[id]);
            mCameraHandler.sendMessageDelayed(message, CANCEL_TOUCH_FOCUS_DELAY);
        } catch (CameraAccessException | IllegalStateException e) {
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

    public PostProcessor getPostProcessor() {
        return mPostProcessor;
    }

    private void captureStillPicture(final int id) {
        Log.d(TAG, "captureStillPicture " + id);
        mJpegImageData = null;
        mIsRefocus = false;
        try {
            if (null == mActivity || null == mCameraDevice[id]) {
                warningToast("Camera is not ready yet to take a picture.");
                return;
            }

            CaptureRequest.Builder captureBuilder =
                    mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            if(mSettingsManager.isZSLInHALEnabled()) {
                captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
            }else{
                captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false);
            }

            applySettingsForJpegInformation(captureBuilder, id);
            if (!mIsSupportedQcfa) {
                addPreviewSurface(captureBuilder, null, id);
            }
            VendorTagUtil.setCdsMode(captureBuilder, 2);// CDS 0-OFF, 1-ON, 2-AUTO
            applySettingsForCapture(captureBuilder, id);

            if(isClearSightOn()) {
                captureStillPictureForClearSight(id);
            } else if(id == getMainCameraId() && mPostProcessor.isFilterOn()) { // Case of post filtering
                captureStillPictureForFilter(captureBuilder, id);
            } else {
                captureBuilder.addTarget(mImageReader[id].getSurface());
                if (mSaveRaw) {
                    captureBuilder.addTarget(mRawImageReader[id].getSurface());
                }
                if (!mIsSupportedQcfa) {
                    mCaptureSession[id].stopRepeating();
                }
                if (mLongshotActive) {
                    captureStillPictureForLongshot(captureBuilder, id);
                } else {
                    captureStillPictureForCommon(captureBuilder, id);
                }
            }
        } catch (CameraAccessException e) {
            Log.d(TAG, "Capture still picture has failed");
            e.printStackTrace();
        }
    }

    private void captureStillPictureForClearSight(int id) throws CameraAccessException{
        CaptureRequest.Builder captureBuilder =
                ClearSightImageProcessor.getInstance().createCaptureRequest(mCameraDevice[id]);

        if(mSettingsManager.isZSLInHALEnabled()) {
            captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
        }else{
            captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false);
        }
        
        applySettingsForJpegInformation(captureBuilder, id);
        addPreviewSurface(captureBuilder, null, id);
        VendorTagUtil.setCdsMode(captureBuilder, 2); // CDS 0-OFF, 1-ON, 2-AUTO
        applySettingsForCapture(captureBuilder, id);
        applySettingsForLockExposure(captureBuilder, id);
        checkAndPlayShutterSound(id);
        ClearSightImageProcessor.getInstance().capture(
                id==BAYER_ID, mCaptureSession[id], captureBuilder, mCaptureCallbackHandler);
    }

    private void captureStillPictureForFilter(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        applySettingsForLockExposure(captureBuilder, id);
        checkAndPlayShutterSound(id);
        mCaptureSession[id].stopRepeating();
        captureBuilder.addTarget(mImageReader[id].getSurface());
        if (mSaveRaw) {
            captureBuilder.addTarget(mRawImageReader[id].getSurface());
        }
        mPostProcessor.onStartCapturing();
        if(mPostProcessor.isManualMode()) {
            mPostProcessor.manualCapture(captureBuilder, mCaptureSession[id], mCaptureCallbackHandler);
        } else {
            List<CaptureRequest> captureList = mPostProcessor.setRequiredImages(captureBuilder);
            mCaptureSession[id].captureBurst(captureList, mPostProcessor.getCaptureCallback(), mCaptureCallbackHandler);
        }
    }

    private void captureStillPictureForLongshot(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        Log.d(TAG, "captureStillPictureForLongshot " + id);
        List<CaptureRequest> burstList = new ArrayList<>();
        for (int i = 0; i < PersistUtil.getLongshotShotLimit(); i++) {
            burstList.add(captureBuilder.build());
        }
        mCaptureSession[id].captureBurst(burstList, new
                CameraCaptureSession.CaptureCallback() {

                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session,
                                                   CaptureRequest request,
                                                   TotalCaptureResult result) {
                        Log.d(TAG, "captureStillPictureForLongshot onCaptureCompleted: " + id);
                        if (mLongshotActive) {
                            checkAndPlayShutterSound(id);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mUI.doShutterAnimation();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCaptureFailed(CameraCaptureSession session,
                                                CaptureRequest request,
                                                CaptureFailure result) {
                        Log.d(TAG, "captureStillPictureForLongshot onCaptureFailed: " + id);
                        if (mLongshotActive) {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mUI.doShutterAnimation();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                            sequenceId, long frameNumber) {
                        Log.d(TAG, "captureStillPictureForLongshot onCaptureSequenceCompleted: " + id);
                        mLongshotActive = false;
                        unlockFocus(id);
                    }
                }, mCaptureCallbackHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.enableVideo(false);
            }
        });
    }

    private void captureStillPictureForCommon(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        checkAndPlayShutterSound(id);
        if(isMpoOn()) {
            mCaptureStartTime = System.currentTimeMillis();
            mMpoSaveHandler.obtainMessage(MpoSaveHandler.MSG_CONFIGURE,
                    Long.valueOf(mCaptureStartTime)).sendToTarget();
        }
        if(mChosenImageFormat == ImageFormat.YUV_420_888 || mChosenImageFormat == ImageFormat.PRIVATE) { // Case of ZSL, FrameFilter, SelfieMirror
            mPostProcessor.onStartCapturing();
            mCaptureSession[id].capture(captureBuilder.build(), mPostProcessor.getCaptureCallback(), mCaptureCallbackHandler);
        } else {
            mCaptureSession[id].capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureCompleted: " + id);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session,
                                            CaptureRequest request,
                                            CaptureFailure result) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureFailed: " + id);
                }

                @Override
                public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                        sequenceId, long frameNumber) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureSequenceCompleted: " + id);
                    unlockFocus(id);
                }
            }, mCaptureCallbackHandler);
        }
    }

    private void captureVideoSnapshot(final int id) {
        Log.d(TAG, "captureVideoSnapshot " + id);
        try {
            if (null == mActivity || null == mCameraDevice[id] || mCurrentSession == null) {
                warningToast("Camera is not ready yet to take a video snapshot.");
                return;
            }
            checkAndPlayShutterSound(id);
            CaptureRequest.Builder captureBuilder =
                    mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.getJpegRotation(id, mOrientation));
            captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, mVideoSnapshotThumbSize);
            captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, (byte)80);
            applyVideoSnapshot(captureBuilder, id);
            applyZoom(captureBuilder, id);

            captureBuilder.addTarget(mVideoSnapshotImageReader.getSurface());

            mCurrentSession.capture(captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {

                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session,
                                                       CaptureRequest request,
                                                       TotalCaptureResult result) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureCompleted: " + id);
                        }

                        @Override
                        public void onCaptureFailed(CameraCaptureSession session,
                                                    CaptureRequest request,
                                                    CaptureFailure result) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureFailed: " + id);
                        }

                        @Override
                        public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                                sequenceId, long frameNumber) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureSequenceCompleted: " + id);
                        }
                    }, mCaptureCallbackHandler);
        } catch (CameraAccessException e) {
            Log.d(TAG, "captureVideoSnapshot failed");
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence(int id) {
        Log.d(TAG, "runPrecaptureSequence: " + id);
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);
            applySettingsForPrecapture(builder, id);
            CaptureRequest request = builder.build();
            mPrecaptureRequestHashCode[id] = request.hashCode();

            mState[id] = STATE_WAITING_PRECAPTURE;
            mCaptureSession[id].capture(request, mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public CameraCharacteristics getMainCameraCharacteristics() {
        return mMainCameraCharacteristics;
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int imageFormat) {
        Log.d(TAG, "setUpCameraOutputs");
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (isInMode(i))
                    mCameraIdList.add(i);
                if(i == getMainCameraId()) {
                    mBayerCameraRegion = characteristics.get(CameraCharacteristics
                            .SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    mMainCameraCharacteristics = characteristics;
                }
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                mCameraId[i] = cameraId;

                if (isClearSightOn()) {
                    if(i == getMainCameraId()) {
//                        ClearSightImageProcessor.getInstance().init(map, mPictureSize.getWidth(),
//                                mPictureSize.getHeight(), mActivity, mOnMediaSavedListener);
                        ClearSightImageProcessor.getInstance().init(map, mActivity,
                                mOnMediaSavedListener);
                        ClearSightImageProcessor.getInstance().setCallback(this);
                    }
                } else {
                    if ((imageFormat == ImageFormat.YUV_420_888 || imageFormat == ImageFormat.PRIVATE)
                            && i == getMainCameraId()) {
                        if(mPostProcessor.isZSLEnabled()) {
                            mImageReader[i] = ImageReader.newInstance(mSupportedMaxPictureSize.getWidth(),
                                    mSupportedMaxPictureSize.getHeight(), imageFormat, mPostProcessor.getMaxRequiredImageNum());
                        } else {
                            mImageReader[i] = ImageReader.newInstance(mPictureSize.getWidth(),
                                    mPictureSize.getHeight(), imageFormat, mPostProcessor.getMaxRequiredImageNum());
                        }
                        if (mSaveRaw) {
                            mRawImageReader[i] = ImageReader.newInstance(mSupportedRawPictureSize.getWidth(),
                                    mSupportedRawPictureSize.getHeight(), ImageFormat.RAW10, mPostProcessor.getMaxRequiredImageNum());
                            mPostProcessor.setRawImageReader(mRawImageReader[i]);
                        }
                        mImageReader[i].setOnImageAvailableListener(mPostProcessor.getImageHandler(), mImageAvailableHandler);
                        mPostProcessor.onImageReaderReady(mImageReader[i], mSupportedMaxPictureSize, mPictureSize);
                    } else if (i == getMainCameraId()) {
                        mImageReader[i] = ImageReader.newInstance(mPictureSize.getWidth(),
                                mPictureSize.getHeight(), imageFormat, PersistUtil.getLongshotShotLimit());

                        ImageAvailableListener listener = new ImageAvailableListener(i) {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                if (mIsSupportedQcfa) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mUI.enableShutter(true);
                                        }
                                    });
                                }
                                Log.d(TAG, "image available for cam: " + mCamId);
                                Image image = reader.acquireNextImage();

                                if (isMpoOn()) {
                                    mMpoSaveHandler.obtainMessage(
                                            MpoSaveHandler.MSG_NEW_IMG, mCamId, 0, image).sendToTarget();
                                } else {
                                    mCaptureStartTime = System.currentTimeMillis();
                                    mNamedImages.nameNewImage(mCaptureStartTime);
                                    NamedEntity name = mNamedImages.getNextNameEntity();
                                    String title = (name == null) ? null : name.title;
                                    long date = (name == null) ? -1 : name.date;

                                    byte[] bytes = getJpegData(image);

                                    if (image.getFormat() == ImageFormat.RAW10) {
                                        mActivity.getMediaSaveService().addRawImage(bytes, title,
                                                "raw");
                                    } else {
                                        ExifInterface exif = Exif.getExif(bytes);
                                        int orientation = Exif.getOrientation(exif);

                                        if (mIntentMode != CaptureModule.INTENT_MODE_NORMAL) {
                                            mJpegImageData = bytes;
                                            if (!mQuickCapture) {
                                                showCapturedReview(bytes, orientation);
                                            } else {
                                                onCaptureDone();
                                            }
                                        } else {
                                            mActivity.getMediaSaveService().addImage(bytes, title, date,
                                                    null, image.getWidth(), image.getHeight(), orientation, null,
                                                    mOnMediaSavedListener, mContentResolver, "jpeg");

                                            if (mLongshotActive) {
                                                mLastJpegData = bytes;
                                            } else {
                                                mActivity.updateThumbnail(bytes);
                                            }
                                        }
                                        image.close();
                                    }
                                }
                            }
                        };
                        mImageReader[i].setOnImageAvailableListener(listener, mImageAvailableHandler);

                        if (mSaveRaw) {
                            mRawImageReader[i] = ImageReader.newInstance(mSupportedRawPictureSize.getWidth(),
                                    mSupportedRawPictureSize.getHeight(), ImageFormat.RAW10, PersistUtil.getLongshotShotLimit());
                            mRawImageReader[i].setOnImageAvailableListener(listener, mImageAvailableHandler);
                        }
                    }
                }
            }
            mMediaRecorder = new MediaRecorder();
            mAutoFocusRegionSupported = mSettingsManager.isAutoFocusRegionSupported(mCameraIdList);
            mAutoExposureRegionSupported = mSettingsManager.isAutoExposureRegionSupported(mCameraIdList);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createVideoSnapshotImageReader() {
        if (mVideoSnapshotImageReader != null) {
            mVideoSnapshotImageReader.close();
        }
        mVideoSnapshotImageReader = ImageReader.newInstance(mVideoSnapshotSize.getWidth(),
                mVideoSnapshotSize.getHeight(), ImageFormat.JPEG, 2);
        mVideoSnapshotImageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireNextImage();
                        mCaptureStartTime = System.currentTimeMillis();
                        mNamedImages.nameNewImage(mCaptureStartTime);
                        NamedEntity name = mNamedImages.getNextNameEntity();
                        String title = (name == null) ? null : name.title;
                        long date = (name == null) ? -1 : name.date;

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        ExifInterface exif = Exif.getExif(bytes);
                        int orientation = Exif.getOrientation(exif);

                        mActivity.getMediaSaveService().addImage(bytes, title, date,
                                null, image.getWidth(), image.getHeight(), orientation, null,
                                mOnMediaSavedListener, mContentResolver, "jpeg");

                        mActivity.updateThumbnail(bytes);
                        image.close();
                    }
                }, mImageAvailableHandler);
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    public void unlockFocus(int id) {
        Log.d(TAG, "unlockFocus " + id);
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);

            applySettingsForUnlockFocus(builder, id);
            mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            mState[id] = STATE_PREVIEW;
            if (id == getMainCameraId()) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.clearFocus();
                    }
                });
            }
            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
            applyFlash(mPreviewRequestBuilder[id], id);
            applySettingsForUnlockExposure(mPreviewRequestBuilder[id], id);
            setAFModeToPreview(id, mControlAFMode);
            mTakingPicture[id] = false;
            if (id == getMainCameraId()) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.stopSelfieFlash();
                        if (!mIsSupportedQcfa) {
                            mUI.enableShutter(true);
                        }
                        mUI.enableVideo(true);
                    }
                });
            }
        } catch (NullPointerException | IllegalStateException | CameraAccessException e) {
            Log.w(TAG, "Session is already closed");
        }
    }

    private Size parsePictureSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    private void closeProcessors() {
        if(mPostProcessor != null) {
            mPostProcessor.onClose();
        }

        if(mFrameProcessor != null) {
            mFrameProcessor.onClose();
        }
    }

    public boolean isAllSessionClosed() {
        for (int i = MAX_NUM_CAM - 1; i >= 0; i--) {
            if (mCaptureSession[i] != null) {
                return false;
            }
        }
        return true;
    }

    private void closeSessions() {
        for (int i = MAX_NUM_CAM-1; i >= 0; i--) {
            if (null != mCaptureSession[i]) {
                if (mCamerasOpened) {
                    try {
                        mCaptureSession[i].capture(mPreviewRequestBuilder[i].build(), null,
                                mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
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
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        Log.d(TAG, "closeCamera");

        closeProcessors();

        /* no need to set this in the callback and handle asynchronously. This is the same
        reason as why we release the semaphore here, not in camera close callback function
        as we don't have to protect the case where camera open() gets called during camera
        close(). The low level framework/HAL handles the synchronization for open()
        happens after close() */

        try {
            // Close camera starting with AUX first
            for (int i = MAX_NUM_CAM-1; i >= 0; i--) {
                if (null != mCameraDevice[i]) {
                    if (!mCameraOpenCloseLock.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
                        Log.d(TAG, "Time out waiting to lock camera closing.");
                        throw new RuntimeException("Time out waiting to lock camera closing");
                    }
                    Log.d(TAG, "Closing camera: " + mCameraDevice[i].getId());
                    mCameraDevice[i].close();
                    mCameraDevice[i] = null;
                    mCameraOpened[i] = false;
                    mCaptureSession[i] = null;
                }

                if (null != mImageReader[i]) {
                    mImageReader[i].close();
                    mImageReader[i] = null;
                }
            }

            mIsLinked = false;

            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }

            if (null != mVideoSnapshotImageReader) {
                mVideoSnapshotImageReader.close();
                mVideoSnapshotImageReader = null;
            }
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Lock the exposure for capture
     */
    private void lockExposure(int id) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        Log.d(TAG, "lockExposure: " + id);
        try {
            applySettingsForLockExposure(mPreviewRequestBuilder[id], id);
            mState[id] = STATE_WAITING_AE_LOCK;
            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                    mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void applySettingsForLockFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyAERegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForCapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        applyJpegQuality(builder);
        applyCommonSettings(builder, id);
        applyFlash(builder, id);
    }

    private void applySettingsForPrecapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        applyCommonSettings(builder, id);

        // For long shot, torch mode is used
        if (!mLongshotActive)
            applyFlash(builder, id);
    }

    private void applySettingsForLockExposure(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
    }

    private void applySettingsForUnlockExposure(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.FALSE);
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
        applyAERegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForJpegInformation(CaptureRequest.Builder builder, int id) {
        Location location = mLocationManager.getCurrentLocation();
        if(location != null) {
            // make copy so that we don't alter the saved location since we may re-use it
            location = new Location(location);
            // workaround for Google bug. Need to convert timestamp from ms -> sec
            location.setTime(location.getTime()/1000);
            builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            Log.d(TAG, "gps: " + location.toString());
        } else {
            Log.d(TAG, "no location - getRecordLocation: " + getRecordLocation());
        }
        builder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.getJpegRotation(id, mOrientation));
        builder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, mPictureThumbSize);
        builder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, (byte)80);
    }

    private void applyVideoSnapshot(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        applyColorEffect(builder);
        applyVideoFlash(builder);
    }

    private void applyCommonSettings(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
        applyFaceDetection(builder);
        applyWhiteBalance(builder);
        applyExposure(builder);
        applyIso(builder);
        applyColorEffect(builder);
        applySceneMode(builder);
        applyZoom(builder, id);
        applyInstantAEC(builder);
        applySaturationLevel(builder);
        applyAntiBandingLevel(builder);
        applyHistogram(builder);
        enableBokeh(builder);
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
        mMpoSaveThread = new HandlerThread("MpoSaveHandler");
        mMpoSaveThread.start();

        mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
        mImageAvailableHandler = new Handler(mImageAvailableThread.getLooper());
        mCaptureCallbackHandler = new Handler(mCaptureCallbackThread.getLooper());
        mMpoSaveHandler = new MpoSaveHandler(mMpoSaveThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        mImageAvailableThread.quitSafely();
        mCaptureCallbackThread.quitSafely();
        mMpoSaveThread.quitSafely();

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
        try {
            mMpoSaveThread.join();
            mMpoSaveThread = null;
            mMpoSaveHandler = null;
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
        cancelTouchFocus();
        mPaused = true;
        mToast = null;
        mUI.onPause();
        if (mIsRecordingVideo) {
            stopRecordingVideo(getMainCameraId());
        }
        if (mSound != null) {
            mSound.release();
            mSound = null;
        }
        if (selfieThread != null) {
            selfieThread.interrupt();
        }
        resetScreenOn();
        mUI.stopSelfieFlash();
    }

    @Override
    public void onPauseAfterSuper() {
        Log.d(TAG, "onPause");
        if (mLocationManager != null) mLocationManager.recordLocation(false);
        if(isClearSightOn()) {
            ClearSightImageProcessor.getInstance().close();
        }
        closeCamera();
        mUI.showPreviewCover();
        mUI.hideSurfaceView();
        mFirstPreviewLoaded = false;
        stopBackgroundThread();
        mLastJpegData = null;
        setProModeVisible();
        setBokehModeVisible();
        mJpegImageData = null;
        closeVideoFileDescriptor();
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mCameraOpened[i] = false;
            mTakingPicture[i] = false;
        }
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }
        mLongshotActive = false;
        updateZoom();
        updatePreviewSurfaceReadyState(false);
    }

    private void cancelTouchFocus() {
        if (getCameraMode() == DUAL_MODE) {
            if(mState[BAYER_ID] == STATE_WAITING_TOUCH_FOCUS) {
                cancelTouchFocus(BAYER_ID);
            } else if (mState[MONO_ID] == STATE_WAITING_TOUCH_FOCUS) {
                cancelTouchFocus(MONO_ID);
            }
        } else {
            if (mState[getMainCameraId()] == STATE_WAITING_TOUCH_FOCUS) {
                cancelTouchFocus(getMainCameraId());
            }
        }
    }

    private ArrayList<Integer> getFrameProcFilterId() {
        ArrayList<Integer> filters = new ArrayList<Integer>();

        String scene = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(scene != null && !scene.equalsIgnoreCase("0")) {
            filters.add(FrameProcessor.FILTER_MAKEUP);
        }
        if(isTrackingFocusSettingOn()) {
            filters.add(FrameProcessor.LISTENER_TRACKING_FOCUS);
        }

        return filters;
    }

    public boolean isTrackingFocusSettingOn() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        try {
            int mode = Integer.parseInt(scene);
            if (mode == SettingsManager.SCENE_MODE_TRACKINGFOCUS_INT) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void setRefocusLastTaken(final boolean value) {
        mIsRefocus = value;
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mUI.showRefocusToast(value);
            }
        });
    }

    private int getPostProcFilterId(int mode) {
        if (mode == SettingsManager.SCENE_MODE_OPTIZOOM_INT) {
            return PostProcessor.FILTER_OPTIZOOM;
        } else if (mode == SettingsManager.SCENE_MODE_NIGHT_INT && StillmoreFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_STILLMORE;
        } else if (mode == SettingsManager.SCENE_MODE_CHROMAFLASH_INT && ChromaflashFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_CHROMAFLASH;
        } else if (mode == SettingsManager.SCENE_MODE_BLURBUSTER_INT && BlurbusterFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_BLURBUSTER;
        } else if (mode == SettingsManager.SCENE_MODE_UBIFOCUS_INT && UbifocusFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_UBIFOCUS;
        } else if (mode == SettingsManager.SCENE_MODE_SHARPSHOOTER_INT && SharpshooterFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_SHARPSHOOTER;
        } else if (mode == SettingsManager.SCENE_MODE_BESTPICTURE_INT) {
            return PostProcessor.FILTER_BESTPICTURE;
        }
        return PostProcessor.FILTER_NONE;
    }

    private void initializeValues() {
        updatePictureSize();
        updateVideoSize();
        updateVideoSnapshotSize();
        updateTimeLapseSetting();
        estimateJpegFileSize();
        updateMaxVideoDuration();
    }

    private void updatePreviewSize() {
        int width = mPreviewSize.getWidth();
        int height = mPreviewSize.getHeight();

        String makeup = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        boolean makeupOn = makeup != null && !makeup.equals("0");
        if (makeupOn) {
            width = mVideoSize.getWidth();
            height = mVideoSize.getHeight();
        }

        Point previewSize = PersistUtil.getCameraPreviewSize();
        if (previewSize != null) {
            width = previewSize.x;
            height = previewSize.y;
        }

        Log.d(TAG, "updatePreviewSize final preview size = " + width + ", " + height);

        mPreviewSize = new Size(width, height);
        mUI.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
    }

    private void openProcessors() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        mIsSupportedQcfa = mSettingsManager.getQcfaPrefEnabled() &&
            mSettingsManager.getIsSupportedQcfa(getMainCameraId()) &&
            mPictureSize.toString().equals(mSettingsManager.getSupportedQcfaDimension(
                getMainCameraId()));
        boolean isFlashOn = false;
        boolean isMakeupOn = false;
        boolean isSelfieMirrorOn = false;
        if(mPostProcessor != null) {
            String selfieMirror = mSettingsManager.getValue(SettingsManager.KEY_SELFIEMIRROR);
            if(selfieMirror != null && selfieMirror.equalsIgnoreCase("on")) {
                isSelfieMirrorOn = true;
            }
            String makeup = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
            if(makeup != null && !makeup.equals("0")) {
                isMakeupOn = true;
            }
            String flashMode = mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE);
            if(flashMode != null && flashMode.equalsIgnoreCase("on")) {
                isFlashOn = true;
            }

            mSaveRaw = isRawCaptureOn();
            if (scene != null) {
                int mode = Integer.parseInt(scene);
                Log.d(TAG, "Chosen postproc filter id : " + getPostProcFilterId(mode));
                mPostProcessor.onOpen(getPostProcFilterId(mode), isFlashOn,
                        isTrackingFocusSettingOn(), isMakeupOn, isSelfieMirrorOn,
                        mSaveRaw, mIsSupportedQcfa);
            } else {
                mPostProcessor.onOpen(PostProcessor.FILTER_NONE, isFlashOn,
                        isTrackingFocusSettingOn(), isMakeupOn, isSelfieMirrorOn,
                        mSaveRaw, mIsSupportedQcfa);
            }
        }
        if(mFrameProcessor != null) {
            mFrameProcessor.onOpen(getFrameProcFilterId(), mPreviewSize);
        }

        if(mPostProcessor.isZSLEnabled()) {
            mChosenImageFormat = ImageFormat.PRIVATE;
        } else if(mPostProcessor.isFilterOn() || getFrameFilters().size() != 0 || mPostProcessor.isSelfieMirrorOn()) {
            mChosenImageFormat = ImageFormat.YUV_420_888;
        } else {
            mChosenImageFormat = ImageFormat.JPEG;
        }
        setUpCameraOutputs(mChosenImageFormat);

    }

    @Override
    public void onResumeAfterSuper() {
        Log.d(TAG, "onResume " + getCameraMode());
        reinit();
        initializeValues();
        updatePreviewSize();
        mCameraIdList = new ArrayList<>();

        if (mSound == null) {
            mSound = new MediaActionSound();
        }

        updateSaveStorageState();
        setDisplayOrientation();
        startBackgroundThread();
        openProcessors();
        Message msg = Message.obtain();
        msg.what = OPEN_CAMERA;
        if (isBackCamera()) {
            switch (getCameraMode()) {
                case DUAL_MODE:
                case BAYER_MODE:
                    msg.arg1 = BAYER_ID;
                    mCameraHandler.sendMessage(msg);
                    break;
                case MONO_MODE:
                    msg.arg1 = MONO_ID;
                    mCameraHandler.sendMessage(msg);
                    break;
                case SWITCH_MODE:
                    msg.arg1 = SWITCH_ID;
                    mCameraHandler.sendMessage(msg);
                    break;
            }
        } else {
            int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
            msg.arg1 = cameraId;
            mCameraHandler.sendMessage(msg);
        }
        mUI.showSurfaceView();
        if (!mFirstTimeInitialized) {
            initializeFirstTime();
        } else {
            initializeSecondTime();
        }
        mUI.reInitUI();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mActivity.updateStorageSpaceAndHint();
            }
        });
        mUI.enableShutter(true);
        mUI.enableVideo(true);
        setProModeVisible();
        setBokehModeVisible();

        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (Integer.parseInt(scene) != SettingsManager.SCENE_MODE_UBIFOCUS_INT) {
            setRefocusLastTaken(false);
        }
        if(isPanoSetting(scene)) {
            if (mIntentMode != CaptureModule.INTENT_MODE_NORMAL) {
                mSettingsManager.setValue(
                        SettingsManager.KEY_SCENE_MODE, ""+SettingsManager.SCENE_MODE_AUTO_INT);
                showToast("Pano Capture is not supported in this mode");
            } else {
                mActivity.onModuleSelected(ModuleSwitcher.PANOCAPTURE_MODULE_INDEX);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {
        if(mFrameProcessor != null){
            mFrameProcessor.onDestory();
        }
        mSettingsManager.unregisterListener(this);
        mSettingsManager.unregisterListener(mUI);
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
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (CameraUtil.volumeKeyShutterDisable(mActivity)) {
                    return false;
                }
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
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
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (mFirstTimeInitialized
                        && !CameraUtil.volumeKeyShutterDisable(mActivity)) {
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

    @Override
    public int onZoomChanged(int requestedZoom) {
        return 0;
    }

    @Override
    public void onZoomChanged(float requestedZoom) {
        mZoomValue = requestedZoom;

        if (isBackCamera()) {
            switch (getCameraMode()) {
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
                case SWITCH_MODE:
                    applyZoomAndUpdate(SWITCH_ID);
                    break;
            }
        } else {
            int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
            applyZoomAndUpdate(cameraId);
        }
        mUI.updateFaceViewCameraBound(mCropRegion[getMainCameraId()]);
    }

    private boolean isInMode(int cameraId) {
        if (isBackCamera()) {
            switch (getCameraMode()) {
                case DUAL_MODE:
                    return cameraId == BAYER_ID || cameraId == MONO_ID;
                case BAYER_MODE:
                    return cameraId == BAYER_ID;
                case MONO_MODE:
                    return cameraId == MONO_ID;
                case SWITCH_MODE:
                    return cameraId == SWITCH_ID;
            }
        } else if (SWITCH_ID != -1) {
            return cameraId == SWITCH_ID;
        } else {
            return cameraId == FRONT_ID;
        }
        return false;
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

    public void onRecordingDone(boolean valid) {
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
        if (mPaused || !mCamerasOpened || !mFirstTimeInitialized || !mAutoFocusRegionSupported
                || !mAutoExposureRegionSupported || !isTouchToFocusAllowed()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onSingleTapUp " + x + " " + y);
        }
        int[] newXY = {x, y};
        if (mUI.isOverControlRegion(newXY)) return;
        if (!mUI.isOverSurfaceView(newXY)) return;
        mUI.setFocusPosition(x, y);
        x = newXY[0];
        y = newXY[1];
        mUI.onFocusStarted();
        if (isBackCamera()) {
            switch (getCameraMode()) {
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
                case SWITCH_MODE:
                    triggerFocusAtPoint(x, y, SWITCH_ID);
                    break;
            }
        } else {
            int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
            triggerFocusAtPoint(x, y, cameraId);
        }
    }

    public int getMainCameraId() {
        if (isBackCamera()) {
            switch (getCameraMode()) {
                case DUAL_MODE:
                case BAYER_MODE:
                    return BAYER_ID;
                case MONO_MODE:
                    return MONO_ID;
                case SWITCH_MODE:
                    return SWITCH_ID;
            }
            return 0;
        } else {
            int cameraId = SWITCH_ID == -1? FRONT_ID : SWITCH_ID;
            return cameraId;
        }
    }

    public boolean isTakingPicture() {
        for (int i = 0; i < mTakingPicture.length; i++) {
            if (mTakingPicture[i]) return true;
        }
        return false;
    }

    private boolean isTouchToFocusAllowed() {
        if (isTakingPicture() || mIsRecordingVideo || isTouchAfEnabledSceneMode()) return false;
        return true;
    }

    private boolean isTouchAfEnabledSceneMode() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (scene == null) return false;
        int mode = Integer.parseInt(scene);
        if (mode != CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                && mode < SettingsManager.SCENE_MODE_CUSTOM_START)
            return true;
        return false;
    }

    private ExtendedFace[] getBsgcInfo(CaptureResult captureResult, int size) {
        ExtendedFace []extendedFaces = new ExtendedFace[size];
        byte[] blinkDetectedArray = captureResult.get(blinkDetected);
        byte[] blinkDegreesArray = captureResult.get(blinkDegree);
        int[] gazeDirectionArray = captureResult.get(gazeDirection);
        byte[] gazeAngleArray = captureResult.get(gazeAngle);
        byte[] smileDegreeArray = captureResult.get(smileDegree);
        byte[] smileConfidenceArray = captureResult.get(smileConfidence);
        for(int i=0;i<size;i++) {
            ExtendedFace tmp = new ExtendedFace(i);
            tmp.setBlinkDetected(blinkDetectedArray[i]);
            tmp.setBlinkDegree(blinkDegreesArray[2*i], blinkDegreesArray[2*i+1]);
            tmp.setGazeDirection(gazeDirectionArray[3*i], gazeDirectionArray[3*i+1], gazeDirectionArray[3*i+2]);
            tmp.setGazeAngle(gazeAngleArray[i]);
            tmp.setSmileDegree(smileDegreeArray[i]);
            tmp.setSmileConfidence(smileConfidenceArray[i]);
            extendedFaces[i] = tmp;
        }
        return extendedFaces;
    }

    private void updateFaceView(final Face[] faces, final ExtendedFace[] extendedFaces) {
        mPreviewFaces = faces;
        mExFaces = extendedFaces;
        if (faces != null) {
            if (faces.length != 0) {
                mStickyFaces = faces;
                mStickyExFaces = extendedFaces;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mUI.onFaceDetection(faces, extendedFaces);
                }
            });
        }
    }

    public boolean isSelfieFlash() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SELFIE_FLASH);
        return value != null && value.equals("on") && getMainCameraId() == FRONT_ID;
    }

    private void checkSelfieFlashAndTakePicture() {
        if (isSelfieFlash()) {
            mUI.startSelfieFlash();
            if (selfieThread == null) {
                selfieThread = new SelfieThread();
                selfieThread.start();
            }
        } else {
            takePicture();
        }
    }

    @Override
    public void onCountDownFinished() {
        checkSelfieFlashAndTakePicture();
        mUI.showUIAfterCountDown();
    }

    @Override
    public void onScreenSizeChanged(int width, int height) {

    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {

    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void waitingLocationPermissionResult(boolean result) {
        mLocationManager.waitingLocationPermissionResult(result);
    }

    @Override
    public void enableRecordingLocation(boolean enable) {
        String value = (enable ? RecordLocationPreference.VALUE_ON
                               : RecordLocationPreference.VALUE_OFF);
        mSettingsManager.setValue(SettingsManager.KEY_RECORD_LOCATION, value);
        mLocationManager.recordLocation(enable);
    }

    @Override
    public void setPreferenceForTest(String key, String value) {
        mSettingsManager.setValue(key, value);
    }

    @Override
    public void onPreviewUIReady() {
        updatePreviewSurfaceReadyState(true);

        if (mPaused || mIsRecordingVideo) {
            return;
        }
    }

    @Override
    public void onPreviewUIDestroyed() {
        updatePreviewSurfaceReadyState(false);
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
            if (mGraphViewR != null) {
                mGraphViewR.setRotation(-mOrientation);
            }
            if (mGraphViewGR != null) {
                mGraphViewGR.setRotation(-mOrientation);
            }
            if (mGraphViewGB != null) {
                mGraphViewGB.setRotation(-mOrientation);
            }
            if (mGraphViewB != null) {
                mGraphViewB.setRotation(-mOrientation);
            }
        }

        // need to re-initialize mGraphView to show histogram on rotate
        mGraphViewR = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_r);
        mGraphViewGR = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gr);
        mGraphViewGB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gb);
        mGraphViewB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_b);
        mGraphViewR.setDataSection(0,256);
        mGraphViewGR.setDataSection(256,512);
        mGraphViewGB.setDataSection(512,768);
        mGraphViewB.setDataSection(768,1024);
        if(mGraphViewR != null){
            mGraphViewR.setAlpha(0.75f);
            mGraphViewR.setCaptureModuleObject(this);
            mGraphViewR.PreviewChanged();
        }
        if(mGraphViewGR != null){
            mGraphViewGR.setAlpha(0.75f);
            mGraphViewGR.setCaptureModuleObject(this);
            mGraphViewGR.PreviewChanged();
        }
        if(mGraphViewGB != null){
            mGraphViewGB.setAlpha(0.75f);
            mGraphViewGB.setCaptureModuleObject(this);
            mGraphViewGB.PreviewChanged();
        }
        if(mGraphViewB != null){
            mGraphViewB.setAlpha(0.75f);
            mGraphViewB.setCaptureModuleObject(this);
            mGraphViewB.PreviewChanged();
        }
    }

    public int getDisplayOrientation() {
        return mOrientation;
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
        if (!pressed && mLongshotActive) {
            Log.d(TAG, "Longshot button up");
            mLongshotActive = false;
            mPostProcessor.stopLongShot();
        }
    }

    private void updatePictureSize() {
        String pictureSize = mSettingsManager.getValue(SettingsManager.KEY_PICTURE_SIZE);
        mPictureSize = parsePictureSize(pictureSize);
        Size[] prevSizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                SurfaceHolder.class);
        mSupportedMaxPictureSize = prevSizes[0];
        Size[] rawSize = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                    ImageFormat.RAW10);
        mSupportedRawPictureSize = rawSize[0];
        mPreviewSize = getOptimalPreviewSize(mPictureSize, prevSizes);
        Size[] thumbSizes = mSettingsManager.getSupportedThumbnailSizes(getMainCameraId());
        mPictureThumbSize = getOptimalPreviewSize(mPictureSize, thumbSizes); // get largest thumb size
    }

    public Size getThumbSize() {
        return mPictureThumbSize;
    }

    public boolean isRecordingVideo() {
        return mIsRecordingVideo;
    }

    public void setMute(boolean enable, boolean isValue) {
        AudioManager am = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        am.setMicrophoneMute(enable);
        if (isValue) {
            mIsMute = enable;
        }
    }

    public boolean isAudioMute() {
        return mIsMute;
    }

    private void updateVideoSize() {
        String videoSize = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_QUALITY);
        if (videoSize == null) return;
        mVideoSize = parsePictureSize(videoSize);
        Size[] prevSizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                MediaRecorder.class);
        mVideoPreviewSize = getOptimalPreviewSize(mVideoSize, prevSizes);

        Point previewSize = PersistUtil.getCameraPreviewSize();
        if (previewSize != null) {
            mVideoPreviewSize = new Size(previewSize.x, previewSize.y);
        }
        Log.d(TAG, "updatePreviewSize final preview size = " + mVideoPreviewSize.getWidth()
                + ", " + mVideoPreviewSize.getHeight());
    }

    private void updateVideoSnapshotSize() {
        mVideoSnapshotSize = mVideoSize;
        if (is4kSize(mVideoSize) && is4kSize(mVideoSnapshotSize)) {
            mVideoSnapshotSize = getMaxPictureSizeLessThan4k();
        }
        Size[] thumbSizes = mSettingsManager.getSupportedThumbnailSizes(getMainCameraId());
        mVideoSnapshotThumbSize = getOptimalPreviewSize(mVideoSnapshotSize, thumbSizes); // get largest thumb size
    }

    private boolean is4kSize(Size size) {
        return (size.getHeight() >= 2160 || size.getWidth() >= 3840);
    }

    private void updateMaxVideoDuration() {
        String minutesStr = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_DURATION);
        int minutes = Integer.parseInt(minutesStr);
        if (minutes == -1) {
            // User wants lowest, set 30s */
            mMaxVideoDurationInMs = 30000;
        } else {
            // 1 minute = 60000ms
            mMaxVideoDurationInMs = 60000 * minutes;
        }
    }

    private void updateZoom() {
        String zoomStr = mSettingsManager.getValue(SettingsManager.KEY_ZOOM);
        int zoom = Integer.parseInt(zoomStr);
        if ( zoom !=0 ) {
            mZoomValue = (float)zoom;
        }else{
            mZoomValue = 1.0f;
        }
    }

    private boolean startRecordingVideo(final int cameraId) {
        if (null == mCameraDevice[cameraId]) {
            return false;
        }
        Log.d(TAG, "StartRecordingVideo " + cameraId);
        mStartRecPending = true;
        mIsRecordingVideo = true;
        mMediaRecorderPausing = false;

        mActivity.updateStorageSpaceAndHint();
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "Storage issue, ignore the start request");
            mStartRecPending = false;
            mIsRecordingVideo = false;
            return false;
        }

        try {
            setUpMediaRecorder(cameraId);
            try {
                mMediaRecorder.start(); // Recording is now started
            } catch (RuntimeException e) {
                Toast.makeText(mActivity,"Could not start media recorder.\n " +
                        "Can't start video recording.", Toast.LENGTH_LONG).show();
                releaseMediaRecorder();
                releaseAudioFocus();
                mStartRecPending = false;
                mIsRecordingVideo = false;
                return false;
            }
            if (mUnsupportedResolution == true ) {
                Log.v(TAG, "Unsupported Resolution according to target");
                mStartRecPending = false;
                mIsRecordingVideo = false;
                return false;
            }
            if (mMediaRecorder == null) {
                Log.e(TAG, "Fail to initialize media recorder");
                mStartRecPending = false;
                mIsRecordingVideo = false;
                return false;
            }

            requestAudioFocus();
            mUI.clearFocus();
            mUI.hideUIwhileRecording();
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[cameraId]);
            mState[cameraId] = STATE_PREVIEW;
            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
            closePreviewSession();
            mFrameProcessor.onClose();

            Size preview = mVideoPreviewSize;
            if (mHighSpeedCapture) {
                preview = mVideoSize;
            }
            boolean changed = mUI.setPreviewSize(preview.getWidth(),
                    preview.getHeight());
            if (changed) {
                mUI.hideSurfaceView();
                mUI.showSurfaceView();
            }
            mUI.resetTrackingFocus();

            createVideoSnapshotImageReader();
            mVideoRequestBuilder = mCameraDevice[cameraId].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mVideoRequestBuilder.setTag(cameraId);
            mPreviewRequestBuilder[cameraId] = mVideoRequestBuilder;
            List<Surface> surfaces = new ArrayList<>();

            Surface surface = getPreviewSurfaceForSession(cameraId);
            mFrameProcessor.onOpen(getFrameProcFilterId(),mVideoSize);
            if(mFrameProcessor.isFrameFilterEnabled()) {
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mUI.getSurfaceHolder().setFixedSize(mVideoSize.getHeight(), mVideoSize.getWidth());
                    }
                });
            }
            mFrameProcessor.setOutputSurface(surface);
            mFrameProcessor.setVideoOutputSurface(mMediaRecorder.getSurface());
            addPreviewSurface(mVideoRequestBuilder, surfaces, cameraId);

            if (mHighSpeedCapture)
                mVideoRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mHighSpeedFPSRange);

            if (mHighSpeedCapture && ((int)mHighSpeedFPSRange.getUpper() > NORMAL_SESSION_MAX_FPS)) {
                mCameraDevice[cameraId].createConstrainedHighSpeedCaptureSession(surfaces, new
                        CameraConstrainedHighSpeedCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        mCurrentSession = cameraCaptureSession;
                        mCaptureSession[cameraId] = cameraCaptureSession;
                        CameraConstrainedHighSpeedCaptureSession session =
                                    (CameraConstrainedHighSpeedCaptureSession) mCurrentSession;
                        try {
                            List list = CameraUtil
                                 .createHighSpeedRequestList(mVideoRequestBuilder.build(),cameraId);
                            session.setRepeatingBurst(list, mCaptureCallback, mCameraHandler);
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to start high speed video recording "
                                        + e.getMessage());
                            e.printStackTrace();
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Failed to start high speed video recording "
                                        + e.getMessage());
                            e.printStackTrace();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Failed to start high speed video recording "
                                        + e.getMessage());
                            e.printStackTrace();
                        }
                        mUI.clearFocus();
                        mUI.resetPauseButton();
                        mRecordingTotalTime = 0L;
                        mRecordingStartTime = SystemClock.uptimeMillis();
                        mUI.enableShutter(false);
                        mUI.showRecordingUI(true, true);
                        updateRecordingTime();
                        keepScreenOn();
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(mActivity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }, null);
            } else {
                surfaces.add(mVideoSnapshotImageReader.getSurface());
                mCameraDevice[cameraId].createCaptureSession(surfaces, new CameraCaptureSession
                        .StateCallback() {

                    @Override
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        Log.d(TAG, "StartRecordingVideo session onConfigured");
                        mCurrentSession = cameraCaptureSession;
                        mCaptureSession[cameraId] = cameraCaptureSession;
                        try {
                            setUpVideoCaptureRequestBuilder(mVideoRequestBuilder, cameraId);
                            mCurrentSession.setRepeatingRequest(mVideoRequestBuilder.build(),
                                    mCaptureCallback, mCameraHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        } catch (IllegalStateException e) {
                            e.printStackTrace();
                        }
                        mUI.clearFocus();
                        mUI.resetPauseButton();
                        mRecordingTotalTime = 0L;
                        mRecordingStartTime = SystemClock.uptimeMillis();
                        mUI.showRecordingUI(true, false);
                        updateRecordingTime();
                        keepScreenOn();
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(mActivity, "Video Failed", Toast.LENGTH_SHORT).show();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mStartRecPending = false;
        return true;
    }

    private void updateTimeLapseSetting() {
        String value = mSettingsManager.getValue(SettingsManager
                .KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        if (value == null) return;
        int time = Integer.parseInt(value);
        mTimeBetweenTimeLapseFrameCaptureMs = time;
        mCaptureTimeLapse = mTimeBetweenTimeLapseFrameCaptureMs != 0;
        mUI.showTimeLapseUI(mCaptureTimeLapse);
    }

    private void updateHFRSetting() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        if (value == null) return;
        if (value.equals("off")) {
            mHighSpeedCapture = false;
        } else {
            mHighSpeedCapture = true;
            String mode = value.substring(0, 3);
            mHighSpeedRecordingMode = mode.equals("hsr");
            mHighSpeedCaptureRate = Integer.parseInt(value.substring(3));
        }
    }

    private void setUpVideoCaptureRequestBuilder(CaptureRequest.Builder builder,int cameraId) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                .CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        applyVideoStabilization(builder);
        applyNoiseReduction(builder);
        applyColorEffect(builder);
        applyVideoFlash(builder);
        applyFaceDetection(builder);
        applyZoom(builder, cameraId);
    }

    private void updateVideoFlash() {
        if (!mIsRecordingVideo || mHighSpeedCapture) return;
        applyVideoFlash(mVideoRequestBuilder);
        try {
            mCurrentSession.setRepeatingRequest(mVideoRequestBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void applyVideoFlash(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_FLASH_MODE);
        if (value == null) return;
        boolean flashOn = value.equals("torch");

        if (flashOn) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void applyNoiseReduction(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_NOISE_REDUCTION);
        if (value == null) return;
        int noiseReduction = SettingTranslation.getNoiseReduction(value);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReduction);
    }

    private void applyVideoStabilization(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_DIS);
        if (value == null) return;
        if (value.equals("on")) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                    .CONTROL_VIDEO_STABILIZATION_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                    .CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    private void updateRecordingTime() {
        if (!mIsRecordingVideo) {
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
            text = CameraUtil.millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = CameraUtil.millisecondToTimeString(getTimeLapseVideoLength(delta), true);
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

    public void onButtonPause() {
        pauseVideoRecording();
    }

    public void onButtonContinue() {
        resumeVideoRecording();
    }

    private void stopRecordingVideo(int cameraId) {
        Log.d(TAG, "stopRecordingVideo " + cameraId);

        mStopRecPending = true;
        boolean shouldAddToMediaStoreNow = false;
        // Stop recording
        mFrameProcessor.setVideoOutputSurface(null);
        mFrameProcessor.onClose();
        closePreviewSession();
        try {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.stop();
            shouldAddToMediaStoreNow = true;
            AccessibilityUtils.makeAnnouncement(mUI.getVideoButton(),
                    mActivity.getString(R.string.video_recording_stopped));
        } catch (RuntimeException e) {
            Log.w(TAG, "MediaRecoder stop fail",  e);
            if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
        }
        if (shouldAddToMediaStoreNow) {
            saveVideo();
        }
        keepScreenOnAwhile();
        // release media recorder
        releaseMediaRecorder();
        releaseAudioFocus();

        mUI.showRecordingUI(false, false);
        mUI.enableShutter(true);

        mIsRecordingVideo = false;
        if (mIntentMode == INTENT_MODE_VIDEO) {
            if (isQuickCapture()) {
                onRecordingDone(true);
            } else {
                Bitmap thumbnail = getVideoThumbnail();
                mUI.showRecordVideoForReview(thumbnail);
            }
        }

        if(mFrameProcessor != null) {
            mFrameProcessor.onOpen(getFrameProcFilterId(), mPreviewSize);
        }
        boolean changed = mUI.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        if (changed) {
            mUI.hideSurfaceView();
            mUI.showSurfaceView();
        }
        createSessions();
        mUI.showUIafterRecording();
        mUI.resetTrackingFocus();
        mStopRecPending = false;
    }

    private void closePreviewSession() {
        Log.d(TAG, "closePreviewSession");
        if (mCurrentSession != null) {
            mCurrentSession.close();
            mCurrentSession = null;
        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    private String generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String filename = title + CameraUtil.convertOutputFormatToFileExt(outputFileFormat);
        String mime = CameraUtil.convertOutputFormatToMimeType(outputFileFormat);
        String path;
        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            path = SDCard.instance().getDirectory() + '/' + filename;
        } else {
            path = Storage.DIRECTORY + '/' + filename;
        }
        mCurrentVideoValues = new ContentValues(9);
        mCurrentVideoValues.put(MediaStore.Video.Media.TITLE, title);
        mCurrentVideoValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues.put(MediaStore.Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATA, path);
        mCurrentVideoValues.put(MediaStore.Video.Media.RESOLUTION,
                "" + mVideoSize.getWidth() + "x" + mVideoSize.getHeight());
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues.put(MediaStore.Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues.put(MediaStore.Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoFilename = path;
        return path;
    }

    private void saveVideo() {
        if (mVideoFileDescriptor == null) {
            File origFile = new File(mVideoFilename);
            if (!origFile.exists() || origFile.length() <= 0) {
                Log.e(TAG, "Invalid file");
                mCurrentVideoValues = null;
                return;
            }

            long duration = 0L;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(mVideoFilename);
                duration = Long.valueOf(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "cannot access the file");
            }
            retriever.release();

            mActivity.getMediaSaveService().addVideo(mVideoFilename,
                    duration, mCurrentVideoValues,
                    mOnVideoSavedListener, mContentResolver);
        }
        mCurrentVideoValues = null;
    }

    private void setUpMediaRecorder(int cameraId) throws IOException {
        Log.d(TAG, "setUpMediaRecorder");
        String videoSize = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_QUALITY);
        int size = CameraSettings.VIDEO_QUALITY_TABLE.get(videoSize);
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                size = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                size = CamcorderProfile.QUALITY_LOW;
            }
        }
        if (mCaptureTimeLapse) {
            size = CameraSettings.getTimeLapseQualityFor(size);
        }

        Bundle myExtras = intent.getExtras();

        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();

        updateHFRSetting();
        boolean hfr = mHighSpeedCapture && !mHighSpeedRecordingMode;

        if (CamcorderProfile.hasProfile(cameraId, size)) {
            mProfile = CamcorderProfile.get(cameraId, size);
        } else {
            if (!"-1".equals(mSettingsManager.getValue(SettingsManager.KEY_SWITCH_CAMERA))) {
                mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            } else {
                RotateTextToast.makeText(mActivity, R.string.error_app_unsupported_profile,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        int videoWidth = mProfile.videoFrameWidth;
        int videoHeight = mProfile.videoFrameHeight;
        mUnsupportedResolution = false;

        int videoEncoder = SettingTranslation
                .getVideoEncoder(mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ENCODER));
        int audioEncoder = SettingTranslation
                .getAudioEncoder(mSettingsManager.getValue(SettingsManager.KEY_AUDIO_ENCODER));

        mProfile.videoCodec = videoEncoder;
        if (!mCaptureTimeLapse && !hfr) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mProfile.audioCodec = audioEncoder;
            if (mProfile.audioCodec == MediaRecorder.AudioEncoder.AMR_NB) {
                mProfile.fileFormat = MediaRecorder.OutputFormat.THREE_GPP;
            }
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mMediaRecorder.setOutputFormat(mProfile.fileFormat);
        closeVideoFileDescriptor();
        if (mIntentMode == CaptureModule.INTENT_MODE_VIDEO && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mCurrentVideoUri = saveUri;
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            String fileName = generateVideoFilename(mProfile.fileFormat);
            Log.v(TAG, "New video filename: " + fileName);
            mMediaRecorder.setOutputFile(fileName);
        }
        mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
        if(mFrameProcessor.isFrameFilterEnabled()) {
            mMediaRecorder.setVideoSize(mProfile.videoFrameHeight, mProfile.videoFrameWidth);
        } else {
            mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        }
        mMediaRecorder.setVideoEncoder(videoEncoder);
        if (!mCaptureTimeLapse && !hfr) {
            mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
            mMediaRecorder.setAudioChannels(mProfile.audioChannels);
            mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
            mMediaRecorder.setAudioEncoder(audioEncoder);
        }
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

        Log.i(TAG, "Profile video bitrate: "+ mProfile.videoBitRate);
        Log.i(TAG, "Profile video frame rate: "+ mProfile.videoFrameRate);
        if (mCaptureTimeLapse) {
            double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
            mMediaRecorder.setCaptureRate(fps);
        }  else if (mHighSpeedCapture) {
            mHighSpeedFPSRange = new Range(mHighSpeedCaptureRate, mHighSpeedCaptureRate);
            int fps = (int) mHighSpeedFPSRange.getUpper();
            mMediaRecorder.setCaptureRate(fps);
            int targetRate = mHighSpeedRecordingMode ? fps : 30;
            mMediaRecorder.setVideoFrameRate(targetRate);
            Log.i(TAG, "Capture rate: "+fps+", Target rate: "+targetRate);
            int scaledBitrate = mSettingsManager.getHighSpeedVideoEncoderBitRate(mProfile, targetRate);
            Log.i(TAG, "Scaled video bitrate : " + scaledBitrate);
            mMediaRecorder.setVideoEncodingBitRate(scaledBitrate);
        }

        long requestedSizeLimit = 0;
        if (isVideoCaptureIntent() && myExtras != null) {
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        //check if codec supports the resolution, otherwise throw toast
        List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (VideoEncoderCap videoEnc: videoEncoders) {
            if (videoEnc.mCodec == videoEncoder) {
                if (videoWidth > videoEnc.mMaxFrameWidth ||
                        videoWidth < videoEnc.mMinFrameWidth ||
                        videoHeight > videoEnc.mMaxFrameHeight ||
                        videoHeight < videoEnc.mMinFrameHeight) {
                    Log.e(TAG, "Selected codec " + videoEncoder +
                            " does not support "+ videoWidth + "x" + videoHeight
                            + " resolution");
                    Log.e(TAG, "Codec capabilities: " +
                            "mMinFrameWidth = " + videoEnc.mMinFrameWidth + " , " +
                            "mMinFrameHeight = " + videoEnc.mMinFrameHeight + " , " +
                            "mMaxFrameWidth = " + videoEnc.mMaxFrameWidth + " , " +
                            "mMaxFrameHeight = " + videoEnc.mMaxFrameHeight);
                    mUnsupportedResolution = true;
                    RotateTextToast.makeText(mActivity, R.string.error_app_unsupported,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            }
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpaceBytes() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        if (Storage.isSaveSDCard() && maxFileSize > SDCARD_SIZE_LIMIT) {
            maxFileSize = SDCARD_SIZE_LIMIT;
        }
        Log.i(TAG, "MediaRecorder setMaxFileSize: " + maxFileSize);
        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
                    (float) loc.getLongitude());
        }
        int rotation = CameraUtil.getJpegRotation(cameraId, mOrientation);
        String videoRotation = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ROTATION);
        if (videoRotation != null) {
            rotation += Integer.parseInt(videoRotation);
            rotation = rotation % 360;
        }
        if(mFrameProcessor.isFrameFilterEnabled()) {
            mMediaRecorder.setOrientationHint(0);
        } else {
            mMediaRecorder.setOrientationHint(rotation);
        }
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

    public void onVideoButtonClick() {
        if (isRecorderReady() == false) return;

        if (getCameraMode() == DUAL_MODE) return;
        if (mIsRecordingVideo) {
            stopRecordingVideo(getMainCameraId());
        } else {
            if (!startRecordingVideo(getMainCameraId())) {
                // Show ui when start recording failed.
                mUI.showUIafterRecording();
                releaseMediaRecorder();
            }
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }

        if (mIsRecordingVideo) {
            captureVideoSnapshot(getMainCameraId());
        } else {
            String timer = mSettingsManager.getValue(SettingsManager.KEY_TIMER);

            int seconds = Integer.parseInt(timer);
            // When shutter button is pressed, check whether the previous countdown is
            // finished. If not, cancel the previous countdown and start a new one.
            if (mUI.isCountingDown()) {
                mUI.cancelCountDown();
            }
            if (seconds > 0) {
                mUI.startCountDown(seconds, true);
            } else {
                if(mChosenImageFormat == ImageFormat.YUV_420_888 && mPostProcessor.isItBusy()) {
                    warningToast("It's still busy processing previous scene mode request.");
                    return;
                }
                checkSelfieFlashAndTakePicture();
            }
        }
    }

    private void warningToast(final String msg) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                RotateTextToast.makeText(mActivity, msg,
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    public boolean isLongShotSettingEnabled() {
        String longshot = mSettingsManager.getValue(SettingsManager.KEY_LONGSHOT);
        if(longshot.equals("on")) {
            return true;
        }
        return false;
    }

    @Override
    public void onShutterButtonLongClick() {
        if (isBackCamera() && getCameraMode() == DUAL_MODE) return;

        if (isLongShotSettingEnabled()) {
            //Cancel the previous countdown when long press shutter button for longshot.
            if (mUI.isCountingDown()) {
                mUI.cancelCountDown();
            }
            //check whether current memory is enough for longshot.
            mActivity.updateStorageSpaceAndHint();

            long storageSpace = mActivity.getStorageSpaceBytes();
            int mLongShotCaptureCountLimit = PersistUtil.getLongshotShotLimit();

            if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD_BYTES + mLongShotCaptureCountLimit
                    * mJpegFileSizeEstimation) {
                Log.i(TAG, "Not enough space or storage not ready. remaining=" + storageSpace);
                return;
            }

            if (isLongshotNeedCancel()) {
                mLongshotActive = false;
                return;
            }

            Log.d(TAG, "Start Longshot");
            mLongshotActive = true;
            takePicture();
        }
    }

    private void estimateJpegFileSize() {
        String quality = mSettingsManager.getValue(SettingsManager
            .KEY_JPEG_QUALITY);
        int[] ratios = mActivity.getResources().getIntArray(R.array.jpegquality_compression_ratio);
        String[] qualities = mActivity.getResources().getStringArray(
                R.array.pref_camera_jpegquality_entryvalues);
        int ratio = 0;
        for (int i = ratios.length - 1; i >= 0; --i) {
            if (qualities[i].equals(quality)) {
                ratio = ratios[i];
                break;
            }
        }
        String pictureSize = mSettingsManager.getValue(SettingsManager
                .KEY_PICTURE_SIZE);

        Size size = parsePictureSize(pictureSize);
        if (ratio == 0) {
            Log.d(TAG, "mJpegFileSizeEstimation 0");
        } else {
            mJpegFileSizeEstimation =  size.getWidth() * size.getHeight() * 3 / ratio;
            Log.d(TAG, "mJpegFileSizeEstimation " + mJpegFileSizeEstimation);
        }

    }

    private boolean isLongshotNeedCancel() {
        if (PersistUtil.getSkipMemoryCheck()) {
            return false;
        }

        if (Storage.getAvailableSpace() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "current storage is full");
            return true;
        }
        if (SECONDARY_SERVER_MEM == 0) {
            ActivityManager am = (ActivityManager) mActivity.getSystemService(
                    Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            SECONDARY_SERVER_MEM = memInfo.secondaryServerThreshold;
        }

        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long remainMemory = maxMemory - totalMemory;

        MemInfoReader reader = new MemInfoReader();
        reader.readMemInfo();
        long[] info = reader.getRawInfo();
        long availMem = (info[Debug.MEMINFO_FREE] + info[Debug.MEMINFO_CACHED]) * 1024;

        if (availMem <= SECONDARY_SERVER_MEM || remainMemory <= LONGSHOT_CANCEL_THRESHOLD) {
            Log.e(TAG, "cancel longshot: free=" + info[Debug.MEMINFO_FREE] * 1024
                    + " cached=" + info[Debug.MEMINFO_CACHED] * 1024
                    + " threshold=" + SECONDARY_SERVER_MEM);
            RotateTextToast.makeText(mActivity, R.string.msg_cancel_longshot_for_limited_memory,
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        if ( mIsRecordingVideo ) {
            Log.e(TAG, " cancel longshot:not supported when recording");
            return true;
        }
        return false;
    }

    private boolean isFlashOff(int id) {
        if (!mSettingsManager.isFlashSupported(id)) return true;
        return mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE).equals("off");
    }

    private boolean isFlashOn(int id) {
        if (!mSettingsManager.isFlashSupported(id)) return false;
        return mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE).equals("on");
    }

    private void initializePreviewConfiguration(int id) {
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_IDLE);
        applyCommonSettings(mPreviewRequestBuilder[id], id);
        applyFlash(mPreviewRequestBuilder[id], id);
    }

    public float getZoomValue() {
        return mZoomValue;
    }

    public Rect cropRegionForZoom(int id) {
        if (DEBUG) {
            Log.d(TAG, "cropRegionForZoom " + id);
        }
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

    private void applyInstantAEC(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_INSTANT_AEC);
        if (value == null || value.equals("0"))
            return;
        int intValue = Integer.parseInt(value);
        request.set(CaptureModule.INSTANT_AEC_MODE, intValue);
    }

    private void applySaturationLevel(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SATURATION_LEVEL);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            request.set(CaptureModule.SATURATION, intValue);
        }
    }

    private void applyAntiBandingLevel(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_ANTI_BANDING_LEVEL);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            request.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, intValue);
        }
    }

    private void applyHistogram(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_HISTOGRAM);
        if (value != null ) {
            if (value.equals("enable")){
                final byte enable = 1;
                request.set(CaptureModule.histMode, enable);
                mHiston = true;
                updateGraghViewVisibility(View.VISIBLE);
                updateGraghView();
                return;
            }
        }
        mHiston = false;
        updateGraghViewVisibility(View.GONE);
    }

    private void enableBokeh(CaptureRequest.Builder request) {
        if (mBokehEnabled) {
            mBokehRequestBuilder = request;
            try {
                request.set(CaptureModule.bokeh_enable, true);
                final SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(mActivity);
                int progress = prefs.getInt(SettingsManager.KEY_BOKEH_BLUR_DEGREE, 50);
                request.set(CaptureModule.bokeh_blur_level, progress);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "can not find vendor tag : org.codeaurora.qcamera3.bokeh");
            }
        }
    }

    public void setBokehBlurDegree(int degree) {
        if (!checkSessionAndBuilder(mCaptureSession[getMainCameraId()], mBokehRequestBuilder)) {
            return;
        }
        try {
            mBokehRequestBuilder.set(CaptureModule.bokeh_blur_level, degree);
            mCaptureSession[getMainCameraId()].setRepeatingRequest(mBokehRequestBuilder
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "can not find vendor tag : " + CaptureModule.bokeh_blur_level);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera Access Exception in setBokehBlurDegree");
        }
    }

    private void updateGraghViewVisibility(final int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mGraphViewR != null) {
                    mGraphViewR.setVisibility(visibility);
                }
                if(mGraphViewGR != null) {
                    mGraphViewGR.setVisibility(visibility);
                }
                if(mGraphViewGB != null) {
                    mGraphViewGB.setVisibility(visibility);
                }
                if(mGraphViewB != null) {
                    mGraphViewB.setVisibility(visibility);
                }
            }
        });
    }

    private void updateGraghView(){
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mGraphViewR != null) {
                    mGraphViewR.PreviewChanged();
                }
                if(mGraphViewGR != null) {
                    mGraphViewGR.PreviewChanged();
                }
                if(mGraphViewGB != null) {
                    mGraphViewGB.PreviewChanged();
                }
                if(mGraphViewB != null) {
                    mGraphViewB.PreviewChanged();
                }
            }
        });
    }

    private boolean applyPreferenceToPreview(int cameraId, String key, String value) {
        if (!checkSessionAndBuilder(mCaptureSession[cameraId], mPreviewRequestBuilder[cameraId])) {
            return false;
        }
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
            case SettingsManager.KEY_ISO:
                updatePreview = true;
                applyIso(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_FACE_DETECTION:
                updatePreview = true;
                applyFaceDetection(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_FOCUS_DISTANCE:
                updatePreview = true;
                applyFocusDistance(mPreviewRequestBuilder[cameraId], value);
        }
        return updatePreview;
    }

    private void applyZoomAndUpdate(int id) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        applyZoom(mPreviewRequestBuilder[id], id);
        try {
            if(id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                CameraCaptureSession session = mCaptureSession[id];
                if (session instanceof CameraConstrainedHighSpeedCaptureSession) {
                    List list = CameraUtil
                            .createHighSpeedRequestList(mPreviewRequestBuilder[id].build(),id);
                    ((CameraConstrainedHighSpeedCaptureSession) session).setRepeatingBurst(list
                            , mCaptureCallback, mCameraHandler);
                } else {
                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                            .build(), mCaptureCallback, mCameraHandler);
                }

            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void applyJpegQuality(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_JPEG_QUALITY);
        int jpegQuality = getQualityNumber(value);
        request.set(CaptureRequest.JPEG_QUALITY, (byte) jpegQuality);
    }

    private void applyAFRegions(CaptureRequest.Builder request, int id) {
        if (mControlAFMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions[id]);
        } else {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
        }
    }

    private void applyAERegions(CaptureRequest.Builder request, int id) {
        if (mControlAFMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
            request.set(CaptureRequest.CONTROL_AE_REGIONS, mAERegions[id]);
        } else {
            request.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        }
    }

    private void applySceneMode(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        String autoHdr = mSettingsManager.getValue(SettingsManager.KEY_AUTO_HDR);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        if (autoHdr != null && "enable".equals(autoHdr) && "0".equals(value)) {
            if (mSettingsManager.isHdrScene(getMainCameraId())) {
                request.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR);
                request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            }
            return;
        }
        if(getPostProcFilterId(mode) != PostProcessor.FILTER_NONE || mCaptureHDRTestEnable) {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            return;
        }
        if (mode != CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                && mode != SettingsManager.SCENE_MODE_DUAL_INT
                && mode != SettingsManager.SCENE_MODE_PROMODE_INT
                && mode != SettingsManager.SCENE_MODE_BOKEH_INT) {
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
        if (value.equals("auto")) {
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            VendorTagUtil.setIsoExpPriority(request, 0L);
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) == null) {
                request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mIsoExposureTime);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) == null) {
                request.set(CaptureRequest.SENSOR_SENSITIVITY, mIsoSensitivity);
            }
        } else {
            long intValue = SettingsManager.KEY_ISO_INDEX.get(value);
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            VendorTagUtil.setIsoExpPriority(request, intValue);
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) != null) {
                mIsoExposureTime = request.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) != null) {
                mIsoSensitivity = request.get(CaptureRequest.SENSOR_SENSITIVITY);
            }
            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, null);
        }
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
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        if (redeye != null && redeye.equals("on") && !mLongshotActive) {
            request.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
        } else {
            switch (value) {
                case "on":
                    if (mLongshotActive) {
                        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    } else {
                        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                        request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                    }
                    break;
                case "auto":
                    if (mLongshotActive) {
                        // When long shot is active, turn off the flash in auto mode
                        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    } else {
                        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                    }
                    break;
                case "off":
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    break;
            }
        }
    }

    private void applyFaceDetection(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION);
        if (value != null && value.equals("on")) {
            request.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
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

    private void addPreviewSurface(CaptureRequest.Builder builder, List<Surface> surfaceList, int id) {
        if (isBackCamera() && getCameraMode() == DUAL_MODE && id == MONO_ID) {
            if(surfaceList != null) {
                surfaceList.add(mUI.getMonoDummySurface());
            }
            builder.addTarget(mUI.getMonoDummySurface());
            return;
        } else {
            List<Surface> surfaces = mFrameProcessor.getInputSurfaces();
            for(Surface surface : surfaces) {
                if(surfaceList != null) {
                    surfaceList.add(surface);
                }
                builder.addTarget(surface);
            }
            return;
        }
    }

    public void checkAndPlayShutterSound(int id) {
        if (id == getMainCameraId()) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_SHUTTER_SOUND);
            if (value != null && value.equals("on") && mSound != null) {
                mSound.play(MediaActionSound.SHUTTER_CLICK);
            }
        }
    }

    public Surface getPreviewSurfaceForSession(int id) {
        if (isBackCamera()) {
            if (getCameraMode() == DUAL_MODE && id == MONO_ID) {
                return mUI.getMonoDummySurface();
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
        if (DEBUG) {
            Log.d(TAG, "triggerFocusAtPoint " + x + " " + y + " " + id);
        }
        Point p = mUI.getSurfaceViewSize();
        int width = p.x;
        int height = p.y;
        mAFRegions[id] = afaeRectangle(x, y, width, height, 1f, mCropRegion[id]);
        mAERegions[id] = afaeRectangle(x, y, width, height, 1.5f, mCropRegion[id]);
        mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[id]);
        autoFocusTrigger(id);
    }

    private void cancelTouchFocus(int id) {
        if(mPaused)
            return;

        if (DEBUG) {
            Log.v(TAG, "cancelTouchFocus " + id);
        }
        mState[id] = STATE_PREVIEW;
        mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        setAFModeToPreview(id, mControlAFMode);
    }

    private MeteringRectangle[] afaeRectangle(float x, float y, int width, int height,
                                              float multiple, Rect cropRegion) {
        int side = (int) (Math.max(width, height) / 8 * multiple);
        RectF meteringRegionF = new RectF(x - side / 2, y - side / 2, x + side / 2, y + side / 2);

        // inverse of matrix1 will translate from touch to (-1000 to 1000), which is camera1
        // coordinates, while accounting for orientation and mirror
        Matrix matrix1 = new Matrix();
        CameraUtil.prepareMatrix(matrix1, !isBackCamera(), mDisplayOrientation, width, height);
        matrix1.invert(matrix1);

        // inverse of matrix2 will translate from (-1000 to 1000) to camera 2 coordinates
        Matrix matrix2 = new Matrix();
        matrix2.preTranslate(-cropRegion.width() / 2f, -cropRegion.height() / 2f);
        matrix2.postScale(2000f / cropRegion.width(), 2000f / cropRegion.height());
        matrix2.invert(matrix2);

        matrix1.mapRect(meteringRegionF);
        matrix2.mapRect(meteringRegionF);

        Rect meteringRegion = new Rect((int) meteringRegionF.left, (int) meteringRegionF.top,
                (int) meteringRegionF.right, (int) meteringRegionF.bottom);

        meteringRegion.left = CameraUtil.clamp(meteringRegion.left, cropRegion.left,
                cropRegion.right);
        meteringRegion.top = CameraUtil.clamp(meteringRegion.top, cropRegion.top,
                cropRegion.bottom);
        meteringRegion.right = CameraUtil.clamp(meteringRegion.right, cropRegion.left,
                cropRegion.right);
        meteringRegion.bottom = CameraUtil.clamp(meteringRegion.bottom, cropRegion.top,
                cropRegion.bottom);

        MeteringRectangle[] meteringRectangle = new MeteringRectangle[1];
        meteringRectangle[0] = new MeteringRectangle(meteringRegion, 1);
        return meteringRectangle;
    }

    private void updateFocusStateChange(CaptureResult result) {
        final Integer resultAFState = result.get(CaptureResult.CONTROL_AF_STATE);
        if (resultAFState == null) return;

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

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, getMainCameraId());
    }

    @Override
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        if (mPaused) return;
        boolean updatePreviewBayer = false;
        boolean updatePreviewMono = false;
        boolean updatePreviewFront = false;
        boolean updatePreviewLogical = false;
        int count = 0;
        for (SettingsManager.SettingState settingState : settings) {
            String key = settingState.key;
            SettingsManager.Values values = settingState.values;
            String value;
            if (values.overriddenValue != null) {
                value = values.overriddenValue;
            } else {
                value = values.value;
            }
            switch (key) {
                case SettingsManager.KEY_CAMERA_SAVEPATH:
                    Storage.setSaveSDCard(value.equals("1"));
                    mActivity.updateStorageSpaceAndHint();
                    continue;
                case SettingsManager.KEY_JPEG_QUALITY:
                    estimateJpegFileSize();
                    continue;
                case SettingsManager.KEY_VIDEO_DURATION:
                    updateMaxVideoDuration();
                    continue;
                case SettingsManager.KEY_VIDEO_QUALITY:
                    updateVideoSize();
                    continue;
                case SettingsManager.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL:
                    updateTimeLapseSetting();
                    continue;
                case SettingsManager.KEY_FACE_DETECTION:
                    updateFaceDetection();
                    break;
                case SettingsManager.KEY_CAMERA_ID:
                case SettingsManager.KEY_MONO_ONLY:
                case SettingsManager.KEY_CLEARSIGHT:
                case SettingsManager.KEY_SWITCH_CAMERA:
                case SettingsManager.KEY_MONO_PREVIEW:
                    if (count == 0) restartAll();
                    return;
                case SettingsManager.KEY_VIDEO_FLASH_MODE:
                    updateVideoFlash();
                    return;
                case SettingsManager.KEY_FLASH_MODE:
                case SettingsManager.KEY_ZSL:
                case SettingsManager.KEY_AUTO_HDR:
                case SettingsManager.KEY_SAVERAW:
                case SettingsManager.KEY_HDR:
                    if (count == 0) restartSession(false);
                    return;
                case SettingsManager.KEY_SCENE_MODE:
                    restartAll();
                    return;
            }

            if (isBackCamera()) {
                switch (getCameraMode()) {
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
                    case SWITCH_MODE:
                        updatePreviewMono |= applyPreferenceToPreview(SWITCH_ID, key, value);
                        break;
                }
            } else if (SWITCH_ID != -1) {
                updatePreviewLogical = applyPreferenceToPreview(SWITCH_ID,key,value);
            }else {
                updatePreviewFront |= applyPreferenceToPreview(FRONT_ID, key, value);
            }
            count++;
        }
        if (updatePreviewBayer) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[BAYER_ID],
                        mPreviewRequestBuilder[BAYER_ID])) {
                    mCaptureSession[BAYER_ID].setRepeatingRequest(mPreviewRequestBuilder[BAYER_ID]
                            .build(), mCaptureCallback, mCameraHandler);
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewMono) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[MONO_ID],
                        mPreviewRequestBuilder[MONO_ID])) {
                    if (canStartMonoPreview()) {
                        mCaptureSession[MONO_ID].setRepeatingRequest(mPreviewRequestBuilder[MONO_ID]
                                .build(), mCaptureCallback, mCameraHandler);
                    } else {
                        mCaptureSession[MONO_ID].capture(mPreviewRequestBuilder[MONO_ID]
                                .build(), mCaptureCallback, mCameraHandler);
                    }
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewFront) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[FRONT_ID],
                        mPreviewRequestBuilder[FRONT_ID])) {
                    mCaptureSession[FRONT_ID].setRepeatingRequest(mPreviewRequestBuilder[FRONT_ID]
                            .build(), mCaptureCallback, mCameraHandler);
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }

        if (updatePreviewLogical) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[SWITCH_ID],
                        mPreviewRequestBuilder[SWITCH_ID])) {
                    mCaptureSession[SWITCH_ID].setRepeatingRequest(mPreviewRequestBuilder[SWITCH_ID]
                            .build(), mCaptureCallback, mCameraHandler);
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isPanoSetting(String value) {
        try {
            int mode = Integer.parseInt(value);
            if(mode == SettingsManager.SCENE_MODE_PANORAMA_INT) {
                return true;
            }
        } catch(Exception e) {
        }
        return false;
    }

    private void updateFaceDetection() {
        final String value = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (value == null || value.equals("off")) mUI.onStopFaceDetection();
                else {
                    mUI.onStartFaceDetection(mDisplayOrientation,
                            mSettingsManager.isFacingFront(getMainCameraId()),
                            mSettingsManager.getSensorActiveArraySize(getMainCameraId()));
                }
            }
        });
    }

    public void restartAll() {
        reinit();
        onPauseBeforeSuper();
        onPauseAfterSuper();
        onResumeBeforeSuper();
        onResumeAfterSuper();
        setRefocusLastTaken(false);
    }

    public void restartSession(boolean isSurfaceChanged) {
        Log.d(TAG, "restartSession isSurfaceChanged = " + isSurfaceChanged);
        if (isAllSessionClosed()) return;

        closeProcessors();
        closeSessions();

        if(isSurfaceChanged) {
            mUI.hideSurfaceView();
            mUI.showSurfaceView();
        }

        initializeValues();
        updatePreviewSize();
        openProcessors();
        createSessions();

        if(isTrackingFocusSettingOn()) {
            mUI.resetTrackingFocus();
        }
        resetStateMachine();
    }

    private void resetStateMachine() {
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }
        mUI.enableShutter(true);
    }

    private Size getOptimalPreviewSize(Size pictureSize, Size[] prevSizes) {
        Point[] points = new Point[prevSizes.length];

        double targetRatio = (double) pictureSize.getWidth() / pictureSize.getHeight();
        int index = 0;
        for (Size s : prevSizes) {
            points[index++] = new Point(s.getWidth(), s.getHeight());
        }

        int optimalPickIndex = CameraUtil.getOptimalPreviewSize(mActivity, points, targetRatio);
        return (optimalPickIndex == -1) ? null : prevSizes[optimalPickIndex];
    }

    private Size getMaxPictureSizeLessThan4k() {
        Size[] sizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(), ImageFormat.JPEG);
        float ratio = (float) mVideoSize.getWidth() / mVideoSize.getHeight();
        Size optimalSize = null;
        for (Size size : sizes) {
            if (is4kSize(size)) continue;
            float pictureRatio = (float) size.getWidth() / size.getHeight();
            if (Math.abs(pictureRatio - ratio) > 0.01) continue;
            if (optimalSize == null || size.getWidth() > optimalSize.getWidth()) {
                optimalSize = size;
            }
        }

        // Cannot find one that matches the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w(TAG, "No picture size match the aspect ratio");
            for (Size size : sizes) {
                if (is4kSize(size)) continue;
                if (optimalSize == null || size.getWidth() > optimalSize.getWidth()) {
                    optimalSize = size;
                }
            }
        }
        return optimalSize;
    }

    public TrackingFocusRenderer getTrackingForcusRenderer() {
        return mUI.getTrackingFocusRenderer();
    }

    private class MyCameraHandler extends Handler {

        public MyCameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int id = msg.arg1;
            switch (msg.what) {
                case OPEN_CAMERA:
                    openCamera(id);
                    break;
                case CANCEL_TOUCH_FOCUS:
                    cancelTouchFocus(id);
                    break;
            }
        }
    }

    private class MpoSaveHandler extends Handler {
        static final int MSG_CONFIGURE = 0;
        static final int MSG_NEW_IMG = 1;

        private Image monoImage;
        private Image bayerImage;
        private Long captureStartTime;

        public MpoSaveHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_CONFIGURE:
                captureStartTime = (Long) msg.obj;
                break;
            case MSG_NEW_IMG:
                processNewImage(msg);
                break;
            }
        }

        private void processNewImage(Message msg) {
            Log.d(TAG, "MpoSaveHandler:processNewImage for cam id: " + msg.arg1);
            if(msg.arg1 == MONO_ID) {
                monoImage = (Image)msg.obj;
            } else if(bayerImage == null){
                bayerImage = (Image)msg.obj;
            }

            if(monoImage != null && bayerImage != null) {
                saveMpoImage();
            }
        }

        private void saveMpoImage() {
            mNamedImages.nameNewImage(captureStartTime);
            NamedEntity namedEntity = mNamedImages.getNextNameEntity();
            String title = (namedEntity == null) ? null : namedEntity.title;
            long date = (namedEntity == null) ? -1 : namedEntity.date;
            int width = bayerImage.getWidth();
            int height = bayerImage.getHeight();
            byte[] bayerBytes = getJpegData(bayerImage);
            byte[] monoBytes = getJpegData(monoImage);

            ExifInterface exif = Exif.getExif(bayerBytes);
            int orientation = Exif.getOrientation(exif);

            mActivity.getMediaSaveService().addMpoImage(
                    null, bayerBytes, monoBytes, width, height, title,
                    date, null, orientation, mOnMediaSavedListener, mContentResolver, "jpeg");

            mActivity.updateThumbnail(bayerBytes);

            bayerImage.close();
            bayerImage = null;
            monoImage.close();
            monoImage = null;
            namedEntity = null;
        }
    }

    @Override
    public void onReleaseShutterLock() {
        Log.d(TAG, "onReleaseShutterLock");
        unlockFocus(BAYER_ID);
        unlockFocus(MONO_ID);
    }

    @Override
    public void onClearSightSuccess(byte[] thumbnailBytes) {
        Log.d(TAG, "onClearSightSuccess");
        onReleaseShutterLock();
        if(thumbnailBytes != null) mActivity.updateThumbnail(thumbnailBytes);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RotateTextToast.makeText(mActivity, R.string.clearsight_capture_success,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClearSightFailure(byte[] thumbnailBytes) {
        Log.d(TAG, "onClearSightFailure");
        if(thumbnailBytes != null) mActivity.updateThumbnail(thumbnailBytes);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RotateTextToast.makeText(mActivity, R.string.clearsight_capture_fail,
                        Toast.LENGTH_SHORT).show();
            }
        });

        onReleaseShutterLock();
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
                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }
                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }
            }
        }
    }

    @Override
    public void onErrorListener(int error) {
        enableRecordingLocation(false);
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        stopRecordingVideo(getMainCameraId());
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
            if (mIsRecordingVideo) {
                stopRecordingVideo(getMainCameraId());
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
           if (mIsRecordingVideo) {
               stopRecordingVideo(getMainCameraId());
           }
            // Show the toast.
            RotateTextToast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    private byte[] getJpegData(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void updateSaveStorageState() {
        Storage.setSaveSDCard(mSettingsManager.getValue(SettingsManager
                .KEY_CAMERA_SAVEPATH).equals("1"));
    }

    public void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri,
                CameraUtil.convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity
                    .startActivityForResult(intent, CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
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

    private Bitmap getVideoThumbnail() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mVideoFileDescriptor.getFileDescriptor(),
                    mVideoPreviewSize.getWidth());
        } else if (mCurrentVideoUri != null) {
            try {
                mVideoFileDescriptor = mContentResolver.openFileDescriptor(mCurrentVideoUri, "r");
                bitmap = Thumbnail.createVideoThumbnailBitmap(
                        mVideoFileDescriptor.getFileDescriptor(), mVideoPreviewSize.getWidth());
            } catch (java.io.FileNotFoundException ex) {
                // invalid uri
                Log.e(TAG, ex.toString());
            }
        }

        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            Camera.CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            boolean mirror = mPostProcessor.isSelfieMirrorOn();
            bitmap = CameraUtil.rotateAndMirror(bitmap, 0, mirror);
        }
        return bitmap;
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
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

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    private void showToast(String tips) {
        if (mToast == null) {
            mToast = Toast.makeText(mActivity, tips, Toast.LENGTH_LONG);
            mToast.setGravity(Gravity.CENTER, 0, 0);
        }
        mToast.setText(tips);
        mToast.show();
    }

    private boolean isRecorderReady() {
        if ((mStartRecPending == true || mStopRecPending == true))
            return false;
        else
            return true;
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

    private boolean isVideoCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
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


    private void setProModeVisible() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        boolean promode = false;
        if (scene != null) {
            int mode = Integer.parseInt(scene);
            if (mode == SettingsManager.SCENE_MODE_PROMODE_INT) {
                promode = true;
            }
        }
        mUI.initializeProMode(!mPaused && promode);
    }

    private void setBokehModeVisible() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (scene != null) {
            int mode = Integer.parseInt(scene);
            mBokehEnabled = mode == SettingsManager.SCENE_MODE_BOKEH_INT;
        }
        mUI.initializeBokehMode(!mPaused && mBokehEnabled);
        if (mPaused || !mBokehEnabled) {//disable bokeh mode
            mBokehRequestBuilder = null;
        }
    }
	
    boolean checkSessionAndBuilder(CameraCaptureSession session, CaptureRequest.Builder builder) {
        return session != null && builder != null;
    }
}

class Camera2GraphView extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mScale = (float)3;
    private float   mWidth;
    private float   mHeight;
    private int mStart, mEnd;
    private CaptureModule mCaptureModule;
    private float scaled;
    private static final int STATS_SIZE = 256;
    private static final String TAG = "GraphView";


    public Camera2GraphView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }

    void setDataSection(int start, int end){
        mStart =  start;
        mEnd = end;
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
        if(mCaptureModule == null && !mCaptureModule.mHiston) {
            Log.e(TAG, "returning as histogram is off ");
            return;
        }

        if (mBitmap != null) {
            final Paint paint = mPaint;
            final Canvas cavas = mCanvas;
            final float border = 5;
            float graphheight = mHeight - (2 * border);
            float graphwidth = mWidth - (2 * border);
            float left, top, right, bottom;
            float bargap = 0.0f;
            float barwidth = graphwidth / STATS_SIZE;

            cavas.drawColor(0xFFAAAAAA);
            paint.setColor(Color.BLACK);

            for (int k = 0; k <= (graphheight / 32); k++) {
                float y = (float) (32 * k) + border;
                cavas.drawLine(border, y, graphwidth + border, y, paint);
            }
            for (int j = 0; j <= (graphwidth / 32); j++) {
                float x = (float) (32 * j) + border;
                cavas.drawLine(x, border, x, graphheight + border, paint);
            }
            synchronized(CaptureModule.statsdata) {
                int maxValue = Integer.MIN_VALUE;
                for ( int i = mStart ; i < mEnd ; i++ ) {
                    if ( maxValue < CaptureModule.statsdata[i] ) {
                        maxValue = CaptureModule.statsdata[i];
                    }
                }
                mScale = ( float ) maxValue;
                for(int i=mStart ; i < mEnd ; i++)  {
                    scaled = (CaptureModule.statsdata[i]/mScale)*STATS_SIZE;
                    if(scaled >= (float)STATS_SIZE)
                        scaled = (float)STATS_SIZE;
                    left = (bargap * (i - mStart + 1)) + (barwidth * (i - mStart)) + border;
                    top = graphheight + border;
                    right = left + barwidth;
                    bottom = top - scaled;
                    cavas.drawRect(left, top, right, bottom, mPaintRect);
                }
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }
    public void PreviewChanged() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}
