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

import java.lang.reflect.Method;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

/**
 * The UI of {@link WideAnglePanoramaModule}.
 */
public class WideAnglePanoramaUI implements
        SurfaceHolder.Callback,
        ShutterButton.OnShutterButtonListener,
        CameraRootView.MyDisplayListener,
        View.OnLayoutChangeListener {

    @SuppressWarnings("unused")
    private static final String TAG = "CAM_WidePanoramaUI";

    private CameraActivity mActivity;
    private WideAnglePanoramaController mController;

    private ViewGroup mRootView;
    private ModuleSwitcher mSwitcher;
    private FrameLayout mCaptureLayout;
    private View mReviewLayout;
    private ImageView mReview;
    private View mPreviewBorder;
    private View mCaptureIndicator;
    private PanoProgressBar mSavingProgressBar;
    private TextView mTooFastPrompt;
    private View mPreviewLayout;
    private ViewGroup mReviewControl;
    private SurfaceView mSurfaceView;
    private ShutterButton mShutterButton;
    private CameraControls mCameraControls;
    private ImageView mThumbnail;
    private ImageView mCapturePreview;
    private ViewGroup mCapturePreviewLayout;

    private DialogHelper mDialogHelper;

    // Color definitions.
    private int mReviewBackground;
    private SurfaceHolder mSurfaceHolder;
    private View mPreviewCover;

    private int mOrientation;
    private int mPreviewYOffset;
    private RotateLayout mWaitingDialog;
    private RotateLayout mPanoFailedDialog;
    private Button mPanoFailedButton;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mOriginalPreviewWidth = 0;
    private int mOriginalPreviewHeight = 0;

    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;

    private Matrix mMatrix = null;

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private boolean mCapturePreviewSet = false;

    private final int capturePreviewW = 80;
    private final int capturePreviewH = 80;
    private final int sidePadding = 10;
    private final int botPadding = 130;

    private void setTransformMatrix(int width, int height) {
        mMatrix = mSurfaceView.getMatrix();

        // Calculate the new preview rectangle.
        RectF previewRect = new RectF(0, 0, width, height);
        mMatrix.mapRect(previewRect);
    }

    /** Constructor. */
    public WideAnglePanoramaUI(
            CameraActivity activity,
            WideAnglePanoramaController controller,
            ViewGroup root) {
        mActivity = activity;
        mController = controller;
        mRootView = root;

        createContentView();
        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.WIDE_ANGLE_PANO_MODULE_INDEX);
        mSwitcher.setSwitchListener(mActivity);
        mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating())
                    mActivity.gotoGallery();
            }
        });

        mSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSwitcher.showPopup();
                mSwitcher.setOrientation(mOrientation, false);
            }
        });

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

    public void onStartCapture() {
        hideSwitcher();
        mShutterButton.setImageResource(R.drawable.shutter_button_stop);
        mCaptureIndicator.setVisibility(View.VISIBLE);
    }

    public void showPreviewUI() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        showUI();
    }

    public void onStopCapture() {
        mCaptureIndicator.setVisibility(View.INVISIBLE);
        mCapturePreview.setImageBitmap(null);
        mCapturePreviewSet = false;
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void hideUI() {
        hideSwitcher();
        mCameraControls.setVisibility(View.INVISIBLE);
    }

    public void showUI() {
        showSwitcher();
        mCameraControls.setVisibility(View.VISIBLE);
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
    }

    public boolean arePreviewControlsVisible() {
        return (mCameraControls.getVisibility() == View.VISIBLE);
    }

    public void showSwitcher() {
        mSwitcher.setVisibility(View.VISIBLE);
    }

    public void drawCapturePreview(Bitmap bitmap, int mDeviceOrientationAtCapture, boolean horiz) {
        if (!mCapturePreviewSet) {
            int w = horiz ? LayoutParams.MATCH_PARENT : convertDpToPix(capturePreviewW);
            int h = horiz ? convertDpToPix(capturePreviewH) : LayoutParams.MATCH_PARENT;
            FrameLayout.LayoutParams param = (FrameLayout.LayoutParams) mCapturePreview
                    .getLayoutParams();
            param.height = h;
            param.width = w;
            param.gravity = Gravity.CENTER;
            mCapturePreview.setLayoutParams(param);

            mCapturePreviewSet = true;
            setPreviewOrientation(mDeviceOrientationAtCapture, horiz);

        }
        mCapturePreview.setImageBitmap(bitmap);
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        hidePreviewCover();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        mController.onPreviewUIReady();

        if (mPreviewWidth != 0 && mPreviewHeight != 0) {
            // Re-apply transform matrix for new surface texture
            setTransformMatrix(mPreviewWidth, mPreviewHeight);
        }

        mActivity.updateThumbnail(mThumbnail);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
        mController.onPreviewUIDestroyed();
    }

    public void reset() {
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
        mReviewLayout.setVisibility(View.GONE);
    }

    public void showFinalMosaic(Bitmap bitmap, int orientation) {
        if (bitmap != null && orientation != 0) {
            Matrix rotateMatrix = new Matrix();
            rotateMatrix.setRotate(orientation);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    rotateMatrix, false);
        }

        mActivity.updateThumbnail(bitmap);
    }

    public void onConfigurationChanged(
            Configuration newConfig, boolean threadRunning) {
        Drawable lowResReview = null;
        if (threadRunning) lowResReview = mReview.getDrawable();

        // Change layout in response to configuration change
        LayoutInflater inflater = (LayoutInflater)
                mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mReviewControl.removeAllViews();
        ((ViewGroup) mReviewControl).clearDisappearingChildren();
        inflater.inflate(R.layout.pano_review_control, mReviewControl, true);

        mRootView.bringChildToFront(mCameraControls);
        if (threadRunning) {
            mReview.setImageDrawable(lowResReview);
            mCaptureLayout.setVisibility(View.GONE);
            mReviewLayout.setVisibility(View.VISIBLE);
        }
    }

    private void setPanoramaPreviewView() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Display display = mActivity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int width = size.x;
        int height = size.y;
        int xOffset = 0;
        int yOffset = 0;
        int w = width;
        int h = height;

        h = w * 4 / 3;
        yOffset = (height - h) / 2;

        FrameLayout.LayoutParams param = new FrameLayout.LayoutParams(w, h);

        mSurfaceView.setLayoutParams(param);
        mSurfaceView.setX(xOffset);
        mSurfaceView.setY(yOffset);
        mPreviewBorder.setLayoutParams(param);
        mPreviewBorder.setX(xOffset);
        mPreviewBorder.setY(yOffset);
        mPreviewYOffset = yOffset;

        mCameraControls.setPreviewRatio(1.0f, true);
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // Do nothing.
    }

    @Override
    public void onShutterButtonClick() {
        mController.onShutterButtonClick();
    }

    @Override
    public void onShutterButtonLongClick() {}

    @Override
    public void onLayoutChange(
            View v, int l, int t, int r, int b,
            int oldl, int oldt, int oldr, int oldb) {
        mController.onPreviewUILayoutChange(l, t, r, b);
    }

    public void showAlertDialog(
            String title, String failedString,
            String OKString, Runnable runnable) {
        mDialogHelper.showAlertDialog(title, failedString, OKString, runnable);
    }

    public void showWaitingDialog(String title) {
        mDialogHelper.showWaitingDialog(title);
    }

    public void dismissAllDialogs() {
        mDialogHelper.dismissAll();
    }

    private void createContentView() {
        LayoutInflater inflator = (LayoutInflater) mActivity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflator.inflate(R.layout.panorama_module, mRootView, true);

        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        mPreviewLayout = mRootView.findViewById(R.id.pano_preview_layout);
        mReviewControl = (ViewGroup) mRootView.findViewById(R.id.pano_review_control);
        mReviewLayout = mRootView.findViewById(R.id.pano_review_layout);
        mReview = (ImageView) mRootView.findViewById(R.id.pano_reviewarea);
        mCaptureLayout = (FrameLayout) mRootView.findViewById(R.id.panorama_capture_layout);
        mCapturePreviewLayout = (FrameLayout) mRootView
                .findViewById(R.id.pano_capture_preview_layout);
        mCapturePreview = (ImageView) mRootView.findViewById(R.id.pano_capture_preview);
        mCapturePreview.setScaleType(ScaleType.FIT_CENTER);
        mPreviewBorder = mCaptureLayout.findViewById(R.id.pano_preview_area_border);

        mTooFastPrompt = (TextView) mRootView.findViewById(R.id.pano_capture_too_fast_textview);
        mCaptureIndicator = mRootView.findViewById(R.id.pano_capture_indicator);

        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(this);
        // Hide menu and indicators.
        mRootView.findViewById(R.id.menu).setVisibility(View.GONE);
        mRootView.findViewById(R.id.on_screen_indicators).setVisibility(View.GONE);
        mReview.setBackgroundColor(mReviewBackground);

        // TODO: set display change listener properly.
        ((CameraRootView) mRootView).setDisplayChangeListener(null);
        mSurfaceView = (SurfaceView) mRootView.findViewById(R.id.pano_preview_surfaceview);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceView.addOnLayoutChangeListener(this);
        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        setPanoramaPreviewView();

        mWaitingDialog = (RotateLayout) mRootView.findViewById(R.id.waitingDialog);
        mPanoFailedDialog = (RotateLayout) mRootView.findViewById(R.id.pano_dialog_layout);
        mPanoFailedButton = (Button) mRootView.findViewById(R.id.pano_dialog_button1);
        mDialogHelper = new DialogHelper();
    }

    @Override
    public void onDisplayChanged() {
        mCameraControls.checkLayoutFlip();
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    private class DialogHelper {

        DialogHelper() {
        }

        public void dismissAll() {
            if (mPanoFailedDialog != null) {
                mPanoFailedDialog.setVisibility(View.INVISIBLE);
            }
            if (mWaitingDialog != null) {
                mWaitingDialog.setVisibility(View.INVISIBLE);
            }
        }

        public void showAlertDialog(
                CharSequence title, CharSequence message,
                CharSequence buttonMessage, final Runnable buttonRunnable) {
            dismissAll();
            mPanoFailedButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    buttonRunnable.run();
                    mPanoFailedDialog.setVisibility(View.INVISIBLE);
                }
            });
            mPanoFailedDialog.setVisibility(View.VISIBLE);
        }

        public void showWaitingDialog(CharSequence message) {
            dismissAll();
            mWaitingDialog.setVisibility(View.VISIBLE);
        }
    }

    private static class FlipBitmapDrawable extends BitmapDrawable {

        public FlipBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int cx = bounds.centerX();
            int cy = bounds.centerY();
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.rotate(180, cx, cy);
            super.draw(canvas);
            canvas.restore();
        }
    }

    public boolean hideSwitcherPopup() {
        if (mSwitcher != null && mSwitcher.showsPopup()) {
            mSwitcher.closePopup();
            return true;
        }
        return false;
    }

    private int convertDpToPix(int dp) {
        Resources r = mActivity.getResources();
        return ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                r.getDisplayMetrics()));
    }


    public void setPreviewOrientation(int orientation, boolean horiz) {

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);
        int sW = size.x;
        int sH = size.y;
        ViewGroup v = mCapturePreviewLayout;
        int w = v.getWidth();
        int h = v.getHeight();
        int idx1;
        int idx2;
        int botPadding_pix = convertDpToPix(botPadding);
        int sidePadding_pix = convertDpToPix(sidePadding);

        final View dummy = mRootView.findViewById(R.id.pano_dummy_layout);
        int t = dummy.getTop();
        int b1 = dummy.getBottom();
        int r = dummy.getRight();
        int b2 = dummy.getBottom();
        int yc = (t + b1) / 2;
        int xc = r / 2;
        int x = xc;
        int y = yc;
        int vH = convertDpToPix(capturePreviewH);
        int vW = convertDpToPix(capturePreviewW);
        if (horiz) {
            v.setPivotX(w / 2);
            v.setPivotY(h / 2);
            switch (orientation) {
                case 90:
                    x = sW - vH / 2 - sidePadding_pix;
                    y = yc;
                    break;
                case 180:
                    x = xc;
                    y = t / 2;
                    break;
                case 270:
                    x = vH / 2 + sidePadding_pix;
                    y = yc;
                    break;
                default:
                    x = xc;
                    y = sH - botPadding_pix;
                    break;
            }
            v.setTranslationX(x - xc);
            v.setTranslationY(y - yc);
            v.setRotation(-orientation);
        } else {
            v.setPivotX(w / 2);
            v.setPivotY(h / 2);
            switch (orientation) {
                case 90:
                    x = xc;
                    y = sH - botPadding_pix;
                    break;
                case 180:
                    x = sW - vW / 2 - sidePadding_pix;
                    y = yc;
                    break;
                case 270:
                    x = xc;
                    y = t / 2;

                    break;
                default:
                    x = vW / 2 + sidePadding_pix;
                    y = yc;
                    break;
            }
            v.setTranslationX(x - xc);
            v.setTranslationY(y - yc);
            v.setRotation(-orientation);
        }
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        // '---------`
        // |    0    |
        // |---------| =t
        // | |     | |
        // |1|     |2|
        // | |     | |
        // |---------| =b1
        // |    3    |
        // `---------' =b2
        //          =r
        final View dummy = mRootView.findViewById(R.id.pano_dummy_layout);
        int t = dummy.getTop();
        int b1 = dummy.getBottom();
        int r = dummy.getRight();
        int b2 = dummy.getBottom();
        final FrameLayout progressLayout = (FrameLayout)
                mRootView.findViewById(R.id.pano_progress_layout);
        int pivotY = ((ViewGroup) progressLayout).getPaddingTop();
        int[] x = { r / 2, r / 10, r * 9 / 10, r / 2 };
        int[] y = { t / 2, (t + b1) / 2, (t + b1) / 2, b1 + pivotY };

        int idx1, idx2;
        int g;
        switch (orientation) {
            case 90:
                idx1 = 1;
                idx2 = 2;
                g = Gravity.TOP | Gravity.RIGHT;
                break;
            case 180:
                idx1 = 3;
                idx2 = 0;
                g = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case 270:
                idx1 = 2;
                idx2 = 1;
                g = Gravity.TOP | Gravity.RIGHT;
                break;
            default:
                idx1 = 0;
                idx2 = 3;
                g = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
        }

        final View[] views1 = {
            (View) mCaptureIndicator.getParent(),
            mRootView.findViewById(R.id.pano_review_indicator)
        };
        for (final View v : views1) {
            v.setTranslationX(x[idx1] - x[0]);
            v.setTranslationY(y[idx1]- y[0]);
            // use relection here to build on Kitkat
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    final Class cls = Class.forName("android.view.View");
                    final Method method = cls.getMethod("setTranslationZ", float.class);
                    method.invoke(v, 1);
                } catch (Exception e) {
                    // ignore
                }
            }
            v.setRotation(-orientation);
        }

        final View[] views2 = { progressLayout, mReviewControl};
        for (final View v : views2) {
            v.setPivotX(r / 2);
            v.setPivotY(pivotY);
            v.setTranslationX(x[idx2] - x[3]);
            v.setTranslationY(y[idx2] - y[3]);
            v.setRotation(-orientation);
        }

        final View button = mReviewControl.findViewById(R.id.pano_review_cancel_button);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
        lp.gravity = g;
        button.setLayoutParams(lp);
        mWaitingDialog.setRotation(-orientation);
        mPanoFailedDialog.setRotation(-orientation);
        mReview.setRotation(-orientation);
        mTooFastPrompt.setRotation(-orientation);
        mCameraControls.setOrientation(orientation, animation);
        RotateTextToast.setOrientation(orientation);
    }
}
