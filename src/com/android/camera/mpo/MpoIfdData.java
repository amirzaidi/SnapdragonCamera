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

import java.util.HashMap;
import java.util.Map;

/**
 * This class stores all the tags in an MP Index IFD.
 */
public class MpoIfdData {
    public static final int TYPE_MP_INDEX_IFD = 1;
    public static final int TYPE_MP_ATTRIB_IFD = 2;
    public static final byte[] MP_FORMAT_VER_VALUE = { 0x30, 0x31, 0x30, 0x30 };

    private final int mIfdId;
    private final Map<Short, MpoTag> mTags = new HashMap<Short, MpoTag>();
    private int mOffsetToNextIfd = 0;

    /**
     * Creates an empty MpIndexIfdData
     */
    public MpoIfdData(int ifdId) {
        mIfdId = ifdId;
    }

    /**
     * Get a array the contains all {@link MpoTag} in this IFD.
     */
    protected MpoTag[] getAllTags() {
        return mTags.values().toArray(new MpoTag[mTags.size()]);
    }

    /**
     * Gets the {@link MpoTag} with given tag id. Return null if there is no
     * such tag.
     */
    protected MpoTag getTag(short tagId) {
        return mTags.get(tagId);
    }

    /**
     * Adds or replaces a {@link MpoTag}.
     */
    protected MpoTag setTag(MpoTag tag) {
        tag.setIfd(mIfdId);
        return mTags.put(tag.getTagId(), tag);
    }

    protected boolean checkCollision(short tagId) {
        return mTags.get(tagId) != null;
    }

    /**
     * Removes the tag of the given ID
     */
    protected void removeTag(short tagId) {
        mTags.remove(tagId);
    }

    /**
     * Gets the tags count in the IFD.
     */
    protected int getTagCount() {
        return mTags.size();
    }

    /**
     * Sets the offset of next IFD.
     */
    protected void setOffsetToNextIfd(int offset) {
        mOffsetToNextIfd = offset;
    }

    /**
     * Gets the offset of next IFD.
     */
    protected int getOffsetToNextIfd() {
        return mOffsetToNextIfd;
    }

    /**
     * Returns true if all tags in this two IFDs are equal. Note that tags of
     * IFD offset will be ignored.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof MpoIfdData) {
            MpoIfdData data = (MpoIfdData) obj;
            if (data.getTagCount() == getTagCount()) {
                MpoTag[] tags = data.getAllTags();
                for (MpoTag tag : tags) {
                    MpoTag tag2 = mTags.get(tag.getTagId());
                    if (!tag.equals(tag2)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
