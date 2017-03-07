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

import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import com.android.camera.util.PersistUtil;

import java.nio.ByteBuffer;
import java.util.List;

public interface ImageFilter {

    public static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);

    /* Return the number of required images to process*/
    List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder);

    String getStringName();

    /* This is used for auto mode burst picture */
    int getNumRequiredImage();

    void init(int width, int height, int strideY, int strideVU);

    /* Free all buffer */
    void deinit();

    /* Adding the image to process */
    void addImage(ByteBuffer bY, ByteBuffer bVU, int imageNum, Object param);

    /* Processing all the added images and return roi*/
    ResultImage processImage();

    boolean isSupported();

    class ResultImage {
        public ByteBuffer outBuffer;
        public Rect outRoi;
        public int width;
        public int height;
        public int stride;

        public ResultImage(ByteBuffer buf, Rect roi, int width, int height, int stride) {
            outBuffer = buf;
            outRoi = roi;
            this.width = width;
            this.height = height;
            this.stride = stride;
        }
    }

    /* Whether it is post proc filter or frame proc filter */
    boolean isFrameListener();

    /* Whether it will use burst capture or manual capture */
    boolean isManualMode();

    /* if it's manual mode, this function has to be implemented */
    void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                       CameraCaptureSession.CaptureCallback callback, Handler handler) throws CameraAccessException;
}
