/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.camera.PhotoUI;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.wrapper.ExtendedFaceWrapper;

public class FaceView extends View
    implements FocusIndicator, Rotatable,
    PhotoUI.SurfaceTextureSizeChangedListener {
    protected static final String TAG = "CAM FaceView";
    protected final boolean LOGV = false;
    // The value for android.hardware.Camera.setDisplayOrientation.
    protected int mDisplayOrientation;
    // The orientation compensation for the face indicator to make it look
    // correctly in all device orientations. Ex: if the value is 90, the
    // indicator should be rotated 90 degrees counter-clockwise.
    protected int mOrientation;
    protected boolean mMirror;
    protected boolean mPause;
    protected Matrix mMatrix = new Matrix();
    protected RectF mRect = new RectF();
    // As face detection can be flaky, we add a layer of filtering on top of it
    // to avoid rapid changes in state (eg, flickering between has faces and
    // not having faces)
    private Face[] mFaces;
    private Face[] mPendingFaces;
    protected int mColor;
    protected final int mFocusingColor;
    private final int mFocusedColor;
    private final int mFailColor;
    protected Paint mPaint;
    protected volatile boolean mBlocked;

    protected int mUncroppedWidth;
    protected int mUncroppedHeight;

    private final int smile_threashold_no_smile = 30;
    private final int smile_threashold_small_smile = 60;
    private final int blink_threshold = 60;

    protected static final int MSG_SWITCH_FACES = 1;
    protected static final int SWITCH_DELAY = 70;
    protected int mDisplayRotation = 0;
    protected boolean mStateSwitchPending = false;
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

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        mFocusingColor = res.getColor(R.color.face_detect_start);
        mFocusedColor = res.getColor(R.color.face_detect_success);
        mFailColor = res.getColor(R.color.face_detect_fail);
        mColor = mFocusingColor;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.STROKE);
        mPaint.setStrokeWidth(res.getDimension(R.dimen.face_circle_stroke));
        mPaint.setDither(true);
        mPaint.setColor(Color.WHITE);//setColor(0xFFFFFF00);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight) {
        mUncroppedWidth = uncroppedWidth;
        mUncroppedHeight = uncroppedHeight;
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
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0)) {
            invalidate();
        }

    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        invalidate();
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        if (LOGV) Log.v(TAG, "mMirror=" + mirror);
    }

    public boolean faceExists() {
        return (mFaces != null && mFaces.length > 0);
    }

    @Override
    public void showStart() {
        mColor = mFocusingColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showSuccess(boolean timeout) {
        mColor = mFocusedColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showFail(boolean timeout) {
        mColor = mFailColor;
        invalidate();
    }

    @Override
    public void clear() {
        // Face indicator is displayed during preview. Do not clear the
        // drawable.
        mColor = mFocusingColor;
        mFaces = null;
        invalidate();
    }

    public void pause() {
        mPause = true;
    }

    public void resume() {
        mPause = false;
    }

    public void setBlockDraw(boolean block) {
        mBlocked = block;
    }

    public void setDisplayRotation(int orientation) {
        mDisplayRotation = orientation;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0)) {
            int rw, rh;
            rw = mUncroppedWidth;
            rh = mUncroppedHeight;
            // Prepare the matrix.
            if (((rh > rw) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                    || ((rw > rh) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
                int temp = rw;
                rw = rh;
                rh = temp;
            }
            CameraUtil.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, rw, rh);
            int dx = (getWidth() - rw) / 2;;
            int dy = (getHeight() - rh) / 2;

            // Focus indicator is directional. Rotate the matrix and the canvas
            // so it looks correctly in all orientations.
            canvas.save();
            mMatrix.postRotate(mOrientation); // postRotate is clockwise
            canvas.rotate(-mOrientation); // rotate is counter-clockwise (for canvas)
            for (int i = 0; i < mFaces.length; i++) {
                // Filter out false positives.
                if (mFaces[i].score < 50) continue;

                // Transform the coordinates.
                mRect.set(mFaces[i].rect);
                if (LOGV) CameraUtil.dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) CameraUtil.dumpRect(mRect, "Transformed rect");
                mPaint.setColor(mColor);
                mRect.offset(dx, dy);
                canvas.drawOval(mRect, mPaint);

                if (ExtendedFaceWrapper.isExtendedFaceInstance(mFaces[i])) {
                    float[] point = new float[4];
                    int delta_x = mFaces[i].rect.width() / 12;
                    int delta_y = mFaces[i].rect.height() / 12;
                    Log.e(TAG, "blink: (" + ExtendedFaceWrapper.getLeftEyeBlinkDegree(mFaces[i])+ ", " +
                            ExtendedFaceWrapper.getRightEyeBlinkDegree(mFaces[i]) + ")");
                    if (mFaces[i].leftEye != null) {
                        if ((mDisplayRotation == 0) ||
                                (mDisplayRotation == 180)) {
                            point[0] = mFaces[i].leftEye.x;
                            point[1] = mFaces[i].leftEye.y - delta_y / 2;
                            point[2] = mFaces[i].leftEye.x;
                            point[3] = mFaces[i].leftEye.y + delta_y / 2;
                        } else {
                            point[0] = mFaces[i].leftEye.x - delta_x / 2;
                            point[1] = mFaces[i].leftEye.y;
                            point[2] = mFaces[i].leftEye.x + delta_x / 2;
                            point[3] = mFaces[i].leftEye.y;

                        }
                        mMatrix.mapPoints (point);
                        if (ExtendedFaceWrapper.getLeftEyeBlinkDegree(mFaces[i]) >= blink_threshold) {
                            canvas.drawLine(point[0]+ dx, point[1]+ dy,
                                point[2]+ dx, point[3]+ dy, mPaint);
                        }
                    }
                    if (mFaces[i].rightEye != null) {
                        if ((mDisplayRotation == 0) ||
                                (mDisplayRotation == 180)) {
                            point[0] = mFaces[i].rightEye.x;
                            point[1] = mFaces[i].rightEye.y - delta_y / 2;
                            point[2] = mFaces[i].rightEye.x;
                            point[3] = mFaces[i].rightEye.y + delta_y / 2;
                        } else {
                            point[0] = mFaces[i].rightEye.x - delta_x / 2;
                            point[1] = mFaces[i].rightEye.y;
                            point[2] = mFaces[i].rightEye.x + delta_x / 2;
                            point[3] = mFaces[i].rightEye.y;
                        }
                        mMatrix.mapPoints (point);
                        if (ExtendedFaceWrapper.getRightEyeBlinkDegree(mFaces[i]) >= blink_threshold) {
                            //Add offset to the points if the rect has an offset
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                point[2] +dx, point[3] +dy, mPaint);
                        }
                    }

                    if (ExtendedFaceWrapper.getLeftRightGazeDegree(mFaces[i]) != 0
                        || ExtendedFaceWrapper.getTopBottomGazeDegree(mFaces[i]) != 0 ) {

                        double length =
                            Math.sqrt((mFaces[i].leftEye.x - mFaces[i].rightEye.x) *
                                (mFaces[i].leftEye.x - mFaces[i].rightEye.x) +
                                (mFaces[i].leftEye.y - mFaces[i].rightEye.y) *
                                (mFaces[i].leftEye.y - mFaces[i].rightEye.y)) / 2.0;
                        double nGazeYaw = -ExtendedFaceWrapper.getLeftRightGazeDegree(mFaces[i]);
                        double nGazePitch = -ExtendedFaceWrapper.getTopBottomGazeDegree(mFaces[i]);
                        float gazeRollX =
                            (float)((-Math.sin(nGazeYaw/180.0*Math.PI) *
                                Math.cos(-ExtendedFaceWrapper.getRollDirection(mFaces[i])/
                                    180.0*Math.PI) +
                                Math.sin(nGazePitch/180.0*Math.PI) *
                                Math.cos(nGazeYaw/180.0*Math.PI) *
                                Math.sin(-ExtendedFaceWrapper.getRollDirection(mFaces[i])/
                                    180.0*Math.PI)) *
                                (-length) + 0.5);
                        float gazeRollY =
                            (float)((Math.sin(-nGazeYaw/180.0*Math.PI) *
                                Math.sin(-ExtendedFaceWrapper.getRollDirection(mFaces[i])/
                                    180.0*Math.PI)-
                                Math.sin(nGazePitch/180.0*Math.PI) *
                                Math.cos(nGazeYaw/180.0*Math.PI) *
                                Math.cos(-ExtendedFaceWrapper.getRollDirection(mFaces[i])/
                                    180.0*Math.PI)) *
                                (-length) + 0.5);

                        if (ExtendedFaceWrapper.getLeftEyeBlinkDegree(mFaces[i]) < blink_threshold) {
                            if ((mDisplayRotation == 90) ||
                                    (mDisplayRotation == 270)) {
                                point[0] = mFaces[i].leftEye.x;
                                point[1] = mFaces[i].leftEye.y;
                                point[2] = mFaces[i].leftEye.x + gazeRollX;
                                point[3] = mFaces[i].leftEye.y + gazeRollY;
                            } else {
                                point[0] = mFaces[i].leftEye.x;
                                point[1] = mFaces[i].leftEye.y;
                                point[2] = mFaces[i].leftEye.x + gazeRollY;
                                point[3] = mFaces[i].leftEye.y + gazeRollX;
                            }
                            mMatrix.mapPoints (point);
                            canvas.drawLine(point[0] +dx, point[1] + dy,
                                point[2] + dx, point[3] +dy, mPaint);
                        }

                        if (ExtendedFaceWrapper.getRightEyeBlinkDegree(mFaces[i]) < blink_threshold) {
                            if ((mDisplayRotation == 90) ||
                                    (mDisplayRotation == 270)) {
                                point[0] = mFaces[i].rightEye.x;
                                point[1] = mFaces[i].rightEye.y;
                                point[2] = mFaces[i].rightEye.x + gazeRollX;
                                point[3] = mFaces[i].rightEye.y + gazeRollY;
                            } else {
                                point[0] = mFaces[i].rightEye.x;
                                point[1] = mFaces[i].rightEye.y;
                                point[2] = mFaces[i].rightEye.x + gazeRollY;
                                point[3] = mFaces[i].rightEye.y + gazeRollX;

                            }
                            mMatrix.mapPoints (point);
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                point[2] + dx, point[3] + dy, mPaint);
                        }
                    }

                    if (mFaces[i].mouth != null) {
                        Log.e(TAG, "smile: " + ExtendedFaceWrapper.getSmileDegree(mFaces[i]) + "," +
                                ExtendedFaceWrapper.getSmileScore(mFaces[i]));
                        if (ExtendedFaceWrapper.getSmileDegree(mFaces[i]) < smile_threashold_no_smile) {
                            point[0] = mFaces[i].mouth.x + dx - delta_x;
                            point[1] = mFaces[i].mouth.y;
                            point[2] = mFaces[i].mouth.x + dx + delta_x;
                            point[3] = mFaces[i].mouth.y;

                            Matrix faceMatrix = new Matrix(mMatrix);
                            faceMatrix.preRotate(ExtendedFaceWrapper.getRollDirection(mFaces[i]),
                                    mFaces[i].mouth.x, mFaces[i].mouth.y);
                            faceMatrix.mapPoints(point);
                            canvas.drawLine(point[0] + dx, point[1] + dy,
                                point[2] + dx, point[3] + dy, mPaint);
                        } else if (ExtendedFaceWrapper.getSmileDegree(mFaces[i]) <
                            smile_threashold_small_smile) {
                            int rotation_mouth = 360 - mDisplayRotation;
                            mRect.set(mFaces[i].mouth.x-delta_x,
                                mFaces[i].mouth.y-delta_y, mFaces[i].mouth.x+delta_x,
                                mFaces[i].mouth.y+delta_y);
                            mMatrix.mapRect(mRect);
                            mRect.offset(dx, dy);
                            canvas.drawArc(mRect, rotation_mouth,
                                    180, true, mPaint);
                        } else {
                            mRect.set(mFaces[i].mouth.x-delta_x,
                                mFaces[i].mouth.y-delta_y, mFaces[i].mouth.x+delta_x,
                                mFaces[i].mouth.y+delta_y);
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
}
