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

import java.util.List;

import org.codeaurora.snapcam.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.Toast;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.TsMakeupManager.MakeupLevelListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.MenuHelp;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.SelfieFlashView;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;

public class PhotoUI implements PieListener,
        PreviewGestures.SingleTapListener,
        FocusUI,
        SurfaceHolder.Callback,
        CameraRootView.MyDisplayListener,
        CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "CAM_UI";
    private int mDownSampleFactor = 4;
    private final AnimationManager mAnimationManager;
    private CameraActivity mActivity;
    private PhotoController mController;
    private PreviewGestures mGestures;

    private View mRootView;
    private SurfaceHolder mSurfaceHolder;

    private PopupWindow mPopup;
    private ShutterButton mShutterButton;
    private CountDownView mCountDownView;
    private SelfieFlashView mSelfieView;

    private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private ImageView mReviewImage;
    private DecodeImageForReview mDecodeTaskForReview = null;

    private View mMenuButton;
    private PhotoMenu mMenu;
    private ModuleSwitcher mSwitcher;
    private CameraControls mCameraControls;
    private MenuHelp mMenuHelp;
    private AlertDialog mLocationDialog;
    private SeekBar mBlurDegreeProgressBar;

    // Small indicators which show the camera settings in the viewfinder.
    private OnScreenIndicators mOnScreenIndicators;

    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private RotateTextToast mNotSelectableToast;

    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private int mMaxPreviewWidth = 0;
    private int mMaxPreviewHeight = 0;

    public boolean mMenuInitialized = false;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    private ImageView mThumbnail;
    private View mFlashOverlay;

    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private SurfaceView mSurfaceView = null;
    private float mAspectRatio = 4f / 3f;
    private boolean mAspectRatioResize;

    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private View mPreviewCover;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;
    private LinearLayout mMakeupMenuLayout;
    private boolean mUIhidden = false;
    private int mPreviewOrientation = -1;

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private boolean mIsLayoutInitializedAlready = false;

    private int mOrientation;
    private float mScreenBrightness = 0.0f;
    private boolean mIsBokehMode = false;

    public enum SURFACE_STATUS {
        HIDE,
        SURFACE_VIEW;
    }

    public interface SurfaceTextureSizeChangedListener {
        public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight);
    }

    public CameraControls getCameraControls() {
        return mCameraControls;
    }

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte [] mData;
        private int mOrientation;
        private boolean mMirror;

        public DecodeTask(byte[] data, int orientation, boolean mirror) {
            mData = data;
            mOrientation = orientation;
            mMirror = mirror;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            // Decode image in background.
            Bitmap bitmap = CameraUtil.downSample(mData, mDownSampleFactor);
            if ((mOrientation != 0 || mMirror) && (bitmap != null)) {
                Matrix m = new Matrix();
                if (mMirror) {
                    // Flip horizontally
                    m.setScale(-1f, 1f);
                }
                m.preRotate(mOrientation);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m,
                        false);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
        }
    }

    private class DecodeImageForReview extends DecodeTask {
        public DecodeImageForReview(byte[] data, int orientation, boolean mirror) {
            super(data, orientation, mirror);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                return;
            }
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
            mDecodeTaskForReview = null;
        }
    }

    public synchronized void applySurfaceChange(SURFACE_STATUS status) {
        if(status == SURFACE_STATUS.HIDE) {
            mSurfaceView.setVisibility(View.GONE);
            return;
        }
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.photo_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (SurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        Log.v(TAG, "Using mdp_preview_content (MDP path)");
        mSurfaceView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight,
                    int oldBottom) {
                int width = right - left;
                int height = bottom - top;

                tryToCloseSubList();

                if (mMaxPreviewWidth == 0 && mMaxPreviewHeight == 0) {
                    mMaxPreviewWidth = width;
                    mMaxPreviewHeight = height;
                }

                if (mOrientationResize != mPrevOrientationResize
                        || mAspectRatioResize || !mIsLayoutInitializedAlready) {
                    layoutPreview(mAspectRatio);
                    mAspectRatioResize = false;
                }
            }
        });

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mFlashOverlay = mRootView.findViewById(R.id.flash_overlay);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
        mSwitcher.setSwitchListener(mActivity);
        mSwitcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.getCameraState() == PhotoController.LONGSHOT) {
                       return;
                }
                mSwitcher.showPopup();
                mSwitcher.setOrientation(mOrientation, false);
            }
        });
        mMenuButton = mRootView.findViewById(R.id.menu);

        RotateImageView muteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mBlurDegreeProgressBar = (SeekBar)mRootView.findViewById(R.id.blur_degree_bar);
        mBlurDegreeProgressBar.setMax(100);

        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        ViewStub faceViewStub = (ViewStub) mRootView
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        initIndicators();
        mAnimationManager = new AnimationManager();
        mOrientationResize = false;
        mPrevOrientationResize = false;

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);
        mScreenRatio = CameraUtil.determineRatio(size.x, size.y);
        calculateMargins(size);
        mCameraControls.setMargins(mTopMargin, mBottomMargin);
        showFirstTimeHelp();
    }

    public SeekBar getBokehDegreeBar() {
        return mBlurDegreeProgressBar;
    }

    private void calculateMargins(Point size) {
        int l = size.x > size.y ? size.x : size.y;
        int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
        int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
        mTopMargin = l / 4 * tm / (tm + bm);
        mBottomMargin = l / 4 - mTopMargin;
    }

    public void setDownFactor(int factor) {
        mDownSampleFactor = factor;
    }

     public void cameraOrientationPreviewResize(boolean orientation){
        mPrevOrientationResize = mOrientationResize;
        mOrientationResize = orientation;
     }

    private void showFirstTimeHelp(int topMargin, int bottomMargin) {
        mMenuHelp = (MenuHelp) mRootView.findViewById(R.id.menu_help);
        mMenuHelp.setVisibility(View.VISIBLE);
        mMenuHelp.setMargins(topMargin, bottomMargin);
        mMenuHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMenuHelp != null) {
                    mMenuHelp.setVisibility(View.GONE);
                    mMenuHelp = null;
                }
            }
        });
    }

    public void setAspectRatio(float ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (mOrientationResize &&
                mActivity.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        Log.d(TAG, "setAspectRatio() ratio[" + ratio + "] mAspectRatio[" + mAspectRatio + "]");
        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = ratio;
        }
        mCameraControls.setPreviewRatio(mAspectRatio, false);
        layoutPreview(ratio);
    }

    public void layoutPreview(float ratio) {
        FrameLayout.LayoutParams lp;
        float scaledTextureWidth, scaledTextureHeight;
        int rotation = CameraUtil.getDisplayRotation(mActivity);
        mScreenRatio = CameraUtil.determineRatio(ratio);
        if (mScreenRatio == CameraUtil.RATIO_16_9
                && CameraUtil.determinCloseRatio(ratio) == CameraUtil.RATIO_4_3) {
            int l = (mTopMargin + mBottomMargin) * 4;
            int s = l * 9 / 16;
            switch (rotation) {
                case 90:
                    lp = new FrameLayout.LayoutParams(l * 3 / 4, s);
                    lp.setMargins(mTopMargin, 0, mBottomMargin, 0);
                    scaledTextureWidth = l * 3 / 4;
                    scaledTextureHeight = s;
                    break;
                case 180:
                    lp = new FrameLayout.LayoutParams(s, l * 3 / 4);
                    lp.setMargins(0, mBottomMargin, 0, mTopMargin);
                    scaledTextureWidth = s;
                    scaledTextureHeight = l * 3 / 4;
                    break;
                case 270:
                    lp = new FrameLayout.LayoutParams(l * 3 / 4, s);
                    lp.setMargins(mBottomMargin, 0, mTopMargin, 0);
                    scaledTextureWidth = l * 3 / 4;
                    scaledTextureHeight = s;
                    break;
                default:
                    lp = new FrameLayout.LayoutParams(s, l * 3 / 4);
                    lp.setMargins(0, mTopMargin, 0, mBottomMargin);
                    scaledTextureWidth = s;
                    scaledTextureHeight = l * 3 / 4;
                    break;
            }
        } else {
            float width = mMaxPreviewWidth, height = mMaxPreviewHeight;
            if (width == 0 || height == 0) return;
            if(mScreenRatio == CameraUtil.RATIO_4_3)
                height -=  (mTopMargin + mBottomMargin);
            if (mOrientationResize) {
                scaledTextureWidth = height * mAspectRatio;
                if (scaledTextureWidth > width) {
                    scaledTextureWidth = width;
                    scaledTextureHeight = scaledTextureWidth / mAspectRatio;
                } else {
                    scaledTextureHeight = height;
                }
            } else {
                if (width > height) {
                    if(Math.max(width, height * mAspectRatio) > width) {
                        scaledTextureWidth = width;
                        scaledTextureHeight = width / mAspectRatio;
                    } else {
                        scaledTextureWidth = height * mAspectRatio;
                        scaledTextureHeight = height;
                    }
                } else {
                    if(Math.max(height, width * mAspectRatio) > height) {
                        scaledTextureWidth = height / mAspectRatio;
                        scaledTextureHeight = height;
                    } else {
                        scaledTextureWidth = width;
                        scaledTextureHeight = width * mAspectRatio;
                    }
                }
            }

            Log.v(TAG, "setTransformMatrix: scaledTextureWidth = " + scaledTextureWidth
                    + ", scaledTextureHeight = " + scaledTextureHeight);
            if (((rotation == 0 || rotation == 180) && scaledTextureWidth > scaledTextureHeight)
                    || ((rotation == 90 || rotation == 270)
                        && scaledTextureWidth < scaledTextureHeight)) {
                lp = new FrameLayout.LayoutParams((int) scaledTextureHeight,
                        (int) scaledTextureWidth, Gravity.CENTER);
            } else {
                lp = new FrameLayout.LayoutParams((int) scaledTextureWidth,
                        (int) scaledTextureHeight, Gravity.CENTER);
            }
            if(mScreenRatio == CameraUtil.RATIO_4_3) {
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                lp.setMargins(0, mTopMargin, 0, mBottomMargin);
            }
        }

        if (mSurfaceTextureUncroppedWidth != scaledTextureWidth ||
                mSurfaceTextureUncroppedHeight != scaledTextureHeight) {
            mSurfaceTextureUncroppedWidth = scaledTextureWidth;
            mSurfaceTextureUncroppedHeight = scaledTextureHeight;
            if (mSurfaceTextureSizeListener != null) {
                mSurfaceTextureSizeListener.onSurfaceTextureSizeChanged(
                        (int) mSurfaceTextureUncroppedWidth,
                        (int) mSurfaceTextureUncroppedHeight);
                Log.d(TAG, "mSurfaceTextureUncroppedWidth=" + mSurfaceTextureUncroppedWidth
                        + "mSurfaceTextureUncroppedHeight=" + mSurfaceTextureUncroppedHeight);
            }
        }

        mSurfaceView.setLayoutParams(lp);
        mRootView.requestLayout();
        if (mFaceView != null) {
            mFaceView.setLayoutParams(lp);
        }
        mIsLayoutInitializedAlready = true;
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
        RectF r = new RectF(mSurfaceView.getLeft(), mSurfaceView.getTop(),
                mSurfaceView.getRight(), mSurfaceView.getBottom());
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(r));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
        mController.onPreviewUIReady();
        mActivity.updateThumbnail(mThumbnail);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mSurfaceHolder = null;
        mController.onPreviewUIDestroyed();
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mRootView.findViewById(R.id.on_screen_indicators));
    }

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener, MakeupLevelListener makeupListener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mMenu == null) {
            mMenu = new PhotoMenu(mActivity, this, makeupListener);
            mMenu.setListener(listener);
        }
        mMenu.initialize(prefGroup);
        mMenuInitialized = true;

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer, null);
            mRenderOverlay.setGestures(mGestures);
        }
        mGestures.setPhotoMenu(mMenu);

        mGestures.setZoomEnabled(params.isZoomSupported());
        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        initializeZoom(params);
        updateOnScreenIndicators(params, prefGroup, prefs);
        mActivity.setPreviewGestures(mGestures);
    }

    public void animateCapture(final byte[] jpegData) {
        // Decode jpeg byte array and then animate the jpeg
        mActivity.updateThumbnail(jpegData);
    }

    public void showRefocusToast(boolean show) {
        mCameraControls.showRefocusToast(show);
    }

    private void openMenu() {
        if (mPieRenderer != null) {
            // If autofocus is not finished, cancel autofocus so that the
            // subsequent touch can be handled by PreviewGestures
            if (mController.getCameraState() == PhotoController.FOCUSING) {
                    mController.cancelAutoFocus();
            }
            mPieRenderer.showInCenter();
        }
    }

    public void initializeControlByIntent() {
        if (!mActivity.isSecureCamera() && !mActivity.isCaptureIntent()) {
            mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
            mThumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!CameraControls.isAnimating()
                            && mController.getCameraState() != PhotoController.SNAPSHOT_IN_PROGRESS)
                        mActivity.gotoGallery();
                }
            });
        }
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMenu != null) {
                    mMenu.openFirstLevel();
                }
            }
        });
        if (mController.isImageCaptureIntent()) {
            hideSwitcher();
            mCameraControls.hideRemainingPhotoCnt();
            mSwitcher.setSwitcherVisibility(false);
            ViewGroup cameraControls = (ViewGroup) mRootView.findViewById(R.id.camera_controls);
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, cameraControls);

            mReviewDoneButton = mRootView.findViewById(R.id.done_button);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mRootView.findViewById(R.id.btn_retake);
            mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
            mReviewCancelButton.setVisibility(View.VISIBLE);

            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureDone();
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureCancelled();
                }
            });

            mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureRetake();
                    if (mController.isImageCaptureIntent()) {
                        mCameraControls.setTitleBarVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    public void hideRemainingPhotoCnt() {
        mCameraControls.hideRemainingPhotoCnt();
    }

    public void showRemainingPhotoCnt() {
        mCameraControls.showRemainingPhotoCnt();
    }

    public void hideUI() {
        mSwitcher.closePopup();
        if (mUIhidden)
            return;
        mUIhidden = true;
        mCameraControls.hideUI();
    }

    public void showUI() {
        if (!mUIhidden || (mMenu != null && mMenu.isMenuBeingShown()))
            return;
        mUIhidden = false;
        mCameraControls.showUI();
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showSwitcher() {
        mSwitcher.setVisibility(View.VISIBLE);
    }

    public void setSwitcherIndex() {
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.shutter_button_anim);
        mShutterButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating())
                    doShutterAnimation();
                    if (mController.isImageCaptureIntent()) {
                        mCameraControls.setTitleBarVisibility(View.VISIBLE);
                    }
            }
        });

        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    public void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    // called from onResume every other time
    public void initializeSecondTime(Camera.Parameters params) {
        initializeZoom(params);
        if (mController.isImageCaptureIntent()) {
            hidePostCaptureAlert();
        }
        if (mMenu != null) {
            mMenu.reloadPreferences();
        }
        RotateImageView muteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);
    }

    public void initializeZoom(Camera.Parameters params) {
        if ((params == null) || !params.isZoomSupported()
                || (mZoomRenderer == null)) return;
        mZoomMax = params.getMaxZoom();
        mZoomRatios = params.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer != null) {
            mZoomRenderer.setZoomMax(mZoomMax);
            mZoomRenderer.setZoom(params.getZoom());
            mZoomRenderer.setZoomValue(mZoomRatios.get(params.getZoom()));
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    public void overrideSettings(final String ... keyvalues) {
        if (mMenu == null)
            return;
        mMenu.overrideSettings(keyvalues);
    }

    public void updateOnScreenIndicators(Camera.Parameters params,
            PreferenceGroup group, ComboPreferences prefs) {
        if (params == null || group == null) return;
        mOnScreenIndicators.updateSceneOnScreenIndicator(params.getSceneMode());
        mOnScreenIndicators.updateExposureOnScreenIndicator(params,
                CameraSettings.readExposure(prefs));
        mOnScreenIndicators.updateFlashOnScreenIndicator(params.getFlashMode());
        int wbIndex = -1;
        String wb = Camera.Parameters.WHITE_BALANCE_AUTO;
        if (Camera.Parameters.SCENE_MODE_AUTO.equals(params.getSceneMode())) {
            wb = params.getWhiteBalance();
        }
        ListPreference pref = group.findPreference(CameraSettings.KEY_WHITE_BALANCE);
        if (pref != null) {
            wbIndex = pref.findIndexOfValue(wb);
        }
        // make sure the correct value was found
        // otherwise use auto index
        mOnScreenIndicators.updateWBIndicator(wbIndex < 0 ? 2 : wbIndex);
        boolean location = RecordLocationPreference.get(prefs, CameraSettings.KEY_RECORD_LOCATION);
        mOnScreenIndicators.updateLocationIndicator(location);
    }

    public void setCameraState(int state) {
    }

    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    // forward from preview gestures to controller
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public boolean onBackPressed() {
        if (mMenu != null && mMenu.handleBackKey()) {
            return true;
        }

        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        // In image capture mode, back button should:
        // 1) if there is any popup, dismiss them, 2) otherwise, get out of
        // image capture
        if (mController.isImageCaptureIntent()) {
            mController.onCaptureCancelled();
            return true;
        } else if (!mController.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        } if (mSwitcher != null && mSwitcher.showsPopup()) {
            mSwitcher.closePopup();
            return true;
        } else {
            return false;
        }
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!previewFocused);
        }
        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!previewFocused);
        }
        setShowMenu(previewFocused);
        if (!previewFocused && mCountDownView != null) mCountDownView.cancelCountDown();
    }

    public ViewGroup getMenuLayout() {
        return mMenuLayout;
    }

    public void setPreviewMenuLayout(LinearLayout layout) {
        mPreviewMenuLayout = layout;
    }

    public ViewGroup getPreviewMenuLayout() {
        return mPreviewMenuLayout;
    }

    public void setMakeupMenuLayout(LinearLayout layout) {
        mMakeupMenuLayout = layout;
    }

    public void showPopup(ListView popup, int level, boolean animate) {
        FrameLayout.LayoutParams params;
        hideUI();

        popup.setVisibility(View.VISIBLE);
        if (level == 1) {
            if (mMenuLayout == null) {
                mMenuLayout = new RotateLayout(mActivity, null);
                if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                    params = new FrameLayout.LayoutParams(
                            CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.WRAP_CONTENT,
                            Gravity.LEFT | Gravity.TOP);
                } else {
                    params = new FrameLayout.LayoutParams(
                            CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.WRAP_CONTENT,
                            Gravity.RIGHT | Gravity.TOP);
                }
                mMenuLayout.setLayoutParams(params);
                ((ViewGroup) mRootView).addView(mMenuLayout);
            }
            mMenuLayout.setOrientation(mOrientation, true);
            mMenuLayout.addView(popup);
        }
        if (level == 2) {
            if (mSubMenuLayout == null) {
                mSubMenuLayout = new RotateLayout(mActivity, null);
                ((ViewGroup) mRootView).addView(mSubMenuLayout);
            }
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                params = new FrameLayout.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.TOP);
            } else {
                params = new FrameLayout.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT,
                        Gravity.RIGHT | Gravity.TOP);
            }
            int screenHeight = (mOrientation == 0 || mOrientation == 180)
                ? mRootView.getHeight() : mRootView.getWidth();
            int height = ((ListSubMenu) popup).getPreCalculatedHeight();
            int yBase = ((ListSubMenu) popup).getYBase();
            int y = Math.max(0, yBase);
            if (yBase + height > screenHeight)
                y = Math.max(0, screenHeight - height);
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                params.setMargins(CameraActivity.SETTING_LIST_WIDTH_1, y, 0, 0);
            } else {
                params.setMargins(0, y, CameraActivity.SETTING_LIST_WIDTH_1, 0);
            }

            mSubMenuLayout.setLayoutParams(params);

            mSubMenuLayout.addView(popup);
            mSubMenuLayout.setOrientation(mOrientation, true);
        }
        if (animate) {
            if (level == 1)
                mMenu.animateSlideIn(mMenuLayout, CameraActivity.SETTING_LIST_WIDTH_1, true);
            if (level == 2)
                mMenu.animateFadeIn(popup);
        } else
            popup.setAlpha(0.85f);
    }

    public void removeLevel2() {
        if (mSubMenuLayout != null) {
            View v = mSubMenuLayout.getChildAt(0);
            mSubMenuLayout.removeView(v);
        }
    }

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup == null) {
            mPopup = new PopupWindow(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mPopup.setOutsideTouchable(true);
            mPopup.setFocusable(true);
            mPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mPopup = null;
                    // mMenu.popupDismissed(mDismissAll);
                    mDismissAll = false;
                    showUI();

                    // Switch back into fullscreen/lights-out mode after popup
                    // is dimissed.
                    mActivity.setSystemBarsVisibility(false);
                }
            });
        }
        popup.setVisibility(View.VISIBLE);
        mPopup.setContentView(popup);
        mPopup.showAtLocation(mRootView, Gravity.CENTER, 0, 0);
    }

    public void cleanupListview() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
    }

    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
    }

    private boolean mDismissAll = false;
    public void dismissAllPopup() {
        mDismissAll = true;
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
    }

    public void dismissLevel1() {
        if (mMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mMenuLayout);
            mMenuLayout = null;
        }
    }

    public void dismissLevel2() {
        if (mSubMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mSubMenuLayout);
            mSubMenuLayout = null;
        }
    }

    public boolean sendTouchToPreviewMenu(MotionEvent ev) {
        if (mPreviewMenuLayout != null) {
            return mPreviewMenuLayout.dispatchTouchEvent(ev);
        }
        return false;
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        if (mMenuLayout != null) {
            View v = mMenuLayout.getChildAt(0);
            return v.dispatchTouchEvent(ev);
        }
        return false;
    }

    public void dismissSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
    }

    public void removeSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
        cleanupListview();
    }

    public void onShowSwitcherPopup() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public boolean collapseCameraControls() {
        // TODO: Mode switcher should behave like a popup and should hide itself when there
        // is a touch outside of it.
        mSwitcher.closePopup();
        // Remove all the popups/dialog boxes
        boolean ret = false;
        if (mMenu != null) {
            mMenu.removeAllView();
        }
        if (mPopup != null) {
            dismissAllPopup();
            ret = true;
        }
        onShowSwitcherPopup();
        mCameraControls.showRefocusToast(false);
        return ret;
    }

    protected void showCapturedImageForReview(byte[] jpegData, int orientation, boolean mirror) {
        mCameraControls.hideCameraSettings();
        mDecodeTaskForReview = new DecodeImageForReview(jpegData, orientation, mirror);
        mDecodeTaskForReview.execute();
        mOnScreenIndicators.setVisibility(View.GONE);
        mMenuButton.setVisibility(View.GONE);
        CameraUtil.fadeIn(mReviewDoneButton);
        mShutterButton.setVisibility(View.INVISIBLE);
        CameraUtil.fadeIn(mReviewRetakeButton);
        setOrientation(mOrientation, true);
        mMenu.hideTopMenu(true);
        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        mCameraControls.showCameraSettings();
        if (mDecodeTaskForReview != null) {
            mDecodeTaskForReview.cancel(true);
        }
        mReviewImage.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.VISIBLE);
        if (mMenu != null) {
            mMenu.hideTopMenu(false);
        }
        CameraUtil.fadeOut(mReviewDoneButton);
        mShutterButton.setVisibility(View.VISIBLE);
        CameraUtil.fadeOut(mReviewRetakeButton);
        resumeFaceDetection();
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(orientation);
        }
        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mMenu != null && mMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
            mMenu.addModeBack();
        }
        mPreviewOrientation = orientation;
    }

    // shutter button handling

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            int newZoom = mController.onZoomChanged(index);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }

        @Override
        public void onZoomStart() {
            if (mPieRenderer != null) {
                mPieRenderer.hide();
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }

        @Override
        public void onZoomValueChanged(float value) {

        }
    }

    @Override
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
        // Close module selection menu when pie menu is opened.
        mSwitcher.closePopup();
        if (mIsBokehMode && mBlurDegreeProgressBar != null) {
            mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
        if (mBlurDegreeProgressBar != null) {
            mBlurDegreeProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPieMoved(int centerX, int centerY) {
        Size bokehCircle = mPieRenderer.getBokehFocusSize();
        int y;
        if (centerY > mPieRenderer.getHeight()/2) {
            y = centerY - bokehCircle.getHeight()/2 - mBlurDegreeProgressBar.getHeight();
        } else {
            y = centerY + bokehCircle.getHeight()/2;
        }
        mBlurDegreeProgressBar.setX(centerX - mBlurDegreeProgressBar.getWidth() /2);
        mBlurDegreeProgressBar.setY(y);
        if (mIsBokehMode && mBlurDegreeProgressBar.getVisibility() != View.VISIBLE
                && mPieRenderer.isVisible()) {
            mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public void enableBokehRender(boolean enable) {
        if (mPieRenderer != null) {
            mPieRenderer.setBokehMode(enable);
            mIsBokehMode = enable;
        }
    }

    public void enableBokehFocus(boolean enable) {
        if (mPieRenderer != null && mIsBokehMode) {
            mPieRenderer.setBokehMode(enable);
            if (mBlurDegreeProgressBar != null) {
                if (enable && mPieRenderer.isVisible()) {
                    mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
                } else {
                    mBlurDegreeProgressBar.setVisibility(View.GONE);
                }
            }
        }
    }

    public void setBokehRenderDegree(int degree) {
        if (mPieRenderer != null) {
            mPieRenderer.setBokehDegree(degree);
        }
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
    }
    // Countdown timer

    private void initializeCountDown() {
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((OnCountDownFinishedListener) mController);
        mCountDownView.bringToFront();
        mCountDownView.setOrientation(mOrientation);
    }

    public boolean isCountingDown() {
        return mCountDownView != null && mCountDownView.isCountingDown();
    }

    public void cancelCountDown() {
        if (mCountDownView == null) return;
        mCountDownView.cancelCountDown();
        showUIAfterCountDown();
    }

    public void startCountDown(int sec, boolean playSound) {
        if (mCountDownView == null) initializeCountDown();
        mCountDownView.startCountDown(sec, playSound);
        hideUIWhileCountDown();
    }

    public void startSelfieFlash() {
        if(mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.bringToFront();
        mSelfieView.open();
        mScreenBrightness = setScreenBrightness(1F);
    }

    public void stopSelfieFlash() {
        if(mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.close();
        if(mScreenBrightness != 0.0f)
            setScreenBrightness(mScreenBrightness);
    }

    private float setScreenBrightness(float brightness) {
        float originalBrightness;
        Window window = mActivity.getWindow();
        WindowManager.LayoutParams layout = window.getAttributes();
        originalBrightness = layout.screenBrightness;
        layout.screenBrightness = brightness;
        window.setAttributes(layout);
        return originalBrightness;
    }

    public void showPreferencesToast() {
        if (mNotSelectableToast == null) {
            String str = mActivity.getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = RotateTextToast.makeText(mActivity, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        // Hide the preview cover if need.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    public boolean isPreviewCoverVisible() {
        if ((mPreviewCover != null) &&
            (mPreviewCover.getVisibility() == View.VISIBLE)) {
            return true;
        } else {
            return false;
        }
    }

    public void onPause() {
        cancelCountDown();

        // Clear UI.
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();

        if (mLocationDialog != null && mLocationDialog.isShowing()) {
            mLocationDialog.dismiss();
        }
        mLocationDialog = null;
        if (mMenu != null) {
            mMenu.animateSlideOutPreviewMenu();
        }
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

    // focus UI implementation

    private FocusIndicator getFocusIndicator() {
        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : mPieRenderer;
    }

    @Override
    public boolean hasFaces() {
        return (mFaceView != null && mFaceView.faceExists());
    }

    public void clearFaces() {
        if (mFaceView != null) mFaceView.clear();
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = mPieRenderer;
        if (hasFaces()) {
            mFaceView.showStart();
        }
        if (indicator != null) indicator.clear();
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
    }

    @Override
    public void onFocusStarted() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showStart();
    }

    @Override
    public void onFocusSucceeded(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showSuccess(timeout);
    }

    @Override
    public void onFocusFailed(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showFail(timeout);
    }

    @Override
    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

    @Override
    public void resumeFaceDetection() {
        if (mFaceView != null) mFaceView.resume();
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.resume();
    }

    public void onStopFaceDetection() {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
            mFaceView.clear();
        }
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
        if (mIsBokehMode) {
            mFaceView.clear();
            return;
        }
        mFaceView.setFaces(faces);
    }

    @Override
    public void onDisplayChanged() {
        Log.d(TAG, "Device flip detected.");
        mCameraControls.checkLayoutFlip();
        mController.updateCameraOrientation();
    }

    public void setPreference(String key, String value) {
        mMenu.setPreference(key, value);
    }

    public void updateRemainingPhotos(int remaining) {
        mCameraControls.updateRemainingPhotos(remaining);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
        if (mMenuHelp != null)
            mMenuHelp.setOrientation(orientation, animation);
        if (mMenuLayout != null)
            mMenuLayout.setOrientation(orientation, animation);
        if (mSubMenuLayout != null)
            mSubMenuLayout.setOrientation(orientation, animation);
        if (mPreviewMenuLayout != null) {
            ViewGroup vg = (ViewGroup) mPreviewMenuLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if(mMakeupMenuLayout != null) {
            View view = mMakeupMenuLayout.getChildAt(0);
            if(view instanceof RotateLayout) {
                for(int i = mMakeupMenuLayout.getChildCount() -1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) mMakeupMenuLayout.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            } else {
                ViewGroup vg = (ViewGroup) mMakeupMenuLayout.getChildAt(1);
                if(vg != null) {
                    for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                        ViewGroup vewiGroup = (ViewGroup) vg.getChildAt(i);
                        if(vewiGroup instanceof RotateLayout) {
                            RotateLayout l = (RotateLayout) vewiGroup;
                            l.setOrientation(orientation, animation);
                        }
                    }
                }
            }

        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);
        RotateTextToast.setOrientation(orientation);
        if (mFaceView != null) {
            mFaceView.setDisplayRotation(orientation);
        }
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
        if (mReviewImage != null) {
            RotateImageView v = (RotateImageView) mReviewImage;
            v.setOrientation(orientation, animation);
        }
    }

    public void tryToCloseSubList() {
        if (mMenu != null)
            mMenu.tryToCloseSubList();
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, true);
    }

    public void showFirstTimeHelp() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean isMenuShown = prefs.getBoolean(CameraSettings.KEY_SHOW_MENU_HELP, false);
        if(!isMenuShown) {
            showFirstTimeHelp(mTopMargin, mBottomMargin);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(CameraSettings.KEY_SHOW_MENU_HELP, true);
            editor.apply();
        }
    }

    public void showRefocusDialog() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int prompt = prefs.getInt(CameraSettings.KEY_REFOCUS_PROMPT, 1);
        if (prompt == 1) {
            AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.refocus_prompt_title)
                .setMessage(R.string.refocus_prompt_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(CameraSettings.KEY_REFOCUS_PROMPT, 0);
                editor.apply();

        }
    }

    public void hideUIWhileCountDown() {
        mMenu.hideCameraControls(true);
        mGestures.setZoomOnly(true);
    }

    public void showUIAfterCountDown() {
        mMenu.hideCameraControls(false);
        mGestures.setZoomOnly(false);
    }
}
