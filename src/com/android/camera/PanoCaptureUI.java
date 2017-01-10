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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;

import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.PanoCaptureProcessView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

public class PanoCaptureUI implements
        SurfaceHolder.Callback,
        LocationManager.Listener,
        CameraRootView.MyDisplayListener,
        CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "SnapCam_PanoCaptureUI";
    private CameraActivity mActivity;
    private PanoCaptureModule mController;

    private View mRootView;
    private SurfaceHolder mSurfaceHolder;
    private ShutterButton mShutterButton;
    private ModuleSwitcher mSwitcher;
    private CameraControls mCameraControls;
    private RotateLayout mSceneModeLabelRect;
    private LinearLayout mSceneModeLabelView;
    private TextView mSceneModeName;
    private ImageView mSceneModeLabelCloseIcon;
    private AlertDialog  mSceneModeInstructionalDialog = null;

    // Small indicators which show the camera settings in the viewfinder.
    private OnScreenIndicators mOnScreenIndicators;

    private AutoFitSurfaceView mSurfaceView = null;
    private Matrix mMatrix = null;
    private boolean mUIhidden = false;

    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private int mSurfaceMode = 0; //0: INIT 1: TextureView 2: SurfaceView
    private PanoCaptureProcessView mPreviewProcessView;
    private ImageView mThumbnail;

    private int mOrientation;
    private boolean mIsSceneModeLabelClose = false;

    public void clearSurfaces() {
        mSurfaceHolder = null;
    }

    public boolean isPanoCompleting() {
        return mPreviewProcessView.isPanoCompleting();
    }

    public boolean isFrameProcessing() {
        return mPreviewProcessView.isFrameProcessing();
    }

    public void onFrameAvailable(Bitmap bitmap, boolean isCancelling) {
        mPreviewProcessView.onFrameAvailable(bitmap, isCancelling);
    }

    public void onPanoStatusChange(final boolean isStarting) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(isStarting) {
                    if (mThumbnail != null) {
                        mThumbnail.setVisibility(View.GONE);
                    }
                    if (mShutterButton != null) {
                        mShutterButton.setImageResource(R.drawable.shutter_button_video_stop);
                    }
                } else {
                    if (mThumbnail != null) {
                        mThumbnail.setVisibility(View.VISIBLE);
                    }
                    if (mShutterButton != null) {
                        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
                    }
                }
            }
        });
    }

    /*
    * mode
    * 0: Hiding and closing
    * 1: TextureView
    * 2: SurfaceView
    */
    public synchronized void applySurfaceChange(int mode, boolean isForcing) {
        if(mode == 0) {
            clearSurfaces();
            mSurfaceView.setVisibility(View.GONE);
            mSurfaceMode = 0;
            return;
        }
        if(!isForcing &&
                ((mode == 1 && mSurfaceMode == 1) || (mode == 2 && mSurfaceMode == 2)))
            return;
        if(mode == 1) {
            mSurfaceView.setVisibility(View.GONE);
            mSurfaceMode = 1;
        } else {
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceMode = 2;
        }
    }

    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right,
                                   int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            Size size = mController.getPictureOutputSize();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
            mPreviewProcessView.setLayoutParams(lp);
            mPreviewProcessView.setPanoPreviewSize(lp.width,
                    lp.height,
                    size.getWidth(),
                    size.getHeight());
        }
    };


    public void setLayout(Size size) {
        mSurfaceView.setAspectRatio(size.getHeight(), size.getWidth());
    }

    public PanoCaptureUI(CameraActivity activity, PanoCaptureModule controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.pano_capture_module,
                (ViewGroup) mRootView, true);

        mPreviewProcessView = (PanoCaptureProcessView)mRootView.findViewById(R.id.preview_process_view);
        mPreviewProcessView.setContext(activity, mController);
        mSurfaceView = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceView.addOnLayoutChangeListener(mLayoutListener);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mRootView.findViewById(R.id.mute_button).setVisibility(View.GONE);
        mRootView.findViewById(R.id.menu).setVisibility(View.GONE);
        applySurfaceChange(2, false);

        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mShutterButton.setLongClickable(false);
        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setVisibility(View.GONE);
        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);

        mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating())
                    mActivity.gotoGallery();
            }
        });

        mSceneModeLabelRect = (RotateLayout)mRootView.findViewById(R.id.scene_mode_label_rect);
        mSceneModeName = (TextView)mRootView.findViewById(R.id.scene_mode_label);
        mSceneModeName.setText(R.string.pref_camera_scenemode_entry_panorama);
        mSceneModeLabelCloseIcon = (ImageView)mRootView.findViewById(R.id.scene_mode_label_close);
        mSceneModeLabelCloseIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSceneModeLabelClose = true;
                mSceneModeLabelRect.setVisibility(View.GONE);
            }
        });
        initIndicators();

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);
        calculateMargins(size);
        mCameraControls.setMargins(mTopMargin, mBottomMargin);
        if ( needShowInstructional() ) {
            showSceneInstructionalDialog(mOrientation);
        }
    }

    private void calculateMargins(Point size) {
        int l = size.x > size.y ? size.x : size.y;
        int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
        int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
        mTopMargin = l / 4 * tm / (tm + bm);
        mBottomMargin = l / 4 - mTopMargin;
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
        mController.onPreviewUIReady();
        mActivity.updateThumbnail(mThumbnail);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mController.onPreviewUIDestroyed();
        mSurfaceHolder = null;
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mRootView.findViewById(R.id.on_screen_indicators));
    }

    public void onCameraOpened() {

    }

    public void hideUI() {
        mSwitcher.closePopup();
        if (mUIhidden)
            return;
        mUIhidden = true;
        mCameraControls.hideUI();
    }

    public void showUI() {
        mUIhidden = false;
        mCameraControls.showUI();
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
    }

    public void initializeShutterButton() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
        mShutterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Any animation is needed?
            }
        });
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void overrideSettings(final String... keyvalues) {
    }

    public boolean onBackPressed() {
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
        setShowMenu(previewFocused);
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
        return true;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public void onResume() {
        mPreviewProcessView.onResume();
        onPanoStatusChange(false);
        mCameraControls.getPanoramaExitButton().setVisibility(View.VISIBLE);
        mCameraControls.getPanoramaExitButton().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SettingsManager.getInstance().setValueIndex(SettingsManager.KEY_SCENE_MODE, SettingsManager.SCENE_MODE_AUTO_INT);
                } catch(NullPointerException e) {}
                mActivity.onModuleSelected(ModuleSwitcher.CAPTURE_MODULE_INDEX);
            }
        });
    }

    public void onPause() {
        collapseCameraControls();
        mPreviewProcessView.onPause();
        mCameraControls.getPanoramaExitButton().setVisibility(View.GONE);
        mCameraControls.getPanoramaExitButton().setOnClickListener(null);
    }

    // focus UI implementation
    private FocusIndicator getFocusIndicator() {
        return null;
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
        mPreviewProcessView.setOrientation(orientation);

        if ( mSceneModeLabelRect != null ) {
            if (orientation == 180) {
                mSceneModeName.setRotation(180);
                mSceneModeLabelCloseIcon.setRotation(180);
                mSceneModeLabelRect.setOrientation(0, false);
            } else {
                mSceneModeName.setRotation(0);
                mSceneModeLabelCloseIcon.setRotation(0);
                mSceneModeLabelRect.setOrientation(orientation, false);
            }
        }

        if ( mSceneModeInstructionalDialog != null && mSceneModeInstructionalDialog.isShowing()) {
            mSceneModeInstructionalDialog.dismiss();
            mSceneModeInstructionalDialog = null;
            showSceneInstructionalDialog(orientation);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public void onErrorListener(int error) {

    }

    private boolean needShowInstructional() {
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(mActivity), Context.MODE_PRIVATE);
        SettingsManager settingsManager = SettingsManager.getInstance();
        int index = settingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        final String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
        return !pref.getBoolean(instructionalKey, false);
    }

    private void showSceneInstructionalDialog(int orientation) {
        int layoutId = R.layout.scene_mode_instructional;
        if ( orientation == 90 || orientation == 270 ) {
            layoutId = R.layout.scene_mode_instructional_landscape;
        }
        LayoutInflater inflater =
                (LayoutInflater)mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(layoutId, null);

        TextView name = (TextView)view.findViewById(R.id.scene_mode_name);
        name.setText(R.string.pref_camera_scenemode_entry_panorama);

        ImageView icon = (ImageView)view.findViewById(R.id.scene_mode_icon);
        icon.setImageResource(R.drawable.ic_scene_mode_black_panorama);

        TextView instructional = (TextView)view.findViewById(R.id.scene_mode_instructional);
        instructional.setText(R.string.pref_camera2_scene_mode_panorama_instructional_content);

        final CheckBox remember = (CheckBox)view.findViewById(R.id.remember_selected);
        Button ok = (Button)view.findViewById(R.id.scene_mode_instructional_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                SharedPreferences pref = mActivity.getSharedPreferences(
                        ComboPreferences.getGlobalSharedPreferencesName(mActivity),
                        Context.MODE_PRIVATE);
                int index =
                        SettingsManager.getInstance().getValueIndex(SettingsManager.KEY_SCENE_MODE);
                String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
                if ( remember.isChecked()) {
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(instructionalKey, true);
                    editor.commit();
                }
                mSceneModeInstructionalDialog.dismiss();
                mSceneModeInstructionalDialog = null;
            }
        });
        mSceneModeInstructionalDialog =
                new AlertDialog.Builder(mActivity, AlertDialog.THEME_HOLO_LIGHT)
                        .setView(view).create();
        try {
            mSceneModeInstructionalDialog.show();
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        if ( orientation != 0 ) {
            rotationSceneModeInstructionalDialog(view, orientation);
        }
    }

    private int getScreenWidth() {
        DisplayMetrics metric = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metric);
        return metric.widthPixels < metric.heightPixels ? metric.widthPixels : metric.heightPixels;
    }

    private void rotationSceneModeInstructionalDialog(View view, int orientation) {
        view.setRotation(-orientation);
        int screenWidth = getScreenWidth();
        int dialogSize = screenWidth*9/10;
        Window dialogWindow = mSceneModeInstructionalDialog.getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        dialogWindow.setGravity(Gravity.CENTER);
        lp.width = lp.height = dialogSize;
        dialogWindow.setAttributes(lp);
        RelativeLayout layout = (RelativeLayout)view.findViewById(R.id.mode_layout_rect);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dialogSize, dialogSize);
        layout.setLayoutParams(params);
    }
}
