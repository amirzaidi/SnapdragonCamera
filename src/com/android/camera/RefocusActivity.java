/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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
package com.android.camera;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.animation.Animator;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.FrameLayout;

import com.android.camera.exif.ExifInterface;

import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

public class RefocusActivity extends Activity {
    private static final String TAG = "RefocusActivity";
    private static final String[] NAMES = {
        "00", "01", "02", "03", "04", "AllFocusImage"
    };

    private Uri mUri;

    private ImageView mImageView;
    private int mWidth;
    private int mHeight;
    private Indicator mIndicator;
    private boolean mSecureCamera;
    private View mAllInFocusView;

    private DepthMap mDepthMap;
    private int mCurrentImage = -1;
    private int mRequestedImage = -1;
    private LoadImageTask mLoadImageTask;
    private boolean mMapRotated = false;
    private int mOrientation = 0;
    public static final int MAP_ROTATED = 1;
    private String mFilesPath;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = getIntent();
        mUri = intent.getData();
        String action = intent.getAction();
        if (CameraUtil.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)
                || CameraUtil.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mSecureCamera = true;
        } else {
            mSecureCamera = intent.getBooleanExtra(CameraUtil.SECURE_CAMERA_EXTRA, false);
        }

        if (mSecureCamera) {
            // Change the window flags so that secure camera can show when locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(params);
        }
        mFilesPath = getFilesDir()+"";
        if(intent.getFlags() == MAP_ROTATED || mSecureCamera) {
            mMapRotated = true;
            mFilesPath = getFilesDir()+"/Ubifocus";
        }

        new Thread(new Runnable() {
            public void run() {
                mDepthMap = new DepthMap(mFilesPath + "/DepthMapImage.y");
            }
        }).start();


        setContentView(R.layout.refocus_editor);
        mIndicator = (Indicator) findViewById(R.id.refocus_indicator);

        mImageView = (ImageView) findViewById(R.id.refocus_image);
        mImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        float x = event.getX();
                        float y = event.getY();
                        int w = v.getWidth();
                        int h = v.getHeight();
                        mIndicator.startAnimation(x + mImageView.getLeft(),
                                y + mImageView.getTop());
                        if (mDepthMap != null) {
                            int depth = mDepthMap.getDepth(x / (float) w, y / (float) h);
                            setCurrentImage(depth);
                            mAllInFocusView.setBackground(getDrawable(
                                    R.drawable.refocus_button_disable));
                        }
                        break;
                }
                return true;
            }
        });

        mAllInFocusView = findViewById(R.id.refocus_all);
        mAllInFocusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                allInFocus();
            }
        });

        findViewById(R.id.refocus_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setResult(RESULT_CANCELED, new Intent());
                finish();
            }
        });

        findViewById(R.id.refocus_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mRequestedImage != NAMES.length - 1) {
                    new SaveImageTask().execute(mFilesPath + "/" + NAMES[mRequestedImage]
                            + ".jpg");
                } else {
                    finish();
                }
                setResult(RESULT_OK, new Intent());
            }
        });

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mWidth = size.x;
        mHeight = size.y;

        allInFocus();
    }

    private void setCurrentImage(int depth) {
        if (depth >= 0 && depth < NAMES.length && depth != mRequestedImage) {
            mRequestedImage = depth;
            if (mLoadImageTask != null) {
                mLoadImageTask.cancel(true);
            }
            if (depth != mCurrentImage) {
                mCurrentImage = depth;
                mLoadImageTask = new LoadImageTask();
                mLoadImageTask.execute(mFilesPath + "/" + NAMES[depth] + ".jpg");
            }
        }
    }

    private void allInFocus() {
        setCurrentImage(NAMES.length - 1);
        mAllInFocusView.setBackground(getDrawable(R.drawable.refocus_button_enable));
    }

    private class SaveImageTask extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... path) {
            try {
                OutputStream out = getContentResolver().openOutputStream(mUri);
                FileInputStream in = new FileInputStream(path[0]);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } catch (Exception e) {
            }
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mUri);
            sendBroadcast(intent);
            return null;
        }

        protected void onPostExecute(Void v) {
            finish();
        }
    }

    private class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        protected Bitmap doInBackground(String... path) {
            final BitmapFactory.Options o = new BitmapFactory.Options();
            int height;
            int width;
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path[0], o);
            ExifInterface exif = new ExifInterface();
            mOrientation = 0;
            try {
                exif.readExif(path[0]);
                mOrientation = Exif.getOrientation(exif);
            } catch (IOException e) {
            }
            int h = o.outHeight;
            int w = o.outWidth;
            int screenOrientation = RefocusActivity.this.getResources().getConfiguration()
                    .orientation;
            if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
                height = mWidth;
                width = mHeight;
            } else {
                height = mHeight;
                width = mWidth;
            }
            int sample = 1;
            if (h > height || w > width) {
                while (h / sample / 2 > height && w / sample / 2 > width) {
                    sample *= 2;
                }
            }
            Log.d(TAG, "sample =  " + sample);
            Log.d(TAG, "h = " + h + "  height = " + height);
            Log.d(TAG, "w = " + w + "  width = " + width);
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(path[0], o);
            if (mOrientation != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate(mOrientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            }
            return bitmap;
        }

        protected void onPostExecute(Bitmap result) {
            mImageView.setImageBitmap(result);
        }
    }

    private class DepthMap {
        private byte[] mData;
        private int mWidth;
        private int mHeight;
        private boolean mFail = true;
        private static final int W_SIZE = 61;

        public DepthMap(final String path) {
            File file = new File(path);
            try {
                FileInputStream stream = new FileInputStream(file);
                mData = new byte[(int) file.length()];
                stream.read(mData);
                stream.close();
            } catch (Exception e) {
                mData = new byte[0];
            }

            int length = (int) mData.length;
            if (length > 25) {
                mFail = (mData[length - 25] != 0);
                mWidth = readInteger(length - 24);
                mHeight = readInteger(length - 20);
            }
            if (mWidth * mHeight + 25 > length) {
                mFail = true;
            }
        }

        public int getDepth(float x, float y) {
            if (mFail || x > 1.0f || y > 1.0f) {
                return NAMES.length - 1;
            }

            int newX = (int) (x * mWidth);
            int newY = (int) (y * mHeight);
            if(mMapRotated) {
                if(mOrientation == 0) {
                    newX = (int) (x * mWidth);
                    newY = (int) (y * mHeight);
                } if(mOrientation == 90) {
                    newX = (int) ((y) * mWidth);
                    newY = (int) ((1 - x) * mHeight);
                } else if (mOrientation == 180) {
                    newX = (int) ((1-x) * mWidth);
                    newY = (int) ((1-y) * mHeight);
                } else if (mOrientation == 270) {
                    newX = (int) ((1-y) * mWidth);
                    newY = (int) ((x) * mHeight);
                }
            }

            int[] hist = new int[256];
            for (int i = 0; i < 256; i++) {
                hist[i] = 0;
            }

            int colStart = Math.max(newX - W_SIZE / 2, 0);
            int colEnd = Math.min(colStart + W_SIZE, mWidth);
            int rowStart = Math.max(newY - W_SIZE / 2, 0);
            int rowEnd = Math.min(rowStart + W_SIZE, mHeight);

            for (int col = colStart; col < colEnd; col++) {
                for (int row = rowStart; row < rowEnd; row++) {
                    int level = mData[row * mWidth + col];
                    hist[level]++;
                }
            }

            int depth = NAMES.length - 1;
            int maxCount = 0;
            for (int i = 0; i < 256; i++) {
                int count = hist[i];
                if (count != 0 && (maxCount == 0 || count > maxCount)) {
                    maxCount = count;
                    depth = i;
                }
            }
            return depth;
        }

        private int readInteger(int offset) {
            int result = mData[offset] & 0xff;
            for (int i = 1; i < 4; ++i) {
                result <<= 8;
                result += mData[offset + i] & 0xff;
            }
            return result;
        }
    }

    public static class Indicator extends FrameLayout {
        private float mX;
        private float mY;
        private ValueAnimator mAnimator;
        private Paint mPaint;
        private float mCrossLength;
        private float mStrokeWidth;
        private int mColor1;
        private int mColor2;

        public Indicator(Context context, AttributeSet attr) {
            super(context, attr);
            final Resources res = context.getResources();

            float r1 = res.getDimensionPixelSize(R.dimen.refocus_circle_diameter_1) / 2;
            float r2 = res.getDimensionPixelSize(R.dimen.refocus_circle_diameter_2) / 2;
            float r3 = res.getDimensionPixelSize(R.dimen.refocus_circle_diameter_3) / 2;
            mCrossLength = res.getDimensionPixelSize(R.dimen.refocus_cross_length) / 2;
            mStrokeWidth = res.getDimensionPixelSize(R.dimen.refocus_stroke_width) / 2;
            mColor1 = res.getColor(R.color.refocus_indicator_1);
            mColor2 = res.getColor(R.color.refocus_indicator_2);

            Keyframe k0 = Keyframe.ofFloat(0f, r1);
            Keyframe k1 = Keyframe.ofFloat(5f / 12f, r2);
            Keyframe k2 = Keyframe.ofFloat(0.5f, r2);
            Keyframe k3 = Keyframe.ofFloat(0.75f, r3);
            Keyframe k4 = Keyframe.ofFloat(1f, r2);
            PropertyValuesHolder holder = PropertyValuesHolder.ofKeyframe(
                    "radius", k0, k1, k2, k3, k4);
            mAnimator = ValueAnimator.ofPropertyValuesHolder(holder);

            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setStrokeCap(Paint.Cap.BUTT);
            mPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.refocus_stroke_width));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mAnimator != null && mAnimator.isStarted()) {
                mPaint.setColor(mAnimator.getAnimatedFraction() < 0.5f ? mColor1 : mColor2);

                mPaint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(mX, mY, (Float) mAnimator.getAnimatedValue(), mPaint);
                canvas.drawLine(mX - mCrossLength, mY, mX + mCrossLength, mY, mPaint);
                canvas.drawLine(mX, mY - mCrossLength, mX, mY + mCrossLength, mPaint);
            }
        }

        public void startAnimation(float x, float y) {
            mX = x;
            mY = y;

            if (mAnimator != null) {
                mAnimator.cancel();
            }

            mAnimator.setDuration(720);
            mAnimator.removeAllUpdateListeners();
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    invalidate();
                }
            });
            mAnimator.removeAllListeners();
            mAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setWillNotDraw(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setWillNotDraw(true);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    setWillNotDraw(true);
                }
            });
            mAnimator.start();
        }
    }
}
