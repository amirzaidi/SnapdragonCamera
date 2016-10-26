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

#include <jni.h>
#include <assert.h>
#include <stdlib.h>

#ifdef __ANDROID__
#include "android/log.h"
#define printf(...) __android_log_print( ANDROID_LOG_ERROR, "ImageUtil", __VA_ARGS__ )
#endif

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jint JNICALL Java_com_android_camera_imageprocessor_FrameProcessor_nativeRotateNV21
        (JNIEnv* env, jobject thiz, jobjectArray inBuf,
         jint imageWidth, jint imageHeight, jint degree, jobjectArray outBuf);
JNIEXPORT jint JNICALL Java_com_android_camera_imageprocessor_FrameProcessor_nativeNV21toRgb(
        JNIEnv *env, jobject thiz, jobjectArray yvuBuf, jobjectArray rgbBuf, jint width, jint height, jint stride);
JNIEXPORT jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeFlipNV21(
        JNIEnv* env, jobject thiz, jbyteArray yvuBytes, jint stride, jint height, jint gap, jboolean isVertical);
JNIEXPORT jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeResizeImage(
        JNIEnv* env, jobject thiz, jbyteArray oldBuf, jbyteArray newBuf, jint oldWidth, jint oldHeight, jint oldStride, jint newWidth, jint newHeight);
JNIEXPORT jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeNV21Split(
        JNIEnv* env, jobject thiz, jbyteArray srcYVU, jobjectArray yBuf, jobjectArray vuBuf, jint width, jint height, jint srcStride, jint dstStride);
#ifdef __cplusplus
}
#endif

typedef unsigned char uint8_t;

void rotateBufAndMerge(uint8_t *in_buf, jint imageWidth, jint imageHeight, jint degree, uint8_t *out_buf)
{
    if(degree == 90) {
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                int offset = y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
            }
        }
        i = imageWidth * imageHeight;
        for (int x = 0; x < imageWidth; x += 2) {
            for (int y = imageHeight / 2 - 1; y >= 0; y--) {
                int offset = imageWidth*imageHeight + y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
                out_buf[i] = in_buf[offset + 1];
                i++;
            }
        }
    } else if(degree == 270) {
        int i = 0;
        for (int x = imageWidth - 1; x >= 0; x--) {
            for (int y = 0; y < imageHeight; y++) {
                int offset = y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
            }
        }
        i = imageWidth * imageHeight;
        for (int x = imageWidth - 2; x >= 0; x-=2) {
            for (int y = 0; y < imageHeight/2; y++) {
                int offset = imageWidth*imageHeight + y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
                out_buf[i] = in_buf[offset + 1];
                i++;
            }
        }
    } else if(degree == 180) {
        int i = 0;
        for (int y = imageHeight - 1; y >= 0; y--) {
            for (int x = imageWidth - 1; x >= 0 ; x--) {
                int offset = y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
            }
        }
        i = imageWidth * imageHeight;
        for (int y = imageHeight/2 - 1; y >= 0; y--) {
            for (int x = imageWidth - 2; x >= 0 ; x-=2) {
                int offset = imageWidth*imageHeight + y * imageWidth + x;
                out_buf[i] = in_buf[offset];
                i++;
                out_buf[i] = in_buf[offset + 1];
                i++;
            }
        }
    }
}

jint JNICALL Java_com_android_camera_imageprocessor_FrameProcessor_nativeRotateNV21(
        JNIEnv* env, jobject thiz, jobjectArray inBuf,
        jint imageWidth, jint imageHeight, jint degree, jobjectArray outBuf)
{
    uint8_t *in_buf = (uint8_t *)env->GetDirectBufferAddress(inBuf);
    uint8_t *out_buf = (uint8_t *)env->GetDirectBufferAddress(outBuf);
    rotateBufAndMerge(in_buf, imageWidth, imageHeight, degree, out_buf);

    return 0;
}

jint JNICALL Java_com_android_camera_imageprocessor_FrameProcessor_nativeNV21toRgb(
        JNIEnv* env, jobject thiz, jobjectArray yvuBuf, jobjectArray rgbBuf, jint width, jint height)
{
    uint8_t *in_buf = (uint8_t *)env->GetDirectBufferAddress(yvuBuf);
    uint8_t *rgb_buf = (uint8_t *)env->GetDirectBufferAddress(rgbBuf);
    int ysize = width * height;
    int y_value;
    int i, v, u, r, g, b;
    for(int x=0; x < width; x++) {
        for(int y=0; y < height; y++) {
            y_value = (in_buf[y*width+x] & 0xFF);
            i = ysize + (x/2*2) + ((y/2) * width);
            v = (in_buf[i] & 0xFF) - 128;
            u = (in_buf[i + 1] & 0xFF) - 128;
            r = (int)(1.164f * y_value + 1.596f * v);
            g = (int)(1.164f * y_value - 0.813f * v - 0.391f * u);
            b = (int)(1.164f * y_value + 2.018f * u);
            r = r > 255 ? 255 : r < 0 ? 0 : r;
            g = g > 255 ? 255 : g < 0 ? 0 : g;
            b = b > 255 ? 255 : b < 0 ? 0 : b;
            rgb_buf[(y*width + x) * 4 + 3] = (uint8_t)(0xFF);
            rgb_buf[(y*width + x) * 4 + 2] = (uint8_t)(b & 0xFF);
            rgb_buf[(y*width + x) * 4 + 1] = (uint8_t)(g & 0xFF);
            rgb_buf[(y*width + x) * 4 + 0] = (uint8_t)(r & 0xFF);
        }
    }
    return 0;
}

jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeFlipNV21(
        JNIEnv* env, jobject thiz, jbyteArray yvuBytes, jint stride, jint height, jint gap, jboolean isVertical)
{
    jbyte* imageDataNV21Array = env->GetByteArrayElements(yvuBytes, NULL);
    uint8_t *buf = (uint8_t *)imageDataNV21Array;
    int ysize = stride * height;
    uint8_t temp1, temp2;

    if(isVertical) {
        for (int x = 0; x < stride; x++) {
            for (int y = 0; y < height / 2; y++) {
                temp1 = buf[y * stride + x];
                buf[y * stride + x] = buf[(height - 1 - y) * stride + x];
                buf[(height - 1 - y) * stride + x] = temp1;
            }
        }
        for (int x = 0; x < stride; x += 2) {
            for (int y = 0; y < height / 4; y++) {
                temp1 = buf[ysize + y * stride + x];
                temp2 = buf[ysize + y * stride + x + 1];
                buf[ysize + y * stride + x] = buf[ysize + (height / 2 - 1 - y) * stride + x];
                buf[ysize + y * stride + x + 1] = buf[ysize + (height / 2 - 1 - y) * stride + x + 1];
                buf[ysize + (height / 2 - 1 - y) * stride + x] = temp1;
                buf[ysize + (height / 2 - 1 - y) * stride + x + 1] = temp2;
            }
        }
    } else {
        int width = stride - gap;
        for (int x = 0; x < width/2; x++) {
            for (int y = 0; y < height; y++) {
                temp1 = buf[y * stride + x];
                buf[y * stride + x] = buf[y * stride + (width - 1 - x)];
                buf[y * stride + (width - 1 - x)] = temp1;
            }
        }
        for (int x = 0; x < width/2; x += 2) {
            for (int y = 0; y < height / 2; y++) {
                temp1 = buf[ysize + y * stride + x];
                temp2 = buf[ysize + y * stride + x + 1];
                buf[ysize + y * stride + x] = buf[ysize + y * stride + (width - 1 - x - 1)];
                buf[ysize + y * stride + x + 1] = buf[ysize + y * stride + (width - 1 - x)];
                buf[ysize + y * stride + (width - 1 - x - 1)] = temp1;
                buf[ysize + y * stride + (width - 1 - x)] = temp2;
            }
        }
    }

    env->ReleaseByteArrayElements(yvuBytes, imageDataNV21Array, JNI_ABORT);
    return 0;
}

jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeNV21Split(
        JNIEnv* env, jobject thiz, jbyteArray srcYVU, jobjectArray yBuf, jobjectArray vuBuf, jint width, jint height, jint srcStride, jint dstStride) {
    uint8_t *old_buf = (uint8_t *) env->GetByteArrayElements(srcYVU, NULL);
    uint8_t *y_buf = (uint8_t *)env->GetDirectBufferAddress(yBuf);
    uint8_t *vu_buf = (uint8_t *)env->GetDirectBufferAddress(vuBuf);
    int ySize = srcStride*height;

    for(int j=0; j < height; j++) {
        for (int i = 0; i < width; i++) {
            y_buf[j*dstStride+i] = old_buf[j*srcStride + i];
            if (j < height / 2) {
                vu_buf[j*dstStride + i] = old_buf[ySize + j*srcStride + i];
            }
        }
    }
    env->ReleaseByteArrayElements(srcYVU, (jbyte *)old_buf, JNI_ABORT);

    return 0;
}

jint JNICALL Java_com_android_camera_imageprocessor_PostProcessor_nativeResizeImage(
        JNIEnv* env, jobject thiz, jbyteArray oldBuf, jbyteArray newBuf, jint oldWidth, jint oldHeight, jint oldStride, jint newWidth, jint newHeight) {
    uint8_t *old_buf = (uint8_t *) env->GetByteArrayElements(oldBuf, NULL);
    uint8_t *new_buf = (uint8_t *) env->GetByteArrayElements(newBuf, NULL);
    int adjustedOldWidth = oldWidth;

    if((float)oldWidth/oldHeight != (float)newWidth/newHeight) {
        adjustedOldWidth = (int)(((float)newWidth/newHeight) * oldHeight);
    }

    int wR = adjustedOldWidth / newWidth;
    int hR = oldHeight / newHeight;
    if(wR < hR && adjustedOldWidth - newWidth*wR >= adjustedOldWidth/4) {
        wR++;
    }
    if(hR < wR && oldHeight - newHeight*hR >= oldHeight/4) {
        hR++;
    }
    int R = wR < hR ? wR : hR;
    int wC = oldWidth - (newWidth*R);
    int hC = oldHeight - (newHeight*R);
    unsigned int cv1, cv2;

    int index = 0;
    for(int j=hC/2; j < newHeight*R + hC/2; j+=R) {
        for(int i=wC/2; i < newWidth*R + wC/2; i+=R) {
            cv1 = 0;
            for(int y = 0; y < R; y++) {
                for (int x = 0; x < R; x++) {
                    cv1 += old_buf[(j+y)*oldStride + i+x];
                }
            }
            cv1 /= R*R;
            new_buf[index] = (unsigned char)cv1;
            index++;
        }
    }
    int ySize = oldStride*oldHeight;
    index = newWidth*newHeight;
    for(int j=hC/2; j < newHeight*R + hC/2; j+=R*2) {
        for(int i=wC/2; i < newWidth*R + wC/2; i+=R*2) {
            cv1 = 0;
            cv2 = 0;
            for(int y = 0; y < R*2; y+=2) {
                for (int x = 0; x < R*2; x+=2) {
                    cv1 += old_buf[ySize + (j+y)/2*oldStride + (i+x)/2*2];
                    cv2 += old_buf[ySize + (j+y)/2*oldStride + (i+x)/2*2 + 1];
                }
            }
            cv1 /= R*R;
            cv2 /= R*R;
            new_buf[index] = (unsigned char)cv1;
            index++;
            new_buf[index] = (unsigned char)cv2;
            index++;
        }
    }
    env->ReleaseByteArrayElements(oldBuf, (jbyte *)old_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(newBuf, (jbyte *)new_buf, JNI_ABORT);

    return R;
}