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

package com.android.camera.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.Image.Plane;
import android.util.Log;

public class ClearSightNativeEngine {
    private static final String TAG = "ClearSightNativeEngine";
    static {
        try {
            System.loadLibrary("jni_clearsight");
            mLibLoaded = true;
            Log.v(TAG, "successfully loaded clearsight lib");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "failed to load clearsight lib");
            e.printStackTrace();
            mLibLoaded = false;
        }
    }

    private static final int METADATA_SIZE = 5;
    private static final int Y_PLANE = 0;
    private static final int VU_PLANE = 2;

    // dummy OTP calib data
    private static final String otp_calib = "Calibration OTP format version = 10301\n"
            + "Main Native Sensor Resolution width = 4224px\n"
            + "Main Native Sensor Resolution height = 3136px\n"
            + "Main Calibration Resolution width = 1280px\n"
            + "Main Calibration Resolution height = 950px\n"
            + "Main Focal length ratio = 1.004896\n"
            + "Aux Native Sensor Resolution width = 1600px\n"
            + "Aux Native Sensor Resolution height = 1200px\n"
            + "Aux Calibration Resolution width = 1280px\n"
            + "Aux Calibration Resolution height = 960px\n"
            + "Aux Focal length ratio = 1.000000\n"
            + "Relative Rotation matrix [0] through [8] = 1.000000,-0.003008,0.000251,0.003073,1.000189,0.003329,0.019673,-0.003329,1.000284\n"
            + "Relative Geometric surface parameters [0] through [31] = -0.307164,-0.879074,4.636152,0.297486,-0.157539,-6.889396,0.109467,-2.797022,-0.066306,-0.120142,0.196464,0.021974,2.905827,0.241197,0.048328,-5.116615,0.496533,-5.263813,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000\n"
            + "Relative Principal point X axis offset (ox) = 0.000000px\n"
            + "Relative Principal point Y axis offset (oy) = 0.000000px\n"
            + "Relative position flag = 1\n"
            + "Baseline distance = 20.000000mm\n"
            + "Main sensor mirror and flip setting = 3\n"
            + "Aux sensor mirror and flip setting = 3\n"
            + "Module orientation during calibration = 0\n"
            + "Rotation flag = 0\n"
            + "Main Normalized Focal length = 1000.0px\n"
            + "Aux Normalized Focal length = 1000.0px";

    private static boolean mLibLoaded;
    private static ClearSightNativeEngine mInstance;

    private Image mRefColorImage;
    private Image mRefMonoImage;
    private ArrayList<SourceImage> mSrcColor = new ArrayList<SourceImage>();
    private ArrayList<SourceImage> mSrcMono = new ArrayList<SourceImage>();

    private ClearSightNativeEngine() {
    }

    public static void createInstance() {
        if (mInstance == null) {
            mInstance = new ClearSightNativeEngine();
        }
    }

    public static ClearSightNativeEngine getInstance() {
        createInstance();
        return mInstance;
    }

    public boolean isLibLoaded() {
        return mLibLoaded;
    }

    public void reset() {
        mSrcColor.clear();
        mSrcMono.clear();
        setReferenceColorImage(null);
        setReferenceMonoImage(null);
    }

    public void setReferenceImage(boolean color, Image image) {
        if (color)
            setReferenceColorImage(image);
        else
            setReferenceMonoImage(image);
    }

    private void setReferenceColorImage(Image reference) {
        if (mRefColorImage != null) {
            mRefColorImage.close();
            mRefColorImage = null;
        }

        mRefColorImage = reference;

        if (mRefColorImage != null) {
            Log.e(TAG,
                    "setRefColorImage - isdirectbuff: "
                            + mRefColorImage.getPlanes()[0].getBuffer()
                                    .isDirect());
            mSrcColor.add(new SourceImage(mRefColorImage.getPlanes()[Y_PLANE]
                    .getBuffer(), mRefColorImage.getPlanes()[VU_PLANE]
                    .getBuffer(), new int[] { 0, 0, 0, 0, 0 }));
        }
    }

    private void setReferenceMonoImage(Image reference) {
        if (mRefMonoImage != null) {
            mRefMonoImage.close();
            mRefMonoImage = null;
        }

        mRefMonoImage = reference;

        if (mRefMonoImage != null) {
            Log.e(TAG,
                    "setRefMonoImage - isdirectbuff: "
                            + mRefMonoImage.getPlanes()[0].getBuffer()
                                    .isDirect());
            mSrcMono.add(new SourceImage(mRefMonoImage.getPlanes()[Y_PLANE]
                    .getBuffer(), null, new int[] { 0, 0, 0, 0, 0 }));
        }
    }

    public boolean hasReferenceImage(boolean color) {
        return !(color ? mSrcColor.isEmpty() : mSrcMono.isEmpty());
    }

    public Image getReferenceImage(boolean color) {
        return color ? mRefColorImage : mRefMonoImage;
    }

    public boolean registerImage(boolean color, Image image) {
        return (color ? registerColorImage(image) : registerMonoImage(image));
    }

    private boolean registerColorImage(Image image) {
        if (mSrcColor.isEmpty()) {
            Log.w(TAG, "reference color image not yet set");
            return false;
        }

        Plane[] planes = image.getPlanes();
        ByteBuffer refY = mRefColorImage.getPlanes()[Y_PLANE].getBuffer();
        ByteBuffer refVU = mRefColorImage.getPlanes()[VU_PLANE].getBuffer();
        ByteBuffer regY = ByteBuffer.allocateDirect(refY.capacity());
        ByteBuffer regVU = ByteBuffer.allocateDirect(refVU.capacity());
        int[] metadata = new int[METADATA_SIZE];

        boolean result = clearSightRegisterImage(refY,
                planes[Y_PLANE].getBuffer(), planes[VU_PLANE].getBuffer(),
                image.getWidth(), image.getHeight(),
                planes[Y_PLANE].getRowStride(),
                planes[VU_PLANE].getRowStride(), regY, regVU, metadata);

        if (result) {
            mSrcColor.add(new SourceImage(regY, regVU, metadata));
        }

        image.close();
        return result;
    }

    private boolean registerMonoImage(Image image) {
        if (mSrcMono.isEmpty()) {
            Log.w(TAG, "reference mono image not yet set");
            return false;
        }

        Plane[] planes = image.getPlanes();
        ByteBuffer refY = mRefMonoImage.getPlanes()[Y_PLANE].getBuffer();
        ByteBuffer regY = ByteBuffer.allocateDirect(refY.capacity());
        int[] metadata = new int[METADATA_SIZE];

        boolean result = clearSightRegisterImage(refY,
                planes[Y_PLANE].getBuffer(), null, image.getWidth(),
                image.getHeight(), planes[Y_PLANE].getRowStride(), 0, regY,
                null, metadata);

        if (result) {
            mSrcMono.add(new SourceImage(regY, null, metadata));
        }

        image.close();
        return result;
    }

    public ClearsightImage processImage() {
        // check data validity
        if (mSrcColor.size() != mSrcMono.size()) {
            // mis-match in num images
            Log.e(TAG, "processImage - numImages mismatch - bayer: "
                    + mSrcColor.size() + ", mono: " + mSrcMono.size());
            return null;
        }

        int numImages = mSrcColor.size();
        ByteBuffer[] srcColorY = new ByteBuffer[numImages];
        ByteBuffer[] srcColorVU = new ByteBuffer[numImages];
        int[][] metadataColor = new int[numImages][];
        ByteBuffer[] srcMonoY = new ByteBuffer[numImages];
        int[][] metadataMono = new int[numImages][];

        Log.e(TAG, "processImage - numImages: " + numImages);

        for (int i = 0; i < numImages; i++) {
            SourceImage color = mSrcColor.get(i);
            SourceImage mono = mSrcMono.get(i);

            srcColorY[i] = color.mY;
            srcColorVU[i] = color.mVU;
            metadataColor[i] = color.mMetadata;

            srcMonoY[i] = mono.mY;
            metadataMono[i] = mono.mMetadata;
        }

        Plane[] colorPlanes = mRefColorImage.getPlanes();
        Plane[] monoPlanes = mRefMonoImage.getPlanes();
        ByteBuffer dstY = ByteBuffer.allocateDirect(colorPlanes[Y_PLANE]
                .getBuffer().capacity());
        ByteBuffer dstVU = ByteBuffer.allocateDirect(colorPlanes[VU_PLANE]
                .getBuffer().capacity());
        int[] roiRect = new int[4];

        boolean result = clearSightProcess(numImages, srcColorY, srcColorVU,
                metadataColor, mRefColorImage.getWidth(),
                mRefColorImage.getHeight(),
                colorPlanes[Y_PLANE].getRowStride(),
                colorPlanes[VU_PLANE].getRowStride(), srcMonoY, metadataMono,
                mRefMonoImage.getWidth(), mRefMonoImage.getHeight(),
                monoPlanes[Y_PLANE].getRowStride(), otp_calib.getBytes(), dstY, dstVU,
                colorPlanes[Y_PLANE].getRowStride(),
                colorPlanes[VU_PLANE].getRowStride(), roiRect);

        if (result) {
            dstY.rewind();
            dstVU.rewind();
            byte[] data = new byte[dstY.capacity() + dstVU.capacity()];
            int[] strides = new int[] { colorPlanes[Y_PLANE].getRowStride(),
                    colorPlanes[VU_PLANE].getRowStride() };
            dstY.get(data, 0, dstY.capacity());
            dstVU.get(data, dstY.capacity(), dstVU.capacity());
            return new ClearsightImage(new YuvImage(data, ImageFormat.NV21,
                    mRefColorImage.getWidth(), mRefColorImage.getHeight(),
                    strides), roiRect);
        } else {
            return null;
        }
    }

    native public boolean configureClearSight(float focalLengthRatio,
            float brIntensity, float sharpenIntensity);

    native public boolean clearSightRegisterImage(ByteBuffer refY,
            ByteBuffer srcY, ByteBuffer srcVU, int width, int height,
            int strideY, int strideVU, ByteBuffer dstY, ByteBuffer dstVU,
            int[] metadata);

    native public boolean clearSightProcess(int numImagePairs,
            ByteBuffer[] srcColorY, ByteBuffer[] srcColorVU,
            int[][] metadataColor, int srcColorWidth, int srcColorHeight,
            int srcColorStrideY, int srcColorStrideVU, ByteBuffer[] srcMonoY,
            int[][] metadataMono, int srcMonoWidth, int srcMonoHeight,
            int srcMonoStrideY, byte[] otp, ByteBuffer dstY, ByteBuffer dstVU,
            int dstStrideY, int dstStrideVU, int[] roiRect);

    private class SourceImage {
        ByteBuffer mY;
        ByteBuffer mVU;
        int[] mMetadata;

        SourceImage(ByteBuffer y, ByteBuffer vu, int[] metadata) {
            mY = y;
            mVU = vu;
            mMetadata = metadata;
        }
    }

    public static class ClearsightImage {
        private YuvImage mImage;
        private Rect mRoiRect;

        ClearsightImage(YuvImage image, int[] rect) {
            mImage = image;
            mRoiRect = new Rect(rect[0], rect[1], rect[0] + rect[2], rect[1]
                    + rect[3]);
        }

        public Rect getRoiRect() {
            return mRoiRect;
        }

        public long getDataLength() {
            return (mImage==null?0:mImage.getYuvData().length);
        }

        public int getWidth() {
            return (mRoiRect.right - mRoiRect.left);
        }

        public int getHeight() {
            return (mRoiRect.bottom - mRoiRect.top);
        }

        public byte[] compressToJpeg() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            mImage.compressToJpeg(mRoiRect, 100, baos);
            return baos.toByteArray();
        }
    }
}
