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
package com.android.camera.imageprocessor.filter;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.android.camera.BestpictureActivity;
import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.MediaSaveService;
import com.android.camera.PhotoModule;
import com.android.camera.imageprocessor.PostProcessor;
import com.android.camera.util.CameraUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BestpictureFilter implements ImageFilter {
    public static final int NUM_REQUIRED_IMAGE = 10;
    private int mWidth;
    private int mHeight;
    private int mStrideY;
    private int mStrideVU;
    private static String TAG = "BestpictureFilter";
    private static boolean mIsSupported = false;
    private CaptureModule mModule;
    private CameraActivity mActivity;
    private int mOrientation = 0;
    final String[] NAMES = {"00.jpg", "01.jpg", "02.jpg", "03.jpg",
            "04.jpg", "05.jpg", "06.jpg", "07.jpg", "08.jpg"
            ,"09.jpg"};
    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    private final static int TIME_DELAY = 50;
    private int mSavedCount = 0;
    private PhotoModule.NamedImages mNamedImages;
    private ByteBuffer mBY;
    private ByteBuffer mBVU;
    private Object mClosingLock = new Object();
    private boolean mIsOn = false;
    private PostProcessor mProcessor;
    private ProgressDialog mProgressDialog;
    private ImageFilter.ResultImage mBestpictureResultImage;

    private static void Log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public BestpictureFilter(CaptureModule module, CameraActivity activity, PostProcessor processor) {
        mModule = module;
        mActivity = activity;
        mProcessor = processor;
        mNamedImages = new PhotoModule.NamedImages();
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        List<CaptureRequest> list = new ArrayList<CaptureRequest>();
        for(int i=0; i < NUM_REQUIRED_IMAGE; i++) {
            list.add(builder.build());
        }
        return list;
    }

    @Override
    public String getStringName() {
        return "BestpictureFilter";
    }

    @Override
    public int getNumRequiredImage() {
        return NUM_REQUIRED_IMAGE;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        Log("init");
        mWidth = width/2*2;
        mHeight = height/2*2;
        mStrideY = strideY/2*2;
        mStrideVU = strideVU / 2 * 2;
        mIsOn = true;
        Log("width: " + mWidth + " height: " + mHeight + " strideY: " + mStrideY + " strideVU: " + mStrideVU);
    }

    @Override
    public void deinit() {
        Log("deinit");
        dismissProgressDialog();
        synchronized (mClosingLock) {
            mIsOn = false;
        }
    }

    @Override
    public void addImage(final ByteBuffer bY, final ByteBuffer bVU, final int imageNum, Object param) {
        Log("addImage");
        if(imageNum == 0) {
            showProgressDialog();
            mOrientation = CameraUtil.getJpegRotation(mModule.getMainCameraId(), mModule.getDisplayOrientation());
            mSavedCount = 0;
            mBY = bY;
            mBVU = bVU;

            byte[] bytes = getYUVBytes(bY, bVU, imageNum);
            long captureStartTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(captureStartTime);
            PhotoModule.NamedImages.NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;
            mActivity.getMediaSaveService().addImage(
                    bytes, title, date, null, mWidth, mHeight,
                    mOrientation, null, new MediaSaveService.OnMediaSavedListener() {
                        @Override
                        public void onMediaSaved(final  Uri uri) {
                            if (uri != null) {
                                mActivity.notifyNewMedia(uri);
                                new Thread() {
                                    public void run() {
                                        while(mSavedCount < NUM_REQUIRED_IMAGE) {
                                            try {
                                                Thread.sleep(10);
                                            } catch (Exception e) {
                                            }
                                        }
                                        mActivity.runOnUiThread(new Runnable() {
                                            public void run() {
                                                dismissProgressDialog();
                                                startBestpictureActivity(uri);
                                            }
                                        });
                                    }
                                }.start();

                            }
                        }
                    }
                    , mActivity.getContentResolver(), "jpeg");
        }
        byte[] bytes = getYUVBytes(bY, bVU, imageNum);
        saveBestPicture(bytes, imageNum);
    }

    @Override
    public ResultImage processImage() {
        return null;
    }

    private void showProgressDialog() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mProgressDialog = ProgressDialog.show(mActivity, "", "Saving pictures...", true, false);
                mProgressDialog.show();
            }
        });
    }

    private byte[] getYUVBytes(final ByteBuffer yBuf, final ByteBuffer vuBuf,
                               final int imageNum) {
        synchronized (mClosingLock) {
            if (!mIsOn) {
                return null;
            }
            mBestpictureResultImage = new ImageFilter.ResultImage(ByteBuffer.allocateDirect(
                    mStrideY * mHeight * 3 / 2),
                    new Rect(0, 0, mWidth, mHeight), mWidth, mHeight, mStrideY);
            yBuf.get(mBestpictureResultImage.outBuffer.array(), 0, yBuf.remaining());
            vuBuf.get(mBestpictureResultImage.outBuffer.array(), mStrideY * mHeight,
                    vuBuf.remaining());
            yBuf.rewind();
            vuBuf.rewind();

            return nv21ToJpeg(mBestpictureResultImage, mOrientation,
                    mProcessor.waitForMetaData(imageNum));
        }
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

    private void startBestpictureActivity(Uri uri) {
        Log("Start best picture activity");
        Intent intent = new Intent();
        intent.setData(uri);
        if (mActivity.isSecureCamera()) {
            intent.setAction(INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
        }
        intent.setClass(mActivity, BestpictureActivity.class);
        mActivity.startActivityForResult(intent, BestpictureActivity.BESTPICTURE_ACTIVITY_CODE);
    }

    @Override
    public boolean isSupported() {
        if (mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            return false;
        }
        return mIsSupported;
    }

    @Override
    public boolean isFrameListener() {
        return false;
    }

    @Override
    public boolean isManualMode() {
        return true;
    }

    @Override
    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback, Handler handler) throws CameraAccessException {
        for(int i=0; i < NUM_REQUIRED_IMAGE; i++) {
            captureSession.capture(builder.build(), callback, handler);
            try {
                Thread.sleep(TIME_DELAY);
            } catch (InterruptedException e) {
            }
        }
    }

    public static boolean isSupportedStatic() {
        return mIsSupported;
    }

    private byte[] nv21ToJpeg(ImageFilter.ResultImage resultImage, int orientation,
                              TotalCaptureResult result) {
        BitmapOutputStream bos = new BitmapOutputStream(1024);
        YuvImage im = new YuvImage(resultImage.outBuffer.array(), ImageFormat.NV21,
                resultImage.width, resultImage.height, new int[]{resultImage.stride,
                resultImage.stride});
        im.compressToJpeg(resultImage.outRoi, mProcessor.getJpegQualityValue(), bos);
        byte[] bytes = bos.getArray();
        bytes = PostProcessor.addExifTags(bytes, orientation, result);
        return bytes;
    }

    private class BitmapOutputStream extends ByteArrayOutputStream {
        public BitmapOutputStream(int size) {
            super(size);
        }

        public byte[] getArray() {
            return buf;
        }
    }

    private void saveBestPicture(byte[] bytes, int imageNum) {
        if(bytes == null)
            return;
        String filesPath = mActivity.getFilesDir()+"/Bestpicture";
        File file = new File(filesPath);
        if(!file.exists()) {
            file.mkdir();
        }
        file = new File(filesPath+"/"+NAMES[imageNum]);
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes, 0, bytes.length);
            out.close();
        } catch (Exception e) {
        }
        mSavedCount++;
        Log(imageNum+" image is saved");
    }
}
