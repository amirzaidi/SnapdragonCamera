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

package com.android.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.camera.WideAnglePanoramaModule.INotifier;

/**
 * A singleton to handle the processing of each frame by {@link Mosaic}.
 */
public class MosaicFrameProcessor {
    private static final String TAG = "MosaicFrameProcessor";

    public static final int MAX_HORIZONAL_ANGLE = 180;
    public static final int MAX_VERTICAL_ANGLE = 180;
    public static final int MSG_PANORAMA_TIP = 0x0001;
    public static final int MSG_CAPTURE_SUCCESS = 0x0002;
    public static final int MSG_CAPTURE_FAILED = 0x0003;
    public static final int MSG_UPDATE_UI = 0x0004;
    /** Panorama direction unknown. */
    public final static int DIRECTION_UNKNOW = 0x00000000;
    /** Panorama direction left to right. */
    public final static int DIRECTION_LEFTTORIGHT = 0x00000001;
    /** Panorama direction right to left. */
    public final static int DIRECTION_RIGHTTOLEFT = 0x00000002;
    /** Panorama direction up to down. */
    public final static int DIRECTION_UPTODOWN = 0x00000004;
    /** Panorama direction down to up. */
    public final static int DIRECTION_DOWMTOUP = 0x00000008;

    private int mPreviewWidth;
    private int mPreviewHeight;

    private Handler mThreadHandler = null;
    private long mHandler = 0;
    private boolean mIsActive = false;

    private INotifier mPanoNotifier;
    private static MosaicFrameProcessor sMosaicFrameProcessor; // singleton

    static {
        System.loadLibrary("jni_snapcammosaic");
    }

    public static MosaicFrameProcessor getInstance() {
        if (sMosaicFrameProcessor == null) {
            sMosaicFrameProcessor = new MosaicFrameProcessor();
        }
        return sMosaicFrameProcessor;
    }

    public synchronized boolean IsInited() {
        return (mHandler != 0 && mIsActive);
    }

    public synchronized int Init(Context context, int maxFrameCount, int width, int height,
            INotifier notifier) {
        Log.v(TAG, "Init <----");
        Uninit();
        mPanoNotifier = notifier;
        mThreadHandler = new Handler(context.getMainLooper(), new Handler.Callback() {

            @Override
            public boolean handleMessage(Message msg) {
                if (mPanoNotifier != null) {
                    mPanoNotifier.onNotify(msg.what, msg.obj);
                }
                return true;
            }

        });
        mPreviewWidth = width;
        mPreviewHeight = height;
        mHandler = _InitMosaic(context, this, maxFrameCount, width, height);
        mIsActive = (mHandler != 0);
        Log.v(TAG, "Init mHandler: " + mHandler + " ---->");
        return mIsActive ? 0 : -1;
    }

    public synchronized void Uninit() {
        if (mHandler != 0) {
            Log.v(TAG, "Unint <----");
            mIsActive = false;
            mThreadHandler = null;
            _UninitMosaic(mHandler);
            mHandler = 0;
            Log.v(TAG, "Unint ---->");
        }
    }

    public synchronized int Process(byte[] data, int width, int height) {
        int res = -1;
        if (IsInited() && data != null && mPreviewWidth == width && mPreviewHeight == height) {
            Log.v(TAG, "Process <----");
            res = _ProcessMosaic(mHandler, data, width, height);
            Log.v(TAG, "Process res " + res + " ---->");
        }
        else {
            Log.v(TAG, "Process Error " + " mWidth " + mPreviewWidth + " mHeight " + mPreviewHeight
                    + " width " + width + " height " + height + " data " + (data != null));
        }
        return res;
    }

    public synchronized int StopProcessing() {
        int res = -1;
        if (IsInited()) {
            Log.v(TAG, "StopProcessing <----");
            mIsActive = false;
            res = _StopProcessMosaic(mHandler);
            Log.v(TAG, "StopProcessing res " + res + " ---->");
        }
        return res;
    }

    public int onNotify(int key, Object obj) {
        if (mThreadHandler != null && mHandler != 0) {
            Message msg = new Message();
            msg.what = key;
            msg.obj = obj;
            mThreadHandler.sendMessage(msg);
        }
        return 0;
    }

    public native long _InitMosaic(Context context, Object thiz, int maxFrameCount, int width,
            int height);

    public native int _UninitMosaic(long handler);

    public native int _StopProcessMosaic(long handler);

    public native int _ProcessMosaic(long handler, byte[] data, int width, int height);

}
