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

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Display;
import android.graphics.Point;
import android.Manifest;
import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.media.ThumbnailUtils;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.camera.app.AppManagerFactory;
import com.android.camera.app.PlaceholderManager;
import com.android.camera.app.PanoramaStitchingManager;
import com.android.camera.crop.CropActivity;
import com.android.camera.data.CameraDataAdapter;
import com.android.camera.data.CameraPreviewData;
import com.android.camera.data.FixedFirstDataAdapter;
import com.android.camera.data.FixedLastDataAdapter;
import com.android.camera.data.InProgressDataWrapper;
import com.android.camera.data.LocalData;
import com.android.camera.data.LocalDataAdapter;
import com.android.camera.data.LocalMediaObserver;
import com.android.camera.data.MediaDetails;
import com.android.camera.data.SimpleViewData;
import com.android.camera.exif.ExifInterface;
import com.android.camera.tinyplanet.TinyPlanetFragment;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.DetailsDialog;
import com.android.camera.ui.FilmStripView;
import com.android.camera.ui.FilmStripView.ImageData;
import com.android.camera.ui.PanoCaptureProcessView;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.IntentHelper;
import com.android.camera.util.PersistUtil;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;

import java.io.File;
import java.io.IOException;

import static com.android.camera.CameraManager.CameraOpenErrorCallback;

public class CameraActivity extends Activity
        implements ModuleSwitcher.ModuleSwitchListener,
        ActionBar.OnMenuVisibilityListener,
        ShareActionProvider.OnShareTargetSelectedListener {

    private static final String TAG = "CAM_Activity";

    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";
    public static final String ACTION_TRIM_VIDEO =
            "com.android.camera.action.TRIM";
    public static final String MEDIA_ITEM_PATH = "media-item-path";
    public static final String KEY_TOTAL_NUMBER = "total-number";

    // Used to show whether Gallery was launched from Snapcam
    private static final String KEY_FROM_SNAPCAM = "from-snapcam";

    // The intent extra for camera from secure lock screen. True if the gallery
    // should only show newly captured pictures. sSecureAlbumId does not
    // increment. This is used when switching between camera, camcorder, and
    // panorama. If the extra is not set, it is in the normal camera mode.
    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    // This string is used for judge start activity from screenoff or not
    public static final String GESTURE_CAMERA_NAME = "com.android.camera.CameraGestureActivity";

    private static final String AUTO_TEST_INTENT ="com.android.camera.autotest";

    /**
     * Request code from an activity we started that indicated that we do not
     * want to reset the view to the preview in onResume.
     */
    public static final int REQ_CODE_DONT_SWITCH_TO_PREVIEW = 142;

    public static final int REQ_CODE_GCAM_DEBUG_POSTCAPTURE = 999;

    private static final int HIDE_ACTION_BAR = 1;
    private static final long SHOW_ACTION_BAR_TIMEOUT_MS = 3000;

    private static final int SWITCH_SAVE_PATH = 2;

    /** Permission request code */
    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;

    /** Whether onResume should reset the view to the preview. */
    private boolean mResetToPreviewOnResume = true;

    // Supported operations at FilmStripView. Different data has different
    // set of supported operations.
    private static final int SUPPORT_DELETE = 1 << 0;
    private static final int SUPPORT_ROTATE = 1 << 1;
    private static final int SUPPORT_INFO = 1 << 2;
    private static final int SUPPORT_CROP = 1 << 3;
    private static final int SUPPORT_SETAS = 1 << 4;
    private static final int SUPPORT_EDIT = 1 << 5;
    private static final int SUPPORT_TRIM = 1 << 6;
    private static final int SUPPORT_SHARE = 1 << 7;
    private static final int SUPPORT_SHARE_PANORAMA360 = 1 << 8;
    private static final int SUPPORT_SHOW_ON_MAP = 1 << 9;
    private static final int SUPPORT_ALL = 0xffffffff;

    // Pie Setting Menu enabled
    private static boolean PIE_MENU_ENABLED = false;
    private boolean mDeveloperMenuEnabled = false;

    /** This data adapter is used by FilmStripView. */
    private LocalDataAdapter mDataAdapter;
    /** This data adapter represents the real local camera data. */
    private LocalDataAdapter mWrappedDataAdapter;

    private PanoramaStitchingManager mPanoramaManager;
    private PlaceholderManager mPlaceholderManager;
    private int mCurrentModuleIndex;
    private CameraModule mCurrentModule;
    private PhotoModule mPhotoModule;
    private VideoModule mVideoModule;
    private WideAnglePanoramaModule mPanoModule;
    private CaptureModule mCaptureModule;
    private PanoCaptureModule mPano2Module;
    private FrameLayout mAboveFilmstripControlLayout;
    private FrameLayout mCameraRootFrame;
    private View mCameraPhotoModuleRootView;
    private View mCameraVideoModuleRootView;
    private View mCameraPanoModuleRootView;
    private View mCameraCaptureModuleRootView;
    private FilmStripView mFilmStripView;
    private ProgressBar mBottomProgress;
    private View mPanoStitchingPanel;
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    private OnScreenHint mStorageHint;
    private final Object mStorageSpaceLock = new Object();
    private long mStorageSpaceBytes = Storage.LOW_STORAGE_THRESHOLD_BYTES;
    private boolean mSecureCamera;
    private int mLastRawOrientation;
    private MyOrientationEventListener mOrientationListener;
    private Handler mMainHandler;
    private PanoramaViewHelper mPanoramaViewHelper;
    private CameraPreviewData mCameraPreviewData;
    private ActionBar mActionBar;
    private OnActionBarVisibilityListener mOnActionBarVisibilityListener = null;
    private Menu mActionBarMenu;
    private ViewGroup mUndoDeletionBar;
    private boolean mIsUndoingDeletion = false;
    private boolean mIsEditActivityInProgress = false;
    private View mPreviewCover;
    private FrameLayout mPreviewContentLayout;
    private boolean mPaused = true;
    private boolean mForceReleaseCamera = false;

    private Uri[] mNfcPushUris = new Uri[1];

    private ShareActionProvider mStandardShareActionProvider;
    private Intent mStandardShareIntent;
    private ShareActionProvider mPanoramaShareActionProvider;
    private Intent mPanoramaShareIntent;
    private LocalMediaObserver mLocalImagesObserver;
    private LocalMediaObserver mLocalVideosObserver;

    private final int DEFAULT_SYSTEM_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    private boolean mPendingDeletion = false;

    private Intent mVideoShareIntent;
    private Intent mImageShareIntent;
    public static int SETTING_LIST_WIDTH_1 = 250;
    public static int SETTING_LIST_WIDTH_2 = 250;

    private ImageView mThumbnail;
    private UpdateThumbnailTask mUpdateThumbnailTask;
    private CircularDrawable mThumbnailDrawable;
    // FilmStripView.setDataAdapter fires 2 onDataLoaded calls before any data is actually loaded
    // Keep track of data request here to avoid creating useless UpdateThumbnailTask.
    private boolean mDataRequested;
    private Cursor mCursor;

    private boolean mAutoTestEnabled = false;

    private WakeLock mWakeLock;
    private static final int REFOCUS_ACTIVITY_CODE = 1;

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) {
                return;
            }
            mLastRawOrientation = orientation;
            mCurrentModule.onOrientationChanged(orientation);
        }
    }

    private MediaSaveService mMediaSaveService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder b) {
            mMediaSaveService = ((MediaSaveService.LocalBinder) b).getService();
            mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            if (mMediaSaveService != null) {
                mMediaSaveService.setListener(null);
                mMediaSaveService = null;
            }
        }
    };

    private CameraOpenErrorCallback mCameraOpenErrorCallback =
            new CameraOpenErrorCallback() {
                @Override
                public void onCameraDisabled(int cameraId) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_OPEN_FAIL, "security");
                    CameraUtil.showErrorAndFinish(CameraActivity.this,
                            R.string.camera_disabled);
                }

                @Override
                public void onDeviceOpenFailure(int cameraId) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_OPEN_FAIL, "open");
                    showOpenCameraErrorDialog();
                }

                @Override
                public void onReconnectionFailure(CameraManager mgr) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_OPEN_FAIL, "reconnect");
                    showOpenCameraErrorDialog();
                }

                @Override
                public void onStartPreviewFailure(int cameraId) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_START_PREVIEW_FAIL, "startpreview");
                    showOpenCameraErrorDialog();
                }
            };

    // update the status of storage space when SD card status changed.
    private BroadcastReceiver mSDcardMountedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "SDcard status changed, update storage space");
            updateStorageSpaceAndHint();
        }
    };

    private void registerSDcardMountedReceiver() {
        // filter for SDcard status
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        registerReceiver(mSDcardMountedReceiver, filter);
    }

    private class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == HIDE_ACTION_BAR) {
                removeMessages(HIDE_ACTION_BAR);
                CameraActivity.this.setSystemBarsVisibility(false);
            }else if ( msg.what == SWITCH_SAVE_PATH ) {
                mCurrentModule.onSwitchSavePath();
            }
        }
    }

    public interface OnActionBarVisibilityListener {
        public void onActionBarVisibilityChanged(boolean isVisible);
    }

    public void setOnActionBarVisibilityListener(OnActionBarVisibilityListener listener) {
        mOnActionBarVisibilityListener = listener;
    }

    public static boolean isPieMenuEnabled() {
        return PIE_MENU_ENABLED;
    }

    public boolean isDeveloperMenuEnabled() {
        return mDeveloperMenuEnabled;
    }

    public void enableDeveloperMenu() {
        mDeveloperMenuEnabled = true;
    }

    private String fileNameFromDataID(int dataID) {
        final LocalData localData = mDataAdapter.getLocalData(dataID);

        File localFile = new File(localData.getPath());
        return localFile.getName();
    }

    private FilmStripView.Listener mFilmStripListener =
            new FilmStripView.Listener() {
                @Override
                public void onDataPromoted(int dataID) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_DELETE, "promoted", 0,
                            UsageStatistics.hashFileName(fileNameFromDataID(dataID)));

                    removeData(dataID);
                }

                @Override
                public void onDataDemoted(int dataID) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                            UsageStatistics.ACTION_DELETE, "demoted", 0,
                            UsageStatistics.hashFileName(fileNameFromDataID(dataID)));

                    removeData(dataID);
                }

                @Override
                public void onDataFullScreenChange(int dataID, boolean full) {
                    boolean isCameraID = isCameraPreview(dataID);
                    if (full && isCameraID && CameraActivity.this.hasWindowFocus()){
                        updateStorageSpaceAndHint();
                    }
                    if (!isCameraID) {
                        if (!full) {
                            // Always show action bar in filmstrip mode
                            CameraActivity.this.setSystemBarsVisibility(true, false);
                        } else if (mActionBar.isShowing()) {
                            // Hide action bar after time out in full screen mode
                            mMainHandler.sendEmptyMessageDelayed(HIDE_ACTION_BAR,
                                    SHOW_ACTION_BAR_TIMEOUT_MS);
                        }
                    }
                }

                /**
                 * Check if the local data corresponding to dataID is the camera
                 * preview.
                 *
                 * @param dataID the ID of the local data
                 * @return true if the local data is not null and it is the
                 *         camera preview.
                 */
                private boolean isCameraPreview(int dataID) {
                    LocalData localData = mDataAdapter.getLocalData(dataID);
                    if (localData == null) {
                        Log.w(TAG, "Current data ID not found.");
                        return false;
                    }
                    return localData.getLocalDataType() == LocalData.LOCAL_CAMERA_PREVIEW;
                }

                @Override
                public void onReload() {
                    setPreviewControlsVisibility(true);
                    CameraActivity.this.setSystemBarsVisibility(false);
                }

                @Override
                public void onCurrentDataCentered(int dataID) {
                    if (dataID != 0 && !mFilmStripView.isCameraPreview()) {
                        // For now, We ignore all items that are not the camera preview.
                        return;
                    }

                    if(!arePreviewControlsVisible()) {
                        setPreviewControlsVisibility(true);
                        CameraActivity.this.setSystemBarsVisibility(false);
                        mFilmStripView.getController().goToFullScreen();
                    }
                }

                @Override
                public void onCurrentDataOffCentered(int dataID) {
                    if (dataID != 0 && !mFilmStripView.isCameraPreview()) {
                        // For now, We ignore all items that are not the camera preview.
                        return;
                    }

                    if (arePreviewControlsVisible()) {
                        setPreviewControlsVisibility(false);
                    }
                }

                @Override
                public void onDataFocusChanged(final int dataID, final boolean focused) {
                    boolean isPreview = isCameraPreview(dataID);
                    boolean isFullScreen = mFilmStripView.inFullScreen();
                    if (isFullScreen && isPreview && CameraActivity.this.hasWindowFocus()){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateStorageSpaceAndHint();
                            }
                        });
                    }
                    // Delay hiding action bar if there is any user interaction
                    if (mMainHandler.hasMessages(HIDE_ACTION_BAR)) {
                        mMainHandler.removeMessages(HIDE_ACTION_BAR);
                        mMainHandler.sendEmptyMessageDelayed(HIDE_ACTION_BAR,
                                SHOW_ACTION_BAR_TIMEOUT_MS);
                    }
                    // TODO: This callback is UI event callback, should always
                    // happen on UI thread. Find the reason for this
                    // runOnUiThread() and fix it.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LocalData currentData = mDataAdapter.getLocalData(dataID);
                            if (currentData == null) {
                                Log.w(TAG, "Current data ID not found.");
                                hidePanoStitchingProgress();
                                return;
                            }
                            boolean isCameraID = currentData.getLocalDataType() ==
                                    LocalData.LOCAL_CAMERA_PREVIEW;
                            if (!focused) {
                                if (isCameraID) {
                                    mCurrentModule.onPreviewFocusChanged(false);
                                    CameraActivity.this.setSystemBarsVisibility(true);
                                }
                                hidePanoStitchingProgress();
                            } else {
                                if (isCameraID) {
                                    // Don't show the action bar in Camera
                                    // preview.
                                    CameraActivity.this.setSystemBarsVisibility(false);

                                    if (mPendingDeletion) {
                                        performDeletion();
                                    }
                                } else {
                                    updateActionBarMenu(dataID);
                                }

                                Uri contentUri = currentData.getContentUri();
                                if (contentUri == null) {
                                    hidePanoStitchingProgress();
                                    return;
                                }
                                int panoStitchingProgress = mPanoramaManager.getTaskProgress(
                                        contentUri);
                                if (panoStitchingProgress < 0) {
                                    hidePanoStitchingProgress();
                                    return;
                                }
                                showPanoStitchingProgress();
                                updateStitchingProgress(panoStitchingProgress);
                            }
                        }
                    });
                }

                @Override
                public void onToggleSystemDecorsVisibility(int dataID) {
                    // If action bar is showing, hide it immediately, otherwise
                    // show action bar and hide it later
                    if (mActionBar.isShowing()) {
                        CameraActivity.this.setSystemBarsVisibility(false);
                    } else {
                        // Don't show the action bar if that is the camera preview.
                        boolean isCameraID = isCameraPreview(dataID);
                        if (!isCameraID) {
                            CameraActivity.this.setSystemBarsVisibility(true, true);
                        }
                    }
                }

                @Override
                public void setSystemDecorsVisibility(boolean visible) {
                    CameraActivity.this.setSystemBarsVisibility(visible);
                }
            };

    public void gotoGallery() {
        LocalDataAdapter adapter = getDataAdapter();
        ImageData img = adapter.getImageData(1);
        if (img == null)
            return;
        Uri uri = img.getContentUri();
        if (mCurrentModule instanceof PhotoModule) {
            if (((PhotoModule) mCurrentModule).isRefocus()) {
                Intent intent = new Intent();
                intent.setClass(this, RefocusActivity.class);
                intent.setData(uri);
                startActivity(intent);
                return;
            }
        }
        if (mCurrentModule instanceof CaptureModule) {
            if (((CaptureModule) mCurrentModule).isRefocus()) {
                Intent intent = new Intent();
                intent.setClass(this, RefocusActivity.class);
                intent.setData(uri);
                intent.setFlags(RefocusActivity.MAP_ROTATED);
                if (isSecureCamera()) {
                    intent.setAction(INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                }
                startActivityForResult(intent, REFOCUS_ACTIVITY_CODE);
                return;
            }
        }
        try {
            Intent intent = IntentHelper.getGalleryIntent(this);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.putExtra(KEY_FROM_SNAPCAM, true);
            intent.putExtra(KEY_TOTAL_NUMBER, (adapter.getTotalNumber() -1));
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            try {
                Log.w(TAG, "Gallery not found");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.putExtra(KEY_FROM_SNAPCAM, true);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No Activity could be found to open image or video");
            }
        }
    }

    /**
     * If {@param visible} is false, this hides the action bar and switches the system UI
     * to lights-out mode.
     */
    // TODO: This should not be called outside of the activity.
    public void setSystemBarsVisibility(boolean visible) {
        setSystemBarsVisibility(visible, false);
    }

    /**
     * If {@param visible} is false, this hides the action bar and switches the
     * system UI to lights-out mode. If {@param hideLater} is true, a delayed message
     * will be sent after a timeout to hide the action bar.
     */
    private void setSystemBarsVisibility(boolean visible, boolean hideLater) {
        mMainHandler.removeMessages(HIDE_ACTION_BAR);

        View decorView = getWindow().getDecorView();
        int currentSystemUIVisibility = decorView.getSystemUiVisibility();
        boolean hidePreview = SystemProperties.getBoolean("camera.ui.no_navigation_bar", false);
        int systemUIVisibility = DEFAULT_SYSTEM_UI_VISIBILITY;
        int systemUINotVisible = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (hidePreview) {
            systemUIVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            systemUINotVisible |= (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        int newSystemUIVisibility = systemUIVisibility
                | (visible ? View.SYSTEM_UI_FLAG_VISIBLE : systemUINotVisible);

        if (newSystemUIVisibility != currentSystemUIVisibility) {
            decorView.setSystemUiVisibility(newSystemUIVisibility);
        }

        boolean currentActionBarVisibility = mActionBar.isShowing();
        if (visible != currentActionBarVisibility) {
            if (visible) {
                mActionBar.show();
            } else {
                mActionBar.hide();
            }
            if (mOnActionBarVisibilityListener != null) {
                mOnActionBarVisibilityListener.onActionBarVisibilityChanged(visible);
            }
        }

        // Now delay hiding the bars
        if (visible && hideLater) {
            mMainHandler.sendEmptyMessageDelayed(HIDE_ACTION_BAR, SHOW_ACTION_BAR_TIMEOUT_MS);
        }
    }

    private void hidePanoStitchingProgress() {
        mPanoStitchingPanel.setVisibility(View.GONE);
    }

    private void showPanoStitchingProgress() {
        mPanoStitchingPanel.setVisibility(View.VISIBLE);
    }

    private void updateStitchingProgress(int progress) {
        mBottomProgress.setProgress(progress);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(CameraActivity.this);
        if (adapter == null) {
            return;
        }

        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) {
            // Disable beaming
            adapter.setNdefPushMessage(null, CameraActivity.this);
            return;
        }

        adapter.setBeamPushUris(null, CameraActivity.this);
        adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
            @Override
            public Uri[] createBeamUris(NfcEvent event) {
                return mNfcPushUris;
            }
        }, CameraActivity.this);
    }

    private void setNfcBeamPushUri(Uri uri) {
        mNfcPushUris[0] = uri;
    }

    public LocalDataAdapter getDataAdapter() {
        return mDataAdapter;
    }

    private String getPathFromUri(Uri uri) {
        String[] projection = {
                MediaStore.Images.Media.DATA
        };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor == null)
            return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        String s = null;
        if (cursor.moveToFirst()) {
            s = cursor.getString(column_index);
        }
        cursor.close();
        return s;
    }

    public void updateThumbnail(final byte[] jpegData) {
        if (mUpdateThumbnailTask != null) mUpdateThumbnailTask.cancel(true);
        mUpdateThumbnailTask = new UpdateThumbnailTask(jpegData, true);
        mUpdateThumbnailTask.execute();
    }

    public void updateThumbnail(final Bitmap bitmap) {
        if (bitmap == null) return;
        mThumbnailDrawable = new CircularDrawable(bitmap);
        if (mThumbnail != null) {
            mThumbnail.setImageDrawable(mThumbnailDrawable);
            if (!isSecureCamera()) {
                mThumbnail.setVisibility(View.VISIBLE);
            } else {
                //under SecureCamera and UbiFocus mode, if click shutter button, if it is
                // Refocus,should display the thumbnail.
                if (mCurrentModule instanceof CaptureModule) {
                    if (((CaptureModule) mCurrentModule).isRefocus()) {
                        mThumbnail.setVisibility(View.VISIBLE);
                    } else {
                        mThumbnail.setVisibility(View.GONE);
                    }
                } else {
                    mThumbnail.setVisibility(View.GONE);
                }
            }
       }
    }

    public void updateThumbnail(ImageView thumbnail) {
        mThumbnail = thumbnail;
        if (mThumbnail == null) return;
        if (mThumbnailDrawable != null) {
            mThumbnail.setImageDrawable(mThumbnailDrawable);
            if (!isSecureCamera()) {
                mThumbnail.setVisibility(View.VISIBLE);
            } else {
                //under SecureCamera and UbiFocus mode, when back from RefocusActivity,if not save
                // image and is Refocus ,still need to display the thumbnail.
                if (mCurrentModule instanceof CaptureModule) {
                    if (((CaptureModule) mCurrentModule).isRefocus()) {
                        mThumbnail.setVisibility(View.VISIBLE);
                    } else {
                        mThumbnail.setVisibility(View.GONE);
                    }
                } else {
                    mThumbnail.setVisibility(View.GONE);
                }
            }
        }
    }

    public void updateThumbnail(boolean videoOnly) {
        // Only handle OnDataInserted if it's video.
        // Photo and Panorama have their own way of updating thumbnail.
        if (!videoOnly || (mCurrentModule instanceof VideoModule) ||
                ((mCurrentModule instanceof CaptureModule) && videoOnly)) {
            (new UpdateThumbnailTask(null, true)).execute();
        }
    }

    private class UpdateThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
        private byte[] mJpegData;
        private boolean mCheckOrientation;

        public UpdateThumbnailTask(final byte[] jpegData, boolean checkOrientation) {
            mJpegData = jpegData;
            mCheckOrientation = checkOrientation;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (mJpegData != null)
                return decodeImageCenter(null);

            LocalDataAdapter adapter = getDataAdapter();
            ImageData img = adapter.getImageData(1);
            if (img == null) {
                return null;
            }
            Uri uri = img.getContentUri();
            String path = getPathFromUri(uri);
            if (path == null) {
                return null;
            }
            else {
                if (img.isPhoto()) {
                    return decodeImageCenter(path);
                } else {
                    return ThumbnailUtils
                            .createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                }
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                if (mThumbnail != null) {
                    // Clear the image resource when the bitmap is invalid.
                    mThumbnail.setImageDrawable(null);
                    mThumbnail.setVisibility(View.GONE);
                }
            } else {
                updateThumbnail(bitmap);
            }

            mJpegData = null;
        }

        @Override
        protected void onCancelled(Bitmap bitmap) {
            if(bitmap != null)
                bitmap.recycle();

            bitmap = null;
            mJpegData = null;
        }

        private Bitmap decodeImageCenter(final String path) {
            // Check photo orientation for Panorama. This is necessary during app launch because
            // Panorama module generates thumbnail bitmap with orientation adjustment but only
            // saves jpeg with orientation tag set.
            int orientation = 0;
            if (mCheckOrientation) {
                ExifInterface exif = new ExifInterface();
                try {
                    if (mJpegData != null) {
                        exif.readExif(mJpegData);
                    } else {
                        exif.readExif(path);
                    }
                    orientation = Exif.getOrientation(exif);
                } catch (IOException e) {
                    // ignore
                }
            }

            final BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            if (mJpegData != null) {
                BitmapFactory.decodeByteArray(mJpegData, 0, mJpegData.length, opt);
            } else {
                BitmapFactory.decodeFile(path, opt);
            }

            int w = opt.outWidth;
            int h = opt.outHeight;
            int d = w > h ? h : w;

            final int target = getResources().getDimensionPixelSize(R.dimen.capture_size);
            int sample = 1;
            if (d > target) {
                while (d / sample / 2 > target) {
                    sample *= 2;
                }
            }
            int st = sample * target;
            final Rect rect = new Rect((w - st) / 2, (h - st) / 2, (w + st) / 2, (h + st) / 2);

            opt.inJustDecodeBounds = false;
            opt.inSampleSize = sample;
            final BitmapRegionDecoder decoder;
            try {
                if (mJpegData == null) {
                    decoder = BitmapRegionDecoder.newInstance(path, true);
                } else {
                    decoder = BitmapRegionDecoder.newInstance(mJpegData, 0, mJpegData.length, true);
                }
            } catch (IOException e) {
                return null;
            }
            Bitmap bitmap = decoder.decodeRegion(rect, opt);
            if (orientation != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate(orientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            }
            return bitmap;
        }
    }

    private class CircularDrawable extends Drawable {
        private final BitmapShader mBitmapShader;
        private final Paint mPaint;
        private Rect mRect;
        private int mLength;

        public CircularDrawable(Bitmap bitmap) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int targetSize = getResources().getDimensionPixelSize(R.dimen.capture_size);
            if (Math.min(w, h) < targetSize) {
                Matrix matrix = new Matrix();
                float scale = 1.0f;
                if (w > h) {
                    scale = (float) targetSize / (float) h;
                } else {
                    scale = (float) targetSize / (float) w;
                }
                matrix.postScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
                w = (int) (w * scale);
                h = (int) (h * scale);
            }
            if (w > h) {
                mLength = h;
                bitmap = Bitmap.createBitmap(bitmap, (w - h) / 2, 0, h, h);
            } else if (w < h) {
                mLength = w;
                bitmap = Bitmap.createBitmap(bitmap, 0, (h - w) / 2, w, w);
            } else {
                mLength = w;
            }

            mBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setShader(mBitmapShader);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mRect = bounds;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(new RectF(mRect), (mRect.right - mRect.left) / 2,
                    (mRect.bottom - mRect.top) / 2, mPaint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
        }

        @Override
        public int getIntrinsicWidth() {
            return mLength;
        }

        @Override
        public int getIntrinsicHeight() {
            return mLength;
        }
    }

    private void setStandardShareIntent(Uri contentUri, String mimeType) {
        mStandardShareIntent = getShareIntentFromType(mimeType);
        if (mStandardShareIntent != null) {
            mStandardShareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            mStandardShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (mStandardShareActionProvider != null) {
                mStandardShareActionProvider.setShareIntent(mStandardShareIntent);
            }
        }
    }

    /**
     * Get the share intent according to the mimeType
     *
     * @param mimeType The mimeType of current data.
     * @return the video/image's ShareIntent or null if mimeType is invalid.
     */
    private Intent getShareIntentFromType(String mimeType) {
        // Lazily create the intent object.
        if (mimeType.startsWith("video/")) {
            if (mVideoShareIntent == null) {
                mVideoShareIntent = new Intent(Intent.ACTION_SEND);
                mVideoShareIntent.setType("video/*");
            }
            return mVideoShareIntent;
        } else if (mimeType.startsWith("image/")) {
            if (mImageShareIntent == null) {
                mImageShareIntent = new Intent(Intent.ACTION_SEND);
                mImageShareIntent.setType("image/*");
            }
            return mImageShareIntent;
        }
        Log.w(TAG, "unsupported mimeType " + mimeType);
        return null;
    }

    private void setPanoramaShareIntent(Uri contentUri) {
        if (mPanoramaShareIntent == null) {
            mPanoramaShareIntent = new Intent(Intent.ACTION_SEND);
        }
        mPanoramaShareIntent.setType("application/vnd.google.panorama360+jpg");
        mPanoramaShareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        if (mPanoramaShareActionProvider != null) {
            mPanoramaShareActionProvider.setShareIntent(mPanoramaShareIntent);
        }
    }

    @Override
    public void onMenuVisibilityChanged(boolean isVisible) {
        // If menu is showing, we need to make sure action bar does not go away.
        mMainHandler.removeMessages(HIDE_ACTION_BAR);
        if (!isVisible) {
            mMainHandler.sendEmptyMessageDelayed(HIDE_ACTION_BAR, SHOW_ACTION_BAR_TIMEOUT_MS);
        }
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
        int currentDataId = mFilmStripView.getCurrentId();
        if (currentDataId < 0) {
            return false;
        }
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA, UsageStatistics.ACTION_SHARE,
                intent.getComponent().getPackageName(), 0,
                UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)));
        return true;
    }

    /**
     * According to the data type, make the menu items for supported operations
     * visible.
     *
     * @param dataID the data ID of the current item.
     */
    private void updateActionBarMenu(int dataID) {
        LocalData currentData = mDataAdapter.getLocalData(dataID);
        if (currentData == null) {
            return;
        }
        int type = currentData.getLocalDataType();

        if (mActionBarMenu == null) {
            return;
        }

        int supported = 0;

        switch (type) {
            case LocalData.LOCAL_IMAGE:
                supported |= SUPPORT_DELETE | SUPPORT_ROTATE | SUPPORT_INFO
                        | SUPPORT_CROP | SUPPORT_SETAS | SUPPORT_EDIT
                        | SUPPORT_SHARE | SUPPORT_SHOW_ON_MAP;
                break;
            case LocalData.LOCAL_VIDEO:
                supported |= SUPPORT_DELETE | SUPPORT_INFO | SUPPORT_SHARE;
                break;
            case LocalData.LOCAL_PHOTO_SPHERE:
                supported |= SUPPORT_DELETE | SUPPORT_ROTATE | SUPPORT_INFO
                        | SUPPORT_CROP | SUPPORT_SETAS | SUPPORT_EDIT
                        | SUPPORT_SHARE | SUPPORT_SHOW_ON_MAP;
                break;
            case LocalData.LOCAL_360_PHOTO_SPHERE:
                supported |= SUPPORT_DELETE | SUPPORT_ROTATE | SUPPORT_INFO
                        | SUPPORT_CROP | SUPPORT_SETAS | SUPPORT_EDIT
                        | SUPPORT_SHARE | SUPPORT_SHARE_PANORAMA360
                        | SUPPORT_SHOW_ON_MAP;
                break;
            default:
                break;
        }

        // In secure camera mode, we only support delete operation.
        if (isSecureCamera()) {
            supported &= SUPPORT_DELETE;
        }

        setMenuItemVisible(mActionBarMenu, R.id.action_delete,
                (supported & SUPPORT_DELETE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_rotate_ccw,
                (supported & SUPPORT_ROTATE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_rotate_cw,
                (supported & SUPPORT_ROTATE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_details,
                (supported & SUPPORT_INFO) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_crop,
                (supported & SUPPORT_CROP) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_setas,
                (supported & SUPPORT_SETAS) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_edit,
                (supported & SUPPORT_EDIT) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_trim,
                (supported & SUPPORT_TRIM) != 0);

        boolean standardShare = (supported & SUPPORT_SHARE) != 0;
        boolean panoramaShare = (supported & SUPPORT_SHARE_PANORAMA360) != 0;
        setMenuItemVisible(mActionBarMenu, R.id.action_share, standardShare);
        setMenuItemVisible(mActionBarMenu, R.id.action_share_panorama, panoramaShare);

        if (panoramaShare) {
            // For 360 PhotoSphere, relegate standard share to the overflow menu
            MenuItem item = mActionBarMenu.findItem(R.id.action_share);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                item.setTitle(getResources().getString(R.string.share_as_photo));
            }
            // And, promote "share as panorama" to action bar
            item = mActionBarMenu.findItem(R.id.action_share_panorama);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            setPanoramaShareIntent(currentData.getContentUri());
        }
        if (standardShare) {
            if (!panoramaShare) {
                MenuItem item = mActionBarMenu.findItem(R.id.action_share);
                if (item != null) {
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    item.setTitle(getResources().getString(R.string.share));
                }
            }
            setStandardShareIntent(currentData.getContentUri(), currentData.getMimeType());
            setNfcBeamPushUri(currentData.getContentUri());
        }

        boolean itemHasLocation = currentData.getLatLong() != null;
        setMenuItemVisible(mActionBarMenu, R.id.action_show_on_map,
                itemHasLocation && (supported & SUPPORT_SHOW_ON_MAP) != 0);
    }

    private void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item != null)
            item.setVisible(visible);
    }

    private ImageTaskManager.TaskListener mPlaceholderListener =
            new ImageTaskManager.TaskListener() {

                @Override
                public void onTaskQueued(String filePath, final Uri imageUri) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyNewMedia(imageUri);
                            int dataID = mDataAdapter.findDataByContentUri(imageUri);
                            if (dataID != -1) {
                                LocalData d = mDataAdapter.getLocalData(dataID);
                                InProgressDataWrapper newData = new InProgressDataWrapper(d, true);
                                mDataAdapter.updateData(dataID, newData);
                            }
                        }
                    });
                }

                @Override
                public void onTaskDone(String filePath, final Uri imageUri) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mDataAdapter.refresh(getContentResolver(), imageUri);
                        }
                    });
                }

                @Override
                public void onTaskProgress(String filePath, Uri imageUri, int progress) {
                    // Do nothing
                }
    };

    private ImageTaskManager.TaskListener mStitchingListener =
            new ImageTaskManager.TaskListener() {
                @Override
                public void onTaskQueued(String filePath, final Uri imageUri) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyNewMedia(imageUri);
                            int dataID = mDataAdapter.findDataByContentUri(imageUri);
                            if (dataID != -1) {
                                // Don't allow special UI actions (swipe to
                                // delete, for example) on in-progress data.
                                LocalData d = mDataAdapter.getLocalData(dataID);
                                InProgressDataWrapper newData = new InProgressDataWrapper(d);
                                mDataAdapter.updateData(dataID, newData);
                            }
                        }
                    });
                }

                @Override
                public void onTaskDone(String filePath, final Uri imageUri) {
                    Log.v(TAG, "onTaskDone:" + filePath);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            int doneID = mDataAdapter.findDataByContentUri(imageUri);
                            int currentDataId = mFilmStripView.getCurrentId();

                            if (currentDataId == doneID) {
                                hidePanoStitchingProgress();
                                updateStitchingProgress(0);
                            }

                            mDataAdapter.refresh(getContentResolver(), imageUri);
                        }
                    });
                }

                @Override
                public void onTaskProgress(
                        String filePath, final Uri imageUri, final int progress) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            int currentDataId = mFilmStripView.getCurrentId();
                            if (currentDataId == -1) {
                                return;
                            }
                            if (imageUri.equals(
                                    mDataAdapter.getLocalData(currentDataId).getContentUri())) {
                                updateStitchingProgress(progress);
                            }
                        }
                    });
                }
            };

    public MediaSaveService getMediaSaveService() {
        return mMediaSaveService;
    }

    public void notifyNewMedia(Uri uri) {
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType == null) {
            Log.e(TAG, "mimeType is NULL");
            return;
        }
        if (mimeType.startsWith("video/")) {
            sendBroadcast(new Intent(CameraUtil.ACTION_NEW_VIDEO, uri));
            mDataAdapter.addNewVideo(cr, uri);
        } else if (mimeType.startsWith("image/")) {
            CameraUtil.broadcastNewPicture(this, uri);
            mDataAdapter.addNewPhoto(cr, uri);
        } else if (mimeType.startsWith("application/stitching-preview")) {
            mDataAdapter.addNewPhoto(cr, uri);
        } else if (mimeType.startsWith(PlaceholderManager.PLACEHOLDER_MIME_TYPE)) {
            mDataAdapter.addNewPhoto(cr, uri);
        } else {
            android.util.Log.w(TAG, "Unknown new media with MIME type:"
                    + mimeType + ", uri:" + uri);
        }
    }

    private void removeData(int dataID) {
        mDataAdapter.removeData(CameraActivity.this, dataID);
        if (mDataAdapter.getTotalNumber() > 1) {
            showUndoDeletionBar();
        } else {
            // If camera preview is the only view left in filmstrip,
            // no need to show undo bar.
            mPendingDeletion = true;
            performDeletion();
        }
    }

    private void bindMediaSaveService() {
        Intent intent = new Intent(this, MediaSaveService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindMediaSaveService() {
        if (mConnection != null) {
            unbindService(mConnection);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.operations, menu);
        mActionBarMenu = menu;

        // Configure the standard share action provider
        MenuItem item = menu.findItem(R.id.action_share);
        mStandardShareActionProvider = (ShareActionProvider) item.getActionProvider();
        mStandardShareActionProvider.setShareHistoryFileName("standard_share_history.xml");
        if (mStandardShareIntent != null) {
            mStandardShareActionProvider.setShareIntent(mStandardShareIntent);
        }

        // Configure the panorama share action provider
        item = menu.findItem(R.id.action_share_panorama);
        mPanoramaShareActionProvider = (ShareActionProvider) item.getActionProvider();
        mPanoramaShareActionProvider.setShareHistoryFileName("panorama_share_history.xml");
        if (mPanoramaShareIntent != null) {
            mPanoramaShareActionProvider.setShareIntent(mPanoramaShareIntent);
        }

        mStandardShareActionProvider.setOnShareTargetSelectedListener(this);
        mPanoramaShareActionProvider.setOnShareTargetSelectedListener(this);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int currentDataId = mFilmStripView.getCurrentId();
        if (currentDataId < 0) {
            return false;
        }
        final LocalData localData = mDataAdapter.getLocalData(currentDataId);

        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case android.R.id.home:
                // ActionBar's Up/Home button was clicked
                try {
                    startActivity(IntentHelper.getGalleryIntent(this));
                    return true;
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "Failed to launch gallery activity, closing");
                    finish();
                }
            case R.id.action_delete:
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                        UsageStatistics.ACTION_DELETE, null, 0,
                        UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)));
                removeData(currentDataId);
                return true;
            case R.id.action_edit:
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                        UsageStatistics.ACTION_EDIT, null, 0,
                        UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)));
                launchEditor(localData);
                return true;
            case R.id.action_trim: {
                // This is going to be handled by the Gallery app.
                Intent intent = new Intent(ACTION_TRIM_VIDEO);
                LocalData currentData = mDataAdapter.getLocalData(
                        mFilmStripView.getCurrentId());
                intent.setData(currentData.getContentUri());
                // We need the file path to wrap this into a RandomAccessFile.
                intent.putExtra(MEDIA_ITEM_PATH, currentData.getPath());
                startActivityForResult(intent, REQ_CODE_DONT_SWITCH_TO_PREVIEW);
                return true;
            }
            case R.id.action_rotate_ccw:
                localData.rotate90Degrees(this, mDataAdapter, currentDataId, false);
                return true;
            case R.id.action_rotate_cw:
                localData.rotate90Degrees(this, mDataAdapter, currentDataId, true);
                return true;
            case R.id.action_crop: {
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                        UsageStatistics.ACTION_CROP, null, 0,
                        UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)));
                Intent intent = new Intent(CropActivity.CROP_ACTION);
                intent.setClass(this, CropActivity.class);
                intent.setDataAndType(localData.getContentUri(), localData.getMimeType())
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, REQ_CODE_DONT_SWITCH_TO_PREVIEW);
                return true;
            }
            case R.id.action_setas: {
                Intent intent = new Intent(Intent.ACTION_ATTACH_DATA)
                        .setDataAndType(localData.getContentUri(),
                                localData.getMimeType())
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra("mimeType", intent.getType());
                startActivityForResult(Intent.createChooser(
                        intent, getString(R.string.set_as)), REQ_CODE_DONT_SWITCH_TO_PREVIEW);
                return true;
            }
            case R.id.action_details:
                (new AsyncTask<Void, Void, MediaDetails>() {
                    @Override
                    protected MediaDetails doInBackground(Void... params) {
                        return localData.getMediaDetails(CameraActivity.this);
                    }

                    @Override
                    protected void onPostExecute(MediaDetails mediaDetails) {
                        if ((mediaDetails != null) && !mPaused) {
                            DetailsDialog.create(CameraActivity.this, mediaDetails).show();
                        }
                    }
                }).execute();
                return true;
            case R.id.action_show_on_map:
                double[] latLong = localData.getLatLong();
                if (latLong != null) {
                    CameraUtil.showOnMap(this, latLong);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isCaptureIntent() {
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            return true;
        } else {
            return false;
        }
    }

    private BroadcastReceiver mAutoTestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("KEY") && intent.hasExtra("VALUE")) {
                String key = intent.getExtras().getString("KEY");
                String value = intent.getExtras().getString("VALUE");
                if (mCurrentModule != null) {
                    mCurrentModule.setPreferenceForTest(key,value);
                }
            }
        }
    };

    private  void registerAutoTestReceiver() {
        IntentFilter filter = new IntentFilter(AUTO_TEST_INTENT);
        registerReceiver(mAutoTestReceiver, filter);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Check if this is in the secure camera mode.
        Intent intent = getIntent();
        String action = intent.getAction();
        if (INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)
                || ACTION_IMAGE_CAPTURE_SECURE.equals(action)
                || intent.getComponent().getClassName().equals(GESTURE_CAMERA_NAME)) {
            mSecureCamera = true;
        } else {
            mSecureCamera = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
        }

        if (mSecureCamera) {
            // Change the window flags so that secure camera can show when locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            if (intent.getComponent().getClassName().equals(GESTURE_CAMERA_NAME)) {
                params.flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
                PowerManager pm = ((PowerManager) getSystemService(POWER_SERVICE));
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                mWakeLock.acquire();
                Log.d(TAG, "acquire wake lock");
            }
            win.setAttributes(params);
        }

        if (mSecureCamera && !hasCriticalPermissions()) {
            return;
        }

        if (isStartRequsetPermission()) {
            Log.v(TAG, "onCreate: Missing critical permissions.");
            finish();
            return;
        }

        mCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, null, null, null);
        GcamHelper.init(getContentResolver());

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);

        LayoutInflater inflater = getLayoutInflater();
        View rootLayout = inflater.inflate(R.layout.camera, null, false);
        mCameraRootFrame = (FrameLayout)rootLayout.findViewById(R.id.camera_root_frame);
        mCameraPhotoModuleRootView = rootLayout.findViewById(R.id.camera_photo_root);
        mCameraVideoModuleRootView = rootLayout.findViewById(R.id.camera_video_root);
        mCameraPanoModuleRootView = rootLayout.findViewById(R.id.camera_pano_root);
        mCameraCaptureModuleRootView = rootLayout.findViewById(R.id.camera_capture_root);

        int moduleIndex = -1;
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            moduleIndex = ModuleSwitcher.VIDEO_MODULE_INDEX;
        } else if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(getIntent().getAction())
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(getIntent()
                .getAction())) {
            moduleIndex = ModuleSwitcher.PHOTO_MODULE_INDEX;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getInt(CameraSettings.KEY_STARTUP_MODULE_INDEX, -1)
                    == ModuleSwitcher.GCAM_MODULE_INDEX && GcamHelper.hasGcamCapture()) {
                moduleIndex = ModuleSwitcher.GCAM_MODULE_INDEX;
            }
        } else if (MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            moduleIndex = ModuleSwitcher.PHOTO_MODULE_INDEX;
        } else {
            // If the activity has not been started using an explicit intent,
            // read the module index from the last time the user changed modes
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            moduleIndex = prefs.getInt(CameraSettings.KEY_STARTUP_MODULE_INDEX, -1);
            if ((moduleIndex == ModuleSwitcher.GCAM_MODULE_INDEX &&
                    !GcamHelper.hasGcamCapture()) || moduleIndex < 0) {
                moduleIndex = ModuleSwitcher.PHOTO_MODULE_INDEX;
            }
        }

        boolean cam2on = PersistUtil.getCamera2Mode();
        if (cam2on && (moduleIndex == ModuleSwitcher.PHOTO_MODULE_INDEX ||
                moduleIndex == ModuleSwitcher.VIDEO_MODULE_INDEX))
            moduleIndex = ModuleSwitcher.CAPTURE_MODULE_INDEX;

        mOrientationListener = new MyOrientationEventListener(this);
        setContentView(R.layout.camera_filmstrip);
        mFilmStripView = (FilmStripView) findViewById(R.id.filmstrip_view);
        setModuleFromIndex(moduleIndex);

        mActionBar = getActionBar();
        mActionBar.addOnMenuVisibilityListener(this);

        if (ApiHelper.HAS_ROTATION_ANIMATION) {
            setRotationAnimation();
        }

        mMainHandler = new MainHandler(getMainLooper());

        mAboveFilmstripControlLayout =
                (FrameLayout) findViewById(R.id.camera_above_filmstrip_layout);
        mAboveFilmstripControlLayout.setFitsSystemWindows(true);
        mPanoramaManager = AppManagerFactory.getInstance(this)
                .getPanoramaStitchingManager();
        mPlaceholderManager = AppManagerFactory.getInstance(this)
                .getGcamProcessingManager();
        mPanoramaManager.addTaskListener(mStitchingListener);
        mPlaceholderManager.addTaskListener(mPlaceholderListener);
        mPanoStitchingPanel = findViewById(R.id.pano_stitching_progress_panel);
        mBottomProgress = (ProgressBar) findViewById(R.id.pano_stitching_progress_bar);
        mCameraPreviewData = new CameraPreviewData(rootLayout,
                FilmStripView.ImageData.SIZE_FULL,
                FilmStripView.ImageData.SIZE_FULL);
        // Put a CameraPreviewData at the first position.
        mWrappedDataAdapter = new FixedFirstDataAdapter(
                new CameraDataAdapter(new ColorDrawable(
                        getResources().getColor(R.color.photo_placeholder))),
                mCameraPreviewData);

        mFilmStripView.setViewGap(
                getResources().getDimensionPixelSize(R.dimen.camera_film_strip_gap));
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        mFilmStripView.setPanoramaViewHelper(mPanoramaViewHelper);
        // Set up the camera preview first so the preview shows up ASAP.
        mFilmStripView.setListener(mFilmStripListener);

        if (!mSecureCamera) {
            mDataAdapter = mWrappedDataAdapter;
            mFilmStripView.setDataAdapter(mDataAdapter);
            if (!isCaptureIntent()) {
                mDataAdapter.requestLoad(getContentResolver());
                mDataRequested = true;
            }
        } else {
            // Put a lock placeholder as the last image by setting its date to
            // 0.
            ImageView v = (ImageView) getLayoutInflater().inflate(
                    R.layout.secure_album_placeholder, null);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                                UsageStatistics.ACTION_GALLERY, null);
                        startActivity(IntentHelper.getGalleryIntent(CameraActivity.this));
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Failed to launch gallery activity, closing");
                    }
                    finish();
                }
            });
            mDataAdapter = new FixedLastDataAdapter(
                    mWrappedDataAdapter,
                    new SimpleViewData(
                            v,
                            v.getDrawable().getIntrinsicWidth(),
                            v.getDrawable().getIntrinsicHeight(),
                            0, 0));
            // Flush out all the original data.
            mDataAdapter.flush();
            mFilmStripView.setDataAdapter(mDataAdapter);
        }

        setupNfcBeamPush();

        mLocalImagesObserver = new LocalMediaObserver();
        mLocalVideosObserver = new LocalMediaObserver();

        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
                mLocalImagesObserver);
        getContentResolver().registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true,
                mLocalVideosObserver);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDeveloperMenuEnabled = prefs.getBoolean(CameraSettings.KEY_DEVELOPER_MENU, false);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        int lower = Math.min(width, height);

        int offset = lower * 7 / 100;
        SETTING_LIST_WIDTH_1 = lower / 2 + offset;
        SETTING_LIST_WIDTH_2 = lower / 2 - offset;
        registerSDcardMountedReceiver();
        mAutoTestEnabled = SystemProperties.getBoolean("camera.ui.auto_test", false);

        if (mAutoTestEnabled) {
            registerAutoTestReceiver();
        }
    }

    private void setRotationAnimation() {
        int rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
        rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.rotationAnimation = rotationAnimation;
        win.setAttributes(winParams);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (mCurrentModule != null) {
            mCurrentModule.onUserInteraction();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean result = false;
        if (mFilmStripView.checkSendToModeView(ev)) {
            result = mFilmStripView.sendToModeView(ev);
        }
        if (result == false)
            result = super.dispatchTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Real deletion is postponed until the next user interaction after
            // the gesture that triggers deletion. Until real deletion is performed,
            // users can click the undo button to bring back the image that they
            // chose to delete.
            if (mPendingDeletion && !mIsUndoingDeletion) {
                 performDeletion();
            }
        }
        return result;
    }

    @Override
    public void onPause() {
        if (mSecureCamera && !hasCriticalPermissions()) {
            super.onPause();
            return;
        }
        // Delete photos that are pending deletion
        performDeletion();
        mOrientationListener.disable();
        mCurrentModule.onPauseBeforeSuper();
        super.onPause();
        mCurrentModule.onPauseAfterSuper();

        mPaused = true;
        mLocalImagesObserver.setActivityPaused(true);
        mLocalVideosObserver.setActivityPaused(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_DONT_SWITCH_TO_PREVIEW) {
            mResetToPreviewOnResume = false;
            mIsEditActivityInProgress = false;
        } else if (requestCode == REFOCUS_ACTIVITY_CODE)  {
            if(resultCode == RESULT_OK) {
                mCaptureModule.setRefocusLastTaken(false);
            }
        } else if (requestCode == BestpictureActivity.BESTPICTURE_ACTIVITY_CODE)  {
            if(resultCode == RESULT_OK) {
                byte[] jpeg = data.getByteArrayExtra("thumbnail");
                if(jpeg != null) {
                    updateThumbnail(jpeg);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        // Hide action bar first since we are in full screen mode first, and
        // switch the system UI to lights-out mode.
        if (focus) this.setSystemBarsVisibility(false);
    }

    /**
     * Checks if any of the needed Android runtime permissions are missing.
     * If they are, then launch the permissions activity under one of the following conditions:
     * a) If critical permissions are missing, display permission request again
     * b) If non-critical permissions are missing, just display permission request once.
     * Critical permissions are: camera, microphone and storage. The app cannot run without them.
     * Non-critical permission is location.
     */
    private boolean hasCriticalPermissions() {
        boolean hasCriticalPermission = false;
        if (checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED) {
            hasCriticalPermission = true;
        } else {
            hasCriticalPermission = false;
        }
        return hasCriticalPermission;
    }

    private boolean isStartRequsetPermission() {
        boolean isStartPermissionActivity = false;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isRequestShown = prefs.getBoolean(CameraSettings.KEY_REQUEST_PERMISSION, false);

        if(!mSecureCamera && (!isRequestShown || !hasCriticalPermissions())) {
            Log.v(TAG, "Start Request Permission");
            Intent intent = new Intent(this, PermissionsActivity.class);
            startActivity(intent);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(CameraSettings.KEY_REQUEST_PERMISSION, true);
            editor.apply();
            isStartPermissionActivity = true;
        }
        return isStartPermissionActivity;
    }

    @Override
    public void onResume() {
        if (mSecureCamera && !hasCriticalPermissions()) {
            super.onResume();
            showOpenCameraErrorDialog();
            return;
        }
        if (isStartRequsetPermission()) {
            super.onResume();
            Log.v(TAG, "onResume: Missing critical permissions.");
            finish();
            return;
        }
        SettingsManager settingsManager = SettingsManager.getInstance();
        if (settingsManager == null) {
            SettingsManager.createInstance(this);
        }
        // Hide action bar first since we are in full screen mode first, and
        // switch the system UI to lights-out mode.
        this.setSystemBarsVisibility(false);

        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                UsageStatistics.ACTION_FOREGROUNDED, this.getClass().getSimpleName());

        mOrientationListener.enable();
        mCurrentModule.onResumeBeforeSuper();
        super.onResume();
        mPaused = false;
        mCurrentModule.onResumeAfterSuper();

        setSwipingEnabled(true);

        if (mResetToPreviewOnResume) {
            // Go to the preview on resume.
            mFilmStripView.getController().goToFirstItem();
        }
        // Default is showing the preview, unless disabled by explicitly
        // starting an activity we want to return from to the filmstrip rather
        // than the preview.
        mResetToPreviewOnResume = true;

        if (mLocalVideosObserver.isMediaDataChangedDuringPause()
                || mLocalImagesObserver.isMediaDataChangedDuringPause()) {
            if (!mSecureCamera) {
                // If it's secure camera, requestLoad() should not be called
                // as it will load all the data.
                mDataAdapter.requestLoad(getContentResolver());
                mThumbnailDrawable = null;
            }
        }
        mLocalImagesObserver.setActivityPaused(false);
        mLocalVideosObserver.setActivityPaused(false);

        //This is a temporal solution to share LED resource
        //as Android doesn’t have any default intent to share the state.
        // if the led flash light is open, turn it off
        Log.d(TAG, "send the turn off Flashlight broadcast");
        Intent intent = new Intent("org.codeaurora.snapcam.action.CLOSE_FLASHLIGHT");
        intent.putExtra("camera_led", true);
        sendBroadcast(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mSecureCamera && !hasCriticalPermissions()) {
            return;
        }
        bindMediaSaveService();
        mPanoramaViewHelper.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mSecureCamera && !hasCriticalPermissions()) {
            return;
        }
        mPanoramaViewHelper.onStop();
        unbindMediaSaveService();
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            Log.d(TAG, "wake lock release");
        }
        if (mCursor != null) {
            getContentResolver().unregisterContentObserver(mLocalImagesObserver);
            getContentResolver().unregisterContentObserver(mLocalVideosObserver);
            unregisterReceiver(mSDcardMountedReceiver);

            mCursor.close();
            mCursor=null;
        }
        if (mAutoTestEnabled) {
            unregisterReceiver(mAutoTestReceiver);
        }
        if(mCurrentModule != null){
            mCurrentModule.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mFilmStripView.inCameraFullscreen()) {
            if (mCurrentModule.onKeyDown(keyCode, event)) {
                return true;
            }
            // Prevent software keyboard or voice search from showing up.
            if (keyCode == KeyEvent.KEYCODE_SEARCH
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mFilmStripView.inCameraFullscreen() && mCurrentModule.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!mFilmStripView.inCameraFullscreen()) {
            mFilmStripView.getController().goToFirstItem();
            mCurrentModule.resizeForPreviewAspectRatio();
        } else if (!mCurrentModule.onBackPressed()) {
            super.onBackPressed();
        }
    }

    public void setPreviewGestures(PreviewGestures previewGestures) {
        mFilmStripView.setPreviewGestures(previewGestures);
    }

    protected long updateStorageSpace() {
        synchronized (mStorageSpaceLock) {
            mStorageSpaceBytes = Storage.getAvailableSpace();
            if (Storage.switchSavePath()) {
                mStorageSpaceBytes = Storage.getAvailableSpace();
                mMainHandler.sendEmptyMessage(SWITCH_SAVE_PATH);
            }
            return mStorageSpaceBytes;
        }
    }

    protected long getStorageSpaceBytes() {
        synchronized (mStorageSpaceLock) {
            return mStorageSpaceBytes;
        }
    }

    protected void updateStorageSpaceAndHint() {
        updateStorageSpace();
        updateStorageHint(mStorageSpaceBytes);
    }

    protected interface OnStorageUpdateDoneListener {
        void onStorageUpdateDone(long storageSpace);
    }

    protected void updateStorageSpaceAndHint(final OnStorageUpdateDoneListener callback) {
        (new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void ... arg) {
                return updateStorageSpace();
            }

            @Override
            protected void onPostExecute(Long storageSpace) {
                updateStorageHint(storageSpace);
                // This callback returns after I/O to check disk, so we could be
                // pausing and shutting down. If so, don't bother invoking.
                if (callback != null && !mPaused) {
                    callback.onStorageUpdateDone(storageSpace);
                } else {
                    Log.v(TAG, "ignoring storage callback after activity pause");
                }
            }
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    protected void updateStorageHint(long storageSpace) {
        String message = null;
        if (storageSpace == Storage.UNAVAILABLE) {
            message = getString(R.string.no_storage);
        } else if (storageSpace == Storage.PREPARING) {
            message = getString(R.string.preparing_sd);
        } else if (storageSpace == Storage.UNKNOWN_SIZE) {
            message = getString(R.string.access_sd_fail);
        } else if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            message = getString(R.string.spaceIsLow_content);
        }

        if (message != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, message);
            } else {
                mStorageHint.setText(message);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    protected void setResultEx(int resultCode) {
        mResultCodeForTesting = resultCode;
        setResult(resultCode);
    }

    protected void setResultEx(int resultCode, Intent data) {
        mResultCodeForTesting = resultCode;
        mResultDataForTesting = data;
        setResult(resultCode, data);
    }

    public int getResultCode() {
        return mResultCodeForTesting;
    }

    public Intent getResultData() {
        return mResultDataForTesting;
    }

    public boolean isSecureCamera() {
        return mSecureCamera;
    }

    public void requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Request Location permission");
            mCurrentModule.waitingLocationPermissionResult(true);
            requestPermissions(
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                mCurrentModule.waitingLocationPermissionResult(false);
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Location permission is granted");
                    mCurrentModule.enableRecordingLocation(true);
                } else {
                    Log.w(TAG, "Location permission is denied");
                    mCurrentModule.enableRecordingLocation(false);
                }
                break;
            }
        }
    }

    public boolean isForceReleaseCamera() {
        return mForceReleaseCamera;
    }

    @Override
    public void onModuleSelected(int moduleIndex) {
        boolean cam2on = PersistUtil.getCamera2Mode();
        mForceReleaseCamera = moduleIndex == ModuleSwitcher.CAPTURE_MODULE_INDEX ||
                (cam2on && moduleIndex == ModuleSwitcher.PHOTO_MODULE_INDEX);
        if (mForceReleaseCamera) {
            moduleIndex = ModuleSwitcher.CAPTURE_MODULE_INDEX;
        }
        if (mCurrentModuleIndex == moduleIndex) {
            if (mCurrentModuleIndex != ModuleSwitcher.CAPTURE_MODULE_INDEX) {
                return;
            }
        }
        CameraHolder.instance().keep();
        closeModule(mCurrentModule);
        setModuleFromIndex(moduleIndex);

        openModule(mCurrentModule);
        mForceReleaseCamera = false;
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        if (mMediaSaveService != null) {
            mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
        }

        // Store the module index so we can use it the next time the Camera
        // starts up.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putInt(CameraSettings.KEY_STARTUP_MODULE_INDEX, moduleIndex).apply();
    }

    /**
     * Sets the mCurrentModuleIndex, creates a new module instance for the given
     * index an sets it as mCurrentModule.
     */
    private void setModuleFromIndex(int moduleIndex) {
        mCameraPhotoModuleRootView.setVisibility(View.GONE);
        mCameraVideoModuleRootView.setVisibility(View.GONE);
        mCameraPanoModuleRootView.setVisibility(View.GONE);
        mCameraCaptureModuleRootView.setVisibility(View.GONE);
        mCurrentModuleIndex = moduleIndex;
        switch (moduleIndex) {
            case ModuleSwitcher.VIDEO_MODULE_INDEX:
                if(mVideoModule == null) {
                    mVideoModule = new VideoModule();
                    mVideoModule.init(this, mCameraVideoModuleRootView);
                } else {
                    mVideoModule.reinit();
                }
                mCurrentModule = mVideoModule;
                mCameraVideoModuleRootView.setVisibility(View.VISIBLE);
                break;

            case ModuleSwitcher.PHOTO_MODULE_INDEX:
                if(mPhotoModule == null) {
                    mPhotoModule = new PhotoModule();
                    mPhotoModule.init(this, mCameraPhotoModuleRootView);
                } else {
                    mPhotoModule.reinit();
                }
                mCurrentModule = mPhotoModule;
                mCameraPhotoModuleRootView.setVisibility(View.VISIBLE);
                break;

            case ModuleSwitcher.WIDE_ANGLE_PANO_MODULE_INDEX:
                if(mPanoModule == null) {
                    mPanoModule = new WideAnglePanoramaModule();
                    mPanoModule.init(this, mCameraPanoModuleRootView);
                }
                mCurrentModule = mPanoModule;
                mCameraPanoModuleRootView.setVisibility(View.VISIBLE);
                break;

            case ModuleSwitcher.CAPTURE_MODULE_INDEX:
                if(mCaptureModule == null) {
                    mCaptureModule = new CaptureModule();
                    mCaptureModule.init(this, mCameraCaptureModuleRootView);
                } else {
                    mCaptureModule.reinit();
                }
                mCurrentModule = mCaptureModule;
                mCameraCaptureModuleRootView.setVisibility(View.VISIBLE);
                break;

            case ModuleSwitcher.PANOCAPTURE_MODULE_INDEX:
                final Activity activity = this;
                if(!PanoCaptureProcessView.isSupportedStatic()) {
                    this.runOnUiThread(new Runnable() {
                        public void run() {
                            RotateTextToast.makeText(activity, "Panocapture library is missing", Toast.LENGTH_SHORT).show();
                        }
                    });
                    mCurrentModuleIndex = ModuleSwitcher.PHOTO_MODULE_INDEX;
                    //Let it fall through to photo module
                } else {
                    if (mPano2Module == null) {
                        mPano2Module = new PanoCaptureModule();
                        mPano2Module.init(this, mCameraPanoModuleRootView);
                    }
                    mCurrentModule = mPano2Module;
                    mCameraPanoModuleRootView.setVisibility(View.VISIBLE);
                    break;
                }
            case ModuleSwitcher.LIGHTCYCLE_MODULE_INDEX: //Unused module for now
            case ModuleSwitcher.GCAM_MODULE_INDEX:  //Unused module for now
            default:
                // Fall back to photo mode.
                if(mPhotoModule == null) {
                    mPhotoModule = new PhotoModule();
                    mPhotoModule.init(this, mCameraPhotoModuleRootView);
                } else {
                    mPhotoModule.reinit();
                }
                mCurrentModule = mPhotoModule;
                mCameraPhotoModuleRootView.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Launches an ACTION_EDIT intent for the given local data item.
     */
    public void launchEditor(LocalData data) {
        if (!mIsEditActivityInProgress) {
            Intent intent = new Intent(Intent.ACTION_EDIT)
                    .setDataAndType(data.getContentUri(), data.getMimeType())
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(intent, REQ_CODE_DONT_SWITCH_TO_PREVIEW);
            } catch (ActivityNotFoundException e) {
                startActivityForResult(Intent.createChooser(intent, null),
                        REQ_CODE_DONT_SWITCH_TO_PREVIEW);
            }
            mIsEditActivityInProgress = true;
        }
    }

    /**
     * Launch the tiny planet editor.
     *
     * @param data the data must be a 360 degree stereographically mapped
     *            panoramic image. It will not be modified, instead a new item
     *            with the result will be added to the filmstrip.
     */
    public void launchTinyPlanetEditor(LocalData data) {
        TinyPlanetFragment fragment = new TinyPlanetFragment();
        Bundle bundle = new Bundle();
        bundle.putString(TinyPlanetFragment.ARGUMENT_URI, data.getContentUri().toString());
        bundle.putString(TinyPlanetFragment.ARGUMENT_TITLE, data.getTitle());
        fragment.setArguments(bundle);
        fragment.show(getFragmentManager(), "tiny_planet");
    }

    private void openModule(CameraModule module) {
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(CameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
    }

    private void performDeletion() {
        if (!mPendingDeletion) {
            return;
        }
        hideUndoDeletionBar(false);
        mDataAdapter.executeDeletion(CameraActivity.this);

        int currentId = mFilmStripView.getCurrentId();
        updateActionBarMenu(currentId);
        mFilmStripListener.onCurrentDataCentered(currentId);
    }

    public void showUndoDeletionBar() {
        if (mPendingDeletion) {
            performDeletion();
        }
        Log.v(TAG, "showing undo bar");
        mPendingDeletion = true;
        if (mUndoDeletionBar == null) {
            ViewGroup v = (ViewGroup) getLayoutInflater().inflate(
                    R.layout.undo_bar, mAboveFilmstripControlLayout, true);
            mUndoDeletionBar = (ViewGroup) v.findViewById(R.id.camera_undo_deletion_bar);
            View button = mUndoDeletionBar.findViewById(R.id.camera_undo_deletion_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mDataAdapter.undoDataRemoval();
                    hideUndoDeletionBar(true);
                }
            });
            // Setting undo bar clickable to avoid touch events going through
            // the bar to the buttons (eg. edit button, etc) underneath the bar.
            mUndoDeletionBar.setClickable(true);
            // When there is user interaction going on with the undo button, we
            // do not want to hide the undo bar.
            button.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mIsUndoingDeletion = true;
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        mIsUndoingDeletion =false;
                    }
                    return false;
                }
            });
        }
        mUndoDeletionBar.setAlpha(0f);
        mUndoDeletionBar.setVisibility(View.VISIBLE);
        mUndoDeletionBar.animate().setDuration(200).alpha(1f).setListener(null).start();
    }

    private void hideUndoDeletionBar(boolean withAnimation) {
        Log.v(TAG, "Hiding undo deletion bar");
        mPendingDeletion = false;
        if (mUndoDeletionBar != null) {
            if (withAnimation) {
                mUndoDeletionBar.animate()
                        .setDuration(200)
                        .alpha(0f)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                // Do nothing.
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mUndoDeletionBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                // Do nothing.
                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {
                                // Do nothing.
                            }
                        })
                        .start();
            } else {
                mUndoDeletionBar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onShowSwitcherPopup() {
        mCurrentModule.onShowSwitcherPopup();
    }

    /**
     * Enable/disable swipe-to-filmstrip. Will always disable swipe if in
     * capture intent.
     *
     * @param enable {@code true} to enable swipe.
     */
    public void setSwipingEnabled(boolean enable) {
        if (isCaptureIntent()) {
            mCameraPreviewData.lockPreview(true);
        } else {
            mCameraPreviewData.lockPreview(!enable);
        }
    }


    /**
     * Check whether camera controls are visible.
     *
     * @return whether controls are visible.
     */
    private boolean arePreviewControlsVisible() {
        return mCurrentModule.arePreviewControlsVisible();
    }

    /**
     * Show or hide the {@link CameraControls} using the current module's
     * implementation of {@link #onPreviewFocusChanged}.
     *
     * @param showControls whether to show camera controls.
     */
    private void setPreviewControlsVisibility(boolean showControls) {
        mCurrentModule.onPreviewFocusChanged(showControls);
    }

    // Accessor methods for getting latency times used in performance testing
    public long getAutoFocusTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mAutoFocusTime : -1;
    }

    public long getShutterLag() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterLag : -1;
    }

    public long getShutterToPictureDisplayedTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterToPictureDisplayedTime : -1;
    }

    public long getPictureDisplayedToJpegCallbackTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mPictureDisplayedToJpegCallbackTime : -1;
    }

    public long getJpegCallbackFinishTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mJpegCallbackFinishTime : -1;
    }

    public long getCaptureStartTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mCaptureStartTime : -1;
    }

    public boolean isRecording() {
        return (mCurrentModule instanceof VideoModule) ?
                ((VideoModule) mCurrentModule).isRecording() : false;
    }

    public CameraOpenErrorCallback getCameraOpenErrorCallback() {
        return mCameraOpenErrorCallback;
    }

    // For debugging purposes only.
    public CameraModule getCurrentModule() {
        return mCurrentModule;
    }

    private void showOpenCameraErrorDialog() {
        if (!hasCriticalPermissions()) {
            CameraUtil.showErrorAndFinish(CameraActivity.this,
                    R.string.error_permissions);
        } else {
            CameraUtil.showErrorAndFinish(CameraActivity.this,
                    R.string.cannot_connect_camera);
        }
    }
}
