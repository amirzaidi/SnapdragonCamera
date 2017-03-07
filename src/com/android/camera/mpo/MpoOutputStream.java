/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a contribution/
 *
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera.mpo;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import android.util.Log;

import com.android.camera.exif.JpegHeader;
import com.android.camera.exif.OrderedDataOutputStream;
import com.android.camera.mpo.MpoTag.MpEntry;
import com.android.camera.util.PersistUtil;

class MpoOutputStream extends FilterOutputStream {
    private static final String TAG = "MpoOutputStream";
    private static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);
    private static final int STREAMBUFFER_SIZE = 0x00010000; // 64Kb

    private static final int STATE_SOI = 0;
    private static final int STATE_FRAME_HEADER = 1;
    private static final int STATE_SKIP_CROP = 2;
    private static final int STATE_JPEG_DATA = 3;

    private static final short TIFF_HEADER = 0x002A;
    private static final short TIFF_BIG_ENDIAN = 0x4d4d;
    private static final short TIFF_LITTLE_ENDIAN = 0x4949;
    private static final int MAX_EXIF_SIZE = 65535;

    private static final String DC_CROP_INFO = "Qualcomm Dual Camera Attributes";
    private static final int DC_CROP_INFO_BYTE_SIZE = DC_CROP_INFO.length();

    private MpoData mMpoData;
    private MpoImageData mCurrentImageData;
    private int mState = STATE_SOI;
    private int mByteToSkip;
    private int mByteToCopy;
    private byte[] mSingleByteArray = new byte[1];
    private ByteBuffer mBuffer = ByteBuffer.allocate(4);
    private ByteBuffer mCropInfo = ByteBuffer.allocate(DC_CROP_INFO_BYTE_SIZE);
    private int mMpoOffsetStart = -1;
    private int mSize = 0;
    private boolean mSkipCropData = false;

    protected MpoOutputStream(OutputStream ou) {
        super(new BufferedOutputStream(ou, STREAMBUFFER_SIZE));
    }

    /**
     * Sets the ExifData to be written into the JPEG file. Should be called
     * before writing image data.
     */
    protected void setMpoData(MpoData mpoData) {
        mMpoData = mpoData;
        mMpoData.updateAllTags();
    }

    private void resetStates() {
        mState = STATE_SOI;
        mByteToSkip = 0;
        mByteToCopy = 0;
        mBuffer.rewind();
    }

    private int requestByteToBuffer(ByteBuffer buffer, int requestByteCount, byte[] data, int offset, int length) {
        int byteNeeded = requestByteCount - buffer.position();
        int byteToRead = length > byteNeeded ? byteNeeded : length;
        buffer.put(data, offset, byteToRead);
        return byteToRead;
    }

    private boolean isDualCamCropInfo() {
        // first check length
        if(mCropInfo.position() != DC_CROP_INFO_BYTE_SIZE) {
            return false;
        }

        mCropInfo.rewind();
        for(int i = 0; i < DC_CROP_INFO.length(); i++) {
            char c = (char)mCropInfo.get(i);
            //Log.d(TAG, "mCropInfo char @ " + (i) + ": " + c);
            if(DC_CROP_INFO.charAt(i) != c)
                return false;
        }

        return true;
    }

    void writeMpoFile() throws IOException {
        // check and write primary image
        mCurrentImageData = mMpoData.getPrimaryMpoImage();
        // don't skip if primary == bayer
        if(mMpoData.getAuxiliaryImageCount() > 1) {
            mSkipCropData = true;
        }
        write(mCurrentImageData.getJpegData());
        flush();

        mSkipCropData = false;
        // check and write auxiliary images
        for (MpoImageData image : mMpoData.getAuxiliaryMpoImages()) {
            resetStates();
            mCurrentImageData = image;
            write(mCurrentImageData.getJpegData());
            flush();
        }
    }

    /**
     * Writes the image out. The input data should be a valid JPEG format. After
     * writing, it's Exif header will be replaced by the given header.
     */
    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        while ((mByteToSkip > 0 || mByteToCopy > 0 || mState != STATE_JPEG_DATA) && length > 0) {
            if (mByteToSkip > 0) {
                int byteToProcess = length > mByteToSkip ? mByteToSkip : length;
                length -= byteToProcess;
                mByteToSkip -= byteToProcess;
                offset += byteToProcess;
            }
            if (mByteToCopy > 0) {
                int byteToProcess = length > mByteToCopy ? mByteToCopy : length;
                out.write(buffer, offset, byteToProcess);
                mSize += byteToProcess;
                length -= byteToProcess;
                mByteToCopy -= byteToProcess;
                offset += byteToProcess;
            }
            if (length == 0) {
                return;
            }
            switch (mState) {
            case STATE_SOI:
                int byteRead = requestByteToBuffer(mBuffer, 2, buffer, offset, length);
                offset += byteRead;
                length -= byteRead;
                if (mBuffer.position() < 2) {
                    return;
                }
                mBuffer.rewind();
                if (mBuffer.getShort() != JpegHeader.SOI) {
                    throw new IOException("Not a valid jpeg image, cannot write exif");
                }
                out.write(mBuffer.array(), 0, 2);
                mSize += 2;
                mState = STATE_FRAME_HEADER;
                mBuffer.rewind();
                break;
            case STATE_FRAME_HEADER:
                // Copy APP0 and APP1 if it exists
                // Insert MPO data
                byteRead = requestByteToBuffer(mBuffer, 4, buffer, offset, length);
                // Check if this image data doesn't contain SOF.
                if (mBuffer.position() == 2) {
                    short tag = mBuffer.getShort();
                    if (tag == JpegHeader.EOI) {
                        out.write(mBuffer.array(), 0, 2);
                        mSize += 2;
                        mBuffer.rewind();
                    }
                }
                if (mBuffer.position() < 4) {
                    return;
                }
                mBuffer.rewind();
                short marker = mBuffer.getShort();
                if (marker == JpegHeader.APP1 || marker == JpegHeader.APP0) {
                    out.write(mBuffer.array(), 0, 4);
                    mSize += 4;
                    mByteToCopy = (mBuffer.getShort() & 0x0000ffff) - 2;
                    offset += byteRead;
                    length -= byteRead;
                } else {
                    writeMpoData();
                    if(mSkipCropData)
                        mState = STATE_SKIP_CROP;
                    else
                        mState = STATE_JPEG_DATA;
                }
                mBuffer.rewind();
                break;
            case STATE_SKIP_CROP:
                byteRead = requestByteToBuffer(mBuffer, 4, buffer, offset, length);
                // Check if this image data doesn't contain SOF.
                if (mBuffer.position() == 2) {
                    short tag = mBuffer.getShort();
                    if (tag == JpegHeader.EOI) {
                        out.write(mBuffer.array(), 0, 2);
                        mSize += 2;
                        mBuffer.rewind();
                    }
                }
                if (mBuffer.position() < 4) {
                    return;
                }

                offset += byteRead;
                length -= byteRead;
                mBuffer.rewind();

                marker = mBuffer.getShort();
                if (!JpegHeader.isSofMarker(marker)) {
                    // if not SOF, read first 31 bytes
                    // try to match dual cam crop magic string
                    byteRead = requestByteToBuffer(mCropInfo, DC_CROP_INFO_BYTE_SIZE, buffer, offset, length);
                    if(isDualCamCropInfo()) {
                        // if crop info, clear with 0
                        out.write(mBuffer.array(), 0, 4);
                        mSize += 4;

                        int sizeToClear = mByteToSkip = (mBuffer.getShort() & 0x0000ffff) - 2;
                        while(sizeToClear > 0) {
                            out.write(0);
                            mSize++;
                            sizeToClear--;
                        }
                        mState = STATE_JPEG_DATA;
                    } else {
                        // else copy this block
                        // and move on to next header
                        out.write(mBuffer.array(), 0, 4);
                        mSize += 4;
                        mByteToCopy = (mBuffer.getShort() & 0x0000ffff) - 2;
                    }
                    mCropInfo.rewind();
                } else {
                    // SOF is reached, no crop info detected, skip
                    out.write(mBuffer.array(), 0, 4);
                    mSize += 4;
                    mState = STATE_JPEG_DATA;
                }
                mBuffer.rewind();
                break;
            }
        }
        if (length > 0) {
            out.write(buffer, offset, length);
            mSize += length;
        }
    }

    /**
     * Writes the one bytes out. The input data should be a valid JPEG format.
     * After writing, it's Exif header will be replaced by the given header.
     */
    @Override
    public void write(int oneByte) throws IOException {
        mSingleByteArray[0] = (byte) (0xff & oneByte);
        write(mSingleByteArray);
    }

    /**
     * Equivalent to calling write(buffer, 0, buffer.length).
     */
    @Override
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    private void writeMpoData() throws IOException {
        if (mMpoData == null) {
            return;
        }
        if (DEBUG) {
            Log.v(TAG, "Writing mpo data...");
        }
        int exifSize = mCurrentImageData.calculateAllIfdOffsets() + MpoImageData.APP_HEADER_SIZE;
        if (exifSize > MAX_EXIF_SIZE) {
            throw new IOException("Exif header is too large (>64Kb)");
        }
        OrderedDataOutputStream dataOutputStream = new OrderedDataOutputStream(out);
        dataOutputStream.setByteOrder(ByteOrder.BIG_ENDIAN);
        dataOutputStream.writeShort(JpegHeader.APP2);
        dataOutputStream.writeShort((short) (exifSize));
        dataOutputStream.writeInt(MpoImageData.MP_FORMAT_IDENTIFIER);
        if (mMpoOffsetStart == -1) {
            mMpoOffsetStart = mSize + dataOutputStream.size();
        }
        if (mCurrentImageData.getByteOrder() == ByteOrder.BIG_ENDIAN) {
            dataOutputStream.writeShort(TIFF_BIG_ENDIAN);
        } else {
            dataOutputStream.writeShort(TIFF_LITTLE_ENDIAN);
        }
        dataOutputStream.setByteOrder(mCurrentImageData.getByteOrder());
        dataOutputStream.writeShort(TIFF_HEADER);
        if (exifSize > MpoImageData.MP_HEADER_SIZE + MpoImageData.APP_HEADER_SIZE) {
            dataOutputStream.writeInt(MpoImageData.OFFSET_TO_FIRST_IFD);
            writeAllTags(dataOutputStream);
        } else
            dataOutputStream.writeInt(0);

        mSize += dataOutputStream.size();
    }

    private void updateIndexIfdOffsets(MpoIfdData indexIfd, int mpoOffset) {
        // update offsets
        MpoTag mpEntryTag = mMpoData.getPrimaryMpoImage().getTag((short) MpoInterface.TAG_MP_ENTRY,
                MpoIfdData.TYPE_MP_INDEX_IFD);
        List<MpEntry> mpEntries = mpEntryTag.getMpEntryValue();
        for (int i = 1; i < mpEntries.size(); i++) { // primary offset is always
                                                     // 0
            MpEntry entry = mpEntries.get(i);
            entry.setImageOffset(entry.getImageOffset() - mpoOffset);
        }

        mpEntryTag.setValue(mpEntries);
    }

    private void writeAllTags(OrderedDataOutputStream dataOutputStream) throws IOException {
        MpoIfdData indexIfd = mCurrentImageData.getIndexIfdData();
        if (indexIfd.getTagCount() > 0) {
            updateIndexIfdOffsets(indexIfd, mMpoOffsetStart);
            writeIfd(indexIfd, dataOutputStream);
        }

        MpoIfdData attribIfd = mCurrentImageData.getAttribIfdData();
        if (attribIfd.getTagCount() > 0)
            writeIfd(attribIfd, dataOutputStream);
    }

    private void writeIfd(MpoIfdData ifd, OrderedDataOutputStream dataOutputStream)
            throws IOException {
        MpoTag[] tags = ifd.getAllTags();
        dataOutputStream.writeShort((short) tags.length);
        for (MpoTag tag : tags) {
            dataOutputStream.writeShort(tag.getTagId());
            dataOutputStream.writeShort(tag.getDataType());
            dataOutputStream.writeInt(tag.getComponentCount());
            if (DEBUG) {
                Log.v(TAG, "\n" + tag.toString());
            }
            if (tag.getDataSize() > 4) {
                dataOutputStream.writeInt(tag.getOffset());
            } else {
                MpoOutputStream.writeTagValue(tag, dataOutputStream);
                for (int i = 0, n = 4 - tag.getDataSize(); i < n; i++) {
                    dataOutputStream.write(0);
                }
            }
        }
        dataOutputStream.writeInt(ifd.getOffsetToNextIfd());
        for (MpoTag tag : tags) {
            if (tag.getDataSize() > 4) {
                MpoOutputStream.writeTagValue(tag, dataOutputStream);
            }
        }
    }

    static void writeTagValue(MpoTag tag, OrderedDataOutputStream dataOutputStream)
            throws IOException {
        switch (tag.getDataType()) {
        case MpoTag.TYPE_ASCII:
            byte buf[] = tag.getStringByte();
            if (buf.length == tag.getComponentCount()) {
                buf[buf.length - 1] = 0;
                dataOutputStream.write(buf);
            } else {
                dataOutputStream.write(buf);
                dataOutputStream.write(0);
            }
            break;
        case MpoTag.TYPE_LONG:
        case MpoTag.TYPE_UNSIGNED_LONG:
            for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                dataOutputStream.writeInt((int) tag.getValueAt(i));
            }
            break;
        case MpoTag.TYPE_RATIONAL:
        case MpoTag.TYPE_UNSIGNED_RATIONAL:
            for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                dataOutputStream.writeRational(tag.getRational(i));
            }
            break;
        case MpoTag.TYPE_UNDEFINED:
        case MpoTag.TYPE_UNSIGNED_BYTE:
            buf = new byte[tag.getComponentCount()];
            tag.getBytes(buf);
            dataOutputStream.write(buf);
            break;
        case MpoTag.TYPE_UNSIGNED_SHORT:
            for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                dataOutputStream.writeShort((short) tag.getValueAt(i));
            }
            break;
        }
    }

    int size() {
        return mSize;
    }
}
