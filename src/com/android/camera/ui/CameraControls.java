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

package com.android.camera.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.util.Log;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import java.util.ArrayList;

import org.codeaurora.snapcam.R;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ShutterButton;

public class CameraControls extends RotatableLayout {

    private static final String TAG = "CAM_Controls";

    private View mBackgroundView;
    private View mShutter;
    private View mSwitcher;
    private View mMenu;
    private View mFrontBackSwitcher;
    private View mHdrSwitcher;
    private View mIndicators;
    private View mPreview;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private int mSize;
    private static final int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;
    private static boolean isAnimating = false;
    private ArrayList<View> mViewList;
    private static final int FRONT_BACK_INDEX = 0;
    private static final int HDR_INDEX = 1;
    private static final int SCENE_MODE_INDEX = 2;
    private static final int FILTER_MODE_INDEX = 3;
    private static final int MENU_INDEX = 4;
    private static final int SWITCHER_INDEX = 5;
    private static final int SHUTTER_INDEX = 6;
    private static final int PREVIEW_INDEX = 7;
    private static final int INDICATOR_INDEX = 8;
    private static final int ANIME_DURATION = 300;
    private float[][] mLocX = new float[4][9];
    private float[][] mLocY = new float[4][9];
    private boolean[] mTempEnabled = new boolean[9];
    private boolean mLocSet = false;

    AnimatorListener outlistener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            resetLocation(0, 0);

            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
            mHdrSwitcher.setVisibility(View.INVISIBLE);
            mSceneModeSwitcher.setVisibility(View.INVISIBLE);
            mFilterModeSwitcher.setVisibility(View.INVISIBLE);

            mSwitcher.setVisibility(View.INVISIBLE);
            mShutter.setVisibility(View.INVISIBLE);
            mMenu.setVisibility(View.INVISIBLE);
            mIndicators.setVisibility(View.INVISIBLE);
            mPreview.setVisibility(View.INVISIBLE);
            isAnimating = false;
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            resetLocation(0, 0);

            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
            mHdrSwitcher.setVisibility(View.INVISIBLE);
            mSceneModeSwitcher.setVisibility(View.INVISIBLE);
            mFilterModeSwitcher.setVisibility(View.INVISIBLE);

            mSwitcher.setVisibility(View.INVISIBLE);
            mShutter.setVisibility(View.INVISIBLE);
            mMenu.setVisibility(View.INVISIBLE);
            mIndicators.setVisibility(View.INVISIBLE);
            mPreview.setVisibility(View.INVISIBLE);
            isAnimating = false;
            enableTouch(true);
        }
    };

    AnimatorListener inlistener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            isAnimating = false;
            resetLocation(0, 0);
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            isAnimating = false;
            resetLocation(0, 0);
            enableTouch(true);
        }
    };

    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMeasureAllChildren(true);
    }

    public CameraControls(Context context) {
        super(context);
        setMeasureAllChildren(true);
    }

    public static boolean isAnimating() {
        return isAnimating;
    }

    public void enableTouch(boolean enable) {
        if (enable) {
            ((ShutterButton) mShutter).setPressed(false);
            mSwitcher.setPressed(false);
            mMenu.setPressed(false);
            mFrontBackSwitcher.setPressed(false);
            mHdrSwitcher.setPressed(false);
            mSceneModeSwitcher.setPressed(false);
            mFilterModeSwitcher.setPressed(false);
        } else {
            mTempEnabled[FILTER_MODE_INDEX] = mFilterModeSwitcher.isEnabled();
        }
        ((ShutterButton) mShutter).enableTouch(enable);
        ((ModuleSwitcher) mSwitcher).enableTouch(enable);
        mMenu.setEnabled(enable);
        mFrontBackSwitcher.setEnabled(enable);
        mHdrSwitcher.setEnabled(enable);
        mSceneModeSwitcher.setEnabled(enable);
        mPreview.setEnabled(enable);
        mFilterModeSwitcher.setEnabled(enable && mTempEnabled[FILTER_MODE_INDEX]);
    }

    private void markVisibility() {
        mViewList = new ArrayList<View>();
        if (mFrontBackSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mFrontBackSwitcher);
        if (mHdrSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mHdrSwitcher);
        if (mSceneModeSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mSceneModeSwitcher);
        if (mFilterModeSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mFilterModeSwitcher);
        if (mShutter.getVisibility() == View.VISIBLE)
            mViewList.add(mShutter);
        if (mMenu.getVisibility() == View.VISIBLE)
            mViewList.add(mMenu);
        if (mIndicators.getVisibility() == View.VISIBLE)
            mViewList.add(mIndicators);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundView = findViewById(R.id.blocker);
        mSwitcher = findViewById(R.id.camera_switcher);
        mShutter = findViewById(R.id.shutter_button);
        mFrontBackSwitcher = findViewById(R.id.front_back_switcher);
        mHdrSwitcher = findViewById(R.id.hdr_switcher);
        mMenu = findViewById(R.id.menu);
        mIndicators = findViewById(R.id.on_screen_indicators);
        mPreview = findViewById(R.id.preview_thumb);
        mSceneModeSwitcher = findViewById(R.id.scene_mode_switcher);
        mFilterModeSwitcher = findViewById(R.id.filter_mode_switcher);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int orientation = getResources().getConfiguration().orientation;
        int size = getResources().getDimensionPixelSize(R.dimen.camera_controls_size);
        int rotation = getUnifiedRotation();
        adjustBackground();
        // As l,t,r,b are positions relative to parents, we need to convert them
        // to child's coordinates
        r = r - l;
        b = b - t;
        l = 0;
        t = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.layout(l, t, r, b);
        }
        Rect shutter = new Rect();
        center(mShutter, l, t, r, b, orientation, rotation, shutter, SHUTTER_INDEX);
        mSize = (int) (Math.max(shutter.right - shutter.left, shutter.bottom - shutter.top) * 1.2f);
        center(mBackgroundView, l, t, r, b, orientation, rotation, new Rect(), -1);
        mBackgroundView.setVisibility(View.GONE);
        setLocation(r - l, b - t);

        View retake = findViewById(R.id.btn_retake);
        if (retake != null) {
            center(retake, shutter, rotation);
            View cancel = findViewById(R.id.btn_cancel);
            toLeft(cancel, shutter, rotation);
            View done = findViewById(R.id.btn_done);
            toRight(done, shutter, rotation);
        }
    }

    private void setLocation(int w, int h) {
        int rotation = getUnifiedRotation();
        toIndex(mSwitcher, w, h, rotation, 4, 6, SWITCHER_INDEX);
        toIndex(mMenu, w, h, rotation, 4, 0, MENU_INDEX);
        toIndex(mIndicators, w, h, rotation, 0, 6, INDICATOR_INDEX);
        toIndex(mFrontBackSwitcher, w, h, rotation, 2, 0, FRONT_BACK_INDEX);
        toIndex(mPreview, w, h, rotation, 0, 6, PREVIEW_INDEX);
        toIndex(mHdrSwitcher, w, h, rotation, 3, 0, HDR_INDEX);
        toIndex(mFilterModeSwitcher, w, h, rotation, 1, 0, FILTER_MODE_INDEX);
        toIndex(mSceneModeSwitcher, w, h, rotation, 0, 0, SCENE_MODE_INDEX);
    }

    private void center(View v, int l, int t, int r, int b, int orientation, int rotation,
            Rect result, int idx) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        switch (rotation) {
            case 0:
                // phone portrait; controls bottom
                result.left = (r + l) / 2 - tw / 2 + lp.leftMargin;
                result.right = (r + l) / 2 + tw / 2 - lp.rightMargin;
                result.bottom = b - lp.bottomMargin;
                result.top = b - th + lp.topMargin;
                break;
            case 90:
                // phone landscape: controls right
                result.right = r - lp.rightMargin;
                result.left = r - tw + lp.leftMargin;
                result.top = (b + t) / 2 - th / 2 + lp.topMargin;
                result.bottom = (b + t) / 2 + th / 2 - lp.bottomMargin;
                break;
            case 180:
                // phone upside down: controls top
                result.left = (r + l) / 2 - tw / 2 + lp.leftMargin;
                result.right = (r + l) / 2 + tw / 2 - lp.rightMargin;
                result.top = t + lp.topMargin;
                result.bottom = t + th - lp.bottomMargin;
                break;
            case 270:
                // reverse landscape: controls left
                result.left = l + lp.leftMargin;
                result.right = l + tw - lp.rightMargin;
                result.top = (b + t) / 2 - th / 2 + lp.topMargin;
                result.bottom = (b + t) / 2 + th / 2 - lp.bottomMargin;
                break;
        }
        v.layout(result.left, result.top, result.right, result.bottom);
        if (idx != -1) {
            int idx1 = rotation / 90;
            int idx2 = idx;
            mLocX[idx1][idx2] = result.left;
            mLocY[idx1][idx2] = result.top;
        }
    }

    private void resetLocation(float x, float y) {
        int rotation = getUnifiedRotation();
        int idx1 = rotation / 90;

        mFrontBackSwitcher.setX(mLocX[idx1][FRONT_BACK_INDEX] + x);
        mHdrSwitcher.setX(mLocX[idx1][HDR_INDEX] + x);
        mSceneModeSwitcher.setX(mLocX[idx1][SCENE_MODE_INDEX] + x);
        mFilterModeSwitcher.setX(mLocX[idx1][FILTER_MODE_INDEX] + x);
        mMenu.setX(mLocX[idx1][MENU_INDEX] + x);
        mSwitcher.setX(mLocX[idx1][SWITCHER_INDEX] - x);
        mShutter.setX(mLocX[idx1][SHUTTER_INDEX] - x);
        mIndicators.setX(mLocX[idx1][INDICATOR_INDEX] - x);
        mPreview.setX(mLocX[idx1][PREVIEW_INDEX] - x);

        mFrontBackSwitcher.setY(mLocY[idx1][FRONT_BACK_INDEX] + y);
        mHdrSwitcher.setY(mLocY[idx1][HDR_INDEX] + y);
        mSceneModeSwitcher.setY(mLocY[idx1][SCENE_MODE_INDEX] + y);
        mFilterModeSwitcher.setY(mLocY[idx1][FILTER_MODE_INDEX] + y);
        mMenu.setY(mLocY[idx1][MENU_INDEX] + y);
        mSwitcher.setY(mLocY[idx1][SWITCHER_INDEX] - y);
        mShutter.setY(mLocY[idx1][SHUTTER_INDEX] - y);
        mIndicators.setY(mLocY[idx1][INDICATOR_INDEX] - y);
        mPreview.setY(mLocY[idx1][PREVIEW_INDEX] - y);
    }

    public void hideUI() {
        isAnimating = true;
        enableTouch(false);
        int rotation = getUnifiedRotation();
        mFrontBackSwitcher.animate().cancel();
        mHdrSwitcher.animate().cancel();
        mSceneModeSwitcher.animate().cancel();
        mFilterModeSwitcher.animate().cancel();
        mSwitcher.animate().cancel();
        mShutter.animate().cancel();
        mMenu.animate().cancel();
        mIndicators.animate().cancel();
        mPreview.animate().cancel();
        mFrontBackSwitcher.animate().setListener(outlistener);
        ((ModuleSwitcher) mSwitcher).removePopup();
        resetLocation(0, 0);
        markVisibility();
        switch (rotation) {
            case 0:
                mFrontBackSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 90:
                mFrontBackSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 180:
                mFrontBackSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 270:
                mFrontBackSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                break;
        }
    }

    public void showUI() {
        isAnimating = true;
        enableTouch(false);
        int rotation = getUnifiedRotation();
        mFrontBackSwitcher.animate().cancel();
        mHdrSwitcher.animate().cancel();
        mSceneModeSwitcher.animate().cancel();
        mFilterModeSwitcher.animate().cancel();
        mSwitcher.animate().cancel();
        mShutter.animate().cancel();
        mMenu.animate().cancel();
        mIndicators.animate().cancel();
        mPreview.animate().cancel();
        if (mViewList != null)
            for (View v : mViewList) {
                v.setVisibility(View.VISIBLE);
            }
        ((ModuleSwitcher) mSwitcher).removePopup();
        AnimationDrawable shutterAnim = (AnimationDrawable) mShutter.getBackground();
        if (shutterAnim != null)
            shutterAnim.stop();

        mMenu.setVisibility(View.VISIBLE);
        mIndicators.setVisibility(View.VISIBLE);
        mPreview.setVisibility(View.VISIBLE);

        mFrontBackSwitcher.animate().setListener(inlistener);
        switch (rotation) {
            case 0:
                resetLocation(0, -mSize);

                mFrontBackSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 90:
                resetLocation(-mSize, 0);

                mFrontBackSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 180:
                resetLocation(0, mSize);

                mFrontBackSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 270:
                resetLocation(mSize, 0);

                mFrontBackSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mHdrSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mSceneModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mIndicators.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                break;
        }
    }

    private void center(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        v.layout(cx - tw / 2 + lp.leftMargin,
                cy - th / 2 + lp.topMargin,
                cx + tw / 2 - lp.rightMargin,
                cy + th / 2 - lp.bottomMargin);
    }

    private void toIndex(View v, int w, int h, int rotation, int index, int index2, int index3) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = v.getMeasuredWidth();
        int th = v.getMeasuredHeight();
        int l = 0, r = 0, t = 0, b = 0;

        int wnumber = WIDTH_GRID;
        int hnumber = HEIGHT_GRID;
        int windex = 0;
        int hindex = 0;
        switch (rotation) {
            case 0:
                // portrait, to left of anchor at bottom
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = index;
                hindex = index2;
                break;
            case 90:
                // phone landscape: below anchor on right
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = index2;
                hindex = hnumber - index - 1;
                break;
            case 180:
                // phone upside down: right of anchor at top
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = wnumber - index - 1;
                hindex = hnumber - index2 - 1;
                break;
            case 270:
                // reverse landscape: above anchor on left
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = wnumber - index2 - 1;
                hindex = index;
                break;
        }
        int boxh = h / hnumber;
        int boxw = w / wnumber;
        int cx = (2 * windex + 1) * boxw / 2;
        int cy = (2 * hindex + 1) * boxh / 2;

        l = cx - tw / 2;
        r = cx + tw / 2;
        t = cy - th / 2;
        b = cy + th / 2;

        if (index3 != -1) {
            int idx1 = rotation / 90;
            int idx2 = index3;
            mLocX[idx1][idx2] = l;
            mLocY[idx1][idx2] = t;
        }
        v.layout(l, t, r, b);
    }

    private void toLeft(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        int l = 0, r = 0, t = 0, b = 0;
        switch (rotation) {
            case 0:
                // portrait, to left of anchor at bottom
                l = other.left - tw + lp.leftMargin;
                r = other.left - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 90:
                // phone landscape: below anchor on right
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.bottom + lp.topMargin;
                b = other.bottom + th - lp.bottomMargin;
                break;
            case 180:
                // phone upside down: right of anchor at top
                l = other.right + lp.leftMargin;
                r = other.right + tw - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 270:
                // reverse landscape: above anchor on left
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.top - th + lp.topMargin;
                b = other.top - lp.bottomMargin;
                break;
        }
        v.layout(l, t, r, b);
    }

    private void toRight(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        int l = 0, r = 0, t = 0, b = 0;
        switch (rotation) {
            case 0:
                l = other.right + lp.leftMargin;
                r = other.right + tw - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 90:
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.top - th + lp.topMargin;
                b = other.top - lp.bottomMargin;
                break;
            case 180:
                l = other.left - tw + lp.leftMargin;
                r = other.left - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 270:
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.bottom + lp.topMargin;
                b = other.bottom + th - lp.bottomMargin;
                break;
        }
        v.layout(l, t, r, b);
    }

    private void adjustBackground() {
        int rotation = getUnifiedRotation();
        // remove current drawable and reset rotation
        mBackgroundView.setBackgroundDrawable(null);
        mBackgroundView.setRotationX(0);
        mBackgroundView.setRotationY(0);
        // if the switcher background is top aligned we need to flip the
        // background
        // drawable vertically; if left aligned, flip horizontally
        switch (rotation) {
            case 180:
                mBackgroundView.setRotationX(180);
                break;
            case 270:
                mBackgroundView.setRotationY(180);
                break;
            default:
                break;
        }
        mBackgroundView.setBackgroundResource(R.drawable.switcher_bg);
    }

}
