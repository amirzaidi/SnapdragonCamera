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

package org.codeaurora.snapcam.filter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Rect;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.Image.Plane;
import android.util.Log;

public class ClearSightNativeEngine {
    private static final boolean DEBUG = false;
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

    private static final int METADATA_SIZE = 6;
    private static final int Y_PLANE = 0;
    private static final int VU_PLANE = 2;

    private static boolean mLibLoaded;
    private static byte[] mOtpCalibData;
    private static ClearSightNativeEngine mInstance;

    private int mYSize;
    private int mVUSize;
    private int mYStride;
    private int mVUStride;
    private Image mRefColorImage;
    private Image mRefMonoImage;
    private TotalCaptureResult mRefColorResult;
    private TotalCaptureResult mRefMonoResult;
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

    public static void setOtpCalibData(CamSystemCalibrationData calibData) {
        String calibStr = calibData.toString();
        Log.d(TAG, "OTP calibration data: \n" + calibStr);
        mOtpCalibData = calibStr.getBytes();
    }

    public void setImageSizeStride(Image image) {
        Plane[] planes = image.getPlanes();
        mYSize = planes[Y_PLANE].getBuffer().capacity();
        mVUSize = planes[VU_PLANE].getBuffer().capacity();
        mYStride = planes[Y_PLANE].getRowStride();
        mVUStride = planes[VU_PLANE].getRowStride();
    }

    public boolean isLibLoaded() {
        return mLibLoaded;
    }

    public void reset() {
        mSrcColor.clear();
        mSrcMono.clear();
        setReferenceColorImage(null);
        setReferenceMonoImage(null);
        setReferenceColorResult(null);
        setReferenceMonoResult(null);
    }

    public void setReferenceResult(boolean color, TotalCaptureResult result) {
        if (color)
            setReferenceColorResult(result);
        else
            setReferenceMonoResult(result);
    }

    private void setReferenceColorResult(TotalCaptureResult result) {
        mRefColorResult = result;
    }

    private void setReferenceMonoResult(TotalCaptureResult result) {
        mRefMonoResult = result;
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
            Log.d(TAG, "setRefColorImage");
            setImageSizeStride(mRefColorImage);
            mSrcColor.add(new SourceImage(mRefColorImage.getPlanes()[Y_PLANE]
                    .getBuffer(), mRefColorImage.getPlanes()[VU_PLANE]
                            .getBuffer(), new float[] { 0, 0, 0, 0, 0, 0 }));
        }
    }

    private void setReferenceMonoImage(Image reference) {
        if (mRefMonoImage != null) {
            mRefMonoImage.close();
            mRefMonoImage = null;
        }

        mRefMonoImage = reference;

        if (mRefMonoImage != null) {
            Log.d(TAG, "setRefMonoImage");
            mSrcMono.add(new SourceImage(mRefMonoImage.getPlanes()[Y_PLANE]
                    .getBuffer(), null, new float[] { 0, 0, 0, 0, 0, 0 }));
        }
    }

    public boolean hasReferenceImage(boolean color) {
        return (getImageCount(color) > 0);
    }

    public int getImageCount(boolean color) {
        return color ? mSrcColor.size() : mSrcMono.size();
    }

    public Image getReferenceImage(boolean color) {
        return color ? mRefColorImage : mRefMonoImage;
    }

    public TotalCaptureResult getReferenceResult(boolean color) {
        return color ? mRefColorResult : mRefMonoResult;
    }

    public boolean registerImage(boolean color, Image image) {
        List<SourceImage> sourceImages = color?mSrcColor:mSrcMono;
        if (sourceImages.isEmpty()) {
            Log.w(TAG, "reference image not yet set");
            return false;
        }

        Image referenceImage = color?mRefColorImage:mRefMonoImage;
        Plane[] planes = image.getPlanes();
        ByteBuffer yBuf = planes[Y_PLANE].getBuffer();
        ByteBuffer refY = referenceImage.getPlanes()[Y_PLANE].getBuffer();
        ByteBuffer regY = ByteBuffer.allocateDirect(refY.capacity());
        int yRowStride = planes[Y_PLANE].getRowStride();

        ByteBuffer vuBuf = null;
        ByteBuffer refVU = null;
        ByteBuffer regVU = null;
        int vuRowStride = 0;
        if(color) {
            vuBuf = planes[VU_PLANE].getBuffer();
            refVU = referenceImage.getPlanes()[VU_PLANE].getBuffer();
            regVU = ByteBuffer.allocateDirect(refVU.capacity());
            vuRowStride = planes[VU_PLANE].getRowStride();
        }

        float[] metadata = new float[METADATA_SIZE];

        boolean result = nativeClearSightRegisterImage(refY,
                yBuf, vuBuf, image.getWidth(), image.getHeight(),
                yRowStride, vuRowStride, regY, regVU, metadata);

        if (result) {
            sourceImages.add(new SourceImage(regY, regVU, metadata));
        }

        image.close();
        return result;
    }

    public boolean initProcessImage() {
        // check data validity
        if (mSrcColor.size() != mSrcMono.size()) {
            // mis-match in num images
            Log.d(TAG, "processImage - numImages mismatch - bayer: "
                    + mSrcColor.size() + ", mono: " + mSrcMono.size());
            return false;
        }

        int numImages = mSrcColor.size();
        ByteBuffer[] srcColorY = new ByteBuffer[numImages];
        ByteBuffer[] srcColorVU = new ByteBuffer[numImages];
        float[][] metadataColor = new float[numImages][];
        ByteBuffer[] srcMonoY = new ByteBuffer[numImages];
        float[][] metadataMono = new float[numImages][];

        Log.d(TAG, "processImage - numImages: " + numImages);

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

        Log.d(TAG, "processImage - refImage size - y: "
                + colorPlanes[Y_PLANE].getBuffer().capacity()
                + " vu: " + colorPlanes[VU_PLANE].getBuffer().capacity());

        int iso = mRefMonoResult.get(CaptureResult.SENSOR_SENSITIVITY);
        long exposure = mRefMonoResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        // capture result stores exposure time in NS and we need MS.
        exposure /= 100000;

        Log.d(TAG, "processImage - iso: " + iso + " exposure ms: " + exposure);
        return nativeClearSightProcessInit(numImages, srcColorY, srcColorVU,
                metadataColor, mRefColorImage.getWidth(),
                mRefColorImage.getHeight(),
                colorPlanes[Y_PLANE].getRowStride(),
                colorPlanes[VU_PLANE].getRowStride(), srcMonoY, metadataMono,
                mRefMonoImage.getWidth(), mRefMonoImage.getHeight(),
                monoPlanes[Y_PLANE].getRowStride(), mOtpCalibData,
                (int)exposure, iso);
    }

    public ClearsightImage processImage() {
        // check data validity
        ByteBuffer dstY = ByteBuffer.allocateDirect(mYSize);
        ByteBuffer dstVU = ByteBuffer.allocateDirect(mVUSize);
        int[] roiRect = new int[4];

        Log.d(TAG, "processImage - dst size - y: "
                + dstY.capacity() + " vu: " + dstVU.capacity());

        boolean result = nativeClearSightProcess(dstY, dstVU,
                mYStride, mVUStride, roiRect);

        if (result) {
            dstY.rewind();
            dstVU.rewind();
            return new ClearsightImage(dstY, dstVU, roiRect);
        } else {
            return null;
        }
    }

    private native final boolean nativeClearSightRegisterImage(ByteBuffer refY,
            ByteBuffer srcY, ByteBuffer srcVU, int width, int height,
            int strideY, int strideVU, ByteBuffer dstY, ByteBuffer dstVU,
            float[] metadata);

    private native final boolean nativeClearSightProcessInit(int numImagePairs,
            ByteBuffer[] srcColorY, ByteBuffer[] srcColorVU,
            float[][] metadataColor, int srcColorWidth, int srcColorHeight,
            int srcColorStrideY, int srcColorStrideVU, ByteBuffer[] srcMonoY,
            float[][] metadataMono, int srcMonoWidth, int srcMonoHeight,
            int srcMonoStrideY, byte[] otp, int exposureMs, int iso);

    private native final boolean nativeClearSightProcess(ByteBuffer dstY,
            ByteBuffer dstVU, int dstStrideY, int dstStrideVU, int[] roiRect);

    private class SourceImage {
        ByteBuffer mY;
        ByteBuffer mVU;
        float[] mMetadata;

        SourceImage(ByteBuffer y, ByteBuffer vu, float[] metadata) {
            mY = y;
            mVU = vu;
            mMetadata = metadata;
        }
    }

    public static class ClearsightImage {
        public final ByteBuffer mYplane;
        public final ByteBuffer mVUplane;
        private Rect mRoiRect;

        ClearsightImage(ByteBuffer yPlane, ByteBuffer vuPlane, int[] rect) {
            mYplane = yPlane;
            mVUplane = vuPlane;
            mRoiRect = new Rect(rect[0], rect[1], rect[0] + rect[2], rect[1]
                    + rect[3]);
        }

        public Rect getRoiRect() {
            return mRoiRect;
        }

        public int getWidth() {
            return (mRoiRect.right - mRoiRect.left);
        }

        public int getHeight() {
            return (mRoiRect.bottom - mRoiRect.top);
        }
    }

    public static class CamSensorCalibrationData {
        /* Focal length in pixels @ calibration resolution.*/
        float normalized_focal_length;
        /* Native sensor resolution W that was used to capture calibration image */
        short native_sensor_resolution_width;
        /* Native sensor resolution H that was used to capture calibration image */
        short native_sensor_resolution_height;
        /* Image size W used internally by calibration tool */
        short calibration_sensor_resolution_width;
        /* Image size H used internally by calibration tool */
        short calibration_sensor_resolution_height;
        /* Focal length ratio @ Calibration */
        float focal_length_ratio;

        private CamSensorCalibrationData() {}

        public static CamSensorCalibrationData createFromBytes(byte[] bytes) {
            final ByteBuffer buf = ByteBuffer.wrap(bytes);
            return createFromByteBuff(buf);
        }

        public static CamSensorCalibrationData createFromByteBuff(ByteBuffer buf) {
            final CamSensorCalibrationData data = new CamSensorCalibrationData();

            data.normalized_focal_length = buf.getFloat();
            data.native_sensor_resolution_width = buf.getShort();
            data.native_sensor_resolution_height = buf.getShort();
            data.calibration_sensor_resolution_width = buf.getShort();
            data.calibration_sensor_resolution_height = buf.getShort();
            data.focal_length_ratio = buf.getFloat();

            return data;
        }
    }

    public static class CamSystemCalibrationData {
        private static final String[] CALIB_FMT_STRINGS = {
            "Calibration OTP format version = %x\n",
            "Main Native Sensor Resolution width = %dpx\n",
            "Main Native Sensor Resolution height = %dpx\n",
            "Main Calibration Resolution width = %dpx\n",
            "Main Calibration Resolution height = %dpx\n",
            "Main Focal length ratio = %f\n",
            "Aux Native Sensor Resolution width = %dpx\n",
            "Aux Native Sensor Resolution height = %dpx\n",
            "Aux Calibration Resolution width = %dpx\n",
            "Aux Calibration Resolution height = %dpx\n",
            "Aux Focal length ratio = %f\n",
            "Relative Rotation matrix [0] through [8] = %s\n",
            "Relative Geometric surface parameters [0] through [31] = %s\n",
            "Relative Principal point X axis offset (ox) = %fpx\n",
            "Relative Principal point Y axis offset (oy) = %fpx\n",
            "Relative position flag = %d\n",
            "Baseline distance = %fmm\n",
            "Main sensor mirror and flip setting = %d\n",
            "Aux sensor mirror and flip setting = %d\n",
            "Module orientation during calibration = %d\n",
            "Rotation flag = %d\n",
            "Main Normalized Focal length = %fpx\n",
            "Aux Normalized Focal length = %fpx"
        };

        /* Version information */
        int calibration_format_version;

        /* Main Camera Sensor specific calibration */
        CamSensorCalibrationData  main_cam_specific_calibration;
        /* Aux Camera Sensor specific calibration */
        CamSensorCalibrationData  aux_cam_specific_calibration;

        /* Relative viewpoint matching matrix w.r.t Main */
        float[] relative_rotation_matrix = new float[9];
        /* Relative geometric surface description parameters */
        float[] relative_geometric_surface_parameters = new float[32];

        /* Relative offset of sensor center from optical axis along horizontal dimension */
        float relative_principle_point_x_offset;
        /* Relative offset of sensor center from optical axis along vertical dimension */
        float relative_principle_point_y_offset;

        /* 0=Main Camera is on the left of Aux; 1=Main Camera is on the right of Aux */
        short relative_position_flag;
        /* Camera separation in mm */
        float relative_baseline_distance;

        /* calibration orientation fields */
        short main_sensor_mirror_and_flip_setting;
        short aux_sensor_mirror_and_flip_setting;
        short module_orientation_during_calibration;
        short rotation_flag;

        private CamSystemCalibrationData() {}

        public static CamSystemCalibrationData createFromBytes(byte[] bytes) {
            if(bytes == null)
                return null;

            final ByteBuffer buf = ByteBuffer.wrap(bytes);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            CamSystemCalibrationData data = createFromByteBuff(buf);

            if(DEBUG) {
                StringBuilder sb = new StringBuilder();
                sb.append("OTP Calib Data:");
                for(int i=0; i<bytes.length; i++) {
                    if(i%16 == 0)
                        sb.append("\n");
                    sb.append(String.format("%02X ", bytes[i]));
                }
                Log.d(TAG, sb.toString());
                Log.d(TAG, "Parsed OTP DATA:\n" + data.toString());
            }

            return data;
        }

        public static CamSystemCalibrationData createFromByteBuff(ByteBuffer buf) {
            final CamSystemCalibrationData data = new CamSystemCalibrationData();

            data.calibration_format_version = buf.getInt();
            data.main_cam_specific_calibration = CamSensorCalibrationData.createFromByteBuff(buf);
            data.aux_cam_specific_calibration = CamSensorCalibrationData.createFromByteBuff(buf);

            for(int i=0; i<9; i++)
                data.relative_rotation_matrix[i] = buf.getFloat();

            for(int i=0; i<32; i++)
                data.relative_geometric_surface_parameters[i] = buf.getFloat();

            data.relative_principle_point_x_offset = buf.getFloat();
            data.relative_principle_point_y_offset = buf.getFloat();
            data.relative_position_flag = buf.getShort();
            data.relative_baseline_distance = buf.getFloat();

            data.main_sensor_mirror_and_flip_setting = buf.getShort();
            data.aux_sensor_mirror_and_flip_setting = buf.getShort();
            data.module_orientation_during_calibration = buf.getShort();
            data.rotation_flag = buf.getShort();

            return data;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(CALIB_FMT_STRINGS[0], this.calibration_format_version));

            sb.append(String.format(CALIB_FMT_STRINGS[1], this.main_cam_specific_calibration.native_sensor_resolution_width));
            sb.append(String.format(CALIB_FMT_STRINGS[2], this.main_cam_specific_calibration.native_sensor_resolution_height));
            sb.append(String.format(CALIB_FMT_STRINGS[3], this.main_cam_specific_calibration.calibration_sensor_resolution_width));
            sb.append(String.format(CALIB_FMT_STRINGS[4], this.main_cam_specific_calibration.calibration_sensor_resolution_height));
            sb.append(String.format(CALIB_FMT_STRINGS[5], this.main_cam_specific_calibration.focal_length_ratio));

            sb.append(String.format(CALIB_FMT_STRINGS[6], this.aux_cam_specific_calibration.native_sensor_resolution_width));
            sb.append(String.format(CALIB_FMT_STRINGS[7], this.aux_cam_specific_calibration.native_sensor_resolution_height));
            sb.append(String.format(CALIB_FMT_STRINGS[8], this.aux_cam_specific_calibration.calibration_sensor_resolution_width));
            sb.append(String.format(CALIB_FMT_STRINGS[9], this.aux_cam_specific_calibration.calibration_sensor_resolution_height));
            sb.append(String.format(CALIB_FMT_STRINGS[10], this.aux_cam_specific_calibration.focal_length_ratio));

            sb.append(String.format(CALIB_FMT_STRINGS[11], buildCommaSeparatedString(this.relative_rotation_matrix)));
            sb.append(String.format(CALIB_FMT_STRINGS[12], buildCommaSeparatedString(this.relative_geometric_surface_parameters)));

            sb.append(String.format(CALIB_FMT_STRINGS[13], this.relative_principle_point_x_offset));
            sb.append(String.format(CALIB_FMT_STRINGS[14], this.relative_principle_point_y_offset));
            sb.append(String.format(CALIB_FMT_STRINGS[15], this.relative_position_flag));
            sb.append(String.format(CALIB_FMT_STRINGS[16], this.relative_baseline_distance));
            sb.append(String.format(CALIB_FMT_STRINGS[17], this.main_sensor_mirror_and_flip_setting));
            sb.append(String.format(CALIB_FMT_STRINGS[18], this.aux_sensor_mirror_and_flip_setting));
            sb.append(String.format(CALIB_FMT_STRINGS[19], this.module_orientation_during_calibration));
            sb.append(String.format(CALIB_FMT_STRINGS[20], this.rotation_flag));
            sb.append(String.format(CALIB_FMT_STRINGS[21], this.main_cam_specific_calibration.normalized_focal_length));
            sb.append(String.format(CALIB_FMT_STRINGS[22], this.aux_cam_specific_calibration.normalized_focal_length));

            return sb.toString();
        }

        private String buildCommaSeparatedString(float[] array) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%f", array[0]));
            for(int i=1; i<array.length; i++) {
                sb.append(String.format(",%f", array[i]));
            }
            return sb.toString();
        }
    }
}
