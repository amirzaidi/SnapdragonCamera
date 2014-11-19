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

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

public class VideoUI implements PieRenderer.PieListener,
        PreviewGestures.SingleTapListener,
        CameraRootView.MyDisplayListener,
        SurfaceHolder.Callback,
        PauseButton.OnPauseButtonListener {
    private static final String TAG = "CAM_VideoUI";
    private static final int UPDATE_TRANSFORM_MATRIX = 1;
    // module fields
    private CameraActivity mActivity;
    private View mRootView;
    private SurfaceHolder mSurfaceHolder;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private ShutterButton mShutterButton;
    private PauseButton mPauseButton;
    private ModuleSwitcher mSwitcher;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private VideoMenu mVideoMenu;
    private CameraControls mCameraControls;
    private SettingsPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    private View mMenuButton;
    private OnScreenIndicators mOnScreenIndicators;
    private RotateLayout mRecordingTimeRect;
    private boolean mRecordingStarted = false;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private View mPreviewThumb;
    private View mFlashOverlay;
    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private boolean mIsTimeLapse = false;
    private LinearLayout mMenuLayout;
    private LinearLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;
    private CustomVideoMenu mCustomVideoMenu;

    private View mPreviewCover;
    private SurfaceView mSurfaceView = null;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mAspectRatio = 4f / 3f;
    private boolean mAspectRatioResize;
    private Matrix mMatrix = null;
    private final AnimationManager mAnimationManager;
    private boolean mUIhidden = false;
    private int mPreviewOrientation = -1;

    // temporary variables for updating SurfaceView
    private int mTempWidth;
    private int mTempHeight;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TRANSFORM_MATRIX:
                    setTransformMatrix(mPreviewWidth, mPreviewHeight);
                    break;
                default:
                    break;
            }
        }
    };

    private final Handler mSurfaceViewUpdateHandler = new Handler();

    private Runnable updateSurfaceView = new Runnable() {

        @Override
        public void run() {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSurfaceView
                    .getLayoutParams();
            params.width = mTempWidth;
            params.height = mTempHeight;
            mSurfaceView.setLayoutParams(params);
        }
    };

    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;

            int orientation = mActivity.getResources().getConfiguration().orientation;
            if ((orientation == Configuration.ORIENTATION_PORTRAIT && width > height)
                    || (orientation == Configuration.ORIENTATION_LANDSCAPE && width < height)) {
                // The screen has rotated; swap SurfaceView width & height
                // to ensure correct preview
                int oldWidth = width;
                width = height;
                height = oldWidth;
                Log.d(TAG, "Swapping SurfaceView width & height dimensions");
            }

            if (mPreviewWidth != width || mPreviewHeight != height
                    || (mOrientationResize != mPrevOrientationResize)
                    || mAspectRatioResize) {
                mPreviewWidth = width;
                mPreviewHeight = height;
                setTransformMatrix(width, height);
                mAspectRatioResize = false;
            }
            mCustomVideoMenu.tryToCloseSubList();
        }
    };

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    private class SettingsPopup extends PopupWindow {
        public SettingsPopup(View popup) {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            setOutsideTouchable(true);
            setFocusable(true);
            popup.setVisibility(View.VISIBLE);
            setContentView(popup);
            showAtLocation(mRootView, Gravity.CENTER, 0, 0);
        }

        public void dismiss(boolean topLevelOnly) {
            super.dismiss();
            popupDismissed();
            showUI();
            mVideoMenu.popupDismissed(topLevelOnly);

            // Switch back into fullscreen/lights-out mode after popup
            // is dimissed.
            mActivity.setSystemBarsVisibility(false);
        }

        @Override
        public void dismiss() {
            // Called by Framework when touch outside the popup or hit back key
            dismiss(true);
        }
    }

    public VideoUI(CameraActivity activity, VideoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.video_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (SurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceHolder.addCallback(this);
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mSurfaceView.addOnLayoutChangeListener(mLayoutListener);
            Log.v(TAG, "Using mdp_preview_content (MDP path)");

        mFlashOverlay = mRootView.findViewById(R.id.flash_overlay);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.VIDEO_MODULE_INDEX);
        mSwitcher.setSwitchListener(mActivity);

        initializeMiscControls();
        initializeControlByIntent();
        initializeOverlay();
        initializePauseButton();
        mAnimationManager = new AnimationManager();
        mOrientationResize = false;
        mPrevOrientationResize = false;
    }

    public void cameraOrientationPreviewResize(boolean orientation){
       mPrevOrientationResize = mOrientationResize;
       mOrientationResize = orientation;
    }

    public void initializeSurfaceView() {
        if (mSurfaceView == null) {
            mSurfaceView = new SurfaceView(mActivity);
            ((ViewGroup) mRootView).addView(mSurfaceView, 0);
            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceHolder.addCallback(this);
        }
    }

    private void initializeControlByIntent() {
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCustomVideoMenu.openFirstLevel();
            }
        });

        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mRootView.findViewById(R.id.on_screen_indicators));
        mOnScreenIndicators.resetToDefault();
        if (mController.isVideoCaptureIntent()) {
            hideSwitcher();
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control,
                    (ViewGroup) mCameraControls);
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewPlayButton = mRootView.findViewById(R.id.btn_play);
            mReviewCancelButton.setVisibility(View.VISIBLE);
            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewDoneClicked(v);
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewCancelClicked(v);
                }
            });
            mReviewPlayButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewPlayClicked(v);
                }
            });
        }
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        float ratio;
        if (width > height) {
            ratio = (float) width / height;
        } else {
            ratio = (float) height / width;
        }
        if (mOrientationResize &&
                mActivity.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio){
            mAspectRatioResize = true;
            mAspectRatio = ratio;
        }

        if (mPreviewWidth > 0 && mPreviewHeight > 0) {
            mHandler.sendEmptyMessage(UPDATE_TRANSFORM_MATRIX);
        }
    }

    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    private void setTransformMatrix(int width, int height) {
            mMatrix = mSurfaceView.getMatrix();

        float scaledTextureWidth, scaledTextureHeight;
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
                scaledTextureWidth = Math.max(width, height * mAspectRatio);
                scaledTextureHeight = Math.max(height, width / mAspectRatio);
            } else {
                scaledTextureWidth = Math.max(width, height / mAspectRatio);
                scaledTextureHeight = Math.max(height, width * mAspectRatio);
            }
        }

        Log.v(TAG, "setTransformMatrix: scaledTextureWidth = " + scaledTextureWidth
                + ", scaledTextureHeight = " + scaledTextureHeight);
        mTempWidth = (int) scaledTextureWidth;
        mTempHeight = (int) scaledTextureHeight;
        mSurfaceView.requestLayout();
        mHandler.post(updateSurfaceView);
    }

    /**
     * Starts a flash animation
     */
    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
    }

    /**
     * Starts a capture animation
     */
    public void animateCapture() {
        Bitmap bitmap = null;
        animateCapture(bitmap);
    }

    /**
     * Starts a capture animation
     * @param bitmap the captured image that we shrink and slide in the animation
     */
    public void animateCapture(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "No valid bitmap for capture animation.");
            return;
        }
        ((ImageView) mPreviewThumb).setImageBitmap(bitmap);
        mAnimationManager.startCaptureAnimation(mPreviewThumb);
    }

    /**
     * Cancels on-going animations
     */
    public void cancelAnimations() {
        mAnimationManager.cancelAnimations();
    }

    public void hideUI() {
        mSwitcher.closePopup();
        if (mUIhidden)
            return;
        mUIhidden = true;
        mCameraControls.hideUI();
    }

    public void showUI() {
        if (!mUIhidden || (mCustomVideoMenu != null && mCustomVideoMenu.isMenuBeingShown()))
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

    public boolean collapseCameraControls() {
        boolean ret = false;
        mSwitcher.closePopup();
        if (mCustomVideoMenu != null) {
            mCustomVideoMenu.closeAllView();
        }
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    public void enableCameraControls(boolean enable) {
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void setDisplayOrientation(int orientation) {
        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mCustomVideoMenu != null && mCustomVideoMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
            mCustomVideoMenu.addModeBack();
        }
        mPreviewOrientation = orientation;
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

    public void overrideSettings(final String... keyvalues) {
        if (mVideoMenu != null) {
            mVideoMenu.overrideSettings(keyvalues);
        }
    }

    public void setOrientationIndicator(int orientation, boolean animation) {
        // We change the orientation of the linearlayout only for phone UI
        // because when in portrait the width is not enough.
        if (mLabelsLinearLayout != null) {
            if (((orientation / 90) & 1) == 0) {
                mLabelsLinearLayout.setOrientation(LinearLayout.VERTICAL);
            } else {
                mLabelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            }
        }
        mRecordingTimeRect.setOrientation(0, animation);
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.GONE);

        setTransformMatrix(mPreviewWidth, mPreviewHeight);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);

        setTransformMatrix(mPreviewWidth, mPreviewHeight);
    }

    private void initializeOverlay() {
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mVideoMenu = new VideoMenu(mActivity, this, mPieRenderer);
            mPieRenderer.setPieListener(this);
        }
        if (mCustomVideoMenu == null) {
            mCustomVideoMenu = new CustomVideoMenu(mActivity, this);
        }
        mRenderOverlay.addRenderer(mPieRenderer);
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mZoomRenderer);
        if (mGestures == null) {
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
            mRenderOverlay.setGestures(mGestures);
        }
        mGestures.setCustomVideoMenu(mCustomVideoMenu);

        mGestures.setRenderOverlay(mRenderOverlay);

        mPreviewThumb = mRootView.findViewById(R.id.preview_thumb);
        mPreviewThumb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Do not allow navigation to filmstrip during video recording
                if (!mRecordingStarted && !CameraControls.isAnimating()) {
                    mActivity.gotoGallery();
                }
            }
        });

        mActivity.setPreviewGestures(mGestures);
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
        mCustomVideoMenu.setListener(listener);
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
        mShutterButton.requestFocus();
        mShutterButton.enableTouch(true);
        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) mRootView.findViewById(R.id.labels);
    }

    private void initializePauseButton() {
        mPauseButton = (PauseButton) mRootView.findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);
    }

    public void updateOnScreenIndicators(Parameters param, ComboPreferences prefs) {
      mOnScreenIndicators.updateFlashOnScreenIndicator(param.getFlashMode());
      boolean location = RecordLocationPreference.get(
              prefs, mActivity.getContentResolver());
      mOnScreenIndicators.updateLocationIndicator(location);

    }

    public void setAspectRatio(double ratio) {
        if (mOrientationResize &&
                mActivity.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = (float)ratio;
        }

            mSurfaceView.requestLayout();

        if (mPreviewWidth > 0 && mPreviewHeight > 0) {
            mHandler.sendEmptyMessage(UPDATE_TRANSFORM_MATRIX);
        }
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
        mIsTimeLapse = enable;
    }

    private void openMenu() {
        if (mPieRenderer != null) {
            mPieRenderer.showInCenter();
        }
    }

    public void dismissPopup(boolean topLevelOnly) {
        // In review mode, we do not want to bring up the camera UI
        if (mController.isInReviewMode()) return;
        if (mPopup != null) {
            mPopup.dismiss(topLevelOnly);
        }
    }

    private void popupDismissed() {
        mPopup = null;
    }

    public boolean onBackPressed() {
        if (mCustomVideoMenu != null && mCustomVideoMenu.handleBackKey()) {
            return true;
        }
        if (hidePieRenderer()) {
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    public void cleanupListview() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
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
        return mPreviewMenuLayout.dispatchTouchEvent(ev);
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        View v = mMenuLayout.getChildAt(0);
        return v.dispatchTouchEvent(ev);
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

    public void removeLevel2() {
        if (mSubMenuLayout != null) {
            View v = mSubMenuLayout.getChildAt(0);
            mSubMenuLayout.removeView(v);
        }
    }

    public void showPopup(ListView popup, int level, boolean animate) {
        hideUI();

        popup.setVisibility(View.VISIBLE);
        if (level == 1) {
            if (mMenuLayout == null) {
                mMenuLayout = new LinearLayout(mActivity);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.WRAP_CONTENT);
                mMenuLayout.setLayoutParams(params);
                ((ViewGroup) mRootView).addView(mMenuLayout);
            }
            mMenuLayout.addView(popup);
        }
        if (level == 2) {
            if (mSubMenuLayout == null) {
                mSubMenuLayout = new LinearLayout(mActivity);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT);
                mSubMenuLayout.setLayoutParams(params);

                ((ViewGroup) mRootView).addView(mSubMenuLayout);
            }
            mSubMenuLayout.addView(popup);
            mSubMenuLayout.setX(CameraActivity.SETTING_LIST_WIDTH_1);
        }
        if (animate) {
            if (level == 1)
                mCustomVideoMenu.animateSlideIn(popup, CameraActivity.SETTING_LIST_WIDTH_1, true);
            if (level == 2)
                mCustomVideoMenu.animateFadeIn(popup);
        }
        else
            popup.setAlpha(0.85f);
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

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup != null) {
            mPopup.dismiss(false);
        }
        mPopup = new SettingsPopup(popup);
    }

    public void onShowSwitcherPopup() {
        hidePieRenderer();
    }

    public boolean hidePieRenderer() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        return false;
    }

    // disable preview gestures after shutter is pressed
    public void setShutterPressed(boolean pressed) {
        if (mGestures == null) return;
        mGestures.setEnabled(!pressed);
    }

    public void enableShutter(boolean enable) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enable);
        }
    }

    // PieListener
    @Override
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
        // Close module selection menu when pie menu is opened.
        mSwitcher.closePopup();
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public void showPreviewBorder(boolean enable) {
       // TODO: mPreviewFrameLayout.showBorder(enable);
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public void showRecordingUI(boolean recording) {
        mRecordingStarted = recording;
        mMenuButton.setVisibility(recording ? View.GONE : View.VISIBLE);
        mOnScreenIndicators.setVisibility(recording ? View.GONE : View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.shutter_button_video_stop);
            hideSwitcher();
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            mPauseButton.setVisibility(mIsTimeLapse ? View.GONE : View.VISIBLE);
        } else {
            mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
            if (!mController.isVideoCaptureIntent()) {
                showSwitcher();
            }
            mRecordingTimeView.setVisibility(View.GONE);
            mPauseButton.setVisibility(View.GONE);
        }
    }

    public void hideUIwhileRecording() {
        mCustomVideoMenu.hideUI();
    }

    public void showUIafterRecording() {
        mCustomVideoMenu.showUI();
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        CameraUtil.fadeOut(mShutterButton);
        CameraUtil.fadeIn(mReviewDoneButton);
        CameraUtil.fadeIn(mReviewPlayButton);
        mReviewImage.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.GONE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mMenuButton.setVisibility(View.VISIBLE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewPlayButton);
        CameraUtil.fadeIn(mShutterButton);
    }

    private void setShowMenu(boolean show) {
        if (mController.isVideoCaptureIntent())
            return;
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        setShowMenu(previewFocused);
    }

    public void initializePopup(PreferenceGroup pref) {
        mVideoMenu.initialize(pref);
        mCustomVideoMenu.initialize(pref);
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported()) {
            mGestures.setZoomEnabled(false);
            return;
        }
        mGestures.setZoomEnabled(true);
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom());
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void clickShutter() {
        mShutterButton.performClick();
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public View getShutterButton() {
        return mShutterButton;
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public boolean isVisible() {
        return mCameraControls.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onDisplayChanged() {
        mCameraControls.checkLayoutFlip();
        mController.updateCameraOrientation();
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
                if (!mRecordingStarted) mPieRenderer.hide();
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged: width = " + width + ", height = " + height);
        // Make sure preview cover is hidden if preview data is available.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
        mController.onPreviewUIReady();
        if (mPreviewWidth != 0 && mPreviewHeight != 0) {
            // Re-apply transform matrix for new surface texture
            setTransformMatrix(mPreviewWidth, mPreviewHeight);
        }
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

     @Override
    public void onButtonPause() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        mController.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mController.onButtonContinue();
    }

    public void resetPauseButton() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_recording_indicator, 0, 0, 0);
        mPauseButton.setPaused(false);
    }

    public void setPreference(String key, String value) {
        mVideoMenu.setPreference(key, value);
    }

    public boolean hideSwitcherPopup() {
        if (mSwitcher != null && mSwitcher.showsPopup()) {
            mSwitcher.closePopup();
            return true;
        }
        return false;
    }
}
