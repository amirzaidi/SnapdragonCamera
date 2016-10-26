/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.view.MotionEvent;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.CaptureUI;
import com.android.camera.imageprocessor.filter.TrackingFocusFrameListener;

public class TrackingFocusRenderer extends OverlayRenderer implements FocusIndicator {
    private FocusRequestThread mFocusRequestThread = null;
    private TrackingFocusFrameListener.Result mResult;
    private CameraActivity mActivity;
    private CaptureModule mModule;
    private Paint mTargetPaint;
    private int mInX = -1;
    private int mInY = -1;
    private final static int CIRCLE_THUMB_SIZE = 100;
    private Object mLock = new Object();
    private Rect mSurfaceDim;
    private CaptureUI mUI;

    public final static int STATUS_INIT = 0;
    public final static int STATUS_INPUT = 1;
    public final static int STATUS_TRACKING = 2;
    public final static int STATUS_TRACKED = 3;
    private int mStatus = STATUS_INIT;
    private boolean mIsFlipped = false;

    private final static String TAG = "TrackingFocusRenderer";
    private final static boolean DEBUG = false; //Enabling DEBUG LOG reduces the performance drastically.

    private void printErrorLog(String msg) {
        if(DEBUG) {
            android.util.Log.e(TAG, msg);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if(mModule.getMainCameraCharacteristics() != null &&
                mModule.getMainCameraCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION) == 270) {
            mIsFlipped = true;
        } else {
            mIsFlipped = false;
        }

        if(!visible) {
            synchronized (mLock) {
                mStatus = STATUS_INIT;
                mResult = null;
                mInX = 0;
                mInY = 0;
            }
            if(mFocusRequestThread != null) {
                mFocusRequestThread.kill();
                mFocusRequestThread = null;
            }
        } else {
            mFocusRequestThread = new FocusRequestThread();
            mFocusRequestThread.start();
        }
    }

    public void setSurfaceDim(int left, int top, int right, int bottom) {
        mSurfaceDim = new Rect(left, top, right, bottom);
    }

    public TrackingFocusRenderer(CameraActivity activity, CaptureModule module, CaptureUI ui) {
        mActivity = activity;
        mModule = module;
        mUI = ui;
        mTargetPaint = new Paint();
        mTargetPaint.setStrokeWidth(4f);
        mTargetPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public boolean handlesTouch() {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mSurfaceDim == null) {
            return true;
        }
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                synchronized (mLock) {
                    mInX = (int) event.getX();
                    mInY = (int) event.getY();
                    if(!mSurfaceDim.contains(mInX, mInY)) {
                        break;
                    }
                    mStatus = STATUS_INPUT;
                }
                update();
                break;
        }
        return true;
    }

    public int[] getInputCords(int width, int height) {
        synchronized (mLock) {
            if (mStatus != STATUS_INPUT) {
                return null;
            }
            mStatus = STATUS_TRACKING;
            int x = (mUI.getDisplaySize().y-1-mInY);
            int y = mInX;
            int bottomMargin = mUI.getDisplaySize().y - mSurfaceDim.bottom;
            x = (int)((x - bottomMargin)*((float)width/mSurfaceDim.height()));
            y = (int)((y - mSurfaceDim.left)*((float)height/mSurfaceDim.width()));

            /* It's supposed to give x,y like above but library x,y is reversed*/
            if(mModule.isBackCamera()) {
                if(!mIsFlipped) {
                    x = width - 1 - x;
                    y = height - 1 - y;
                }
            } else {  //Front camera
                if(!mIsFlipped) {
                    x = width - 1 - x;
                } else {
                    y = height - 1 - y;
                }
            }

            return new int[]{x, y};
        }
    }

    public void putRegisteredCords(TrackingFocusFrameListener.Result result, int width, int height) {
        synchronized (mLock) {
            if(result != null && result.pos != null &&
                    !(result.pos.width() == 0 && result.pos.height() == 0)) {
                result.pos = translateToSurface(result.pos, width, height);
                mResult = result;
                mStatus = STATUS_TRACKED;
            } else {
                mStatus = STATUS_TRACKING;
            }
        }
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                update();
            }
        });
    }

    private Rect translateToSurface(Rect src, int width, int height) {
        /* It's supposed to be this but direction is reversed in library.
        int x = src.centerY();
        int y = width-1-src.centerX();
         */
        int x = height-1-src.centerY();
        int y = src.centerX();

        if(!mModule.isBackCamera()) {
            if(mIsFlipped) {
                y = width - 1 - src.centerX();
            } else {
                x = src.centerY();
            }
        } else { //Back Camera
            if(mIsFlipped) {
                x = src.centerY();
                y = width - 1 -src.centerX();
            }
        }

        int w = (int)(src.height()*((float)mSurfaceDim.width()/height));
        int h = (int)(src.width()*((float)mSurfaceDim.height()/width));
        x = mSurfaceDim.left + (int)(x*((float)mSurfaceDim.width()/height));
        y = mSurfaceDim.top + (int)(y*((float)mSurfaceDim.height()/width));
        Rect rect = new Rect();
        rect.left = x - w/2;
        rect.top = y - h/2;
        rect.right = rect.left + w;
        rect.bottom = rect.top + h;
        return rect;
    }

    private Rect mRect;

    @Override
    public void onDraw(Canvas canvas) {
        synchronized (mLock) {
            if(mResult == null) {
                return;
            }
            if(mStatus == STATUS_TRACKED) {
                mRect = mResult.pos;
            }
        }

        if(mStatus == STATUS_TRACKED) {
            if(mRect != null) {
                mTargetPaint.setColor(Color.GREEN);
                canvas.drawRect(mRect, mTargetPaint);
            }
        } else if(mStatus == STATUS_TRACKING){
            if(mRect != null) {
                mTargetPaint.setColor(Color.RED);
                canvas.drawRect(mRect, mTargetPaint);
            }
        } else if(mStatus == STATUS_INPUT){
            mTargetPaint.setColor(Color.RED);
            canvas.drawCircle(mInX, mInY, CIRCLE_THUMB_SIZE, mTargetPaint);
        }
    }

    @Override
    public void showStart() {
    }

    @Override
    public void showSuccess(boolean timeout) {
    }

    @Override
    public void showFail(boolean timeout) {

    }

    @Override
    public void clear() {

    }

    private class FocusRequestThread extends Thread {
        private boolean isRunning = true;
        private final static int FOCUS_DELAY = 1000;
        private final static int MIN_DIFF_CORDS = 100;
        private final static int MIN_DIFF_SIZE = 100;
        private int mOldX = -MIN_DIFF_CORDS;
        private int mOldY = -MIN_DIFF_CORDS;
        private int mOldWidth = -MIN_DIFF_SIZE;
        private int mOldHeight = -MIN_DIFF_SIZE;
        private int mNewX;
        private int mNewY;
        private int mNewWidth;
        private int mNewHeight;

        public void kill() {
            isRunning = false;
        }

        public void run() {
            while(isRunning) {
                try {
                    Thread.sleep(FOCUS_DELAY);
                }catch(InterruptedException e) {
                    //Ignore
                }

                synchronized (mLock) {
                    if (mResult == null || mResult.pos == null
                            || (mResult.pos.centerX() == 0 && mResult.pos.centerY() == 0)) {
                        continue;
                    }
                    mNewX = mResult.pos.centerX();
                    mNewY = mResult.pos.centerY();
                    mNewWidth = mResult.pos.width();
                    mNewHeight = mResult.pos.height();
                }
                if(Math.abs(mOldX - mNewX) >= MIN_DIFF_CORDS || Math.abs(mOldY - mNewY) >= MIN_DIFF_CORDS  ||
                        Math.abs(mOldWidth - mNewWidth) >= MIN_DIFF_SIZE || Math.abs(mOldHeight - mNewHeight) >= MIN_DIFF_SIZE) {
                    try {
                        mModule.onSingleTapUp(null, mNewX, mNewY);
                    mOldX = mNewX;
                    mOldY = mNewY;
                    mOldWidth = mNewWidth;
                    mOldHeight = mNewHeight;
                    } catch(Exception e) {
                    }
                }
            }
        }
    }
}