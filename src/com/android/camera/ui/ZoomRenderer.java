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

package com.android.camera.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.ScaleGestureDetector;

import org.codeaurora.snapcam.R;

public class ZoomRenderer extends OverlayRenderer
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_Zoom";

    private int mMaxZoom;
    private int mMinZoom;
    private OnZoomChangedListener mListener;

    private ScaleGestureDetector mDetector;
    private Paint mPaint;
    private Paint mTextPaint;
    private float mCircleSize;
    private int mCenterX;
    private int mCenterY;
    private float mMaxCircle;
    private float mMinCircle;
    private int mInnerStroke;
    private int mOuterStroke;
    private int mZoomSig;
    private int mZoomFraction;
    private Rect mTextBounds;
    private int mOrientation;
    private boolean mCamera2 = false;
    private float mZoomMinValue;
    private float mZoomMaxValue;

    public interface OnZoomChangedListener {
        void onZoomStart();
        void onZoomEnd();
        void onZoomValueChanged(int index);  // only for immediate zoom
        void onZoomValueChanged(float value);
    }

    public ZoomRenderer(Context ctx) {
        Resources res = ctx.getResources();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mTextPaint = new Paint(mPaint);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(res.getDimensionPixelSize(R.dimen.zoom_font_size));
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setAlpha(192);
        mInnerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
        mOuterStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
        mDetector = new ScaleGestureDetector(ctx, this);
        mMinCircle = res.getDimensionPixelSize(R.dimen.zoom_ring_min);
        mTextBounds = new Rect();
        setVisible(false);
    }

    // set from module
    public void setZoomMax(int zoomMaxIndex) {
        mMaxZoom = zoomMaxIndex;
        mMinZoom = 0;
    }

    public void setZoomMax(float zoomMax) {
        mCamera2 = true;
        mZoomMaxValue = zoomMax;
        mZoomMinValue = 1f;
    }

    public void setZoom(int index) {
        mCircleSize = mMinCircle + index * (mMaxCircle - mMinCircle) / (mMaxZoom - mMinZoom);
    }

    public void setZoom(float zoomValue) {
        mCamera2 = true;
        mZoomSig = (int) zoomValue;
        mZoomFraction = (int)(zoomValue * 10) % 10;
        mCircleSize = (int) (mMinCircle + (mMaxCircle - mMinCircle) * (zoomValue - mZoomMinValue) /
                (mZoomMaxValue - mZoomMinValue));
    }

    public void setZoomValue(int value) {
        value = value / 10;
        mZoomSig = value / 10;
        mZoomFraction = value % 10;
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
        mMaxCircle = Math.min(getWidth(), getHeight());
        mMaxCircle = (mMaxCircle - mMinCircle) / 2;
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.rotate(mOrientation, mCenterX, mCenterY);
        mPaint.setStrokeWidth(mInnerStroke);
        canvas.drawCircle(mCenterX, mCenterY, mMinCircle, mPaint);
        canvas.drawCircle(mCenterX, mCenterY, mMaxCircle, mPaint);
        canvas.drawLine(mCenterX - mMinCircle, mCenterY,
                mCenterX - mMaxCircle - 4, mCenterY, mPaint);
        mPaint.setStrokeWidth(mOuterStroke);
        canvas.drawCircle((float) mCenterX, (float) mCenterY,
                mCircleSize, mPaint);
        String txt = mZoomSig+"."+mZoomFraction+"x";
        mTextPaint.getTextBounds(txt, 0, txt.length(), mTextBounds);
        canvas.drawText(txt, mCenterX - mTextBounds.centerX(), mCenterY - mTextBounds.centerY(),
                mTextPaint);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        final float sf = detector.getScaleFactor();
        float circle = mCircleSize * sf * sf;
        circle = Math.max(mMinCircle, circle);
        circle = Math.min(mMaxCircle, circle);
        if (mListener != null && circle != mCircleSize) {
            mCircleSize = circle;
            if (mCamera2) {
                float zoom = mZoomMinValue + (mZoomMaxValue - mZoomMinValue) / (mMaxCircle -
                        mMinCircle) * (mCircleSize - mMinCircle);
                mListener.onZoomValueChanged(zoom);
            } else {
                int zoom = mMinZoom + (int) ((mCircleSize - mMinCircle) * (mMaxZoom - mMinZoom) /
                        (mMaxCircle - mMinCircle));
                mListener.onZoomValueChanged(zoom);
            }
            update();
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        setVisible(true);
        if (mListener != null) {
            mListener.onZoomStart();
        }
        update();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        setVisible(false);
        if (mListener != null) {
            mListener.onZoomEnd();
        }
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
        if (mOrientation == 90) mOrientation = 270;
        else if (mOrientation == 270) mOrientation = 90;
    }

}
