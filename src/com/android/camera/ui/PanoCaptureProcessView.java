/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.PanoCaptureModule;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PanoCaptureProcessView extends View implements SensorEventListener {
    private static final int DEG_INIT_VALUE = 365;
    private float mCurrDegX = DEG_INIT_VALUE;
    private float mCurrDegY = DEG_INIT_VALUE;
    private final static int OBJ_DEPTH = 800;
    private RectF rectF = new RectF();
    private CameraActivity mActivity;
    private PanoCaptureModule mController;
    private Matrix matrix = new Matrix();
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private float[] mRots = new float[5];
    private float[] mOldRots = new float[5];
    private float[] mR = new float[9];
    private float[] mRR = new float[9];
    private float[] mOrients = new float[3];
    private int mOrientation;
    private int mPendingOrientation;
    private Bitmap tmpBitmap;
    private Paint mCenterRectPaint = new Paint();

    public static int mPreviewThumbWidth;
    public static int mPreviewThumbHeight;
    private static float mPanoPreviewRatioToCamera;
    public static int mFinalPictureWidth;
    public static int mFinalPictureHeight;
    private static float mFinalPictureRatioToCamera;
    private Picture mPreviewPicture;
    private int[] mAargbBuffer;
    private byte[] mDataBuffer;
    public static int MAX_PANO_FRAME = 6;
    private static String TAG = "PanoramaCapture";
    private Bitmap mTempBitmap;
    private int mTempOrietnation;
    private Picture mGuidePicture;
    private Handler mHandler;
    private final static int DIRECTION_GOT_LOST = -1;
    private final static int DIRECTION_LEFTRIGHT = 0;
    private final static int DIRECTION_UPDOWN = 1;
    private int mDir = DIRECTION_LEFTRIGHT;
    private boolean mShouldFinish = false;
    private static final boolean DEBUG = false; //TODO: This has to be false before release
    private BitmapArrayOutputStream mBitmapStream;
    private static boolean mIsSupported = false;
    private Object mBitmapStreamLock = new Object();

    private boolean mIsFrameProcessing = false;
    enum PANO_STATUS {
        INACTIVE,
        ACTIVE_UNKNOWN,
        ACTIVE_LEFT,
        ACTIVE_RIGHT,
        ACTIVE_UP,
        ACTIVE_DOWN,

        /* These two statuses below are for showing the background thread is running
           while subsequent frame input will be ignored and passed.
          */
        COMPLETING,
        OPENING
    };
    private PANO_STATUS mPanoStatus = PANO_STATUS.INACTIVE;
    private Object mPreviewBitmapLock = new Object();
    private static int DECISION_MARGIN;
    private boolean mIsFirstBlend;
    private PanoQueueProcessor mQueueProcessor;
    private ProgressDialog mProgressDialog;
    private String mCompleteSentence = "";
    private String mProgressSentence = "";
    private String mIntroSentence = "";
    private Paint mCompleteSentencePaint = new Paint();
    private int mFinalDoneLength;

    class Picture {
        Bitmap bitmap;
        Bitmap bitmapIn;
        float xDeg;
        float yDeg;
        int xPos;
        int yPos;
        int leftIn;
        int topIn;
        int width;
        int height;
        Matrix mat;
        RectF rF;
        float[] pts;
        Paint paintInAir = new Paint();
        Paint paintFrameEdge = new Paint();

        public Picture(Bitmap bm, float xdeg, float ydeg, int x, int y) {
            init(bm, xdeg, ydeg, x, y);
        }

        public Picture(Bitmap bm, float xdeg, float ydeg, int x, int y, int w, int h) {
            init(bm, xdeg, ydeg, x, y);
            width = w;
            height = h;
        }

        private void init(Bitmap bm, float xdeg, float ydeg, int x, int y) {
            bitmap = bm;
            xDeg = xdeg;
            yDeg = ydeg;
            xPos = x;
            yPos = y;
            mat = new Matrix();
            rF = new RectF();
            pts = new float[8];
            paintInAir.setAlpha(124);
            if(bm != null) {
                width = bm.getWidth();
                height = bm.getHeight();
            }
            paintFrameEdge.setColor(Color.WHITE);
            paintFrameEdge.setStrokeWidth(2f);
            paintFrameEdge.setStyle(Paint.Style.STROKE);
        }

        public void drawPictureInAir(Canvas canvas) {
            float setha, x, y;

            setha = ((xDeg - mCurrDegX) + 360) % 360;
            if(90 <= setha && setha <= 270)
                return;
            x = (OBJ_DEPTH * (float) Math.sin(Math.toRadians(setha)));
            setha = ((yDeg - mCurrDegY) + 360) % 360;
            if(90 <= setha && setha <= 270)
                return;
            y = (OBJ_DEPTH * (float) Math.sin(Math.toRadians(setha)));

            rF.left = canvas.getWidth()/2 + x - bitmap.getWidth()/2;
            rF.right = canvas.getWidth()/2 + x + bitmap.getWidth()/2;
            rF.top = canvas.getHeight()/2 + y - bitmap.getHeight()/2;
            rF.bottom = canvas.getHeight()/2 + y + bitmap.getHeight()/2;
            skew(rF, pts, x, y, canvas.getWidth() / 2, canvas.getHeight() / 2);
            mat.reset();
            mat.setPolyToPoly(new float[]{rF.left, rF.top,
                            rF.right, rF.top,
                            rF.right, rF.bottom,
                            rF.left, rF.bottom},
                    0,
                    pts,
                    0,
                    4
            );
            canvas.translate(rF.left, rF.top);
            canvas.drawBitmap(bitmap, mat, paintInAir);
        }

        public void drawGuideInAir(Canvas canvas) {
            float setha, x, y;

            setha = ((xDeg - mCurrDegX) + 360) % 360;
            if(90 <= setha && setha <= 270)
                return;
            x = (OBJ_DEPTH * (float) Math.sin(Math.toRadians(setha)));
            setha = ((yDeg - mCurrDegY) + 360) % 360;
            if(90 <= setha && setha <= 270)
                return;
            y = (OBJ_DEPTH * (float) Math.sin(Math.toRadians(setha)));

            rF.left = canvas.getWidth()/2 + x - width;
            rF.right = canvas.getWidth()/2 + x + width;
            rF.top = canvas.getHeight()/2 + y - height;
            rF.bottom = canvas.getHeight()/2 + y + height;
            skew(rF, pts, x, y, canvas.getWidth() / 2, canvas.getHeight() / 2);
            for(int i=1; i < 4; i++) {
                canvas.drawLine(pts[2*(i-1)], pts[2*(i-1)+1], pts[2*i], pts[2*i+1], paintFrameEdge);
            }
            canvas.drawLine(pts[0], pts[1], pts[6], pts[7], paintFrameEdge);
        }

        public void drawMasterPanoPreview(Canvas canvas) {
            int bitmapWidth;
            int bitmapHeight;
            if(mPanoStatus == PANO_STATUS.ACTIVE_LEFT || mPanoStatus == PANO_STATUS.ACTIVE_RIGHT) {
                bitmapWidth = mPreviewPicture.bitmap.getWidth();
                bitmapHeight = mPreviewPicture.bitmap.getHeight();
                rectF.left = canvas.getWidth() / 2 - bitmapWidth / 2;
                rectF.right = canvas.getWidth() / 2 + bitmapWidth / 2;
                rectF.top = canvas.getHeight() * 4 / 5 - bitmapHeight;
                rectF.bottom = canvas.getHeight() * 4 / 5;
                canvas.drawBitmap(mPreviewPicture.bitmap, null, rectF, null);
                canvas.drawRect(rectF, paintFrameEdge);

            } else if(mPanoStatus == PANO_STATUS.ACTIVE_UP || mPanoStatus == PANO_STATUS.ACTIVE_DOWN) {
                bitmapWidth = mPreviewPicture.bitmap.getWidth();
                bitmapHeight = mPreviewPicture.bitmap.getHeight();
                rectF.left = canvas.getWidth() / 4 - bitmapWidth / 2;
                rectF.right = canvas.getWidth() / 4 + bitmapWidth / 2;
                rectF.top = canvas.getHeight() / 2 - bitmapHeight / 2;
                rectF.bottom = canvas.getHeight() / 2 + bitmapHeight / 2;
                canvas.drawBitmap(mPreviewPicture.bitmap, null, rectF, null);
                canvas.drawRect(rectF, paintFrameEdge);
            }
            if(mOrientation == 0 || mOrientation == 180) {
                rectF.left += leftIn;
                rectF.right = rectF.left + mPreviewThumbWidth;
                rectF.top += topIn;
                rectF.bottom = rectF.top + mPreviewThumbHeight;
            } else {
                rectF.left += leftIn;
                rectF.right = rectF.left + mPreviewThumbHeight;
                rectF.top += topIn;
                rectF.bottom = rectF.top + mPreviewThumbWidth;
            }
            canvas.drawBitmap(mPreviewPicture.bitmapIn, null, rectF, null);
            canvas.drawRect(rectF, paintFrameEdge);
        }

        private void skew(RectF src, float[] pts, float x, float y, float Wh, float Hh) {
            float lh = src.height(); //Left height
            float tw = src.width();  //Top width
            float rh = lh;           //Right height
            float bw = tw;           //Bottom width

            if(x < 0) {
                lh = lh * ((-x/Wh)/2f + 1);
            } else {
                rh = rh * ((x/Wh)/2f + 1);
            }

            if(y < 0) {
                tw = tw * ((-y/Hh)/2f + 1);
            } else {
                bw = bw * ((y/Hh)/2f + 1);
            }

            //Left Top
            pts[0] = src.centerX() - tw/2;
            pts[1] = src.centerY() - lh/2;
            //Right Top
            pts[2] = src.centerX() + tw/2;
            pts[3] = src.centerY() - rh/2;
            //Right Bottom
            pts[4] = src.centerX() + bw/2;
            pts[5] = src.centerY() + rh/2;
            //Left Bottom
            pts[6] = src.centerX() - bw/2;
            pts[7] = src.centerY() + lh/2;
        }
    }

    public void setContext(CameraActivity activity, PanoCaptureModule contoller) {
        mActivity = activity;
        mController = contoller;
        mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mCenterRectPaint.setColor(Color.CYAN);
        mCenterRectPaint.setStrokeWidth(2f);
        mCenterRectPaint.setStyle(Paint.Style.STROKE);
        mCompleteSentencePaint.setColor(Color.WHITE);
        mCompleteSentencePaint.setTextSize(45f);
        mQueueProcessor = new PanoQueueProcessor();
        mQueueProcessor.start();
        mHandler = new Handler();
        mIntroSentence = mActivity.getResources().getString(R.string.panocapture_intro);
    }

    public void onPause() {
        mSensorManager.unregisterListener(this, mRotationSensor);
        synchronized (mBitmapStreamLock) {
            if(mBitmapStream != null) {
                try {
                    mBitmapStream.close();
                } catch (IOException e) {
                    //Ignore
                }
                mBitmapStream = null;
            }
        }
    }

    public void onResume() {
        mSensorManager.registerListener(this, mRotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void setPanoPreviewSize(int width, int height, int cameraWidth, int cameraHeight) {
        mPreviewThumbWidth = width / (PanoCaptureProcessView.MAX_PANO_FRAME+2) / 2 * 2;
        mPreviewThumbHeight = height / (PanoCaptureProcessView.MAX_PANO_FRAME+2) / 2 * 2;
        mFinalPictureWidth = width / 2 * 2;
        mFinalPictureHeight = height / 2 * 2;
        mAargbBuffer = new int[mPreviewThumbWidth * mPreviewThumbHeight];
        mDataBuffer = new byte[mPreviewThumbWidth * mPreviewThumbHeight * 3 / 2];

        DECISION_MARGIN = (int)(0.2 * mPreviewThumbHeight);

        mPanoPreviewRatioToCamera = (float)Math.min(mPreviewThumbWidth, mPreviewThumbHeight) /
                                    (float)Math.min(cameraWidth, cameraHeight);
        mFinalPictureRatioToCamera = (float)Math.min(mFinalPictureWidth, mFinalPictureHeight) /
                                     (float)Math.min(cameraWidth, cameraHeight);
    }

    public PanoCaptureProcessView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOrientation == 0 || mOrientation == 180) {
            rectF.left = canvas.getWidth() / 2 - mPreviewThumbWidth;
            rectF.right = canvas.getWidth() / 2 + mPreviewThumbWidth;
            rectF.top = canvas.getHeight() / 2 - mPreviewThumbHeight;
            rectF.bottom = canvas.getHeight() / 2 + mPreviewThumbHeight;
        } else {
            rectF.left = canvas.getWidth() / 2 - mPreviewThumbHeight;
            rectF.right = canvas.getWidth() / 2 + mPreviewThumbHeight;
            rectF.top = canvas.getHeight() / 2 - mPreviewThumbWidth;
            rectF.bottom = canvas.getHeight() / 2 + mPreviewThumbWidth;
        }

        if(mPanoStatus != PANO_STATUS.INACTIVE) {
            canvas.rotate(-mOrientation, canvas.getWidth() / 2, canvas.getHeight() / 2);

            if(!mProgressSentence.equals("")) {
                int textWidth = (int) mCompleteSentencePaint.measureText(mProgressSentence);
                canvas.drawText(mProgressSentence, rectF.centerX() - textWidth / 2, canvas.getHeight()*4/5, mCompleteSentencePaint);
            }

            if(mPanoStatus == PANO_STATUS.COMPLETING) {
                int textWidth = (int) mCompleteSentencePaint.measureText(mCompleteSentence);
                canvas.drawText(mCompleteSentence, rectF.centerX() - textWidth / 2, rectF.centerY(), mCompleteSentencePaint);
            } else {
                //Draw Aiming rectangle at the center
                canvas.drawRect(rectF, mCenterRectPaint);

                //Draw the guide frames
                if(mGuidePicture != null) {
                    canvas.save();
                    mGuidePicture.drawGuideInAir(canvas);
                    canvas.restore();
                }

                //Blended pano preview
                synchronized (mPreviewBitmapLock) {
                    if (mPreviewPicture != null) {
                        mPreviewPicture.drawMasterPanoPreview(canvas);
                    }
                }
            }
        } else {
            canvas.rotate(-mPendingOrientation, canvas.getWidth()/2, canvas.getHeight()/2);
            int textWidth = (int) mCompleteSentencePaint.measureText(mIntroSentence);
            canvas.drawText(mIntroSentence, rectF.centerX() - textWidth / 2, canvas.getHeight()*4/5, mCompleteSentencePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e){
        return true;
    }

    private void bitmapToDataNV21(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int y = 0;
        int u = w*h;
        int a, R, G, B, Y, U, V;
        int index = 0;

        bitmap.getPixels(mAargbBuffer, 0, w, 0, 0, w, h);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {

                a = (mAargbBuffer[index] & 0xff000000) >> 24;
                R = (mAargbBuffer[index] & 0xff0000) >> 16;
                G = (mAargbBuffer[index] & 0xff00) >> 8;
                B = (mAargbBuffer[index] & 0xff) >> 0;

                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                mDataBuffer[y++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    mDataBuffer[u++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    mDataBuffer[u++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }
                index ++;
            }
        }
    }

    class PanoQueueProcessor extends Thread {
        private ArrayBlockingQueue<BitmapTask> queue;
        private Object lock = new Object();
        public PanoQueueProcessor() {
            queue = new ArrayBlockingQueue<BitmapTask>(MAX_PANO_FRAME);
        }

        private void waitTillNotFull() {
            while(true) {
                if (queue.size() < MAX_PANO_FRAME) {
                    return;
                }
            }
        }

        @Override
        public void run() {
            while(true) {
                try {
                    BitmapTask bt = queue.take();
                    if(mShouldFinish)
                        continue;
                    synchronized (lock) {
                        doTask(bt);
                    }
                } catch (InterruptedException e) {
                    //Ignore
                }
            }
        }

        public boolean isEmpty() {
            synchronized (lock) {
                if(!queue.isEmpty()) {
                    return false;
                }
                return true;
            }
        }

        public void queueClear() {
            this.interrupt();
            queue.clear();
        }

        //This function is the only one running on UI thread.
        public void addTask(Bitmap bitmap, int x, int y, int dir) {
            waitTillNotFull();
            BitmapTask bt = new BitmapTask(bitmap, x, y, dir);
            queue.add(bt);
        }

        private void doTask(BitmapTask bitmapTask) {
            int rtv = -1;
            synchronized (mBitmapStreamLock) {
                if(mBitmapStream == null) {
                    mBitmapStream = new BitmapArrayOutputStream(1024*1204);
                }
                mBitmapStream.reset();
                bitmapTask.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, mBitmapStream);
                rtv = callNativeProcessKeyFrame(mBitmapStream.toByteArray(), mBitmapStream.size(),
                        bitmapTask.x, bitmapTask.y, 0, bitmapTask.dir);
            }
            if(rtv < 0) {
                mShouldFinish = true;
                stopPano(false, mActivity.getResources().getString(R.string.panocapture_direction_is_changed));
                Log.w(TAG, "Keyframe return value: "+rtv);
            }
            bitmapTask.clear();
        }
    }

    class BitmapArrayOutputStream extends ByteArrayOutputStream {

        public BitmapArrayOutputStream(int size) {
            super(size);
        }

        @Override
        public synchronized byte[] toByteArray() {
            return buf;
        }

        @Override
        public void close() throws IOException{
            super.close();
            buf = null;
        }
    }

    class BitmapTask {
        Bitmap bitmap;
        int x;
        int y;
        int dir;
        public BitmapTask(Bitmap orgBitmap, int x, int y, int dir) {
            Bitmap newBitmap;
            if (mOrientation == 0 || mOrientation == 180) {
                newBitmap = Bitmap.createBitmap(mFinalPictureWidth, mFinalPictureHeight, Bitmap.Config.ARGB_8888);
            } else {//if(mOrientation == 90 || mOrientation == 270)
                newBitmap = Bitmap.createBitmap(mFinalPictureHeight, mFinalPictureWidth, Bitmap.Config.ARGB_8888);
            }
            rotateAndScale(orgBitmap, newBitmap, mFinalPictureRatioToCamera);
            this.bitmap = newBitmap;
            this.x = x;
            this.y = y;
            this.dir = dir;
        }
        public void clear() {
            this.bitmap.recycle();
        }
    }

    private void waitForQueueDone() {
        while(true) {
            if(mQueueProcessor.isEmpty()) return;
            try {
                Thread.sleep(10);
            } catch(InterruptedException e) {
                //Ignore
            }
        }
    }

    private void processPreviewFrame(boolean[] isKey, int[] framePos, int[] moveSpeed) {
        if (callNativeProcessPreviewFrame(mDataBuffer, isKey, framePos, moveSpeed) < 0) {
            Log.e(TAG, "Preview processing is failed.");
        }
    }

    public boolean isPanoCompleting() {
        return (mPanoStatus == PANO_STATUS.COMPLETING);
    }

    public boolean isFrameProcessing() {
        return mIsFrameProcessing;
    }

    /*
     *  bitmap will be kept to use further.
     */
    public void onFrameAvailable(final Bitmap bitmap, final boolean isCancelling) {
        if(mPanoStatus == PANO_STATUS.COMPLETING || mPanoStatus == PANO_STATUS.OPENING) {
            return;
        }
        if(bitmap == null) {
            if(isCancelling) {
                mCompleteSentence = "Cancelling...";
            } else {
                mCompleteSentence = "Processing...";
            }
            mPanoStatus = PANO_STATUS.COMPLETING;
            invalidate();
            mHandler.post(new Runnable() {
                public void run() {
                    if(mPreviewPicture != null) {
                        waitForQueueDone();
                        if(!isCancelling) {
                            int size = callNativeGetResultSize();
                            if (size <= 0) {
                                callNativeCancelPanorama();
                            } else {
                                byte[] jpegData = new byte[size];
                                callNativeCompletePanorama(jpegData, size);
                                int orient = 270;
                                if(mDir == DIRECTION_UPDOWN) {
                                    orient = 0;
                                }
                                final Uri uri = mController.savePanorama(jpegData, mFinalPictureWidth*8, mFinalPictureHeight, orient);
                                Bitmap bm = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                                final Bitmap thumbBitmap = CameraUtil.rotate(bm, orient);
                                if(uri != null) {
                                    mActivity.runOnUiThread(new Runnable() {
                                        public void run() {
                                            mActivity.updateThumbnail(thumbBitmap);
                                            mActivity.notifyNewMedia(uri);
                                        }
                                    });
                                } else {
                                    Log.d(TAG, "Image uri is null, size : "+size+" jpegData: "+jpegData);
                                }
                            }
                        } else {
                            callNativeCancelPanorama();
                            mQueueProcessor.queueClear();
                        }
                    } else {
                        callNativeCancelPanorama();
                    }
                    synchronized (mPreviewBitmapLock) {
                        if(mPreviewPicture != null) {
                            mPreviewPicture.bitmap.recycle();
                            mPreviewPicture.bitmapIn.recycle();
                        }
                        mPreviewPicture = null;
                        mGuidePicture = null;
                    }
                    callNativeInstanceRelease();
                    mPanoStatus = PANO_STATUS.INACTIVE;
                    mShouldFinish = false;
                    mOrientation = mPendingOrientation;
                }
            });
            return;
        }
        if(mPanoStatus == PANO_STATUS.INACTIVE) {
            mPanoStatus = PANO_STATUS.OPENING;
            mHandler.post(new Runnable() {
                public void run() {
                    int width;
                    int height;
                    if (mOrientation == 0 || mOrientation == 180) {
                        width = mPreviewThumbWidth;
                        height = mPreviewThumbHeight;
                    } else {
                        width = mPreviewThumbHeight;
                        height = mPreviewThumbWidth;
                    }
                    if(callNativeInstanceInit(width, height, width, 0, 1) < 0) {
                        Log.e(TAG,"Failed to create panorama native instance");
                        mPanoStatus = PANO_STATUS.INACTIVE;
                        mOrientation = mPendingOrientation;
                        return;
                    }
                    mPanoStatus = PANO_STATUS.ACTIVE_UNKNOWN;
                }
            });
            return;
        }

        if(mPanoStatus != PANO_STATUS.INACTIVE)
        {
            if(mIsFrameProcessing) {
                return;
            }
            mIsFrameProcessing = true;
            mHandler.post(new Runnable() {
                public void run() {
                    Picture picture;
                    if (mTempBitmap == null || mTempOrietnation != mOrientation) {
                        if (mOrientation == 0 || mOrientation == 180) {
                            mTempBitmap = Bitmap.createBitmap(mPreviewThumbWidth, mPreviewThumbHeight, Bitmap.Config.ARGB_8888);
                        } else {//if(mOrientation == 90 || mOrientation == 270)
                            mTempBitmap = Bitmap.createBitmap(mPreviewThumbHeight, mPreviewThumbWidth, Bitmap.Config.ARGB_8888);
                        }
                        mTempOrietnation = mOrientation;
                    }
                    rotateAndScale(bitmap, mTempBitmap, mPanoPreviewRatioToCamera);
                    bitmapToDataNV21(mTempBitmap);
                    boolean[] isKey = new boolean[1];
                    int[] framePos = new int[3];
                    int[] moveSpeed = new int[1];
                    processPreviewFrame(isKey, framePos, moveSpeed);
                    if (framePos[2] == DIRECTION_GOT_LOST) {
                        mProgressSentence = mActivity.getResources().getString(R.string.panocapture_direction_is_not_determined);
                    } else {
                        mProgressSentence = "";
                        mDir = framePos[2];
                    }

                    if (isKey[0]) {
                        mQueueProcessor.addTask(bitmap, framePos[0], framePos[1], framePos[2]);
                    }
                    picture = new Picture(mTempBitmap, mCurrDegX, mCurrDegY, framePos[0], framePos[1]);
                    if (mPanoStatus == PANO_STATUS.ACTIVE_UNKNOWN) {
                        if (framePos[0] < -DECISION_MARGIN) {
                            mPanoStatus = PANO_STATUS.ACTIVE_RIGHT;
                        } else if (framePos[0] > DECISION_MARGIN) {
                            mPanoStatus = PANO_STATUS.ACTIVE_LEFT;
                        } else if (framePos[1] < -DECISION_MARGIN) {
                            mPanoStatus = PANO_STATUS.ACTIVE_DOWN;
                        } else if (framePos[1] > DECISION_MARGIN) {
                            mPanoStatus = PANO_STATUS.ACTIVE_UP;
                        }
                    }
                    if (mPreviewPicture == null && mPanoStatus != PANO_STATUS.ACTIVE_UNKNOWN) {
                        Picture masterPicture;
                        Bitmap masterBitmap;
                        Bitmap liveBitmap;
                        if (mPanoStatus == PANO_STATUS.ACTIVE_RIGHT || mPanoStatus == PANO_STATUS.ACTIVE_LEFT) {
                            if (mOrientation == 0 || mOrientation == 180) {
                                mFinalDoneLength = mPreviewThumbWidth * MAX_PANO_FRAME;
                                masterBitmap = Bitmap.createBitmap(mFinalDoneLength, mPreviewThumbHeight, Bitmap.Config.ARGB_8888);
                                liveBitmap = Bitmap.createBitmap(mPreviewThumbWidth, mPreviewThumbHeight, Bitmap.Config.ARGB_8888);
                            } else {//if(mOrientation == 90 || mOrientation == 270)
                                mFinalDoneLength = mPreviewThumbHeight * MAX_PANO_FRAME;
                                masterBitmap = Bitmap.createBitmap(mFinalDoneLength, mPreviewThumbWidth, Bitmap.Config.ARGB_8888);
                                liveBitmap = Bitmap.createBitmap(mPreviewThumbHeight, mPreviewThumbWidth, Bitmap.Config.ARGB_8888);
                            }
                        } else { //UP or DOWN
                            if (mOrientation == 0 || mOrientation == 180) {
                                mFinalDoneLength = mPreviewThumbHeight * MAX_PANO_FRAME;
                                masterBitmap = Bitmap.createBitmap(mPreviewThumbWidth, mFinalDoneLength, Bitmap.Config.ARGB_8888);
                                liveBitmap = Bitmap.createBitmap(mPreviewThumbWidth, mPreviewThumbHeight, Bitmap.Config.ARGB_8888);
                            } else {//if(mOrientation == 90 || mOrientation == 270)
                                mFinalDoneLength = mPreviewThumbWidth * MAX_PANO_FRAME;
                                masterBitmap = Bitmap.createBitmap(mPreviewThumbHeight, mFinalDoneLength, Bitmap.Config.ARGB_8888);
                                liveBitmap = Bitmap.createBitmap(mPreviewThumbHeight, mPreviewThumbWidth, Bitmap.Config.ARGB_8888);
                            }
                        }
                        mGuidePicture = new Picture(null, mCurrDegX, mCurrDegY, 0, 0, liveBitmap.getWidth(), liveBitmap.getHeight());
                        masterPicture = new Picture(masterBitmap, mCurrDegX, mCurrDegY, 0, 0, 0, 0);
                        synchronized (mPreviewBitmapLock) {
                            mPreviewPicture = masterPicture;
                            mPreviewPicture.bitmapIn = liveBitmap;
                        }
                        mIsFirstBlend = true;
                    }
                    if (mPreviewPicture != null) {
                        blendToPreviewPicture(picture, isKey[0], mIsFirstBlend);
                        if (isAllTaken()) {
                            stopPano(false, null);
                        }
                        mIsFirstBlend = false;
                    }
                    mIsFrameProcessing = false;
                }
            });
        }
    }

    private void stopPano(final boolean isCancelling, final String message) {
        if(message != null) {
            mProgressSentence = message;
            Log.w(TAG, message);
        }
        mHandler.post(new Runnable() {
            public void run() {
                mController.changePanoStatus(false, isCancelling);
            }
        });
    }

    private boolean isAllTaken() {
        if(mFinalDoneLength == 0) {
            return false;
        }
        if(mPanoStatus == PANO_STATUS.ACTIVE_LEFT || mPanoStatus == PANO_STATUS.ACTIVE_RIGHT) {
            if(mPreviewPicture.width >= mFinalDoneLength) {
                return true;
            }
        } else if(mPanoStatus == PANO_STATUS.ACTIVE_UP || mPanoStatus == PANO_STATUS.ACTIVE_DOWN) {
            if(mPreviewPicture.height >= mFinalDoneLength) {
                return true;
            }
        }
        return false;
    }

    private void blendToPreviewPicture(Picture pic2, boolean isKey, boolean isFirst) {
        Canvas canvas;
        canvas = new Canvas(mPreviewPicture.bitmapIn);
        canvas.drawBitmap(pic2.bitmap, 0, 0, null);
        Picture pic1 = mPreviewPicture;
        if(mPanoStatus == PANO_STATUS.ACTIVE_RIGHT || mPanoStatus == PANO_STATUS.ACTIVE_LEFT) {
            int gap = pic2.xPos - pic1.xPos;
            pic1.topIn = -pic2.yPos;
            if((gap > 0 && mPanoStatus == PANO_STATUS.ACTIVE_RIGHT) ||
                    gap < 0 && mPanoStatus == PANO_STATUS.ACTIVE_LEFT) {
                return;
            }
            gap = pic2.width - Math.abs(gap);
            if(isFirst) {
                gap = 0;
            }
            int newWidth = pic1.width + pic2.width - gap;

            if(mPanoStatus == PANO_STATUS.ACTIVE_RIGHT) {
                pic1.leftIn = newWidth - pic2.width;
            } else {
                pic1.leftIn = pic1.bitmap.getWidth() - newWidth;
            }
            if(pic1.leftIn < 0) {
                pic1.leftIn = 0;
            }
            if(pic1.leftIn > pic1.bitmap.getWidth() - pic2.bitmap.getWidth()) {
                pic1.leftIn = pic1.bitmap.getWidth() - pic2.bitmap.getWidth();
            }

            if(isKey || isFirst) {
                canvas = new Canvas(pic1.bitmap);
                canvas.drawBitmap(pic2.bitmap, pic1.leftIn, 0, null);
                int overlapS, overlapE;
                if (mPanoStatus == PANO_STATUS.ACTIVE_RIGHT) {
                    overlapS = newWidth - pic2.width;
                    overlapE = newWidth - pic2.width + gap;
                } else {
                    overlapS = pic1.bitmap.getWidth() - newWidth + pic2.width - gap;
                    overlapE = pic1.bitmap.getWidth() - newWidth + pic2.width;
                }
                for (int i = overlapS; i < overlapE; i++) {
                    if (i >= pic1.bitmap.getWidth() || i - overlapS >= pic2.bitmap.getWidth())
                        break;
                    for (int j = 0; j < pic1.height; j++) {
                        if (j >= pic1.bitmap.getHeight() || j >= pic2.bitmap.getHeight())
                            break;
                        int iC1 = pic1.bitmap.getPixel(i, j);
                        int iC2 = pic2.bitmap.getPixel(i - overlapS, j);
                        int blendAlpha = (overlapE - i) / gap;
                        int or = blendAlpha * Color.red(iC1) + (1 - blendAlpha) * Color.red(iC2);
                        int og = blendAlpha * Color.green(iC1) + (1 - blendAlpha) * Color.green(iC2);
                        int ob = blendAlpha * Color.blue(iC1) + (1 - blendAlpha) * Color.blue(iC2);
                        int pixel = Color.argb(255, or, og, ob);
                        pic1.bitmap.setPixel(i, j, pixel);
                    }
                }
            }
            pic1.width = newWidth;
        } else { //UP or DOWN
            int gap = pic2.yPos - pic1.yPos;
            pic1.leftIn = -pic2.xPos;
            if((gap > 0 && mPanoStatus == PANO_STATUS.ACTIVE_DOWN) ||
                    gap < 0 && mPanoStatus == PANO_STATUS.ACTIVE_UP) {
                return;
            }
            gap = pic2.height - Math.abs(gap);
            if(isFirst) {
                gap = 0;
            }
            int newHeight = pic1.height + pic2.height - gap;

            if(mPanoStatus == PANO_STATUS.ACTIVE_DOWN) {
                pic1.topIn = newHeight - pic2.height;
            } else {
                pic1.topIn = pic1.bitmap.getHeight() - newHeight;
            }
            if(pic1.topIn < 0) {
                pic1.topIn = 0;
            }
            if(pic1.topIn > pic1.bitmap.getHeight() - pic2.bitmap.getHeight()) {
                pic1.topIn = pic1.bitmap.getHeight() - pic2.bitmap.getHeight();
            }
            if(isKey || isFirst) {
                canvas = new Canvas(pic1.bitmap);
                canvas.drawBitmap(pic2.bitmap, 0, pic1.topIn, null);
                int overlapS, overlapE;
                if (mPanoStatus == PANO_STATUS.ACTIVE_DOWN) {
                    overlapS = newHeight - pic2.height;
                    overlapE = newHeight - pic2.height + gap;
                } else {
                    overlapS = pic1.bitmap.getHeight() - newHeight + pic2.height - gap;
                    overlapE = pic1.bitmap.getHeight() - newHeight + pic2.height;
                }
                for (int i = overlapS; i < overlapE; i++) {
                    if (i >= pic1.bitmap.getHeight() || i - overlapS >= pic2.bitmap.getHeight())
                        break;
                    for (int j = 0; j < pic1.width; j++) {
                        if (j >= pic1.bitmap.getWidth() || j >= pic2.bitmap.getWidth())
                            break;
                        int iC1 = pic1.bitmap.getPixel(j, i);
                        int iC2 = pic2.bitmap.getPixel(j, i - overlapS);
                        int blendAlpha = (overlapE - i) / gap;
                        int or = blendAlpha * Color.red(iC1) + (1 - blendAlpha) * Color.red(iC2);
                        int og = blendAlpha * Color.green(iC1) + (1 - blendAlpha) * Color.green(iC2);
                        int ob = blendAlpha * Color.blue(iC1) + (1 - blendAlpha) * Color.blue(iC2);
                        int pixel = Color.argb(255, or, og, ob);
                        pic1.bitmap.setPixel(j, i, pixel);
                    }
                }
            }
            pic1.height = newHeight;
        }
        pic1.xPos = pic2.xPos;
        pic1.yPos = pic2.yPos;
    }

    private void rotateAndScale(Bitmap srcBitmap, Bitmap dstBitmap, float ratio) {
        Canvas canvas = new Canvas(dstBitmap);
        matrix.reset();
        int sensorOrientation = mController.getCameraSensorOrientation();
        if(mOrientation == 0 || mOrientation == 270) {
            matrix.postRotate((sensorOrientation + mOrientation + 360) % 360, srcBitmap.getHeight() / 2, srcBitmap.getHeight() / 2);
        } else  if (mOrientation == 180){
            matrix.postRotate((sensorOrientation + mOrientation + 180 + 360) % 360, srcBitmap.getHeight() / 2, srcBitmap.getHeight() / 2);
            matrix.postRotate(180, srcBitmap.getHeight() / 2, srcBitmap.getWidth() / 2);
        } else if(mOrientation == 90) {
            matrix.postRotate((sensorOrientation + mOrientation + 180 + 360) % 360, srcBitmap.getHeight() / 2, srcBitmap.getHeight() / 2);
            matrix.postRotate(180, srcBitmap.getWidth() / 2, srcBitmap.getHeight() / 2);
        }
        matrix.postScale(ratio, ratio);
        canvas.drawBitmap(srcBitmap, matrix, null);
    }

    public void setOrientation(int orientation) {
        if(mPanoStatus != PANO_STATUS.INACTIVE) {
            mPendingOrientation = orientation;
            return;
        }
        mOrientation = mPendingOrientation = orientation;
    }

    private boolean isPortrait() {
        if(mOrientation == 0 || mOrientation == 180) {
            return true;
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                System.arraycopy(event.values, 0, mOldRots, 0, event.values.length);
                SensorManager.getRotationMatrixFromVector(mR, mOldRots);
                if(isPortrait()) {
                    SensorManager.remapCoordinateSystem(mR,
                           SensorManager.AXIS_X, SensorManager.AXIS_Z,
                            mRR);
                } else {
                    SensorManager.remapCoordinateSystem(mR,
                            SensorManager.AXIS_Z, SensorManager.AXIS_X,
                            mRR);
                }
                SensorManager.getOrientation(mRR, mOrients);
                mCurrDegX = (((float) Math.toDegrees(mOrients[0]) + 360) % 360);
                mCurrDegY = (((float) Math.toDegrees(mOrients[1]) + 360) % 360);
                if(!isPortrait()) {
                    mCurrDegX = (mCurrDegX + 180) % 360;
                    mCurrDegY = (-mCurrDegY + 360) % 360;
                }
                invalidate();
                break;
            default:
                return;
        }
    }

    private void lowPassFilteredCopy( float[] a, float[] b) {
        for ( int i=0; i < 3; i++ ) {
            b[i] = b[i] + 0.45f * (a[i] - b[i]);
        }
    }

    private void highPassFilteredCopy( float[] a, float[] b, float[] c, boolean isValid ) {
        if(!isValid) {
            System.arraycopy(a, 0, b, 0, a.length);
            return;
        }

        for ( int i=0; i < a.length; i++ ) {
            b[i] = 1.2f*(b[i] + a[i] - c[i]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private int callNativeInstanceInit(int width, int height, int stride, int orientation, int colorFormat) {
        if(DEBUG) {
            Log.d(TAG, "native instance init");
        }
        int rtv = nativeInstanceInit(width, height, stride, orientation, colorFormat);
        if(DEBUG) {
            Log.d(TAG, "native instance init done");
        }
        return rtv;
    }

    private int callNativeInstanceRelease() {
        if(DEBUG) {
            Log.d(TAG, "native instance release");
        }
        int rtv = nativeInstanceRelease();
        if(DEBUG) {
            Log.d(TAG, "native instance release done");
        }
        return rtv;
    }

    private int callNativeProcessPreviewFrame(byte[] frameData ,boolean[] isKey , int[] framePosition , int[] moveSpeed) {
        if(DEBUG) {
            Log.d(TAG, "native process preview frame");
        }
        int rtv = nativeProcessPreviewFrame(frameData, isKey, framePosition, moveSpeed);
        if(DEBUG) {
            Log.d(TAG, "native process preview frame done");
        }
        return rtv;
    }

    private int callNativeProcessKeyFrame(byte[] jpegInData, int dataSize, int x, int y, int orientation, int direction) {
        if(DEBUG) {
            Log.d(TAG, "native process key frame");
        }
        int rtv = nativeProcessKeyFrame(jpegInData, dataSize, x, y, orientation, direction);
        if(DEBUG) {
            Log.d(TAG, "native process key frame done");
        }
        return rtv;
    }

    private int callNativeCancelPanorama() {
        if(DEBUG) {
            Log.d(TAG, "native cancel panorama");
        }
        int rtv = nativeCancelPanorama();
        if(DEBUG) {
            Log.d(TAG, "native cancel panorama done");
        }
        return rtv;
    }

    private int callNativeGetResultSize() {
        if(DEBUG) {
            Log.d(TAG, "native getResultSize");
        }
        int rtv = nativeGetResultSize();
        if(DEBUG) {
            Log.d(TAG, "native getResultSize done");
        }
        return rtv;
    }

    private int callNativeCompletePanorama(byte[] jpegOutData, int size) {
        if(DEBUG) {
            Log.d(TAG, "native complete panorama");
        }
        int rtv = nativeCompletePanorama(jpegOutData, size);
        if(DEBUG) {
            Log.d(TAG, "native complete panorama done");
        }
        return rtv;
    }

    public static boolean isSupportedStatic() {
        return mIsSupported;
    }

    private native int nativeInstanceInit(int width, int height, int stride, int orientation, int colorFormat);
    private native int nativeInstanceRelease();
    private native int nativeProcessPreviewFrame(byte[] frameData ,boolean[] isKey , int[] framePosition , int[] moveSpeed);
    private native int nativeProcessKeyFrame(byte[] jpegInData, int dataSize, int x, int y, int orientation, int direction);
    private native int nativeCancelPanorama();
    private native int nativeGetResultSize();
    private native int nativeCompletePanorama(byte[] jpegOutData, int size);

    static {
        try {
            mIsSupported = true;
            System.loadLibrary("jni_panorama");
        } catch(UnsatisfiedLinkError e) {
            Log.e(TAG, e.toString());
            mIsSupported = false;
        }
    }
}
