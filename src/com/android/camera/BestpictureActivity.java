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
package com.android.camera;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.android.camera.exif.ExifInterface;
import com.android.camera.ui.DotsView;
import com.android.camera.ui.DotsViewItem;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class BestpictureActivity extends FragmentActivity{
    private static final String TAG = "BestpictureActivity";
    public static final String[] NAMES = {
        "00", "01", "02", "03", "04", "05", "06", "07", "08", "09"
    };

    public static final int NUM_IMAGES = 10;

    private ViewPager mImagePager;
    private PagerAdapter mImagePagerAdapter;
    private int mWidth;
    private int mHeight;
    private String mFilesPath;
    private ProgressDialog mProgressDialog;
    private BestpictureActivity mActivity;
    private DotsView mDotsView;
    private ImageItems mImageItems;
    private ImageLoadingThread mLoadingThread;
    private PhotoModule.NamedImages mNamedImages;
    private Uri mPlaceHolderUri;
    public static int BESTPICTURE_ACTIVITY_CODE = 11;

    static class ImageItems implements Parcelable, DotsViewItem {
        private Bitmap[] mBitmap;
        private boolean[] mChosen;
        private BestpictureActivity mActivity;

        public ImageItems(BestpictureActivity activity) {
            mBitmap = new Bitmap[NUM_IMAGES];
            mChosen = new boolean[NUM_IMAGES];
            for (int i = 0; i < mChosen.length; i++) {
                if (i == 0) {
                    mChosen[i] = true;
                } else {
                    mChosen[i] = false;
                }
            }
            mActivity = activity;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public Bitmap getBitmap(int index) {
            return mBitmap[index];
        }

        public void setBitmap(int index, Bitmap bitmap) {
            mBitmap[index] = bitmap;
        }

        @Override
        public int getTotalItemNums() {
            return NUM_IMAGES;
        }

        public boolean isChosen(int index) {
            return mChosen[index];
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }

        public void toggleImageSelection(int num) {
            mChosen[num] = !mChosen[num];
            boolean isChosen = false;
            for(int i=0; i < mChosen.length; i++) {
                isChosen |= mChosen[i];
            }
            if(!isChosen) {
                mChosen[num] = true;
                RotateTextToast.makeText(mActivity,
                        mActivity.getResources().getString(R.string.bestpicture_at_least_one_picture),
                        Toast.LENGTH_SHORT).show();
            }
            mActivity.mDotsView.update();
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mActivity = this;
        mFilesPath = getFilesDir()+"/Bestpicture";
        setContentView(R.layout.bestpicture_editor);
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mWidth = size.x/2;
        mHeight = size.y/2;
        mNamedImages = new PhotoModule.NamedImages();

        mImageItems = new ImageItems(mActivity);
        mDotsView = (DotsView) findViewById(R.id.dots_view);
        mDotsView.setItems(mImageItems);
        mPlaceHolderUri = getIntent().getData();

        mImagePager = (ViewPager) findViewById(R.id.bestpicture_pager);
        mImagePagerAdapter = new ImagePagerAdapter(getFragmentManager());
        mImagePager.setAdapter(mImagePagerAdapter);
        mImagePager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                mDotsView.update(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
            }
        });
        findViewById(R.id.bestpicture_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                int index = -1;
                for(int i=0; i < mImageItems.mChosen.length; i++) {
                    if (mImageItems.mChosen[i]) {
                        if(index != -1) {
                            new SaveImageTask().execute(mFilesPath + "/" + NAMES[i] + ".jpg");
                        } else {
                            index = i;
                            saveForground(mFilesPath + "/" + NAMES[i] + ".jpg");
                        }
                    }
                }
                setResult(RESULT_OK, new Intent());
                finish();
            }
        });
    }

    private class ImageLoadingThread extends Thread {
        public void run() {
            showProgressDialog();
            for(int i=0; i < NUM_IMAGES; i++) {
                String path = mFilesPath + "/" + BestpictureActivity.NAMES[i] + ".jpg";
                final BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                ExifInterface exif = new ExifInterface();
                int orientation = 0;
                try {
                    exif.readExif(path);
                    orientation = Exif.getOrientation(exif);
                } catch (IOException e) {
                }
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
                Bitmap bitmap = BitmapFactory.decodeFile(path, o);
                if (orientation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.setRotate(orientation);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                }
                mImageItems.setBitmap(i, bitmap);
            }
            dismissProgressDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLoadingThread == null) {
            mLoadingThread = new ImageLoadingThread();
            mLoadingThread.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void showProgressDialog() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mProgressDialog = ProgressDialog.show(mActivity, "", "Processing...", true, false);
                mProgressDialog.show();
            }
        });
    }

    private void dismissProgressDialog() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    private class ImagePagerAdapter extends FragmentStatePagerAdapter {
        public ImagePagerAdapter(android.app.FragmentManager manager) {
            super(manager);
        }

        @Override
        public android.app.Fragment getItem(int imageNum) {
           while(mImageItems.getBitmap(imageNum) == null) {
                try {
                    Thread.sleep(5);
                } catch (Exception e) {
                }
            }
            return BestpictureFragment.create(imageNum, mImageItems);
        }

        @Override
        public int getCount() {
            return NUM_IMAGES;
        }
    }

    private void saveForground(String path) {
        long captureStartTime = System.currentTimeMillis();
        mNamedImages.nameNewImage(captureStartTime);
        PhotoModule.NamedImages.NamedEntity name = mNamedImages.getNextNameEntity();
        String title = (name == null) ? null : name.title;
        String outPath = mPlaceHolderUri.getPath();
        try {
            FileOutputStream out = new FileOutputStream(outPath);
            FileInputStream in = new FileInputStream(path);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception e) {
        }
    }

    private class SaveImageTask extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... path) {
            long captureStartTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(captureStartTime);
            PhotoModule.NamedImages.NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            String outPath = Storage.generateFilepath(title, "jpeg");
            try {
                FileOutputStream out = new FileOutputStream(outPath);
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
            Uri uri = Uri.fromFile(new File(outPath));
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
            sendBroadcast(intent);
            return null;
        }

        protected void onPostExecute(Void v) {
        }
    }
}
