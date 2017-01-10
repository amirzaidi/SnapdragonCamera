/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.params.Face;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;

import com.android.camera.util.CameraUtil;

public class Camera2FaceView extends FaceView {

    private Face[] mFaces;
    private Face[] mPendingFaces;
    private Rect mCameraBound;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SWITCH_FACES:
                    mStateSwitchPending = false;
                    mFaces = mPendingFaces;
                    invalidate();
                    break;
            }
        }
    };

    public Camera2FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCameraBound(Rect cameraBound) {
        mCameraBound = cameraBound;
    }

    public void setFaces(Face[] faces) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);
        if (mPause) return;
        if (mFaces != null) {
            if ((faces.length > 0 && mFaces.length == 0)
                    || (faces.length == 0 && mFaces.length > 0)) {
                mPendingFaces = faces;
                if (!mStateSwitchPending) {
                    mStateSwitchPending = true;
                    mHandler.sendEmptyMessageDelayed(MSG_SWITCH_FACES, SWITCH_DELAY);
                }
                return;
            }
        }
        if (mStateSwitchPending) {
            mStateSwitchPending = false;
            mHandler.removeMessages(MSG_SWITCH_FACES);
        }
        mFaces = faces;
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0) && mCameraBound != null) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0) && mCameraBound != null) {
            int rw, rh;
            rw = mUncroppedWidth;
            rh = mUncroppedHeight;
            if (((rh > rw) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                    || ((rw > rh) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
                int temp = rw;
                rw = rh;
                rh = temp;
            }
            CameraUtil.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, rw, rh);

            // mMatrix assumes that the face coordinates are from -1000 to 1000.
            // so translate the face coordination to match the assumption.
            Matrix translateMatrix = new Matrix();
            translateMatrix.preTranslate(-mCameraBound.width() / 2f, -mCameraBound.height() / 2f);
            translateMatrix.postScale(2000f / mCameraBound.width(), 2000f / mCameraBound.height());

            int dx = (getWidth() - rw) / 2;
            int dy = (getHeight() - rh) / 2;

            // Focus indicator is directional. Rotate the matrix and the canvas
            // so it looks correctly in all orientations.
            canvas.save();
            mMatrix.postRotate(mOrientation); // postRotate is clockwise
            canvas.rotate(-mOrientation); // rotate is counter-clockwise (for canvas)

            float rectWidth;
            float rectHeight;
            float diameter;
            for (int i = 0; i < mFaces.length; i++) {
                if (mFaces[i].getScore() < 50) continue;
                Rect faceBound = mFaces[i].getBounds();
                faceBound.offset(-mCameraBound.left, -mCameraBound.top);
                mRect.set(faceBound);
                translateMatrix.mapRect(mRect);
                mMatrix.mapRect(mRect);
                mPaint.setColor(mColor);
                mRect.offset(dx, dy);

                rectHeight = mRect.bottom-mRect.top;
                rectWidth = mRect.right - mRect.left;
                diameter = rectHeight > rectWidth ? rectWidth : rectHeight;

                canvas.drawCircle(mRect.centerX(), mRect.centerY(), diameter/2, mPaint);
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    @Override
    public void clear() {
        // Face indicator is displayed during preview. Do not clear the
        // drawable.
        mColor = mFocusingColor;
        mFaces = null;
        invalidate();
    }
}
