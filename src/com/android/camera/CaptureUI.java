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

import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera.Face;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.List;

public class CaptureUI implements PieListener,
        PreviewGestures.SingleTapListener,
        FocusUI,
        SurfaceHolder.Callback,
        LocationManager.Listener,
        CameraRootView.MyDisplayListener,
        CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "SnapCam_CaptureUI";
    public boolean mMenuInitialized = false;
    private boolean surface1created = false;
    private boolean surface2created = false;
    private CameraActivity mActivity;
    private PhotoController mController;
    private PreviewGestures mGestures;

    private View mRootView;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSurfaceHolder2;
    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {

        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder2 = holder;
            if (surface1created) mController.onPreviewUIReady();
            surface2created = true;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mSurfaceHolder2 = null;
            surface2created = false;
        }
    };
    private PopupWindow mPopup;
    private ShutterButton mShutterButton;
    private RenderOverlay mRenderOverlay;
    private View mMenuButton;
    private CaptureMenu mMenu;
    private ModuleSwitcher mSwitcher;
    private CameraControls mCameraControls;
    // Small indicators which show the camera settings in the viewfinder.
    private OnScreenIndicators mOnScreenIndicators;
    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mOriginalPreviewWidth = 0;
    private int mOriginalPreviewHeight = 0;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    private SurfaceView mSurfaceView = null;
    private SurfaceView mSurfaceView2 = null;
    private Matrix mMatrix = null;
    private boolean mAspectRatioResize;

    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private View mPreviewCover;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;
    private boolean mUIhidden = false;
    private int mPreviewOrientation = -1;

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;

    private int mOrientation;
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
                if (mOriginalPreviewWidth != 0 && mOriginalPreviewHeight != 0) {
                    int temp = mOriginalPreviewWidth;
                    mOriginalPreviewWidth = mOriginalPreviewHeight;
                    mOriginalPreviewHeight = temp;
                }
            }

            if (mPreviewWidth != width || mPreviewHeight != height
                    || (mOrientationResize != mPrevOrientationResize)
                    || mAspectRatioResize) {
                if (mOriginalPreviewWidth == 0) mOriginalPreviewWidth = width;
                if (mOriginalPreviewHeight == 0) mOriginalPreviewHeight = height;
                mPreviewWidth = width;
                mPreviewHeight = height;
                setTransformMatrix(mPreviewWidth, mPreviewHeight);
                mController.onScreenSizeChanged((int) mSurfaceTextureUncroppedWidth,
                        (int) mSurfaceTextureUncroppedHeight);
                mAspectRatioResize = false;
            }

            if (mMenu != null)
                mMenu.tryToCloseSubList();
        }
    };

    public CaptureUI(CameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.capture_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (SurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceView2 = (SurfaceView) mRootView.findViewById(R.id.mdp_preview_content2);
        mSurfaceView2.setZOrderMediaOverlay(true);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder2 = mSurfaceView2.getHolder();
        mSurfaceHolder2.addCallback(callback);
        Log.v(TAG, "Using mdp_preview_content (MDP path)");

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
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

        RotateImageView muteButton = (RotateImageView) mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);

        initIndicators();
        mOrientationResize = false;
        mPrevOrientationResize = false;

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);
        mScreenRatio = CameraUtil.determineRatio(size.x, size.y);
        if (mScreenRatio == CameraUtil.RATIO_16_9) {
            int l = size.x > size.y ? size.x : size.y;
            int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
            int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
            mTopMargin = l / 4 * tm / (tm + bm);
            mBottomMargin = l / 4 - mTopMargin;
        }
        mCameraControls.setMargins(mTopMargin, mBottomMargin);
    }

    private void setTransformMatrix(int width, int height) {
        mMatrix = mSurfaceView.getMatrix();

        // Calculate the new preview rectangle.
        RectF previewRect = new RectF(0, 0, width, height);
        mMatrix.mapRect(previewRect);
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(previewRect));
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
        if (surface2created) mController.onPreviewUIReady();
        surface1created = true;
        if (mPreviewWidth != 0 && mPreviewHeight != 0) {
            // Re-apply transform matrix for new surface texture
            setTransformMatrix(mPreviewWidth, mPreviewHeight);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mSurfaceHolder = null;
        surface1created = false;
        mController.onPreviewUIDestroyed();
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mRootView.findViewById(R.id.on_screen_indicators));
    }

    public void onCameraOpened(CameraCharacteristics[] characteristics,
                               List<Integer> characteristicsIndex, PreferenceGroup prefGroup,
                               OnPreferenceChangedListener listener) {
        if (mMenu == null) {
            mMenu = new CaptureMenu(mActivity, this);
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
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
            mRenderOverlay.setGestures(mGestures);
        }
        mGestures.setCaptureMenu(mMenu);

        mGestures.setZoomEnabled(CameraUtil.isZoomSupported(characteristics, characteristicsIndex));
        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        initializeZoom(characteristics, characteristicsIndex);
        mActivity.setPreviewGestures(mGestures);
    }

    public void initializeControlByIntent() {
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMenu != null) {
                    mMenu.openFirstLevel();
                }
            }
        });
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

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.shutter_button_anim);
        mShutterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating())
                    doShutterAnimation();
            }
        });
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    // called from onResume every other time
    public void initializeSecondTime() {
        if (mMenu != null) {
            mMenu.reloadPreferences();
        }
    }

    public void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    public void initializeZoom(CameraCharacteristics[] characteristics,
                               List<Integer> characteristicsIndex) {
        if ((characteristics == null) || !CameraUtil.isZoomSupported(characteristics,
                characteristicsIndex) || (mZoomRenderer == null))
            return;
        if (mZoomRenderer != null) {
            float zoomMax = Float.MAX_VALUE;
            for (int i = 0; i < characteristicsIndex.size(); i++) {
                zoomMax = Math.min(characteristics[characteristicsIndex.get(i)].get
                        (CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM), zoomMax);
            }
            mZoomRenderer.setZoomMax(zoomMax);
            mZoomRenderer.setZoom(1f);
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {
    }

    @Override
    public void hideGpsOnScreenIndicator() {
    }

    public void overrideSettings(final String... keyvalues) {
        if (mMenu == null)
            return;
        mMenu.overrideSettings(keyvalues);
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
        }
        if (mSwitcher != null && mSwitcher.showsPopup()) {
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
    }

    public ViewGroup getMenuLayout() {
        return mMenuLayout;
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

    public void cleanupListview() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
    }

    public void dismissAllPopup() {
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
        return mPreviewMenuLayout.dispatchTouchEvent(ev);
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
        return ret;
    }

    public void setDisplayOrientation(int orientation) {
        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mMenu != null && mMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
        }
        mPreviewOrientation = orientation;
    }

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    // shutter button handling

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

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public SurfaceHolder getSurfaceHolder2() {
        return mSurfaceHolder2;
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

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public void onPause() {
        // Clear UI.
        collapseCameraControls();
    }

    // focus UI implementation
    private FocusIndicator getFocusIndicator() {
        return null;
    }

    @Override
    public boolean hasFaces() {
        return false;
    }

    public void clearFaces() {
    }

    public void setPreference(String key, String value) {
        mMenu.setPreference(key, value);
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
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
    }

    @Override
    public void resumeFaceDetection() {
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
    }

    public void onStopFaceDetection() {
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
    }

    @Override
    public void onDisplayChanged() {
        Log.d(TAG, "Device flip detected.");
        mCameraControls.checkLayoutFlip();
        mController.updateCameraOrientation();
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
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

        RotateTextToast.setOrientation(orientation);
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(float mZoomValue) {
            mController.onZoomChanged(mZoomValue);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoom(mZoomValue);
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
        public void onZoomValueChanged(int index) {

        }
    }

}
