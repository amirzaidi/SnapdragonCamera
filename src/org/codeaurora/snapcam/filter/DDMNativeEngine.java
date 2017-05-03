/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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

import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.util.Log;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import org.codeaurora.snapcam.filter.ClearSightNativeEngine.CamSystemCalibrationData;

public class DDMNativeEngine {
    private static final String TAG = "DDMNativeEngine";
    static {
        try {//load jni_dualcamera
            System.loadLibrary("jni_dualcamera");
            mLibLoaded = true;
            Log.v(TAG, "successfully loaded jni_dualcamera lib");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "failed to load jni_dualcamera lib");
            Log.e(TAG, e.toString());
            e.printStackTrace();
            mLibLoaded = false;
        }

    }

    private CaptureResult.Key<byte[]> SCALE_CROP_ROTATION_REPROCESS_BLOB =
            new CaptureResult.Key<byte[]>(
                    "org.codeaurora.qcamera3.hal_private_data.reprocess_data_blob",
                    byte[].class);

    private static boolean mLibLoaded;
    private Image mBayerImage;
    private Image mMonoImage;
    private ByteBuffer mPrimaryY;
    private ByteBuffer mPrivaryVU;
    CamReprocessInfo mBayerCamReprocessInfo;
    CamReprocessInfo mMonoCamReprocessInfo;
    CamSystemCalibrationData mCamSystemCalibrationData;
    private float mLensFocusDistance;
    private static final int Y_PLANE = 0;
    private static final int VU_PLANE = 2;

    public boolean getDepthMapSize(int[] depthMap){
        return nativeGetDepthMapSize(mBayerImage.getWidth(), mBayerImage.getHeight(), depthMap);
    }

    public void setCamSystemCalibrationData(CamSystemCalibrationData otpCalibration){
        mCamSystemCalibrationData = otpCalibration;
    }

    public String getOTPCalibration() {
        return mCamSystemCalibrationData.toString();
    }

    public void reset() {
        mBayerImage = null;
        mMonoImage  = null;
        mBayerCamReprocessInfo = null;
        mMonoCamReprocessInfo = null;
        mLensFocusDistance = 0;
    }
    public boolean isReadyForGenerateDepth(){
        return mBayerImage != null && mMonoImage != null
                && mBayerCamReprocessInfo != null && mMonoCamReprocessInfo != null;
    }

    public void setBayerLensFocusDistance(float lensFocusDistance) {
        mLensFocusDistance = lensFocusDistance;
    }
    public void setBayerImage(Image image){
        mBayerImage = image;
    }

    public void setMonoImage(Image image) {
        mMonoImage = image;
    }

    public void setBayerReprocessResult(CaptureResult result ){
        byte[] bytes = result.get(SCALE_CROP_ROTATION_REPROCESS_BLOB);
        mBayerCamReprocessInfo = CamReprocessInfo.createCamReprocessFromBytes(bytes);
    }

    public String getBayerScaleCrop() {
        return mBayerCamReprocessInfo.toString();
    }

    public void setMonoReprocessResult(CaptureResult result) {
        byte[] bytes = result.get(SCALE_CROP_ROTATION_REPROCESS_BLOB);
        mMonoCamReprocessInfo = CamReprocessInfo.createCamReprocessFromBytes(bytes);
    }

    public String getMonoScaleCrop(){
        return mMonoCamReprocessInfo.toString();
    }

    public boolean dualCameraGenerateDDM(byte[] depthMapBuffer, int depthMapStride, Rect roiRect) {
        if ( mLensFocusDistance == 0 ){
            Log.e(TAG, " dualCameraGenerateDDM error: mLensFocusDistance is 0");
            return false;
        }

        if (mBayerImage == null || mMonoImage == null ) {
            Log.e(TAG, "mBayerImage=" +(mBayerImage == null)+ " mMonoImage=" + (mMonoImage == null));
            return false;
        }

        if ( depthMapBuffer == null ) {
            Log.e(TAG, "depthMapBuffer can't be null");
            return false;
        }

        if ( mMonoCamReprocessInfo== null
                || mBayerCamReprocessInfo == null
                || mCamSystemCalibrationData == null ) {
            Log.e(TAG, "mMonoCamReprocessInfo== null:" +(mMonoCamReprocessInfo== null)
                    + " mBayerCamReprocessInfo == null:" +(mBayerCamReprocessInfo == null)
                    + " mCamSystemCalibrationData == null:" +(mCamSystemCalibrationData == null));
            return false;
        }

        Plane[] bayerPlanes = mBayerImage.getPlanes();
        Plane[] monoPlanes = mMonoImage.getPlanes();
        int[] goodRoi = new int[4];
        boolean result =  nativeDualCameraGenerateDDM(
                bayerPlanes[Y_PLANE].getBuffer(),
                bayerPlanes[VU_PLANE].getBuffer(),
                mBayerImage.getWidth(),
                mBayerImage.getHeight(),
                bayerPlanes[Y_PLANE].getRowStride(),
                bayerPlanes[VU_PLANE].getRowStride(),

                monoPlanes[Y_PLANE].getBuffer(),
                monoPlanes[VU_PLANE].getBuffer(),
                mMonoImage.getWidth(),
                mMonoImage.getHeight(),
                monoPlanes[Y_PLANE].getRowStride(),
                monoPlanes[VU_PLANE].getRowStride(),

                depthMapBuffer,
                depthMapStride,

                goodRoi,

                mBayerCamReprocessInfo.toString(),
                mMonoCamReprocessInfo.toString(),
                mCamSystemCalibrationData.toString(),
                mLensFocusDistance,
                true);
        roiRect.left = goodRoi[0];
        roiRect.top = goodRoi[1];
        roiRect.right  = goodRoi[0] + goodRoi[2];
        roiRect.bottom = goodRoi[1] + goodRoi[3];

        return result;
    }



    private native boolean nativeGetDepthMapSize(int primaryWidth, int primaryHeight,int[] size);

    private native boolean nativeDualCameraGenerateDDM(
            ByteBuffer primaryY,
            ByteBuffer primaryVU,
            int primaryWidth,
            int primaryHeight,
            int primaryStrideY,
            int primaryStrideVU,

            ByteBuffer auxiliaryY,
            ByteBuffer auxiliaryVU,
            int auxiliaryWidth,
            int auxiliaryHeight,
            int auxiliaryStrideY,
            int auxiliaryStrideVU,

            byte[] outDst,
            int dstStride,

            int[] roiRect,

            String scaleCropRotationDataPrimaryCamera,
            String scaleCropRotationDataAuxiliaryCamera,
            String otpCalibration,
            float focalLengthPrimaryCamera,
            boolean isAuxiliaryMonoSensor);

    public static class DepthMap{
        private int width;
        private int height;
        private ByteBuffer buffer;
        private int stride;
        private Rect roi;
    }
   public static class CamStreamCropInfo{
        int stream_id;
        Rect crop;
        Rect roi_map;
        int user_zoom;
        int stream_zoom;
        float scale_ratio;

       private CamStreamCropInfo(){}

       public static CamStreamCropInfo createFromBytes(byte[] bytes) {
           ByteBuffer buffer = ByteBuffer.wrap(bytes);
           buffer.order(ByteOrder.LITTLE_ENDIAN);
            return createFromByteBuffer(buffer);
       }

       public static CamStreamCropInfo createFromByteBuffer(ByteBuffer buffer) {
           CamStreamCropInfo camStreamCropInfo = new CamStreamCropInfo();
           camStreamCropInfo.stream_id = buffer.getInt();
           Rect crop = new Rect();
           crop.left = buffer.getInt();
           crop.top = buffer.getInt();
           crop.right = crop.left + buffer.getInt();
           crop.bottom = crop.top + buffer.getInt();
           camStreamCropInfo.crop = crop;

           Rect roi_map = new Rect();
           roi_map.left = buffer.getInt();
           roi_map.top = buffer.getInt();
           roi_map.right = roi_map.left + buffer.getInt();
           roi_map.bottom = roi_map.top + buffer.getInt();
           camStreamCropInfo.roi_map = roi_map;

           camStreamCropInfo.user_zoom = buffer.getInt();
           camStreamCropInfo.stream_zoom = buffer.getInt();
           camStreamCropInfo.scale_ratio = buffer.getFloat();

           return camStreamCropInfo;
       }
    }

    public static class CamRotationInfo {
        int jpeg_rotation;
        int device_rotation;
        int stream_id;
        private CamRotationInfo(){}

        public static CamRotationInfo createCamReprocessFromBytes(byte[] bytes) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return createFromByteBuffer(buf);
        }
        public static CamRotationInfo createFromByteBuffer(ByteBuffer buffer) {
            CamRotationInfo rotation_info = new CamRotationInfo();
            rotation_info.jpeg_rotation = buffer.getInt();
            rotation_info.device_rotation = buffer.getInt();
            rotation_info.stream_id = buffer.getInt();
            return rotation_info;
        }
    }
    public static class CamReprocessInfo{
        CamStreamCropInfo sensor_crop_info;
        CamStreamCropInfo camif_crop_info;
        CamStreamCropInfo isp_crop_info;
        CamStreamCropInfo cpp_crop_info;
        float af_focal_length_ratio;
        int pipeline_flip;
        CamRotationInfo rotation_info;

        private final String SCALE_CROP_ROTATION_FORMAT_STRING[] = {
                "Sensor Crop left = %d\n",
                "Sensor Crop top = %d\n",
                "Sensor Crop width = %d\n",
                "Sensor Crop height = %d\n",
                "Sensor ROI Map left = %d\n",
                "Sensor ROI Map top = %d\n",
                "Sensor ROI Map width = %d\n",
                "Sensor ROI Map height = %d\n",
                "CAMIF Crop left = %d\n",
                "CAMIF Crop top = %d\n",
                "CAMIF Crop width = %d\n",
                "CAMIF Crop height = %d\n",
                "CAMIF ROI Map left = %d\n",
                "CAMIF ROI Map top = %d\n",
                "CAMIF ROI Map width = %d\n",
                "CAMIF ROI Map height = %d\n",
                "ISP Crop left = %d\n",
                "ISP Crop top = %d\n",
                "ISP Crop width = %d\n",
                "ISP Crop height = %d\n",
                "ISP ROI Map left = %d\n",
                "ISP ROI Map top = %d\n",
                "ISP ROI Map width = %d\n",
                "ISP ROI Map height = %d\n",
                "CPP Crop left = %d\n",
                "CPP Crop top = %d\n",
                "CPP Crop width = %d\n",
                "CPP Crop height = %d\n",
                "CPP ROI Map left = %d\n",
                "CPP ROI Map top = %d\n",
                "CPP ROI Map width = %d\n",
                "CPP ROI Map height = %d\n",
                "Focal length Ratio = %f\n",
                "Current pipeline mirror flip setting = %d\n",
                "Current pipeline rotation setting = %d\n"
        };

        public static CamReprocessInfo createCamReprocessFromBytes(byte[] bytes){
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return createCamReprocessFromBytes(buf);
        }
        public static CamReprocessInfo createCamReprocessFromBytes(ByteBuffer buffer){
            CamReprocessInfo  scaleCropRotation = new CamReprocessInfo();
            scaleCropRotation.sensor_crop_info = CamStreamCropInfo.createFromByteBuffer(buffer);
            scaleCropRotation.camif_crop_info = CamStreamCropInfo.createFromByteBuffer(buffer);
            scaleCropRotation.isp_crop_info = CamStreamCropInfo.createFromByteBuffer(buffer);
            scaleCropRotation.cpp_crop_info = CamStreamCropInfo.createFromByteBuffer(buffer);
            scaleCropRotation.af_focal_length_ratio = buffer.getFloat();
            scaleCropRotation.pipeline_flip = buffer.getInt();
            scaleCropRotation.rotation_info = CamRotationInfo.createFromByteBuffer(buffer);
            return scaleCropRotation;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[0], this.sensor_crop_info.crop.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[1], this.sensor_crop_info.crop.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[2], this.sensor_crop_info.crop.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[3], this.sensor_crop_info.crop.height()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[4], this.sensor_crop_info.roi_map.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[5], this.sensor_crop_info.roi_map.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[6], this.sensor_crop_info.roi_map.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[7], this.sensor_crop_info.roi_map.height()));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[8], this.camif_crop_info.crop.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[9], this.camif_crop_info.crop.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[10], this.camif_crop_info.crop.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[11], this.camif_crop_info.crop.height()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[12], this.camif_crop_info.roi_map.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[13], this.camif_crop_info.roi_map.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[14], this.camif_crop_info.roi_map.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[15], this.camif_crop_info.roi_map.height()));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[16], this.isp_crop_info.crop.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[17], this.isp_crop_info.crop.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[18], this.isp_crop_info.crop.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[19], this.isp_crop_info.crop.height()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[20], this.isp_crop_info.roi_map.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[21], this.isp_crop_info.roi_map.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[22], this.isp_crop_info.roi_map.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[23], this.isp_crop_info.roi_map.height()));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[24], this.cpp_crop_info.crop.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[25], this.cpp_crop_info.crop.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[26], this.cpp_crop_info.crop.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[27], this.cpp_crop_info.crop.height()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[28], this.cpp_crop_info.roi_map.left));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[29], this.cpp_crop_info.roi_map.top));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[30], this.cpp_crop_info.roi_map.width()));
            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[31], this.cpp_crop_info.roi_map.height()));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[32], this.af_focal_length_ratio));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[33], this.pipeline_flip));

            sb.append(String.format(SCALE_CROP_ROTATION_FORMAT_STRING[34], this.rotation_info.jpeg_rotation));
            return sb.toString();
        }

    }


}