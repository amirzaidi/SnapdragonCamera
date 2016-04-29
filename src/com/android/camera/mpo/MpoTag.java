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

package com.android.camera.mpo;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import com.android.camera.exif.ExifTag;

public class MpoTag extends ExifTag {
    private static final String TAG = "MpoTag";
    static final int TAG_SIZE = 12;

    MpoTag(short tagId, short type, int componentCount, int ifd, boolean hasDefinedComponentCount) {
        super(tagId, type, componentCount, ifd, hasDefinedComponentCount);
    }

    public boolean setValue(List<MpEntry> entries) {
        if (getTagId() != (short) MpoInterface.TAG_MP_ENTRY) {
            return false;
        }

        byte[] bytes = new byte[entries.size() * MpEntry.SIZE];
        for (int i = 0; i < entries.size(); i++) {
            MpEntry entry = entries.get(i);
            entry.getBytes(ByteBuffer.wrap(bytes, i * MpEntry.SIZE, MpEntry.SIZE));
        }
        return setValue(bytes);
    }

    public List<MpEntry> getMpEntryValue() {
        if (getTagId() != (short) MpoInterface.TAG_MP_ENTRY) {
            return null;
        }

        byte[] bytes = getValueAsBytes();
        List<MpEntry> entries = new ArrayList<MpEntry>(bytes.length / MpEntry.SIZE);
        for (int i = 0; i < bytes.length; i += MpEntry.SIZE) {
            entries.add(new MpEntry(ByteBuffer.wrap(bytes, i, MpEntry.SIZE)));
        }
        return entries;
    }

    static class MpEntry {
        static final int SIZE = 16;
        private int mImageAttrib;
        private int mImageSize;
        private int mImageOffset;
        private short mDependantImage1;
        private short mDependantImage2;

        public MpEntry() {
            this(0, 0, 0, (short) 0, (short) 0);
        }

        public MpEntry(int imageAttrib, int imageSize, int imageOffset) {
            this(imageAttrib, imageSize, imageOffset, (short) 0, (short) 0);
        }

        public MpEntry(int imageAttrib, int imageSize, int imageOffset, short dependantImage1,
                short dependantImage2) {
            mImageAttrib = imageAttrib;
            mImageSize = imageSize;
            mImageOffset = imageOffset;
            mDependantImage1 = dependantImage1;
            mDependantImage2 = dependantImage2;
        }

        public MpEntry(ByteBuffer buffer) {
            mImageAttrib = buffer.getInt();
            mImageSize = buffer.getInt();
            mImageOffset = buffer.getInt();
            mDependantImage1 = buffer.getShort();
            mDependantImage2 = buffer.getShort();
        }

        public int getImageAttrib() {
            return mImageAttrib;
        }

        public int getImageSize() {
            return mImageSize;
        }

        public int getImageOffset() {
            return mImageOffset;
        }

        public short getDependantImage1() {
            return mDependantImage1;
        }

        public short getDependantImage2() {
            return mDependantImage2;
        }

        public void setImageAttrib(int imageAttrib) {
            mImageAttrib = imageAttrib;
        }

        public void setImageSize(int imageSize) {
            mImageSize = imageSize;
        }

        public void setImageOffset(int imageOffset) {
            mImageOffset = imageOffset;
        }

        public void setDependantImage1(short depImage1) {
            mDependantImage1 = depImage1;
        }

        public void setDependantImage2(short depImage2) {
            mDependantImage2 = depImage2;
        }

        public boolean getBytes(ByteBuffer buffer) {
            try {
                buffer.putInt(mImageAttrib);
                buffer.putInt(mImageSize);
                buffer.putInt(mImageOffset);
                buffer.putShort(mDependantImage1);
                buffer.putShort(mDependantImage2);
            } catch (BufferOverflowException e) {
                Log.w(TAG, "Buffer size too small");
                return false;
            }

            return true;
        }
    }
}