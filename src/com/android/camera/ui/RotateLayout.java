/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import android.widget.FrameLayout;

// A RotateLayout is designed to display a single item and provides the
// capabilities to rotate the item.
public class RotateLayout extends ViewGroup implements Rotatable {
    @SuppressWarnings("unused")
    private static final String TAG = "RotateLayout";
    private int mOrientation;
    private Matrix mMatrix = new Matrix();
    protected View mChild;

    public RotateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The transparent background here is a workaround of the render issue
        // happened when the view is rotated as the device's orientation
        // changed. The view looks fine in landscape. After rotation, the view
        // is invisible.
        setBackgroundResource(android.R.color.transparent);
    }

    @Override
    protected void onFinishInflate() {
        setupChild(getChildAt(0));
    }

    private void setupChild(View child) {
        if (child != null) {
            mChild = child;
            child.setPivotX(0);
            child.setPivotY(0);
        }
    }

    public void addView(View child) {
        super.addView(child);
        setupChild(child);
    }

    public void removeView(View v) {
        super.removeView(v);
        mOrientation = 0;
    }

    @Override
    protected void onLayout(
            boolean change, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int p = getPaddingTop();
        switch (mOrientation) {
            case 0:
            case 180:
                mChild.layout(p, p, width - p, height - p);
                break;
            case 90:
            case 270:
                mChild.layout(p, p, height - p, width - p);
                break;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = 0, h = 0, p = getPaddingTop();
        switch(mOrientation) {
            case 0:
            case 180:
                measureChild(mChild, widthSpec, heightSpec);
                w = mChild.getMeasuredWidth();
                h = mChild.getMeasuredHeight();
                break;
            case 90:
            case 270:
                measureChild(mChild, heightSpec, widthSpec);
                w = mChild.getMeasuredHeight();
                h = mChild.getMeasuredWidth();
                break;
        }
        setMeasuredDimension(w + 2 * p, h + 2 * p);

        switch (mOrientation) {
            case 0:
                mChild.setTranslationX(0);
                mChild.setTranslationY(0);
                break;
            case 90:
                mChild.setTranslationX(0);
                mChild.setTranslationY(h);
                break;
            case 180:
                mChild.setTranslationX(w);
                mChild.setTranslationY(h);
                break;
            case 270:
                mChild.setTranslationX(w);
                mChild.setTranslationY(0);
                break;
        }
        mChild.setRotation(-mOrientation);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        orientation = orientation % 360;
        if (mOrientation == orientation) return;

        if (getParent() instanceof FrameLayout) {
            int diff = (orientation - mOrientation + 360) % 360;
            if (diff == 90) {
                RotatableLayout.rotateCounterClockwise(this);
            } else if (diff == 180) {
                RotatableLayout.rotateClockwise(this);
                RotatableLayout.rotateClockwise(this);
            } else if (diff == 270) {
                RotatableLayout.rotateClockwise(this);
            }
        }
        mOrientation = orientation;
        if (mChild != null)
            requestLayout();
    }

    public int getOrientation() {
        return mOrientation;
    }
}
