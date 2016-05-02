/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a contribution.
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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * This class stores the MPO header in IFDs according to the MPO specification.
 */

public class MpoImageData {
    private static final String TAG = "MpoImageData";
    static final int OFFSET_TO_FIRST_IFD = 8;
    static final int MP_FORMAT_IDENTIFIER = 0x4D504600; // 'M' 'P' 'F' 'NULL'
    static final int MP_HEADER_SIZE = 8;
    static final int APP_HEADER_SIZE = 6;

    private final MpoIfdData mMpIndexIfdData = new MpoIfdData(MpoIfdData.TYPE_MP_INDEX_IFD);
    private final MpoIfdData mMpAttribIfdData = new MpoIfdData(MpoIfdData.TYPE_MP_ATTRIB_IFD);
    private final byte[] mJpegData;
    private final ByteOrder mByteOrder;

    public MpoImageData(byte[] jpegData, ByteOrder byteOrder) {
        mJpegData = jpegData;
        mByteOrder = byteOrder;
    }

    /**
     * Gets the jpeg data.
     */
    protected byte[] getJpegData() {
        return mJpegData;
    }

    /**
     * Gets the byte order.
     */
    protected ByteOrder getByteOrder() {
        return mByteOrder;
    }

    /**
     * Returns the {@link mMpAttribIfdData} object if it exists or null.
     */
    protected MpoIfdData getAttribIfdData() {
        return mMpAttribIfdData;
    }

    /**
     * Returns the {@link mMpIndexIfdData} object if it exists or null.
     */
    protected MpoIfdData getIndexIfdData() {
        return mMpIndexIfdData;
    }

    /**
     * Returns the {@link MpoIfdData} object corresponding to a given IFD.
     */
    protected MpoIfdData getMpIfdData(int ifdId) {
        return (ifdId == MpoIfdData.TYPE_MP_INDEX_IFD) ? mMpIndexIfdData : mMpAttribIfdData;
    }

    /**
     * Returns the tag with a given tag ID in the given IFD if the tag exists.
     * Otherwise returns null.
     */
    protected MpoTag getTag(short tag, int ifd) {
        MpoIfdData mpIfdData = getMpIfdData(ifd);
        return mpIfdData.getTag(tag);
    }

    /**
     * Adds the given MpoTag to its default IFD and returns an existing MpoTag
     * with the same TID or null if none exist.
     */
    protected MpoTag addTag(MpoTag tag) {
        if (tag != null) {
            int ifd = tag.getIfd();
            return addTag(tag, ifd);
        }
        return null;
    }

    /**
     * Adds the given MpoTag to the given IFD and returns an existing MpoTag
     * with the same tag ID or null if none exist.
     */
    protected MpoTag addTag(MpoTag tag, int ifdId) {
        if (tag != null && MpoTag.isValidIfd(ifdId)) {
            return getMpIfdData(ifdId).setTag(tag);
        }
        return null;
    }

    /**
     * Removes the tag with a given tag ID and IFD.
     */
    protected void removeTag(short tagId, int ifdId) {
        getMpIfdData(ifdId).removeTag(tagId);
    }

    /**
     * Returns a list of all {@link MpoTag}s in the ExifData or null if there
     * are none.
     */
    protected List<MpoTag> getAllTags() {
        ArrayList<MpoTag> ret = new ArrayList<MpoTag>();
        MpoTag[] tags = mMpIndexIfdData.getAllTags();
        if (tags != null) {
            for (MpoTag t : tags) {
                ret.add(t);
            }
        }

        tags = mMpAttribIfdData.getAllTags();
        if (tags != null) {
            for (MpoTag t : tags) {
                ret.add(t);
            }
        }

        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link MpoTag}s in a given IFD or null if there are
     * none.
     */
    protected List<MpoTag> getAllTagsForIfd(int ifd) {
        MpoTag[] tags = getMpIfdData(ifd).getAllTags();
        if (tags == null) {
            return null;
        }
        ArrayList<MpoTag> ret = new ArrayList<MpoTag>(tags.length);
        for (MpoTag t : tags) {
            ret.add(t);
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link MpoTag}s with a given TID or null if there
     * are none.
     */
    protected List<MpoTag> getAllTagsForTagId(short tag) {
        ArrayList<MpoTag> ret = new ArrayList<MpoTag>();
        MpoTag t = mMpIndexIfdData.getTag(tag);
        if (t != null) {
            ret.add(t);
        }

        t = mMpAttribIfdData.getTag(tag);
        if (t != null) {
            ret.add(t);
        }

        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof MpoImageData) {
            MpoImageData data = (MpoImageData) obj;
            if (data.mByteOrder != mByteOrder) {
                return false;
            }

            MpoIfdData indexIfd1 = data.getMpIfdData(MpoIfdData.TYPE_MP_INDEX_IFD);
            MpoIfdData indexIfd2 = getMpIfdData(MpoIfdData.TYPE_MP_INDEX_IFD);
            if (indexIfd1 != indexIfd2 && indexIfd1 != null && !indexIfd1.equals(indexIfd2)) {
                return false;
            }

            MpoIfdData attribIfd1 = data.getMpIfdData(MpoIfdData.TYPE_MP_ATTRIB_IFD);
            MpoIfdData attribIfd2 = getMpIfdData(MpoIfdData.TYPE_MP_ATTRIB_IFD);
            if (attribIfd1 != attribIfd2 && attribIfd1 != null && !attribIfd1.equals(attribIfd2)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private int calculateOffsetOfIfd(MpoIfdData ifd, int offset) {
        offset += 2 + ifd.getTagCount() * MpoTag.TAG_SIZE + 4;
        MpoTag[] tags = ifd.getAllTags();
        for (MpoTag tag : tags) {
            if (tag.getDataSize() > 4) {
                tag.setOffset(offset);
                offset += tag.getDataSize();
            }
        }
        return offset;
    }

    public int calculateAllIfdOffsets() {
        int offset = MP_HEADER_SIZE;
        MpoIfdData indexIfd = getIndexIfdData();
        if (indexIfd.getTagCount() > 0)
            offset = calculateOffsetOfIfd(indexIfd, offset);

        MpoIfdData attribIfd = getAttribIfdData();
        if (attribIfd.getTagCount() > 0) {
            indexIfd.setOffsetToNextIfd(offset);
            offset = calculateOffsetOfIfd(attribIfd, offset);
        }

        return offset;
    }

    public int calculateImageSize() {
        return 2 + APP_HEADER_SIZE + calculateAllIfdOffsets() + mJpegData.length;
    }
}
