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
import java.io.OutputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Build;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

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

    private DepthMap mDepthMap;
    private int mCurrentImage = -1;
    private int mRequestedImage = -1;
    private LoadImageTask mLoadImageTask;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        new Thread(new Runnable() {
            public void run() {
                mDepthMap = new DepthMap(getFilesDir() + "/DepthMapImage.y");
            }
        }).start();

        mUri = getIntent().getData();
        setResult(RESULT_CANCELED, new Intent());

        setContentView(R.layout.refocus_editor);
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
                        if (mDepthMap != null) {
                            int depth = mDepthMap.getDepth(x / (float) w, y / (float) h);
                            setCurrentImage(depth);
                        }
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.refocus_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                allInFocus();
            }
        });

        findViewById(R.id.refocus_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
            }
        });

        findViewById(R.id.refocus_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mRequestedImage != NAMES.length - 1) {
                    new SaveImageTask().execute(getFilesDir() + "/" + NAMES[mRequestedImage]
                            + ".jpg");
                } else {
                    finish();
                }
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
                mLoadImageTask.execute(getFilesDir() + "/" + NAMES[depth] + ".jpg");
            }
        }
    }

    private void allInFocus() {
        setCurrentImage(NAMES.length - 1);
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
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path[0], o);

            int h = o.outHeight;
            int w = o.outWidth;
            int sample = 1;

            if (h > mHeight || w > mWidth) {
                while (h / sample / 2 > mHeight && w / sample / 2 > mWidth) {
                    sample *= 2;
                }
            }

            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(path[0], o);
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

        public DepthMap(final String path) {
            File file = new File(path);
            try {
                FileInputStream stream = new FileInputStream(file);
                mData = new byte[(int) file.length()];
                stream.read(mData);
                stream.close();
            } catch (Exception e) {
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
            } else {
                return mData[(int) ((y * mHeight + x) * mWidth)];
            }
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
}
