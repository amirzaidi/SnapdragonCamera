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
package com.android.camera.imageprocessor;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.util.Log;

import com.android.camera.CaptureModule;
import com.android.camera.util.PersistUtil;
import android.os.SystemProperties;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class ZSLQueue {
    private int mCircularBufferSize;
    private ImageItem[] mBuffer;
    private int mImageHead;
    private int mMetaHead;
    private Object mLock = new Object();
    private CaptureModule mModule;
    private static final boolean DEBUG_QUEUE  =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);
    private static final String TAG = "ZSLQueue";

    public ZSLQueue(CaptureModule module) {
        mCircularBufferSize = PersistUtil.getCircularBufferSize();
        synchronized (mLock) {
            mBuffer = new ImageItem[mCircularBufferSize];
            mImageHead = 0;
            mMetaHead = 0;
            mModule = module;
        }
    }

    private int findMeta(long timestamp, int index) {
        int startIndex = index;
        do {
            if(mBuffer[index] != null && mBuffer[index].getMetadata() != null &&
                    mBuffer[index].getMetadata().get(CaptureResult.SENSOR_TIMESTAMP).longValue() == timestamp) {
                return index;
            }
            index = (index + 1) % mBuffer.length;
        } while(index != startIndex);
        return -1;
    }

    private int findImage(long timestamp, int index) {
        int startIndex = index;
        do {
            if(mBuffer[index] != null && mBuffer[index].getImage() != null &&
                    mBuffer[index].getImage().getTimestamp() == timestamp) {
                return index;
            }
            index = (index + 1) % mBuffer.length;
        } while(index != startIndex);
        return -1;
    }

    public void add(Image image, Image rawImage) {
        int lastIndex = -1;
        synchronized (mLock) {
            if(mBuffer == null)
                return;
            if(mBuffer[mImageHead] != null) {
                mBuffer[mImageHead].closeImage();
            } else {
                mBuffer[mImageHead] = new ImageItem();
            }
            if(mBuffer[mImageHead].getMetadata() != null) {
                if((mBuffer[mImageHead].getMetadata().get(CaptureResult.SENSOR_TIMESTAMP)).longValue() == image.getTimestamp()) {
                    mBuffer[mImageHead].setImage(image,rawImage);
                    lastIndex = mImageHead;
                    mImageHead = (mImageHead + 1) % mBuffer.length;
                } else if((mBuffer[mImageHead].getMetadata().get(CaptureResult.SENSOR_TIMESTAMP)).longValue() > image.getTimestamp()) {
                    image.close();
                } else {
                    int i = findMeta(image.getTimestamp(), mImageHead);
                    if(i == -1) {
                        mBuffer[mImageHead].setImage(image, rawImage);
                        mBuffer[mImageHead].setMetadata(null);
                        mImageHead = (mImageHead + 1) % mBuffer.length;
                    } else {
                        lastIndex = mImageHead = i;
                        mBuffer[mImageHead].setImage(image, rawImage);
                        mImageHead = (mImageHead + 1) % mBuffer.length;
                    }
                }
            } else {
                mBuffer[mImageHead].setImage(image, rawImage);
                lastIndex = mImageHead;
                mImageHead = (mImageHead + 1) % mBuffer.length;
            }
        }

        if(DEBUG_QUEUE) Log.d(TAG, "imageIndex: " + lastIndex + " " + image.getTimestamp());
    }

    public void add(TotalCaptureResult metadata) {
        int lastIndex = -1;
        synchronized (mLock) {
            if(mBuffer == null)
                return;
            long timestamp = -1;
            try {
                timestamp = metadata.get(CaptureResult.SENSOR_TIMESTAMP).longValue();
            } catch(IllegalStateException e) {
                //This happens when corresponding image to this metadata is closed and discarded.
                return;
            }
            if(timestamp == -1) {
                return;
            }
            if(mBuffer[mMetaHead] == null) {
                mBuffer[mMetaHead] = new ImageItem();
            } else {
                mBuffer[mMetaHead].closeMeta();
            }
            if(mBuffer[mMetaHead].getImage() != null) {
                if(mBuffer[mMetaHead].getImage().getTimestamp() == timestamp) {
                    mBuffer[mMetaHead].setMetadata(metadata);
                    lastIndex = mMetaHead;
                    mMetaHead = (mMetaHead + 1) % mBuffer.length;
                } else if(mBuffer[mMetaHead].getImage().getTimestamp() > timestamp) {
                    //Disard
                } else {
                    int i = findImage(timestamp, mMetaHead);
                    if(i == -1) {
                        mBuffer[mMetaHead].setImage(null, null);
                        mBuffer[mMetaHead].setMetadata(metadata);
                        mMetaHead = (mMetaHead + 1) % mBuffer.length;
                    } else {
                        lastIndex = mMetaHead = i;
                        mBuffer[mMetaHead].setMetadata(metadata);
                        mMetaHead = (mMetaHead + 1) % mBuffer.length;
                    }
                }
            } else {
                mBuffer[mMetaHead].setMetadata(metadata);
                lastIndex = mImageHead;
                mMetaHead = (mMetaHead + 1) % mBuffer.length;
            }
        }

        if(DEBUG_QUEUE) Log.d(TAG, "Meta: " + lastIndex + " " + metadata.get(CaptureResult.SENSOR_TIMESTAMP));
    }

    public ImageItem tryToGetMatchingItem() {
        synchronized (mLock) {
            int index = mImageHead;
            ImageItem item;
            do {
                item = mBuffer[index];
                if (item != null && item.isValid() && checkImageRequirement(item.getMetadata())) {
                    mBuffer[index] = null;
                    return item;
                }
                index--;
                if (index < 0) index = mBuffer.length - 1;
            } while (index != mImageHead);
        }
        return null;
    }

    public void onClose() {
        synchronized (mLock) {
            for (int i = 0; i < mBuffer.length; i++) {
                if (mBuffer[i] != null) {
                    mBuffer[i].closeImage();
                    mBuffer[i].closeMeta();
                    mBuffer[i] = null;
                }
            }
            mBuffer = null;
            mImageHead = 0;
            mMetaHead = 0;
        }
    }

    private boolean checkImageRequirement(TotalCaptureResult captureResult) {
        if( (captureResult.get(CaptureResult.LENS_STATE) != null &&
             captureResult.get(CaptureResult.LENS_STATE).intValue() == CaptureResult.LENS_STATE_MOVING)
                ||
            (captureResult.get(CaptureResult.CONTROL_AE_STATE) != null &&
                (captureResult.get(CaptureResult.CONTROL_AE_STATE).intValue() == CaptureResult.CONTROL_AE_STATE_SEARCHING ||
                 captureResult.get(CaptureResult.CONTROL_AE_STATE).intValue() == CaptureResult.CONTROL_AE_STATE_PRECAPTURE))
                ||
            (captureResult.get(CaptureResult.CONTROL_AF_STATE) != null) &&
                (captureResult.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ||
                 captureResult.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN)) {
            return false;
        }

        if( captureResult.get(CaptureResult.CONTROL_AE_STATE) != null &&
            captureResult.get(CaptureResult.FLASH_MODE) != null &&
            captureResult.get(CaptureResult.CONTROL_AE_STATE).intValue() == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED &&
            captureResult.get(CaptureResult.FLASH_MODE).intValue() != CaptureResult.FLASH_MODE_OFF) {
            return true;
        }

        if( captureResult.get(CaptureResult.CONTROL_AWB_STATE) != null &&
            captureResult.get(CaptureResult.CONTROL_AWB_STATE).intValue() == CaptureResult.CONTROL_AWB_STATE_SEARCHING ) {
            return false;
        }

        return true;
    }

    static class ImageItem {
        private Image mImage = null;
        private Image mRawImage = null;
        private TotalCaptureResult mMetadata = null;

        public Image getImage() {
            return mImage;
        }

        public Image getRawImage() {return mRawImage;}

        public void setImage(Image image, Image rawImage) {
            if(mImage != null) {
                mImage.close();
            }
            if(mRawImage != null) {
                mRawImage.close();
            }
            mImage = image;
            mRawImage =rawImage;
        }

        public TotalCaptureResult getMetadata() {
            return mMetadata;
        }

        public void setMetadata(TotalCaptureResult metadata) {
            mMetadata = metadata;
        }

        public void closeImage() {
            if(mImage != null) {
                mImage.close();
            }
            if(mRawImage != null) {
                mRawImage.close();
            }
            mImage = null;
        }

        public void closeMeta() {
            mMetadata = null;
        }

        public boolean isValid() {
            if(mImage != null && mMetadata != null) {
                return true;
            }
            return false;
        }
    }
}
