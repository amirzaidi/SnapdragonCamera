/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
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

import com.android.camera.ExtendedFace;
import com.android.camera.util.CameraUtil;

public class Camera2FaceView extends FaceView {

    private final int smile_threashold_no_smile = 30;
    private final int smile_threashold_small_smile = 60;
    private final int blink_threshold = 60;

    private Face[] mFaces;
    private ExtendedFace[] mExFaces;
    private Face[] mPendingFaces;
    private ExtendedFace[] mPendingExFaces;
    private Rect mCameraBound;
    private float mZoom = 1.0f;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SWITCH_FACES:
                    mStateSwitchPending = false;
                    mFaces = mPendingFaces;
                    mExFaces = mPendingExFaces;
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

    public void setZoom(float zoom) {
        mZoom = zoom;
    }

    public void setFaces(Face[] faces, ExtendedFace[] extendedFaces) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);
        if (mPause) return;
        if (mFaces != null) {
            if ((faces.length > 0 && mFaces.length == 0)
                    || (faces.length == 0 && mFaces.length > 0)) {
                mPendingFaces = faces;
                mPendingExFaces = extendedFaces;
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
        mExFaces = extendedFaces;
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

            Matrix bsgcTranslateMatrix = new Matrix();
            bsgcTranslateMatrix.preTranslate(-mCameraBound.width() / 2f * mZoom,
                    -mCameraBound.height() / 2f * mZoom);
            bsgcTranslateMatrix.postScale(2000f / mCameraBound.width(),
                    2000f / mCameraBound.height());

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
            int extendFaceSize = 0;
            extendFaceSize = mExFaces == null? 0 : mExFaces.length;
            for (int i = 0; i < mFaces.length; i++) {
                if (mFaces[i].getScore() < 50) continue;
                Rect faceBound = mFaces[i].getBounds();
                faceBound.offset(-mCameraBound.left, -mCameraBound.top);
                mRect.set(faceBound);
                translateMatrix.mapRect(mRect);
                if (LOGV) CameraUtil.dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) CameraUtil.dumpRect(mRect, "Transformed rect");
                mPaint.setColor(mColor);
                mRect.offset(dx, dy);

                rectHeight = mRect.bottom-mRect.top;
                rectWidth = mRect.right - mRect.left;
                diameter = rectHeight > rectWidth ? rectWidth : rectHeight;

                canvas.drawCircle(mRect.centerX(), mRect.centerY(), diameter/2, mPaint);

                if (i < extendFaceSize && mExFaces[i] != null) {
                    ExtendedFace exFace = mExFaces[i];
                    Face face = mFaces[i];
                    float[] point = new float[4];
                    int delta_x = faceBound.width() / 12;
                    int delta_y = faceBound.height() / 12;

                    delta_x = (int)(delta_x * mZoom);
                    delta_y = (int)(delta_y * mZoom);

                    Log.e(TAG, "blink: (" + exFace.getLeyeBlink()+ ", " +
                            exFace.getReyeBlink() + ")");
                    if (face.getLeftEyePosition() != null) {
                        if ((mDisplayRotation == 0) ||
                                (mDisplayRotation == 180)) {
                            point[0] = face.getLeftEyePosition().x;
                            point[1] = face.getLeftEyePosition().y - delta_y / 2;
                            point[2] = face.getLeftEyePosition().x;
                            point[3] = face.getLeftEyePosition().y + delta_y / 2;
                        } else {
                            point[0] = face.getLeftEyePosition().x - delta_x / 2;
                            point[1] = face.getLeftEyePosition().y;
                            point[2] = face.getLeftEyePosition().x + delta_x / 2;
                            point[3] = face.getLeftEyePosition().y;
                        }
                        bsgcTranslateMatrix.mapPoints(point);
                        mMatrix.mapPoints (point);
                        if (exFace.getLeyeBlink() >= blink_threshold) {
                            canvas.drawLine(point[0]+ dx, point[1]+ dy,
                                    point[2]+ dx, point[3]+ dy, mPaint);
                        }
                    }
                    if (face.getRightEyePosition() != null) {
                        if ((mDisplayRotation == 0) ||
                                (mDisplayRotation == 180)) {
                            point[0] = face.getRightEyePosition().x;
                            point[1] = face.getRightEyePosition().y - delta_y / 2;
                            point[2] = face.getRightEyePosition().x;
                            point[3] = face.getRightEyePosition().y + delta_y / 2;
                        } else {
                            point[0] = face.getRightEyePosition().x - delta_x / 2;
                            point[1] = face.getRightEyePosition().y;
                            point[2] = face.getRightEyePosition().x + delta_x / 2;
                            point[3] = face.getRightEyePosition().y;
                        }
                        bsgcTranslateMatrix.mapPoints(point);
                        mMatrix.mapPoints (point);
                        if (exFace.getReyeBlink() >= blink_threshold) {
                            //Add offset to the points if the rect has an offset
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                    point[2] +dx, point[3] +dy, mPaint);
                        }
                    }

                    if (exFace.getLeftrightGaze() != 0
                            || exFace.getTopbottomGaze() != 0 ) {

                        double length =
                                Math.sqrt((face.getLeftEyePosition().x - face.getRightEyePosition().x) *
                                        (face.getLeftEyePosition().x - face.getRightEyePosition().x) +
                                        (face.getLeftEyePosition().y - face.getRightEyePosition().y) *
                                                (face.getLeftEyePosition().y - face.getRightEyePosition().y)) / 2.0;
                        double nGazeYaw = -exFace.getLeftrightGaze();
                        double nGazePitch = -exFace.getTopbottomGaze();
                        float gazeRollX =
                                (float)((-Math.sin(nGazeYaw/180.0*Math.PI) *
                                        Math.cos(-exFace.getRollDirection()/
                                                180.0*Math.PI) +
                                        Math.sin(nGazePitch/180.0*Math.PI) *
                                                Math.cos(nGazeYaw/180.0*Math.PI) *
                                                Math.sin(-exFace.getRollDirection()/
                                                        180.0*Math.PI)) *
                                        (-length) + 0.5);
                        float gazeRollY =
                                (float)((Math.sin(-nGazeYaw/180.0*Math.PI) *
                                        Math.sin(-exFace.getRollDirection()/
                                                180.0*Math.PI)-
                                        Math.sin(nGazePitch/180.0*Math.PI) *
                                                Math.cos(nGazeYaw/180.0*Math.PI) *
                                                Math.cos(-exFace.getRollDirection()/
                                                        180.0*Math.PI)) *
                                        (-length) + 0.5);

                        if (exFace.getLeyeBlink() < blink_threshold) {
                            if ((mDisplayRotation == 90) ||
                                    (mDisplayRotation == 270)) {
                                point[0] = face.getLeftEyePosition().x;
                                point[1] = face.getLeftEyePosition().y;
                                point[2] = face.getLeftEyePosition().x + gazeRollX;
                                point[3] = face.getLeftEyePosition().y + gazeRollY;
                            } else {
                                point[0] = face.getLeftEyePosition().x;
                                point[1] = face.getLeftEyePosition().y;
                                point[2] = face.getLeftEyePosition().x + gazeRollY;
                                point[3] = face.getLeftEyePosition().y + gazeRollX;
                            }
                            bsgcTranslateMatrix.mapPoints(point);
                            mMatrix.mapPoints (point);
                            canvas.drawLine(point[0] +dx, point[1] + dy,
                                    point[2] + dx, point[3] +dy, mPaint);
                        }

                        if (exFace.getReyeBlink() < blink_threshold) {
                            if ((mDisplayRotation == 90) ||
                                    (mDisplayRotation == 270)) {
                                point[0] = face.getRightEyePosition().x;
                                point[1] = face.getRightEyePosition().y;
                                point[2] = face.getRightEyePosition().x + gazeRollX;
                                point[3] = face.getRightEyePosition().y + gazeRollY;
                            } else {
                                point[0] = face.getRightEyePosition().x;
                                point[1] = face.getRightEyePosition().y;
                                point[2] = face.getRightEyePosition().x + gazeRollY;
                                point[3] = face.getRightEyePosition().y + gazeRollX;
                            }
                            bsgcTranslateMatrix.mapPoints(point);
                            mMatrix.mapPoints (point);
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                    point[2] + dx, point[3] + dy, mPaint);
                        }
                    }

                    if (face.getMouthPosition() != null) {
                        Log.e(TAG, "smile: " + exFace.getSmileDegree() + "," +
                                exFace.getSmileConfidence());
                        if (exFace.getSmileDegree() < smile_threashold_no_smile) {
                            point[0] = face.getMouthPosition().x + dx - delta_x;
                            point[1] = face.getMouthPosition().y;
                            point[2] = face.getMouthPosition().x + dx + delta_x;
                            point[3] = face.getMouthPosition().y;
                            Matrix faceMatrix = new Matrix();
                            faceMatrix.preRotate(exFace.getRollDirection(),
                                    face.getMouthPosition().x, face.getMouthPosition().y);
                            faceMatrix.mapPoints(point);
                            bsgcTranslateMatrix.mapPoints(point);
                            mMatrix.mapPoints(point);
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                    point[2] + dx, point[3] + dy, mPaint);
                        } else if (exFace.getSmileDegree() <
                                smile_threashold_small_smile) {
                            int rotation_mouth = 360 - mDisplayRotation;
                            mRect.set(face.getMouthPosition().x-delta_x,
                                    face.getMouthPosition().y-delta_y, face.getMouthPosition().x+delta_x,
                                    face.getMouthPosition().y+delta_y);
                            bsgcTranslateMatrix.mapRect(mRect);
                            mMatrix.mapRect(mRect);
                            mRect.offset(dx, dy);
                            canvas.drawArc(mRect, rotation_mouth,
                                    180, true, mPaint);
                        } else {
                            mRect.set(face.getMouthPosition().x-delta_x,
                                    face.getMouthPosition().y-delta_y, face.getMouthPosition().x+delta_x,
                                    face.getMouthPosition().y+delta_y);
                            bsgcTranslateMatrix.mapRect(mRect);
                            mMatrix.mapRect(mRect);
                            mRect.offset(dx, dy);
                            canvas.drawOval(mRect, mPaint);
                        }
                    }
                }
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
        mExFaces = null;
        invalidate();
    }
}
