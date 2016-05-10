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

import android.animation.Animator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera.Face;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.List;
import java.util.Locale;

public class CaptureUI implements FocusOverlayManager.FocusUI,
        PreviewGestures.SingleTapListener,
        LocationManager.Listener,
        CameraManager.CameraFaceDetectionCallback,
        SettingsManager.Listener,
        ListMenu.Listener,
        ListSubMenu.Listener {
    private static final int HIGHLIGHT_COLOR = 0xff33b5e5;
    private static final String TAG = "SnapCam_CaptureUI";
    private static final int SETTING_MENU_NONE = 0;
    private static final int SETTING_MENU_IN_ANIMATION = 1;
    private static final int SETTING_MENU_ON = 2;
    private static final int SETTING_MENU_LEVEL_ONE = 0;
    private static final int SETTING_MENU_LEVEL_TWO = 1;
    private static final int SCENE_AND_FILTER_MENU_NONE = 0;
    private static final int SCENE_AND_FILTER_MENU_IN_ANIMATION = 1;
    private static final int SCENE_AND_FILTER_MENU_ON = 2;
    private static final int MODE_FILTER = 0;
    private static final int MODE_SCENE = 1;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    String[] mSettingKeys = new String[]{
            SettingsManager.KEY_FLASH_MODE,
            SettingsManager.KEY_RECORD_LOCATION,
            SettingsManager.KEY_PICTURE_SIZE,
            SettingsManager.KEY_JPEG_QUALITY,
            SettingsManager.KEY_TIMER,
            SettingsManager.KEY_CAMERA_SAVEPATH,
            SettingsManager.KEY_LONGSHOT,
            SettingsManager.KEY_ISO,
            SettingsManager.KEY_EXPOSURE,
            SettingsManager.KEY_WHITE_BALANCE,
            SettingsManager.KEY_CAMERA2,
            SettingsManager.KEY_DUAL_CAMERA,
            SettingsManager.KEY_CLEARSIGHT
    };
    String[] mDeveloperKeys = new String[]{
            SettingsManager.KEY_FLASH_MODE,
            SettingsManager.KEY_RECORD_LOCATION,
            SettingsManager.KEY_PICTURE_SIZE,
            SettingsManager.KEY_JPEG_QUALITY,
            SettingsManager.KEY_TIMER,
            SettingsManager.KEY_CAMERA_SAVEPATH,
            SettingsManager.KEY_LONGSHOT,
            SettingsManager.KEY_ISO,
            SettingsManager.KEY_EXPOSURE,
            SettingsManager.KEY_WHITE_BALANCE,
            SettingsManager.KEY_CAMERA2,
            SettingsManager.KEY_DUAL_CAMERA,
            SettingsManager.KEY_CLEARSIGHT,
            SettingsManager.KEY_MONO_PREVIEW
    };
    private CameraActivity mActivity;
    private View mRootView;
    private View mPreviewCover;
    private CaptureModule mModule;
    private AutoFitSurfaceView mSurfaceView;
    private AutoFitSurfaceView mSurfaceView2;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSurfaceHolder2;
    private boolean surface1created = false;
    private boolean surface2created = false;
    private int mOrientation;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private int mSettingMenuState;
    private int mSettingMenuLevel;
    private int mSceneAndFilterMenuStatus;
    private int mSceneAndFilterMenuMode;
    private ListMenu mSettingMenu;
    private ListSubMenu mSettingSubMenu;
    private PreviewGestures mGestures;
    private boolean mUIhidden = false;
    private SettingsManager mSettingsManager;
    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {

        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "surfaceCreated");
            mSurfaceHolder = holder;
            if (surface2created) mModule.onPreviewUIReady();
            surface1created = true;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "surfaceDestroyed");
            mSurfaceHolder = null;
            surface1created = false;
            mModule.onPreviewUIDestroyed();
        }
    };

    private ShutterButton mShutterButton;
    private RenderOverlay mRenderOverlay;
    private View mMenuButton;
    private ModuleSwitcher mSwitcher;
    private CountDownView mCountDownView;
    private CameraControls mCameraControls;
    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private LinearLayout mSceneAndFilterLayout;
    private int mSceneAndFilterMenuSize;

    private View mFilterModeSwitcher;
    private View mSceneModeSwitcher;
    private View mFrontBackSwitcher;

    private SurfaceHolder.Callback callback2 = new SurfaceHolder.Callback() {

        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder2 = holder;
            if (surface1created) mModule.onPreviewUIReady();
            surface2created = true;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mSurfaceHolder2 = null;
            surface2created = false;
        }
    };

    public CaptureUI(CameraActivity activity, CaptureModule module, View parent) {
        mActivity = activity;
        mModule = module;
        mRootView = parent;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mActivity.getLayoutInflater().inflate(R.layout.capture_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceView2 = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content2);
        mSurfaceView2.setZOrderMediaOverlay(true);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(callback);
        mSurfaceHolder2 = mSurfaceView2.getHolder();
        mSurfaceHolder2.addCallback(callback2);

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mFilterModeSwitcher = mRootView.findViewById(R.id.filter_mode_switcher);
        mSceneModeSwitcher = mRootView.findViewById(R.id.scene_mode_switcher);
        mFrontBackSwitcher = mRootView.findViewById(R.id.front_back_switcher);
        initFilterModeButton();
        initSceneModeButton();
        initSwitchCamera();

        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
        mSwitcher.setSwitchListener(mActivity);
        mSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mModule.getCameraState() == PhotoController.LONGSHOT) {
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

        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
            mRenderOverlay.setGestures(mGestures);
        }

        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        mActivity.setPreviewGestures(mGestures);
    }

    public void onCameraOpened(List<Integer> cameraIds) {
        mGestures.setCaptureUI(this);
        mGestures.setZoomEnabled(mSettingsManager.isZoomSupported(cameraIds));
        initializeZoom(cameraIds);
    }

    public ViewGroup getSceneAndFilterLayout() {
        return mSceneAndFilterLayout;
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.shutter_button_anim);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating())
                    doShutterAnimation();
            }
        });
        mShutterButton.setOnShutterButtonListener(mModule);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    public void initializeZoom(List<Integer> ids) {
        if (!mSettingsManager.isZoomSupported(ids) || (mZoomRenderer == null))
            return;

        Float zoomMax = mSettingsManager.getMaxZoom(ids);
        mZoomRenderer.setZoomMax(zoomMax);
        mZoomRenderer.setZoom(1f);
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    public boolean isPreviewMenuBeingShown() {
        return mSceneAndFilterMenuStatus == SCENE_AND_FILTER_MENU_ON;
    }

    public void removeSceneAndFilterMenu(boolean animate) {
        if (animate) {
            animateSlideOut(mSceneAndFilterLayout);
        } else {
            mSceneAndFilterMenuStatus = SCENE_AND_FILTER_MENU_NONE;
            if (mSceneAndFilterLayout != null) {
                ((ViewGroup) mRootView).removeView(mSceneAndFilterLayout);
                mSceneAndFilterLayout = null;
            }
        }
    }

    public void initSwitchCamera() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
        if (value == null)
            return;

        int[] largeIcons = mSettingsManager.getResource(SettingsManager.KEY_CAMERA_ID,
                SettingsManager.RESOURCE_TYPE_LARGEICON);
        ((ImageView) mFrontBackSwitcher).setImageResource(largeIcons[mSettingsManager
                .getValueIndex(SettingsManager.KEY_CAMERA_ID)]);
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFrontBackSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = mSettingsManager.getValue(SettingsManager.KEY_CAMERA_ID);
                if (value == null)
                    return;

                int index = mSettingsManager.getValueIndex(SettingsManager.KEY_CAMERA_ID);
                CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_CAMERA_ID);
                index = (index + 1) % entries.length;
                mSettingsManager.setValueIndex(SettingsManager.KEY_CAMERA_ID, index);
                int[] largeIcons = mSettingsManager.getResource(SettingsManager.KEY_CAMERA_ID,
                        SettingsManager.RESOURCE_TYPE_LARGEICON);
                ((ImageView) v).setImageResource(largeIcons[index]);
            }
        });
    }

    public void initSceneModeButton() {
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return;
        updateSceneModeIcon();
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mSceneModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSceneMode();
                ViewGroup menuLayout = getSceneAndFilterLayout();
                if (menuLayout != null) {
                    View view = menuLayout.getChildAt(0);
                    adjustOrientation();
                    animateSlideIn(view, mSceneAndFilterMenuSize, false);
                }
            }
        });
    }

    public void initFilterModeButton() {
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;
        changeFilterModeControlIcon(value);

        updateFilterModeIcon(!mSettingsManager.isOverriden(SettingsManager.KEY_COLOR_EFFECT));
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
                ViewGroup menuLayout = getSceneAndFilterLayout();
                if (menuLayout != null) {
                    View view = getSceneAndFilterLayout().getChildAt(0);
                    adjustOrientation();
                    animateSlideIn(view, mSceneAndFilterMenuSize, false);
                }
            }
        });
    }

    public void setSwitcherIndex() {
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
    }

    public void addSceneMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);

        int[] thumbnails = mSettingsManager.getResource(SettingsManager.KEY_SCENE_MODE,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.scene_mode_height) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.scene_mode_width) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);

        int gridRes;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        mSceneAndFilterMenuSize = size;
        hideUI();

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout gridOuterLayout = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        removeSceneAndFilterMenu(false);
        mSceneAndFilterMenuStatus = SCENE_AND_FILTER_MENU_ON;
        mSceneAndFilterMenuMode = MODE_SCENE;
        mSceneAndFilterLayout = new LinearLayout(mActivity);
        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, FrameLayout.LayoutParams.MATCH_PARENT);
            mSceneAndFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mSceneAndFilterLayout);
        } else {
            params = new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size);
            mSceneAndFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mSceneAndFilterLayout);
            mSceneAndFilterLayout.setY(display.getHeight() - size);
        }
        gridOuterLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams
                .MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout gridLayout = (LinearLayout) gridOuterLayout.findViewById(R.id.layout);

        final View[] views = new View[entries.length];
        int init = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        for (int i = 0; i < entries.length; i++) {
            RotateLayout sceneBox = (RotateLayout) inflater.inflate(
                    R.layout.scene_mode_view, null, false);

            ImageView imageView = (ImageView) sceneBox.findViewById(R.id.image);
            TextView label = (TextView) sceneBox.findViewById(R.id.label);
            final int j = i;

            sceneBox.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            mSettingsManager.setValueIndex(SettingsManager.KEY_SCENE_MODE, j);
                            updateSceneModeIcon();
                            for (View v1 : views) {
                                v1.setBackgroundResource(R.drawable.scene_mode_view_border);
                            }
                            View border = v.findViewById(R.id.border);
                            border.setBackgroundResource(R.drawable
                                    .scene_mode_view_border_selected);
                            removeSceneAndFilterMenu(true);
                        }
                    }
                    return true;
                }
            });

            View border = sceneBox.findViewById(R.id.border);
            views[j] = border;
            if (i == init)
                border.setBackgroundResource(R.drawable.scene_mode_view_border_selected);

            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            gridLayout.addView(sceneBox);
        }
        mSceneAndFilterLayout.addView(gridOuterLayout);
    }

    public void updateSceneModeIcon() {
        int[] thumbnails = mSettingsManager.getResource(SettingsManager.KEY_SCENE_MODE,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        int thumbnail = thumbnails[mSettingsManager.getValueIndex(SettingsManager
                .KEY_SCENE_MODE)];
        if (thumbnail == -1)
            thumbnail = 0;
        ((ImageView) mSceneModeSwitcher).setImageResource(thumbnail);
    }

    public void addFilterMode() {
        if (mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT) == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_COLOR_EFFECT);

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.filter_mode_height) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.filter_mode_width) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);

        int gridRes;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        mSceneAndFilterMenuSize = size;
        hideUI();

        int[] thumbnails = mSettingsManager.getResource(SettingsManager.KEY_COLOR_EFFECT,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout gridOuterLayout = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        removeSceneAndFilterMenu(false);
        mSceneAndFilterMenuStatus = SCENE_AND_FILTER_MENU_ON;
        mSceneAndFilterMenuMode = MODE_FILTER;
        mSceneAndFilterLayout = new LinearLayout(mActivity);

        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, FrameLayout.LayoutParams.MATCH_PARENT);
            mSceneAndFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mSceneAndFilterLayout);
        } else {
            params = new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size);
            mSceneAndFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mSceneAndFilterLayout);
            mSceneAndFilterLayout.setY(display.getHeight() - size);
        }
        gridOuterLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams
                .MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout gridLayout = (LinearLayout) gridOuterLayout.findViewById(R.id.layout);
        final View[] views = new View[entries.length];

        int init = mSettingsManager.getValueIndex(SettingsManager.KEY_COLOR_EFFECT);
        for (int i = 0; i < entries.length; i++) {
            RotateLayout filterBox = (RotateLayout) inflater.inflate(
                    R.layout.filter_mode_view, null, false);
            ImageView imageView = (ImageView) filterBox.findViewById(R.id.image);
            final int j = i;

            filterBox.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            mSettingsManager.setValueIndex(SettingsManager
                                    .KEY_COLOR_EFFECT, j);
                            for (View v1 : views) {
                                v1.setBackground(null);
                            }
                            ImageView image = (ImageView) v.findViewById(R.id.image);
                            image.setBackgroundColor(HIGHLIGHT_COLOR);
                        }
                    }
                    return true;
                }
            });

            views[j] = imageView;
            if (i == init)
                imageView.setBackgroundColor(HIGHLIGHT_COLOR);
            TextView label = (TextView) filterBox.findViewById(R.id.label);

            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            gridLayout.addView(filterBox);
        }
        mSceneAndFilterLayout.addView(gridOuterLayout);
    }

    private void changeFilterModeControlIcon(String value) {
        int index;
        if (value.equals("0")) {
            index = 0;
        } else {
            index = 1;
        }
        ImageView iv = (ImageView) mFilterModeSwitcher;
        iv.setImageResource(mSettingsManager.getResource(SettingsManager
                .KEY_COLOR_EFFECT, SettingsManager.RESOURCE_TYPE_LARGEICON)[index]);
    }

    private void updateFilterModeIcon(boolean enable) {
        buttonSetEnabled(mFilterModeSwitcher, enable);
    }

    private void buttonSetEnabled(View v, boolean enable) {
        v.setEnabled(enable);
        if (v instanceof ViewGroup) {
            View v2 = ((ViewGroup) v).getChildAt(0);
            if (v2 != null)
                v2.setEnabled(enable);
        }
    }

    private void animateFadeOut(final View v, final int level) {
        if (v == null || mSettingMenuState == SETTING_MENU_IN_ANIMATION)
            return;
        mSettingMenuState = SETTING_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0f).setDuration(ANIMATION_DURATION);
        vp.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finishSettingMenuAnimateOut(level);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finishSettingMenuAnimateOut(level);
            }
        });
        vp.start();
    }

    private void animateSlideOut(final View v, final int level) {
        if (v == null || mSettingMenuState == SETTING_MENU_IN_ANIMATION)
            return;
        mSettingMenuState = SETTING_MENU_IN_ANIMATION;
        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (getOrientation()) {
                case 0:
                    vp.translationXBy(v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(-2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(-2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(v.getHeight());
                    break;
            }
        } else {
            switch (getOrientation()) {
                case 0:
                    vp.translationXBy(-v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(-v.getHeight());
                    break;
            }
        }
        vp.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finishSettingMenuAnimateOut(level);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finishSettingMenuAnimateOut(level);
            }
        });
        vp.setDuration(ANIMATION_DURATION).start();
    }

    private void finishSettingMenuAnimateOut(int level) {
        if (level == SETTING_MENU_LEVEL_ONE) {
            mSettingMenuState = SETTING_MENU_ON;
            removeSettingMenu(level, false);
            cleanUpMenus();
        } else if (level == SETTING_MENU_LEVEL_TWO) {
            mSettingMenuState = SETTING_MENU_ON;
            removeSettingMenu(level, false);
        }
    }

    private void finishScenceAndFilterMenuAnimateOut() {
        removeSceneAndFilterMenu(false);
        cleanUpMenus();
    }

    public void animateFadeIn(View v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    private void animateSlideOut(final View v) {
        if (v == null || mSceneAndFilterMenuStatus == SCENE_AND_FILTER_MENU_IN_ANIMATION)
            return;
        mSceneAndFilterMenuStatus = SCENE_AND_FILTER_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            vp.translationXBy(v.getWidth()).setDuration(ANIMATION_DURATION);
        } else {
            vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
        }
        vp.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finishScenceAndFilterMenuAnimateOut();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finishScenceAndFilterMenuAnimateOut();
            }
        });
        vp.start();
    }

    public void animateSlideIn(View v, int delta, boolean forcePortrait) {
        int orientation = getOrientation();
        if (!forcePortrait)
            orientation = 0;

        ViewPropertyAnimator vp = v.animate();
        float dest;
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(-(dest - delta));
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(-(dest + delta));
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(-(dest + delta));
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(-(dest - delta));
                    vp.translationY(dest);
                    break;
            }
        } else {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(dest - delta);
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(dest + delta);
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(dest + delta);
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(dest - delta);
                    vp.translationY(dest);
                    break;
            }
        }
        vp.setDuration(ANIMATION_DURATION).start();
    }

    private void initializeSettingMenu() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mSettingMenu = (ListMenu) inflater.inflate(
                R.layout.list_menu, null, false);

        mSettingMenu.setSettingChangedListener(this);
        mSettingMenu.setSettingsManager(mSettingsManager);

        String[] keys = mSettingKeys;
        if (mActivity.isDeveloperMenuEnabled())
            keys = mDeveloperKeys;
        mSettingMenu.initializeForCamera2(keys);
    }

    public boolean isMenuBeingShown() {
        return mSettingMenuState != SETTING_MENU_NONE;
    }

    public boolean isMenuBeingAnimated() {
        return mSettingMenuState == SETTING_MENU_IN_ANIMATION;
    }


    public void showSettingMenu() {
        if (isMenuBeingShown() || CameraControls.isAnimating()) {
            return;
        }
        if (mSettingMenu == null) {
            initializeSettingMenu();
        }
        showSettingMenu(SETTING_MENU_LEVEL_ONE, true);
    }

    private void showSettingMenu(int level, boolean animate) {
        FrameLayout.LayoutParams params;
        hideUI();

        mSettingMenu.setVisibility(View.VISIBLE);
        mSettingMenuState = SETTING_MENU_ON;
        if (level == SETTING_MENU_LEVEL_ONE) {
            mSettingMenuLevel = SETTING_MENU_LEVEL_ONE;
            if (mMenuLayout == null) {
                mMenuLayout = new RotateLayout(mActivity, null);
                if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                    params = new FrameLayout.LayoutParams(CameraActivity.SETTING_LIST_WIDTH_1,
                            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
                } else {
                    params = new FrameLayout.LayoutParams(CameraActivity.SETTING_LIST_WIDTH_1,
                            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
                }
                mMenuLayout.setLayoutParams(params);
                ((ViewGroup) mRootView).addView(mMenuLayout);
            }
            mMenuLayout.setOrientation(mOrientation, true);
            mMenuLayout.addView(mSettingMenu);
        } else if (level == SETTING_MENU_LEVEL_TWO) {
            mSettingMenuLevel = SETTING_MENU_LEVEL_TWO;
            if (mSubMenuLayout == null) {
                mSubMenuLayout = new RotateLayout(mActivity, null);
                ((ViewGroup) mRootView).addView(mSubMenuLayout);
            }
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                params = new FrameLayout.LayoutParams(CameraActivity.SETTING_LIST_WIDTH_2,
                        FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
            } else {
                params = new FrameLayout.LayoutParams(CameraActivity.SETTING_LIST_WIDTH_2,
                        FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
            }
            int screenHeight = (mOrientation == 0 || mOrientation == 180)
                    ? mRootView.getHeight() : mRootView.getWidth();
            int height = mSettingSubMenu.getPreCalculatedHeight();
            int yBase = mSettingSubMenu.getYBase();
            int y = Math.max(0, yBase);
            if (yBase + height > screenHeight)
                y = Math.max(0, screenHeight - height);
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                params.setMargins(CameraActivity.SETTING_LIST_WIDTH_1, y, 0, 0);
            } else {
                params.setMargins(0, y, CameraActivity.SETTING_LIST_WIDTH_1, 0);
            }

            mSubMenuLayout.setLayoutParams(params);

            mSubMenuLayout.addView(mSettingSubMenu);
            mSubMenuLayout.setOrientation(mOrientation, true);
        }
        if (animate) {
            if (level == SETTING_MENU_LEVEL_ONE) {
                animateSlideIn(mMenuLayout, CameraActivity.SETTING_LIST_WIDTH_1, true);
            }
            if (level == SETTING_MENU_LEVEL_TWO) {
                animateFadeIn(mSettingSubMenu);
            }
        } else {
            if (level == SETTING_MENU_LEVEL_ONE) {
                mMenuLayout.setAlpha(0.85f);
            }
            if (level == SETTING_MENU_LEVEL_TWO) {
                mSettingSubMenu.setAlpha(0.85f);
            }
        }
    }

    public void hideUIWhileCountDown() {
        hideCameraControls(true);
        mGestures.setZoomOnly(true);
    }

    public void showUIAfterCountDown() {
        hideCameraControls(false);
        mGestures.setZoomOnly(false);
    }

    public void hideCameraControls(boolean hide) {
        final int status = (hide) ? View.INVISIBLE : View.VISIBLE;
        if (mMenuButton != null) mMenuButton.setVisibility(status);
        if (mFrontBackSwitcher != null) mFrontBackSwitcher.setVisibility(status);
        if (mSceneModeSwitcher != null) mSceneModeSwitcher.setVisibility(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setVisibility(status);
        if (mSwitcher != null) mSwitcher.setVisibility(status);
    }

    public void initializeControlByIntent() {
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingMenu();
            }
        });
    }

    public void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    public void showUI() {
        if (!mUIhidden || isMenuBeingShown())
            return;
        mUIhidden = false;
        mCameraControls.showUI();
    }

    public void hideUI() {
        mSwitcher.closePopup();
        if (mUIhidden)
            return;
        mUIhidden = true;
        mCameraControls.hideUI();
    }

    public void cleanUpMenus() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
    }

    public void onOrientationChanged() {
        removeSettingMenu(SETTING_MENU_LEVEL_TWO, false);
        if (mSettingMenu != null)
            mSettingMenu.resetHighlight();
    }

    public void removeAllSettingMenu(boolean animate) {
        removeSettingMenu(SETTING_MENU_LEVEL_TWO, false);
        removeSettingMenu(SETTING_MENU_LEVEL_ONE, animate);
    }

    public void removeAllSettingMenu() {
        removeAllSettingMenu(false);
    }

    public void removeSettingMenu(int level, boolean animate) {
        if (mSettingMenuState == SETTING_MENU_NONE)
            return;
        if (!animate) {
            if (level == SETTING_MENU_LEVEL_TWO) {
                if (mSubMenuLayout != null) {
                    mSubMenuLayout.removeView(mSubMenuLayout.getChildAt(0));
                    mSubMenuLayout = null;
                }
                mSettingSubMenu = null;
                mSettingMenuState = SETTING_MENU_ON;
                mSettingMenuLevel = SETTING_MENU_LEVEL_ONE;
            } else if (level == SETTING_MENU_LEVEL_ONE) {
                mSettingMenu.resetHighlight();
                if (mMenuLayout != null) {
                    mMenuLayout.removeView(mMenuLayout.getChildAt(0));
                    mMenuLayout = null;
                }
                mSettingMenu = null;
                mSettingMenuState = SETTING_MENU_NONE;
                cleanUpMenus();
            }
        } else {
            if (level == SETTING_MENU_LEVEL_TWO) {
                mSettingMenu.resetHighlight();
                animateFadeOut(mSettingSubMenu, level);
            } else if (level == SETTING_MENU_LEVEL_ONE) {
                animateSlideOut(mSettingMenu, level);
            }
        }
    }

    public void removeAllMenu() {
        removeAllSettingMenu();
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    private boolean handleBackKeyOnMenu() {
        if (mSceneAndFilterMenuStatus == SCENE_AND_FILTER_MENU_ON) {
            removeSceneAndFilterMenu(true);
            return true;
        }
        if (mSettingMenuState == SETTING_MENU_NONE)
            return false;
        if (mSettingMenuState == SETTING_MENU_ON) {
            removeSettingMenu(mSettingMenuLevel, true);
        }
        return true;
    }

    public boolean onBackPressed() {
        if (handleBackKeyOnMenu()) return true;
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }

        if (!mModule.isCameraIdle()) {
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


    private void initializeCountDown() {
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((CountDownView.OnCountDownFinishedListener) mModule);
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

    public void onPause() {
        cancelCountDown();
        collapseCameraControls();
    }

    public boolean collapseCameraControls() {
        // TODO: Mode switcher should behave like a popup and should hide itself when there
        // is a touch outside of it.
        mSwitcher.closePopup();
        // Remove all the popups/dialog boxes
        boolean ret = false;
        removeAllMenu();
        mCameraControls.showRefocusToast(false);
        return ret;
    }

    private FocusIndicator getFocusIndicator() {
        return mPieRenderer;
    }

    @Override
    public boolean hasFaces() {
        return false;
    }

    public void clearFaces() {
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
    public void onFocusFailed(boolean timeOut) {

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

    public Point getSurfaceViewSize() {
        Point point = new Point();
        if (mSurfaceView != null) point.set(mSurfaceView.getWidth(), mSurfaceView.getHeight());
        return point;
    }

    public Point getSurfaceView2Size() {
        Point point = new Point();
        if (mSurfaceView2 != null) point.set(mSurfaceView2.getWidth(), mSurfaceView2.getHeight());
        return point;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, true);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
        if (mMenuLayout != null)
            mMenuLayout.setOrientation(orientation, animation);
        if (mSubMenuLayout != null)
            mSubMenuLayout.setOrientation(orientation, animation);
        if (mSceneAndFilterLayout != null) {
            ViewGroup vg = (ViewGroup) mSceneAndFilterLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);
        RotateTextToast.setOrientation(orientation);
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) {

    }

    @Override
    public void hideGpsOnScreenIndicator() {

    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mModule.onSingleTapUp(view, x, y);
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
        if (!previewFocused && mCountDownView != null) mCountDownView.cancelCountDown();
    }

    public ViewGroup getMenuLayout() {
        return mMenuLayout;
    }

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
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
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        for (SettingsManager.SettingState setting : settings) {
            String key = setting.key;
            SettingsManager.Values values = setting.values;
            String value = (values.overriddenValue == null) ? values.value : values.overriddenValue;
            switch (key) {
                case SettingsManager.KEY_CAMERA2:
                    switchCameraMode(value);
                    return;
                case SettingsManager.KEY_CAMERA_ID:
                case SettingsManager.KEY_DUAL_CAMERA:
                case SettingsManager.KEY_CLEARSIGHT:
                case SettingsManager.KEY_PICTURE_SIZE:
                    mActivity.onModuleSelected(ModuleSwitcher.CAPTURE_MODULE_INDEX);
                    return;
                case SettingsManager.KEY_COLOR_EFFECT:
                    changeFilterModeControlIcon(value);
                    updateFilterModeIcon(values.overriddenValue == null);
                    break;
            }
        }
    }

    public void setPreviewSize(int width, int height) {
        mSurfaceView.setAspectRatio(width, height);
    }

    private void switchCameraMode(String value) {
        if (value.equals("enable")) {
            mActivity.onModuleSelected(ModuleSwitcher.CAPTURE_MODULE_INDEX);
        } else {
            mActivity.onModuleSelected(ModuleSwitcher.PHOTO_MODULE_INDEX);
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        removeAllSettingMenu();
    }

    @Override
    public void onPreferenceClicked(ListPreference pref) {
        onPreferenceClicked(pref, 0);
    }

    @Override
    public void onPreferenceClicked(ListPreference pref, int y) {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        removeSettingMenu(SETTING_MENU_LEVEL_TWO, false);
        mSettingSubMenu = (ListSubMenu) inflater.inflate(R.layout.list_sub_menu, null, false);
        mSettingSubMenu.initialize(pref, y);
        mSettingSubMenu.setSettingChangedListener(mSettingMenu);
        mSettingSubMenu.setAlpha(0f);

        if (mSettingMenuState == SETTING_MENU_ON) {
            if (mSettingMenuLevel == SETTING_MENU_LEVEL_TWO) {
                showSettingMenu(SETTING_MENU_LEVEL_TWO, false);
            } else if (mSettingMenuLevel == SETTING_MENU_LEVEL_ONE) {
                showSettingMenu(SETTING_MENU_LEVEL_TWO, true);
            }
        }
    }

    @Override
    public void onListMenuTouched() {
        removeSettingMenu(SETTING_MENU_LEVEL_TWO, false);
    }

    @Override
    public void onListPrefChanged(ListPreference pref) {
        removeAllSettingMenu();
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(float mZoomValue) {
            mModule.onZoomChanged(mZoomValue);
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
